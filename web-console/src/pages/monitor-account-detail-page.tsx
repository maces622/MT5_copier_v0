import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { getMonitorAccountDetail, getMonitorCommands, getMonitorDispatches } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { DataTable, EmptyState, ErrorState, KeyValueGrid, LoadingState, PageHeader, StatusPill, Surface } from '../components/common'

export function MonitorAccountDetailPage() {
  const { accountId } = useParams({ from: '/app/monitor/accounts/$accountId' })
  const [eventKeyword, setEventKeyword] = useState('')
  const detailQuery = useQuery({
    queryKey: ['monitor', 'detail', accountId],
    queryFn: () => getMonitorAccountDetail(accountId),
  })
  const commandsQuery = useQuery({
    queryKey: ['monitor', 'commands', accountId],
    queryFn: () => getMonitorCommands(accountId),
  })
  const dispatchesQuery = useQuery({
    queryKey: ['monitor', 'dispatches', accountId],
    queryFn: () => getMonitorDispatches(accountId),
  })

  if (detailQuery.isPending || commandsQuery.isPending || dispatchesQuery.isPending) {
    return <LoadingState />
  }

  if (detailQuery.error || commandsQuery.error || dispatchesQuery.error) {
    const error = detailQuery.error || commandsQuery.error || dispatchesQuery.error
    return <ErrorState message={error instanceof Error ? error.message : '请求失败'} />
  }

  const detail = detailQuery.data!
  const commands = commandsQuery.data!.filter((command) =>
    !eventKeyword.trim() || command.masterEventId.toLowerCase().includes(eventKeyword.trim().toLowerCase()),
  )
  const dispatches = dispatchesQuery.data!.filter((dispatch) =>
    !eventKeyword.trim() || dispatch.masterEventId.toLowerCase().includes(eventKeyword.trim().toLowerCase()),
  )

  return (
    <div className="page-stack">
      <PageHeader
        title={`监控详情 #${accountId}`}
        description="汇总 runtime-state、websocket session、command 和 dispatch。"
      />

      <div className="two-column">
        <Surface title="Overview">
          {detail.overview ? (
            <KeyValueGrid
              items={[
                { label: 'accountKey', value: detail.overview.accountKey },
                { label: 'role', value: detail.overview.accountRole },
                { label: 'connection', value: <StatusPill value={detail.overview.connectionStatus} /> },
                { label: 'lastHeartbeat', value: formatDateTime(detail.overview.lastHeartbeatAt) },
                { label: 'pendingDispatch', value: detail.overview.pendingDispatchCount },
                { label: 'failedDispatch', value: detail.overview.failedDispatchCount },
              ]}
            />
          ) : (
            <EmptyState title="没有 overview" message="这个账户当前还没有聚合监控视图。" />
          )}
        </Surface>

        <Surface title="Runtime State">
          {detail.runtimeState ? (
            <KeyValueGrid
              items={[
                { label: 'connection', value: <StatusPill value={detail.runtimeState.connectionStatus} /> },
                { label: 'balance', value: detail.runtimeState.balance ?? 'n/a' },
                { label: 'equity', value: detail.runtimeState.equity ?? 'n/a' },
                { label: 'lastHello', value: formatDateTime(detail.runtimeState.lastHelloAt) },
                { label: 'lastHeartbeat', value: formatDateTime(detail.runtimeState.lastHeartbeatAt) },
                { label: 'lastSignalType', value: detail.runtimeState.lastSignalType ?? 'n/a' },
              ]}
            />
          ) : (
            <EmptyState title="没有 runtime-state" message="这个账户暂时还没有 heartbeat 或运行态快照。" />
          )}
        </Surface>
      </div>

      <Surface title="Event Filter" description="按 masterEventId 过滤当前账户下的命令和 dispatch。">
        <div className="toolbar">
          <label className="field">
            <span>masterEventId</span>
            <input
              placeholder="51631-DEAL-31111080"
              value={eventKeyword}
              onChange={(event) => setEventKeyword(event.target.value)}
            />
          </label>
        </div>
      </Surface>

      <Surface title="WebSocket Sessions">
        {detail.wsSessions.length === 0 && detail.followerExecSessions.length === 0 ? (
          <EmptyState title="没有会话" message="账户当前没有活动 websocket 记录。" />
        ) : (
          <div className="two-column">
            <DataTable
              headers={['MT5 Session', 'Server', 'Login', 'Status', 'Last Heartbeat']}
              rows={detail.wsSessions.map((session) => [
                session.sessionId,
                session.server || '—',
                session.login ?? '—',
                <StatusPill value={session.connectionStatus} />,
                formatDateTime(session.lastHeartbeatAt),
              ])}
            />
            <DataTable
              headers={['Follower Session', 'Account', 'Server', 'Login', 'Last Ack']}
              rows={detail.followerExecSessions.map((session) => [
                session.sessionId,
                session.followerAccountId ?? '—',
                session.server || '—',
                session.login ?? '—',
                formatDateTime(session.lastAckAt),
              ])}
            />
          </div>
        )}
      </Surface>

      <Surface title="Recent Commands">
        {commands.length === 0 ? (
          <EmptyState title="没有 command" message="这个账户目前还没有复制引擎命令记录。" />
        ) : (
          <DataTable
            headers={['ID', 'Event', 'Type', 'Status', 'Volume', 'Reason', 'Created']}
            rows={commands.slice(0, 12).map((command) => [
              command.id,
              command.masterEventId,
              command.commandType,
              <StatusPill value={command.status} />,
              command.requestedVolume ?? '—',
              command.rejectReason || '—',
              formatDateTime(command.createdAt),
            ])}
          />
        )}
      </Surface>

      <Surface title="Recent Dispatches">
        {dispatches.length === 0 ? (
          <EmptyState title="没有 dispatch" message="这个账户目前还没有 follower dispatch 记录。" />
        ) : (
          <DataTable
            headers={['ID', 'Execution', 'Status', 'Message', 'Acked', 'Updated']}
            rows={dispatches.slice(0, 12).map((dispatch) => [
              dispatch.id,
              dispatch.executionCommandId,
              <StatusPill value={dispatch.status} />,
              dispatch.statusMessage || '—',
              formatDateTime(dispatch.ackedAt),
              formatDateTime(dispatch.updatedAt),
            ])}
          />
        )}
      </Surface>
    </div>
  )
}
