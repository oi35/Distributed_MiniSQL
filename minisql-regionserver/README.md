# MiniSQL RegionServer

RegionServer 是分布式 MiniSQL 系统的核心组件之一，负责数据存储、查询执行和 Region 生命周期管理。

## 功能特性

- **数据操作**: 支持 Put/Get/Delete/Exists 等基础 CRUD 操作 ✅
- **命令行界面**: 提供交互式命令行进行数据操作测试 ✅
- **内存存储**: 基于 TreeMap 的内存存储引擎，支持范围扫描 ✅
- **MySQL 存储**: HikariCP 连接池 + MySQL 持久化存储 ✅
- **可执行 JAR**: 支持打包为独立运行的 JAR 文件 ✅
- **日志记录**: 使用 SLF4J + Logback 进行结构化日志记录 ✅
- **配置管理**: 支持环境变量和配置文件 ✅
- **批量操作**: 支持 BatchPut/BatchGet/BatchDelete ✅
- **范围扫描**: 支持流式范围查询（正向/反向）✅
- **Region 管理**: Region 打开/关闭/迁移 ✅
- **副本同步**: WAL 日志同步 + Paxos 共识协议 ✅

## 快速开始

### 1. 编译项目

```bash
# 从项目根目录编译所有模块
cd Distributed_MiniSQL
mvn clean install

# 或单独编译 RegionServer
cd minisql-regionserver
mvn clean package
```

### 2. 启动 RegionServer

#### Linux/Mac
```bash
# 使用默认配置启动
./start-regionserver.sh

# 或指定参数
REGIONSERVER_ID=rs-001 REGIONSERVER_PORT=8001 ./start-regionserver.sh
```

#### Windows
```cmd
REM 使用默认配置启动
start-regionserver.bat

REM 或设置环境变量后启动
set REGIONSERVER_ID=rs-001
set REGIONSERVER_PORT=8001
start-regionserver.bat
```

### 3. 验证启动

启动成功后，你应该看到类似以下的日志输出：

```
2026-04-19 10:30:15.123 [main] INFO  [c.m.r.RegionServerMain] - RegionServer rs-001 started, listening on port 8001
=====================================
  RegionServer rs-001 Started
=====================================
Available commands:
  put <table> <key> <column>=<value> [column2=value2...]
  get <table> <key>
  delete <table> <key>
  exists <table> <key>
  list <table>
  exit
=====================================
RegionServer>
```

## 命令行界面使用

RegionServer 提供了交互式命令行界面，方便开发和测试。支持以下命令：

### 数据操作命令

```bash
# 插入数据 (支持多列)
put <table> <key> <column>=<value> [column2=value2...]
put users user001 name=John age=25 city=Beijing

# 查询数据
get <table> <key>
get users user001

# 删除数据
delete <table> <key>
delete users user001

# 检查键是否存在
exists <table> <key>
exists users user001

# 列出表中的所有键 (开发中)
list <table>
list users

# 退出程序
exit
```

### 使用示例

```
RegionServer> put users user001 name=John age=25
PUT successful

RegionServer> get users user001
Data:
  name = John
  age = 25

RegionServer> exists users user001
Key exists

RegionServer> delete users user001
DELETE successful (key existed)

RegionServer> exists users user001
Key not found

RegionServer> exit
RegionServer rs-001 stopped
```

## 配置说明

RegionServer 的配置位于 `src/main/resources/regionserver.conf`：

```properties
# 服务器设置
regionserver.id=rs-001
regionserver.port=8001
regionserver.host=0.0.0.0

# Master 连接设置
master.host=localhost
master.port=8000
master.heartbeat.interval.ms=3000

# MySQL 设置
mysql.host=localhost
mysql.port=3306
mysql.database=minisql
mysql.username=root
mysql.password=password

# Region 设置
region.max.size.mb=256
region.memstore.flush.threshold.mb=64
```

## API 接口

RegionServer 提供 gRPC 接口，详细定义见 `minisql-common/src/main/proto/regionserver.proto`。

### 基础数据操作

#### Put 操作
```java
// 插入数据
PutRequest request = PutRequest.newBuilder()
    .setTableName("users")
    .setRegionId("region-001")
    .putColumns("name", ByteString.copyFromUtf8("Alice"))
    .putColumns("age", ByteString.copyFromUtf8("30"))
    .build();
```

#### Get 操作
```java
// 查询数据
GetRequest request = GetRequest.newBuilder()
    .setTableName("users")
    .setRegionId("region-001")
    .setKey(ByteString.copyFromUtf8("user123"))
    .build();
```

## 开发计划

### Phase 1: 基础功能 ✅
- [x] 项目结构搭建
- [x] gRPC 服务框架
- [x] 基础 CRUD 操作
- [x] 内存存储实现
- [x] 单元测试

### Phase 2: 存储层 ✅
- [x] MySQL 集成（HikariCP 连接池）
- [x] 连接池配置
- [x] Schema 管理（按 Region 建表）
- [ ] 索引优化

### Phase 3: Region 管理 ✅
- [x] Region 生命周期（openRegion/closeRegion）
- [ ] Region 分裂逻辑
- [x] Region 迁移（migrateRegion）
- [ ] 负载均衡（由 Master 模块负责）

### Phase 4: 高级功能 ✅
- [x] 批量操作（BatchPut/BatchGet/BatchDelete）
- [x] 范围扫描（流式正向/反向）
- [x] 副本同步（ReplicationManager + ReplicationLogService）
- [x] WAL 日志（WalManager + WalService 含校验和）

## 测试

### 运行单元测试
```bash
mvn test
```

### 运行特定测试
```bash
mvn test -Dtest=RegionServerServiceImplTest
```

### 查看测试覆盖率
```bash
mvn jacoco:report
```

## 日志

日志文件位于 `logs/` 目录下：
- `regionserver.log`: 主要日志
- `regionserver-error.log`: 错误日志

## 故障排除

### 常见问题

1. **端口被占用**
   ```
   解决方案：修改 regionserver.conf 中的 regionserver.port，或停止占用端口的进程
   ```

2. **依赖编译失败**
   ```
   解决方案：确保 minisql-common 已编译：cd minisql-common && mvn clean install
   ```

3. **测试失败**
   ```
   解决方案：检查日志输出，确认 gRPC 服务是否正常启动
   ```

## 贡献指南

1. 遵循项目编码规范（Google Java Style）
2. 编写单元测试
3. 更新文档
4. 提交前运行 `mvn clean test`

## 相关文档

- [项目总体设计](../docs/superpowers/specs/2026-04-15-distributed-minisql-design.md)
- [接口设计](../docs/interface-design.md)
- [团队分工](../docs/team-division.md)
- [Claude 配置使用指南](../.claude/README.md)