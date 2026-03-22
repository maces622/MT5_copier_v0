# MT5 WebSocket 信号协议

## 范围

这份文档描述的是当前仓库里已经落地的主端 MT5 上行协议，来源于：

1. `mt5/Websock_Sender_Master_v0.mq5`

它不是理想化草案，而是当前 Java 接入层必须兼容的真实协议。

## 连接参数

主端 EA 当前会使用：

1. `WsUrl`
2. `BearerToken`
3. `ReconnectIntervalMs`
4. `HeartbeatIntervalMs`
5. `MaxOutbox`
6. `MaxDealRetries`
7. `MaxOrderRetries`

## 消息类型

### 1. HELLO

用途：

1. 主端连接建立后的身份声明

典型字段：

1. `type`
2. `login`
3. `server`
4. `ts`

示例：

```json
{
  "type": "HELLO",
  "login": 51631,
  "server": "EBCFinancialGroupKY-Demo",
  "ts": "2026.03.22 08:49:52"
}
```

### 2. HEARTBEAT

用途：

1. 保活
2. 更新主端运行态

当前主端 HEARTBEAT 只带时间戳，不带余额净值。

示例：

```json
{
  "type": "HEARTBEAT",
  "ts": "2026.03.22 08:55:44"
}
```

### 3. DEAL

用途：

1. 开仓
2. 平仓
3. 比例平仓计算

当前关键字段：

1. `event_id`
2. `login`
3. `server`
4. `deal`
5. `order`
6. `position`
7. `symbol`
8. `action`
9. `volume`
10. `price`
11. `account_balance`
12. `account_equity`
13. `position_volume_before`
14. `position_volume_after`
15. `symbol_digits`
16. `symbol_point`
17. `symbol_tick_size`
18. `symbol_tick_value`
19. `symbol_contract_size`
20. `symbol_volume_step`
21. `symbol_volume_min`
22. `symbol_volume_max`
23. `symbol_currency_base`
24. `symbol_currency_profit`
25. `symbol_currency_margin`

说明：

1. `account_balance / account_equity` 用于比例跟单缩放
2. `position_volume_before / after` 用于部分平仓比例计算
3. `symbol_*` 元信息会透传到 follower dispatch payload

示例：

```json
{
  "type": "DEAL",
  "event_id": "51631-DEAL-31111112",
  "login": 51631,
  "server": "EBCFinancialGroupKY-Demo",
  "deal": 31111112,
  "order": 35962787,
  "position": 35962787,
  "symbol": "BTCUSD",
  "action": "BUY OPEN",
  "volume": 0.02,
  "price": 69158.66,
  "account_balance": 1810.12,
  "account_equity": 1809.66,
  "symbol_digits": 2,
  "symbol_point": 0.01,
  "symbol_tick_size": 0.01,
  "symbol_tick_value": 0.01,
  "symbol_contract_size": 1.0,
  "symbol_volume_step": 0.01,
  "symbol_volume_min": 0.01,
  "symbol_volume_max": 10.0,
  "symbol_currency_base": "USD",
  "symbol_currency_profit": "USD",
  "symbol_currency_margin": "USD",
  "time": "2026.03.22 08:59:08"
}
```

### 4. ORDER

用途：

1. 挂单新增
2. 挂单修改
3. 挂单删除
4. 市价持仓 TP/SL 同步
5. 市价单生命周期补充事件

关键字段：

1. `event`
2. `scope`
3. `event_id`
4. `order`
5. `position`
6. `symbol`
7. `order_type`
8. `order_state`
9. `vol_init`
10. `vol_cur`
11. `price_open`
12. `sl`
13. `tp`
14. `account_balance`
15. `account_equity`

说明：

1. `ORDER_UPDATE + market order + ACTIVE` 会被识别成 `SYNC_TP_SL`
2. `ORDER_ADD / ORDER_DELETE` 的市场单生命周期事件会被记录，但通常不会下发执行
3. 挂单场景下也会参与比例缩放计算

示例：

```json
{
  "type": "ORDER",
  "event": "ORDER_UPDATE",
  "scope": "ACTIVE",
  "event_id": "51631-ORDER_UPDATE-35962747-1774169402-6945565000-0-0-200",
  "login": 51631,
  "server": "EBCFinancialGroupKY-Demo",
  "order": 35962747,
  "position": 0,
  "symbol": "BTCUSD",
  "order_type": 2,
  "order_state": 1,
  "vol_init": 0.02,
  "vol_cur": 0.02,
  "price_open": 69455.65,
  "sl": 0,
  "tp": 0
}
```

## 当前服务端处理约束

### 1. 幂等

1. ingest 层先做信号去重
2. Copy Engine 再做 command 级重复保护

### 2. 顺序

当前主端链路仍然是单 websocket 输入，服务端不能假设绝对顺序可靠：

1. reconnect 后可能补发
2. DEAL 和 ORDER 可能时间接近
3. 市价单生命周期事件不应简单等同于真实复制动作

### 3. 协议与优化的关系

当前协议字段已经直接支撑这些优化：

1. `server + login` 账户绑定缓存
2. `account_balance / account_equity` 比例跟单
3. `position_volume_before / after` 比例平仓
4. `symbol_*` 元信息透传
5. `event_id` 去重与幂等

## 边界

这份文档只描述主端上行协议，不描述：

1. follower 下行协议
2. MQ topic 协议
3. 未来多服务拆分后的外部事件格式
