# 文档索引

![Cover](./images/cover.png)

## 当前真实系统画像

当前仓库已经落地的真实运行形态不是完整的微服务集群，而是：

1. `mt5/Websock_Sender_Master_v0.mq5` 作为 Master 上行 EA
2. 一个按模块拆分的 Spring Boot 单体应用
3. `mt5/Websock_Receiver_Follower_Exec_v0.mq5` 作为 Follower 下行执行 EA
4. MariaDB 作为配置、历史和审计的 durable truth
5. Redis 作为热状态、缓存、会话共享和热路径承载层
6. `web-console/` 作为前端控制台

必须先记住的 5 条规则：

1. MariaDB 是配置、执行历史、dispatch 历史和信号审计的最终真实来源。
2. Redis 负责 route/risk/account-binding/runtime-state/session/hot-path 的加速与协调，但不是业务真源。
3. 前端不会自动把 `WsUrl`、`BearerToken`、`FollowerAccountId` 下发给 EA，这些参数仍然手工填写。
4. 把关系改成 `PAUSED` 只是暂停跟单，不等于解绑；解绑必须删除对应的 `copy relation`。
5. 删除 MT5 账户当前只开放给 `FOLLOWER`，并会同时清理该 follower 的关系、风控和 symbol mapping。

## 推荐阅读顺序

1. [实现状态总表](./implementation-status.md)
2. [总体架构](./architecture/overall-architecture.md)
3. [模块交互与端到端数据链路](./architecture/system-modules-and-dataflows.md)
4. [前端配置到 EA 参数填写](./operations/frontend-to-ea-setup.md)
5. [Redis 备份与恢复](./operations/redis-backup-recovery.md)

## 架构文档

1. [总体架构](./architecture/overall-architecture.md)
2. [模块交互与端到端数据链路](./architecture/system-modules-and-dataflows.md)
3. [账户与监控控制台方案](./architecture/account-monitor-console.md)
4. [安全与可靠性](./architecture/security-and-reliability.md)

## 模块文档

1. [账户与配置服务](./modules/account-config-service.md)
2. [用户认证服务](./modules/user-auth-service.md)
3. [MT5 Bridge / Signal Ingest](./modules/mt5-bridge-service.md)
4. [跟单引擎](./modules/copy-engine-service.md)
5. [Follower Exec 服务](./modules/follower-exec-service.md)
6. [监控服务](./modules/monitor-service.md)

以下文档保留为目标态设计，当前仓库并未完整实现：

1. [API Gateway](./modules/api-gateway.md)
2. [实时通知服务](./modules/websocket-notification-service.md)
3. [Agent Service](./modules/agent-service.md)

## 操作与联调

1. [前端配置到 EA 参数填写](./operations/frontend-to-ea-setup.md)
2. [Redis 备份与恢复](./operations/redis-backup-recovery.md)
3. `src/main/resources/application-local.yml`
4. `bootstrap/local.example.json`

## 本次文档整理重点

本轮文档已经把下面这些近期变更纳入统一口径：

1. `REDIS_QUEUE` 热路径、异步持久化和 `copy:hot:seq:*` 启动对齐
2. `executionCommandId` 冲突保护与 dispatch 去重合并
3. MT5 `HELLO / HEARTBEAT` 持仓盘点与 open-position ledger
4. 账户删除仅开放给 `FOLLOWER`
5. `PAUSED`、`解绑关系`、`删除 follower 账户` 三者的语义边界
6. 前端配置与 EA 参数填写的责任边界
7. 监控控制台当前的数据来源、刷新方式和 follower 语义
