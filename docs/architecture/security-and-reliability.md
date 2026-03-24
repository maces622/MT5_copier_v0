# 安全与可靠性

## 当前安全边界

### 1. 凭证

1. WebSocket-only 账户可以不保存 MT5 密码
2. 如果传入 `credential`，会加密落库
3. Java 当前不直接使用这些凭证去登录 MT5

这意味着当前系统的主执行面主要在：

1. 主端 EA
2. follower EA
3. Java 路由与下发层

### 2. WebSocket 鉴权

当前已实现：

1. `/ws/trade` bearer/query-token
2. `/ws/follower-exec` bearer/query-token

### 3. 审计

当前会持久化：

1. 已接收的主端信号
2. execution command
3. follower dispatch 状态

## 当前可靠性策略

### 1. MariaDB 真源

以下数据始终以 MariaDB 为准：

1. 账户绑定
2. 风控规则
3. 主从关系
4. 品种映射
5. `execution_commands`
6. `follower_dispatch_outbox`
7. 信号审计

### 2. Redis 只做热状态和加速

Redis 当前承担：

1. route/risk/account-binding 缓存
2. runtime-state
3. signal dedup
4. session registry
5. 可选 pub/sub 通知

不会把 Redis 当成核心执行真源。

### 3. 比例跟单资金真实性保护

当前已经实现一条关键门禁：

1. `BALANCE_RATIO / EQUITY_RATIO` 必须拿到新鲜的 follower runtime-state
2. follower 余额或净值快照过期时，直接拒绝
3. 不再回退成 `1.0` 缩放继续下单

这是为了避免：

1. Redis 恢复后的旧余额被继续使用
2. follower 离线后仍按过期资金继续开仓

### 4. 幂等和重复保护

1. MT5 信号已经有 Redis TTL 去重
2. Copy Engine 对 `masterEventId + followerAccountId` 有 command 级重复保护

### 5. 并发保护

核心表当前已经有：

1. 关键索引
2. `@Version row_version`

Copy Engine follower 并行处理的线程安全保护：

1. `CopyHotPathIdAllocator` 使用 Redis `INCR`（原子操作），支持并行 ID 分配
2. 每个 follower 使用独立 `TransactionTemplate`，无跨 follower 事务冲突
3. `CopyHotPathRedisStore` 的 Redis 操作天然原子
4. `CopyHotPathPersistenceQueue` 使用 Redis LIST `RPUSH/LPOP`，天然线程安全

当前没有引入 Redisson 分布式锁，这是后续可选项，不是当前必需项。

## 当前已落地的工程优化

1. `spring.jpa.open-in-view=false`
2. 核心表补索引
3. 核心表补乐观锁
4. Redis-first 热路径替换高频 JPA 读
5. route fallback 批量查询，消掉 N+1
6. runtime-state Redis-first，数据库只做节流快照
7. 测试 profile 显式隔离本机 `SPRING_PROFILES_ACTIVE` 和 `COPIER_*`
8. DTO/entity/config 大量样板代码已用 Lombok 收缩
9. Lettuce 升级至 6.3.2，兼容 Redis 7+/8+ 的 HELLO AUTH 内联认证
10. follower 并行处理线程池 + 独立事务，延迟从 O(N) 降至 O(1)

## 备份与恢复原则

当前恢复策略必须遵守：

1. 先恢复 MariaDB
2. Redis 恢复后先清理易失 key
3. route/risk/account-binding 可以重建
4. runtime-state 恢复后要经过新鲜度门禁
5. 旧 websocket 会话和旧 dedup 窗口不能直接恢复成真相

详细流程见 [../operations/redis-backup-recovery.md](../operations/redis-backup-recovery.md)。

## 当前未完成项

1. 没有 MQ 级别的可靠投递骨干
2. 没有 durable claim/lease
3. 没有独立补偿 worker
4. 没有 broker 成交后的二次滑点核对
5. 没有 follower 执行前的高级保证金检查
