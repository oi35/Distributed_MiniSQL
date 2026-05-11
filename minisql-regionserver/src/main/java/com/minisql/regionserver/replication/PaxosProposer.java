package com.minisql.regionserver.replication;

import com.google.protobuf.ByteString;
import com.minisql.regionserver.proto.ApplyReplicationLogRequest;
import com.minisql.regionserver.proto.ApplyReplicationLogResponse;
import com.minisql.regionserver.proto.RegionServerServiceGrpc;
import com.minisql.regionserver.wal.WalRecord;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Paxos Proposer（提案者）—— 运行在主节点上，驱动共识协议。
 *
 * 完整的 Paxos 三阶段流程：
 *
 *   1. Prepare（准备）:
 *      - 生成唯一提案编号 N
 *      - 向所有 Acceptor（副本）广播 Prepare(N)
 *      - 收集 Promise 响应
 *      - 如果多数承诺 → 进入 Accept 阶段
 *      - 如果 Promise 中携带了之前已接受的值，则用该值替换我们要提交的值
 *
 *   2. Accept（接受）:
 *      - 向所有承诺的 Acceptor 发送 Accept(N, 值)
 *      - 收集 Accepted 响应
 *      - 如果多数接受 → 进入 Commit 阶段
 *
 *   3. Commit（提交）:
 *      - 向所有 Acceptor 发送 Commit(N)
 *      - 标记为已提交 → 返回成功
 *
 * 多数派计算：
 *   多数 = totalReplicas/2 + 1
 *   例如：3个副本需要2票，5个副本需要3票
 *
 * 提案编号生成（简化版）：
 *   使用 WAL 序列号 + 服务器ID 保证全局唯一且单调递增
 */
public class PaxosProposer {

    private static final Logger logger = LoggerFactory.getLogger(PaxosProposer.class);

    private final String serverId;
    private final PaxosAcceptor localAcceptor; // 本地的 Acceptor（主节点也参与投票）
    private final Map<String, ManagedChannel> replicaChannels;
    private final ExecutorService phaseExecutor;

    // 提案编号中的序列号部分（单调递增）
    private final AtomicLong proposalSequence;

    // 每个阶段的超时时间（毫秒）
    private static final long PREPARE_TIMEOUT_MS = 3000;
    private static final long ACCEPT_TIMEOUT_MS = 3000;
    private static final long COMMIT_TIMEOUT_MS = 2000;

