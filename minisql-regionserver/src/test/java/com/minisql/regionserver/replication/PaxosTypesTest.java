package com.minisql.regionserver.replication;

import org.junit.Test;

import static org.junit.Assert.*;

public class PaxosTypesTest {

    @Test
    public void testProposalNumberCompareBySequence() {
        PaxosTypes.ProposalNumber pn1 = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.ProposalNumber pn2 = new PaxosTypes.ProposalNumber(2, "rs-001");
        PaxosTypes.ProposalNumber pn3 = new PaxosTypes.ProposalNumber(2, "rs-002");

        assertTrue("pn1 < pn2（序列号更小）", pn1.compareTo(pn2) < 0);
        assertTrue("pn2 > pn1（序列号更大）", pn2.compareTo(pn1) > 0);
        // 相同序列号但不同serverId：按serverId字典序比较
        assertTrue("pn2 < pn3（相同seq按serverId字典序）", pn2.compareTo(pn3) < 0);
    }

    @Test
    public void testProposalNumberCompareSameSequenceDifferentServer() {
        PaxosTypes.ProposalNumber pnA = new PaxosTypes.ProposalNumber(5, "rs-A");
        PaxosTypes.ProposalNumber pnB = new PaxosTypes.ProposalNumber(5, "rs-B");

        // 相同seq时按serverId字典序
        assertTrue("相同seq时应按serverId比较", pnA.compareTo(pnB) < 0);
    }

    @Test
    public void testProposalNumberToString() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(42, "rs-master");
        assertEquals("rs-master:42", pn.toString());
    }

    @Test
    public void testPrepareRequestFields() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.PrepareRequest req = new PaxosTypes.PrepareRequest(pn, "region-1");

        assertEquals("proposalNumber应匹配", pn, req.getProposalNumber());
        assertEquals("regionId应匹配", "region-1", req.getRegionId());
    }

    @Test
    public void testPromiseResponseFields() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.ProposalNumber lastP = new PaxosTypes.ProposalNumber(0, "rs-000");
        byte[] value = "test".getBytes();

        PaxosTypes.PromiseResponse resp = new PaxosTypes.PromiseResponse(true, pn, lastP, value);

        assertTrue("应被承诺", resp.isPromised());
        assertEquals("提案编号应匹配", pn, resp.getProposalNumber());
        assertEquals("最后接受提案应匹配", lastP, resp.getLastAcceptedProposal());
        assertArrayEquals("最后接受值应匹配", value, resp.getLastAcceptedValue());
    }

    @Test
    public void testPromiseResponseNotPromised() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.PromiseResponse resp = new PaxosTypes.PromiseResponse(false, pn, null, null);

        assertFalse("不应被承诺", resp.isPromised());
        assertNull("未承诺时lastAcceptedProposal应为null", resp.getLastAcceptedProposal());
        assertNull("未承诺时lastAcceptedValue应为null", resp.getLastAcceptedValue());
    }

    @Test
    public void testAcceptRequestFields() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        byte[] value = "payload".getBytes();
        PaxosTypes.AcceptRequest req = new PaxosTypes.AcceptRequest(pn, "region-2", value);

        assertEquals("提案编号应匹配", pn, req.getProposalNumber());
        assertEquals("regionId应匹配", "region-2", req.getRegionId());
        assertArrayEquals("值应匹配", value, req.getValue());
    }

    @Test
    public void testAcceptedResponseFields() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.AcceptedResponse resp = new PaxosTypes.AcceptedResponse(true, pn);

        assertTrue("应被接受", resp.isAccepted());
        assertEquals("提案编号应匹配", pn, resp.getProposalNumber());
    }

    @Test
    public void testCommitRequestFields() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.CommitRequest req = new PaxosTypes.CommitRequest(pn, "region-3");

        assertEquals("提案编号应匹配", pn, req.getProposalNumber());
        assertEquals("regionId应匹配", "region-3", req.getRegionId());
    }

    @Test
    public void testConsensusResultValues() {
        assertEquals("COMMITTED枚举", PaxosTypes.ConsensusResult.COMMITTED,
                PaxosTypes.ConsensusResult.valueOf("COMMITTED"));
        assertEquals("REJECTED枚举", PaxosTypes.ConsensusResult.REJECTED,
                PaxosTypes.ConsensusResult.valueOf("REJECTED"));
        assertEquals("TIMEOUT枚举", PaxosTypes.ConsensusResult.TIMEOUT,
                PaxosTypes.ConsensusResult.valueOf("TIMEOUT"));
        assertEquals("应有3个枚举值", 3, PaxosTypes.ConsensusResult.values().length);
    }
}
