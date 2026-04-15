# 基础框架与gRPC接口定义 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建分布式MiniSQL系统的基础框架，定义所有模块间的gRPC通信接口

**Architecture:** 采用Maven多模块项目结构，使用gRPC作为RPC框架，定义Master、RegionServer、Client之间的通信协议

**Tech Stack:** Java 11, Maven, gRPC 1.50+, Protocol Buffers 3

---

## 文件结构

```
distributed-minisql/
├── pom.xml                           # 父POM
├── minisql-common/                   # 公共模块
│   ├── pom.xml
│   └── src/main/
│       ├── proto/                    # Protocol Buffers定义
│       │   ├── master.proto
│       │   ├── regionserver.proto
│       │   └── common.proto
│       └── java/com/minisql/common/
│           ├── config/
│           │   └── Configuration.java
│           └── model/
│               ├── RegionInfo.java
│               ├── TableSchema.java
│               └── RegionRouteTable.java
├── minisql-master/                   # Master模块
│   ├── pom.xml
│   └── src/main/java/com/minisql/master/
│       └── MasterServer.java
├── minisql-regionserver/             # RegionServer模块
│   ├── pom.xml
│   └── src/main/java/com/minisql/regionserver/
│       └── RegionServerMain.java
├── minisql-client/                   # Client模块
│   ├── pom.xml
│   └── src/main/java/com/minisql/client/
│       └── MiniSQLClient.java
└── README.md
```

---

### Task 1: 创建Maven父项目

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`

- [ ] **Step 1: 创建父POM文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.minisql</groupId>
    <artifactId>distributed-minisql</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Distributed MiniSQL</name>
    <description>A distributed database system for educational purposes</description>

    <modules>
        <module>minisql-common</module>
        <module>minisql-master</module>
        <module>minisql-regionserver</module>
        <module>minisql-client</module>
    </modules>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <grpc.version>1.50.0</grpc.version>
        <protobuf.version>3.21.7</protobuf.version>
        <slf4j.version>2.0.5</slf4j.version>
        <logback.version>1.4.5</logback.version>
        <junit.version>5.9.1</junit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- gRPC -->
            <dependency>
                <groupId>io.grpc</groupId>
                <artifactId>grpc-netty-shaded</artifactId>
                <version>${grpc.version}</version>
            </dependency>
            <dependency>
                <groupId>io.grpc</groupId>
                <artifactId>grpc-protobuf</artifactId>
                <version>${grpc.version}</version>
            </dependency>
            <dependency>
                <groupId>io.grpc</groupId>
                <artifactId>grpc-stub</artifactId>
                <version>${grpc.version}</version>
            </dependency>
            
            <!-- Logging -->
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>
            <dependency>
                <groupId>ch.qos.logback</groupId>
                <artifactId>logback-classic</artifactId>
                <version>${logback.version}</version>
            </dependency>
            
            <!-- Testing -->
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.10.1</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: 创建.gitignore文件**

```
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# OS
.DS_Store
Thumbs.db

# Logs
*.log

# Generated sources
**/generated-sources/
```

- [ ] **Step 3: 验证Maven配置**

Run: `mvn clean validate`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add pom.xml .gitignore
git commit -m "build: initialize Maven parent project structure"
```

### Task 2: 创建minisql-common模块

**Files:**
- Create: `minisql-common/pom.xml`
- Create: `minisql-common/src/main/proto/common.proto`

- [ ] **Step 1: 创建common模块POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.minisql</groupId>
        <artifactId>distributed-minisql</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>minisql-common</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty-shaded</artifactId>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-protobuf</artifactId>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-stub</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.7.0</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.6.1</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:3.21.7:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.50.0:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建common.proto定义公共数据结构**

```protobuf
syntax = "proto3";

package minisql.common;

option java_multiple_files = true;
option java_package = "com.minisql.common.proto";
option java_outer_classname = "CommonProto";

// Region状态
enum RegionState {
  ONLINE = 0;
  SPLITTING = 1;
  OFFLINE = 2;
}

// Region信息
message RegionInfo {
  string region_id = 1;
  string table_name = 2;
  bytes start_key = 3;
  bytes end_key = 4;
  string region_server = 5;
  repeated string replicas = 6;
  RegionState state = 7;
  int64 size = 8;
}

// 表结构定义
message TableSchema {
  string table_name = 1;
  repeated ColumnSchema columns = 2;
  string primary_key = 3;
}

message ColumnSchema {
  string name = 1;
  string type = 2;
  bool nullable = 3;
}

// Region路由表
message RegionRouteTable {
  string table_name = 1;
  repeated RegionInfo regions = 2;
  int64 version = 3;
}

// 通用响应
message CommonResponse {
  bool success = 1;
  string message = 2;
}
```

