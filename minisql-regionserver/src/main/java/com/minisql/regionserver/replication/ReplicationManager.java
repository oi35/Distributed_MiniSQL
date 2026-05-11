package com.minisql.regionserver.replication;

import com.minisql.regionserver.proto.ApplyReplicationLogRequest;
import com.minisql.regionserver.proto.ApplyReplicationLogResponse;
import com.minisql.regionserver.proto.RegionServerServiceGrpc;
import com.minisql.regionserver.wal.WalRecord;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 副本管理器 —— 协调主节点向多个副本复制 WAL 日志。
 *
 * 核心职责：
 * 1. 副本列表管理：记录每个 Region 的副本服务器地址
 * 2. 日志复制：主节点写操作后，异步将 WAL 推送到所有副本
 * 3. 水印追踪：记录每个副本已成功应用的序列号
 * 4. 故障检测：通过重试和超时机制，标记不健康的副本
 *
 *
 * 同步 vs 异步复制（本实现选择异步）：
 * - 同步复制：客户端必须等待所有副本确认后才返回。强一致性但延迟高。
 * - 异步复制：主节点写完后立即返回客户端，后台异步推给副本。
 *   延迟低但可能出现"主节点挂了但副本还没收到最新数据"的情况。
 *   对于教学项目，选择异步复制以降低复杂度。
 */
