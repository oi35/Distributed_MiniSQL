package com.minisql.regionserver.wal;

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

public class WalManagerTest {

    private Path tempDir;
    private WalManager walManager;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("wal-mgr-test-");
        walManager = new WalManager(tempDir, "test-rs");
    }

    @After
    public void tearDown() throws IOException {
        walManager.close();
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
    }

    @Test
    public void testAppendAndLoad() {
        Map<String, byte[]> cols = new HashMap<>();
        cols.put("name", "Alice".getBytes());
        cols.put("age", "30".getBytes());
        WalRecord record = new WalRecord(1L, "region-1", "users",
                System.currentTimeMillis(), "PUT", "key-1".getBytes(), cols, null);
        walManager.append(record);

        List<WalRecord> loaded = walManager.loadAll();
        assertEquals("应加载1条记录", 1, loaded.size());
        WalRecord r = loaded.get(0);
        assertEquals("seq应匹配", 1L, r.getSequenceId());
        assertEquals("regionId应匹配", "region-1", r.getRegionId());
        assertEquals("tableName应匹配", "users", r.getTableName());
        assertEquals("operation应匹配", "PUT", r.getOperation());
        assertEquals("key应匹配", "key-1", new String(r.getKey()));
        assertEquals("columns数量应匹配", 2, r.getColumns().size());
        assertEquals("name列应匹配", "Alice", new String(r.getColumns().get("name")));
        assertEquals("age列应匹配", "30", new String(r.getColumns().get("age")));
    }

    @Test
    public void testAppendMultipleRecords() {
        for (int i = 0; i < 5; i++) {
            Map<String, byte[]> cols = new HashMap<>();
            cols.put("val", String.valueOf(i).getBytes());
            walManager.append(new WalRecord((long) i, "region-1", "t",
                    System.currentTimeMillis(), "PUT", ("k" + i).getBytes(), cols, null));
        }

        List<WalRecord> loaded = walManager.loadAll();
        assertEquals("应加载5条记录", 5, loaded.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("seq " + i + "应匹配", (long) i, loaded.get(i).getSequenceId());
        }
    }

    @Test
    public void testLoadEmptyFile() {
        List<WalRecord> loaded = walManager.loadAll();
        assertTrue("空WAL文件应返回空列表", loaded.isEmpty());
    }

    @Test
    public void testDeleteRecord() {
        Map<String, byte[]> cols = new HashMap<>();
        walManager.append(new WalRecord(1L, "region-1", "t",
                System.currentTimeMillis(), "DELETE", "key-del".getBytes(), cols, null));

        List<WalRecord> loaded = walManager.loadAll();
        assertEquals("应加载1条DELETE记录", 1, loaded.size());
        assertEquals("operation应为DELETE", "DELETE", loaded.get(0).getOperation());
        assertEquals("key应匹配", "key-del", new String(loaded.get(0).getKey()));
        assertTrue("DELETE记录的columns应为空", loaded.get(0).getColumns().isEmpty());
    }

    @Test
    public void testWalFileExtension() {
        Path walFile = walManager.getWalFile();
        assertTrue("WAL文件应以.wal结尾", walFile.toString().endsWith(".wal"));
        assertEquals("WAL父目录应正确", tempDir, walFile.getParent());
    }
}
