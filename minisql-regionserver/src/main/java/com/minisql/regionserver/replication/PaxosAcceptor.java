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
 */
public class PaxosAcceptor {

    private static final Logger logger = LoggerFactory.getLogger(PaxosAcceptor.class);

    private final String serverId;

    // 每个 region 有一个独立的 Paxos 实例
    // regionId → 该 region 已承诺的最高提案编号
    private final Map<String, PaxosTypes.ProposalNumber> highestPromised;

    // regionId → 该 region 最后接受的提案编号
    private final Map<String, PaxosTypes.ProposalNumber> lastAcceptedProposal;

    // regionId → 该 region 最后接受的值
    private final Map<String, byte[]> lastAcceptedValue;

    // regionId → 该 region 最后提交的提案编号
    private final Map<String, PaxosTypes.ProposalNumber> lastCommittedProposal;

    public PaxosAcceptor(String serverId) {
        this.serverId = serverId;
        this.highestPromised = new ConcurrentHashMap<>();
        this.lastAcceptedProposal = new ConcurrentHashMap<>();
        this.lastAcceptedValue = new ConcurrentHashMap<>();
        this.lastCommittedProposal = new ConcurrentHashMap<>();
        logger.info("PaxosAcceptor 初始化完成: server={}", serverId);
    }

    /**
     * 处理 Prepare 请求。
     *
     * 规则：如果传入的提案编号 > 已承诺的最高编号，则承诺（返回 Promise），
     * 并且附带上次已接受的值（如果有的话）。否则拒绝。
     *
     * 为什么附带上次已接受的值？
     * 如果之前有一个提案已经进入了 Accept 阶段但没完成 Commit（比如
     * 提案者故障了），新提案者需要继承这个值，保证一致性。
     */
    public PaxosTypes.PromiseResponse handlePrepare(PaxosTypes.PrepareRequest request) {
        String regionId = request.getRegionId();
        PaxosTypes.ProposalNumber proposalNumber = request.getProposalNumber();

        PaxosTypes.ProposalNumber currentHighest = highestPromised.get(regionId);

        // 如果这是第一个提案，或者新提案编号大于已承诺的
        if (currentHighest == null || proposalNumber.compareTo(currentHighest) > 0) {
            // 记录新的承诺
            highestPromised.put(regionId, proposalNumber);

            // 检查是否有之前已接受但可能未提交的值
            PaxosTypes.ProposalNumber lastAccepted = lastAcceptedProposal.get(regionId);
            byte[] lastValue = lastAcceptedValue.get(regionId);

            logger.debug("Prepare 承诺: region={}, proposal={}", regionId, proposalNumber);
            return new PaxosTypes.PromiseResponse(true, proposalNumber, lastAccepted, lastValue);
        } else {
            // 拒绝：已经有更高的提案编号被承诺
            logger.debug("Prepare 拒绝: region={}, proposal={}, 已承诺={}",
                    regionId, proposalNumber, currentHighest);
            return new PaxosTypes.PromiseResponse(false, proposalNumber, null, null);
        }
    }

    /**
     * 处理 Accept 请求。
     *
     * 规则：如果 proposalNumber >= 已承诺的最高编号，则接受该值。
     *
     * 注意：用的是 >= 而不是 >，因为同一个提案可能重试。
     */
    public PaxosTypes.AcceptedResponse handleAccept(PaxosTypes.AcceptRequest request) {
        String regionId = request.getRegionId();
        PaxosTypes.ProposalNumber proposalNumber = request.getProposalNumber();

        PaxosTypes.ProposalNumber currentHighest = highestPromised.get(regionId);

        if (currentHighest == null || proposalNumber.compareTo(currentHighest) >= 0) {
            // 接受该提案
            lastAcceptedProposal.put(regionId, proposalNumber);
            lastAcceptedValue.put(regionId, request.getValue());
            highestPromised.put(regionId, proposalNumber);

            logger.debug("Accept 接受: region={}, proposal={}", regionId, proposalNumber);
            return new PaxosTypes.AcceptedResponse(true, proposalNumber);
        } else {
            // 拒绝：我们已经承诺了更高的提案编号
            logger.debug("Accept 拒绝: region={}, proposal={}, 已承诺={}",
                    regionId, proposalNumber, currentHighest);
            return new PaxosTypes.AcceptedResponse(false, proposalNumber);
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

        PaxosTypes.ProposalNumber lastAccepted = lastAcceptedProposal.get(regionId);

        // 只能提交我们已接受的提案
        if (lastAccepted != null && lastAccepted.compareTo(proposalNumber) >= 0) {
            lastCommittedProposal.put(regionId, proposalNumber);
            logger.info("Commit 完成: region={}, proposal={}", regionId, proposalNumber);
            return true;
        }

        logger.warn("Commit 失败（未事先接受）: region={}, proposal={}, lastAccepted={}",
                regionId, proposalNumber, lastAccepted);
        return false;
    }

    /**
     * 获取最后已提交的值（用于故障恢复时查询）。
     */
    public byte[] getCommittedValue(String regionId) {
        PaxosTypes.ProposalNumber committed = lastCommittedProposal.get(regionId);
        if (committed == null) {
            return null;
        }

        PaxosTypes.ProposalNumber accepted = lastAcceptedProposal.get(regionId);
        // 已提交且已接受的提案中存储的值
        if (accepted != null && accepted.compareTo(committed) >= 0) {
            return lastAcceptedValue.get(regionId);
        }
        return null;
    }

    /**
     * 获取最后提交的提案编号。
     */
    public PaxosTypes.ProposalNumber getLastCommittedProposal(String regionId) {
        return lastCommittedProposal.get(regionId);
    }

    /**
     * 重置某个 region 的状态（region 关闭时调用）。
     */
    public void resetRegion(String regionId) {
        highestPromised.remove(regionId);
        lastAcceptedProposal.remove(regionId);
        lastAcceptedValue.remove(regionId);
        lastCommittedProposal.remove(regionId);
        logger.info("PaxosAcceptor 重置 region: {}", regionId);
    }
}
