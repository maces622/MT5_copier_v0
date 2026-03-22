# 总体架构

## 当前真实形态

当前仓库不是完整微服务集群，而是“模块化单体 + 两个 MT5 EA”。

当前真实运行组件：

1. 主端 EA：`mt5/Websock_Sender_Master_v0.mq5`
2. Java 服务：一个 Spring Boot 进程
3. 从端 EA：`mt5/Websock_Receiver_Follower_Exec_v0.mq5`
4. MariaDB：配置、command、outbox、审计真源
5. Redis：缓存、运行态、去重、会话共享、可选跨实例通知

## 当前核心链路

### 1. 主端上行

1. 主端 EA 监听 `OnTradeTransaction`
2. 通过 `/ws/trade` 上报 `HELLO / HEARTBEAT / DEAL / ORDER`
3. Java 标准化信号并落审计

### 2. 跟单决策

1. Copy Engine 根据 `server + login` 绑定主账户
2. 读取主从路由和 follower 风控
3. 计算复制指令和手数
4. 落库 `execution_commands`
5. 为可执行命令落库 `follower_dispatch_outbox`

### 3. follower 下行

1. follower EA 通过 `/ws/follower-exec` 连接 Java
2. Java 先回放 backlog，再实时下发新 dispatch
3. follower EA 执行后回 `ACK / FAIL`
4. Java 回写 dispatch 状态

### 4. 监控与运行态

1. runtime-state Redis-first
2. session registry Redis TTL
3. 监控接口聚合账户、运行态、dispatch、signal audit

## 当前架构中的数据分工

### MariaDB 负责

1. 账户绑定
2. 风控规则
3. 主从关系
4. 品种映射
5. `execution_commands`
6. `follower_dispatch_outbox`
7. 信号审计

### Redis 负责

1. route snapshot
2. risk snapshot
3. account binding
4. runtime-state
5. signal dedup
6. session registry
7. 可选的 follower realtime dispatch pub/sub 通知

原则是：

1. MariaDB 是真源
2. Redis 是加速层和协调层
3. Redis 恢复后不能覆盖数据库真相

## 当前已落地的性能优化

### 1. Redis-first 读取

Copy Engine 热路径已经从“JPA 直接拼关系”改成：

1. account binding Redis-first
2. route Redis-first
3. risk Redis-first
4. runtime-state Redis-first

### 2. DB fallback 去 N+1

route fallback 和 warmup 场景里，已经不再按 follower 逐个查 risk/mapping，而是批量查后内存组装。

### 3. 高频运行态脱离数据库主写

runtime-state 已经迁到 Redis-first，数据库只保留：

1. 节流快照
2. 断线强制落盘

### 4. Redis TTL 去重

MT5 信号去重已经从 JVM 本地内存扩展为 Redis TTL，可支持重启和多实例共享。

### 5. Redis TTL 会话注册

主端和 follower websocket session registry 都支持 Redis TTL 存储。

### 6. 实时推送边界清晰

单节点下：

1. 直接用本节点 `liveSessions`

多节点下：

1. Redis pub/sub 只做通知
2. 真正持有 websocket 的实例负责推送
3. 数据库 outbox 仍是真源

## 当前可靠性约束

1. command/outbox 继续事务直写数据库
2. 比例跟单增加 runtime-state 新鲜度门禁
3. 核心表增加索引和乐观锁
4. 测试 profile 已隔离本机环境变量污染

## 目标态与未完成项

当前还没有实现：

1. MQ 骨干
2. 多 broker 执行 worker
3. 独立 Notification Service
4. API Gateway
5. Agent Service
6. durable claim/lease + 补偿 worker

所以这份架构文档的重点不是“理想微服务图”，而是当前真实运行方式和后续演进方向的边界。
