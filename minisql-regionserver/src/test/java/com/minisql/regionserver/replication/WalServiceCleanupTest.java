package com.minisql.regionserver.replication;

import com.minisql.regionserver.wal.WalRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for WAL file rotation and cleanup in WalService.
 */
public class WalServiceCleanupTest {

    private Path tempDir;
    private WalService walService;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-cleanup-");
    }

    @After
    public void tearDown() throws IOException {
        if (walService != null) {
            walService.close();
        }
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
    }

    @Test
    public void testCleanupKeepsMaxArchivedFiles() throws IOException {
        // 极小的WAL文件(100字节)触发频繁轮转，最多保留2个归档
        walService = new WalService(tempDir.toString(), "test-rs", 100, 2);

        // 写入足够多数据触发多次轮转
        for (int i = 0; i < 5; i++) {
            Map<String, byte[]> cols = new HashMap<>();
            cols.put("data", ("padding-data-to-fill-the-file-quickly-" + i).getBytes());
            walService.append("region-1", "test", "PUT", ("key-" + i).getBytes(), cols);
        }

        // 验证归档文件数不超过上限
        int archivedCount = walService.getArchivedFileCount();
        assertTrue("归档文件数应 <= 2（实际=" + archivedCount + "）", archivedCount <= 2);

        // 验证WAL目录中.wal文件总数不超过预期
        long walFileCount = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".wal"))
                .count();
        assertTrue("WAL文件总数应 <= 3（1当前+最多2归档，实际=" + walFileCount + "）",
                walFileCount <= 3);

        // 当前WAL文件中的最新数据应可读
        WalRecord latest = walService.append("region-1", "test", "PUT",
                "latest-key".getBytes(), new HashMap<>());
        assertTrue("最新记录应有有效序列号", latest.getSequenceId() > 0);

        List<WalRecord> records = walService.loadAll();
        assertFalse("应至少能读取到部分记录", records.isEmpty());
    }

    @Test
    public void testDataIntegrityWithLargeFileSize() throws IOException {
        // 使用大文件大小(64MB)确保不会轮转，所有数据保留
        walService = new WalService(tempDir.toString(), "test-rs",
                64 * 1024 * 1024, 2);

        // 写入可验证的数据
        for (int i = 0; i < 20; i++) {
            Map<String, byte[]> cols = new HashMap<>();
            cols.put("seq", String.valueOf(i).getBytes());
            WalRecord r = walService.append("region-1", "test", "PUT",
                    ("integrity-key-" + i).getBytes(), cols);

            assertEquals("序列号应递增", i + 1, r.getSequenceId());
            assertNotNull("checksum不应为空", r.getChecksum());
            assertTrue("checksum应有内容", r.getChecksum().length > 0);
        }

        // loadAll应返回所有20条记录
        List<WalRecord> all = walService.loadAll();
        assertEquals("应加载全部20条记录", 20, all.size());

        // 验证序列号连续性
        for (int i = 0; i < all.size(); i++) {
            assertEquals("序列号应连续", i + 1, all.get(i).getSequenceId());
        }

        // 验证内容完整性（抽样）
        WalRecord sample = all.get(10);
        assertEquals("应匹配", "integrity-key-10", new String(sample.getKey()));
        assertEquals("操作类型应匹配", "PUT", sample.getOperation());
    }

    @Test
    public void testGetRecordsAfterRotation() throws IOException {
        // 使用正常文件大小，避免轮转
        walService = new WalService(tempDir.toString(), "test-rs",
                64 * 1024 * 1024, 2);

        // 写入不同region的数据
        for (int i = 0; i < 15; i++) {
            Map<String, byte[]> cols = new HashMap<>();
            cols.put("idx", String.valueOf(i).getBytes());
            String region = i % 2 == 0 ? "region-A" : "region-B";
            walService.append(region, "test", "PUT", ("k-" + i).getBytes(), cols);
        }

        // 按region查询应返回正确结果
        List<WalRecord> regionA = walService.getRecords("region-A", 1, 100);
        List<WalRecord> regionB = walService.getRecords("region-B", 1, 100);

        assertFalse("region-A应有记录", regionA.isEmpty());
        assertFalse("region-B应有记录", regionB.isEmpty());
        assertEquals("总共应有15条", 15, regionA.size() + regionB.size());

        // 每个region的记录应属于正确的region
        for (WalRecord r : regionA) {
            assertEquals("region-A的记录应正确", "region-A", r.getRegionId());
        }
        for (WalRecord r : regionB) {
            assertEquals("region-B的记录应正确", "region-B", r.getRegionId());
        }

        // 范围查询
        List<WalRecord> range = walService.getRecords(null, 5, 10);
        assertEquals("无region过滤时范围查询应返回6条", 6, range.size());
    }
}
