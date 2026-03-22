# 账户与监控控制台方案

## 1. 目标

这份文档定义平台侧控制台的目标形态，覆盖两块能力：

1. 账户台：平台用户注册、登录、MT5 账户绑定、分享绑定、风控和主从关系管理
2. 监控台：账户连接状态、runtime-state、command/dispatch、订单/持仓链路追踪

这份文档同时区分两类内容：

1. 当前仓库已经落地的能力
2. 后续仍要继续补完的能力

## 2. 设计原则

1. 不推翻现有复制引擎和数据链路，控制台只是平台入口和运维界面
2. MariaDB 仍然是业务真源，Redis 只承担缓存、热状态和协调
3. 前端不直接感知 Redis，只消费后端聚合读模型
4. 当前优先做运维型控制台，不先做营销型收益展示

## 3. 核心业务规则

### 3.1 平台用户

每个平台用户注册后自动生成两类标识：

1. `platform_id`：平台登录标识
2. `share_id`：可对外展示的公开分享标识

规则：

1. 两者都必须全局唯一
2. 两者都不使用简单自增整数

### 3.2 MT5 账户

每个 MT5 账户归属于一个平台用户，角色保持：

1. `MASTER`
2. `FOLLOWER`
3. `BOTH`

WebSocket-only 场景下，Java 不要求持有 MT5 明文密码。

### 3.3 Master 分享

每个 `MASTER` 账户都可以单独设置一个 `share_code`。

规则：

1. `share_code` 属于 master 账户级，不是用户级
2. `share_code` 只存哈希，不回显明文
3. master 可以开启、关闭和重置 `share_code`

### 3.4 Follower 绑定 Master

follower 绑定 master 时，必须同时输入：

1. 目标平台用户的 `share_id`
2. 目标 master 账户的 `share_code`

确认策略：

1. `share_code` 重置后，只影响新绑定
2. 已有 follower 关系继续有效
3. 通过分享创建的新关系默认 `PAUSED`
4. follower 补完风控后再切到 `ACTIVE`
5. follower 可以主动解绑；解绑只删除关系，不影响 master 的分享配置和其他 follower
6. 共享绑定关系的激活、暂停、解绑都由 follower 侧决定

## 4. 数据模型

### 4.1 已落地

1. `platform_users`
2. `platform_user_sessions`
3. `master_share_configs`
4. 现有 `mt5_accounts`
5. 现有 `risk_rules`
6. 现有 `copy_relations`
7. 现有 `symbol_mappings`

### 4.2 当前接口读模型

当前控制台相关读模型已经落地：

1. 账户详情聚合：`PlatformAccountDetailResponse`
2. 当前用户关系列表聚合：`PlatformCopyRelationViewResponse`
3. 监控总览：`MonitorDashboardResponse`
4. 账户监控详情：`MonitorAccountDetailResponse`
5. 订单/持仓链路追踪：`ExecutionTraceResponse`

## 5. 后端接口状态

### 5.1 认证

已实现：

1. `POST /api/auth/register`
2. `POST /api/auth/login`
3. `POST /api/auth/logout`
4. `GET /api/auth/me`
5. `PUT /api/auth/me`

当前实现采用服务端 session + HttpOnly cookie。

### 5.2 账户与分享

已实现：

1. `GET /api/me/accounts`
2. `POST /api/me/accounts`
3. `GET /api/accounts/{accountId}`
4. `GET /api/accounts/{accountId}/detail`
5. `GET /api/accounts/{accountId}/risk-rule`
6. `GET /api/accounts/{accountId}/relations`
7. `GET /api/accounts/{accountId}/symbol-mappings`
8. `GET /api/me/share-profile`
9. `POST /api/accounts/{accountId}/share-config`
10. `PUT /api/accounts/{accountId}/share-config`
11. `POST /api/copy-relations/join-by-share`

### 5.3 当前用户写接口

已实现并按登录态做归属校验：

1. `POST /api/me/accounts`
2. `POST /api/me/accounts/{accountId}/risk-rule`
3. `GET /api/me/copy-relations`
4. `POST /api/me/copy-relations`
5. `PUT /api/me/copy-relations/{relationId}`
6. `DELETE /api/me/copy-relations/{relationId}`
7. `POST /api/me/accounts/{accountId}/symbol-mappings`

### 5.4 监控与追踪

已实现：

1. `GET /api/monitor/dashboard`
2. `GET /api/monitor/accounts/{accountId}/detail`
3. `GET /api/monitor/accounts/{accountId}/commands`
4. `GET /api/monitor/accounts/{accountId}/dispatches`
5. `GET /api/monitor/traces/order`
6. `GET /api/monitor/traces/position`

## 6. 前端信息架构

前端已经独立落在 `web-console/`，技术栈为：

1. React
2. Vite
3. TypeScript
4. TanStack Router
5. TanStack Query

### 6.1 已落地页面

1. `/login`
2. `/register`
3. `/app/overview`
4. `/app/accounts`
5. `/app/accounts/:accountId`
6. `/app/share`
7. `/app/follow/bind`
8. `/app/relations`
9. `/app/monitor/accounts`
10. `/app/monitor/accounts/:accountId`
11. `/app/traces/order`
12. `/app/traces/position`
13. `/app/settings/profile`

### 6.2 已落地前端写操作

1. MT5 账户绑定
2. 风控保存
3. 同用户主从关系创建/更新
4. 品种映射保存
5. share 配置保存
6. `share_id + share_code` 绑定
7. 当前用户关系列表查看、同用户关系调整和 follower 主动解绑
8. 当前用户显示名称和密码修改

### 6.3 页面职责

1. `/app/accounts`
   负责当前用户 MT5 账户列表和账户绑定
2. `/app/accounts/:accountId`
   负责单账户的风控、映射、分享配置和关系局部编辑
3. `/app/share`
   展示当前用户 `share_id` 和名下 master 的分享配置
4. `/app/follow/bind`
   负责通过 `share_id + share_code` 创建跨用户关系
5. `/app/relations`
   负责当前用户视角的关系总览、同用户关系调整和 follower 主动解绑
6. `/app/monitor/accounts`
   负责监控列表和筛选
7. `/app/monitor/accounts/:accountId`
   负责单账户 runtime、signals、commands、dispatches 视图
8. `/app/traces/order` / `/app/traces/position`
   负责按订单或持仓查看复制链路
9. `/app/settings/profile`
   负责当前平台用户的显示名称和登录密码修改

## 7. 权限边界

Phase 1 保持最小权限模型：

1. `USER`
2. `ADMIN`

规则：

1. 普通用户只能查看和修改自己拥有的账户
2. 普通用户只能以自己拥有的 follower 发起 share 绑定
3. 普通用户只能读取与自己相关的关系和监控视图
4. 管理员可以跨用户查看

## 8. 当前已经确认的边界

1. 控制台不重写 Copy Engine
2. 核心交易状态仍以 `execution_commands` 和 `follower_dispatch_outbox` 为真源
3. Redis pub/sub 只做跨实例通知，不是业务真源
4. 单节点本地联调推荐 `copier.mt5.follower-exec.realtime-dispatch.backend=local`

## 9. 后续工作

当前还没有完全收口的部分：

1. 更细粒度 RBAC
2. 异常提示和告警编排
3. 更完整的个人设置页和安全设置
4. 更深的监控可视化
5. 与外部通知或工单系统的集成

## 10. 实现顺序

建议继续按下面顺序推进：

1. 保持后端登录态和聚合读模型优先
2. 继续补完前端监控筛选和用户设置
3. 最后再补更重的可视化和告警
