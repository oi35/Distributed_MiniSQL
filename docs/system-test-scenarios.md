# System Test Scenario Design

## Overview

This document defines the system-level test scenarios for the Distributed MiniSQL project. These scenarios complement the unit tests and integration tests by validating end-to-end system behavior under various conditions.

## Test Levels

| Level | Scope | Tooling | Location |
|-------|-------|---------|----------|
| Unit | Individual class/method | JUnit + Mockito | `*/src/test/java/` |
| Integration | Module interactions | In-process gRPC, Embedded ZK | `integration/fast/` |
| System | End-to-end workflows | Full cluster, Testcontainers | `integration/e2e/` |
| Stress | High load, concurrency | Concurrent clients, in-process gRPC | `benchmark/` |
| Acceptance | CLI, API correctness | Admin CLI, gRPC client | `integration/fast/` |

## Test Scenario Categories

### 1. Normal Operation Scenarios

| ID | Scenario | Steps | Expected Result | Coverage |
|----|----------|-------|----------------|----------|
| N1 | Single row put/get | Insert - Retrieve by key | Exact match, data integrity | MiniSQLClientTest, CrudBenchmarkTest |
| N2 | Batch operations | Batch put - Batch get | All rows accessible | RegionServerServiceImplTest |
| N3 | Range scan | Insert contiguous keys - Scan | Ordered results within range | MiniSQLClientTest |
| N4 | Delete existing row | Insert - Delete - Get | Row no longer exists | MiniSQLClientTest |
| N5 | Multi-region scan | Insert keys across 2 regions | Results merged correctly | MiniSQLClientTest |
| N6 | Table DDL | Create table - List - Describe | Schema matches definition | MetadataManagerTest |

### 2. Cluster Management Scenarios

| ID | Scenario | Steps | Expected Result | Coverage |
|----|----------|-------|----------------|----------|
| C1 | RegionServer registration | Start RS - Register with Master | RS appears in cluster nodes | MasterRegionServerIntegrationTest |
| C2 | Heartbeat mechanism | Register - Send heartbeats | Timestamps updated, status online | MasterRegionServerIntegrationTest |
| C3 | Region assignment | Two RS online - Assign regions | Regions distributed correctly | MasterRegionServerIntegrationTest |
| C4 | Load balancing | Unbalanced load - Trigger balance | Regions redistributed | LoadBalancerTest, EndToEndMigrationTest |
| C5 | Region migration | Migrate region from RS-A to RS-B | Data intact, routes updated | RegionMigrationManagerTest |

### 3. Failure and Recovery Scenarios

| ID | Scenario | Steps | Expected Result | Coverage |
|----|----------|-------|----------------|----------|
| F1 | RegionServer failure detection | Register RS - Stop heartbeats - Wait | RS marked offline | MasterRegionServerIntegrationTest |
| F2 | Master failover | Two Masters - Kill leader - Election | Backup promoted | MasterElectionStressTest |
| F3 | Region recovery after RS failure | RS with regions fails | Regions reassigned | FailureRecoveryIntegrationTest |
| F4 | Network partition recovery | RS disconnected - Reconnected | Resumes heartbeats, state synced | FailureRecoveryIntegrationTest |

### 4. Stress and Concurrency Scenarios

| ID | Scenario | Steps | Expected Result | Coverage |
|----|----------|-------|----------------|----------|
| S1 | Concurrent writes | 10 threads, 500 puts each | All writes succeed, no lost data | ConcurrencyStressTest |
| S2 | Mixed read/write | 10 threads, sustained 10s | Stable throughput, no errors | ConcurrencyStressTest |
| S3 | Delete/Get race | Concurrent delete + get on same keys | No deadlocks, consistent results | ConcurrencyStressTest |

### 5. Admin CLI Scenarios

| ID | Scenario | Steps | Expected Result | Coverage |
|----|----------|-------|----------------|----------|
| A1 | Cluster health | cluster status | Shows correct server count | MiniSqlAdminTest |
| A2 | Server listing | cluster nodes | Lists all registered RS | AdminGrpcClientTest |
| A3 | Table schema | table describe name | Correct column types | AdminGrpcClientTest |
| A4 | Route table | table route name | Correct region distribution | AdminGrpcClientTest |
| A5 | Error handling | Invalid table name | Graceful error message | MiniSqlAdminTest |

### 6. Edge Cases

| ID | Scenario | Steps | Expected Result | Priority |
|----|----------|-------|----------------|----------|
| E1 | Empty table scan | Scan on new table | Empty result set | Medium |
| E2 | Non-existent key | Get/Delete on missing key | Not found response | Medium |
| E3 | Duplicate key | Put same key twice | Last write wins | Medium |
| E4 | Very large key | 1MB key value | Accepted or rejected gracefully | Low |
| E5 | Zero-byte value | Put with empty columns | Stored successfully | Low |
| E6 | Stale route handling | Route changed mid-operation | Client refreshes and retries | High |

## Running the Test Suite

### Quick verification (unit + fast integration)
```bash
mvn test -Pintegration-fast
```

### Full test suite
```bash
mvn test
```

### Performance benchmark
```bash
mvn test -Dtest=CrudBenchmarkTest -pl minisql-client
```

### Stress test
```bash
mvn test -Dtest=ConcurrencyStressTest -pl minisql-client
```

### E2E tests (requires Docker for Zookeeper)
```bash
mvn test -Dtest="*EndToEnd*" -pl minisql-master
```

## Coverage Goals

| Module | Current | Target |
|--------|---------|--------|
| minisql-master | 67% | 70%+ |
| minisql-regionserver | 8% | 60%+ (excl. generated code) |
| minisql-client | 37% | 50%+ |
| minisql-admin | 77% | 80%+ |

## Test Data Management

- Unit tests: Mock objects, no external dependencies
- Integration tests: Embedded Zookeeper (Curator TestingServer), in-process gRPC
- E2E tests: Testcontainers for Zookeeper, real TCP connections
- Stress tests: In-process gRPC with concurrent clients
- Cleanup: @After / GrpcCleanupRule for all resources
