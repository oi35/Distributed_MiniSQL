package com.minisql.master.integration.fixtures;

import org.apache.curator.test.TestingServer;

public class EmbeddedZookeeperServer {
    private TestingServer testingServer;

    public void start() throws Exception {
        testingServer = new TestingServer(true);
    }

    public void stop() throws Exception {
        if (testingServer != null) {
            testingServer.stop();
            testingServer.close();
        }
    }

    public String getConnectString() {
        return testingServer != null ? testingServer.getConnectString() : null;
    }
}
