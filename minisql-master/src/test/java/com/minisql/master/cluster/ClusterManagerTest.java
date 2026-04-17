package com.minisql.master.cluster;

import com.minisql.common.proto.ServerMetrics;
import com.minisql.common.proto.ServerState;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ClusterManager单元测试
 */
public class ClusterManagerTest {

    private ClusterManager clusterManager;
    private static final long HEARTBEAT_TIMEOUT_MS = 1000; // 1秒用于测试
    private static final int HEARTBEAT_INTERVAL_MS = 100;

    @Before
    public void setUp() {
        clusterManager = new ClusterManager(HEARTBEAT_TIMEOUT_MS, HEARTBEAT_INTERVAL_MS);
    }

    @Test
    public void testRegisterServer() {
        // 注册服务器
        String serverId = clusterManager.registerServer("rs-001", "localhost", 8001);

        assertEquals("rs-001", serverId);

        // 验证服务器已注册
        ServerInfo serverInfo = clusterManager.getServerInfo("rs-001");
        assertNotNull(serverInfo);
        assertEquals("rs-001", serverInfo.getServerId());
        assertEquals("localhost", serverInfo.getHost());
        assertEquals(8001, serverInfo.getPort());
        assertEquals(ServerState.SERVER_ONLINE, serverInfo.getState());
    }

    @Test
    public void testRegisterServerWithConflict() {
        // 注册第一个服务器
        String serverId1 = clusterManager.registerServer("rs-001", "localhost", 8001);
        assertEquals("rs-001", serverId1);

        // 注册相同ID的服务器，应该生成新ID
        String serverId2 = clusterManager.registerServer("rs-001", "localhost", 8002);
        assertEquals("rs-001-1", serverId2);

        // 验证两个服务器都存在
        assertNotNull(clusterManager.getServerInfo("rs-001"));
        assertNotNull(clusterManager.getServerInfo("rs-001-1"));
    }

    @Test
    public void testUnregisterServer() {
        // 注册服务器
        clusterManager.registerServer("rs-001", "localhost", 8001);

        // 注销服务器
        boolean success = clusterManager.unregisterServer("rs-001");
        assertTrue(success);

        // 验证服务器状态变为OFFLINE
        ServerInfo serverInfo = clusterManager.getServerInfo("rs-001");
        assertNotNull(serverInfo);
        assertEquals(ServerState.SERVER_OFFLINE, serverInfo.getState());
    }

    @Test
    public void testUpdateHeartbeat() {
        // 注册服务器
        clusterManager.registerServer("rs-001", "localhost", 8001);

        // 创建指标
        ServerMetrics metrics = ServerMetrics.newBuilder()
                .setRegionCount(5)
                .setTotalSizeBytes(1024 * 1024 * 100) // 100MB
                .setCpuUsage(0.5)
                .setMemoryUsage(0.6)
                .build();

        // 更新心跳
        boolean success = clusterManager.updateHeartbeat("rs-001", metrics);
        assertTrue(success);

        // 验证指标已更新
        ServerInfo serverInfo = clusterManager.getServerInfo("rs-001");
        assertNotNull(serverInfo);
        assertEquals(metrics, serverInfo.getMetrics());
    }

    @Test
    public void testGetOnlineServers() {
        // 注册多个服务器
        clusterManager.registerServer("rs-001", "localhost", 8001);
        clusterManager.registerServer("rs-002", "localhost", 8002);
        clusterManager.registerServer("rs-003", "localhost", 8003);

        // 注销一个服务器
        clusterManager.unregisterServer("rs-002");

        // 获取在线服务器
        List<ServerInfo> onlineServers = clusterManager.getOnlineServers();
        assertEquals(2, onlineServers.size());

        // 验证在线服务器列表
        assertTrue(onlineServers.stream().anyMatch(s -> s.getServerId().equals("rs-001")));
        assertTrue(onlineServers.stream().anyMatch(s -> s.getServerId().equals("rs-003")));
        assertFalse(onlineServers.stream().anyMatch(s -> s.getServerId().equals("rs-002")));
    }

