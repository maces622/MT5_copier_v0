export interface AuthenticatedPlatformUserResponse {
  id: number
  platformId: string
  username: string
  shareId: string
  displayName: string | null
  status: string
  role: string
  createdAt: string
  updatedAt: string
}

export interface Mt5AccountResponse {
  id: number
  userId: number
  brokerName: string
  serverName: string
  mt5Login: number
  credentialVersion: number | null
  credentialConfigured: boolean
  accountRole: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface RiskRuleResponse {
  id: number
  accountId: number
  maxLot: number | null
  fixedLot: number | null
  balanceRatio: number | null
  maxSlippagePoints: number | null
  maxSlippagePips: number | null
  maxSlippagePrice: number | null
  maxDailyLoss: number | null
  maxDrawdownPct: number | null
  allowedSymbols: string | null
  blockedSymbols: string | null
  followTpSl: boolean
  reverseFollow: boolean
  createdAt: string
  updatedAt: string
}

export interface CopyRelationResponse {
  id: number
  masterAccountId: number
  followerAccountId: number
  copyMode: string
  status: string
  priority: number
  configVersion: number
  createdAt: string
  updatedAt: string
}

export interface RelationAccountSummaryResponse {
  id: number
  brokerName: string
  serverName: string
  mt5Login: number
  accountRole: string
  status: string
}

export interface PlatformCopyRelationViewResponse {
  relation: CopyRelationResponse
  masterAccount: RelationAccountSummaryResponse
  followerAccount: RelationAccountSummaryResponse
  currentUserOwnsMaster: boolean
  currentUserOwnsFollower: boolean
}

export interface AccountRelationsResponse {
  accountId: number
  masterRelations: CopyRelationResponse[]
  followerRelations: CopyRelationResponse[]
}

export interface SymbolMappingResponse {
  id: number
  followerAccountId: number
  masterSymbol: string
  followerSymbol: string
  createdAt: string
  updatedAt: string
}

export interface MasterShareConfigResponse {
  id: number
  masterAccountId: number
  shareEnabled: boolean
  shareCodeConfigured: boolean
  shareNote: string | null
  rotatedAt: string | null
  expiresAt: string | null
  createdAt: string
  updatedAt: string
}

export interface PlatformAccountDetailResponse {
  account: Mt5AccountResponse
  riskRule: RiskRuleResponse | null
  relations: AccountRelationsResponse
  symbolMappings: SymbolMappingResponse[]
  shareConfig: MasterShareConfigResponse | null
}

export interface MasterShareAccountSummaryResponse {
  masterAccountId: number
  brokerName: string
  serverName: string
  mt5Login: number
  accountRole: string
  shareEnabled: boolean
  shareCodeConfigured: boolean
  shareNote: string | null
  rotatedAt: string | null
  expiresAt: string | null
  updatedAt: string
}

export interface ShareProfileResponse {
  userId: number
  platformId: string
  shareId: string
  displayName: string | null
  masterAccounts: MasterShareAccountSummaryResponse[]
}

export interface MonitorDashboardResponse {
  totalAccounts: number
  onlineMasterCount: number
  onlineFollowerCount: number
  staleRuntimeStateCount: number
  pendingDispatchCount: number
  failedDispatchCount: number
}

export interface Mt5AccountMonitorOverviewResponse {
  accountId: number
  userId: number
  brokerName: string
  serverName: string
  mt5Login: number
  accountKey: string
  accountRole: string
  accountStatus: string
  connectionStatus: string
  lastHelloAt: string | null
  lastHeartbeatAt: string | null
  lastSignalAt: string | null
  lastSignalType: string | null
  lastEventId: string | null
  activeFollowerCount: number
  activeMasterCount: number
  pendingDispatchCount: number
  failedDispatchCount: number
  updatedAt: string | null
}

export interface Mt5RuntimeStateResponse {
  accountId: number | null
  accountKey: string | null
  serverName: string
  mt5Login: number
  connectionStatus: string
  lastHelloAt: string | null
  lastHeartbeatAt: string | null
  lastSignalAt: string | null
  lastSignalType: string | null
  lastEventId: string | null
  balance: number | null
  equity: number | null
  updatedAt: string | null
}

export interface Mt5WsSessionResponse {
  sessionId: string
  traceId: string
  connectedAt: string
  accountId: number | null
  login: number | null
  server: string | null
  accountKey: string | null
  connectionStatus: string
  lastHelloAt: string | null
  lastHeartbeatAt: string | null
  lastSignalAt: string | null
  lastSignalType: string | null
  lastEventId: string | null
}

export interface FollowerExecSessionResponse {
  sessionId: string
  traceId: string
  connectedAt: string
  followerAccountId: number | null
  login: number | null
  server: string | null
  accountKey: string | null
  lastHelloAt: string | null
  lastHeartbeatAt: string | null
  lastAckAt: string | null
}

export interface MonitorAccountDetailResponse {
  overview: Mt5AccountMonitorOverviewResponse | null
  runtimeState: Mt5RuntimeStateResponse | null
  wsSessions: Mt5WsSessionResponse[]
  followerExecSessions: FollowerExecSessionResponse[]
}

export interface ExecutionCommandResponse {
  id: number
  masterEventId: string
  masterAccountId: number
  masterAccountKey: string
  followerAccountId: number
  masterSymbol: string
  signalType: string
  commandType: string
  symbol: string
  masterAction: string
  followerAction: string
  copyMode: string
  requestedVolume: number | null
  requestedPrice: number | null
  requestedSl: number | null
  requestedTp: number | null
  masterDealId: number | null
  masterOrderId: number | null
  masterPositionId: number | null
  status: string
  rejectReason: string | null
  rejectMessage: string | null
  signalTime: string | null
  createdAt: string
}

export interface FollowerDispatchOutboxResponse {
  id: number
  executionCommandId: number
  masterEventId: string
  followerAccountId: number
  status: string
  statusMessage: string | null
  payloadJson: string
  ackedAt: string | null
  failedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ExecutionTraceResponse {
  masterAccountId: number
  masterOrderId: number | null
  masterPositionId: number | null
  commands: ExecutionCommandResponse[]
  dispatches: FollowerDispatchOutboxResponse[]
}
