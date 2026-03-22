import { Link, Outlet, useNavigate, useRouterState } from '@tanstack/react-router'
import { useMutation } from '@tanstack/react-query'
import { logout } from '../lib/api'
import type { AuthenticatedPlatformUserResponse } from '../lib/types'

const navItems = [
  { href: '/app/overview', to: '/app/overview', label: 'Overview', note: '系统总览' },
  { href: '/app/accounts', to: '/app/accounts', label: 'Accounts', note: '账户配置台' },
  { href: '/app/share', to: '/app/share', label: 'Share', note: '分享与绑定' },
  { href: '/app/follow/bind', to: '/app/follow/bind', label: 'Bind Master', note: '绑定主账户' },
  { href: '/app/relations', to: '/app/relations', label: 'Relations', note: '关系管理' },
  { href: '/app/monitor/accounts', to: '/app/monitor/accounts', label: 'Monitor', note: '监控台' },
  { href: '/app/settings/profile', to: '/app/settings/profile', label: 'Settings', note: '个人设置' },
  {
    href: '/app/traces/order',
    to: '/app/traces/$traceType',
    params: { traceType: 'order' as const },
    label: 'Traces',
    note: '链路追踪',
  },
] as const

export function AppShell({ user }: { user: AuthenticatedPlatformUserResponse }) {
  const navigate = useNavigate()
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: async () => {
      await navigate({ to: '/login' })
    },
  })

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <div className="sidebar__eyebrow">Copy Trader MT5</div>
          <div className="sidebar__title">Operator Console</div>
          <p className="sidebar__lead">
            账户配置、分享绑定、复制链路监控统一收口到一个控制台中。
          </p>
        </div>

        <nav className="nav">
          {navItems.map((item) => (
            <Link
              key={item.href}
              to={item.to}
              params={'params' in item ? item.params : undefined}
              className={`nav__item${pathname.startsWith(item.href) ? ' nav__item--active' : ''}`}
            >
              <span>{item.label}</span>
              <small>{item.note}</small>
            </Link>
          ))}
        </nav>

        <div className="sidebar__meta">
          <div className="badge badge--accent">share_id</div>
          <div className="sidebar__code">{user.shareId}</div>
          <div className="badge">{user.role}</div>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <div className="topbar__eyebrow">登录态</div>
            <div className="topbar__user">
              {user.displayName || user.username}
              <span>{user.platformId}</span>
            </div>
          </div>
          <button
            className="button button--ghost"
            disabled={logoutMutation.isPending}
            onClick={() => logoutMutation.mutate()}
          >
            退出登录
          </button>
        </header>

        <div className="page-body">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
