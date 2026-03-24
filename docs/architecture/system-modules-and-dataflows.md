# 系统模块、交互与端到端数据链路

## 1. 适用范围

本文档描述的是当前仓库已经落地并可联调的真实系统，而不是未来拆分后的理想微服务形态。

当前真实拓扑：

1. 浏览器中的 Web Console
2. Spring Boot 单体应用中的多个业务模块
3. Master MT5 EA
4. Follower MT5 EA
5. MariaDB
6. Redis

## 2. 模块清单

| 模块 | 当前职责 | 主要输入 | 主要输出 | 依赖的数据层 |
| --- | --- | --- | --- | --- |
| Web Console | 账户配置、关系管理、分享绑定、监控查看、链路排障 | 用户操作、HTTP API | 配置写请求、监控读请求 | 通过后端访问 MariaDB / Redis 聚合结果 |
| User Auth | 平台用户注册、登录、会话、当前用户信息 | `/api/auth/*` | 当前用户上下文、会话 Cookie | MariaDB |
| Account Config | MT5 账户、风控、主从关系、symbol mapping、share 配置 | `/api/me/*`、`/api/accounts/*`、bootstrap | route/risk/account-binding 快照 | MariaDB + Redis |
| MT5 Bridge / Signal Ingest | 接收 Master EA 上行信号并标准化 | `/ws/trade`、`HELLO/HEARTBEAT/DEAL/ORDER` | `Mt5SignalAcceptedEvent`、signal audit、runtime-state 更新 | MariaDB + Redis |
| Copy Engine | 判断是否复制、生成 command/dispatch、热路径持久化 | 标准化信号、账户绑定、路由、风控、runtime-state | `execution_commands`、`follower_dispatch_outbox`、实时下发触发 | MariaDB + Redis |
| Follower Exec | 管理 Follower EA 会话、回放 backlog、实时下发、状态回写 | `/ws/follower-exec`、`HELLO/HEARTBEAT/ACK/FAIL` | `DISPATCH`、`STATUS_ACK`、dispatch 状态更新、runtime-state 更新 | MariaDB + Redis |
| Monitor | 聚合 runtime-state、signal、session、command、dispatch、trace | signal-ingest、follower-exec、copy-engine 的状态数据 | `/api/monitor/*` 读模型 | MariaDB + Redis |

## 3. 模块之间的交互关系

### 3.1 用户与控制台

1. Web Console 先经 `user-auth` 完成注册、登录、登出、当前用户查询。
2. Web Console 再调用 `account-config` 维护账户、关系、风控、symbol mapping、share 配置。
3. Web Console 调用 `monitor` 查看账户概览、详情、命令、dispatch 和 trace。

### 3.2 Master 上行

1. Master EA 把 MT5 信号发给 `signal-ingest`。
2. `signal-ingest` 标准化后更新 signal audit 与 runtime-state。
3. `signal-ingest` 通过应用内事件把信号交给 `copy-engine`。

### 3.3 跟单决策

1. `copy-engine` 先用 `account-config` 投影出来的 account-binding 找到 masterAccount。
2. 再读取 route/risk/symbol mapping/runtime-state。
3. **并行**对每个 follower 生成 command 和 dispatch（单 follower 内联执行，多 follower 专用线程池并行）。
4. 每个 follower 使用独立事务，单个失败不影响其他 follower。

### 3.4 Follower 下行

1. `copy-engine` 生成 dispatch 后，由 `follower-exec` 负责 backlog 回放和实时推送。
2. Follower EA 执行后回 `ACK / FAIL` 给 `follower-exec`。
3. `follower-exec` 更新 dispatch 状态，并把结果反映到监控读模型。

### 3.5 监控聚合

1. `signal-ingest` 贡献 signal audit、master runtime-state、master session。
2. `follower-exec` 贡献 follower runtime-state、follower session、dispatch 进度。
3. `copy-engine` 贡献 command、dispatch 和 trace 查询数据。
4. `monitor` 把这些状态拼成控制台所需视图。

## 4. 数据主权与存储边界

## 4.1 MariaDB 持有的 durable truth

1. `platform_users`
2. `platform_user_sessions`
3. `mt5_accounts`
4. `risk_rules`
5. `copy_relations`
6. `symbol_mappings`
7. `master_share_configs`
8. signal audit 实体
9. `execution_commands`
10. `follower_dispatch_outbox`
11. runtime-state 快照实体
12. open-position ledger 实体

## 4.2 Redis 持有的热状态

1. `copy:account:binding:{server}:{login}`
2. `copy:route:master:{masterAccountId}`
3. `copy:account:risk:{followerAccountId}`
4. `copy:signal:dedup:{eventId}`
5. `copy:runtime:state:{server}:{login}`
6. `copy:runtime:account:{accountId}`
7. `copy:runtime:index`
8. `copy:runtime:db-sync:{server}:{login}`
9. `copy:runtime:positions:{accountKey}`
10. `copy:runtime:positions:index`
11. `copy:ws:mt5:*`
12. `copy:ws:follower:*`
13. `copy:hot:*`
14. `copy:hot:seq:*`