- [ ] **Step 3: 编译生成Java代码**

Run: `cd minisql-common && mvn clean compile`
Expected: BUILD SUCCESS, generated sources in target/generated-sources/

- [ ] **Step 4: 提交**

```bash
git add minisql-common/
git commit -m "feat(common): add common module with protobuf definitions"
```

### Task 3: 定义Master服务gRPC接口

**Files:**
- Create: `minisql-common/src/main/proto/master.proto`

- [ ] **Step 1: 创建master.proto**

```protobuf
syntax = "proto3";

package minisql.master;

import "common.proto";

option java_multiple_files = true;
option java_package = "com.minisql.master.proto";
option java_outer_classname = "MasterProto";

service MasterService {
  // Region管理
  rpc AssignRegion(AssignRegionRequest) returns (minisql.common.RegionInfo);
  rpc ReportRegionSplit(ReportRegionSplitRequest) returns (minisql.common.CommonResponse);
  rpc GetRegionRouteTable(GetRouteTableRequest) returns (minisql.common.RegionRouteTable);
  
  // RegionServer管理
  rpc RegisterRegionServer(RegisterRequest) returns (RegisterResponse);
  rpc ReportHeartbeat(HeartbeatRequest) returns (HeartbeatResponse);
  
  // 表管理
  rpc CreateTable(CreateTableRequest) returns (minisql.common.CommonResponse);
  rpc DropTable(DropTableRequest) returns (minisql.common.CommonResponse);
  rpc GetTableSchema(GetTableSchemaRequest) returns (minisql.common.TableSchema);
}

// Region分配请求
message AssignRegionRequest {
  string table_name = 1;
}

// Region分裂报告
message ReportRegionSplitRequest {
  string region_id = 1;
  repeated minisql.common.RegionInfo new_regions = 2;
}

// 获取路由表请求
message GetRouteTableRequest {
  string table_name = 1;
}

// RegionServer注册
message RegisterRequest {
  string server_id = 1;
  string host = 2;
  int32 port = 3;
}

message RegisterResponse {
  bool success = 1;
  string message = 2;
}

// 心跳请求
message HeartbeatRequest {
  string server_id = 1;
  repeated minisql.common.RegionInfo regions = 2;
  int64 timestamp = 3;
}

message HeartbeatResponse {
  bool success = 1;
}

// 创建表请求
message CreateTableRequest {
  minisql.common.TableSchema schema = 1;
}

// 删除表请求
message DropTableRequest {
  string table_name = 1;
}

// 获取表结构请求
message GetTableSchemaRequest {
  string table_name = 1;
}
```

- [ ] **Step 2: 编译验证**

Run: `cd minisql-common && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add minisql-common/src/main/proto/master.proto
git commit -m "feat(common): define Master service gRPC interface"
```

---

### Task 4: 定义RegionServer服务gRPC接口

**Files:**
- Create: `minisql-common/src/main/proto/regionserver.proto`

- [ ] **Step 1: 创建regionserver.proto**

```protobuf
syntax = "proto3";

package minisql.regionserver;

import "common.proto";

option java_multiple_files = true;
option java_package = "com.minisql.regionserver.proto";
option java_outer_classname = "RegionServerProto";

service RegionServerService {
  // 数据操作
  rpc Get(GetRequest) returns (GetResponse);
  rpc Put(PutRequest) returns (PutResponse);
  rpc Delete(DeleteRequest) returns (DeleteResponse);
  rpc Scan(ScanRequest) returns (stream ScanResponse);
  
  // Region管理
  rpc OpenRegion(OpenRegionRequest) returns (minisql.common.CommonResponse);
  rpc CloseRegion(CloseRegionRequest) returns (minisql.common.CommonResponse);
  rpc SplitRegion(SplitRegionRequest) returns (SplitRegionResponse);
  
  // 副本同步
  rpc ReplicateLog(ReplicateLogRequest) returns (ReplicateLogResponse);
  rpc SyncRegion(SyncRegionRequest) returns (SyncRegionResponse);
}

// Get请求
message GetRequest {
  string table_name = 1;
  bytes key = 2;
}

message GetResponse {
  bool found = 1;
  bytes value = 2;
}

// Put请求
message PutRequest {
  string table_name = 1;
  bytes key = 2;
  bytes value = 3;
}

message PutResponse {
  bool success = 1;
}

// Delete请求
message DeleteRequest {
  string table_name = 1;
  bytes key = 2;
}

message DeleteResponse {
  bool success = 1;
}

// Scan请求
message ScanRequest {
  string table_name = 1;
  bytes start_key = 2;
  bytes end_key = 3;
  int32 limit = 4;
}

message ScanResponse {
  bytes key = 1;
  bytes value = 2;
}

// Region操作
message OpenRegionRequest {
  minisql.common.RegionInfo region = 1;
}

message CloseRegionRequest {
  string region_id = 1;
}

message SplitRegionRequest {
  string region_id = 1;
}

message SplitRegionResponse {
  bool success = 1;
  repeated minisql.common.RegionInfo new_regions = 2;
}

// 副本同步
message LogEntry {
  int64 sequence_id = 1;
  int64 timestamp = 2;
  string region_id = 3;
  string operation = 4;
  bytes data = 5;
}

message ReplicateLogRequest {
  string region_id = 1;
  repeated LogEntry logs = 2;
}

message ReplicateLogResponse {
  bool success = 1;
}

message SyncRegionRequest {
  string region_id = 1;
  int64 from_sequence = 2;
}

message SyncRegionResponse {
  bool success = 1;
  repeated LogEntry logs = 2;
}
```

