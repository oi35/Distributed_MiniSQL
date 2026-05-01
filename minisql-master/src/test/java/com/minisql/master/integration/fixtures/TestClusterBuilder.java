package com.minisql.master.integration.fixtures;

import com.minisql.master.MasterServer;

public class TestClusterBuilder {
    private EmbeddedZookeeperServer zkServer;
    private int masterPort = 8000;
    private String masterId = "test-master";

    public static TestClusterBuilder create() {
        return new TestClusterBuilder();
    }

    public TestClusterBuilder withZookeeper(EmbeddedZookeeperServer zk) {
        this.zkServer = zk;
        return this;
    }

    public TestClusterBuilder withMaster(int port, String serverId) {
        this.masterPort = port;
        this.masterId = serverId;
        return this;
    }

    public TestCluster build() throws Exception {
        if (zkServer == null) {
            throw new IllegalStateException("Zookeeper not configured");
        }
        MasterServer master = new MasterServer(masterPort, masterId, zkServer.getConnectString());
        return new TestCluster(zkServer, master);
    }
}