## 4.3 三条必须始终成立的边界

1. MariaDB 负责“配置与历史真相”，Redis 负责“当前热状态与加速”。
2. Redis 丢失后允许从 MariaDB 回填，但不允许 Redis 覆盖 MariaDB 的最终历史。
3. EA 当前真实运行参数仍然手工填写，前端配置不会直接生成 EA 本地输入参数。

## 5. 端到端数据链路

### 5.1 平台用户、账户、关系、分享链路

1. 用户通过 `/api/auth/register` 或 `/api/auth/login` 进入控制台。
2. Web Console 通过 `/api/me/accounts` 绑定 MT5 账户。
3. 账户写入 MariaDB 后，`account-config` 同步刷新 Redis 中的 account-binding / route / risk 快照。
4. 用户可以保存 follower 风控、symbol mapping、主从关系和 master share 配置。
5. follower 可以通过 `share_id + share_code` 建立新的主从关系。
6. 新关系默认可按业务规则处于 `PAUSED`，随后由 follower 侧切到 `ACTIVE`。
7. 把关系改成 `PAUSED` 仅表示暂停复制，不会删除关系。
8. 真正解绑关系要调用 `DELETE /api/me/copy-relations/{relationId}`。
9. 删除 MT5 账户当前只开放给 `FOLLOWER`，并会级联清理该 follower 的关系、风控和 symbol mapping。

### 5.2 前端配置到 EA 参数链路

前端负责“平台内配置”，EA 负责“连接后端并执行”。

前端需要维护：

1. `brokerName`
2. `serverName`
3. `mt5Login`
4. `accountRole`
5. `status`
6. follower 风控
7. 主从关系
8. symbol mapping

EA 需要手工填写：

1. Master EA：
   `WsUrl`
   `BearerToken`
2. Follower EA：
   `WsUrl`
   `BearerToken`
   `FollowerAccountId`
   `ExecutionMode`

必须注意：

1. 前端里的 `credential` 不是 websocket token。
2. `FollowerAccountId` 是平台账户 ID，不是 MT5 登录号。
3. `WsUrl` 与 `BearerToken` 来自后端配置，不来自前端。

### 5.3 Master 信号上行链路

1. Master EA 连接 `/ws/trade`。
2. EA 上报 `HELLO / HEARTBEAT / DEAL / ORDER`。
3. `signal-ingest` 完成 token 校验、连接注册、信号标准化和去重。
4. signal audit 进入热路径或直接写库。
5. runtime-state 被更新：
   连接状态
   lastHello
   lastHeartbeat
   lastSignalType
   balance
   equity
6. 如 payload 带 `positions`，position ledger 也会被更新。
7. 最终发布 `Mt5SignalAcceptedEvent` 给 `copy-engine`。

### 5.4 Copy Engine command / dispatch 生成链路

1. `copy-engine` 先按 `server + login` 找到 master 平台账户。
2. 读取 master route snapshot，拿到 follower 列表。
3. **并行**对每个 follower 执行以下步骤（单 follower 内联执行，多 follower 使用专用线程池 `copy-engine-follower`）：
   1. 读取 risk snapshot、symbol mapping、runtime-state。
   2. 按 copy mode 计算目标指令与目标手数。
   3. 生成 `execution_commands`。
   4. 对 `READY` 的 command 生成 `follower_dispatch_outbox`。
4. 每个 follower 使用独立 `TransactionTemplate`，单个 follower 处理失败不影响其他 follower。
5. `CompletableFuture.allOf().join()` 等待所有 follower 完成后返回。

关键保护：

1. 账户、route、risk、runtime-state 都是 Redis-first，miss 才回源数据库。
2. `BALANCE_RATIO / EQUITY_RATIO` 会校验 follower runtime-state 的资金快照是否存在且新鲜。
3. dispatch 持久化会按 `dispatchId` 和 `executionCommandId` 合并，避免唯一键冲突。
4. 若一个 `executionCommandId` 已绑定到其他 dispatch，服务会跳过重复创建并记录告警。
5. 启动时会对齐 `copy:hot:seq:*`，避免重启后旧 ID 被复用。
6. ID 分配使用 Redis `INCR`（`CopyHotPathIdAllocator`），天然线程安全，支持并行分配。

### 5.5 `DATABASE` 与 `REDIS_QUEUE` 两种热路径

#### `DATABASE`

1. signal audit 直接落 MySQL
2. command 直接落 MySQL
3. dispatch 直接落 MySQL
4. 实时推送建立在数据库行已经存在的前提上

#### `REDIS_QUEUE`

