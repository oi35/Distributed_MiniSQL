package com.minisql.master.integration.fast;

import com.minisql.master.integration.fixtures.*;
import org.junit.*;
import static org.junit.Assert.*;

public class MasterRegionServerIntegrationTest {
    private EmbeddedZookeeperServer zkServer;
    private TestCluster cluster;

    @Before
    public void setUp() throws Exception {
        zkServer = new EmbeddedZookeeperServer();
        zkServer.start();
    }

    @After
    public void tearDown() throws Exception {
        if (cluster != null) {
            cluster.shutdown();
        }
        if (zkServer != null) {
            zkServer.stop();
        }
    }

    @Test
    public void testRegionServerRegistration() throws Exception {
        cluster = TestClusterBuilder.create()
                .withZookeeper(zkServer)
                .withMaster(8000, "master-1")
                .build();

        // Test will verify registration when RegionServer implementation is ready
        assertNotNull(cluster.getMasterServer());
    }
}
