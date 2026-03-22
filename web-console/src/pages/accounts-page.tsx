import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { bindAccount, getMyAccounts } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { queryClient } from '../lib/query'
import { DataTable, EmptyState, ErrorState, LoadingState, PageHeader, StatusPill, Surface } from '../components/common'

export function AccountsPage() {
  const accountsQuery = useQuery({
    queryKey: ['accounts', 'mine'],
    queryFn: getMyAccounts,
  })
  const bindAccountMutation = useMutation({
    mutationFn: bindAccount,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['accounts', 'mine'] })
      await queryClient.invalidateQueries({ queryKey: ['share', 'profile'] })
      await queryClient.invalidateQueries({ queryKey: ['monitor', 'overview'] })
    },
  })

  if (accountsQuery.isPending) {
    return <LoadingState />
  }

  if (accountsQuery.error) {
    const error = accountsQuery.error
    return <ErrorState message={error instanceof Error ? error.message : '请求失败'} />
  }

  const accounts = accountsQuery.data!

  return (
    <div className="page-stack">
      <PageHeader
        title="我的 MT5 账户"
        description="展示当前登录用户名下的 master / follower 账户。接下来前端写操作会逐步替换掉手工命令行配置。"
      />

      <div className="two-column">
        <Surface title="绑定新账户" description="当前仍复用现有后端写接口，userId 由登录态自动带入。">
          <form
            className="form-grid"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              bindAccountMutation.mutate({
                brokerName: String(form.get('brokerName') || '').trim(),
                serverName: String(form.get('serverName') || '').trim(),
                mt5Login: Number(form.get('mt5Login')),
                credential: String(form.get('credential') || '').trim() || undefined,
                accountRole: String(form.get('accountRole') || 'FOLLOWER'),
                status: String(form.get('status') || 'ACTIVE'),
              })
            }}
          >
            <label className="field">
              <span>brokerName</span>
              <input name="brokerName" placeholder="EBC" required />
            </label>
            <label className="field">
              <span>serverName</span>
              <input name="serverName" placeholder="EBCFinancialGroupKY-Demo" required />
            </label>
            <label className="field">
              <span>mt5Login</span>
              <input min={1} name="mt5Login" placeholder="51631" required type="number" />
            </label>
            <label className="field">
              <span>accountRole</span>
              <select defaultValue="FOLLOWER" name="accountRole">
                <option value="MASTER">MASTER</option>
                <option value="FOLLOWER">FOLLOWER</option>
                <option value="BOTH">BOTH</option>
              </select>
            </label>
            <label className="field">
              <span>status</span>
              <select defaultValue="ACTIVE" name="status">
                <option value="ACTIVE">ACTIVE</option>
                <option value="PAUSED">PAUSED</option>
              </select>
            </label>
            <label className="field">
              <span>credential</span>
              <input name="credential" placeholder="留空表示 WebSocket-only" />
            </label>
            {bindAccountMutation.error ? (
              <div className="inline-error">{bindAccountMutation.error.message}</div>
            ) : null}
            {bindAccountMutation.data ? (
              <div className="inline-success">
                已绑定账户 #{bindAccountMutation.data.id} · {bindAccountMutation.data.serverName}:{bindAccountMutation.data.mt5Login}
              </div>
            ) : null}
            <button className="button button--primary" disabled={bindAccountMutation.isPending} type="submit">
              {bindAccountMutation.isPending ? '绑定中...' : '绑定 MT5 账户'}
            </button>
          </form>
        </Surface>
        <Surface title="写接口边界" description="账户绑定现在已经走 /api/me/accounts，不再要求前端显式传 userId。">
          <div className="bullet-list">
            <div>当前平台账户列表和详情都按登录态归属过滤。</div>
            <div>账户绑定已经切到登录态接口，前端不会再手填 userId。</div>
            <div>旧的基础配置接口仍保留，后续逐步下线。</div>
          </div>
        </Surface>
      </div>

      <Surface title="账户列表" description="点击详情可以查看风控、关系、映射和分享配置。">
        {accounts.length === 0 ? (
          <EmptyState title="还没有绑定 MT5 账户" message="先通过现有后端接口或 bootstrap 初始化一组账户。" />
        ) : (
          <DataTable
            headers={['ID', '角色', 'Broker / Server', 'Login', '状态', 'Credential', '更新时间', '详情']}
            rows={accounts.map((account) => [
              account.id,
              account.accountRole,
              `${account.brokerName} / ${account.serverName}`,
              account.mt5Login,
              <StatusPill value={account.status} />,
              account.credentialConfigured ? '已配置' : 'WebSocket-only',
              formatDateTime(account.updatedAt),
              <Link className="text-link" params={{ accountId: String(account.id) }} to="/app/accounts/$accountId">
                查看
              </Link>,
            ])}
          />
        )}
      </Surface>
    </div>
  )
}
