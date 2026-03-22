import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { getOrderTrace, getPositionTrace } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { DataTable, EmptyState, ErrorState, KeyValueGrid, LoadingState, PageHeader, StatusPill, Surface } from '../components/common'

export function TracePage() {
  const { traceType } = useParams({ from: '/app/traces/$traceType' })
  const [submitted, setSubmitted] = useState<{ masterAccountId: number; targetId: number } | null>(null)

  const traceQuery = useQuery({
    queryKey: ['monitor', 'trace', traceType, submitted?.masterAccountId, submitted?.targetId],
    queryFn: () =>
      traceType === 'position'
        ? getPositionTrace(submitted!.masterAccountId, submitted!.targetId)
        : getOrderTrace(submitted!.masterAccountId, submitted!.targetId),
    enabled: submitted !== null,
  })

  const traceLabel = traceType === 'position' ? '持仓' : '订单'
  const targetField = traceType === 'position' ? 'masterPositionId' : 'masterOrderId'

  return (
    <div className="page-stack">
      <PageHeader
        title={`${traceLabel}链路追踪`}
        description="按 master 订单号或持仓号回看 command 和 dispatch 的整条复制链路。"
      />

      <Surface title="Trace Query">
        <form
          className="toolbar"
          onSubmit={(event) => {
            event.preventDefault()
            const form = new FormData(event.currentTarget)
            setSubmitted({
              masterAccountId: Number(form.get('masterAccountId')),
              targetId: Number(form.get('targetId')),
            })
          }}
        >
          <label className="field">
            <span>masterAccountId</span>
            <input min={1} name="masterAccountId" required type="number" />
          </label>
          <label className="field">
            <span>{targetField}</span>
            <input min={1} name="targetId" required type="number" />
          </label>
          <button className="button button--primary" type="submit">
            查询链路
          </button>
        </form>
      </Surface>

      {traceQuery.isPending ? <LoadingState label="正在查询链路..." /> : null}
      {traceQuery.error ? <ErrorState message={traceQuery.error.message} /> : null}

      {traceQuery.data ? (
        <>
          <Surface title="Trace Summary">
            <KeyValueGrid
              items={[
                { label: 'masterAccountId', value: traceQuery.data.masterAccountId },
                { label: 'masterOrderId', value: traceQuery.data.masterOrderId ?? '—' },
                { label: 'masterPositionId', value: traceQuery.data.masterPositionId ?? '—' },
                { label: 'commands', value: traceQuery.data.commands.length },
                { label: 'dispatches', value: traceQuery.data.dispatches.length },
              ]}
            />
          </Surface>

          <Surface title="Commands">
            {traceQuery.data.commands.length === 0 ? (
              <EmptyState title="没有 command" message="这条链路还没有命中复制引擎命令。" />
            ) : (
              <DataTable
                headers={['ID', 'Event', 'Type', 'Status', 'Volume', 'Created']}
                rows={traceQuery.data.commands.map((command) => [
                  command.id,
                  command.masterEventId,
                  command.commandType,
                  <StatusPill value={command.status} />,
                  command.requestedVolume ?? '—',
                  formatDateTime(command.createdAt),
                ])}
              />
            )}
          </Surface>

          <Surface title="Dispatches">
            {traceQuery.data.dispatches.length === 0 ? (
              <EmptyState title="没有 dispatch" message="这条链路还没有 follower 下发记录。" />
            ) : (
              <DataTable
                headers={['ID', 'Execution', 'Status', 'Message', 'Acked']}
                rows={traceQuery.data.dispatches.map((dispatch) => [
                  dispatch.id,
                  dispatch.executionCommandId,
                  <StatusPill value={dispatch.status} />,
                  dispatch.statusMessage || '—',
                  formatDateTime(dispatch.ackedAt),
                ])}
              />
            )}
          </Surface>
        </>
      ) : null}
    </div>
  )
}
