# 文档索引

## 说明

这套文档按 3 类状态维护：

1. `已完成实现对齐`：文档内容已经和当前代码对齐，可直接用于联调、排障和本地初始化。
2. `部分完成，含目标设计`：主链路可用，但还有边界能力、多实例能力或运维能力未补齐。
3. `仅目标设计 / 未实现`：当前仓库没有对应运行时代码，文档只保留设计目标。

当前代码真实状态以 [implementation-status.md](./implementation-status.md) 为准。

## 已完成实现对齐

1. [实现状态总表](./implementation-status.md)
2. [账户与配置服务](./modules/account-config-service.md)
3. [跟单引擎](./modules/copy-engine-service.md)
4. [Follower Exec 服务](./modules/follower-exec-service.md)
5. [监控服务](./modules/monitor-service.md)
6. [Redis 备份与恢复](./operations/redis-backup-recovery.md)

这些内容已经覆盖当前仓库的可用主链路：

1. MT5 主端 EA 通过 `/ws/trade` 上报 `HELLO / HEARTBEAT / DEAL / ORDER`
2. Java 完成主从账户绑定、风控、品种映射和主从关系持久化
3. Copy Engine 生成 `execution_commands` 和 `follower_dispatch_outbox`
4. Follower EA 通过 `/ws/follower-exec` 接收并执行开仓、平仓、TP/SL、挂单指令
5. Redis 当前已用于 route/risk/account-binding 缓存、信号去重、session registry 和 runtime-state
6. runtime-state 已改成 Redis-first store，并增加“资金快照新鲜度门禁”
7. MariaDB 仍然是配置、执行命令、dispatch outbox 和审计流水的业务真源

## 部分完成，含目标设计

1. [总体架构](./architecture/overall-architecture.md)
2. [安全与可靠性](./architecture/security-and-reliability.md)
3. [账户与监控控制台方案](./architecture/account-monitor-console.md)
4. [用户认证服务](./modules/user-auth-service.md)
5. [MT5 WebSocket 协议](./contracts/mt5-websocket-signal.md)
6. [MT5 Bridge 服务](./modules/mt5-bridge-service.md)
7. [迭代工作流](./process/iteration-cycle.md)

这些文档里既有已落地内容，也有后续目标：

1. MT5 上行接入、主从配置、Copy Engine、Follower 下行、监控聚合已经落地
2. 平台用户注册登录、`share_id + share_code` 绑定，以及当前登录用户视角的账户台/监控台后端读接口第一阶段已落地
3. `share_code` 轮换只影响新绑定，不影响已 follow 的关系；follower 侧可以主动解绑
4. `web-console/` 独立前端骨架已落地，已接上登录、总览、账户、关系管理、分享绑定、监控详情、链路追踪、个人设置，以及账户绑定/风控/关系/映射/解绑的第一阶段写操作
5. 这批前端写操作已经优先切到 `/api/me/...` 登录态接口，不再继续把 `userId` 暴露给前端
6. MQ、多服务拆分、完整多实例投递保障仍属于后续目标
7. 多实例 follower 实时推送当前是“Redis pub/sub 通知 + 持有 websocket 的实例执行推送”

## 仅目标设计 / 未实现

1. [API Gateway](./modules/api-gateway.md)
2. [实时通知服务](./modules/websocket-notification-service.md)
3. [Agent 调度服务](./modules/agent-service.md)

## 当前代码重点

当前仓库已经可以用于本地 `MariaDB + Redis + MT5` 联调，建议先看：

1. [实现状态总表](./implementation-status.md)
2. `src/main/resources/application-local.yml`
3. `bootstrap/local.example.json`
4. `web-console/`
5. [Redis 备份与恢复](./operations/redis-backup-recovery.md)

本地开发注意：

1. 项目已使用 Lombok，IDEA 需要开启 annotation processing。
2. `local` profile 下，MariaDB 是业务真源，Redis 是缓存和运行态共享层。
3. “节点”在本文档里只表示一个 Java 服务实例；本地只起一个 `spring-boot:run` 时，就是单节点。
4. 单节点本地联调时，推荐把 `copier.mt5.follower-exec.realtime-dispatch.backend` 设为 `local`。
5. Redis-first 读取、runtime-state Redis-first、route fallback 批量回源、乐观锁、资金快照新鲜度门禁都已落地；哪些已实现、哪些刻意不做、哪些还没做，以 [implementation-status.md](./implementation-status.md) 为准。
6. 前端本地开发可进入 `web-console/` 后执行 `npm install`、`npm run dev`；Vite 已代理 `/api`、`/actuator` 和 `/ws` 到 `127.0.0.1:8080`。
