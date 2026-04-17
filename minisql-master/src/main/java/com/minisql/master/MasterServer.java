package com.minisql.master;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.FailureRecoveryManager;
import com.minisql.master.cluster.HeartbeatMonitor;
import com.minisql.master.service.ClientMasterServiceImpl;
import com.minisql.master.service.MasterServiceImpl;
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
    private final Server server;
    private final ClusterManager clusterManager;
    private final HeartbeatMonitor heartbeatMonitor;
    private final FailureRecoveryManager failureRecoveryManager;

    // 配置参数
    private static final long HEARTBEAT_TIMEOUT_MS = 30000; // 30秒
    private static final int HEARTBEAT_INTERVAL_MS = 3000;  // 3秒
    private static final long MONITOR_CHECK_INTERVAL_MS = 10000; // 10秒

    public MasterServer(int port) {
        this.port = port;

        // 初始化集群管理器
        this.clusterManager = new ClusterManager(HEARTBEAT_TIMEOUT_MS, HEARTBEAT_INTERVAL_MS);

        // 初始化故障恢复管理器
        this.failureRecoveryManager = new FailureRecoveryManager(clusterManager);

        // 初始化心跳监控器
        this.heartbeatMonitor = new HeartbeatMonitor(clusterManager, MONITOR_CHECK_INTERVAL_MS);
        this.heartbeatMonitor.setFailureHandler(failureRecoveryManager);

        // 构建gRPC服务器
        this.server = ServerBuilder.forPort(port)
                .addService(new MasterServiceImpl(clusterManager))
                .addService(new ClientMasterServiceImpl())
                .build();
    }

    /**
     * 启动服务器
     */
    public void start() throws IOException {
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
     * 停止服务器
     */
    public void stop() throws InterruptedException {
        // 停止心跳监控器
        if (heartbeatMonitor != null) {
            heartbeatMonitor.stop();
            logger.info("HeartbeatMonitor stopped");
        }

        // 停止gRPC服务器
        if (server != null) {
            logger.info("Stopping Master server...");
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("Master server stopped");
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
        // 默认端口
        int port = 8000;

        // 从命令行参数读取端口
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.error("Invalid port number: {}", args[0]);
                System.exit(1);
            }
        }

        // 从环境变量读取端口
        String portEnv = System.getenv("MASTER_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.error("Invalid MASTER_PORT environment variable: {}", portEnv);
            }
        }

        logger.info("Starting Master server on port {}", port);

        MasterServer masterServer = new MasterServer(port);
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
