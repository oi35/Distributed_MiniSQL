package com.minisql.master;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.FailureRecoveryManager;
import com.minisql.master.cluster.HeartbeatMonitor;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.service.ClientMasterServiceImpl;
import com.minisql.master.service.MasterServiceImpl;
import com.minisql.master.zk.MasterElection;
import com.minisql.master.zk.MetadataPersistence;
import com.minisql.master.zk.ZookeeperClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Master服务器主类
 *
 * 负责启动gRPC服务器，注册服务，处理优雅关闭
 */
public class MasterServer {

    private static final Logger logger = LoggerFactory.getLogger(MasterServer.class);

    private final int port;
    private final String serverId;
    private final Server server;
    private final ClusterManager clusterManager;
    private final MetadataManager metadataManager;
    private final HeartbeatMonitor heartbeatMonitor;
    private final FailureRecoveryManager failureRecoveryManager;

    // Zookeeper组件
    private final ZookeeperClient zkClient;
    private final MasterElection masterElection;
    private final MetadataPersistence metadataPersistence;

    // 配置参数
    private static final long HEARTBEAT_TIMEOUT_MS = 30000; // 30秒
    private static final int HEARTBEAT_INTERVAL_MS = 3000;  // 3秒
    private static final long MONITOR_CHECK_INTERVAL_MS = 10000; // 10秒
    private static final String DEFAULT_ZK_CONNECT = "localhost:2181";
    private static final int ZK_SESSION_TIMEOUT = 30000; // 30秒

    public MasterServer(int port, String serverId, String zkConnect) {
        this.port = port;
        this.serverId = serverId;

        // 初始化Zookeeper客户端
        this.zkClient = new ZookeeperClient(zkConnect, ZK_SESSION_TIMEOUT);

        // 初始化Master选举
        this.masterElection = new MasterElection(zkClient, serverId);

        // 初始化元数据持久化
        this.metadataPersistence = new MetadataPersistence(zkClient);

        // 初始化集群管理器
        this.clusterManager = new ClusterManager(HEARTBEAT_TIMEOUT_MS, HEARTBEAT_INTERVAL_MS);

        // 初始化元数据管理器
        this.metadataManager = new MetadataManager();

        // 初始化故障恢复管理器
        this.failureRecoveryManager = new FailureRecoveryManager(clusterManager);

        // 初始化心跳监控器
        this.heartbeatMonitor = new HeartbeatMonitor(clusterManager, MONITOR_CHECK_INTERVAL_MS);
        this.heartbeatMonitor.setFailureHandler(failureRecoveryManager);

        // 构建gRPC服务器
        this.server = ServerBuilder.forPort(port)
                .addService(new MasterServiceImpl(clusterManager))
                .addService(new ClientMasterServiceImpl(metadataManager))
                .build();
    }

    /**
     * 启动服务器
     */
    public void start() throws IOException {
        try {
            // 连接Zookeeper
            zkClient.connect();
            logger.info("Connected to Zookeeper");

            // 初始化元数据路径
            metadataPersistence.initialize();
            logger.info("Metadata paths initialized");

            // 设置Master选举监听器
            masterElection.setListener(new MasterElection.MasterStateListener() {
                @Override
                public void onBecomeMaster() {
                    logger.info("This node became Master: {}", serverId);
                    // 成为Master后启动服务
                    try {
                        startServices();
                    } catch (IOException e) {
                        logger.error("Failed to start services", e);
                    }
                }

                @Override
                public void onLoseMaster() {
                    logger.warn("This node lost Master status: {}", serverId);
                    // 失去Master身份后停止服务
                    stopServices();
                }
            });

            // 开始Master选举
            masterElection.startElection();
            logger.info("Master election started");

        } catch (Exception e) {
            logger.error("Failed to start Master server", e);
            throw new IOException("Failed to start Master server", e);
        }
    }

    /**
     * 启动服务（仅Master执行）
     */
    private void startServices() throws IOException {
        // 启动gRPC服务器
        server.start();
        logger.info("Master server started, listening on port {}", port);

        // 启动心跳监控器
        heartbeatMonitor.start();
        logger.info("HeartbeatMonitor started");

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Master server due to JVM shutdown");
            try {
                MasterServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
            }
        }));
    }

    /**
     * 停止服务
     */
    private void stopServices() {
        // 停止心跳监控器
        if (heartbeatMonitor != null) {
            heartbeatMonitor.stop();
            logger.info("HeartbeatMonitor stopped");
        }

        // 停止gRPC服务器
        if (server != null && !server.isShutdown()) {
            try {
                logger.info("Stopping Master server...");
                server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                logger.info("Master server stopped");
            } catch (InterruptedException e) {
                logger.error("Error stopping server", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 停止服务器
     */
    public void stop() throws InterruptedException {
        // 停止Master选举
        if (masterElection != null) {
            masterElection.stopElection();
            logger.info("Master election stopped");
        }

        // 停止服务
        stopServices();

        // 关闭Zookeeper连接
        if (zkClient != null) {
            zkClient.close();
            logger.info("Zookeeper connection closed");
        }
    }

    /**
     * 阻塞等待服务器终止
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * 主入口
     */
    public static void main(String[] args) {
        // 默认配置
        int port = 8000;
        String serverId = "master-" + System.currentTimeMillis();
        String zkConnect = DEFAULT_ZK_CONNECT;

        // 从命令行参数读取配置
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.error("Invalid port number: {}", args[0]);
                System.exit(1);
            }
        }
        if (args.length > 1) {
            serverId = args[1];
        }
        if (args.length > 2) {
            zkConnect = args[2];
        }

        // 从环境变量读取配置
        String portEnv = System.getenv("MASTER_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.error("Invalid MASTER_PORT environment variable: {}", portEnv);
            }
        }

        String serverIdEnv = System.getenv("MASTER_ID");
        if (serverIdEnv != null && !serverIdEnv.isEmpty()) {
            serverId = serverIdEnv;
        }

        String zkConnectEnv = System.getenv("ZK_CONNECT");
        if (zkConnectEnv != null && !zkConnectEnv.isEmpty()) {
            zkConnect = zkConnectEnv;
        }

        logger.info("Starting Master server: id={}, port={}, zk={}", serverId, port, zkConnect);

        MasterServer masterServer = new MasterServer(port, serverId, zkConnect);
        try {
            masterServer.start();
            masterServer.blockUntilShutdown();
        } catch (IOException e) {
            logger.error("Failed to start Master server", e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.error("Master server interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}
