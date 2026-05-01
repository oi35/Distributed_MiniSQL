package com.minisql.master.metadata;

import com.minisql.common.proto.RegionState;
import com.minisql.common.proto.TableSchema;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * MetadataManager单元测试
 */
public class MetadataManagerTest {

    private MetadataManager metadataManager;

    @Before
    public void setUp() {
        metadataManager = new MetadataManager();
    }

    @Test
    public void testCreateTable() {
        TableSchema schema = TableSchema.newBuilder()
                .setTableName("users")
                .build();

        boolean success = metadataManager.createTable("users", schema, "id");
        assertTrue(success);

        // 验证表已创建
        TableMetadata table = metadataManager.getTable("users");
        assertNotNull(table);
        assertEquals("users", table.getTableName());
        assertEquals("id", table.getPartitionKey());
    }

    @Test
    public void testCreateTableDuplicate() {
        TableSchema schema = TableSchema.newBuilder()
                .setTableName("users")
                .build();

        metadataManager.createTable("users", schema, "id");

        // 尝试创建重复的表
        boolean success = metadataManager.createTable("users", schema, "id");
        assertFalse(success);
    }

    @Test
    public void testDropTable() {
        TableSchema schema = TableSchema.newBuilder()
                .setTableName("users")
                .build();

        metadataManager.createTable("users", schema, "id");

        // 删除表
        boolean success = metadataManager.dropTable("users");
        assertTrue(success);

        // 验证表已删除
        TableMetadata table = metadataManager.getTable("users");
        assertNull(table);
    }

    @Test
    public void testDropTableNotFound() {
        boolean success = metadataManager.dropTable("nonexistent");
        assertFalse(success);
    }

    @Test
    public void testListTables() {
        TableSchema schema1 = TableSchema.newBuilder().setTableName("users").build();
        TableSchema schema2 = TableSchema.newBuilder().setTableName("orders").build();

        metadataManager.createTable("users", schema1, "id");
        metadataManager.createTable("orders", schema2, "id");

        List<String> tables = metadataManager.listTables();
        assertEquals(2, tables.size());
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
    }

    @Test
    public void testCreateRegion() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");

        boolean success = metadataManager.createRegion("region-1", "users", "", "m");
        assertTrue(success);

        // 验证Region已创建
        RegionMetadata region = metadataManager.getRegion("region-1");
        assertNotNull(region);
        assertEquals("region-1", region.getRegionId());
        assertEquals("users", region.getTableName());
        assertEquals("", region.getStartKey());
        assertEquals("m", region.getEndKey());
    }

    @Test
    public void testCreateRegionTableNotFound() {
        boolean success = metadataManager.createRegion("region-1", "nonexistent", "", "m");
        assertFalse(success);
    }

    @Test
    public void testDeleteRegion() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");

        boolean success = metadataManager.deleteRegion("region-1");
        assertTrue(success);

        // 验证Region已删除
        RegionMetadata region = metadataManager.getRegion("region-1");
        assertNull(region);
    }

    @Test
    public void testUpdateRegionState() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");

        boolean success = metadataManager.updateRegionState("region-1", RegionState.REGION_ONLINE);
        assertTrue(success);

        RegionMetadata region = metadataManager.getRegion("region-1");
        assertEquals(RegionState.REGION_ONLINE, region.getState());
    }

    @Test
    public void testAddRegionReplica() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");

        boolean success = metadataManager.addRegionReplica("region-1", "rs-001");
        assertTrue(success);

        RegionMetadata region = metadataManager.getRegion("region-1");
        assertEquals(1, region.getReplicaCount());
        assertTrue(region.getReplicas().contains("rs-001"));
    }

    @Test
    public void testRemoveRegionReplica() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");
        metadataManager.addRegionReplica("region-1", "rs-001");

        boolean success = metadataManager.removeRegionReplica("region-1", "rs-001");
        assertTrue(success);

        RegionMetadata region = metadataManager.getRegion("region-1");
        assertEquals(0, region.getReplicaCount());
    }

    @Test
    public void testSetRegionPrimary() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");
        metadataManager.addRegionReplica("region-1", "rs-001");

        boolean success = metadataManager.setRegionPrimary("region-1", "rs-001");
        assertTrue(success);

        RegionMetadata region = metadataManager.getRegion("region-1");
        assertEquals("rs-001", region.getPrimaryServer());
    }

    @Test
    public void testFindRegionForKey() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");
        metadataManager.createRegion("region-2", "users", "m", "");

        // 查找键在第一个Region
        RegionMetadata region1 = metadataManager.findRegionForKey("users", "abc");
        assertNotNull(region1);
        assertEquals("region-1", region1.getRegionId());

        // 查找键在第二个Region
        RegionMetadata region2 = metadataManager.findRegionForKey("users", "xyz");
        assertNotNull(region2);
        assertEquals("region-2", region2.getRegionId());
    }

    @Test
    public void testFindRegionsForRange() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");
        metadataManager.createRegion("region-2", "users", "m", "");

        // 查找范围覆盖两个Region
        List<RegionMetadata> regions = metadataManager.findRegionsForRange("users", "a", "z");
        assertEquals(2, regions.size());
    }

    @Test
    public void testGetTableRegions() {
        TableSchema schema = TableSchema.newBuilder().setTableName("users").build();
        metadataManager.createTable("users", schema, "id");
        metadataManager.createRegion("region-1", "users", "", "m");
        metadataManager.createRegion("region-2", "users", "m", "");

        List<RegionMetadata> regions = metadataManager.getTableRegions("users");
        assertEquals(2, regions.size());
    }

    @Test
    public void testGetStats() {
        TableSchema schema1 = TableSchema.newBuilder().setTableName("users").build();
        TableSchema schema2 = TableSchema.newBuilder().setTableName("orders").build();

        metadataManager.createTable("users", schema1, "id");
        metadataManager.createTable("orders", schema2, "id");
        metadataManager.createRegion("region-1", "users", "", "m");
        metadataManager.createRegion("region-2", "users", "m", "");
        metadataManager.createRegion("region-3", "orders", "", "");

        Map<String, Object> stats = metadataManager.getStats();
        assertEquals(2, stats.get("tableCount"));
        assertEquals(3, stats.get("regionCount"));
    }
}
