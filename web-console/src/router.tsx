import { Outlet, createRootRoute, createRoute, createRouter, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'
import { AppShell } from './components/app-shell'
import { ErrorState, LoadingState } from './components/common'
import { useCurrentUser } from './hooks/use-session'
import { isUnauthorized } from './lib/http'
import { queryClient } from './lib/query'
import { AccountDetailPage } from './pages/account-detail-page'
import { AccountsPage } from './pages/accounts-page'
import { LoginPage, RegisterPage } from './pages/auth-page'
import { FollowBindPage } from './pages/follow-bind-page'
import { MonitorAccountDetailPage } from './pages/monitor-account-detail-page'
import { MonitorAccountsPage } from './pages/monitor-accounts-page'
import { NotFoundPage } from './pages/not-found-page'
import { OverviewPage } from './pages/overview-page'
import { RelationsPage } from './pages/relations-page'
import { SharePage } from './pages/share-page'
import { SettingsProfilePage } from './pages/settings-profile-page'
import { TracePage } from './pages/trace-page'

function AuthOnlyLayout() {
  const currentUser = useCurrentUser()
  const navigate = useNavigate()

  useEffect(() => {
    if (currentUser.data) {
      void navigate({ to: '/app/overview', replace: true })
    }
  }, [currentUser.data, navigate])

  return <Outlet />
}

function HomeRedirect() {
  const navigate = useNavigate()

  useEffect(() => {
    void navigate({ to: '/login', replace: true })
  }, [navigate])

  return <LoadingState label="跳转到登录页..." />
}

function ProtectedLayout() {
  const currentUser = useCurrentUser()
  const navigate = useNavigate()

  useEffect(() => {
    if (isUnauthorized(currentUser.error)) {
      void navigate({ to: '/login', replace: true })
    }
  }, [currentUser.error, navigate])

  if (currentUser.isPending) {
    return <LoadingState label="加载平台用户..." />
  }

  if (currentUser.error) {
    if (isUnauthorized(currentUser.error)) {
      return null
    }
    return <ErrorState message={currentUser.error.message} />
  }

  if (!currentUser.data) {
    return null
  }

  return <AppShell user={currentUser.data} />
}

const rootRoute = createRootRoute({
  component: Outlet,
  notFoundComponent: NotFoundPage,
})

const authRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'auth',
  component: AuthOnlyLayout,
})

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: HomeRedirect,
})

const appRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/app',
  component: ProtectedLayout,
})

const loginRoute = createRoute({
  getParentRoute: () => authRoute,
  path: '/login',
  component: LoginPage,
})

const registerRoute = createRoute({
  getParentRoute: () => authRoute,
  path: '/register',
  component: RegisterPage,
})

const overviewRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/overview',
  component: OverviewPage,
})

const accountsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/accounts',
  component: AccountsPage,
})

const accountDetailRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/accounts/$accountId',
  component: AccountDetailPage,
})

const shareRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/share',
  component: SharePage,
})

const followBindRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/follow/bind',
  component: FollowBindPage,
})

const relationsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/relations',
  component: RelationsPage,
})

const monitorAccountsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/monitor/accounts',
  component: MonitorAccountsPage,
})

const monitorAccountDetailRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/monitor/accounts/$accountId',
  component: MonitorAccountDetailPage,
})

const traceRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/traces/$traceType',
  component: TracePage,
})

const settingsProfileRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/settings/profile',
  component: SettingsProfilePage,
})

const routeTree = rootRoute.addChildren([
  homeRoute,
  authRoute.addChildren([loginRoute, registerRoute]),
  appRoute.addChildren([
    overviewRoute,
    accountsRoute,
    accountDetailRoute,
    shareRoute,
    followBindRoute,
    relationsRoute,
    monitorAccountsRoute,
    monitorAccountDetailRoute,
    traceRoute,
    settingsProfileRoute,
  ]),
])

export const router = createRouter({
  routeTree,
  context: {
    queryClient,
  },
  defaultPreload: 'intent',
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
