import { useMutation } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { queryClient } from '../lib/query'
import { login, register } from '../lib/api'

export function LoginPage() {
  return <AuthScreen mode="login" />
}

export function RegisterPage() {
  return <AuthScreen mode="register" />
}

function AuthScreen({ mode }: { mode: 'login' | 'register' }) {
  const navigate = useNavigate()
  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
      await navigate({ to: '/app/overview' })
    },
  })
  const registerMutation = useMutation({
    mutationFn: register,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
      await navigate({ to: '/app/overview' })
    },
  })

  const pending = loginMutation.isPending || registerMutation.isPending
  const error = loginMutation.error || registerMutation.error

  return (
    <div className="auth-layout">
      <div className="auth-panel auth-panel--intro">
        <div className="badge badge--accent">Platform Access</div>
        <h1>把账户配置台和复制监控台接进同一个运营入口。</h1>
        <p>
          当前控制台已经接入 `platform_id` 登录、`share_id + share_code` 绑定主账户、账户详情聚合视图和监控聚合视图。
        </p>
        <div className="auth-stats">
          <div>
            <span>后端接口</span>
            <strong>已连通</strong>
          </div>
          <div>
            <span>配置真源</span>
            <strong>MariaDB</strong>
          </div>
          <div>
            <span>热状态</span>
            <strong>Redis-first</strong>
          </div>
        </div>
      </div>

      <div className="auth-panel">
        <div className="page-header page-header--compact">
          <div>
            <div className="page-header__eyebrow">{mode === 'login' ? 'Sign In' : 'Create User'}</div>
            <h1>{mode === 'login' ? '平台登录' : '注册平台用户'}</h1>
            <p>{mode === 'login' ? '使用 platform_id 或 username 登录。' : '注册后会自动生成 platform_id 和 share_id。'}</p>
          </div>
        </div>

        <form
          className="form-grid"
          onSubmit={(event) => {
            event.preventDefault()
            const form = new FormData(event.currentTarget)
            const identifier = String(form.get('identifier') || '').trim()
            const username = String(form.get('username') || '').trim()
            const password = String(form.get('password') || '').trim()
            const displayName = String(form.get('displayName') || '').trim()

            if (mode === 'login') {
              loginMutation.mutate({
                login: identifier,
                password,
              })
              return
            }

            registerMutation.mutate({
              username,
              password,
              displayName: displayName || undefined,
            })
          }}
        >
          {mode === 'login' ? (
            <label className="field">
              <span>platform_id / username</span>
              <input name="identifier" placeholder="P10000001 或你的用户名" required />
            </label>
          ) : (
            <>
              <label className="field">
                <span>username</span>
                <input name="username" placeholder="trader_ops" required />
              </label>
              <label className="field">
                <span>display name</span>
                <input name="displayName" placeholder="运营台昵称" />
              </label>
            </>
          )}

          <label className="field">
            <span>password</span>
            <input name="password" type="password" placeholder="输入密码" required />
          </label>

          {error ? (
            <div className="inline-error">
              {error instanceof Error ? error.message : '请求失败'}
            </div>
          ) : null}

          <button className="button button--primary" disabled={pending} type="submit">
            {pending ? '提交中...' : mode === 'login' ? '登录控制台' : '注册并进入控制台'}
          </button>
        </form>

        <div className="auth-switch">
          {mode === 'login' ? (
            <>
              <span>还没有平台用户？</span>
              <button className="button button--ghost" onClick={() => void navigate({ to: '/register' })}>
                去注册
              </button>
            </>
          ) : (
            <>
              <span>已经注册过了？</span>
              <button className="button button--ghost" onClick={() => void navigate({ to: '/login' })}>
                去登录
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
