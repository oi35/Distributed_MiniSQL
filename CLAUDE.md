# Distributed MiniSQL Project - Claude Code Configuration

## Project Overview

This is a distributed database system for educational purposes, implementing:
- Data sharding with range-based partitioning
- Replica management with Paxos consensus
- Distributed query execution
- Master-RegionServer architecture

## Team Structure

5-person team with specialized roles:
- **Member 1**: Architecture + Master module
- **Member 2**: RegionServer module
- **Member 3**: Replication + Consensus
- **Member 4**: Client SDK + Distributed queries
- **Member 5**: Testing + Tools + Documentation

## Project-Specific Skills

Use these skills for domain-specific tasks:

### `distributed-system-debug`
Debug distributed system issues including network failures, consensus problems, and data inconsistencies.

**When to use:**
- Master-RegionServer communication failures
- Region split/migration issues
- Paxos consensus failures
- Data inconsistency between replicas

### `grpc-interface-design`
Design and implement gRPC service interfaces following project conventions.

**When to use:**
- Adding new RPC methods
- Modifying protobuf definitions
- Reviewing gRPC interface changes

### `region-management`
Implement and debug Region lifecycle operations including split, merge, and migration.

**When to use:**
- Implementing Region split logic
- Implementing Region migration
- Debugging Region state issues

### `paxos-implementation`
Implement and debug Paxos consensus protocol for replica consistency.

**When to use:**
- Implementing write operations with Paxos
- Debugging consensus failures
- Handling replica failures during consensus

## Specialist Agents

Use these agents for module-specific development:

### `master-specialist`
Expert in Master module development - cluster management, region allocation, load balancing.

**Dispatch for:**
- Master service implementation
- Cluster management features
- Load balancing algorithms
- Zookeeper integration

### `regionserver-specialist`
Expert in RegionServer module - data storage, query execution, region lifecycle.

**Dispatch for:**
- RegionServer service implementation
- MySQL integration
- Query execution
- Region split operations

### `replication-specialist`
Expert in replication and consensus - Paxos protocol, WAL logs, replica synchronization.

**Dispatch for:**
- Paxos implementation
- WAL system
- Replica synchronization
- Failure recovery

### `client-specialist`
Expert in Client SDK - connection management, query routing, distributed join.

**Dispatch for:**
- Client API implementation
- Route caching
- Distributed query execution
- Hash Join implementation

## Development Workflow

### 1. Planning Phase

Before implementing features:
1. Review design document: `docs/superpowers/specs/2026-04-15-distributed-minisql-design.md`
2. Check implementation plan: `docs/superpowers/plans/`
3. Coordinate with team members for interface dependencies

### 2. Implementation Phase

Follow TDD approach:
1. Write failing test
2. Implement minimal code to pass
3. Refactor
4. Commit frequently

### 3. Testing Phase

Test at multiple levels:
- Unit tests: Individual components
- Integration tests: Module interactions
- System tests: End-to-end scenarios
- Failure tests: Fault injection

### 4. Code Review

All code requires review:
- Use Pull Request workflow
- At least one reviewer
- Architecture lead reviews critical modules

## Code Standards

### Java Style

- Follow Google Java Style Guide
- Use SLF4J for logging
- Format: `[timestamp] [level] [component] [thread] message`

### Testing

- JUnit 5 for unit tests
- Mockito for mocking
- Coverage > 70% (critical modules > 85%)

### Git Commits

Format: `<type>(<scope>): <description>`

Types:
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `test`: Add tests
- `docs`: Documentation
- `build`: Build system changes

Examples:
```
feat(master): implement region assignment logic
fix(regionserver): handle region split edge case
test(paxos): add concurrent proposal tests
```

## Module Dependencies

```
minisql-common (protobuf definitions)
    ↓
    ├── minisql-master
    ├── minisql-regionserver
    └── minisql-client
```

## Build Commands

```bash
# Build all modules
mvn clean install

# Build specific module
cd minisql-master && mvn clean install

# Run tests
mvn test

# Run specific test
mvn test -Dtest=MasterServiceTest

# Generate protobuf
cd minisql-common && mvn clean compile
```

## Running Services

### Start Master
```bash
cd minisql-master
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"
```

### Start RegionServer
```bash
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" -Dexec.args="rs-001 8001"
```

## Common Issues

### Issue: Protobuf compilation fails

**Solution:**
```bash
cd minisql-common
mvn clean
mvn compile
```

### Issue: gRPC connection refused

**Check:**
1. Master/RegionServer is running
2. Port is not blocked by firewall
3. Correct host:port in configuration

### Issue: Region not found

**Debug:**
1. Check Master's route table
2. Verify RegionServer has region loaded
3. Refresh client cache

## Documentation

- Architecture: `docs/superpowers/specs/2026-04-15-distributed-minisql-design.md`
- Team division: `docs/team-division.md`
- Implementation plans: `docs/superpowers/plans/`
- API docs: Generate with `mvn javadoc:javadoc`

## Performance Targets

- Single table point query: < 10ms
- Single table range query: > 1000 QPS
- Two table join query: < 100ms
- System availability: > 99%

## When to Ask for Help

- Unclear requirements → Ask user for clarification
- Design decisions → Consult architecture lead (Member 1)
- Interface conflicts → Coordinate with relevant team member
- Stuck on bug → Use `distributed-system-debug` skill

## Autonomous Work Guidelines

When working autonomously:
1. Follow implementation plans strictly
2. Write tests before implementation
3. Commit after each completed task
4. Document non-obvious decisions
5. Flag blocking issues immediately

## Remember

- This is an educational project - clarity over optimization
- Distributed systems are complex - test thoroughly
- Communication is key - coordinate with team
- Document your work - help future maintainers
