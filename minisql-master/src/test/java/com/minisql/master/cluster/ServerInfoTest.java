package com.minisql.master.cluster;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ServerInfoTest {

    @Test
    public void testGetRegionIds() {
        ServerInfo server = new ServerInfo("rs-001", "localhost", 8001);

        // 初始为空
        List<String> regionIds = server.getRegionIds();
        assertTrue(regionIds.isEmpty());

        // 添加Region
        server.addRegion("region-1", 1024L);
        server.addRegion("region-2", 2048L);

        regionIds = server.getRegionIds();
        assertEquals(2, regionIds.size());
        assertTrue(regionIds.contains("region-1"));
        assertTrue(regionIds.contains("region-2"));

        // 返回的是副本，修改不影响原数据
        regionIds.add("region-3");
        assertEquals(2, server.getRegionIds().size());
    }
}
