# Claude Code 配置使用指南

本项目包含专门为分布式MiniSQL系统定制的Claude Code配置。

## 📁 配置结构

```
.claude/
├── skills/                          # 项目专属技能
│   ├── distributed-system-debug.md  # 分布式系统调试
│   ├── grpc-interface-design.md     # gRPC接口设计
│   ├── region-management.md         # Region管理
│   └── paxos-implementation.md      # Paxos实现
├── agents/                          # 专家代理
│   ├── master-specialist.md         # Master模块专家
│   ├── regionserver-specialist.md   # RegionServer专家
│   ├── replication-specialist.md    # 副本管理专家
│   └── client-specialist.md         # 客户端专家
├── settings.json                    # 项目配置
└── .gitignore                       # Git忽略规则
```

## 🚀 如何使用

### 方法1：直接阅读技能文档

技能文档包含了详细的实施指南和最佳实践：

```bash
# 查看分布式系统调试指南
cat .claude/skills/distributed-system-debug.md

# 查看gRPC接口设计指南
cat .claude/skills/grpc-interface-design.md

# 查看Region管理指南
cat .claude/skills/region-management.md

# 查看Paxos实现指南
cat .claude/skills/paxos-implementation.md
```

### 方法2：阅读专家代理文档

专家代理文档描述了各模块的开发规范：

```bash
# 查看Master模块开发指南
cat .claude/agents/master-specialist.md

# 查看RegionServer模块开发指南
cat .claude/agents/regionserver-specialist.md

# 查看副本管理开发指南
cat .claude/agents/replication-specialist.md

# 查看客户端开发指南
cat .claude/agents/client-specialist.md
```

### 方法3：在对话中引用

在与Claude对话时，可以明确引用这些配置：

```
"请按照 .claude/skills/grpc-interface-design.md 中的规范，
为Master服务添加一个新的RPC方法"

"作为Master模块的开发者，请参考 .claude/agents/master-specialist.md
中的指南，实现RegionServer心跳监控功能"
```

## 📚 技能说明

### distributed-system-debug
**用途：** 调试分布式系统问题

**适用场景：**
- Master-RegionServer通信失败
- Region分裂或迁移问题
- Paxos共识失败
- 副本间数据不一致
- 网络分区场景
- Zookeeper连接问题

**包含内容：**
- 5步调试工作流
- 常见问题及解决方案
- 系统状态检查命令

### grpc-interface-design
**用途：** 设计和实现gRPC服务接口

**适用场景：**
- 添加新的RPC方法
- 设计新的服务接口
- 修改现有protobuf定义
- 审查gRPC接口变更

**包含内容：**
- 命名规范
- 设计原则
- 实现工作流
- 测试示例

### region-management
**用途：** 实现和调试Region生命周期管理

**适用场景：**
- 实现Region分裂逻辑
- 实现Region迁移
- 调试Region状态问题
- 处理Region分配

**包含内容：**
- Region状态机
- 分裂工作流（6步）
- 迁移工作流（5步）
- 常见问题及恢复方案

### paxos-implementation
**用途：** 实现和调试Paxos共识协议

**适用场景：**
- 实现带Paxos的写操作
- 调试共识失败
- 处理副本故障
- 优化Paxos性能

**包含内容：**
- Paxos三阶段实现
- 简化版两阶段提交
- 故障处理
- 性能优化（Multi-Paxos、批处理）

## 👥 专家代理说明

### master-specialist
**专长：** Master模块开发

**负责：**
- 集群管理和RegionServer协调
- Region分配和分配策略
- 负载均衡算法
- 元数据管理（Zookeeper）
- Master高可用（HA）

### regionserver-specialist
**专长：** RegionServer模块开发

**负责：**
- Region生命周期管理
- MySQL集成和JDBC操作
- 查询执行和优化
- 数据CRUD操作
- Region分裂逻辑

### replication-specialist
**专长：** 副本管理和一致性

**负责：**
- Paxos共识协议实现
- WAL日志系统设计
- 副本同步
- 故障检测和恢复
- 一致性保证

### client-specialist
**专长：** 客户端SDK开发

**负责：**
- 客户端连接管理
- Region路由缓存
- SQL解析和路由
- 分布式查询执行
- Join实现（Hash Join）

## 🔧 配置说明

### settings.json

包含项目级别的配置：

```json
{
  "skills": { ... },      // 技能启用状态
  "agents": { ... },      // 代理配置
  "permissions": { ... }, // 权限设置
  "preferences": { ... }  // 偏好设置
}
```

### settings.local.json

用户特定的配置（不提交到Git）：
- 个人偏好
- 本地路径
- 临时设置

## 📖 使用示例

### 示例1：实现Master心跳监控

1. 阅读Master专家指南：
```bash
cat .claude/agents/master-specialist.md
```

2. 查看分布式调试技能：
```bash
cat .claude/skills/distributed-system-debug.md
```

3. 在对话中说明：
```
我需要实现Master的RegionServer心跳监控功能。
请参考 master-specialist 的指南，实现以下功能：
- RegionServer每3秒发送心跳
- Master超过30秒未收到心跳则标记为DEAD
- 触发故障恢复流程
```

### 示例2：实现Region分裂

1. 阅读Region管理技能：
```bash
cat .claude/skills/region-management.md
```

2. 在对话中说明：
```
我需要实现Region分裂功能。
请按照 region-management 技能中的6步工作流实现：
1. 标记Region为SPLITTING状态
2. 找到分裂点（中位数key）
3. 创建两个子Region
4. 复制数据到子Region
5. 更新元数据
6. 激活子Region并删除父Region
```

### 示例3：调试Paxos问题

1. 阅读Paxos实现技能：
```bash
cat .claude/skills/paxos-implementation.md
```

2. 在对话中说明：
```
写操作超时，怀疑是Paxos共识问题。
请按照 paxos-implementation 技能中的调试步骤：
1. 检查网络延迟
2. 验证所有副本存活
3. 检查Paxos提案日志
4. 分析是否有并发提案冲突
```

## 🎯 最佳实践

1. **开始新任务前**：先阅读相关技能和代理文档
2. **遇到问题时**：查看distributed-system-debug技能
3. **设计接口时**：遵循grpc-interface-design规范
4. **实现功能时**：参考对应专家代理的指南
5. **代码审查时**：对照CLAUDE.md中的标准

## 🔄 更新配置

如果需要更新配置：

1. 修改相应的.md文件
2. 提交到Git
3. 通知团队成员
4. 更新本文档

## 📞 获取帮助

- 查看项目文档：`docs/`
- 阅读CLAUDE.md：项目级指南
- 查看实施计划：`docs/superpowers/plans/`
- 团队分工：`docs/team-division.md`

---

**注意：** 这些配置是项目专属的，旨在提供一致的开发指南和最佳实践。所有团队成员都应该熟悉这些配置。
