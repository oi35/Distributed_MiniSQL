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
 */
public class ReplicationManager implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationManager.class);

    private final Map<String, List<String>> regionReplicas;
    private final Map<String, ManagedChannel> replicaChannels;
    private final Map<String, RegionServerServiceGrpc.RegionServerServiceStub> replicaStubs;
    private final Map<String, Map<String, Long>> watermarkMap;
    private final Map<String, Map<String, AtomicLong>> failureCountMap;
    private final ExecutorService replicationExecutor;

    private static final long MAX_CONSECUTIVE_FAILURES = 5;
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
     * 根据Master下发的RegionInfo设置副本拓扑。
     *
     * @param regionId       Region ID
     * @param primaryServer  主副本地址
     * @param replicaServers 所有副本地址列表（含主副本自身）
     */
    public void setReplicasFromRegionInfo(String regionId, String primaryServer,
                                          List<String> replicaServers) {
        // 排除主副本自身，仅保留从副本地址
        List<String> followerReplicas = new ArrayList<>();
        if (replicaServers != null) {
            for (String addr : replicaServers) {
                if (!addr.equals(primaryServer)) {
                    followerReplicas.add(addr);
                }
            }
        }
        setReplicas(regionId, followerReplicas);
        logger.info("Region {} 从Master元数据同步副本拓扑: primary={}, replicas={}",
                regionId, primaryServer, followerReplicas);
    }

    /** @deprecated 推荐使用 setReplicasFromRegionInfo */
    @Deprecated
    public void setReplicas(String regionId, List<String> replicas) {
        regionReplicas.put(regionId, new ArrayList<>(replicas));
        watermarkMap.put(regionId, new ConcurrentHashMap<>());
        failureCountMap.put(regionId, new ConcurrentHashMap<>());
        for (String replicaAddr : replicas) {
            getOrCreateChannel(replicaAddr);
        }
        logger.info("Region {} 副本列表已设置: {}", regionId, replicas);
    }

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

    public List<String> getReplicas(String regionId) {
        return regionReplicas.getOrDefault(regionId, Collections.emptyList());
    }

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

    public void batchReplicate(String regionId, List<WalRecord> records) {
        List<String> replicas = regionReplicas.get(regionId);
        if (replicas == null || replicas.isEmpty() || records.isEmpty()) {
            return;
        }
        for (String replicaAddr : replicas) {
            replicationExecutor.submit(() -> batchReplicateToSingleReplica(regionId, replicaAddr, records));
        }
    }

    public Map<String, Long> getWatermarks(String regionId) {
        Map<String, Long> watermarks = watermarkMap.get(regionId);
        if (watermarks == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(watermarks);
    }

    public long getMinWatermark(String regionId) {
        Map<String, Long> watermarks = watermarkMap.get(regionId);
        if (watermarks == null || watermarks.isEmpty()) {
            return 0;
        }
        return watermarks.values().stream().min(Long::compareTo).orElse(0L);
    }

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

    /**
     * 获取不健康的副本列表（失败次数超过阈值）。
     */
    public List<String> getUnhealthyReplicas(String regionId) {
        List<String> unhealthy = new ArrayList<>();
        Map<String, AtomicLong> failures = failureCountMap.get(regionId);
        if (failures == null) {
            return unhealthy;
        }
        for (Map.Entry<String, AtomicLong> entry : failures.entrySet()) {
            if (entry.getValue().get() >= MAX_CONSECUTIVE_FAILURES) {
                unhealthy.add(entry.getKey());
            }
        }
        return unhealthy;
    }

    /**
     * 触发副本恢复 —— 从WAL中增量拉取副本缺失的日志，进行全量追赶。
     *
     * @param regionId    Region ID
     * @param replicaAddr 需要恢复的副本地址
     * @param walService  WAL服务（用于读取日志）
     * @param primaryAddr 主节点地址（用于副本从主节点拉取日志）
     * @return 恢复是否成功
     */
    public boolean recoverReplica(String regionId, String replicaAddr,
                                  WalService walService, String primaryAddr) {
        logger.info("开始副本恢复: region={}, replica={}, primary={}",
                regionId, replicaAddr, primaryAddr);

        try {
            // 1. 获取副本的当前水印（最后成功应用的序列号）
            long replicaWatermark = getMinWatermark(regionId);
            // 如果该副本有记录的水印，使用它；否则从头开始
            Map<String, Long> watermarks = watermarkMap.get(regionId);
            if (watermarks != null && watermarks.containsKey(replicaAddr)) {
                replicaWatermark = watermarks.get(replicaAddr);
            }

            // 2. 从本地WAL中读取增量日志
            long latestSeq = walService.getGlobalLatestSequenceId();
            long startSeq = replicaWatermark + 1;

            logger.info("副本恢复: region={}, 从seq={}恢复到seq={}, 共{}条",
                    regionId, startSeq, latestSeq, latestSeq - startSeq + 1);

            if (startSeq > latestSeq) {
                // 副本已经是最新的
                resetFailureCount(regionId, replicaAddr);
                logger.info("副本已是最新状态，无需恢复: region={}, replica={}", regionId, replicaAddr);
                return true;
            }

            // 3. 读取增量日志并批量发送
            List<WalRecord> records = walService.getRecords(regionId, startSeq, latestSeq);
            if (records.isEmpty()) {
                // 尝试从所有region读取（可能是region不匹配）
                records = walService.getRecords(null, startSeq, latestSeq);
            }

            if (!records.isEmpty()) {
                batchReplicateToSingleReplica(regionId, replicaAddr, records);
                // 更新水印
                long maxSeq = records.get(records.size() - 1).getSequenceId();
                updateWatermark(regionId, replicaAddr, maxSeq);
                resetFailureCount(regionId, replicaAddr);

                logger.info("副本恢复成功: region={}, replica={}, 恢复{}条, 水印更新到{}",
                        regionId, replicaAddr, records.size(), maxSeq);
                return true;
            } else {
                // 没有增量日志，直接标记为已恢复
                resetFailureCount(regionId, replicaAddr);
                logger.info("副本恢复完成（无增量日志）: region={}, replica={}", regionId, replicaAddr);
                return true;
            }

        } catch (Exception e) {
            logger.error("副本恢复失败: region={}, replica={}", regionId, replicaAddr, e);
            recordFailure(regionId, replicaAddr);
            return false;
        }
    }

    /**
     * 检查并自动恢复不健康的副本。
     *
     * 对每个region检查不健康副本，对每个不健康副本尝试恢复。
     *
     * @return 成功恢复的副本数量
     */
    public int autoRecoverUnhealthyReplicas(WalService walService, String primaryAddr) {
        int recovered = 0;
        for (String regionId : regionReplicas.keySet()) {
            List<String> unhealthy = getUnhealthyReplicas(regionId);
            for (String replicaAddr : unhealthy) {
                if (recoverReplica(regionId, replicaAddr, walService, primaryAddr)) {
                    recovered++;
                }
            }
        }
        if (recovered > 0) {
            logger.info("自动恢复完成: {}/{} 个不健康副本已恢复", recovered,
                    regionReplicas.size());
        }
        return recovered;
    }

    private void replicateToSingleReplica(String regionId, String replicaAddr, WalRecord record) {
        try {
            RegionServerServiceGrpc.RegionServerServiceStub stub = getOrCreateStub(replicaAddr);
            if (stub == null) {
                recordFailure(regionId, replicaAddr);
                return;
            }

            ApplyReplicationLogRequest request = buildApplyRequest(regionId,
                    Collections.singletonList(record));

            ManagedChannel channel = replicaChannels.get(replicaAddr);
            RegionServerServiceGrpc.RegionServerServiceBlockingStub blockingStub =
                    RegionServerServiceGrpc.newBlockingStub(channel);

            ApplyReplicationLogResponse response = blockingStub.applyReplicationLog(request);

            if (response.getSuccess()) {
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

    private ManagedChannel getOrCreateChannel(String address) {
        return replicaChannels.computeIfAbsent(address, addr -> {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(addr)
                    .usePlaintext()
                    .build();
            logger.info("创建gRPC连接到副本: {}", addr);
            return channel;
        });
    }

    private RegionServerServiceGrpc.RegionServerServiceStub getOrCreateStub(String address) {
        return replicaStubs.computeIfAbsent(address, addr -> {
            ManagedChannel channel = getOrCreateChannel(address);
            return RegionServerServiceGrpc.newStub(channel);
        });
    }

    private void updateWatermark(String regionId, String replicaAddr, long sequenceId) {
        watermarkMap.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>())
                .merge(replicaAddr, sequenceId, Math::max);
    }

    private void recordFailure(String regionId, String replicaAddr) {
        AtomicLong count = failureCountMap.computeIfAbsent(regionId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(replicaAddr, k -> new AtomicLong(0));
        long failures = count.incrementAndGet();
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            logger.warn("副本 {} 不健康（连续失败{}次）: region={}",
                    replicaAddr, failures, regionId);
        }
    }

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
        for (ManagedChannel channel : replicaChannels.values()) {
            channel.shutdown();
        }
        logger.info("ReplicationManager 已关闭：{} 个连接已释放", replicaChannels.size());
    }
}
