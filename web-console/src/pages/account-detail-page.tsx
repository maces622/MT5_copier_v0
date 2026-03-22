import { useMutation, useQuery } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import {
  createCopyRelation,
  deleteCopyRelation,
  getAccountDetail,
  getMyAccounts,
  saveRiskRule,
  saveShareConfig,
  saveSymbolMapping,
  updateCopyRelation,
} from '../lib/api'
import { formatBoolean, formatDateTime } from '../lib/format'
import { queryClient } from '../lib/query'
import { DataTable, EmptyState, ErrorState, KeyValueGrid, LoadingState, PageHeader, StatusPill, Surface } from '../components/common'
import type { CopyRelationResponse } from '../lib/types'

function readOptionalNumber(form: FormData, key: string) {
  const raw = String(form.get(key) || '').trim()
  return raw ? Number(raw) : undefined
}

function RelationRow({
  relation,
  editable,
  canUnlink,
  onSave,
  onUnlink,
  savePending,
  unlinkPending,
}: {
  relation: CopyRelationResponse
  editable: boolean
  canUnlink: boolean
  onSave: (payload: { relationId: number; copyMode: string; status: string; priority: number }) => void
  onUnlink: (relationId: number) => void
  savePending: boolean
  unlinkPending: boolean
}) {
  if (!editable) {
    return (
      <div className="inline-form">
        <span>#{relation.id}</span>
        <span>
          {relation.masterAccountId} → {relation.followerAccountId}
        </span>
        <span>{relation.copyMode}</span>
        <span>
          <StatusPill value={relation.status} />
        </span>
        <span>priority {relation.priority}</span>
        <div>
          {canUnlink ? (
            <button className="button button--ghost" disabled={unlinkPending} onClick={() => onUnlink(relation.id)} type="button">
              {unlinkPending ? '解绑中...' : '解绑'}
            </button>
          ) : (
            <span className="inline-hint">该关系不是当前用户可编辑关系</span>
          )}
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
        {relation.masterAccountId} → {relation.followerAccountId}
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
      <button className="button button--ghost" disabled={savePending} type="submit">
        保存
      </button>
    </form>
  )
}

export function AccountDetailPage() {
  const { accountId } = useParams({ from: '/app/accounts/$accountId' })
  const detailQuery = useQuery({
    queryKey: ['accounts', 'detail', accountId],
    queryFn: () => getAccountDetail(accountId),
  })
  const myAccountsQuery = useQuery({
    queryKey: ['accounts', 'mine'],
    queryFn: getMyAccounts,
  })

  const saveShareMutation = useMutation({
    mutationFn: (payload: { shareEnabled?: boolean; shareCode?: string; shareNote?: string }) =>
      saveShareConfig(Number(accountId), payload),
    onSuccess: async () => {
      await invalidateAccountView(accountId)
      await queryClient.invalidateQueries({ queryKey: ['share', 'profile'] })
    },
  })
  const saveRiskMutation = useMutation({
    mutationFn: (payload: Parameters<typeof saveRiskRule>[1]) => saveRiskRule(Number(accountId), payload),
    onSuccess: async () => {
      await invalidateAccountView(accountId)
    },
  })
  const createRelationMutation = useMutation({
    mutationFn: createCopyRelation,
    onSuccess: async () => {
      await invalidateAccountView(accountId)
    },
  })
  const updateRelationMutation = useMutation({
    mutationFn: (payload: { relationId: number; copyMode: string; status: string; priority: number }) =>
      updateCopyRelation(payload.relationId, {
        copyMode: payload.copyMode,
        status: payload.status,
        priority: payload.priority,
      }),
    onSuccess: async () => {
      await invalidateAccountView(accountId)
    },
  })
  const deleteRelationMutation = useMutation({
    mutationFn: deleteCopyRelation,
    onSuccess: async () => {
      await invalidateAccountView(accountId)
      await queryClient.invalidateQueries({ queryKey: ['relations', 'mine'] })
    },
  })
  const saveMappingMutation = useMutation({
    mutationFn: (payload: Parameters<typeof saveSymbolMapping>[1]) => saveSymbolMapping(Number(accountId), payload),
    onSuccess: async () => {
      await invalidateAccountView(accountId)
    },
  })

  if (detailQuery.isPending || myAccountsQuery.isPending) {
    return <LoadingState />
  }

  if (detailQuery.error || myAccountsQuery.error) {
    const error = detailQuery.error || myAccountsQuery.error
    return <ErrorState message={error instanceof Error ? error.message : '请求失败'} />
  }

  const detail = detailQuery.data!
  const { account, riskRule, relations, symbolMappings, shareConfig } = detail
  const canShare = account.accountRole === 'MASTER' || account.accountRole === 'BOTH'
  const myAccounts = myAccountsQuery.data!
  const ownedAccountIds = new Set(myAccounts.map((candidate) => candidate.id))
  const masterOptions = myAccounts.filter((candidate) => candidate.id !== account.id && (candidate.accountRole === 'MASTER' || candidate.accountRole === 'BOTH'))
  const followerOptions = myAccounts.filter((candidate) => candidate.id !== account.id && (candidate.accountRole === 'FOLLOWER' || candidate.accountRole === 'BOTH'))
  const relationRows = [...relations.masterRelations, ...relations.followerRelations]

  return (
    <div className="page-stack">
      <PageHeader
        title={`账户 #${account.id}`}
        description={`${account.brokerName} / ${account.serverName} / ${account.mt5Login}`}
      />

      <div className="two-column">
        <Surface title="基础信息">
          <KeyValueGrid
            items={[
              { label: '角色', value: account.accountRole },
              { label: '状态', value: <StatusPill value={account.status} /> },
              { label: '用户 ID', value: account.userId },
              { label: 'Credential', value: account.credentialConfigured ? '已配置' : '未配置' },
              { label: '创建时间', value: formatDateTime(account.createdAt) },
              { label: '更新时间', value: formatDateTime(account.updatedAt) },
            ]}
          />
        </Surface>

        <Surface title="风控规则" description="这里直接走 risk-rules upsert。">
          <form
            className="form-grid form-grid--three"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              saveRiskMutation.mutate({
                fixedLot: readOptionalNumber(form, 'fixedLot'),
                balanceRatio: readOptionalNumber(form, 'balanceRatio'),
                maxLot: readOptionalNumber(form, 'maxLot'),
                maxSlippagePoints: readOptionalNumber(form, 'maxSlippagePoints'),
                maxSlippagePips: readOptionalNumber(form, 'maxSlippagePips'),
                allowedSymbols: String(form.get('allowedSymbols') || '').trim() || undefined,
                blockedSymbols: String(form.get('blockedSymbols') || '').trim() || undefined,
                followTpSl: form.get('followTpSl') === 'on',
                reverseFollow: form.get('reverseFollow') === 'on',
              })
            }}
          >
            <label className="field">
              <span>fixedLot</span>
              <input defaultValue={riskRule?.fixedLot ?? ''} name="fixedLot" step="0.01" />
            </label>
            <label className="field">
              <span>balanceRatio</span>
              <input defaultValue={riskRule?.balanceRatio ?? ''} name="balanceRatio" step="0.01" />
            </label>
            <label className="field">
              <span>maxLot</span>
              <input defaultValue={riskRule?.maxLot ?? ''} name="maxLot" step="0.01" />
            </label>
            <label className="field">
              <span>maxSlippagePoints</span>
              <input defaultValue={riskRule?.maxSlippagePoints ?? ''} name="maxSlippagePoints" step="1" />
            </label>
            <label className="field">
              <span>maxSlippagePips</span>
              <input defaultValue={riskRule?.maxSlippagePips ?? ''} name="maxSlippagePips" step="0.1" />
            </label>
            <label className="field">
              <span>allowedSymbols</span>
              <input defaultValue={riskRule?.allowedSymbols ?? ''} name="allowedSymbols" placeholder="XAUUSD,BTCUSD" />
            </label>
            <label className="field">
              <span>blockedSymbols</span>
              <input defaultValue={riskRule?.blockedSymbols ?? ''} name="blockedSymbols" placeholder="US30,ETHUSD" />
            </label>
            <label className="field field--checkbox">
              <input defaultChecked={riskRule?.followTpSl ?? true} name="followTpSl" type="checkbox" />
              <span>followTpSl</span>
            </label>
            <label className="field field--checkbox">
              <input defaultChecked={riskRule?.reverseFollow ?? false} name="reverseFollow" type="checkbox" />
              <span>reverseFollow</span>
            </label>
            {saveRiskMutation.error ? <div className="inline-error">{saveRiskMutation.error.message}</div> : null}
            {riskRule ? (
              <div className="inline-hint">
                上次更新时间 {formatDateTime(riskRule.updatedAt)} · followTpSl {formatBoolean(riskRule.followTpSl)}
              </div>
            ) : (
              <div className="inline-hint">当前还没有风控记录，保存后会自动创建。</div>
            )}
            <button className="button button--primary" disabled={saveRiskMutation.isPending} type="submit">
              {saveRiskMutation.isPending ? '保存中...' : '保存风控规则'}
            </button>
          </form>
        </Surface>
      </div>

      <div className="two-column">
        <Surface title="关系创建" description="这里只处理当前用户内部的主从关系；跨用户请走 share 绑定页。">
          <form
            className="form-grid"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              createRelationMutation.mutate({
                masterAccountId: Number(form.get('masterAccountId')),
                followerAccountId: Number(form.get('followerAccountId')),
                copyMode: String(form.get('copyMode') || 'BALANCE_RATIO'),
                status: String(form.get('status') || 'ACTIVE'),
                priority: Number(form.get('priority') || 100),
              })
            }}
          >
            <label className="field">
              <span>masterAccountId</span>
              <select
                defaultValue={account.accountRole === 'MASTER' ? String(account.id) : String(masterOptions[0]?.id ?? '')}
                name="masterAccountId"
              >
                {account.accountRole !== 'FOLLOWER' ? (
                  <option value={account.id}>
                    #{account.id} · current
                  </option>
                ) : null}
                {masterOptions.map((option) => (
                  <option key={option.id} value={option.id}>
                    #{option.id} · {option.serverName}:{option.mt5Login}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>followerAccountId</span>
              <select
                defaultValue={account.accountRole === 'FOLLOWER' ? String(account.id) : String(followerOptions[0]?.id ?? '')}
                name="followerAccountId"
              >
                {account.accountRole !== 'MASTER' ? (
                  <option value={account.id}>
                    #{account.id} · current
                  </option>
                ) : null}
                {followerOptions.map((option) => (
                  <option key={option.id} value={option.id}>
                    #{option.id} · {option.serverName}:{option.mt5Login}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>copyMode</span>
              <select defaultValue="BALANCE_RATIO" name="copyMode">
                <option value="BALANCE_RATIO">BALANCE_RATIO</option>
                <option value="EQUITY_RATIO">EQUITY_RATIO</option>
                <option value="FIXED_LOT">FIXED_LOT</option>
                <option value="FOLLOW_MASTER">FOLLOW_MASTER</option>
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
              <span>priority</span>
              <input defaultValue={100} min={1} name="priority" type="number" />
            </label>
            {createRelationMutation.error ? <div className="inline-error">{createRelationMutation.error.message}</div> : null}
            <button className="button button--primary" disabled={createRelationMutation.isPending} type="submit">
              {createRelationMutation.isPending ? '创建中...' : '创建关系'}
            </button>
          </form>
        </Surface>

        <Surface title="现有关系" description="同用户关系可编辑；分享绑定关系由 follower 侧主动解绑。">
          {relationRows.length === 0 ? (
            <EmptyState title="没有关系" message="当前账户还没有作为 master 或 follower 参与任何关系。" />
          ) : (
            <div className="stack-list">
              {relationRows.map((relation) => (
                <RelationRow
                  canUnlink={ownedAccountIds.has(relation.followerAccountId)}
                  editable={ownedAccountIds.has(relation.followerAccountId)}
                  key={relation.id}
                  relation={relation}
                  onSave={(payload) => updateRelationMutation.mutate(payload)}
                  onUnlink={(relationId) => deleteRelationMutation.mutate(relationId)}
                  savePending={updateRelationMutation.isPending}
                  unlinkPending={deleteRelationMutation.isPending}
                />
              ))}
              {updateRelationMutation.error ? <div className="inline-error">{updateRelationMutation.error.message}</div> : null}
              {deleteRelationMutation.error ? <div className="inline-error">{deleteRelationMutation.error.message}</div> : null}
            </div>
          )}
        </Surface>
      </div>

      <div className="two-column">
        <Surface title="品种映射">
          {symbolMappings.length === 0 ? (
            <EmptyState title="没有品种映射" message="同符号交易时可以保持为空。" />
          ) : (
            <DataTable
              headers={['Master Symbol', 'Follower Symbol', '更新时间']}
              rows={symbolMappings.map((mapping) => [
                mapping.masterSymbol,
                mapping.followerSymbol,
                formatDateTime(mapping.updatedAt),
              ])}
            />
          )}
        </Surface>

        <Surface title="新增 / 覆盖映射" description="后端按 followerAccountId + masterSymbol upsert。">
          <form
            className="form-grid"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              saveMappingMutation.mutate({
                masterSymbol: String(form.get('masterSymbol') || '').trim(),
                followerSymbol: String(form.get('followerSymbol') || '').trim(),
              })
            }}
          >
            <label className="field">
              <span>masterSymbol</span>
              <input name="masterSymbol" placeholder="XAUUSD" required />
            </label>
            <label className="field">
              <span>followerSymbol</span>
              <input name="followerSymbol" placeholder="XAUUSDm" required />
            </label>
            {saveMappingMutation.error ? <div className="inline-error">{saveMappingMutation.error.message}</div> : null}
            <button className="button button--primary" disabled={saveMappingMutation.isPending} type="submit">
              {saveMappingMutation.isPending ? '保存中...' : '保存品种映射'}
            </button>
          </form>
        </Surface>
      </div>

      <Surface title="分享设置" description="share_code 是账户级秘密口令；share_id 是用户级公开标识。">
        {!canShare ? (
          <EmptyState title="当前账户不是 Master" message="只有 MASTER 或 BOTH 角色账户可以开启分享。" />
        ) : (
          <form
            className="form-grid form-grid--three"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              saveShareMutation.mutate({
                shareEnabled: form.get('shareEnabled') === 'on',
                shareCode: String(form.get('shareCode') || '').trim() || undefined,
                shareNote: String(form.get('shareNote') || '').trim() || undefined,
              })
            }}
          >
            <label className="field field--checkbox">
              <input defaultChecked={shareConfig?.shareEnabled ?? true} name="shareEnabled" type="checkbox" />
              <span>允许新 follower 通过 share 绑定这个 master</span>
            </label>
            <label className="field">
              <span>share_code</span>
              <input name="shareCode" placeholder={shareConfig?.shareCodeConfigured ? '重新输入会覆盖旧码' : '设置 share_code'} />
            </label>
            <label className="field">
              <span>备注</span>
              <input defaultValue={shareConfig?.shareNote ?? ''} name="shareNote" placeholder="给这个分享入口写一句备注" />
            </label>
            <div className="inline-hint">
              {shareConfig ? `上次轮换 ${formatDateTime(shareConfig.rotatedAt)}` : '还没有创建分享配置'}
            </div>
            {saveShareMutation.error ? <div className="inline-error">{saveShareMutation.error.message}</div> : null}
            <button className="button button--primary" disabled={saveShareMutation.isPending} type="submit">
              {saveShareMutation.isPending ? '保存中...' : '保存分享配置'}
            </button>
          </form>
        )}
      </Surface>
    </div>
  )
}

async function invalidateAccountView(accountId: string) {
  await queryClient.invalidateQueries({ queryKey: ['accounts', 'mine'] })
  await queryClient.invalidateQueries({ queryKey: ['accounts', 'detail', accountId] })
  await queryClient.invalidateQueries({ queryKey: ['share', 'profile'] })
  await queryClient.invalidateQueries({ queryKey: ['monitor', 'overview'] })
}
