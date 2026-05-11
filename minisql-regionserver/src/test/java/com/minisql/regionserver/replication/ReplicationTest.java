package com.minisql.regionserver.replication;

import com.minisql.regionserver.wal.WalRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/**
 * 副本管理 &amp; Paxos 协议 集成测试。
 *
 * 覆盖：
 * - WalService 的增删查及索引功能
 * - ReplicationLogService 的日志转换与校验
 * - PaxosAcceptor 的三阶段投票逻辑
 * - PaxosProposer 的完整共识流程（同进程内，测试 Proposer + Acceptor 交互）
 * - ReplicationManager 的副本列表管理和水印追踪
 */
public class ReplicationTest {

    private Path tempDir;
    private WalService walService;
    private PaxosAcceptor acceptor;
    private PaxosProposer proposer;
    private ReplicationManager replicationManager;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-test-");
        walService = new WalService(tempDir.toString(), "test-rs-001");
        acceptor = new PaxosAcceptor("test-rs-001");
        proposer = new PaxosProposer("test-rs-001");
        replicationManager = new ReplicationManager();
    }

    @After
    public void tearDown() {
        walService.close();
        proposer.close();
        replicationManager.close();
        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    // ==================== WalService 测试 ====================

    @Test
    public void testWalServiceAppendAndRetrieve() {
        Map<String, byte[]> cols1 = new HashMap<>();
        cols1.put("name", "Alice".getBytes());
        cols1.put("age", "25".getBytes());
        WalRecord r1 = walService.append("region-001", "users", "PUT",
                "key-001".getBytes(), cols1);

        Map<String, byte[]> cols2 = new HashMap<>();
        cols2.put("name", "Bob".getBytes());
        WalRecord r2 = walService.append("region-001", "users", "PUT",
                "key-002".getBytes(), cols2);

        Map<String, byte[]> cols3 = new HashMap<>();
        WalRecord r3 = walService.append("region-002", "orders", "DELETE",
                "key-003".getBytes(), cols3);

        assertTrue("序列号应递增", r1.getSequenceId() < r2.getSequenceId());
        assertTrue("序列号应递增", r2.getSequenceId() < r3.getSequenceId());

        assertNotNull("checksum不应为空", r1.getChecksum());
        assertTrue("checksum应有内容", r1.getChecksum().length > 0);

        List<WalRecord> region1Records = walService.getRecords("region-001", 1, 100);
        assertEquals("region-001应有2条记录", 2, region1Records.size());

        List<WalRecord> region2Records = walService.getRecords("region-002", 1, 100);
        assertEquals("region-002应有1条记录", 1, region2Records.size());
    }

    @Test
    public void testWalServiceRangeQuery() {
        for (int i = 0; i < 10; i++) {
            Map<String, byte[]> cols = new HashMap<>();
            cols.put("val", String.valueOf(i).getBytes());
            walService.append("region-001", "test", "PUT",
                    ("key-" + i).getBytes(), cols);
        }

        List<WalRecord> range = walService.getRecords("region-001", 3, 7);
        assertEquals("范围查询应返回5条记录(seq 3-7)", 5, range.size());
        assertEquals("第一条应是seq=3", 3L, range.get(0).getSequenceId());
        assertEquals("最后一条应是seq=7", 7L, range.get(range.size() - 1).getSequenceId());
    }

    @Test
    public void testWalServiceLatestSequenceId() {
        assertEquals("无记录时应返回0", 0L, walService.getLatestSequenceId("region-001"));

        walService.append("region-001", "test", "PUT",
                "key".getBytes(), new HashMap<>());
        long seq = walService.getLatestSequenceId("region-001");
        assertTrue("应有记录", seq > 0);

        walService.append("region-001", "test", "PUT",
                "key2".getBytes(), new HashMap<>());
        long seq2 = walService.getLatestSequenceId("region-001");
        assertTrue("序列号应递增", seq2 > seq);
    }

    @Test
    public void testWalServiceLoadAll() {
        walService.append("region-A", "t1", "PUT", "k1".getBytes(), new HashMap<>());
        walService.append("region-B", "t2", "DELETE", "k2".getBytes(), new HashMap<>());
        walService.append("region-A", "t1", "PUT", "k3".getBytes(), new HashMap<>());

        List<WalRecord> all = walService.loadAll();
        assertEquals("loadAll应返回3条记录", 3, all.size());
    }

    // ==================== PaxosAcceptor 测试 ====================

    @Test
    public void testAcceptorPreparePromise() {
        PaxosTypes.ProposalNumber pn1 = new PaxosTypes.ProposalNumber(1, "rs-001");
        PaxosTypes.PromiseResponse resp1 = acceptor.handlePrepare(
                new PaxosTypes.PrepareRequest(pn1, "region-001"));

        assertTrue("第一个Prepare应该被承诺", resp1.isPromised());
    }

    @Test
    public void testAcceptorRejectLowerProposal() {
        PaxosTypes.ProposalNumber pn10 = new PaxosTypes.ProposalNumber(10, "rs-001");
        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn10, "region-001"));

        PaxosTypes.ProposalNumber pn5 = new PaxosTypes.ProposalNumber(5, "rs-002");
        PaxosTypes.PromiseResponse resp5 = acceptor.handlePrepare(
                new PaxosTypes.PrepareRequest(pn5, "region-001"));

        assertFalse("比已承诺编号小的提案应被拒绝", resp5.isPromised());
    }

    @Test
    public void testAcceptorAcceptAndCommit() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        String regionId = "region-001";

        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn, regionId));

        byte[] value = "test-value".getBytes();
        PaxosTypes.AcceptedResponse acceptResp = acceptor.handleAccept(
                new PaxosTypes.AcceptRequest(pn, regionId, value));
        assertTrue("Accept应该成功", acceptResp.isAccepted());

        boolean committed = acceptor.handleCommit(
                new PaxosTypes.CommitRequest(pn, regionId));
        assertTrue("Commit应该成功", committed);

        byte[] committedValue = acceptor.getCommittedValue(regionId);
        assertNotNull("应有已提交的值", committedValue);
        assertArrayEquals("提交的值应正确", value, committedValue);
    }

    @Test
    public void testAcceptorCannotCommitWithoutAccept() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");

        boolean committed = acceptor.handleCommit(
                new PaxosTypes.CommitRequest(pn, "region-001"));

        assertFalse("未Accept的提案不能被Commit", committed);
    }

    @Test
    public void testAcceptorPromiseCarriesLastAcceptedValue() {
        String regionId = "region-001";

        PaxosTypes.ProposalNumber pn1 = new PaxosTypes.ProposalNumber(1, "rs-001");
        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn1, regionId));
        byte[] value1 = "value-from-proposal-1".getBytes();
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn1, regionId, value1));

        PaxosTypes.ProposalNumber pn2 = new PaxosTypes.ProposalNumber(2, "rs-002");
        PaxosTypes.PromiseResponse resp2 = acceptor.handlePrepare(
                new PaxosTypes.PrepareRequest(pn2, regionId));

        assertTrue("应被承诺", resp2.isPromised());
        assertNotNull("应附带上次已接受的提案编号", resp2.getLastAcceptedProposal());
        assertArrayEquals("应附带上次已接受的值", value1, resp2.getLastAcceptedValue());
    }

    @Test
    public void testAcceptorResetRegion() {
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(1, "rs-001");
        acceptor.handlePrepare(new PaxosTypes.PrepareRequest(pn, "region-001"));
        acceptor.handleAccept(new PaxosTypes.AcceptRequest(pn, "region-001",
                "data".getBytes()));

        acceptor.resetRegion("region-001");

        PaxosTypes.ProposalNumber pnSmall = new PaxosTypes.ProposalNumber(1, "rs-002");
        PaxosTypes.PromiseResponse resp = acceptor.handlePrepare(
                new PaxosTypes.PrepareRequest(pnSmall, "region-001"));
        assertTrue("重置后应接受任何提案", resp.isPromised());
    }

    // ==================== PaxosProposer 测试 ====================

    @Test
    public void testProposerConsensusWithLocalAcceptor() {
        String regionId = "region-001";
        Map<String, byte[]> cols = new HashMap<>();
        cols.put("data", "consensus-test".getBytes());

        WalRecord record = walService.append(regionId, "test", "PUT",
                "consensus-key".getBytes(), cols);

        PaxosTypes.ConsensusResult result = proposer.propose(
                regionId, record, Collections.emptyList());

        assertEquals("只有本地节点时共识应成功",
                PaxosTypes.ConsensusResult.COMMITTED, result);
    }

    @Test
    public void testProposerWithMultipleLocalAcceptors() {
        PaxosAcceptor acceptor2 = new PaxosAcceptor("rs-002");
        PaxosAcceptor acceptor3 = new PaxosAcceptor("rs-003");

        String regionId = "region-002";
        PaxosTypes.ProposalNumber pn = new PaxosTypes.ProposalNumber(100, "rs-001");

        int promises = 0;
        PaxosTypes.PromiseResponse r1 = acceptor.handlePrepare(
                new PaxosTypes.PrepareRequest(pn, regionId));
        PaxosTypes.PromiseResponse r2 = acceptor2.handlePrepare(
                new PaxosTypes.PrepareRequest(pn, regionId));
        PaxosTypes.PromiseResponse r3 = acceptor3.handlePrepare(
                new PaxosTypes.PrepareRequest(pn, regionId));
        if (r1.isPromised()) promises++;
        if (r2.isPromised()) promises++;
        if (r3.isPromised()) promises++;

        assertEquals("所有3个Acceptor都应承诺", 3, promises);

        byte[] value = "paxos-data".getBytes();
        int accepted = 0;
        PaxosTypes.AcceptedResponse a1 = acceptor.handleAccept(
                new PaxosTypes.AcceptRequest(pn, regionId, value));
        PaxosTypes.AcceptedResponse a2 = acceptor2.handleAccept(
                new PaxosTypes.AcceptRequest(pn, regionId, value));
        PaxosTypes.AcceptedResponse a3 = acceptor3.handleAccept(
                new PaxosTypes.AcceptRequest(pn, regionId, value));
        if (a1.isAccepted()) accepted++;
        if (a2.isAccepted()) accepted++;
        if (a3.isAccepted()) accepted++;

        assertEquals("所有3个Acceptor都应接受", 3, accepted);

        acceptor.handleCommit(new PaxosTypes.CommitRequest(pn, regionId));
        acceptor2.handleCommit(new PaxosTypes.CommitRequest(pn, regionId));
        acceptor3.handleCommit(new PaxosTypes.CommitRequest(pn, regionId));

        assertNotNull("acceptor1应有已提交值", acceptor.getCommittedValue(regionId));
        assertNotNull("acceptor2应有已提交值", acceptor2.getCommittedValue(regionId));
        assertNotNull("acceptor3应有已提交值", acceptor3.getCommittedValue(regionId));
    }

    // ==================== ReplicationManager 测试 ====================

    @Test
    public void testReplicationManagerReplicaLifecycle() {
        String regionId = "region-001";

        List<String> replicas = Arrays.asList("rs-002:8001", "rs-003:8001");
        replicationManager.setReplicas(regionId, replicas);

        assertEquals("应有2个副本", 2,
                replicationManager.getReplicas(regionId).size());
        assertTrue("副本列表应包含rs-002",
                replicationManager.getReplicas(regionId).contains("rs-002:8001"));

        replicationManager.addReplica(regionId, "rs-004:8001");
        assertEquals("应有3个副本", 3,
                replicationManager.getReplicas(regionId).size());

        replicationManager.removeReplica(regionId, "rs-003:8001");
        assertEquals("应有2个副本", 2,
                replicationManager.getReplicas(regionId).size());
        assertFalse("不应包含已移除的副本",
                replicationManager.getReplicas(regionId).contains("rs-003:8001"));

        replicationManager.setReplicas(regionId, Collections.emptyList());
        assertEquals("应有0个副本", 0,
                replicationManager.getReplicas(regionId).size());
    }

    @Test
    public void testReplicationManagerWatermark() {
        String regionId = "region-001";
        replicationManager.setReplicas(regionId,
                Arrays.asList("rs-002:8001", "rs-003:8001"));

        Map<String, Long> watermarks = replicationManager.getWatermarks(regionId);
        assertNotNull("水印map不应为空", watermarks);
        assertTrue("初始水印为空（无复制记录）", watermarks.isEmpty());

        assertEquals("最小水印应为0", 0L, replicationManager.getMinWatermark(regionId));
    }

    @Test
    public void testReplicationManagerHealth() {
        String regionId = "region-001";
        replicationManager.setReplicas(regionId,
                Arrays.asList("rs-002:8001", "rs-003:8001"));

        replicationManager.addReplica(regionId, "rs-004:8001");

        Map<String, Boolean> health = replicationManager.getReplicaHealth(regionId);
        assertTrue("rs-004应该有健康状态", health.containsKey("rs-004:8001"));
        assertTrue("rs-004初始应健康", health.get("rs-004:8001"));
    }

    // ==================== 端到端测试 ====================

    @Test
    public void testEndToEndWriteConsensusFlow() {
        String regionId = "region-001";
        String tableName = "users";
        byte[] key = "user-123".getBytes();

        Map<String, byte[]> columns = new HashMap<>();
        columns.put("name", "张三".getBytes());
        columns.put("email", "zhangsan@example.com".getBytes());

        WalRecord record = walService.append(regionId, tableName, "PUT", key, columns);
        assertNotNull("WAL记录不应为空", record);
        assertTrue("序列号应>0", record.getSequenceId() > 0);
        assertNotNull("应有checksum", record.getChecksum());

        PaxosTypes.ConsensusResult result = proposer.propose(
                regionId, record, Collections.emptyList());
        assertEquals("共识应达成", PaxosTypes.ConsensusResult.COMMITTED, result);

        byte[] committed = proposer.getLocalAcceptor().getCommittedValue(regionId);
        assertNotNull("应有已提交的值", committed);

        List<WalRecord> records = walService.getRecords(regionId, 0, 0);
        assertEquals("WAL中应有1条记录", 1, records.size());
        assertEquals("key应匹配", "user-123",
                new String(records.get(0).getKey()));
        assertEquals("操作类型应为PUT", "PUT", records.get(0).getOperation());
    }

    @Test
    public void testEndToEndMultipleWrites() {
        String regionId = "region-001";

        for (int i = 1; i <= 10; i++) {
            Map<String, byte[]> cols = new HashMap<>();
            cols.put("value", ("data-" + i).getBytes());

            WalRecord record = walService.append(regionId, "test", "PUT",
                    ("key-" + i).getBytes(), cols);

            PaxosTypes.ConsensusResult result = proposer.propose(
                    regionId, record, Collections.emptyList());
            assertEquals("第" + i + "次共识应成功",
                    PaxosTypes.ConsensusResult.COMMITTED, result);
        }

        List<WalRecord> all = walService.getRecords(regionId, 0, 0);
        assertEquals("应有10条记录", 10, all.size());

        for (int i = 1; i < all.size(); i++) {
            assertTrue("序列号应递增",
                    all.get(i).getSequenceId() > all.get(i - 1).getSequenceId());
        }
    }

    @Test
    public void testChecksumVerification() {
        String regionId = "region-001";
        byte[] key = "checksum-key".getBytes();
        Map<String, byte[]> cols = new HashMap<>();
        cols.put("data", "important-data".getBytes());

        WalRecord record1 = walService.append(regionId, "test", "PUT", key, cols);
        WalRecord record2 = walService.append(regionId, "test", "PUT", key, cols);

        assertFalse("不同序列号的checksum应不同",
                Arrays.equals(record1.getChecksum(), record2.getChecksum()));
    }

    @Test
    public void testWalServiceMemoryLimit() {
        for (int i = 0; i < 50; i++) {
            Map<String, byte[]> cols = new HashMap<>();
            cols.put("idx", String.valueOf(i).getBytes());
            walService.append("region-bulk", "test", "PUT",
                    ("bulk-key-" + i).getBytes(), cols);
        }

        List<WalRecord> all = walService.getRecords("region-bulk", 1, 50);
        assertEquals("应能查到所有50条记录", 50, all.size());
    }
}