- [ ] **Step 2: 编译验证**

Run: `cd minisql-common && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add minisql-common/src/main/proto/regionserver.proto
git commit -m "feat(common): define RegionServer service gRPC interface"
```

### Task 5: 创建Master模块骨架

**Files:**
- Create: `minisql-master/pom.xml`
- Create: `minisql-master/src/main/java/com/minisql/master/MasterServer.java`
- Create: `minisql-master/src/main/resources/logback.xml`

- [ ] **Step 1: 创建Master模块POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.minisql</groupId>
        <artifactId>distributed-minisql</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>minisql-master</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.minisql</groupId>
            <artifactId>minisql-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建MasterServer主类**

```java
package com.minisql.master;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MasterServer {
    private static final Logger logger = LoggerFactory.getLogger(MasterServer.class);
    private static final int DEFAULT_PORT = 8000;
    
    private Server server;
    private final int port;
    
    public MasterServer(int port) {
        this.port = port;
    }
    
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .build()
                .start();
        logger.info("Master server started on port {}", port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Master server");
            MasterServer.this.stop();
        }));
    }
    
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        MasterServer server = new MasterServer(port);
        server.start();
        server.blockUntilShutdown();
    }
}
```

- [ ] **Step 3: 创建logback配置**

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>[%d{yyyy-MM-dd HH:mm:ss}] [%level] [%logger{36}] [%thread] %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 测试启动**

Run: `cd minisql-master && mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"`
Expected: "Master server started on port 8000"
Press Ctrl+C to stop

- [ ] **Step 6: 提交**

```bash
git add minisql-master/
git commit -m "feat(master): add Master server skeleton"
```

---

### Task 6: 创建RegionServer模块骨架

**Files:**
- Create: `minisql-regionserver/pom.xml`
- Create: `minisql-regionserver/src/main/java/com/minisql/regionserver/RegionServerMain.java`
- Create: `minisql-regionserver/src/main/resources/logback.xml`

- [ ] **Step 1: 创建RegionServer模块POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.minisql</groupId>
        <artifactId>distributed-minisql</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>minisql-regionserver</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.minisql</groupId>
            <artifactId>minisql-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建RegionServerMain主类**

```java
package com.minisql.regionserver;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RegionServerMain {
    private static final Logger logger = LoggerFactory.getLogger(RegionServerMain.class);
    private static final int DEFAULT_PORT = 8001;
    
    private Server server;
    private final int port;
    private final String serverId;
    
    public RegionServerMain(String serverId, int port) {
        this.serverId = serverId;
        this.port = port;
    }
    
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .build()
                .start();
        logger.info("RegionServer {} started on port {}", serverId, port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down RegionServer {}", serverId);
            RegionServerMain.this.stop();
        }));
    }
    
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: RegionServerMain <server-id> [port]");
            System.exit(1);
        }
        
        String serverId = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        
        RegionServerMain server = new RegionServerMain(serverId, port);
        server.start();
        server.blockUntilShutdown();
    }
}
```

- [ ] **Step 3: 创建logback配置**

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>[%d{yyyy-MM-dd HH:mm:ss}] [%level] [%logger{36}] [%thread] %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 测试启动**

Run: `cd minisql-regionserver && mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" -Dexec.args="rs-001"`
Expected: "RegionServer rs-001 started on port 8001"
Press Ctrl+C to stop

- [ ] **Step 6: 提交**

```bash
git add minisql-regionserver/
git commit -m "feat(regionserver): add RegionServer skeleton"
```