    @Test
    public void testCheckTimeoutServers() throws InterruptedException {
        // 注册服务器
        clusterManager.registerServer("rs-001", "localhost", 8001);

        // 等待一段时间，确保rs-001的心跳时间较早
        Thread.sleep(500);

        clusterManager.registerServer("rs-002", "localhost", 8002);

        // 等待超时（只有rs-001会超时）
        Thread.sleep(HEARTBEAT_TIMEOUT_MS - 400);

        // 检查超时服务器
        List<String> timeoutServers = clusterManager.checkTimeoutServers();

        // rs-001应该超时，rs-002不应该超时
        assertEquals(1, timeoutServers.size());
        assertTrue(timeoutServers.contains("rs-001"));

        // 验证rs-001状态变为DEAD
        ServerInfo serverInfo = clusterManager.getServerInfo("rs-001");
        assertEquals(ServerState.SERVER_DEAD, serverInfo.getState());
    }

    @Test
    public void testSelectLeastLoadedServer() {
        // 注册多个服务器
        clusterManager.registerServer("rs-001", "localhost", 8001);
        clusterManager.registerServer("rs-002", "localhost", 8002);
        clusterManager.registerServer("rs-003", "localhost", 8003);

        // 给服务器添加不同数量的Region
        clusterManager.addRegionToServer("rs-001", "region-1", 1024);
        clusterManager.addRegionToServer("rs-001", "region-2", 1024);
        clusterManager.addRegionToServer("rs-002", "region-3", 1024);

        // 选择负载最低的服务器
        ServerInfo leastLoaded = clusterManager.selectLeastLoadedServer();
        assertNotNull(leastLoaded);
        assertEquals("rs-003", leastLoaded.getServerId()); // rs-003没有Region
    }

    @Test
    public void testAddAndRemoveRegion() {
        // 注册服务器
        clusterManager.registerServer("rs-001", "localhost", 8001);

        // 添加Region
        clusterManager.addRegionToServer("rs-001", "region-1", 1024);
        clusterManager.addRegionToServer("rs-001", "region-2", 2048);

        // 验证Region已添加
        ServerInfo serverInfo = clusterManager.getServerInfo("rs-001");
        assertEquals(2, serverInfo.getRegionCount());
        assertTrue(serverInfo.getRegions().containsKey("region-1"));
        assertTrue(serverInfo.getRegions().containsKey("region-2"));

        // 移除Region
        clusterManager.removeRegionFromServer("rs-001", "region-1");

        // 验证Region已移除
        serverInfo = clusterManager.getServerInfo("rs-001");
        assertEquals(1, serverInfo.getRegionCount());
        assertFalse(serverInfo.getRegions().containsKey("region-1"));
        assertTrue(serverInfo.getRegions().containsKey("region-2"));
    }

    @Test
    public void testGetClusterStats() {
        // 注册多个服务器
        clusterManager.registerServer("rs-001", "localhost", 8001);
        clusterManager.registerServer("rs-002", "localhost", 8002);
        clusterManager.registerServer("rs-003", "localhost", 8003);

        // 添加Region
        clusterManager.addRegionToServer("rs-001", "region-1", 1024);
        clusterManager.addRegionToServer("rs-001", "region-2", 1024);
        clusterManager.addRegionToServer("rs-002", "region-3", 1024);

        // 注销一个服务器
        clusterManager.unregisterServer("rs-003");

        // 获取集群统计
        Map<String, Object> stats = clusterManager.getClusterStats();

        assertEquals(3L, stats.get("totalServers"));
        assertEquals(2L, stats.get("onlineServers"));
        assertEquals(0L, stats.get("deadServers"));
        assertEquals(3, stats.get("totalRegions"));
    }

    @Test
    public void testGetHeartbeatIntervalMs() {
        assertEquals(HEARTBEAT_INTERVAL_MS, clusterManager.getHeartbeatIntervalMs());
    }
}
