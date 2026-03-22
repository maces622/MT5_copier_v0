import { Link } from '@tanstack/react-router'

export function NotFoundPage() {
  return (
    <div className="empty-state empty-state--full">
      <strong>这个页面还没有落地</strong>
      <p>当前控制台先覆盖登录、账户、分享绑定和监控主链路。</p>
      <Link className="button button--primary" to="/app/overview">
        回到总览
      </Link>
    </div>
  )
}
