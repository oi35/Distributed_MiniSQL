package com.minisql.master.integration.fast;

import com.minisql.master.balance.*;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LoadBalancerEndToEndTest {
    private ClusterManager clusterManager;
    private MetadataManager metadataManager;
    private RegionMigrationManager migrationManager;
    private LoadBalancer loadBalancer;

    @Before
    public void setUp() {
        clusterManager = mock(ClusterManager.class);
        metadataManager = new MetadataManager();
        MigrationConfig config = MigrationConfig.builder().build();
        MigrationExecutor executor = new MigrationExecutor();
        migrationManager = new RegionMigrationManager(clusterManager, metadataManager, config, executor);

        MasterElection election = mock(MasterElection.class);
        when(election.isLeader()).thenReturn(true);

        loadBalancer = new LoadBalancer(clusterManager, metadataManager, migrationManager, election, new LoadBalancerConfig());
    }

    @After
    public void tearDown() {
        if (loadBalancer.isRunning()) {
            loadBalancer.stop();
        }
        if (migrationManager.isRunning()) {
            migrationManager.stop();
        }
    }

    @Test
    public void testLoadDetectionAndPlanGeneration() {
        ServerInfo overloaded = mock(ServerInfo.class);
        when(overloaded.getServerId()).thenReturn("rs-001");
        when(overloaded.getLoadScore()).thenReturn(5.0);
        when(overloaded.getRegionCount()).thenReturn(5);

        ServerInfo underloaded = mock(ServerInfo.class);
        when(underloaded.getServerId()).thenReturn("rs-002");
        when(underloaded.getLoadScore()).thenReturn(1.0);
        when(underloaded.getRegionCount()).thenReturn(1);

        when(clusterManager.getOnlineServers()).thenReturn(java.util.Arrays.asList(overloaded, underloaded));

        assertTrue(loadBalancer.needsBalance());
    }
}
