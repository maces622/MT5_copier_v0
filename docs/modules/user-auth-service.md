# 用户认证服务

## 1. 目标

这个模块负责平台层身份体系，不直接负责 MT5 交易执行。

职责包括：

1. 平台用户注册
2. 平台用户登录和登出
3. 平台用户会话管理
4. 当前用户身份解析
5. 为账户台、分享绑定和监控台提供登录上下文

## 2. 当前已实现能力

1. 平台用户注册
2. 平台用户登录和登出
3. 当前用户查询
4. 当前用户资料更新
5. 注册后自动生成 `platform_id`
6. 注册后自动生成 `share_id`
7. 服务端 session + HttpOnly cookie
8. `USER` / `ADMIN` 基础角色

## 3. 数据模型

### 3.1 平台用户

表：`platform_users`

核心字段：

1. `id`
2. `platform_id`
3. `username`
4. `password_hash`
5. `share_id`
6. `display_name`
7. `status`
8. `role`
9. `created_at`
10. `updated_at`

### 3.2 平台会话

表：`platform_user_sessions`

核心字段：

1. `id`
2. `user_id`
3. `session_token_hash`
4. `expires_at`
5. `last_seen_at`
6. `ip`
7. `user_agent`

## 4. 当前接口

### 4.1 已实现

1. `POST /api/auth/register`
2. `POST /api/auth/login`
3. `POST /api/auth/logout`
4. `GET /api/auth/me`
5. `PUT /api/auth/me`

### 4.2 `PUT /api/auth/me`

当前支持的资料修改项：

1. `displayName`
2. `currentPassword`
3. `newPassword`

规则：

1. `displayName` 可以单独修改
2. 修改密码时必须同时提供 `currentPassword`
3. `currentPassword` 校验失败时返回 `401`
4. `newPassword` 至少 6 位

## 5. 与其他模块的关系

1. Account/Config 模块通过当前登录用户确定 MT5 账户归属
2. Share 绑定通过当前登录用户限制 follower 账户可见范围
3. Monitor 控制台通过当前登录用户决定可见账户范围
4. 前端控制台的 `/app/settings/profile` 使用本模块的 `GET /api/auth/me` 和 `PUT /api/auth/me`

## 6. 当前边界

1. 当前仍然使用服务端 session，不是 JWT 体系
2. 当前没有短信、邮箱找回和 MFA
3. 当前没有更细粒度 RBAC
4. 当前 profile 页面只做显示名称和密码修改，不做账号注销或安全设备管理
