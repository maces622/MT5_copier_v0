import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { deleteCopyRelation, getMyCopyRelations, updateCopyRelation } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { queryClient } from '../lib/query'
import type { PlatformCopyRelationViewResponse } from '../lib/types'
import { EmptyState, ErrorState, LoadingState, MetricCard, PageHeader, StatusPill, Surface } from '../components/common'

function RelationActions({
  item,
  savePending,
  unlinkPending,
  onSave,
  onUnlink,
}: {
  item: PlatformCopyRelationViewResponse
  savePending: boolean
  unlinkPending: boolean
  onSave: (payload: { relationId: number; copyMode: string; status: string; priority: number }) => void
  onUnlink: (relationId: number) => void
}) {
  const { relation, masterAccount, followerAccount, currentUserOwnsMaster, currentUserOwnsFollower } = item
  const editable = currentUserOwnsFollower

  if (!editable) {
    return (
      <div className="inline-form">
        <span>#{relation.id}</span>
        <span>
          M#{masterAccount.id} {masterAccount.serverName}:{masterAccount.mt5Login}
          <br />
          F#{followerAccount.id} {followerAccount.serverName}:{followerAccount.mt5Login}
        </span>
        <span>{relation.copyMode}</span>
        <span>
          <StatusPill value={relation.status} />
        </span>
        <span>priority {relation.priority}</span>
        <div>
          {currentUserOwnsFollower ? (
            <button
              className="button button--ghost"
              disabled={unlinkPending}
              onClick={() => onUnlink(relation.id)}
              type="button"
            >
              {unlinkPending ? '解绑中...' : '解绑'}
            </button>
          ) : (
            <span className="inline-hint">分享出去的关系由 follower 侧决定是否解绑</span>
          )}
          <div className="inline-hint">
            {currentUserOwnsMaster ? (
              <Link className="text-link" params={{ accountId: String(masterAccount.id) }} to="/app/accounts/$accountId">
                主账户详情
              </Link>
            ) : (
              <span>主账户来自分享绑定</span>
            )}
            <br />
            {currentUserOwnsFollower ? (
              <Link className="text-link" params={{ accountId: String(followerAccount.id) }} to="/app/accounts/$accountId">
                从账户详情
              </Link>
            ) : null}
          </div>
        </div>
      </div>
    )
  }

  return (
    <form
      className="inline-form"
      onSubmit={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        onSave({
          relationId: relation.id,
          copyMode: String(form.get('copyMode') || relation.copyMode),
          status: String(form.get('status') || relation.status),
          priority: Number(form.get('priority') || relation.priority),
        })
      }}
    >
      <span>#{relation.id}</span>
      <span>
        M#{masterAccount.id} {masterAccount.serverName}:{masterAccount.mt5Login}
        <br />
        F#{followerAccount.id} {followerAccount.serverName}:{followerAccount.mt5Login}
      </span>
      <select defaultValue={relation.copyMode} name="copyMode">
        <option value="BALANCE_RATIO">BALANCE_RATIO</option>
        <option value="EQUITY_RATIO">EQUITY_RATIO</option>
        <option value="FIXED_LOT">FIXED_LOT</option>
        <option value="FOLLOW_MASTER">FOLLOW_MASTER</option>
      </select>
      <select defaultValue={relation.status} name="status">
        <option value="ACTIVE">ACTIVE</option>
        <option value="PAUSED">PAUSED</option>
      </select>
      <input defaultValue={relation.priority} min={1} name="priority" type="number" />
      <div>
        <button className="button button--ghost" disabled={savePending} type="submit">
          保存
        </button>
        <div className="inline-hint">
          {currentUserOwnsMaster ? (
            <Link className="text-link" params={{ accountId: String(masterAccount.id) }} to="/app/accounts/$accountId">
              主账户详情
            </Link>
          ) : (
            <span>主账户来自分享绑定</span>
          )}
          <br />
          {currentUserOwnsFollower ? (
            <Link className="text-link" params={{ accountId: String(followerAccount.id) }} to="/app/accounts/$accountId">
              从账户详情
            </Link>
          ) : null}
        </div>
      </div>
    </form>
  )
}

