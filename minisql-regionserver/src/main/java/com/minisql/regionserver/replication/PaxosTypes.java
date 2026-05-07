package com.minisql.regionserver.replication;

/**
 * Paxos 协议的类型定义。
 *
 * 协议角色：
 * - Proposer（提案者）：发起写入请求，驱动共识流程
 * - Acceptor（投票者）：对提案进行投票，只有多数通过时提案才生效
 *
 * 三阶段流程：
 *   Proposer                     Acceptors (多个)
 *      |                              |
 *      |---Prepare(N)---→             |  阶段1：准备
 *      |←--Promise(N)----             |  "我承诺不再接受 < N 的提案"
 *      |                              |
 *      |---Accept(N, V)--→            |  阶段2：接受
 *      |←--Accepted(N)---             |  "我接受了编号为 N 的提案"
 *      |                              |
 *      |---Commit(N)-----→            |  阶段3：提交
 *      |                              |  将值应用到状态机
 *
 * N = 提案编号（提案编号越大，优先级越高）
 * V = 提案的值（即要写入的数据）
 */
public final class PaxosTypes {

    private PaxosTypes() {}

    /** 提案编号（单调递增，全局唯一） */
    public static class ProposalNumber implements Comparable<ProposalNumber> {
        private final long sequenceNum;
        private final String serverId;

        public ProposalNumber(long sequenceNum, String serverId) {
            this.sequenceNum = sequenceNum;
            this.serverId = serverId;
        }

        public long getSequenceNum() { return sequenceNum; }
        public String getServerId() { return serverId; }

        @Override
        public int compareTo(ProposalNumber other) {
            int cmp = Long.compare(this.sequenceNum, other.sequenceNum);
            if (cmp != 0) return cmp;
            return this.serverId.compareTo(other.serverId);
        }

        @Override
        public String toString() {
            return serverId + ":" + sequenceNum;
        }
    }

    /** Prepare 阶段：提案者发出准备请求 */
    public static class PrepareRequest {
        private final ProposalNumber proposalNumber;
        private final String regionId;

        public PrepareRequest(ProposalNumber proposalNumber, String regionId) {
            this.proposalNumber = proposalNumber;
            this.regionId = regionId;
        }
        public ProposalNumber getProposalNumber() { return proposalNumber; }
        public String getRegionId() { return regionId; }
    }

    /** Promise 响应：投票者承诺不再接受更低编号的提案 */
    public static class PromiseResponse {
        private final boolean promised;
        private final ProposalNumber proposalNumber;
        private final ProposalNumber lastAcceptedProposal;
        private final byte[] lastAcceptedValue; // 上次已接受的值（可能为null）

        public PromiseResponse(boolean promised, ProposalNumber proposalNumber,
                               ProposalNumber lastAcceptedProposal, byte[] lastAcceptedValue) {
            this.promised = promised;
            this.proposalNumber = proposalNumber;
            this.lastAcceptedProposal = lastAcceptedProposal;
            this.lastAcceptedValue = lastAcceptedValue;
        }
        public boolean isPromised() { return promised; }
        public ProposalNumber getProposalNumber() { return proposalNumber; }
        public ProposalNumber getLastAcceptedProposal() { return lastAcceptedProposal; }
        public byte[] getLastAcceptedValue() { return lastAcceptedValue; }
    }

    /** Accept 阶段：提案者发送要提交的值 */
    public static class AcceptRequest {
        private final ProposalNumber proposalNumber;
        private final String regionId;
        private final byte[] value; // 要写入的数据（序列化后的WalRecord）

        public AcceptRequest(ProposalNumber proposalNumber, String regionId, byte[] value) {
            this.proposalNumber = proposalNumber;
            this.regionId = regionId;
            this.value = value;
        }
        public ProposalNumber getProposalNumber() { return proposalNumber; }
        public String getRegionId() { return regionId; }
        public byte[] getValue() { return value; }
    }

    /** Accepted 响应：投票者接受了提案 */
    public static class AcceptedResponse {
        private final boolean accepted;
        private final ProposalNumber proposalNumber;

        public AcceptedResponse(boolean accepted, ProposalNumber proposalNumber) {
            this.accepted = accepted;
            this.proposalNumber = proposalNumber;
        }
        public boolean isAccepted() { return accepted; }
        public ProposalNumber getProposalNumber() { return proposalNumber; }
    }

    /** Commit 通知：提案获得多数通过，通知投票者将值应用到状态机 */
    public static class CommitRequest {
        private final ProposalNumber proposalNumber;
        private final String regionId;

        public CommitRequest(ProposalNumber proposalNumber, String regionId) {
            this.proposalNumber = proposalNumber;
            this.regionId = regionId;
        }
        public ProposalNumber getProposalNumber() { return proposalNumber; }
        public String getRegionId() { return regionId; }
    }

    /** 共识结果 */
    public enum ConsensusResult {
        COMMITTED,   // 达成共识并已提交
        REJECTED,    // 提案被拒绝
        TIMEOUT      // 超时未达成共识
    }
}
