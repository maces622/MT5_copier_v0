import { apiRequest } from './http'
import type {
  AccountRelationsResponse,
  AuthenticatedPlatformUserResponse,
  CopyRelationResponse,
  ExecutionTraceResponse,
  ExecutionCommandResponse,
  FollowerDispatchOutboxResponse,
  MasterShareConfigResponse,
  MonitorAccountDetailResponse,
  MonitorDashboardResponse,
  Mt5AccountMonitorOverviewResponse,
  Mt5AccountResponse,
  PlatformAccountDetailResponse,
  PlatformCopyRelationViewResponse,
  RiskRuleResponse,
  ShareProfileResponse,
  SymbolMappingResponse,
} from './types'

export interface LoginPayload {
  login: string
  password: string
}

export interface RegisterPayload {
  username: string
  password: string
  displayName?: string
}

export interface UpdateProfilePayload {
  displayName?: string
  currentPassword?: string
  newPassword?: string
}

export interface SaveShareConfigPayload {
  shareEnabled?: boolean
  shareCode?: string
  shareNote?: string
  expiresAt?: string | null
}

export interface JoinBySharePayload {
  followerAccountId: number
  shareId: string
  shareCode: string
  copyMode: string
  priority: number
}

export interface BindAccountPayload {
  brokerName: string
  serverName: string
  mt5Login: number
  credential?: string
  accountRole: string
  status: string
}

export interface SaveRiskRulePayload {
  maxLot?: number
  fixedLot?: number
  balanceRatio?: number
  maxSlippagePoints?: number
  maxSlippagePips?: number
  maxSlippagePrice?: number
  maxDailyLoss?: number
  maxDrawdownPct?: number
  allowedSymbols?: string
  blockedSymbols?: string
  followTpSl?: boolean
  reverseFollow?: boolean
}

export interface CreateRelationPayload {
  masterAccountId: number
  followerAccountId: number
  copyMode: string
  status: string
  priority: number
}

export interface UpdateRelationPayload {
  copyMode?: string
  status?: string
  priority?: number
}

export interface SaveSymbolMappingPayload {
  masterSymbol: string
  followerSymbol: string
}

export function getCurrentUser() {
  return apiRequest<AuthenticatedPlatformUserResponse>('/api/auth/me')
}

export function login(payload: LoginPayload) {
  return apiRequest<AuthenticatedPlatformUserResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function register(payload: RegisterPayload) {
  return apiRequest<AuthenticatedPlatformUserResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function logout() {
  return apiRequest<void>('/api/auth/logout', {
    method: 'POST',
  })
}

export function updateProfile(payload: UpdateProfilePayload) {
  return apiRequest<AuthenticatedPlatformUserResponse>('/api/auth/me', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function getMyAccounts() {
  return apiRequest<Mt5AccountResponse[]>('/api/me/accounts')
}

export function getMyCopyRelations() {
  return apiRequest<PlatformCopyRelationViewResponse[]>('/api/me/copy-relations')
}

export function bindAccount(payload: BindAccountPayload) {
  return apiRequest<Mt5AccountResponse>('/api/me/accounts', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getAccount(accountId: string) {
  return apiRequest<Mt5AccountResponse>(`/api/accounts/${accountId}`)
}

export function getAccountDetail(accountId: string) {
  return apiRequest<PlatformAccountDetailResponse>(`/api/accounts/${accountId}/detail`)
}

export function getAccountRiskRule(accountId: string) {
  return apiRequest<RiskRuleResponse>(`/api/accounts/${accountId}/risk-rule`)
}

export function getAccountRelations(accountId: string) {
  return apiRequest<AccountRelationsResponse>(`/api/accounts/${accountId}/relations`)
}

export function getAccountSymbolMappings(accountId: string) {
  return apiRequest<SymbolMappingResponse[]>(`/api/accounts/${accountId}/symbol-mappings`)
}

export function getShareProfile() {
  return apiRequest<ShareProfileResponse>('/api/me/share-profile')
}

export function saveShareConfig(accountId: number, payload: SaveShareConfigPayload) {
  return apiRequest<MasterShareConfigResponse>(`/api/accounts/${accountId}/share-config`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function joinByShare(payload: JoinBySharePayload) {
  return apiRequest<CopyRelationResponse>('/api/copy-relations/join-by-share', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function saveRiskRule(accountId: number, payload: SaveRiskRulePayload) {
  return apiRequest<RiskRuleResponse>(`/api/me/accounts/${accountId}/risk-rule`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createCopyRelation(payload: CreateRelationPayload) {
  return apiRequest<CopyRelationResponse>('/api/me/copy-relations', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateCopyRelation(relationId: number, payload: UpdateRelationPayload) {
  return apiRequest<CopyRelationResponse>(`/api/me/copy-relations/${relationId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteCopyRelation(relationId: number) {
  return apiRequest<void>(`/api/me/copy-relations/${relationId}`, {
    method: 'DELETE',
  })
}

export function saveSymbolMapping(accountId: number, payload: SaveSymbolMappingPayload) {
  return apiRequest<SymbolMappingResponse>(`/api/me/accounts/${accountId}/symbol-mappings`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getMonitorDashboard() {
  return apiRequest<MonitorDashboardResponse>('/api/monitor/dashboard')
}

export function getMonitorOverview() {
  return apiRequest<Mt5AccountMonitorOverviewResponse[]>('/api/monitor/accounts/overview')
}

export function getMonitorAccountDetail(accountId: string) {
  return apiRequest<MonitorAccountDetailResponse>(`/api/monitor/accounts/${accountId}/detail`)
}

export function getMonitorCommands(accountId: string) {
  return apiRequest<ExecutionCommandResponse[]>(`/api/monitor/accounts/${accountId}/commands`)
}

export function getMonitorDispatches(accountId: string) {
  return apiRequest<FollowerDispatchOutboxResponse[]>(`/api/monitor/accounts/${accountId}/dispatches`)
}

export function getOrderTrace(masterAccountId: number, masterOrderId: number) {
  return apiRequest<ExecutionTraceResponse>(
    `/api/monitor/traces/order?masterAccountId=${masterAccountId}&masterOrderId=${masterOrderId}`,
  )
}

export function getPositionTrace(masterAccountId: number, masterPositionId: number) {
  return apiRequest<ExecutionTraceResponse>(
    `/api/monitor/traces/position?masterAccountId=${masterAccountId}&masterPositionId=${masterPositionId}`,
  )
}