    public PaxosProposer(String serverId) {
        this.serverId = serverId;
        this.localAcceptor = new PaxosAcceptor(serverId);
        this.replicaChannels = new ConcurrentHashMap<>();
        this.phaseExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "paxos-phase");
            t.setDaemon(true);
            return t;
        });
        this.proposalSequence = new AtomicLong(0);
        logger.info("PaxosProposer 初始化完成: server={}", serverId);
    }

    /**
     * 获取本地 Acceptor（主节点的数据也需要通过 Paxos 来提交）。
     */
    public PaxosAcceptor getLocalAcceptor() {
        return localAcceptor;
    }

    /**
     * 获取或创建到副本的 gRPC 连接。
     */
    public void ensureConnection(String replicaAddress) {
        replicaChannels.computeIfAbsent(replicaAddress, addr -> {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(addr)
                    .usePlaintext()
                    .build();
            logger.info("PaxosProposer 建立连接: {}", addr);
            return channel;
        });
    }

    /**
     * 执行 Paxos 共识协议，将一条数据写入多数副本。
     *
     * @param regionId    Region ID
     * @param record      要共识的 WAL 记录
     * @param replicaAddrs 所有副本的地址列表（包括主节点自身）
     * @return 共识结果
     */
    public PaxosTypes.ConsensusResult propose(String regionId, WalRecord record,
                                               List<String> replicaAddrs) {
        int totalNodes = replicaAddrs.size() + 1; // +1 包括主节点自己
        int majority = totalNodes / 2 + 1;

        logger.info("Paxos 共识启动: region={}, 总节点={}, 多数={}",
                regionId, totalNodes, majority);

        // 生成提案编号
        PaxosTypes.ProposalNumber proposalNumber =
                new PaxosTypes.ProposalNumber(proposalSequence.incrementAndGet(), serverId);

        // 序列化 WalRecord 为字节数组
        byte[] serializedValue = serializeWalRecord(record);
        if (serializedValue == null) {
            return PaxosTypes.ConsensusResult.REJECTED;
        }

        try {
            // ========== 阶段1：Prepare ==========
            logger.info("[Phase-1 Prepare] 开始: region={}, proposal={}", regionId, proposalNumber);

            // 先询问本地 Acceptor
            int promises = 0;
            PaxosTypes.PromiseResponse localPromise = localAcceptor.handlePrepare(
                    new PaxosTypes.PrepareRequest(proposalNumber, regionId));
            if (localPromise.isPromised()) {
                promises++;
                // 如果本地 Acceptor 有之前已接受的值，使用它
                if (localPromise.getLastAcceptedValue() != null) {
                    serializedValue = localPromise.getLastAcceptedValue();
                }
            }

            // 向所有远程副本发送 Prepare
            Map<String, PaxosTypes.PromiseResponse> prepareResults =
                    broadcastPrepare(regionId, proposalNumber, replicaAddrs, PREPARE_TIMEOUT_MS);

            for (PaxosTypes.PromiseResponse response : prepareResults.values()) {
                if (response.isPromised()) {
                    promises++;
                    if (response.getLastAcceptedValue() != null) {
                        serializedValue = response.getLastAcceptedValue();
                    }
                }
            }

            logger.info("[Phase-1 Prepare] 结果: 承诺={}/{}, 需求多数={}",
                    promises, totalNodes, majority);

            // 未获多数承诺 → 提案失败
            if (promises < majority) {
                logger.warn("[Phase-1 Prepare] 失败：未达多数。承诺={}, 需要={}",
                        promises, majority);
                return PaxosTypes.ConsensusResult.REJECTED;
            }

            // ========== 阶段2：Accept ==========
            logger.info("[Phase-2 Accept] 开始: region={}, proposal={}", regionId, proposalNumber);

            // 本地 Acceptor 先接受
            int accepted = 0;
            PaxosTypes.AcceptedResponse localAccept = localAcceptor.handleAccept(
                    new PaxosTypes.AcceptRequest(proposalNumber, regionId, serializedValue));
            if (localAccept.isAccepted()) {
                accepted++;
            }

            // 向所有远程副本发送 Accept
            Map<String, PaxosTypes.AcceptedResponse> acceptResults =
                    broadcastAccept(regionId, proposalNumber, serializedValue, replicaAddrs,
                            ACCEPT_TIMEOUT_MS);

            for (PaxosTypes.AcceptedResponse response : acceptResults.values()) {
                if (response.isAccepted()) {
                    accepted++;
                }
            }

            logger.info("[Phase-2 Accept] 结果: 接受={}/{}, 需求多数={}",
                    accepted, totalNodes, majority);

            // 未获多数接受 → 提案失败
            if (accepted < majority) {
                logger.warn("[Phase-2 Accept] 失败：未达多数。接受={}, 需要={}",
                        accepted, majority);
                return PaxosTypes.ConsensusResult.REJECTED;
            }

            // ========== 阶段3：Commit ==========
            logger.info("[Phase-3 Commit] 开始: region={}, proposal={}", regionId, proposalNumber);

            // 本地 Acceptor 先提交
            localAcceptor.handleCommit(new PaxosTypes.CommitRequest(proposalNumber, regionId));

            // 向所有远程副本发送 Commit
            broadcastCommit(regionId, proposalNumber, replicaAddrs, COMMIT_TIMEOUT_MS);

            logger.info("[Phase-3 Commit] 完成: region={}, proposal={}, 共识达成!",
                    regionId, proposalNumber);

            return PaxosTypes.ConsensusResult.COMMITTED;

        } catch (Exception e) {
            logger.error("Paxos 共识异常: region={}, proposal={}", regionId, proposalNumber, e);
            return PaxosTypes.ConsensusResult.TIMEOUT;
        }
    }

    /**
     * 向远程副本广播 Prepare 请求，收集 Promise 响应。
     */
    private Map<String, PaxosTypes.PromiseResponse> broadcastPrepare(
            String regionId, PaxosTypes.ProposalNumber proposalNumber,
            List<String> replicaAddrs, long timeoutMs) {

        Map<String, PaxosTypes.PromiseResponse> results = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(replicaAddrs.size());

        for (String addr : replicaAddrs) {
            phaseExecutor.submit(() -> {
                try {
                    ManagedChannel channel = replicaChannels.get(addr);
                    if (channel == null) {
                        latch.countDown();
                        return;
                    }

                    RegionServerServiceGrpc.RegionServerServiceBlockingStub stub =
                            RegionServerServiceGrpc.newBlockingStub(channel);

                    // 通过 applyReplicationLog 发送 Prepare 标记
                    ApplyReplicationLogRequest request = ApplyReplicationLogRequest.newBuilder()
                            .setRegionId("__PAXOS_PREPARE__" + regionId)  // 特殊前缀标记Prepare阶段
                            .addLogs(com.minisql.regionserver.proto.ReplicationLogEntry.newBuilder()
                                    .setSequenceId(proposalNumber.getSequenceNum())
                                    .setRegionId(regionId)
                                    .setTimestamp(System.currentTimeMillis())
                                    .setOperation(
                                        com.minisql.regionserver.proto.ReplicationLogEntry
                                            .OperationType.PUT)
                                    .setKey(ByteString.copyFromUtf8(proposalNumber.toString()))
                                    .build())
                            .build();

                    ApplyReplicationLogResponse response = stub.applyReplicationLog(request);

                    // response.success=true 表示 Promise
                    results.put(addr, new PaxosTypes.PromiseResponse(
                            response.getSuccess(), proposalNumber, null, null));

                } catch (Exception e) {
                    logger.warn("Prepare 通信失败: replica={}, error={}", addr, e.getMessage());
                    results.put(addr, new PaxosTypes.PromiseResponse(false, proposalNumber, null, null));
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitLatch(latch, timeoutMs);
        return results;
    }

    /**
     * 向远程副本广播 Accept 请求，收集 Accepted 响应。
     */
    private Map<String, PaxosTypes.AcceptedResponse> broadcastAccept(
            String regionId, PaxosTypes.ProposalNumber proposalNumber,
            byte[] value, List<String> replicaAddrs, long timeoutMs) {

        Map<String, PaxosTypes.AcceptedResponse> results = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(replicaAddrs.size());

        for (String addr : replicaAddrs) {
            phaseExecutor.submit(() -> {
                try {
                    ManagedChannel channel = replicaChannels.get(addr);
                    if (channel == null) {
                        latch.countDown();
                        return;
                    }

                    RegionServerServiceGrpc.RegionServerServiceBlockingStub stub =
                            RegionServerServiceGrpc.newBlockingStub(channel);

                    ApplyReplicationLogRequest request = ApplyReplicationLogRequest.newBuilder()
                            .setRegionId("__PAXOS_ACCEPT__" + regionId)
                            .addLogs(com.minisql.regionserver.proto.ReplicationLogEntry.newBuilder()
                                    .setSequenceId(proposalNumber.getSequenceNum())
                                    .setRegionId(regionId)
                                    .setTimestamp(System.currentTimeMillis())
                                    .setOperation(
                                        com.minisql.regionserver.proto.ReplicationLogEntry
                                            .OperationType.PUT)
                                    .setKey(ByteString.copyFromUtf8(proposalNumber.toString()))
                                    .putColumns("__paxos_value__",
                                            ByteString.copyFrom(value))
                                    .build())
                            .build();

                    ApplyReplicationLogResponse response = stub.applyReplicationLog(request);

                    results.put(addr, new PaxosTypes.AcceptedResponse(
                            response.getSuccess(), proposalNumber));

                } catch (Exception e) {
                    logger.warn("Accept 通信失败: replica={}, error={}", addr, e.getMessage());
                    results.put(addr, new PaxosTypes.AcceptedResponse(false, proposalNumber));
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitLatch(latch, timeoutMs);
        return results;
    }

    /**
     * 向远程副本广播 Commit 请求。
     */
    private void broadcastCommit(String regionId, PaxosTypes.ProposalNumber proposalNumber,
                                 List<String> replicaAddrs, long timeoutMs) {

        CountDownLatch latch = new CountDownLatch(replicaAddrs.size());

        for (String addr : replicaAddrs) {
            phaseExecutor.submit(() -> {
                try {
                    ManagedChannel channel = replicaChannels.get(addr);
                    if (channel == null) {
                        latch.countDown();
                        return;
                    }

                    RegionServerServiceGrpc.RegionServerServiceBlockingStub stub =
                            RegionServerServiceGrpc.newBlockingStub(channel);

                    ApplyReplicationLogRequest request = ApplyReplicationLogRequest.newBuilder()
                            .setRegionId("__PAXOS_COMMIT__" + regionId)
                            .addLogs(com.minisql.regionserver.proto.ReplicationLogEntry.newBuilder()
                                    .setSequenceId(proposalNumber.getSequenceNum())
                                    .setRegionId(regionId)
                                    .setTimestamp(System.currentTimeMillis())
                                    .setOperation(
                                        com.minisql.regionserver.proto.ReplicationLogEntry
                                            .OperationType.PUT)
                                    .setKey(ByteString.copyFromUtf8(proposalNumber.toString()))
                                    .build())
                            .build();

                    stub.applyReplicationLog(request);

                } catch (Exception e) {
                    logger.warn("Commit 通信失败: replica={}, error={}", addr, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitLatch(latch, timeoutMs);
    }

    /**
     * 序列化 WalRecord 为字节数组（用于网络传输）。
     */
    private byte[] serializeWalRecord(WalRecord record) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeLong(record.getSequenceId());
            dos.writeUTF(record.getRegionId());
            dos.writeUTF(record.getTableName() != null ? record.getTableName() : "");
            dos.writeLong(record.getTimestamp());
            dos.writeUTF(record.getOperation());
            dos.writeInt(record.getKey().length);
            dos.write(record.getKey());
            dos.writeInt(record.getColumns().size());
            for (Map.Entry<String, byte[]> entry : record.getColumns().entrySet()) {
                dos.writeUTF(entry.getKey());
                dos.writeInt(entry.getValue().length);
                dos.write(entry.getValue());
            }
            if (record.getChecksum() != null) {
                dos.writeInt(record.getChecksum().length);
                dos.write(record.getChecksum());
            } else {
                dos.writeInt(0);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            logger.error("序列化 WalRecord 失败", e);
            return null;
        }
    }

    /**
     * 等待 CountDownLatch（带超时）。
     */
    private void awaitLatch(CountDownLatch latch, long timeoutMs) {
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                logger.warn("阶段超时（{}ms），已完成 {}/{}", timeoutMs,
                        latch.getCount(), 0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("等待被中断");
        }
    }

    /**
     * 获取副本连接数。
     */
    public int getConnectionCount() {
        return replicaChannels.size();
    }

    /**
     * 关闭所有连接。
     */
    public void close() {
        phaseExecutor.shutdown();
        try {
            phaseExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (ManagedChannel channel : replicaChannels.values()) {
            channel.shutdown();
        }
        replicaChannels.clear();
        logger.info("PaxosProposer 已关闭: server={}", serverId);
    }
}
