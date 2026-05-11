package com.minisql.regionserver.replication;

import com.google.protobuf.ByteString;
import com.minisql.common.proto.ErrorCode;
import com.minisql.regionserver.proto.*;
import com.minisql.regionserver.store.RegionDataStore;
import com.minisql.regionserver.wal.WalRecord;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 副本同步日志服务 —— 处理副本之间的日志拉取和应用。
 *
 * 负责两个核心操作：
 * 1. getReplicationLog:  副本从主节点拉取增量 WAL 日志（流式返回）
 * 2. applyReplicationLog: 副本将收到的日志应用到本地存储 + 写入本地 WAL
 */
public class ReplicationLogService {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationLogService.class);

    // Paxos 消息前缀（用于区分 Paxos 协议消息和普通副本日志）
    private static final String PAXOS_PREPARE_PREFIX = "__PAXOS_PREPARE__";
    private static final String PAXOS_ACCEPT_PREFIX = "__PAXOS_ACCEPT__";
    private static final String PAXOS_COMMIT_PREFIX = "__PAXOS_COMMIT__";

    private final WalService walService;
    // 每个 region 对应的数据存储（用于应用日志时写入数据）
    // ConcurrentHashMap：openRegion/closeRegion 和 applyReplicationLog 可能并发访问
    private final Map<String, RegionDataStore> regionStores;
    // Paxos Acceptor（用于处理 Paxos 协议消息）
    private final PaxosAcceptor paxosAcceptor;

    public ReplicationLogService(WalService walService, String serverId) {
        this.walService = walService;
        this.regionStores = new java.util.concurrent.ConcurrentHashMap<>();
        this.paxosAcceptor = new PaxosAcceptor(serverId);
    }

    public PaxosAcceptor getPaxosAcceptor() {
        return paxosAcceptor;
    }

    /**
     * 注册一个 Region 的数据存储，以便 applyReplicationLog 能将日志写入正确的存储。
     */
    public void registerRegionStore(String regionId, RegionDataStore store) {
        regionStores.put(regionId, store);
        logger.info("注册Region存储: region={}", regionId);
    }

    /**
     * 移除一个 Region 的数据存储。
     */
    public void unregisterRegionStore(String regionId) {
        regionStores.remove(regionId);
        logger.info("移除Region存储: region={}", regionId);
    }

    /**
     * 处理副本的日志拉取请求 —— 从主节点的 WAL 中读取增量日志，流式发送给副本。
     *
     * 流程：
     * 1. 根据请求中的 regionId + startSequence 从 WAL 查询增量日志
     * 2. 将每条 WalRecord 转换为 ReplicationLogEntry（proto格式）
     * 3. 通过 StreamObserver 逐条发送给副本
     * 4. 如果指定了 batchSize，每批发送后暂停
     *
     * @param request          副本的拉取请求（包含 region、起始序列号等）
     * @param responseObserver 流式响应观察者，逐条发送日志
     */
    public void handleGetReplicationLog(GetReplicationLogRequest request,
                                        StreamObserver<ReplicationLogEntry> responseObserver) {
        String regionId = request.getRegionId();
        long startSequence = request.getStartSequence();
        long endSequence = request.getEndSequence();
        int batchSize = request.getBatchSize() > 0 ? request.getBatchSize() : 100;

        logger.info("副本日志拉取: region={}, start={}, end={}, batchSize={}",
                regionId, startSequence, endSequence, batchSize);

        try {
            // 如果没指定结束序列号，取到最新
            if (endSequence <= 0) {
                endSequence = walService.getLatestSequenceId(regionId);
            }

            // 从 WAL 中读取增量日志
            List<WalRecord> records = walService.getRecords(regionId, startSequence, endSequence);

            int sent = 0;
            for (WalRecord record : records) {
                // 将内部 WalRecord 转换为 proto 格式
                ReplicationLogEntry entry = convertToProto(record);
                responseObserver.onNext(entry);
                sent++;

                // 批次控制：每发送 batchSize 条后稍作暂停，给网络和接收方缓冲时间
                if (sent % batchSize == 0 && sent < records.size()) {
                    try {
                        Thread.sleep(1);  // 1ms 暂停，防止发送方过快压倒接收方
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            logger.info("副本日志拉取完成: region={}, 发送{}条, seq={}..{}",
                    regionId, sent, startSequence, endSequence);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("副本日志拉取失败: region={}", regionId, e);
            responseObserver.onError(e);
        }
    }

    /**
     * 处理副本的日志应用请求 —— 副本收到日志后，校验并应用到本地。
     *
     * 流程：
     * 1. 逐条校验 checksum（拒绝损坏的数据）
     * 2. 根据操作类型（PUT/DELETE）应用到本地数据存储
     * 3. 写入本地 WAL（副本也有自己的 WAL，用于自身故障恢复）
     * 4. 返回应用结果（成功数量、最后应用的序列号）
     *
     * @param request          副本发送的日志应用请求
     * @param responseObserver 响应观察者
     */
    public void handleApplyReplicationLog(ApplyReplicationLogRequest request,
                                          StreamObserver<ApplyReplicationLogResponse> responseObserver) {
        String regionId = request.getRegionId();
        List<ReplicationLogEntry> logs = request.getLogsList();

        // ---- Paxos 协议消息拦截 ----
        if (regionId.startsWith(PAXOS_PREPARE_PREFIX)) {
            handlePaxosPrepare(request, responseObserver);
            return;
        }
        if (regionId.startsWith(PAXOS_ACCEPT_PREFIX)) {
            handlePaxosAccept(request, responseObserver);
            return;
        }
        if (regionId.startsWith(PAXOS_COMMIT_PREFIX)) {
            handlePaxosCommit(request, responseObserver);
            return;
        }

        logger.info("应用副本日志: region={}, 共{}条", regionId, logs.size());

        int applied = 0;
        long lastAppliedSeq = 0;
        List<String> errors = new ArrayList<>();

        RegionDataStore store = regionStores.get(regionId);

        for (ReplicationLogEntry logEntry : logs) {
            try {
                // 步骤1：校验 checksum
                if (!validateChecksum(logEntry)) {
                    errors.add("序列号" + logEntry.getSequenceId() + "的日志校验失败，数据可能已损坏");
                    continue;
                }

                // 步骤2：应用到本地数据存储
                if (store != null) {
                    applyLogToStore(store, logEntry);
                }

                // 步骤3：写入本地 WAL
                String operation = logEntry.getOperation() == ReplicationLogEntry.OperationType.PUT
                        ? "PUT" : "DELETE";
                Map<String, byte[]> columnsMap = new HashMap<>();
                for (Map.Entry<String, ByteString> entry : logEntry.getColumnsMap().entrySet()) {
                    columnsMap.put(entry.getKey(), entry.getValue().toByteArray());
                }

                walService.append(
                        logEntry.getRegionId(),
                        "", // tableName 从上下文推断
                        operation,
                        logEntry.getKey().toByteArray(),
                        columnsMap
                );

                applied++;
                lastAppliedSeq = logEntry.getSequenceId();

            } catch (Exception e) {
                logger.error("应用日志失败: seq={}", logEntry.getSequenceId(), e);
                errors.add("序列号" + logEntry.getSequenceId() + "应用失败: " + e.getMessage());
            }
        }

        boolean success = errors.isEmpty();
        ApplyReplicationLogResponse response = ApplyReplicationLogResponse.newBuilder()
                .setSuccess(success)
                .setAppliedCount(applied)
                .setLastAppliedSequence(lastAppliedSeq)
                .setErrorCode(success ? ErrorCode.ERROR_OK : ErrorCode.ERROR_INTERNAL)
                .setErrorMessage(success ? "" : String.join("; ", errors))
                .build();

        logger.info("应用副本日志完成: region={}, 成功{}/{}条, lastSeq={}",
                regionId, applied, logs.size(), lastAppliedSeq);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * 将内部 WalRecord 转换为 gRPC proto 格式的 ReplicationLogEntry。
     *
     * 为什么要转换？WalRecord 是内部 Java 对象，ReplicationLogEntry 是 gRPC 传输格式。
     * gRPC 使用 protobuf 序列化，跨语言兼容，适合网络传输。
     */
    private ReplicationLogEntry convertToProto(WalRecord record) {
        ReplicationLogEntry.Builder builder = ReplicationLogEntry.newBuilder()
                .setSequenceId(record.getSequenceId())
                .setRegionId(record.getRegionId())
                .setTimestamp(record.getTimestamp())
                .setKey(ByteString.copyFrom(record.getKey()));

        // 操作类型映射
        if ("PUT".equalsIgnoreCase(record.getOperation())) {
            builder.setOperation(ReplicationLogEntry.OperationType.PUT);
        } else if ("DELETE".equalsIgnoreCase(record.getOperation())) {
            builder.setOperation(ReplicationLogEntry.OperationType.DELETE);
        }

        // 列数据
        Map<String, ByteString> columns = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : record.getColumns().entrySet()) {
            columns.put(entry.getKey(), ByteString.copyFrom(entry.getValue()));
        }
        builder.putAllColumns(columns);

        // 校验和
        if (record.getChecksum() != null) {
            builder.setChecksum(ByteString.copyFrom(record.getChecksum()));
        }

        return builder.build();
    }

    /**
     * 校验日志条目的 checksum。
     *
     * 将日志中除 checksum 外的字段重新计算 MD5，与携带的 checksum 比对。
     * 如果不同，说明数据在传输或存储过程中损坏，拒绝应用。
     */
    private boolean validateChecksum(ReplicationLogEntry entry) {
        if (entry.getChecksum().isEmpty()) {
            // 没有校验和，可能是旧格式数据，跳过校验
            logger.warn("日志 seq={} 没有校验和，跳过校验", entry.getSequenceId());
            return true;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(Long.toString(entry.getSequenceId()).getBytes());
            md.update(entry.getRegionId().getBytes());
            // 操作类型
            String opStr = entry.getOperation() == ReplicationLogEntry.OperationType.PUT ? "PUT" : "DELETE";
            md.update(opStr.getBytes());
            md.update(entry.getKey().toByteArray());
            for (Map.Entry<String, ByteString> col : entry.getColumnsMap().entrySet()) {
                md.update(col.getKey().getBytes());
                md.update(col.getValue().toByteArray());
            }
            byte[] computed = md.digest();
            byte[] expected = entry.getChecksum().toByteArray();

            return Arrays.equals(computed, expected);
        } catch (NoSuchAlgorithmException e) {
            // MD5 是所有 JVM 必需算法
            throw new IllegalStateException("MD5算法不可用", e);
        }
    }

    /**
     * 将一条日志条目应用到本地数据存储。
     *
     * - PUT 操作：将 key + columns 写入存储
     * - DELETE 操作：从存储中删除 key
     */
    private void applyLogToStore(RegionDataStore store, ReplicationLogEntry entry) {
        byte[] key = entry.getKey().toByteArray();
        long timestamp = entry.getTimestamp();

        if (entry.getOperation() == ReplicationLogEntry.OperationType.PUT) {
            Map<String, ByteString> columns = new HashMap<>(entry.getColumnsMap());
            store.upsert(key, columns, timestamp);
            logger.debug("副本应用PUT: key={}", bytesToHex(key));
        } else if (entry.getOperation() == ReplicationLogEntry.OperationType.DELETE) {
            store.delete(key);
            logger.debug("副本应用DELETE: key={}", bytesToHex(key));
        }
    }

    /**
     * 处理 Paxos Prepare 消息。
     * 从请求中提取提案编号，调用 Acceptor 判断是否承诺。
     */
    private void handlePaxosPrepare(ApplyReplicationLogRequest request,
                                    StreamObserver<ApplyReplicationLogResponse> responseObserver) {
        try {
            String regionId = request.getRegionId().substring(PAXOS_PREPARE_PREFIX.length());
            ReplicationLogEntry entry = request.getLogs(0);
            long seqNum = entry.getSequenceId();
            String serverId = entry.getKey().toStringUtf8();

            PaxosTypes.ProposalNumber proposalNumber = new PaxosTypes.ProposalNumber(seqNum, serverId);
            PaxosTypes.PromiseResponse promise = paxosAcceptor.handlePrepare(
                    new PaxosTypes.PrepareRequest(proposalNumber, regionId));

            ApplyReplicationLogResponse response = ApplyReplicationLogResponse.newBuilder()
                    .setSuccess(promise.isPromised())
                    .setAppliedCount(promise.isPromised() ? 1 : 0)
                    .setLastAppliedSequence(seqNum)
                    .setErrorCode(promise.isPromised() ? ErrorCode.ERROR_OK : ErrorCode.ERROR_ABORTED)
                    .build();

            logger.debug("Paxos Prepare 处理: region={}, proposal={}, promised={}",
                    regionId, proposalNumber, promise.isPromised());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Paxos Prepare 处理异常", e);
            responseObserver.onError(e);
        }
    }

    /**
     * 处理 Paxos Accept 消息。
     * 从请求中提取值，调用 Acceptor 判断是否接受。
     */
    private void handlePaxosAccept(ApplyReplicationLogRequest request,
                                   StreamObserver<ApplyReplicationLogResponse> responseObserver) {
        try {
            String regionId = request.getRegionId().substring(PAXOS_ACCEPT_PREFIX.length());
            ReplicationLogEntry entry = request.getLogs(0);
            long seqNum = entry.getSequenceId();
            String serverId = entry.getKey().toStringUtf8();

            // 从 columns 中提取 Paxos 值
            ByteString paxosValue = entry.getColumnsMap().get("__paxos_value__");
            byte[] value = paxosValue != null ? paxosValue.toByteArray() : new byte[0];

            PaxosTypes.ProposalNumber proposalNumber = new PaxosTypes.ProposalNumber(seqNum, serverId);
            PaxosTypes.AcceptedResponse accepted = paxosAcceptor.handleAccept(
                    new PaxosTypes.AcceptRequest(proposalNumber, regionId, value));

            ApplyReplicationLogResponse response = ApplyReplicationLogResponse.newBuilder()
                    .setSuccess(accepted.isAccepted())
                    .setAppliedCount(accepted.isAccepted() ? 1 : 0)
                    .setLastAppliedSequence(seqNum)
                    .setErrorCode(accepted.isAccepted() ? ErrorCode.ERROR_OK : ErrorCode.ERROR_ABORTED)
                    .build();

            logger.debug("Paxos Accept 处理: region={}, proposal={}, accepted={}",
                    regionId, proposalNumber, accepted.isAccepted());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Paxos Accept 处理异常", e);
            responseObserver.onError(e);
        }
    }

    /**
     * 处理 Paxos Commit 消息。
     * 调用 Acceptor 标记提交，然后将已接受的值应用到数据存储。
     */
    private void handlePaxosCommit(ApplyReplicationLogRequest request,
                                   StreamObserver<ApplyReplicationLogResponse> responseObserver) {
        try {
            String regionId = request.getRegionId().substring(PAXOS_COMMIT_PREFIX.length());
            ReplicationLogEntry entry = request.getLogs(0);
            long seqNum = entry.getSequenceId();
            String serverId = entry.getKey().toStringUtf8();

            PaxosTypes.ProposalNumber proposalNumber = new PaxosTypes.ProposalNumber(seqNum, serverId);
            boolean committed = paxosAcceptor.handleCommit(
                    new PaxosTypes.CommitRequest(proposalNumber, regionId));

            // 提交成功后，将已接受的值应用到数据存储
            if (committed) {
                byte[] value = paxosAcceptor.getCommittedValue(regionId);
                if (value != null) {
                    // 写入本地 WAL
                    walService.append(regionId, "", "COMMIT",
                            proposalNumber.toString().getBytes(), new HashMap<>());
                }
            }

            ApplyReplicationLogResponse response = ApplyReplicationLogResponse.newBuilder()
                    .setSuccess(committed)
                    .setAppliedCount(committed ? 1 : 0)
                    .setLastAppliedSequence(seqNum)
                    .setErrorCode(committed ? ErrorCode.ERROR_OK : ErrorCode.ERROR_ABORTED)
                    .build();

            logger.debug("Paxos Commit 处理: region={}, proposal={}, committed={}",
                    regionId, proposalNumber, committed);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Paxos Commit 处理异常", e);
            responseObserver.onError(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
