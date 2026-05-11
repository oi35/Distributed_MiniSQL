package com.minisql.regionserver.replication;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PaxosAcceptorTest {

    private PaxosAcceptor acceptor;

    @Before
    public void setUp() {
        acceptor = new PaxosAcceptor("test-rs");
    }

    @Test
    public void testAcceptRejectedWhenLowerThanPromised() {
        PaxosTypes.ProposalNumber pn10 = new PaxosTypes.ProposalNumber(10, "rs-001");
        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn10, "region-1"));

        // 尝试用更小编号accept
        PaxosTypes.ProposalNumber pn5 = new PaxosTypes.ProposalNumber(5, "rs-002");
        PaxosTypes.AcceptedResponse resp = acceptor.handleAccept(
                new PaxosTypes.AcceptRequest(pn5, "region-1", "data".getBytes()));

        assertFalse("比承诺编号小的Accept应被拒绝", resp.isAccepted());
    }

    @Test
    public void testAcceptSucceedsWithEqualProposalNumber() {
        PaxosTypes.ProposalNumber pn5 = new PaxosTypes.ProposalNumber(5, "rs-001");
        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn5, "region-1"));

        PaxosTypes.AcceptedResponse resp = acceptor.handleAccept(
                new PaxosTypes.AcceptRequest(pn5, "region-1", "data".getBytes()));

        assertTrue("相同编号的Accept应成功", resp.isAccepted());
    }

    @Test
    public void testCommitOnlyWhenExactMatch() {
        PaxosTypes.ProposalNumber pn1 = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.ProposalNumber pn2 = new PaxosTypes.ProposalNumber(2, "rs-001");

        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn1, "region-1"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn1, "region-1", "v1".getBytes()));

        // 用不同的编号commit
        boolean committed = acceptor.handleCommit(
                new PaxosTypes.CommitRequest(pn2, "region-1"));
        assertFalse("用未Accept的编号Commit应失败", committed);

        // 用正确的编号commit
        committed = acceptor.handleCommit(new PaxosTypes.CommitRequest(pn1, "region-1"));
        assertTrue("正确编号Commit应成功", committed);
    }

    @Test
    public void testGetCommittedValueAfterCommit() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        byte[] value = "important-data".getBytes();

        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn, "region-1"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn, "region-1", value));
        acceptor.handleCommit(new PaxosTypes.CommitRequest(pn, "region-1"));

        byte[] committed = acceptor.getCommittedValue("region-1");
        assertNotNull("已提交的值应存在", committed);
        assertArrayEquals("已提交的值应匹配", value, committed);
    }

    @Test
    public void testGetCommittedValueNullWithoutCommit() {
        byte[] value = acceptor.getCommittedValue("region-nonexistent");
        assertNull("未提交的region应返回null", value);
    }

    @Test
    public void testGetLastCommittedProposal() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(5, "rs-001");

        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn, "region-1"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn, "region-1", "data".getBytes()));
        acceptor.handleCommit(new PaxosTypes.CommitRequest(pn, "region-1"));

        PaxosTypes.ProposalNumber last = acceptor.getLastCommittedProposal("region-1");
        assertNotNull("最后提交的提案应存在", last);
        assertEquals("序列号应匹配", 5, last.getSequenceNum());
        assertEquals("serverId应匹配", "rs-001", last.getServerId());
    }

    @Test
    public void testResetRegionClearsAllState() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");

        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn, "region-1"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn, "region-1", "data".getBytes()));
        acceptor.handleCommit(new PaxosTypes.CommitRequest(pn, "region-1"));

        acceptor.resetRegion("region-1");

        assertNull("重置后已提交值应为null",
                acceptor.getCommittedValue("region-1"));
        assertNull("重置后最后提交提案应为null",
                acceptor.getLastCommittedProposal("region-1"));
    }

    @Test
    public void testConcurrentRegionsIndependent() {
        PaxosTypes.ProposalNumber pn1 = new PaxosTypes.ProposalNumber(10, "rs-001");
        PaxosTypes.ProposalNumber pn2 = new PaxosTypes.ProposalNumber(20, "rs-002");

        // region-1 gets pn10
        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn1, "region-1"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn1, "region-1", "r1".getBytes()));

        // region-2 gets pn20 (different proposer)
        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn2, "region-2"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn2, "region-2", "r2".getBytes()));

        // Both should commit independently
        assertTrue("region-1应能提交", acceptor.handleCommit(
                new PaxosTypes.CommitRequest(pn1, "region-1")));
        assertTrue("region-2应能提交", acceptor.handleCommit(
                new PaxosTypes.CommitRequest(pn2, "region-2")));

        assertArrayEquals("region-1的提交值", "r1".getBytes(),
                acceptor.getCommittedValue("region-1"));
        assertArrayEquals("region-2的提交值", "r2".getBytes(),
                acceptor.getCommittedValue("region-2"));
    }

    @Test
    public void testPrepareResponseCarriesLastAccepted() {
        PaxosTypes.ProposalNumber pn1 = new PaxosTypes.ProposalNumber(1, "rs-001");
        byte[] v1 = "first-value".getBytes();

        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn1, "region-1"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn1, "region-1", v1));

        // New proposer with higher number gets the previously accepted value
        PaxosTypes.ProposalNumber pn2 = new PaxosTypes.ProposalNumber(2, "rs-002");
        PaxosTypes.PromiseResponse resp = acceptor.handlePrepare(
                new PaxosTypes.PrepareRequest(pn2, "region-1"));

        assertTrue("应被承诺", resp.isPromised());
        assertNotNull("应携带之前接受的值", resp.getLastAcceptedValue());
        assertArrayEquals("携带的值应正确", v1, resp.getLastAcceptedValue());
        assertNotNull("应携带之前接受的提案编号", resp.getLastAcceptedProposal());
    }
}
