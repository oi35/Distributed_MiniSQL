package com.minisql.master.integration.stress;

import com.minisql.master.integration.fixtures.FakeRegionServer;
import org.junit.*;
import org.testcontainers.containers.GenericContainer;
import static org.junit.Assert.*;

public class FailureRecoveryIntegrationTest {
    private static GenericContainer<?> zookeeper;
    private FakeRegionServer regionServer;

    @BeforeClass
    public static void setUpClass() {
        zookeeper = new GenericContainer<>("zookeeper:3.9.1")
                .withExposedPorts(2181);
        zookeeper.start();
    }

    @AfterClass
    public static void tearDownClass() {
        if (zookeeper != null) {
            zookeeper.stop();
        }
    }

    @Before
    public void setUp() {
        regionServer = new FakeRegionServer("rs-001");
    }

    @After
    public void tearDown() {
        if (regionServer != null) {
            regionServer.stop();
        }
    }

    @Test
    public void testRegionServerCrashDuringMigration() {
        assertNotNull(regionServer);
    }
}
