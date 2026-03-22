# Monitor Service

## Goal

This module turns MT5 signal activity, runtime connection state, and dispatch execution state into queryable monitoring views.

Current code scope:

1. Persist accepted MT5 signal audit records into MySQL
2. Maintain MT5 account runtime state in a Redis-first store
3. Expose aggregated account monitoring APIs
4. Expose MT5 websocket session views
5. Expose follower websocket session views through the follower-exec module

## Current Runtime-State Design

### Source of truth

1. Signal audit remains in MySQL
2. Runtime-state hot data is Redis-first
3. MySQL keeps throttled runtime-state snapshots and disconnect-time forced persistence
4. Redis runtime-state is never treated as business truth for core config or execution history

### Runtime-state keys

1. `copy:runtime:state:{server}:{login}`
2. `copy:runtime:account:{accountId}`
3. `copy:runtime:index`
4. `copy:runtime:db-sync:{server}:{login}`

### Write path

1. MT5 master `HELLO / HEARTBEAT / DEAL / ORDER` updates runtime-state through a unified store
2. Follower `HELLO / HEARTBEAT` updates runtime-state through the same store
3. Writes go to Redis first
4. Database persistence is throttled by `copier.monitor.runtime-state.database-sync-interval`
5. Disconnect events force a database snapshot write immediately

### Heartbeat to Redis to DB flow

The current heartbeat path is intentionally designed as `Redis-first, DB-throttled`:

1. A master or follower websocket message arrives
2. The runtime-state store normalizes the latest connection status, timestamps, balance, and equity
3. Redis is updated first:
   `copy:runtime:state:{server}:{login}`
   `copy:runtime:account:{accountId}`
   `copy:runtime:index`
4. The store checks the last database sync marker:
   `copy:runtime:db-sync:{server}:{login}`
5. If the throttling window has not expired, the write stops at Redis and does not hit JPA
6. If the throttling window has expired, the latest runtime-state snapshot is also persisted to MySQL
7. If the websocket disconnects, the runtime-state is marked `DISCONNECTED` and the snapshot is forced to MySQL immediately

This is the key reason heartbeat traffic no longer turns into a full-rate JPA write path.

### Why this matters

1. Monitoring APIs can read fresh runtime-state without waiting for database flushes
2. Ratio-copy reads follower funds from Redis-first runtime-state instead of querying the runtime-state table on every trade
3. Database snapshots still exist for restart visibility, audit support, and controlled recovery
4. After Redis restore, stale runtime-state still cannot be used for ratio-copy unless it passes the freshness gate

### Read path

1. `GET /api/monitor/runtime-states` reads the unified runtime-state store
2. `GET /api/monitor/accounts/overview` merges account config, runtime-state, and dispatch counters
3. `GET /api/monitor/ws-sessions` merges websocket session registry with runtime-state
4. Copy engine balance/equity scaling also reads follower funds from the runtime-state store

### Freshness gate for ratio-copy

1. `BALANCE_RATIO` and `EQUITY_RATIO` no longer silently fall back to `1.0` when follower funds are missing
2. Copy engine checks runtime-state freshness before using follower balance/equity
3. Freshness is controlled by `copier.monitor.runtime-state.funds-stale-after`
4. The gate can be disabled by `copier.monitor.runtime-state.require-fresh-funds-for-ratio=false`, but the default is `true`
5. After Redis restore, stale runtime-state must not be trusted for real sizing until fresh `HELLO` or `HEARTBEAT` arrives

## Available APIs

1. `GET /api/monitor/runtime-states`
2. `GET /api/monitor/accounts/overview`
3. `GET /api/monitor/ws-sessions`
4. `GET /api/monitor/accounts/{accountId}/signals`
5. `GET /api/monitor/signals?accountKey=...`

## Config Keys

1. `copier.monitor.heartbeat-stale-after`
2. `copier.monitor.runtime-state.backend`
3. `copier.monitor.runtime-state.key-prefix`
4. `copier.monitor.runtime-state.database-sync-interval`
5. `copier.monitor.runtime-state.funds-stale-after`
6. `copier.monitor.runtime-state.require-fresh-funds-for-ratio`
7. `copier.monitor.runtime-state.warmup-on-startup`
8. `copier.monitor.session-registry.backend`
9. `copier.monitor.session-registry.key-prefix`
10. `copier.monitor.session-registry.ttl`

## Local Recommendation

For local single-node integration:

1. `copier.monitor.runtime-state.backend=redis`
2. `copier.monitor.runtime-state.funds-stale-after=PT30S`
3. `copier.monitor.runtime-state.require-fresh-funds-for-ratio=true`
4. `copier.monitor.session-registry.backend=redis` or `memory`
5. `copier.mt5.follower-exec.realtime-dispatch.backend=local`

## Backup / Recovery Notes

1. MariaDB is still the source of truth for config, command history, outbox, and audit
2. Redis runtime-state is recoverable hot state, not authoritative history
3. After Redis restore, clear `copy:ws:*` and `copy:signal:dedup:*`
4. If restored runtime-state is older than `funds-stale-after`, ratio-copy should stay blocked until fresh heartbeat arrives
5. Full backup/recovery workflow is documented in [../operations/redis-backup-recovery.md](../operations/redis-backup-recovery.md)

## Current Limits

1. Runtime-state database sync is throttled inline, not handled by a dedicated async flush worker
2. Signal audit is still DB-first and is not buffered through Redis
3. Follower position inventory reconciliation and broker-side account reconciliation are not implemented yet
