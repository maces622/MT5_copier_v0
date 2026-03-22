import { useMutation, useQuery } from '@tanstack/react-query'
import { getMyAccounts, joinByShare } from '../lib/api'
import { queryClient } from '../lib/query'
import { EmptyState, ErrorState, LoadingState, PageHeader, Surface, StatusPill } from '../components/common'

export function FollowBindPage() {
  const accountsQuery = useQuery({
    queryKey: ['accounts', 'mine'],
    queryFn: getMyAccounts,
  })
  const bindMutation = useMutation({
    mutationFn: joinByShare,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['accounts', 'mine'] })
      await queryClient.invalidateQueries({ queryKey: ['relations', 'mine'] })
    },
  })

  if (accountsQuery.isPending) {
    return <LoadingState />
  }

  if (accountsQuery.error) {
    return <ErrorState message={accountsQuery.error.message} />
  }

  const followerAccounts = accountsQuery.data!.filter(
    (account) => account.accountRole === 'FOLLOWER' || account.accountRole === 'BOTH',
  )

  return (
    <div className="page-stack">
      <PageHeader
        title="绑定主账户"
        description="通过 share_id + share_code 解析到对方的 master，再为你自己的 follower 创建一条默认 PAUSED 的跟单关系。"
      />

      <div className="two-column">
        <Surface title="绑定表单">
          {followerAccounts.length === 0 ? (
            <EmptyState title="没有可用的 follower 账户" message="先给当前用户绑定一个 FOLLOWER 或 BOTH 角色账户。" />
          ) : (
            <form
              className="form-grid"
              onSubmit={(event) => {
                event.preventDefault()
                const form = new FormData(event.currentTarget)
                bindMutation.mutate({
                  followerAccountId: Number(form.get('followerAccountId')),
                  shareId: String(form.get('shareId') || '').trim(),
                  shareCode: String(form.get('shareCode') || '').trim(),
                  copyMode: String(form.get('copyMode') || 'BALANCE_RATIO'),
                  priority: Number(form.get('priority') || 100),
                })
              }}
            >
              <label className="field">
                <span>Follower 账户</span>
                <select defaultValue={String(followerAccounts[0].id)} name="followerAccountId">
                  {followerAccounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      #{account.id} · {account.serverName}:{account.mt5Login}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>share_id</span>
                <input name="shareId" placeholder="输入对方平台用户 share_id" required />
              </label>
              <label className="field">
                <span>share_code</span>
                <input name="shareCode" placeholder="输入该 master 对应的 share_code" required />
              </label>
              <label className="field">
                <span>copy mode</span>
                <select defaultValue="BALANCE_RATIO" name="copyMode">
                  <option value="BALANCE_RATIO">BALANCE_RATIO</option>
                  <option value="EQUITY_RATIO">EQUITY_RATIO</option>
                  <option value="FIXED_LOT">FIXED_LOT</option>
                  <option value="FOLLOW_MASTER">FOLLOW_MASTER</option>
                </select>
              </label>
              <label className="field">
                <span>priority</span>
                <input defaultValue={100} min={1} name="priority" type="number" />
              </label>
              {bindMutation.error ? <div className="inline-error">{bindMutation.error.message}</div> : null}
              {bindMutation.data ? (
                <div className="inline-success">
                  已创建 relation #{bindMutation.data.id}，当前状态 {bindMutation.data.status}
                </div>
              ) : null}
              <button className="button button--primary" disabled={bindMutation.isPending} type="submit">
                {bindMutation.isPending ? '绑定中...' : '创建 PAUSED 关系'}
              </button>
            </form>
          )}
        </Surface>

        <Surface title="候选 follower">
          {followerAccounts.length === 0 ? (
            <EmptyState title="没有 follower 账户" message="当前登录用户还没有可用于绑定的 follower。" />
          ) : (
            <div className="stack-list">
              {followerAccounts.map((account) => (
                <div key={account.id} className="stack-list__item">
                  <div>
                    <strong>#{account.id}</strong>
                    <p>
                      {account.serverName}:{account.mt5Login}
                    </p>
                  </div>
                  <StatusPill value={account.status} />
                </div>
              ))}
            </div>
          )}
        </Surface>
      </div>
    </div>
  )
}