export function RelationsPage() {
  const [keyword, setKeyword] = useState('')
  const [scope, setScope] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const relationsQuery = useQuery({
    queryKey: ['relations', 'mine'],
    queryFn: getMyCopyRelations,
  })
  const updateMutation = useMutation({
    mutationFn: (payload: { relationId: number; copyMode: string; status: string; priority: number }) =>
      updateCopyRelation(payload.relationId, {
        copyMode: payload.copyMode,
        status: payload.status,
        priority: payload.priority,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['relations', 'mine'] })
      await queryClient.invalidateQueries({ queryKey: ['accounts', 'mine'] })
      await queryClient.invalidateQueries({ queryKey: ['monitor', 'overview'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: deleteCopyRelation,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['relations', 'mine'] })
      await queryClient.invalidateQueries({ queryKey: ['accounts', 'mine'] })
      await queryClient.invalidateQueries({ queryKey: ['monitor', 'overview'] })
    },
  })

  const relations = relationsQuery.data ?? []
  const filteredRelations = useMemo(() => {
    return relations
      .filter((item) => {
        if (scope === 'MASTER') {
          return item.currentUserOwnsMaster
        }
        if (scope === 'FOLLOWER') {
          return item.currentUserOwnsFollower
        }
        if (scope === 'SHARED') {
          return !item.currentUserOwnsMaster && item.currentUserOwnsFollower
        }
        return true
      })
      .filter((item) => statusFilter === 'ALL' || item.relation.status === statusFilter)
      .filter((item) => {
        if (!keyword.trim()) {
          return true
        }
        const normalized = keyword.trim().toLowerCase()
        return [
          item.relation.copyMode,
          item.relation.status,
          item.masterAccount.serverName,
          item.followerAccount.serverName,
          String(item.masterAccount.mt5Login),
          String(item.followerAccount.mt5Login),
          String(item.relation.id),
        ]
          .join(' ')
          .toLowerCase()
          .includes(normalized)
      })
  }, [keyword, relations, scope, statusFilter])

  if (relationsQuery.isPending) {
    return <LoadingState label="加载关系列表..." />
  }

  if (relationsQuery.error) {
    return <ErrorState message={relationsQuery.error.message} />
  }

  const totalCount = relations.length
  const masterCount = relations.filter((item) => item.currentUserOwnsMaster).length
  const followerCount = relations.filter((item) => item.currentUserOwnsFollower).length
  const pausedCount = relations.filter((item) => item.relation.status === 'PAUSED').length

  return (
    <div className="page-stack">
      <PageHeader
        title="关系管理"
        description="集中查看我名下的主从关系和分享绑定关系；共享关系由 follower 侧主动解绑。"
      />

      <div className="metrics-grid">
        <MetricCard label="总关系数" value={totalCount} />
        <MetricCard label="我作为 Master" value={masterCount} tone="good" />
        <MetricCard label="我作为 Follower" value={followerCount} tone="neutral" />
        <MetricCard label="待启用 / 暂停" value={pausedCount} tone="warn" />
      </div>

      <Surface title="筛选" description="按角色范围、状态和关键词过滤当前可见关系。">
        <div className="toolbar">
          <label className="field">
            <span>搜索</span>
            <input
              placeholder="relationId / server / login / copyMode"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </label>
          <label className="field">
            <span>角色范围</span>
            <select value={scope} onChange={(event) => setScope(event.target.value)}>
              <option value="ALL">ALL</option>
              <option value="MASTER">我作为 Master</option>
              <option value="FOLLOWER">我作为 Follower</option>
              <option value="SHARED">分享绑定</option>
            </select>
          </label>
          <label className="field">
            <span>状态</span>
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="ALL">ALL</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="PAUSED">PAUSED</option>
            </select>
          </label>
        </div>
      </Surface>

      <Surface title="关系列表" description="同用户关系可直接调整；分享绑定关系由 follower 主动解绑。">
        {filteredRelations.length === 0 ? (
          <EmptyState title="没有匹配的关系" message="可以先通过账户详情页创建关系，或者用 share_id + share_code 绑定主账户。" />
        ) : (
          <div className="stack-list">
            {filteredRelations.map((item) => (
              <div key={item.relation.id} className="stack-list__item">
                <div>
                  <strong>
                    #{item.relation.id} <StatusPill value={item.relation.status} />
                  </strong>
                  <p>
                    {item.relation.copyMode} · priority {item.relation.priority} · 更新于 {formatDateTime(item.relation.updatedAt)}
                  </p>
                </div>
                <div className="badge">
                  {item.currentUserOwnsMaster && item.currentUserOwnsFollower
                    ? '同用户关系'
                    : item.currentUserOwnsMaster
                      ? '我分享出去'
                      : '我正在跟随'}
                </div>
                <RelationActions
                  item={item}
                  onSave={(payload) => updateMutation.mutate(payload)}
                  onUnlink={(relationId) => deleteMutation.mutate(relationId)}
                  savePending={updateMutation.isPending}
                  unlinkPending={deleteMutation.isPending}
                />
              </div>
            ))}
          </div>
        )}
        {updateMutation.error ? <div className="inline-error">{updateMutation.error.message}</div> : null}
        {deleteMutation.error ? <div className="inline-error">{deleteMutation.error.message}</div> : null}
      </Surface>
    </div>
  )
}