### Task 7: 创建Client模块骨架

**Files:**
- Create: `minisql-client/pom.xml`
- Create: `minisql-client/src/main/java/com/minisql/client/MiniSQLClient.java`

- [ ] **Step 1: 创建Client模块POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.minisql</groupId>
        <artifactId>distributed-minisql</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>minisql-client</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.minisql</groupId>
            <artifactId>minisql-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建MiniSQLClient接口**

```java
package com.minisql.client;

import java.io.Closeable;

public interface MiniSQLClient extends Closeable {
    
    /**
     * 连接到Master节点
     * @param masterAddress Master地址，格式：host:port
     */
    void connect(String masterAddress);
    
    /**
     * 创建表
     * @param sql CREATE TABLE语句
     */
    void createTable(String sql);
    
    /**
     * 删除表
     * @param tableName 表名
     */
    void dropTable(String tableName);
    
    /**
     * 执行查询
     * @param sql SELECT语句
     * @return 查询结果
     */
    ResultSet executeQuery(String sql);
    
    /**
     * 执行更新
     * @param sql INSERT/UPDATE/DELETE语句
     * @return 影响的行数
     */
    int executeUpdate(String sql);
    
    /**
     * 关闭客户端连接
     */
    @Override
    void close();
}
```

- [ ] **Step 3: 创建ResultSet接口**

```java
package com.minisql.client;

import java.util.List;

public interface ResultSet {
    
    /**
     * 移动到下一行
     * @return 如果有下一行返回true，否则返回false
     */
    boolean next();
    
    /**
     * 获取当前行的字符串值
     * @param columnName 列名
     * @return 列值
     */
    String getString(String columnName);
    
    /**
     * 获取当前行的整数值
     * @param columnName 列名
     * @return 列值
     */
    int getInt(String columnName);
    
    /**
     * 获取当前行的长整数值
     * @param columnName 列名
     * @return 列值
     */
    long getLong(String columnName);
    
    /**
     * 获取所有列名
     * @return 列名列表
     */
    List<String> getColumnNames();
    
    /**
     * 关闭结果集
     */
    void close();
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add minisql-client/
git commit -m "feat(client): add Client module skeleton with interfaces"
```

---

### Task 8: 创建README文档

**Files:**
- Create: `README.md`

- [ ] **Step 1: 创建README**

```markdown
# Distributed MiniSQL System

一个用于教学目的的分布式数据库系统，实现了数据分片、副本管理、分布式查询等核心功能。

## 系统架构

- **Master节点**：集群管理、Region分配、负载均衡
- **RegionServer节点**：数据存储、查询执行、副本管理
- **Client**：客户端SDK，支持多语言

## 技术栈

- Java 11
- Maven
- gRPC 1.50+
- MySQL 8.0+
- Apache Zookeeper 3.8+

## 模块结构

```
distributed-minisql/
├── minisql-common/        # 公共模块（gRPC接口定义、数据模型）
├── minisql-master/        # Master服务
├── minisql-regionserver/  # RegionServer服务
└── minisql-client/        # 客户端SDK
```

## 快速开始

### 编译项目

```bash
mvn clean install
```

### 启动Master

```bash
cd minisql-master
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"
```

### 启动RegionServer

```bash
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" -Dexec.args="rs-001 8001"
```

## 开发文档

详细的架构设计和实施计划请参考：
- [架构设计文档](docs/superpowers/specs/2026-04-15-distributed-minisql-design.md)
- [团队分工方案](docs/team-division.md)
- [实施计划](docs/superpowers/plans/)

## 团队

本项目由5人团队开发：
- 成员1：架构负责人 + Master模块
- 成员2：RegionServer模块
- 成员3：副本管理与一致性
- 成员4：客户端与分布式查询
- 成员5：测试、工具与文档

## License

MIT License
```

- [ ] **Step 2: 提交**

```bash
git add README.md
git commit -m "docs: add project README"
```

---

## 验证清单

完成所有任务后，验证以下内容：

- [ ] Maven父项目可以成功编译：`mvn clean install`
- [ ] 所有protobuf文件生成Java代码成功
- [ ] Master服务可以启动
- [ ] RegionServer服务可以启动
- [ ] 所有模块的依赖关系正确
- [ ] 日志输出格式统一
- [ ] Git提交历史清晰

---

**计划完成！**

下一步建议：
1. 实施Master服务核心功能（Region管理、集群管理）
2. 实施RegionServer服务核心功能（数据存储、查询执行）
3. 实施副本管理和一致性协议
4. 实施客户端SDK和分布式查询

