# Implementation Status

## 状态定义

1. `已完成`：代码、配置、测试都已落地，可直接联调。
2. `部分完成`：主链路可用，但多实例、补偿、运维自动化或边界能力未补齐。
3. `未完成`：当前仓库没有对应实现，仍停留在设计目标。

## 已完成

### 1. MT5 Signal Ingest

1. WebSocket endpoint：`/ws/trade`
2. Bearer token / query token 握手校验
3. 连接注册、断开跟踪
4. `HELLO`、`HEARTBEAT`、`DEAL`、`ORDER` 标准化
5. Redis TTL 信号去重，Redis 故障时自动降级到本地内存
6. 主端 EA 已上报余额、净值、合约元信息
7. 主端 WebSocket session registry 已支持 Redis TTL 存储和本地回退

### 2. 账户与跟单配置

1. MT5 账户绑定
2. WebSocket-only 账户可不填 `credential`
3. 提供 `credential` 时按落库加密
4. 风控规则持久化
5. 主从关系管理
6. DAG 防环校验
7. 品种映射持久化
8. 本地 bootstrap 命令行初始化
9. 配置变更后自动刷新 Redis route/risk/account-binding 缓存
10. master 账户级分享配置 `share_code` 持久化
11. `share_id + share_code` 跨用户建立主从关系
12. `join-by-share` 创建的新关系默认 `PAUSED`
13. `share_code` 轮换只影响新绑定，不影响已 follow 的关系
14. follower 可以主动解绑已有 follow 关系

### 2.1 平台用户与认证

1. `platform_users` 已落地
2. 注册后自动生成 `platform_id`
3. 注册后自动生成 `share_id`
4. `POST /api/auth/register`
5. `POST /api/auth/login`
6. `POST /api/auth/logout`
7. `GET /api/auth/me`
8. `PUT /api/auth/me`
9. 当前使用服务端 session + HttpOnly cookie
10. `master_share_configs` 已落地
10. `GET /api/me/share-profile`
11. `POST /api/accounts/{accountId}/share-config`
12. `PUT /api/accounts/{accountId}/share-config`
13. `POST /api/copy-relations/join-by-share`
14. `GET /api/me/accounts`
15. `POST /api/me/accounts`
16. `POST /api/me/accounts/{accountId}/risk-rule`
17. `GET /api/me/copy-relations`
18. `POST /api/me/copy-relations`
19. `PUT /api/me/copy-relations/{relationId}`
20. `DELETE /api/me/copy-relations/{relationId}`
21. `POST /api/me/accounts/{accountId}/symbol-mappings`
22. `GET /api/accounts/{accountId}`
23. `GET /api/accounts/{accountId}/detail`
24. `GET /api/accounts/{accountId}/risk-rule`
25. `GET /api/accounts/{accountId}/relations`
26. `GET /api/accounts/{accountId}/symbol-mappings`

### 3. Copy Engine

1. 消费进程内事件总线中的 `DEAL` / `ORDER`
2. 通过 Redis-first 的 `server + login -> masterAccountId` 绑定缓存定位主账户
3. 通过 Redis-first 的 route/risk 快照加载 follower 路由和风控
4. Redis miss 时回源数据库并自动回填缓存
5. 生成 `execution_commands`
6. 生成 `follower_dispatch_outbox`
7. 支持 `FIXED_LOT`、`BALANCE_RATIO`、`EQUITY_RATIO`、`FOLLOW_MASTER`
8. 默认 relation mode 已改为 `BALANCE_RATIO`
9. 开仓手数支持 `风险比例 * 账户资金缩放比例`
10. 部分平仓按主端平仓比例执行，最后一笔支持 `closeAll=true`
11. 支持 symbol allow/block 和 lot 限制
12. 支持 `reverseFollow`
13. 支持 TP/SL 同步和挂单复制
14. dispatch payload 已包含源端合约元信息
15. 点差限制默认关闭；开启时只限制市价开仓，不限制平仓
16. 比例跟单已增加资金快照新鲜度门禁；follower 资金快照缺失、过期或无效时显式拒绝，不再静默回退到 `1.0`

### 4. Redis 缓存与运行态

