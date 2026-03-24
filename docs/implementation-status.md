# Implementation Status

## 状态定义

1. `已完成`
   代码、配置和主要测试已经落地，可以直接联调。
2. `部分完成`
   主链路可用，但多实例补偿、自动化运维或高级能力还没有补齐。
3. `未完成`
   当前仓库没有对应实现，仍停留在目标设计。

## 已完成

### 1. 平台用户与控制台

1. 平台用户注册、登录、登出、当前用户查询、资料修改
2. `platform_id`、`share_id` 自动生成
3. `session + HttpOnly cookie` 认证链路
4. `web-console/` 已接入：
   `/login`
   `/register`
   `/app/overview`
   `/app/accounts`
   `/app/accounts/:accountId`
   `/app/share`
   `/app/follow/bind`
   `/app/relations`
   `/app/monitor/accounts`
   `/app/monitor/accounts/:accountId`
   `/app/traces/order`
   `/app/traces/position`
   `/app/settings/profile`

### 2. 账户、关系、风控与分享

1. MT5 账户绑定与按 `serverName + mt5Login` 更新
2. WebSocket-only 账户允许不填 `credential`
3. `credential` 入库加密
4. follower 风控持久化
5. 主从关系创建、更新、暂停
6. DAG 防环校验
7. symbol mapping 持久化
8. share 配置持久化
9. `share_id + share_code` 建立 follow 关系
10. 新建 share 关系默认可以以 `PAUSED` 起步
11. follower 可以显式解绑关系
12. `PAUSED` 与“解绑关系”语义已经拆开
13. `DELETE /api/me/accounts/{accountId}` 已支持删除 follower 账户
14. 删除 follower 账户时会联动清理：
    关系
    风控
    symbol mapping

### 3. MT5 Signal Ingest

1. `/ws/trade` WebSocket 接入
2. Bearer token / query token 校验
3. `HELLO / HEARTBEAT / DEAL / ORDER` 标准化
4. Redis TTL 去重
5. signal audit 持久化
6. master runtime-state 更新
7. master session registry
8. held-position inventory 上报

### 4. Copy Engine

1. 基于 `server + login` 的 master 账户定位
2. Redis-first 的 route/risk/account-binding/runtime-state 读取
3. `FIXED_LOT`
4. `BALANCE_RATIO`
5. `EQUITY_RATIO`
6. `FOLLOW_MASTER`
7. `OPEN_POSITION`
8. `CLOSE_POSITION`
9. `SYNC_TP_SL`
10. `CREATE_PENDING_ORDER`
11. `UPDATE_PENDING_ORDER`
12. `CANCEL_PENDING_ORDER`
13. command 生成
14. dispatch 生成
15. `DATABASE` 热路径
16. `REDIS_QUEUE` 热路径
17. dispatch 去重合并，避免 `uk_dispatch_command` 冲突
18. 启动对齐 `copy:hot:seq:*`，避免旧 `executionCommandId` 被复用
19. 资金快照新鲜度门禁，避免比例跟单静默退回 `1.0`
20. follower 并行处理（`CompletableFuture.allOf()` + 专用线程池 `copy-engine-follower`）
21. 每个 follower 独立事务（`TransactionTemplate`），单个失败不影响其他 follower
22. 线程池大小可配置（`copier.copy-engine.hot-path.follower-parallelism`，默认 CPU 核数）

### 5. Follower Exec

1. `/ws/follower-exec` WebSocket 接入
2. bearer/query-token 校验
3. 按 `followerAccountId` 或 `server + login` 绑定 follower
4. backlog replay
5. 新 dispatch 实时推送
6. `ACK / FAIL` 回写 dispatch 状态
7. follower runtime-state 更新
8. follower session registry
9. follower held-position inventory 上报
10. MT5 comment 追踪标记：
    `cp1|mp=<masterPositionId>|mo=<masterOrderId>`

### 6. Monitor

1. signal audit 查询
2. runtime-state Redis-first store
3. account overview 聚合
4. master websocket session 视图
5. follower websocket session 视图
6. command 查询
7. dispatch 查询
8. order trace / position trace
9. runtime-state 读模型已暴露 `balance`、`equity`
10. 监控详情已聚合：
    overview
    runtimeState
    wsSessions
    followerExecSessions
    commands
    dispatches
    traces

### 7. Redis、恢复与持仓台账

1. route/risk/account-binding Redis 投影
2. runtime-state Redis-first
3. session registry Redis TTL
4. signal dedup Redis TTL
5. `copy:hot:*` command/dispatch 热状态
6. `copy:hot:seq:*` 启动对齐
7. open-position ledger：
   Redis 热副本
   MySQL durable snapshot
8. Redis 恢复后，MT5 `HELLO / HEARTBEAT` 可纠正当前持仓

## 部分完成

### 1. 多实例实时投递

1. Redis pub/sub 已可用于跨实例通知
2. 真正持有 websocket 的实例负责推送
3. 但还没有 durable claim/lease 式补偿分发

### 2. 高级交易安全控制

1. 基础风控已完成
2. 更高级的保证金预检查、成交后滑点二次核对还未补齐

### 3. 控制台运维能力

1. 核心页面和写操作已完成
2. 监控页已可自动刷新
3. 但还没有完整告警编排、通知中心和工单联动

## 未完成

1. API Gateway
2. 独立 Notification Service
3. 独立 Agent / Scheduler Service
4. 外部 MQ 骨干
5. 多 broker 执行 worker 编排
6. 更完整 RBAC
7. 完整运营级告警系统

## 当前联调基线

建议按下面的口径理解当前系统：

1. Spring Boot 单体应用是当前真实后端
2. MariaDB 是配置和历史真源
3. Redis 是热状态、缓存和热路径承载层
4. 前端配置不会自动同步到 EA
5. EA 的 `WsUrl`、`BearerToken`、`FollowerAccountId` 仍需手工填写

## 推荐配套阅读

1. [总体架构](./architecture/overall-architecture.md)
2. [模块交互与端到端数据链路](./architecture/system-modules-and-dataflows.md)
3. [账户与配置服务](./modules/account-config-service.md)
4. [跟单引擎](./modules/copy-engine-service.md)
5. [Follower Exec 服务](./modules/follower-exec-service.md)
6. [监控服务](./modules/monitor-service.md)
7. [前端配置到 EA 参数填写](./operations/frontend-to-ea-setup.md)
