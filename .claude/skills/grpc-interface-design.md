---
name: grpc-interface-design
description: Design and implement gRPC service interfaces following project conventions
type: skill
---

# gRPC Interface Design Skill

Use this skill when designing or modifying gRPC service interfaces for MiniSQL.

## When to Use

- Adding new RPC methods to Master or RegionServer
- Designing new service interfaces
- Modifying existing protobuf definitions
- Reviewing gRPC interface changes

## Design Principles

### 1. Follow Naming Conventions

**Services:** Use `Service` suffix
- `MasterService`
- `RegionServerService`

**Methods:** Use verb-noun pattern
- `GetRegion`, `CreateTable`, `ReplicateLog`

**Messages:** Use request/response suffix
- `GetRegionRequest`, `GetRegionResponse`

### 2. Use Common Types

Import from `common.proto` for shared types:
```protobuf
import "common.proto";

message MyRequest {
  minisql.common.RegionInfo region = 1;
}
```

### 3. Design for Evolution

- Use optional fields for backward compatibility
- Reserve field numbers for deprecated fields
- Add new fields at the end

### 4. Error Handling

Always include success/error information:
```protobuf
message MyResponse {
  bool success = 1;
  string message = 2;  // Error message if success=false
  MyData data = 3;     // Actual data if success=true
}
```

## Workflow

### Step 1: Define the Interface

Write the `.proto` file in `minisql-common/src/main/proto/`

### Step 2: Generate Java Code

```bash
cd minisql-common
mvn clean compile
```

### Step 3: Implement Service

Create service implementation class:
```java
public class MyServiceImpl extends MyServiceGrpc.MyServiceImplBase {
    @Override
    public void myMethod(MyRequest request, StreamObserver<MyResponse> responseObserver) {
        // Implementation
    }
}
```

### Step 4: Register Service

Add to server builder:
```java
server = ServerBuilder.forPort(port)
    .addService(new MyServiceImpl())
    .build();
```

### Step 5: Write Tests

Test both client and server:
```java
@Test
void testMyMethod() {
    MyRequest request = MyRequest.newBuilder()
        .setField("value")
        .build();
    MyResponse response = blockingStub.myMethod(request);
    assertTrue(response.getSuccess());
}
```

## Examples

### Streaming Response

```protobuf
service RegionServerService {
  rpc Scan(ScanRequest) returns (stream ScanResponse);
}
```

### Bidirectional Streaming

```protobuf
service ReplicationService {
  rpc StreamLogs(stream LogEntry) returns (stream LogAck);
}
```