1. MariaDB 仍然是业务真源
2. Redis 缓存 master route snapshot
3. Redis 缓存 follower risk snapshot
4. Redis 缓存 `server + login -> account binding`
5. Redis-first 缓存 `server + login -> runtime state`
6. Redis-first 读取 follower 资金快照；比例跟单不再直查 runtime-state 表
7. 启动时会预热 route/risk/account-binding/runtime-state 缓存
8. runtime-state 写入已改成 Redis-first；数据库只做节流快照同步和断线强制落盘
9. route/risk/account-binding/runtime-state 读写失败都按降级策略处理，不阻断主链路
10. runtime-state 新鲜度门禁由 `copier.monitor.runtime-state.funds-stale-after` 和 `copier.monitor.runtime-state.require-fresh-funds-for-ratio` 控制
11. 主端 WebSocket session registry 已支持 Redis TTL 存储
12. Follower WebSocket session registry 已支持 Redis TTL 存储
13. Follower `followerAccountId -> sessionId` 绑定也已进 Redis
14. Follower realtime dispatch 可通过 Redis pub/sub 做跨实例通知
15. 这里的“节点”仅表示一个 Java 服务实例；本地联调通常只有一个节点
16. Redis pub/sub 只负责通知，不是业务真源；真实 dispatch 仍以数据库 outbox 和持有 websocket 的实例内存对象为准

当前默认 key：

1. `copy:route:master:{masterAccountId}`
2. `copy:route:version:{masterAccountId}`
3. `copy:account:risk:{followerAccountId}`
4. `copy:account:binding:{server}:{login}`
5. `copy:runtime:state:{server}:{login}`
6. `copy:runtime:account:{accountId}`
7. `copy:runtime:index`
8. `copy:runtime:db-sync:{server}:{login}`
9. `copy:signal:dedup:{eventId}`
10. `copy:ws:mt5:session:{sessionId}`
11. `copy:ws:mt5:index`
12. `copy:ws:follower:session:{sessionId}`
13. `copy:ws:follower:index`
14. `copy:ws:follower:account:{followerAccountId}`

当前默认 channel：

1. `copy:follower:dispatch`

### 5. Follower Exec

1. 独立 websocket endpoint：`/ws/follower-exec`
2. 独立 bearer/query-token 握手校验
3. 支持按 `followerAccountId` 或 `server + login` 绑定 follower
4. 支持从 `follower_dispatch_outbox` 回放 backlog
5. 支持新建 `PENDING` dispatch 的实时下发
6. 支持 follower `ACK` / `FAIL` 回写状态
7. Session list API 已可通过 Redis-backed registry 聚合会话视图
8. Follower EA 已支持：
   `OPEN_POSITION`
   `CLOSE_POSITION`
   `SYNC_TP_SL`
   `CREATE_PENDING_ORDER`
   `UPDATE_PENDING_ORDER`
   `CANCEL_PENDING_ORDER`
9. Follower EA 支持通过注释恢复本地映射
10. 市价平仓不做点差限制
11. 单节点下实时下发直接使用本节点 `liveSessions`
12. 多实例下可开启 Redis pub/sub 做跨实例通知，由真正持有 websocket 的实例推送

### 6. Monitor

1. 已接收的 MT5 信号会持久化审计
2. 运行态主写入层已改为 Redis-first store，键按 `server + login`
3. 运行态已包含 `balance`、`equity`
4. 运行态数据库快照采用节流同步；断线时会强制落盘
5. WebSocket 断开时会标记运行态掉线
6. 监控接口可查看账户状态、运行态、信号审计和 WebSocket 会话
7. 比例跟单读取 runtime-state 时会强制检查快照新鲜度，恢复后的旧热数据不会直接参与真实下单
8. `GET /api/monitor/dashboard`
9. `GET /api/monitor/accounts/{accountId}/detail`
10. `GET /api/monitor/accounts/{accountId}/commands`
11. `GET /api/monitor/accounts/{accountId}/dispatches`
12. `GET /api/monitor/traces/order`
13. `GET /api/monitor/traces/position`

### 7. 工程维护

