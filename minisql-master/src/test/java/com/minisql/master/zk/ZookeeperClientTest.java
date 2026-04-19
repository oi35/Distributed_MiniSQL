package com.minisql.master.zk;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ZookeeperClient单元测试
 *
 * 注意：这些测试需要本地运行Zookeeper服务
 * 如果Zookeeper未运行，测试将被跳过
 */
public class ZookeeperClientTest {

    private ZookeeperClient zkClient;
    private static final String TEST_ZK_CONNECT = "localhost:2181";
    private static final int TEST_SESSION_TIMEOUT = 5000;
    private boolean zkAvailable = false;

    @Before
    public void setUp() {
        zkClient = new ZookeeperClient(TEST_ZK_CONNECT, TEST_SESSION_TIMEOUT);
        try {
            zkClient.connect();
            zkAvailable = true;
        } catch (Exception e) {
            System.out.println("Zookeeper not available, skipping tests: " + e.getMessage());
            zkAvailable = false;
        }
    }

    @After
    public void tearDown() {
        if (zkClient != null && zkAvailable) {
            try {
                // 清理测试节点
                if (zkClient.exists("/test")) {
                    zkClient.deleteNode("/test");
                }
                zkClient.close();
            } catch (Exception e) {
                // 忽略清理错误
            }
        }
    }

    @Test
    public void testConnection() {
        if (!zkAvailable) {
            return;
        }

        assertTrue(zkClient.isConnected());
    }

    @Test
    public void testCreatePersistentNode() throws Exception {
        if (!zkAvailable) {
            return;
        }

        String path = zkClient.createPersistentNode("/test", "test-data");
        assertEquals("/test", path);
        assertTrue(zkClient.exists("/test"));
    }

    @Test
    public void testGetAndSetData() throws Exception {
        if (!zkAvailable) {
            return;
        }

        zkClient.createPersistentNode("/test", "initial-data");

        String data = zkClient.getData("/test");
        assertEquals("initial-data", data);

        zkClient.setData("/test", "updated-data");
        data = zkClient.getData("/test");
        assertEquals("updated-data", data);
    }

    @Test
    public void testDeleteNode() throws Exception {
        if (!zkAvailable) {
            return;
        }

        zkClient.createPersistentNode("/test", "test-data");
        assertTrue(zkClient.exists("/test"));

        zkClient.deleteNode("/test");
        assertFalse(zkClient.exists("/test"));
    }

    @Test
    public void testEnsurePath() throws Exception {
        if (!zkAvailable) {
            return;
        }

        zkClient.ensurePath("/test/path/to/node");
        assertTrue(zkClient.exists("/test"));
        assertTrue(zkClient.exists("/test/path"));
        assertTrue(zkClient.exists("/test/path/to"));
        assertTrue(zkClient.exists("/test/path/to/node"));

        // 清理
        zkClient.deleteNode("/test/path/to/node");
        zkClient.deleteNode("/test/path/to");
        zkClient.deleteNode("/test/path");
        zkClient.deleteNode("/test");
    }

    @Test
    public void testCreateEphemeralNode() throws Exception {
        if (!zkAvailable) {
            return;
        }

        String path = zkClient.createEphemeralNode("/test", "ephemeral-data");
        assertEquals("/test", path);
        assertTrue(zkClient.exists("/test"));

        String data = zkClient.getData("/test");
        assertEquals("ephemeral-data", data);
    }
}
