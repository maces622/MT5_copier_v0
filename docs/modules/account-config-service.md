# 账户与配置服务

## 目标

这个模块负责维护跟单系统的静态配置真源：

1. MT5 账户绑定
2. 主从关系
3. 风控规则
4. 品种映射
5. 供 Copy Engine 使用的 route/risk/account-binding 缓存

当前实现里，MariaDB 是配置真源，Redis 是热缓存层。

## 当前已实现能力

1. `POST /api/accounts` 绑定或更新 MT5 账户
2. `POST /api/risk-rules` upsert follower 风控
3. `POST /api/copy-relations` 创建主从关系
4. `PUT /api/copy-relations/{relationId}` 更新关系状态和模式
5. `POST /api/symbol-mappings` upsert follower 品种映射
6. `GET /api/accounts`
7. `GET /api/copy-relations/master/{masterAccountId}`
8. 本地 bootstrap 命令行初始化
9. `GET /api/me/share-profile`
10. `POST /api/accounts/{accountId}/share-config`
11. `PUT /api/accounts/{accountId}/share-config`
12. `POST /api/copy-relations/join-by-share`
13. `GET /api/me/accounts`
14. `POST /api/me/accounts`
15. `POST /api/me/accounts/{accountId}/risk-rule`
16. `GET /api/me/copy-relations`
17. `POST /api/me/copy-relations`
18. `PUT /api/me/copy-relations/{relationId}`
19. `POST /api/me/accounts/{accountId}/symbol-mappings`
20. `GET /api/accounts/{accountId}`
21. `GET /api/accounts/{accountId}/detail`
22. `GET /api/accounts/{accountId}/risk-rule`
23. `GET /api/accounts/{accountId}/relations`
24. `GET /api/accounts/{accountId}/symbol-mappings`

## 数据模型

### 1. MT5 账户

核心字段：

1. `userId`
2. `serverName`
3. `mt5Login`
4. `accountRole`
5. `status`
6. `credentialCiphertext`
7. `credentialVersion`

说明：

1. Java 当前不直接登录 MT5，所以 WebSocket-only 账户可以不填 `credential`
2. 如果传了 `credential`，仍会按受控方式加密落库

### 2. 主从关系

核心字段：

1. `masterAccountId`
2. `followerAccountId`
3. `copyMode`
4. `status`
5. `priority`
6. `configVersion`

当前默认模式已经切到 `BALANCE_RATIO`。

### 4. Master 分享配置

当前已新增 master 分享配置层：

1. `share_id` 属于平台用户
2. `share_code` 属于 master 账户
3. `share_code` 按哈希存储
4. follower 可通过 `share_id + share_code` 跨用户建立主从关系

### 3. 风控规则

核心字段：

1. `fixedLot`
2. `balanceRatio`
3. `maxLot`
4. `maxSlippagePoints`
5. `maxSlippagePips`
6. `allowedSymbols`
7. `blockedSymbols`
8. `followTpSl`
9. `reverseFollow`

## Redis 缓存职责

这个模块负责把数据库真源投影成 Redis 快照，供高频路径直接读。

当前 key：

1. `copy:route:master:{masterAccountId}`
2. `copy:route:version:{masterAccountId}`
3. `copy:account:risk:{followerAccountId}`
4. `copy:account:binding:{server}:{login}`

这些缓存都不是业务真源，丢失后可以从 MariaDB 重建。

## 当前已落地的优化

### 1. Redis-first 配置读取

Copy Engine 现在不会在热路径上频繁直接扫关系表，而是优先读：

1. master account binding
2. master route snapshot
3. follower risk snapshot

只有 Redis miss 时才回源数据库并回填。

### 2. 启动预热

服务启动后会预热：

1. route
2. risk
3. account binding

这样本地联调和服务重启后的第一笔单不会总是打到数据库冷路径。

### 3. Route fallback 批量装配

之前 route fallback 在 miss/warmup 场景会按 follower 逐个查：

1. risk rule
2. symbol mapping

现在已经改成批量查询后在内存组装，消掉了这一段的 N+1。

### 4. 乐观锁和索引

核心配置表都加了：

1. 关键索引
2. `@Version row_version`

目的是降低并发配置更新时的覆盖风险，而不是依赖裸 `save()`。

### 5. Bootstrap 初始化

为了保留“命令行初始化”的使用习惯，当前仓库已经支持：

1. 用 JSON 定义账户、关系、风控、映射
2. 一次性落库
3. 自动刷新缓存

### 6. Share 绑定扩展

当前已经支持：

1. master 开启或关闭分享
2. master 重置 `share_code`
3. follower 通过 `share_id + share_code` 建立关系
4. 新关系默认 `PAUSED`
5. 当前登录用户视角的账户详情、风控、关系、映射读取接口已落地
6. 当前登录用户视角的账户绑定、风控保存、关系创建/更新、品种映射保存接口已落地
7. 当前登录用户视角的关系列表聚合接口 `GET /api/me/copy-relations` 已落地，可直接支撑关系管理页

## 设计边界

1. 这个模块不负责真实下单
2. 这个模块不负责交易审计流水
3. 这个模块不把 Redis 当真源
4. 这个模块当前没有对外 MQ 广播配置变更
5. 新增的 `/api/me/accounts` 和 `/api/accounts/{accountId}/*` 已按登录态做归属校验
6. `POST /api/me/accounts`、`POST /api/me/accounts/{accountId}/risk-rule`、`GET /api/me/copy-relations`、`POST /api/me/copy-relations`、`PUT /api/me/copy-relations/{relationId}`、`POST /api/me/accounts/{accountId}/symbol-mappings` 已按登录态做归属校验
7. 现有 `/api/accounts`、`/api/risk-rules`、`/api/copy-relations`、`/api/symbol-mappings` 等基础写接口仍保留，主要用于 bootstrap 和历史兼容

## 当前未完成项

1. Redisson 分布式锁还没有引入
2. 配置变更事件还没有对外发布到 MQ
3. 更细粒度的关系级风控参数还没有补齐

## 本地联调建议

1. 先用 bootstrap 或 REST API 把账户、关系、风控、映射写进 MariaDB
2. 再启动 `local` profile 服务
3. 让 Redis 只承担缓存，不手工往 Redis 写业务配置