1. 核心实体已补 route/runtime/dispatch 相关索引和 `row_version` 乐观锁
2. Redis 配置已统一到 Spring Boot 2.7 的 `spring.redis.*`
3. DTO / entity / config / cache 模板代码已用 Lombok 收缩
4. Route snapshot 的 DB fallback 已改成批量装配，消掉按 follower 的 N+1
5. 集成测试显式钉住 `test` profile，避免被本地 `SPRING_PROFILES_ACTIVE` 或 `COPIER_*` 污染
6. runtime-state 已抽成统一 store，主端 ingest、follower heartbeat、monitor 查询、copy-engine 资金读取全部复用同一层
7. Redis 备份/恢复文档和本地脚本已补齐，见 [docs/operations/redis-backup-recovery.md](./operations/redis-backup-recovery.md)
8. 独立前端控制台骨架已落在 `web-console/`，并已通过 `npm run build`
9. 控制台已接上 `/app/settings/profile`
10. 当前测试通过：`57/57`

### 8. Redis / JPA 优化说明

1. 读优化已部分完成：Copy Engine 热路径对 `master account binding`、`route snapshot`、`risk snapshot` 都是 Redis-first，缓存 miss 时才回源数据库并回填缓存。
2. `execution_commands` 和 `follower_dispatch_outbox` 仍然直接用 JPA 事务落库；这是刻意保留的设计，不会为了“彻底不用 JPA”而改成先写 Redis。
3. 之前 miss / warmup 场景下的 route fallback N+1 已处理：`CopyRouteSnapshotFactory` 现在会批量查询 risk rules 和 symbol mappings，再在内存里组装快照。
4. 并发更新上已经有比裸 `save()` 更稳的保护：`risk_rules`、`copy_relations`、`execution_commands`、`follower_dispatch_outbox` 等核心表都用了 `@Version row_version` 乐观锁。
5. 当前还没有 Redisson 分布式锁；如果未来出现跨实例强互斥更新，再单独评估是否引入。
6. 高频 runtime-state 已搬到 Redis-first store：主端 `HELLO/HEARTBEAT/DEAL/ORDER`、follower `HELLO/HEARTBEAT` 先写 Redis，再按节流窗口同步数据库快照。
7. 当前 runtime-state 的数据库同步不是独立异步 worker，而是写路径内的节流 `maybePersist`；断线事件会强制落盘。
8. MT5 信号审计仍然直接写数据库，没有搬到 Redis。
9. Redis 目前只使用 string KV 存整块 JSON 快照和 TTL 会话键，没有引入 Hash/List；对当前“整份快照整体刷新”的场景，这是刻意选择，不是缺失。
10. 当前没有把 `execution_commands` 或 `follower_dispatch_outbox` 先写 Redis List 再异步落库；这同样是刻意选择，因为这两张表属于核心交易状态，数据库必须保持真源。
11. Redis 备份设计遵循“MariaDB 真源、Redis 热状态加速层”的原则：route/risk/account-binding 可重建，`copy:ws:*` 和 `copy:signal:dedup:*` 属于易失态，runtime-state 恢复后必须经过新鲜度门禁。

## 部分完成

### 1. 单仓库运行形态

1. 当前仓库已经具备 MT5 上行、主从配置、Copy Engine、Follower 下行和监控
2. 但仍然是单个 Spring Boot 应用，不是最终拆分后的多服务部署

### 2. Redis 使用边界

1. route/risk/account-binding/dedup/session registry 都已接入 Redis
2. 会话可见性已经可以跨实例共享
3. follower dispatch 的跨实例实时推送已经通过 Redis pub/sub 协调
4. runtime-state 已经接入 Redis-first store，但数据库同步目前还是节流式快照，不是独立回刷 worker
5. 还没有 durable 的分布式 claim/lease 机制，也没有独立补偿 worker
6. 当前没有单独的“在线重建 Redis 缓存”管理接口；重建路径仍以重启预热和配置重写为主

### 3. 监控深度

1. 运行态已覆盖连接状态、信号心跳、余额、净值
2. follower 持仓全量盘点和 broker 级对账还没补齐

### 4. Follower 挂单支持

1. 标准挂单新增、修改、删除已支持
2. `BUY_STOP_LIMIT` / `SELL_STOP_LIMIT` 还没完成

### 5. 账户与监控控制台

1. `web-console/` React + Vite 控制台骨架已落地
2. 已接入登录、注册、总览、账户列表、账户详情、分享中心、share 绑定、监控列表、监控详情和个人设置
3. 当前前端已接入账户绑定、`share-config`、`join-by-share`、风控保存、关系创建/更新、follower 主动解绑、品种映射保存、关系管理列表、订单/持仓链路追踪和个人资料更新
4. 当前监控页仍然是读模型为主，没有告警编排
5. 当前仍然依赖后端 session + HttpOnly cookie，不是完整的前端权限体系