public class ReplicationManager implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationManager.class);

    // 副本地址映射：regionId → 副本服务器地址列表（host:port）
    private final Map<String, List<String>> regionReplicas;

    // gRPC 连接池：复用连接，避免每次复制都创建新连接
    private final Map<String, ManagedChannel> replicaChannels;

    // gRPC stub 缓存：每个副本地址对应一个异步 stub
    private final Map<String, RegionServerServiceGrpc.RegionServerServiceStub> replicaStubs;

    // 水印追踪：regionId → (replicaAddress → 最后成功应用的序列号)
    private final Map<String, Map<String, Long>> watermarkMap;

    // 失败计数：连续失败次数（用于故障检测）
    private final Map<String, Map<String, AtomicLong>> failureCountMap;

    // 复制线程池：异步执行复制任务
    private final ExecutorService replicationExecutor;

    // 最大连续失败次数（超过后标记副本为不健康）
    private static final long MAX_CONSECUTIVE_FAILURES = 5;

    // 连接超时：5秒
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;

    public ReplicationManager() {
        this.regionReplicas = new ConcurrentHashMap<>();
        this.replicaChannels = new ConcurrentHashMap<>();
        this.replicaStubs = new ConcurrentHashMap<>();
        this.watermarkMap = new ConcurrentHashMap<>();
        this.failureCountMap = new ConcurrentHashMap<>();
        this.replicationExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "replication-worker");
                    t.setDaemon(true);
                    return t;
                });
        logger.info("ReplicationManager 初始化完成: 线程池大小={}",
                Runtime.getRuntime().availableProcessors());
    }

    /**
     * 为一个 Region 设置副本服务器列表。
     * 通常在 Region 上线时由 Master 调用或通过配置指定。
     *
     * @param regionId  Region ID
     * @param replicas  副本服务器地址列表，如 ["rs-002:8001", "rs-003:8001"]
     */
    public void setReplicas(String regionId, List<String> replicas) {
        regionReplicas.put(regionId, new ArrayList<>(replicas));
        watermarkMap.put(regionId, new ConcurrentHashMap<>());
        failureCountMap.put(regionId, new ConcurrentHashMap<>());

        // 预建立 gRPC 连接
        for (String replicaAddr : replicas) {
            getOrCreateChannel(replicaAddr);
        }

        logger.info("Region {} 副本列表已设置: {}", regionId, replicas);
    }

    /**
     * 添加单个副本。
     */
    public void addReplica(String regionId, String replicaAddress) {
        regionReplicas.computeIfAbsent(regionId, k -> new CopyOnWriteArrayList<>())
                .add(replicaAddress);
        watermarkMap.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>())
                .putIfAbsent(replicaAddress, 0L);
        failureCountMap.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>())
                .putIfAbsent(replicaAddress, new AtomicLong(0));
        getOrCreateChannel(replicaAddress);
        logger.info("Region {} 添加副本: {}", regionId, replicaAddress);
    }

    /**
     * 移除一个副本。
     */
    public void removeReplica(String regionId, String replicaAddress) {
        List<String> replicas = regionReplicas.get(regionId);
        if (replicas != null) {
            replicas.remove(replicaAddress);
        }
        Map<String, Long> watermarks = watermarkMap.get(regionId);
        if (watermarks != null) {
            watermarks.remove(replicaAddress);
        }
        Map<String, AtomicLong> failures = failureCountMap.get(regionId);
        if (failures != null) {
            failures.remove(replicaAddress);
        }
        logger.info("Region {} 移除副本: {}", regionId, replicaAddress);
    }

    /**
     * 获取一个 Region 的所有副本地址。
     */
    public List<String> getReplicas(String regionId) {
        return regionReplicas.getOrDefault(regionId, Collections.emptyList());
    }

    /**
     * 主节点写操作完成后调用此方法，将 WAL 记录异步推送到所有副本。
     *
     * 流程：
     * 1. 根据 regionId 找到所有副本地址
     * 2. 为每个副本提交一个异步复制任务
     * 3. 任务内容：将单条 WalRecord 封装为 ApplyReplicationLogRequest 发送给副本
     * 4. 记录成功/失败，更新水印
     *
     * @param regionId Region ID
     * @param record   刚写入主节点 WAL 的记录
     */
    public void replicate(String regionId, WalRecord record) {
        List<String> replicas = regionReplicas.get(regionId);
        if (replicas == null || replicas.isEmpty()) {
            logger.debug("Region {} 没有副本，跳过复制", regionId);
            return;
        }

        for (String replicaAddr : replicas) {
            replicationExecutor.submit(() -> replicateToSingleReplica(regionId, replicaAddr, record));
        }
    }

    /**
     * 批量复制：将多条 WAL 记录打包一次发送，减少网络开销。
     */
    public void batchReplicate(String regionId, List<WalRecord> records) {
        List<String> replicas = regionReplicas.get(regionId);
        if (replicas == null || replicas.isEmpty() || records.isEmpty()) {
            return;
        }

        for (String replicaAddr : replicas) {
            replicationExecutor.submit(() -> batchReplicateToSingleReplica(regionId, replicaAddr, records));
        }
    }

    /**
     * 获取指定 Region 中所有副本的复制水印。
     *
     * @return replicaAddress → lastAppliedSequenceId
     */
    public Map<String, Long> getWatermarks(String regionId) {
        Map<String, Long> watermarks = watermarkMap.get(regionId);
        if (watermarks == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(watermarks);
    }

    /**
     * 获取最落后的副本水印（用于判断是否所有副本都已同步）。
     */
    public long getMinWatermark(String regionId) {
        Map<String, Long> watermarks = watermarkMap.get(regionId);
        if (watermarks == null || watermarks.isEmpty()) {
            return 0;
        }
        return watermarks.values().stream().min(Long::compareTo).orElse(0L);
    }

    /**
     * 获取副本的健康状态。
     *
     * @return replicaAddress → 是否健康
     */
    public Map<String, Boolean> getReplicaHealth(String regionId) {
        Map<String, Boolean> health = new HashMap<>();
        Map<String, AtomicLong> failures = failureCountMap.get(regionId);
        if (failures == null) {
            return health;
        }
        for (Map.Entry<String, AtomicLong> entry : failures.entrySet()) {
            health.put(entry.getKey(), entry.getValue().get() < MAX_CONSECUTIVE_FAILURES);
        }
        return health;
    }

    // ====================== 内部方法 ======================

    /**
     * 向单个副本复制一条 WAL 记录。
     */
    private void replicateToSingleReplica(String regionId, String replicaAddr, WalRecord record) {
        try {
            RegionServerServiceGrpc.RegionServerServiceStub stub = getOrCreateStub(replicaAddr);
            if (stub == null) {
                recordFailure(regionId, replicaAddr);
                return;
            }

            // 构建 ApplyReplicationLogRequest
            ApplyReplicationLogRequest request = buildApplyRequest(regionId,
                    Collections.singletonList(record));

            // 使用阻塞式调用（简化版），也可改为异步回调
            ManagedChannel channel = replicaChannels.get(replicaAddr);
            RegionServerServiceGrpc.RegionServerServiceBlockingStub blockingStub =
                    RegionServerServiceGrpc.newBlockingStub(channel);

            ApplyReplicationLogResponse response = blockingStub.applyReplicationLog(request);

            if (response.getSuccess()) {
                // 更新水印
                updateWatermark(regionId, replicaAddr, record.getSequenceId());
                resetFailureCount(regionId, replicaAddr);
                logger.debug("复制成功: region={}, replica={}, seq={}",
                        regionId, replicaAddr, record.getSequenceId());
            } else {
                recordFailure(regionId, replicaAddr);
                logger.warn("复制失败（副本拒绝）: region={}, replica={}, seq={}, error={}",
                        regionId, replicaAddr, record.getSequenceId(), response.getErrorMessage());
            }
        } catch (StatusRuntimeException e) {
            recordFailure(regionId, replicaAddr);
            logger.warn("复制失败（gRPC错误）: region={}, replica={}, seq={}, error={}",
                    regionId, replicaAddr, record.getSequenceId(), e.getMessage());
        } catch (Exception e) {
            recordFailure(regionId, replicaAddr);
            logger.error("复制异常: region={}, replica={}, seq={}",
                    regionId, replicaAddr, record.getSequenceId(), e);
        }
    }

    /**
     * 向单个副本批量复制多条 WAL 记录。
     */
    private void batchReplicateToSingleReplica(String regionId, String replicaAddr,
                                               List<WalRecord> records) {
        try {
            ManagedChannel channel = replicaChannels.get(replicaAddr);
            if (channel == null) {
                recordFailure(regionId, replicaAddr);
                return;
            }

            RegionServerServiceGrpc.RegionServerServiceBlockingStub blockingStub =
                    RegionServerServiceGrpc.newBlockingStub(channel);

            ApplyReplicationLogRequest request = buildApplyRequest(regionId, records);
            ApplyReplicationLogResponse response = blockingStub.applyReplicationLog(request);

            if (response.getSuccess() && response.getAppliedCount() == records.size()) {
                long maxSeq = records.get(records.size() - 1).getSequenceId();
                updateWatermark(regionId, replicaAddr, maxSeq);
                resetFailureCount(regionId, replicaAddr);
                logger.debug("批量复制成功: region={}, replica={}, {}条, seq<= {}",
                        regionId, replicaAddr, records.size(), maxSeq);
            } else {
                recordFailure(regionId, replicaAddr);
                logger.warn("批量复制部分失败: region={}, replica={}, applied={}/{}",
                        regionId, replicaAddr, response.getAppliedCount(), records.size());
            }
        } catch (Exception e) {
            recordFailure(regionId, replicaAddr);
            logger.error("批量复制异常: region={}, replica={}",
                    regionId, replicaAddr, e);
        }
    }

    /**
     * 构建 ApplyReplicationLogRequest。
     * 将内部 WalRecord 转换为 proto 格式。
     */
    private ApplyReplicationLogRequest buildApplyRequest(String regionId,
                                                         List<WalRecord> records) {
        List<com.minisql.regionserver.proto.ReplicationLogEntry> entries = new ArrayList<>();

        for (WalRecord record : records) {
            com.minisql.regionserver.proto.ReplicationLogEntry.Builder builder =
                    com.minisql.regionserver.proto.ReplicationLogEntry.newBuilder()
                            .setSequenceId(record.getSequenceId())
                            .setRegionId(record.getRegionId())
                            .setTimestamp(record.getTimestamp())
                            .setKey(ByteString.copyFrom(record.getKey()));

            if ("PUT".equalsIgnoreCase(record.getOperation())) {
                builder.setOperation(
                        com.minisql.regionserver.proto.ReplicationLogEntry.OperationType.PUT);
            } else if ("DELETE".equalsIgnoreCase(record.getOperation())) {
                builder.setOperation(
                        com.minisql.regionserver.proto.ReplicationLogEntry.OperationType.DELETE);
            }

            Map<String, ByteString> columns = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : record.getColumns().entrySet()) {
                columns.put(entry.getKey(), ByteString.copyFrom(entry.getValue()));
            }
            builder.putAllColumns(columns);

            if (record.getChecksum() != null) {
                builder.setChecksum(ByteString.copyFrom(record.getChecksum()));
            }

            entries.add(builder.build());
        }

        return ApplyReplicationLogRequest.newBuilder()
                .setRegionId(regionId)
                .addAllLogs(entries)
                .build();
    }

    /**
     * 获取或创建到副本的 gRPC 连接。
     */
    private ManagedChannel getOrCreateChannel(String address) {
        return replicaChannels.computeIfAbsent(address, addr -> {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(addr)
                    .usePlaintext()          // 教学项目使用明文，生产环境应使用 TLS
                    .build();
            logger.info("创建gRPC连接到副本: {}", addr);
            return channel;
        });
    }

    /**
     * 获取或创建副本的 gRPC stub。
     */
    private RegionServerServiceGrpc.RegionServerServiceStub getOrCreateStub(String address) {
        return replicaStubs.computeIfAbsent(address, addr -> {
            ManagedChannel channel = getOrCreateChannel(address);
            return RegionServerServiceGrpc.newStub(channel);
        });
    }

    /**
     * 更新复制水印。
     */
    private void updateWatermark(String regionId, String replicaAddr, long sequenceId) {
        watermarkMap.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>())
                .merge(replicaAddr, sequenceId, Math::max);
    }

    /**
     * 记录失败（递增失败计数）。
     */
    private void recordFailure(String regionId, String replicaAddr) {
        AtomicLong count = failureCountMap.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(replicaAddr, k -> new AtomicLong(0));
        long failures = count.incrementAndGet();

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            logger.warn("副本 {} 不健康（连续失败{}次）: region={}",
                    replicaAddr, failures, regionId);
        }
    }

    /**
     * 重置失败计数（成功复制后调用）。
     */
    private void resetFailureCount(String regionId, String replicaAddr) {
        Map<String, AtomicLong> failures = failureCountMap.get(regionId);
        if (failures != null) {
            AtomicLong count = failures.get(replicaAddr);
            if (count != null) {
                count.set(0);
            }
        }
    }

    @Override
    public void close() {
        replicationExecutor.shutdown();
        try {
            if (!replicationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                replicationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            replicationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 关闭所有 gRPC 连接
        for (ManagedChannel channel : replicaChannels.values()) {
            channel.shutdown();
        }

        logger.info("ReplicationManager 已关闭：{} 个连接已释放", replicaChannels.size());
    }
}
