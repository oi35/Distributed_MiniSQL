package com.minisql.regionserver.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paxos Acceptor（投票者）—— 运行在每个 RegionServer 上的投票逻辑。
 *
 * 每个 Acceptor 独立投票，互不影响。它的三个承诺：
 * 1. Prepare阶段：承诺不接收比当前 proposalNumber 更小的提案
 * 2. Accept阶段： 如果 proposalNumber >= 已承诺的编号，则接受该提案
 * 3. Commit阶段： 将已接受的提案应用到状态机
 *
 * Acceptor 的状态是持久的（通过跟踪已接受的最高提案编号保证一致性）
 *
 * 线程安全：使用 per-region 锁保证同一 region 的 check-then-act 操作原子性。
 */
public class PaxosAcceptor {

    private static final Logger logger = LoggerFactory.getLogger(PaxosAcceptor.class);

    private final String serverId;

    // regionId → 该 region 已承诺的最高提案编号
    private final Map<String, PaxosTypes.ProposalNumber> highestPromised;

    // regionId → 该 region 最后接受的提案编号
    private final Map<String, PaxosTypes.ProposalNumber> lastAcceptedProposal;

    // regionId → 该 region 最后接受的值
    private final Map<String, byte[]> lastAcceptedValue;

    // regionId → 该 region 最后提交的提案编号
    private final Map<String, PaxosTypes.ProposalNumber> lastCommittedProposal;

    // per-region 锁，保证 check-then-act 操作的原子性
    private final Map<String, Object> regionLocks;

    public PaxosAcceptor(String serverId) {
        this.serverId = serverId;
        this.highestPromised = new ConcurrentHashMap<>();
        this.lastAcceptedProposal = new ConcurrentHashMap<>();
        this.lastAcceptedValue = new ConcurrentHashMap<>();
        this.lastCommittedProposal = new ConcurrentHashMap<>();
        this.regionLocks = new ConcurrentHashMap<>();
        logger.info("PaxosAcceptor 初始化完成: server={}", serverId);
    }

    private Object getRegionLock(String regionId) {
        return regionLocks.computeIfAbsent(regionId, k -> new Object());
    }

    /**
     * 处理 Prepare 请求。
     *
     * 规则：如果传入的提案编号 > 已承诺的最高编号，则承诺（返回 Promise），
     * 并且附带上次已接受的值（如果有的话）。否则拒绝。
     */
    public PaxosTypes.PromiseResponse handlePrepare(PaxosTypes.PrepareRequest request) {
        String regionId = request.getRegionId();
        PaxosTypes.ProposalNumber proposalNumber = request.getProposalNumber();

        synchronized (getRegionLock(regionId)) {
            PaxosTypes.ProposalNumber currentHighest = highestPromised.get(regionId);

            if (currentHighest == null || proposalNumber.compareTo(currentHighest) > 0) {
                highestPromised.put(regionId, proposalNumber);

                PaxosTypes.ProposalNumber lastAccepted = lastAcceptedProposal.get(regionId);
                byte[] lastValue = lastAcceptedValue.get(regionId);

                logger.debug("Prepare 承诺: region={}, proposal={}", regionId, proposalNumber);
                return new PaxosTypes.PromiseResponse(true, proposalNumber, lastAccepted, lastValue);
            } else {
                logger.debug("Prepare 拒绝: region={}, proposal={}, 已承诺={}",
                        regionId, proposalNumber, currentHighest);
                return new PaxosTypes.PromiseResponse(false, proposalNumber, null, null);
            }
        }
    }

    /**
     * 处理 Accept 请求。
     *
     * 规则：如果 proposalNumber >= 已承诺的最高编号，则接受该值。
     */
    public PaxosTypes.AcceptedResponse handleAccept(PaxosTypes.AcceptRequest request) {
        String regionId = request.getRegionId();
        PaxosTypes.ProposalNumber proposalNumber = request.getProposalNumber();

        synchronized (getRegionLock(regionId)) {
            PaxosTypes.ProposalNumber currentHighest = highestPromised.get(regionId);

            if (currentHighest == null || proposalNumber.compareTo(currentHighest) >= 0) {
                lastAcceptedProposal.put(regionId, proposalNumber);
                lastAcceptedValue.put(regionId, request.getValue());
                highestPromised.put(regionId, proposalNumber);

                logger.debug("Accept 接受: region={}, proposal={}", regionId, proposalNumber);
                return new PaxosTypes.AcceptedResponse(true, proposalNumber);
            } else {
                logger.debug("Accept 拒绝: region={}, proposal={}, 已承诺={}",
                        regionId, proposalNumber, currentHighest);
                return new PaxosTypes.AcceptedResponse(false, proposalNumber);
            }
        }
    }

    /**
     * 处理 Commit 请求。
     *
     * 提案已获得多数票，通知 Acceptor 将值标记为已提交。
     * 只有之前已接受的提案才能被提交。
     */
    public boolean handleCommit(PaxosTypes.CommitRequest request) {
        String regionId = request.getRegionId();
        PaxosTypes.ProposalNumber proposalNumber = request.getProposalNumber();

        synchronized (getRegionLock(regionId)) {
            PaxosTypes.ProposalNumber lastAccepted = lastAcceptedProposal.get(regionId);

            if (lastAccepted != null && lastAccepted.compareTo(proposalNumber) == 0) {
                lastCommittedProposal.put(regionId, proposalNumber);
                logger.info("Commit 完成: region={}, proposal={}", regionId, proposalNumber);
                return true;
            }

            logger.warn("Commit 失败（未事先接受）: region={}, proposal={}, lastAccepted={}",
                    regionId, proposalNumber, lastAccepted);
            return false;
        }
    }

    /**
     * 获取最后已提交的值（用于故障恢复时查询）。
     */
    public byte[] getCommittedValue(String regionId) {
        synchronized (getRegionLock(regionId)) {
            PaxosTypes.ProposalNumber committed = lastCommittedProposal.get(regionId);
            if (committed == null) {
                return null;
            }

            PaxosTypes.ProposalNumber accepted = lastAcceptedProposal.get(regionId);
            if (accepted != null && accepted.compareTo(committed) >= 0) {
                return lastAcceptedValue.get(regionId);
            }
            return null;
        }
    }

    /**
     * 获取最后提交的提案编号。
     */
    public PaxosTypes.ProposalNumber getLastCommittedProposal(String regionId) {
        synchronized (getRegionLock(regionId)) {
            return lastCommittedProposal.get(regionId);
        }
    }

    /**
     * 重置某个 region 的状态（region 关闭时调用）。
     */
    public void resetRegion(String regionId) {
        synchronized (getRegionLock(regionId)) {
            highestPromised.remove(regionId);
            lastAcceptedProposal.remove(regionId);
            lastAcceptedValue.remove(regionId);
            lastCommittedProposal.remove(regionId);
        }
        logger.info("PaxosAcceptor 重置 region: {}", regionId);
    }
}
