package com.minisql.master.integration.fixtures;

import com.minisql.master.MasterServer;

public class TestCluster {
    private final EmbeddedZookeeperServer zkServer;
    private final MasterServer masterServer;

    public TestCluster(EmbeddedZookeeperServer zkServer, MasterServer masterServer) {
        this.zkServer = zkServer;
        this.masterServer = masterServer;
    }

    public EmbeddedZookeeperServer getZkServer() {
        return zkServer;
    }

    public MasterServer getMasterServer() {
        return masterServer;
    }

    public void shutdown() throws Exception {
        if (masterServer != null) {
            masterServer.stop();
        }
        if (zkServer != null) {
            zkServer.stop();
        }
    }
}
