package com.minisql.master.zk;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Master选举管理器
 *
 * 使用Zookeeper实现Master选举，支持主备切换
 */
public class MasterElection {

    private static final Logger logger = LoggerFactory.getLogger(MasterElection.class);

    private static final String ELECTION_PATH = "/minisql/master/election";

    private final ZookeeperClient zkClient;
    private final String serverId;
    private String electionNode;
    private volatile boolean isMaster = false;
    private MasterStateListener listener;

    public interface MasterStateListener {
        void onBecomeMaster();
        void onLoseMaster();
    }

    public MasterElection(ZookeeperClient zkClient, String serverId) {
        this.zkClient = zkClient;
        this.serverId = serverId;
    }

    /**
     * 设置状态监听器
     */
    public void setListener(MasterStateListener listener) {
        this.listener = listener;
    }

    /**
     * 开始选举
     */
    public void startElection() throws KeeperException, InterruptedException {
        // 确保选举路径存在
        zkClient.ensurePath(ELECTION_PATH);

        // 创建临时顺序节点
        electionNode = zkClient.createEphemeralSequentialNode(
                ELECTION_PATH + "/master-",
                serverId
        );

        logger.info("Created election node: {}", electionNode);

        // 检查是否成为Master
        checkMaster();
    }

    /**
     * 检查是否成为Master
     */
    private void checkMaster() throws KeeperException, InterruptedException {
        List<String> children = zkClient.getChildren(ELECTION_PATH);
        Collections.sort(children);

        String smallestNode = children.get(0);
        String myNode = electionNode.substring(ELECTION_PATH.length() + 1);

        if (myNode.equals(smallestNode)) {
            // 成为Master
            becomeMaster();
        } else {
            // 不是Master，监听前一个节点
            int myIndex = children.indexOf(myNode);
            String prevNode = children.get(myIndex - 1);
            watchPreviousNode(ELECTION_PATH + "/" + prevNode);
        }
    }

    /**
     * 成为Master
     */
    private void becomeMaster() {
        isMaster = true;
        logger.info("Became Master: {}", serverId);

        if (listener != null) {
            listener.onBecomeMaster();
        }
    }

    /**
     * 失去Master身份
     */
    private void loseMaster() {
        isMaster = false;
        logger.info("Lost Master status: {}", serverId);

        if (listener != null) {
            listener.onLoseMaster();
        }
    }

    /**
     * 监听前一个节点
     */
    private void watchPreviousNode(String prevNodePath) throws KeeperException, InterruptedException {
        zkClient.exists(prevNodePath, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == Event.EventType.NodeDeleted) {
                    logger.info("Previous node deleted, re-checking master status");
                    try {
                        checkMaster();
                    } catch (Exception e) {
                        logger.error("Failed to check master status", e);
                    }
                }
            }
        });

        logger.debug("Watching previous node: {}", prevNodePath);
    }

    /**
     * 停止选举
     */
    public void stopElection() {
        if (electionNode != null) {
            try {
                zkClient.deleteNode(electionNode);
                logger.info("Deleted election node: {}", electionNode);
            } catch (Exception e) {
                logger.error("Failed to delete election node", e);
            }
        }

        if (isMaster) {
            loseMaster();
        }
    }

    /**
     * 是否是Master
     */
    public boolean isMaster() {
        return isMaster;
    }

    /**
     * 是否是Leader (isLeader is an alias for isMaster)
     */
    public boolean isLeader() {
        return isMaster;
    }

    /**
     * 获取服务器ID
     */
    public String getServerId() {
        return serverId;
    }
}
