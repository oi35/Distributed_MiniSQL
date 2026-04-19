package com.minisql.master.zk;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Zookeeper客户端封装
 *
 * 提供连接管理、节点操作等基础功能
 */
public class ZookeeperClient {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperClient.class);

    private final String connectString;
    private final int sessionTimeout;
    private ZooKeeper zooKeeper;
    private final CountDownLatch connectedLatch = new CountDownLatch(1);

    public ZookeeperClient(String connectString, int sessionTimeout) {
        this.connectString = connectString;
        this.sessionTimeout = sessionTimeout;
    }

    /**
     * 连接到Zookeeper
     */
    public void connect() throws IOException, InterruptedException {
        zooKeeper = new ZooKeeper(connectString, sessionTimeout, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    connectedLatch.countDown();
                    logger.info("Connected to Zookeeper: {}", connectString);
                } else if (event.getState() == Event.KeeperState.Disconnected) {
                    logger.warn("Disconnected from Zookeeper");
                } else if (event.getState() == Event.KeeperState.Expired) {
                    logger.error("Zookeeper session expired");
                }
            }
        });

        // 等待连接建立
        if (!connectedLatch.await(sessionTimeout, TimeUnit.MILLISECONDS)) {
            throw new IOException("Failed to connect to Zookeeper within timeout");
        }
    }

    /**
     * 关闭连接
     */
    public void close() throws InterruptedException {
        if (zooKeeper != null) {
            zooKeeper.close();
            logger.info("Zookeeper connection closed");
        }
    }

    /**
     * 创建节点
     *
     * @param path 节点路径
     * @param data 节点数据
     * @param createMode 创建模式
     * @return 创建的节点路径
     */
    public String createNode(String path, String data, CreateMode createMode) throws KeeperException, InterruptedException {
        byte[] bytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return zooKeeper.create(path, bytes, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
    }

    /**
     * 创建持久节点
     */
    public String createPersistentNode(String path, String data) throws KeeperException, InterruptedException {
        return createNode(path, data, CreateMode.PERSISTENT);
    }

    /**
     * 创建临时节点
     */
    public String createEphemeralNode(String path, String data) throws KeeperException, InterruptedException {
        return createNode(path, data, CreateMode.EPHEMERAL);
    }

    /**
     * 创建临时顺序节点
     */
    public String createEphemeralSequentialNode(String path, String data) throws KeeperException, InterruptedException {
        return createNode(path, data, CreateMode.EPHEMERAL_SEQUENTIAL);
    }

    /**
     * 删除节点
     */
    public void deleteNode(String path) throws KeeperException, InterruptedException {
        zooKeeper.delete(path, -1);
    }

    /**
     * 检查节点是否存在
     */
    public boolean exists(String path) throws KeeperException, InterruptedException {
        return zooKeeper.exists(path, false) != null;
    }

    /**
     * 检查节点是否存在（带监听）
     */
    public Stat exists(String path, Watcher watcher) throws KeeperException, InterruptedException {
        return zooKeeper.exists(path, watcher);
    }

    /**
     * 获取节点数据
     */
    public String getData(String path) throws KeeperException, InterruptedException {
        byte[] data = zooKeeper.getData(path, false, null);
        return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }

    /**
     * 获取节点数据（带监听）
     */
    public String getData(String path, Watcher watcher) throws KeeperException, InterruptedException {
        byte[] data = zooKeeper.getData(path, watcher, null);
        return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }

    /**
     * 设置节点数据
     */
    public void setData(String path, String data) throws KeeperException, InterruptedException {
        byte[] bytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
        zooKeeper.setData(path, bytes, -1);
    }

    /**
     * 获取子节点列表
     */
    public List<String> getChildren(String path) throws KeeperException, InterruptedException {
        return zooKeeper.getChildren(path, false);
    }

    /**
     * 获取子节点列表（带监听）
     */
    public List<String> getChildren(String path, Watcher watcher) throws KeeperException, InterruptedException {
        return zooKeeper.getChildren(path, watcher);
    }

    /**
     * 确保路径存在（递归创建父节点）
     */
    public void ensurePath(String path) throws KeeperException, InterruptedException {
        if (path == null || path.equals("/")) {
            return;
        }

        if (exists(path)) {
            return;
        }

        // 递归创建父节点
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        if (!parentPath.isEmpty()) {
            ensurePath(parentPath);
        }

        try {
            createPersistentNode(path, "");
            logger.debug("Created path: {}", path);
        } catch (KeeperException.NodeExistsException e) {
            // 节点已存在，忽略
        }
    }

    /**
     * 获取Zookeeper实例（用于高级操作）
     */
    public ZooKeeper getZooKeeper() {
        return zooKeeper;
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return zooKeeper != null && zooKeeper.getState() == ZooKeeper.States.CONNECTED;
    }
}
