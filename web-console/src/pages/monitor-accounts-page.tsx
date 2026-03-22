import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { getMonitorOverview, getMyAccounts } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { DataTable, EmptyState, ErrorState, LoadingState, PageHeader, StatusPill, Surface } from '../components/common'

export function MonitorAccountsPage() {
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const accountsQuery = useQuery({
    queryKey: ['accounts', 'mine'],
    queryFn: getMyAccounts,
  })
  const overviewQuery = useQuery({
    queryKey: ['monitor', 'overview'],
    queryFn: getMonitorOverview,
  })

  if (accountsQuery.isPending || overviewQuery.isPending) {
    return <LoadingState />
  }

  if (accountsQuery.error || overviewQuery.error) {
    const error = accountsQuery.error || overviewQuery.error
    return <ErrorState message={error instanceof Error ? error.message : '请求失败'} />
  }

  const accountIds = new Set(accountsQuery.data!.map((account) => account.id))
  const rows = overviewQuery.data!
    .filter((row) => accountIds.has(row.accountId))
    .filter((row) => {
      if (statusFilter === 'ALL') {
        return true
      }
      return row.connectionStatus === statusFilter
    })
    .filter((row) => {
      if (!keyword.trim()) {
        return true
      }
      const normalized = keyword.trim().toLowerCase()
      return [
        row.accountKey,
        row.brokerName,
        row.serverName,
        String(row.mt5Login),
        row.accountRole,
      ]
        .join(' ')
        .toLowerCase()
        .includes(normalized)
    })

  return (
    <div className="page-stack">
      <PageHeader
        title="账户监控台"
        description="这一层主要面向运维，先看连接、runtime-state、dispatch 压力和失败情况。"
      />

      <Surface title="我的账户监控视图" description="先在前端做快速过滤，后续再视数据量决定是否下推后端查询参数。">
        <div className="toolbar">
          <label className="field">
            <span>搜索</span>
            <input
              placeholder="accountKey / broker / server / login"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </label>
          <label className="field">
            <span>连接状态</span>
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="ALL">ALL</option>
              <option value="CONNECTED">CONNECTED</option>
              <option value="DISCONNECTED">DISCONNECTED</option>
              <option value="UNKNOWN">UNKNOWN</option>
            </select>
          </label>
        </div>
        {rows.length === 0 ? (
          <EmptyState title="当前没有监控数据" message="确认账户已绑定并至少有一次 HELLO / HEARTBEAT。" />
        ) : (
          <DataTable
            headers={['Account', 'Role', 'Connection', 'Last Heartbeat', 'Pending', 'Failed', '详情']}
            rows={rows.map((row) => [
              row.accountKey,
              row.accountRole,
              <StatusPill value={row.connectionStatus} />,
              formatDateTime(row.lastHeartbeatAt),
              row.pendingDispatchCount,
              row.failedDispatchCount,
              <Link className="text-link" params={{ accountId: String(row.accountId) }} to="/app/monitor/accounts/$accountId">
                进入详情
              </Link>,
            ])}
          />
        )}
      </Surface>
    </div>
  )
}
