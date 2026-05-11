# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Distributed database system for educational purposes implementing data sharding with range-based partitioning, replica management with Paxos consensus, and Master-RegionServer architecture.

**Current implementation state:** Two modules exist — `minisql-common` (proto definitions) and `minisql-master` (Master server with cluster management, load balancing, and region migration). RegionServer and Client modules are planned but not yet created.

## Build & Test Commands

```bash
# Build minisql-common first (generates protobuf stubs, installs to local .m2)
cd minisql-common && mvn clean install

# Build minisql-master (depends on minisql-common)
cd minisql-master && mvn clean install

# Build both from root (if parent POM exists)
mvn clean install

# Run all tests in a module
cd minisql-master && mvn test

# Run a single test class
cd minisql-master && mvn test -Dtest=LoadBalancerTest

# Run a single test method
cd minisql-master && mvn test -Dtest=LoadBalancerTest#testBalancedAssignment

# Generate JaCoCo coverage report (runs with test phase)
cd minisql-master && mvn test
# Report at: minisql-master/target/site/jacoco/index.html

# Regenerate protobuf stubs
cd minisql-common && mvn clean compile
```

## Running Master Server

```bash
cd minisql-master && mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"
```

Or with arguments and env vars:

```bash
cd minisql-master
MASTER_PORT=9000 MASTER_ID=master-01 ZK_CONNECT=localhost:2181 \
  mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"
```

**Startup flow:** Zookeeper connection → Master election → (on win) start gRPC server + heartbeat monitor. Default port 8000. Requires Zookeeper running at `localhost:2181` (override with `ZK_CONNECT` env var).

**Env vars:** `MASTER_PORT` (default 8000), `MASTER_ID` (auto-generated if unset), `ZK_CONNECT` (default `localhost:2181`).

## Package Structure

```
minisql-common/
└── src/main/proto/
    ├── common.proto       — Shared types (RegionInfo, TableSchema, enums, error codes)
    ├── master.proto       — MasterService + ClientMasterService gRPC definitions
    └── regionserver.proto — RegionServerService gRPC definitions (for future use)

minisql-master/
└── src/main/java/com/minisql/master/
    ├── MasterServer.java                    — Entry point, gRPC server, lifecycle
    ├── cluster/
    │   ├── ClusterManager.java              — RegionServer registry, state tracking
    │   ├── HeartbeatMonitor.java            — Periodic heartbeat timeout detection
    │   ├── FailureRecoveryManager.java      — Handles RegionServer failures
    │   └── ServerInfo.java                  — RegionServer metadata model
    ├── metadata/
    │   ├── MetadataManager.java             — Table/Region metadata CRUD
    │   ├── RouteTable.java                  — Region routing table
    │   ├── TableMetadata.java               — Table schema + Region list
    │   └── RegionMetadata.java              — Region state + replica info
    ├── balance/
    │   ├── LoadBalancer.java                — Region distribution algorithm
    │   ├── LoadBalancerConfig.java          — Balance thresholds configuration
    │   ├── RegionMigrationManager.java      — Migration lifecycle orchestration
    │   ├── MigrationTask.java               — Single migration job model
    │   ├── MigrationPlan.java               — Multi-task migration plan
    │   ├── MigrationState.java              — State enum + transitions
    │   ├── MigrationExecutor.java           — Executes migration steps
    │   ├── MigrationStateHandler.java       — Per-phase handler interface
    │   ├── PrepareHandler.java              — Prepare phase
    │   ├── SyncHandler.java                 — Data sync phase
    │   ├── SwitchHandler.java               — Switchover phase
    │   ├── RollbackHandler.java             — Rollback on failure
    │   ├── SyncProgress.java                — Sync progress tracking
    │   ├── MigrationStatistics.java         — Migration metrics
    │   ├── MigrationConfig.java             — Migration tuning parameters
    │   └── MigrationException.java          — Migration error types
    ├── service/
    │   ├── MasterServiceImpl.java           — MasterService gRPC impl (RegionServer-facing)
    │   └── ClientMasterServiceImpl.java     — ClientMasterService gRPC impl (client-facing)
    └── zk/
        ├── ZookeeperClient.java             — ZK connection + CRUD helpers
        ├── MasterElection.java              — Leader election via ZK ephemeral nodes
        └── MetadataPersistence.java         — ZK-backed metadata storage paths
```

## Dependencies

```
minisql-common (proto gRPC stubs) → must be installed before minisql-master
    ↓
minisql-master
```

Key runtime dependencies: gRPC/Netty (1.58.0), Protobuf (3.24.0), Zookeeper (3.9.1), Guava (32.1.3), Gson (2.10.1).

## Code Standards

- Java 11, Maven, Google Java Style
- SLF4J for logging; format: `[timestamp] [level] [component] [thread] message`
- **JUnit 4.13.2** for tests, Mockito 5.14.2 for mocking
- Code coverage > 70% (critical modules > 85%) — JaCoCo 0.8.11
- Git commits: `<type>(<scope>): <description>` (feat, fix, refactor, test, docs, build)

## Proto Workflow

Protobuf definitions live in `minisql-common/src/main/proto/`. The `protobuf-maven-plugin` generates Java stubs during `mvn compile`. After changing `.proto` files:

```bash
cd minisql-common && mvn clean install
```

This regenerates stubs and installs them to the local Maven repo so `minisql-master` can pick them up.

## Common Issues

**Proto compilation fails:** `cd minisql-common && mvn clean && mvn compile`

**Master depends on stale common:** Run `cd minisql-common && mvn clean install` first.

**gRPC connection refused:** Check Zookeeper is running, Master has won election, port matches.

**Region not found:** Check Master route table, verify RegionServer has region loaded, refresh client cache.

## Team Structure

5-person educational team: Member 1 (Architecture + Master), Member 2 (RegionServer), Member 3 (Replication + Consensus), Member 4 (Client SDK + Distributed queries), Member 5 (Testing + Tools + Documentation).

## Specialist Agents & Skills

The `.claude/` directory contains project-specific agents and skills. See `.claude/README.md` for usage details.

**Agents:** `master-specialist`, `regionserver-specialist`, `replication-specialist`, `client-specialist`

**Skills:** `distributed-system-debug`, `grpc-interface-design`, `region-management`, `paxos-implementation`

## Documentation

- gRPC interface design: `docs/interface-design.md`
- Architecture spec: `docs/superpowers/specs/2026-04-15-distributed-minisql-design.md`
- Implementation plans: `docs/superpowers/plans/`
- Team division: `docs/team-division.md`

## Key Design Decisions

- **Explicit RPC methods** over generic ones for core operations (type safety, clarity for learning)
- **Range-based partitioning** with `[start_key, end_key)` intervals
- **Zookeeper** for Master HA election and metadata persistence
- **State machine** for Region lifecycle (OFFLINE → OPENING → ONLINE → SPLITTING/MIGRATING → CLOSED)
- **Multi-phase migration**: Prepare → Sync → Switch (with Rollback on failure)
