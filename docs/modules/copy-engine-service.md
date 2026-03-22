# 跟单引擎

## 目标

Copy Engine 是当前系统的核心决策层，负责回答三个问题：

1. 这笔主端信号要不要复制
2. 要复制给哪些 follower
3. 每个 follower 应该以什么指令、什么手数执行

## 当前真实链路

主链路现在是：

1. 主端 EA 通过 `/ws/trade` 上报 `DEAL / ORDER`
2. Signal Ingest 标准化后发布进进程内事件总线
3. Copy Engine 消费事件
4. 先读 Redis-first 的 account binding / route / risk / runtime-state
5. 生成 `execution_commands`
6. 为 `READY` 指令生成 `follower_dispatch_outbox`
7. follower-exec 模块通过 websocket 实时下发

## 当前支持的复制模式

1. `FIXED_LOT`
2. `BALANCE_RATIO`
3. `EQUITY_RATIO`
4. `FOLLOW_MASTER`

默认新建主从关系时使用 `BALANCE_RATIO`。

## 当前支持的指令类型

1. `OPEN_POSITION`
2. `CLOSE_POSITION`
3. `SYNC_TP_SL`
4. `CREATE_PENDING_ORDER`
5. `UPDATE_PENDING_ORDER`
6. `CANCEL_PENDING_ORDER`

## 核心执行逻辑

### 1. 账户绑定

主端信号进入后，先通过 `server + login` 找到平台内 `masterAccountId`。

当前已经做成 Redis-first：

1. `copy:account:binding:{server}:{login}`
2. miss 时回源数据库并回填

### 2. 路由与风控装配

Copy Engine 再读取：

1. `copy:route:master:{masterAccountId}`
2. `copy:account:risk:{followerAccountId}`

route/risk 都是 Redis-first。

### 3. 手数计算

当前比例开仓的核心公式是：

`主手数 * 风险比例 * 账户资金缩放比例`

其中：

1. 风险比例来自 `balanceRatio`，默认 `1.0`
2. 账户资金缩放比例来自 `followerFunds / masterFunds`

### 4. 比例平仓

平仓不再按绝对手数硬平，而是：

1. 主端部分平仓时，生成 `closeRatio`
2. follower 按自己的当前仓位同比例平仓
3. 最后一笔全平时，生成 `closeAll=true`

### 5. 点差限制

当前逻辑是：

1. 默认关闭
2. 开启后只限制 `OPEN_POSITION`
3. `CLOSE_POSITION` 不做点差限制

### 6. 品种元信息

dispatch payload 当前已包含：

1. `contractSize`
2. `volumeMin`
3. `volumeMax`
4. `volumeStep`
5. `point`
6. `tickSize`
7. `tickValue`
8. 币种信息

用于 follower EA 做本地手数和交易参数校验。

## 当前已落地的优化

### 1. Redis-first 热路径

高频热路径不再直接依赖 JPA 拼装主从关系，而是优先读 Redis：

1. account binding
2. route snapshot
3. risk snapshot
4. runtime-state

### 2. 数据库真源保留

虽然读路径已经 Redis-first，但这两个核心表仍然坚持直写数据库：

1. `execution_commands`
2. `follower_dispatch_outbox`

这是刻意保留的设计，因为它们属于核心交易状态，不能先写 Redis 再说。

### 3. Route fallback 去 N+1

Redis miss 或 warmup 时，route fallback 已经改成：

1. 批量查关系
2. 批量查 risk rules
3. 批量查 symbol mappings
4. 在内存组装 snapshot

不再按 follower 逐个查库。

### 4. 比例跟单资金快照门禁

这是当前最关键的安全优化之一。

`BALANCE_RATIO / EQUITY_RATIO` 现在会强制检查 follower runtime-state 是否：

1. 存在
2. 新鲜
3. 余额或净值有效

如果不满足，直接：

1. `status = REJECTED`
2. `rejectReason = ACCOUNT_FUNDS_UNAVAILABLE`

不再偷偷回退成 `1.0` 倍缩放继续下单。

### 5. 幂等保护

同一 follower 对同一 `masterEventId` 不会重复生成 command。

### 6. 乐观锁

核心执行表已经补了：

1. `row_version`
2. 关键索引

目的是减小并发更新冲突风险。

## 风险与边界

1. 当前没有把 command/outbox 放进 Redis List 做异步削峰
2. 当前没有 broker 成交后的二次滑点核对
3. 当前没有 margin 预校验
4. 当前没有跨实例 durable claim/lease 补偿 worker

这些没做是刻意取舍，不是遗漏。

## 当前未完成项

1. `BUY_STOP_LIMIT / SELL_STOP_LIMIT` 还未支持
2. 更复杂的 broker 执行编排未实现
3. 核心执行链路外的异步审计批量写还未单独拆出
