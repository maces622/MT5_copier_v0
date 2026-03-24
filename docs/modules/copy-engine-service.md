# 跟单引擎

## 1. 当前职责

`copy-engine` 是当前系统的核心决策层，负责回答 3 个问题：

1. 这条 master 信号要不要复制
2. 要复制给哪些 follower
3. 每个 follower 应该以什么指令和什么手数执行

## 2. 主要输入与输出

### 输入

1. `signal-ingest` 发布的 `Mt5SignalAcceptedEvent`
2. account-binding 快照
3. route 快照
4. risk 快照
5. runtime-state
6. symbol mapping

### 输出

1. `execution_commands`
2. `follower_dispatch_outbox`
3. follower 实时下发触发
4. command / dispatch 查询数据

## 3. 当前支持的复制模式

1. `FIXED_LOT`
2. `BALANCE_RATIO`
3. `EQUITY_RATIO`
4. `FOLLOW_MASTER`

当前新建关系的默认模式已经收敛到 `BALANCE_RATIO`。

## 4. 当前支持的指令类型

1. `OPEN_POSITION`
2. `CLOSE_POSITION`
3. `SYNC_TP_SL`
4. `CREATE_PENDING_ORDER`
5. `UPDATE_PENDING_ORDER`
6. `CANCEL_PENDING_ORDER`

## 5. 当前主链路

1. `signal-ingest` 接收到 master `DEAL / ORDER`
2. `copy-engine` 根据 `server + login` 找到 master 平台账户
3. 读取该 master 的 route snapshot
4. **并行**对每个 follower 读取 risk / symbol mapping / runtime-state
5. 计算目标指令与目标手数
6. 生成 command
7. 对可执行 command 生成 dispatch
8. `follower-exec` 负责 backlog 回放与实时下发

> 单 follower 时内联执行，多 follower 时通过专用线程池 `copy-engine-follower` 并行处理（`CompletableFuture.allOf().join()`），每个 follower 独立事务（`TransactionTemplate`）。

## 6. 热路径模式

### 6.1 `DATABASE`

1. signal audit 直接持久化
2. command 直接持久化
3. dispatch 直接持久化
4. follower 下发建立在数据库记录已经存在的前提上

### 6.2 `REDIS_QUEUE`

1. signal audit 先写 Redis
2. command 先写 Redis
3. dispatch 先写 Redis
4. follower websocket 实时推送立即发生
5. 后台 worker 再把热状态刷回 MySQL

## 7. 当前关键保护

### 7.1 资金快照新鲜度门禁

比例跟单模式不会再静默退回 `1.0`：

1. follower runtime-state 必须存在
2. 必须通过新鲜度检查
3. balance / equity 必须有效

否则 command 会被显式拒绝。

### 7.2 dispatch 去重与唯一键冲突保护

当前已经修复并固化：

1. dispatch 创建前先检查 `executionCommandId` 是否已绑定到旧 dispatch
2. dispatch 异步持久化会先按 `dispatchId` 查
3. 查不到再按 `executionCommandId` 合并
4. 避免 `uk_dispatch_command` 冲突

### 7.3 follower 并行处理与错误隔离

1. 多 follower 时使用 `CompletableFuture.allOf()` 并行执行
2. 每个 follower 使用独立 `TransactionTemplate`，失败不影响其他 follower
3. 线程池大小可通过 `copier.copy-engine.hot-path.follower-parallelism` 配置
4. ID 分配使用 Redis `INCR`，天然线程安全

### 7.4 启动对齐热路径序列

应用启动会把：

1. `copy:hot:seq:signal`
2. `copy:hot:seq:command`
3. `copy:hot:seq:dispatch`

提升到数据库当前最大 ID，避免 Redis 恢复或重启后复用旧 ID。

## 8. 与其他模块的交互

1. `account-config`
   提供 account-binding、route、risk、symbol mapping
2. `signal-ingest`
   提供标准化 master 信号
3. `follower-exec`
   负责 dispatch 回放、实时下发和状态回写
4. `monitor`
   读取 command / dispatch / trace 形成监控视图

## 9. 当前边界

1. 还没有外部 MQ 骨干
2. 还没有 durable claim/lease 式多实例补偿
3. 还没有 broker 级 margin 预检查
4. 还没有成交后滑点二次核对

## 10. 相关文档

1. [总体架构](../architecture/overall-architecture.md)
2. [模块交互与端到端数据链路](../architecture/system-modules-and-dataflows.md)
3. [Follower Exec 服务](./follower-exec-service.md)
