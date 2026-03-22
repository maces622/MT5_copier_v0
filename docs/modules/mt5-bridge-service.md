# MT5 Bridge 服务

## 当前定位

这份文档描述的是“当前仓库里已经存在的 MT5 接入层”和“后续可演进的独立 Bridge 服务目标态”。

当前真实形态不是独立 Bridge 微服务，而是：

1. 主端 MT5 EA：`mt5/Websock_Sender_Master_v0.mq5`
2. Java 进程内的 signal-ingest 模块：`/ws/trade`
3. follower 下行执行模块：`/ws/follower-exec`

所以这里要分清：

1. 已实现的是“EA + Spring Boot 进程内桥接”
2. 未实现的是“独立 MT5 Bridge 服务 + MQ 骨干”

## 当前已实现能力

### 1. 主端上行接入

当前已经具备：

1. `/ws/trade` WebSocket 接入
2. bearer/query-token 鉴权
3. `HELLO / HEARTBEAT / DEAL / ORDER` 标准化接收
4. 信号审计落库
5. Redis TTL 去重
6. WebSocket session 跟踪
7. 主端余额、净值、合约元信息上报

### 2. follower 下行接入

当前已经具备：

1. `/ws/follower-exec` WebSocket 接入
2. follower 账户绑定
3. backlog replay
4. realtime dispatch
5. `ACK / FAIL` 回写
6. follower `HELLO / HEARTBEAT` 运行态更新

### 3. 当前桥接优化

已经落地的核心优化包括：

1. `server + login -> accountId` 绑定缓存 Redis-first
2. route/risk 缓存 Redis-first
3. runtime-state Redis-first
4. Redis TTL 去重
5. Redis TTL session registry
6. route fallback 批量回源，消掉 N+1
7. 比例跟单资金快照新鲜度门禁

## 当前没有做的事

当前仓库还没有：

1. MQ 级别的外部 topic 发布
2. 独立 execution dispatcher 服务
3. 多 broker 执行 worker 池
4. 独立状态对账 worker
5. 独立补偿 worker

所以现在的 Bridge 更准确地说是“已可用的 WebSocket 接入层”，而不是“最终拆分完成的桥接集群”。

## 当前真实数据流

### 上行

1. 主端 EA 监听 `OnTradeTransaction`
2. 通过 `/ws/trade` 上报主端信号
3. Java 标准化并落审计
4. 事件进入 Copy Engine

### 下行

1. Copy Engine 生成 `execution_commands`
2. 为可执行指令生成 `follower_dispatch_outbox`
3. follower-exec 模块负责回放和实时下发
4. follower EA 执行后回 `ACK / FAIL`

## 当前设计原则

### 1. MariaDB 真源

以下内容继续坚持数据库真源：

1. 账户绑定
2. 风控规则
3. 主从关系
4. 品种映射
5. `execution_commands`
6. `follower_dispatch_outbox`
7. 信号审计

### 2. Redis 加速而不篡位

Redis 当前用于：

1. 高速读取 account binding / route / risk
2. 共享 runtime-state
3. 做信号去重
4. 做 session registry
5. 可选做跨实例实时通知

但不会把 Redis 当成核心交易真源。

### 3. 桥接层优先保证真实可恢复

所以当前没有采用：

1. `execution_commands` 先写 Redis List 再异步落库
2. `follower_dispatch_outbox` 先写缓存再补数据库

这两条都属于刻意不做。因为桥接层最核心的不是“极致吞吐”，而是“恢复后不丢执行真相”。

## 目标态

如果未来要把 Bridge 独立拆出来，建议拆为三块：

### 1. Signal Ingest

1. 接收 MT5 WebSocket
2. 校验身份
3. 解析 JSON
4. 标准化并发布 MQ 事件

### 2. Execution Dispatcher

1. 消费 execution command
2. 选择 broker / worker
3. 下发执行
4. 回写执行结果

### 3. State Sync

1. 定时对账
2. 补偿掉线后的状态差异
3. 聚合账户状态

## 当前与目标态的边界

1. 当前仓库已经能完成本地 `MariaDB + Redis + 两个 MT5 EA` 联调
2. 当前仓库还不是最终的 MQ 化、多服务化部署
3. 任何运维和联调都应先以当前真实实现为准，再看目标态文档