1. signal audit 先写 Redis
2. command 先写 Redis
3. dispatch 先写 Redis
4. follower websocket 实时推送立即发生，不等待 MySQL 提交
5. 后台 worker 异步把热状态刷回 MySQL
6. MySQL 仍然是 durable truth

### 5.6 Follower 下行执行链路

1. Follower EA 连接 `/ws/follower-exec`。
2. 发送 `HELLO`，带上 `followerAccountId` 或 `server + login`。
3. `follower-exec` 绑定账户，更新 follower runtime-state。
4. 如果 payload 带 `positions`，同步更新持仓台账。
5. 绑定完成后，先查询该 follower 的 backlog `PENDING` dispatch。
6. 推送 `HELLO_ACK`。
7. 逐条下发 backlog `DISPATCH`。
8. 新的实时 dispatch 也会在会话在线时立即推送。
9. Follower EA 执行后回 `ACK / FAIL`。
10. `follower-exec` 回写 dispatch 状态，并返回 `STATUS_ACK`。

### 5.7 runtime-state、session 和监控链路

runtime-state 当前由 master 与 follower 两边共同维护：

1. Master `HELLO / HEARTBEAT / DEAL / ORDER` 更新 master runtime-state。
2. Follower `HELLO / HEARTBEAT` 更新 follower runtime-state。
3. runtime-state 先写 Redis，再按节流窗口同步数据库快照。
4. 断线事件会强制落盘为 `DISCONNECTED`。

监控详情页的数据来源：

1. `overview`
   来自账户基础信息 + runtime-state + dispatch 计数
2. `runtimeState`
   来自统一 runtime-state store，包含 `balance`、`equity`
3. `wsSessions`
   来自 `/ws/trade` 的 master session registry
4. `followerExecSessions`
   来自 `/ws/follower-exec` 的 follower session registry
5. `commands`
   来自 `execution_commands`
6. `dispatches`
   来自 `follower_dispatch_outbox`
7. `trace`
   来自 command + dispatch 聚合查询

当前语义边界：

1. follower 详情里的 `lastSignalType` 可能是 `n/a`，因为 follower 主要通过 `HELLO / HEARTBEAT / ACK / FAIL` 更新状态，不走 master signal ingest。
2. follower 会话详情更适合看 `lastHeartbeat` 和 `lastDispatchSentAt`，而不是 master 那套 signal 字段。
3. 前端监控页当前会自动轮询刷新，但本质上仍然是读聚合 API，不是独立消息推送。

### 5.8 持仓恢复链路

1. Master 与 Follower 的 `HELLO / HEARTBEAT` 都可以携带 `positions`。
2. Follower 复制仓位会在 MT5 comment 中写入：
   `cp1|mp=<masterPositionId>|mo=<masterOrderId>`
3. Java 解析 held positions 和 tracking comment。
4. 更新 Redis 中的 open-position ledger。
5. 异步持久化到数据库 ledger 表。
6. 重启或 Redis 恢复后，先用数据库 ledger 回填，再等待新的 MT5 snapshot 做最终纠正。

### 5.9 启动预热与 Redis 恢复链路

1. 应用启动时预热 route/risk/account-binding/runtime-state/open-position ledger。
2. 对齐 `copy:hot:seq:*` 到数据库最新 signal / command / dispatch ID。
3. 运行后等待 EA 的 `HELLO / HEARTBEAT` 提供最新 runtime-state 和持仓。
4. Redis 恢复后：
   `copy:ws:*` 可以丢
   `copy:signal:dedup:*` 可以丢
   runtime-state 可从数据库与后续心跳恢复
   open-position ledger 可从数据库与 MT5 snapshot 恢复

## 6. 当前最重要的运维语义

1. `PAUSED` 不是解绑，解绑要删除关系。
2. follower 账户可删，master 账户当前不可删。
3. 删除 follower 账户会同时清理其关系、风控和 mapping。
4. 前端 `credential` 不等于 EA websocket token。
5. `FollowerAccountId` 必须是平台账户 ID。
6. 单机联调建议 `copier.mt5.follower-exec.realtime-dispatch.backend=local`。
7. 只有在验证多实例实时推送时，才建议切到 Redis pub/sub。

## 7. 当前未完成能力

1. 外部 MQ 骨干
2. durable claim/lease 分发补偿
3. 独立 Notification Service
4. 独立 API Gateway
5. 独立 Agent / Scheduler Service
6. 更高级的 broker 级资金与保证金校验
7. 完整告警编排与工单联动

## 8. 相关阅读

1. [总体架构](./overall-architecture.md)
2. [账户与配置服务](../modules/account-config-service.md)
3. [跟单引擎](../modules/copy-engine-service.md)
4. [Follower Exec 服务](../modules/follower-exec-service.md)
5. [监控服务](../modules/monitor-service.md)
6. [前端配置到 EA 参数填写](../operations/frontend-to-ea-setup.md)
