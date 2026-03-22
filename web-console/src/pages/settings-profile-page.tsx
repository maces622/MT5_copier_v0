import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { updateProfile } from '../lib/api'
import { queryClient } from '../lib/query'
import { useCurrentUser } from '../hooks/use-session'
import { ErrorState, LoadingState, PageHeader, Surface } from '../components/common'

export function SettingsProfilePage() {
  const currentUser = useCurrentUser()
  const [successMessage, setSuccessMessage] = useState('')
  const updateMutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: async (user) => {
      setSuccessMessage('个人资料已更新。')
      queryClient.setQueryData(['auth', 'me'], user)
      await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })

  if (currentUser.isPending) {
    return <LoadingState label="加载当前用户资料..." />
  }

  if (currentUser.error || !currentUser.data) {
    return <ErrorState message={currentUser.error?.message ?? '无法加载当前用户资料'} />
  }

  const user = currentUser.data

  return (
    <div className="page-stack">
      <PageHeader
        title="个人设置"
        description="管理当前登录平台用户的显示名称和登录密码。"
      />

      <div className="two-column">
        <Surface title="当前身份" description="这里展示当前会话绑定的平台身份，不允许直接修改 platform_id 和 share_id。">
          <div className="kv-grid">
            <div className="kv-grid__item">
              <span>platform_id</span>
              <strong>{user.platformId}</strong>
            </div>
            <div className="kv-grid__item">
              <span>share_id</span>
              <strong>{user.shareId}</strong>
            </div>
            <div className="kv-grid__item">
              <span>username</span>
              <strong>{user.username}</strong>
            </div>
            <div className="kv-grid__item">
              <span>role / status</span>
              <strong>
                {user.role} / {user.status}
              </strong>
            </div>
          </div>
        </Surface>

        <Surface title="更新资料" description="displayName 可单独修改；修改密码时必须同时提供当前密码。">
          <form
            className="form-grid"
            onSubmit={(event) => {
              event.preventDefault()
              setSuccessMessage('')
              const form = new FormData(event.currentTarget)
              const displayName = String(form.get('displayName') || '').trim()
              const currentPassword = String(form.get('currentPassword') || '').trim()
              const newPassword = String(form.get('newPassword') || '').trim()
              updateMutation.mutate({
                displayName: displayName || undefined,
                currentPassword: currentPassword || undefined,
                newPassword: newPassword || undefined,
              })
            }}
          >
            <label className="field">
              <span>display name</span>
              <input defaultValue={user.displayName ?? ''} name="displayName" placeholder="运营台显示名称" />
            </label>
            <label className="field">
              <span>current password</span>
              <input name="currentPassword" placeholder="修改密码时必填" type="password" />
            </label>
            <label className="field">
              <span>new password</span>
              <input minLength={6} name="newPassword" placeholder="至少 6 位" type="password" />
            </label>

            {successMessage ? <div className="inline-success">{successMessage}</div> : null}
            {updateMutation.error ? <div className="inline-error">{updateMutation.error.message}</div> : null}

            <button className="button button--primary" disabled={updateMutation.isPending} type="submit">
              {updateMutation.isPending ? '保存中...' : '保存个人设置'}
            </button>
          </form>
        </Surface>
      </div>
    </div>
  )
}
