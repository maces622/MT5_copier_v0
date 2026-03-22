import { useQuery } from '@tanstack/react-query'
import { getShareProfile } from '../lib/api'
import { formatBoolean, formatDateTime } from '../lib/format'
import { DataTable, EmptyState, ErrorState, LoadingState, PageHeader, Surface } from '../components/common'

export function SharePage() {
  const profileQuery = useQuery({
    queryKey: ['share', 'profile'],
    queryFn: getShareProfile,
  })

  if (profileQuery.isPending) {
    return <LoadingState />
  }

  if (profileQuery.error) {
    return <ErrorState message={profileQuery.error.message} />
  }

  const profile = profileQuery.data!

  return (
    <div className="page-stack">
      <PageHeader
        title="分享中心"
        description="用户级 share_id 对外公开；账户级 share_code 只保存在哈希里。"
      />

      <div className="two-column">
        <Surface title="我的 share_id">
          <div className="share-card">
            <div className="share-card__code">{profile.shareId}</div>
            <p>把这个 ID 给其他用户；他们仍然需要知道某个 master 账户对应的 share_code 才能绑定。</p>
          </div>
        </Surface>

        <Surface title="绑定规则">
          <div className="bullet-list">
            <div>share_id 是用户级公开标识，不是账户口令。</div>
            <div>share_code 属于具体 master 账户，重置后只影响新绑定。</div>
            <div>join-by-share 创建的新关系默认 PAUSED，先补风控再启用。</div>
          </div>
        </Surface>
      </div>

      <Surface title="可分享的 Master 账户">
        {profile.masterAccounts.length === 0 ? (
          <EmptyState title="没有可分享的 Master" message="先绑定一个 MASTER 账户，再在账户详情里配置 share_code。" />
        ) : (
          <DataTable
            headers={['Master ID', 'Broker / Server', 'Login', '分享开关', 'Code', '备注', '轮换时间']}
            rows={profile.masterAccounts.map((account) => [
              account.masterAccountId,
              `${account.brokerName} / ${account.serverName}`,
              account.mt5Login,
              formatBoolean(account.shareEnabled),
              formatBoolean(account.shareCodeConfigured),
              account.shareNote || '—',
              formatDateTime(account.rotatedAt),
            ])}
          />
        )}
      </Surface>
    </div>
  )
}
