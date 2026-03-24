# 总体架构

## 1. 当前真实部署形态

当前仓库的真实实现是“模块化单体 + 两个 MT5 EA + MariaDB + Redis + Web Console”，不是完整的微服务集群。

实际运行单元：

1. Master EA：`mt5/Websock_Sender_Master_v0.mq5`
2. Spring Boot 应用：内部包含 user-auth、account-config、signal-ingest、copy-engine、follower-exec、monitor 等模块
3. Follower EA：`mt5/Websock_Receiver_Follower_Exec_v0.mq5`
4. MariaDB：配置、执行历史、dispatch 历史、审计历史、恢复基线
5. Redis：热状态、缓存、会话共享、信号去重、热路径索引、持仓台账热副本
6. Web Console：`web-console/`

## 2. 模块分层

### 2.1 接入层

1. Web Console
2. Master EA 上行 WebSocket
3. Follower EA 下行 WebSocket

### 2.2 平台层

1. `user-auth`
   负责平台用户注册、登录、会话和 `/api/auth/*`
2. `account-config`
   负责 MT5 账户、风控、关系、symbol mapping、share 配置和相关缓存投影
3. `signal-ingest`
   负责 `/ws/trade` 握手、信号标准化、去重、事件发布
4. `copy-engine`
   负责 command/dispatch 生成、风控计算、热路径写入和异步持久化
5. `follower-exec`
   负责 `/ws/follower-exec`、backlog 回放、实时 dispatch 下发、ACK/FAIL 回写
6. `monitor`
   负责 runtime-state、signal audit、会话视图、命令/dispatch 追踪和监控聚合

### 2.3 数据层

1. MariaDB
2. Redis

## 3. 数据所有权

### 3.1 MariaDB 持有的 durable truth

1. 平台用户与会话
2. MT5 账户
3. 风控规则
4. 主从关系
5. Symbol mapping
6. Share 配置
7. Signal audit
8. `execution_commands`
9. `follower_dispatch_outbox`
10. runtime-state 快照
11. open-position ledger

### 3.2 Redis 持有的热状态与协调数据

1. `copy:account:binding:*`
2. `copy:route:*`
3. `copy:account:risk:*`
4. `copy:signal:dedup:*`
5. `copy:runtime:*`
6. `copy:runtime:positions:*`
7. `copy:ws:mt5:*`
8. `copy:ws:follower:*`
9. `copy:hot:*`
10. `copy:hot:seq:*`

Redis 的职责是“加速和共享当前状态”，不是替代 MariaDB。

## 4. 两条主 WebSocket 链路

### 4.1 Master 上行链路

1. Master EA 连接 `/ws/trade`
2. 发送 `HELLO / HEARTBEAT / DEAL / ORDER`
3. Java 标准化信号
4. 写 signal audit
5. `Mt5SignalAcceptedEvent` 被 `publishEvent()` 同步发布
6. Copy Engine 消费事件，**并行**处理各 follower（专用线程池 + 独立事务）

### 4.2 Follower 下行链路

1. Follower EA 连接 `/ws/follower-exec`
2. 发送 `HELLO / HEARTBEAT`
3. Java 绑定 follower 账户、更新 runtime-state、回放 backlog
4. Java 推送 `DISPATCH`
5. Follower EA 执行后回 `ACK / FAIL`
6. Java 回写 dispatch 状态

## 5. 热路径模式

### 5.1 `DATABASE`

1. signal audit、command、dispatch 直接走 JPA / MySQL
2. 实时下发依赖数据库行已经生成

### 5.2 `REDIS_QUEUE`

1. signal audit、command、dispatch 先写 Redis 热状态
2. follower 实时推送不等待 MySQL 事务提交
3. 异步 worker 再把热状态持久化到 MySQL
4. 启动时自动把 `copy:hot:seq:*` 对齐到数据库最大 ID，避免重启后 ID 回退

## 6. 监控架构

监控不是独立采集系统，而是对现有业务状态的聚合读模型：

1. signal-ingest 提供信号审计
2. master 与 follower 的 `HELLO / HEARTBEAT` 提供 runtime-state
3. session registry 提供 WebSocket 会话视图
4. copy-engine 提供 command / dispatch / trace 视图
5. Web Console 调用监控聚合 API 生成账户概览与详情

## 7. 启动、预热与恢复

当前恢复链路已经包含 3 类动作：

1. 从 MariaDB 预热 route/risk/account-binding/runtime-state/open-position ledger
2. 启动时对齐 `copy:hot:seq:*` 到数据库最大 signal / command / dispatch ID
3. 等待 MT5 `HELLO / HEARTBEAT` 上报当前持仓，再把 Redis 热状态与 ledger 修正到最新

这意味着：

1. Redis 丢失后可以靠 MariaDB 恢复基线
2. Redis 恢复后不会把 command / dispatch ID 回滚
3. 持仓跟踪最终仍会被 MT5 当前真实持仓纠正

## 8. 当前架构边界

当前仓库没有完整实现以下目标态能力：

1. 外部 MQ 骨干
2. durable claim/lease 式多实例分发补偿
3. 独立 API Gateway
4. 独立 Notification Service
5. 独立 Agent / Scheduler Service
6. 多 broker 执行 worker 编排

所以当前文档的重点不是“理想微服务蓝图”，而是“当前代码真实如何运行”。

## 9. 详细链路入口

更完整的模块交互、数据主权和端到端步骤，见：

1. [模块交互与端到端数据链路](./system-modules-and-dataflows.md)
