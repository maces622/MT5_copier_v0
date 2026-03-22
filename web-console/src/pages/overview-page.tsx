import { useQuery } from '@tanstack/react-query'
import { getMonitorDashboard, getShareProfile } from '../lib/api'
import { MetricCard, PageHeader, Surface, LoadingState, ErrorState, KeyValueGrid } from '../components/common'

export function OverviewPage() {
  const dashboardQuery = useQuery({
    queryKey: ['monitor', 'dashboard'],
    queryFn: getMonitorDashboard,
  })
  const shareProfileQuery = useQuery({
    queryKey: ['share', 'profile'],
    queryFn: getShareProfile,
  })

  if (dashboardQuery.isPending || shareProfileQuery.isPending) {
    return <LoadingState />
  }

  if (dashboardQuery.error || shareProfileQuery.error) {
    const error = dashboardQuery.error || shareProfileQuery.error
    return <ErrorState message={error instanceof Error ? error.message : '请求失败'} />
  }

  const dashboard = dashboardQuery.data!
  const shareProfile = shareProfileQuery.data!

  return (
    <div className="page-stack">
      <PageHeader
        title="运营总览"
        description="把平台用户身份、复制链路运行态和分享绑定入口放在同一张总览页上。"
      />

      <div className="metrics-grid">
        <MetricCard label="账户总数" value={dashboard.totalAccounts} tone="neutral" />
        <MetricCard label="在线 Master" value={dashboard.onlineMasterCount} tone="good" />
        <MetricCard label="在线 Follower" value={dashboard.onlineFollowerCount} tone="good" />
        <MetricCard label="Stale Runtime" value={dashboard.staleRuntimeStateCount} tone="warn" />
        <MetricCard label="Pending Dispatch" value={dashboard.pendingDispatchCount} tone="warn" />
        <MetricCard label="Failed Dispatch" value={dashboard.failedDispatchCount} tone="bad" />
      </div>

      <div className="two-column">
        <Surface title="当前分享档案" description="share_id 是用户级公开标识，master 账户再单独挂 share_code。">
          <KeyValueGrid
            items={[
              { label: 'platform_id', value: shareProfile.platformId },
              { label: 'share_id', value: shareProfile.shareId },
              { label: '显示名', value: shareProfile.displayName || '未设置' },
              { label: '可分享 Master 数', value: shareProfile.masterAccounts.length },
            ]}
          />
        </Surface>

        <Surface title="当前阶段边界" description="这张控制台优先服务配置和排障，不先做收益营销视图。">
          <div className="bullet-list">
            <div>账户配置和监控台后端已接好，前端从现有接口直接取数。</div>
            <div>MariaDB 仍然是真源，Redis 只承担热状态和缓存。</div>
            <div>share_id + share_code 绑定主账户的核心流程已经可以在控制台里闭环。</div>
          </div>
        </Surface>
      </div>
    </div>
  )
}
