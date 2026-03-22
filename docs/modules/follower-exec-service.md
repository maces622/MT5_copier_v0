# Follower Exec 服务

## 目标

这个模块负责跟单系统的下行执行链路：

1. 接收 follower MT5 EA 的 WebSocket 连接
2. 把连接绑定到已配置的 follower 账户
3. 回放 `follower_dispatch_outbox` 里的待执行指令
4. 实时推送新生成的 dispatch
5. 接收 follower 回执并更新 dispatch 状态
6. 通过 MT5 EA 完成 `OPEN_POSITION / CLOSE_POSITION / SYNC_TP_SL / 挂单复制`

当前真实实现里，数据库 outbox 是执行真源，Redis 只承担会话共享和可选的跨实例通知。

## 当前真实链路

### 1. follower 连接建立

1. follower EA 连接 `/ws/follower-exec`
2. 通过 bearer token 或 query token 完成鉴权
3. 发送 `HELLO`
4. 服务端按 `followerAccountId` 或 `server + login` 绑定到账户

### 2. backlog 回放

1. 绑定成功后，先查询该 follower 的 `PENDING` dispatch
2. 按创建顺序逐条推送
3. follower EA 回 `ACK / FAIL`
4. 服务端回写 `follower_dispatch_outbox`

### 3. 实时下发

1. Copy Engine 为 `READY` command 写入 `follower_dispatch_outbox`
2. follower-exec 模块检测到新 dispatch
3. 当前节点若持有目标 follower 的 websocket，则直接推送
4. follower EA 执行后回写状态

## WebSocket Endpoint

路径：

`/ws/follower-exec`

当前配置项：

1. `copier.mt5.follower-exec.path`
2. `copier.mt5.follower-exec.bearer-token`
3. `copier.mt5.follower-exec.allow-query-token`
4. `copier.mt5.follower-exec.heartbeat-stale-after`
5. `copier.mt5.follower-exec.realtime-dispatch.backend`
6. `copier.mt5.follower-exec.realtime-dispatch.channel`

## 当前协议

### 上行

1. `HELLO`
2. `HEARTBEAT`
3. `ACK`
4. `FAIL`

### 下行

1. `HELLO_ACK`
2. `DISPATCH`
3. `STATUS_ACK`

当前 `DISPATCH.payload` 已包含：

1. `commandType`
2. `copyMode`
3. `targetSymbol`
4. `instrumentMeta`
5. `slippagePolicy`
6. `configuredRiskRatio`
7. `accountScaleRatio`
8. `masterFunds`
9. `followerFunds`
10. `closeRatio`
11. `closeAll`

这意味着 follower EA 不只是“收一条指令然后下单”，而是已经具备：

1. 目标品种执行
2. 合约元信息校验
3. 开仓点差限制
4. 比例平仓

## follower 账户绑定

当前支持两种绑定方式：

1. `followerAccountId`
2. `server + login`

推荐优先使用 `followerAccountId`，原因是：

1. 避免 `serverName` 字符串不完全一致导致绑定失败
2. 避免依赖外部账户标识做模糊匹配
3. 与 Java 平台内账户模型一一对应

## 运行态与心跳

follower EA 的 `HELLO / HEARTBEAT` 当前会更新：

1. 连接状态
2. 最后心跳时间
3. 余额
4. 净值

这些信息写入统一的 runtime-state store。当前实现已经是 Redis-first：

1. 热路径先写 Redis
2. 数据库只做节流快照同步
3. 掉线时会强制落盘

这部分不只是监控用途，也直接影响比例跟单的资金缩放。

## 当前已落地的优化

### 1. backlog + realtime 双路径

绑定成功后先 replay backlog，再接实时 dispatch。这样能覆盖：

1. follower 短时离线
2. Java 服务重启
3. follower EA 重启

### 2. Redis-backed session registry

主端和 follower 的 session registry 都已经支持 Redis TTL：

1. 单节点时可直接用内存
2. 多实例时可共享“谁在线、谁绑定到哪个账户”的会话元信息

### 3. 实时推送边界拆清

单节点：

1. 直接使用本节点内存里的 `liveSessions`

多节点：

1. Redis pub/sub 只负责通知“某 follower 有新 dispatch”
2. 真正持有 websocket 的节点负责推送
3. `follower_dispatch_outbox` 仍然是执行真源

也就是说，Redis pub/sub 不是核心交易真源，只是跨实例实时通知总线。

### 4. runtime-state Redis-first

follower 的资金和连接状态已经脱离数据库主写路径：

1. 高频 `HELLO / HEARTBEAT` 不再每次都主写 JPA
2. 数据库只保留节流快照和断线落盘
3. 比例跟单读取 follower 资金时走统一的 Redis-first runtime-state store

### 5. 开仓点差限制只在 follower 本地执行

当前设计是：

1. Java 只在 payload 里下发 `slippagePolicy`
2. follower EA 本地决定是否放行
3. 默认关闭
4. 开启后只限制 `OPEN_POSITION`
5. `CLOSE_POSITION` 不限制点差

### 6. 比例平仓逻辑已下沉到 dispatch + EA

当前平仓不再简单按“主端平了多少手，从端也平多少手”，而是：

1. Java 计算 `closeRatio`
2. follower EA 按自己当前仓位同比例平
3. 最后一笔全平使用 `closeAll=true`

这解决了主从资金不一致时的部分平仓失真问题。

## 当前 MT5 follower EA 行为

参考：

`mt5/Websock_Receiver_Follower_Exec_v0.mq5`

当前已支持：

1. `EXECUTION_DRY_RUN`
2. `EXECUTION_REAL`
3. `OPEN_POSITION`
4. `CLOSE_POSITION`
5. `SYNC_TP_SL`
6. `CREATE_PENDING_ORDER`
7. `UPDATE_PENDING_ORDER`
8. `CANCEL_PENDING_ORDER`
9. 启动后从 MT5 注释重建本地 ticket 映射

当前建议：

1. 协议验证阶段先用 `EXECUTION_DRY_RUN`
2. 确认账户绑定、route、symbol mapping、资金缩放都正常后，再切 `EXECUTION_REAL`

## 当前设计边界

1. `BUY_STOP_LIMIT / SELL_STOP_LIMIT` 还没支持
2. follower 本地没有独立 durable ticket mapping store
3. 还没有 margin 预检查
4. 还没有 broker 成交后的二次滑点核对
5. 跨实例实时推送目前只有 Redis pub/sub 通知，没有 durable claim/lease 和补偿 worker
6. 当前没有把 dispatch 主链路改成“先写 Redis 再异步落库”，数据库 outbox 仍然坚持真源

第 6 点是刻意设计，不是遗漏。对这个项目来说，核心执行状态必须优先保证可恢复和可审计。

## 本地联调建议

1. 单节点本地联调时，把 `copier.mt5.follower-exec.realtime-dispatch.backend` 设为 `local`
2. 只有在验证多实例实时推送时，才切到 `redis`
3. follower EA 尽量显式填写 `FollowerAccountId`
4. 先看 `/api/follower-exec/sessions`，确认绑定成功后再做真实下单
5. 先用 `DRY_RUN` 跑开仓、平仓、TP/SL、挂单全链路，再切 `REAL`