## 未完成

### 1. 平台用户与控制台

1. 平台用户注册、登录、登出的后端第一阶段已实现
2. `platform_id`、`share_id`、`share_code` 的后端模型已实现
3. 当前登录态下的账户台读接口和监控台聚合读接口已实现
4. `web-console/` 已提供前端第一阶段骨架，但还不是完整可运营版本
5. 账户绑定、风控、关系、映射的第一阶段登录态写接口已经落在 `/api/me/...`
6. 旧的基础配置写接口仍然保留，主要用于兼容命令行初始化和历史调用路径
7. 当前已确认的目标方案见 [docs/architecture/account-monitor-console.md](./architecture/account-monitor-console.md)

### 1. MQ 与事件骨干

1. 还没有 MQ 集成
2. 还没有对配置变更、执行指令、审计事件做外部 topic 发布

### 2. 独立平台服务

1. API Gateway
2. User Auth Service 全量权限体系
3. WebSocket Notification Service
4. Agent Scheduler Service

### 3. 高级交易控制

1. follower 执行前的高级保证金检查
2. broker 成交后的滑点二次核对
3. EA 进程外的 durable 本地 ticket 映射
4. 多 broker 执行 worker 路由

## Local Configuration

本地 MariaDB + Redis 联调：

1. 使用 `src/main/resources/application-local.yml`
2. Redis 连接配置使用 `spring.redis.*`
3. Route cache backend 由 `copier.account-config.route-cache.backend` 控制
4. Route cache warmup 由 `copier.account-config.route-cache.warmup-on-startup` 控制
5. Runtime-state backend 由 `copier.monitor.runtime-state.backend` 控制
6. Runtime-state key prefix 由 `copier.monitor.runtime-state.key-prefix` 控制
7. Runtime-state DB 节流同步窗口由 `copier.monitor.runtime-state.database-sync-interval` 控制
8. Runtime-state 资金快照过期窗口由 `copier.monitor.runtime-state.funds-stale-after` 控制
9. Runtime-state 是否要求比例跟单强制使用新鲜资金快照由 `copier.monitor.runtime-state.require-fresh-funds-for-ratio` 控制
10. Runtime-state warmup 由 `copier.monitor.runtime-state.warmup-on-startup` 控制
11. Signal dedup backend 由 `copier.mt5.signal-ingest.dedup-backend` 控制
12. Session registry backend 由 `copier.monitor.session-registry.backend` 控制
13. Follower realtime dispatch backend 由 `copier.mt5.follower-exec.realtime-dispatch.backend` 控制
14. 点差限制由 `copier.copy-engine.slippage.enabled` 控制

单节点本地联调推荐：

1. `route-cache.backend=redis`
2. `runtime-state.backend=redis`
3. `runtime-state.funds-stale-after=PT30S`
4. `runtime-state.require-fresh-funds-for-ratio=true`
5. `dedup-backend=redis` 或 `memory`
6. `session-registry.backend=redis` 或 `memory`
7. `follower-exec.realtime-dispatch.backend=local`
8. 只有在验证多实例 follower 实时推送时，才建议把 `follower-exec.realtime-dispatch.backend` 切成 `redis`

常用环境变量：

1. `SPRING_PROFILES_ACTIVE=local`
2. `COPIER_ACCOUNT_CONFIG_ROUTE_CACHE_BACKEND=redis`
3. `COPIER_MONITOR_RUNTIME_STATE_BACKEND=redis`
4. `COPIER_MONITOR_RUNTIME_STATE_DATABASE_SYNC_INTERVAL=PT30S`
5. `COPIER_MONITOR_RUNTIME_STATE_FUNDS_STALE_AFTER=PT30S`
6. `COPIER_MONITOR_RUNTIME_STATE_REQUIRE_FRESH_FUNDS_FOR_RATIO=true`
7. `COPIER_MT5_SIGNAL_INGEST_DEDUP_BACKEND=redis`
8. `COPIER_MONITOR_SESSION_REGISTRY_BACKEND=redis`
9. `COPIER_MT5_FOLLOWER_EXEC_REALTIME_DISPATCH_BACKEND=local`
10. `COPIER_COPY_ENGINE_SLIPPAGE_ENABLED=false`
