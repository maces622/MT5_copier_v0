import type { ReactNode } from 'react'
import { statusTone } from '../lib/format'

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string
  description: string
  actions?: ReactNode
}) {
  return (
    <div className="page-header">
      <div>
        <div className="page-header__eyebrow">Console View</div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {actions ? <div className="page-header__actions">{actions}</div> : null}
    </div>
  )
}

export function Surface({
  title,
  description,
  children,
}: {
  title?: string
  description?: string
  children: ReactNode
}) {
  return (
    <section className="surface">
      {title || description ? (
        <header className="surface__header">
          {title ? <h2>{title}</h2> : null}
          {description ? <p>{description}</p> : null}
        </header>
      ) : null}
      {children}
    </section>
  )
}

export function MetricCard({
  label,
  value,
  tone = 'neutral',
}: {
  label: string
  value: ReactNode
  tone?: 'good' | 'warn' | 'bad' | 'neutral'
}) {
  return (
    <article className={`metric metric--${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}

export function EmptyState({ title, message }: { title: string; message: string }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{message}</p>
    </div>
  )
}

export function StatusPill({ value }: { value?: string | null }) {
  return <span className={`pill pill--${statusTone(value)}`}>{value ?? 'n/a'}</span>
}

export function DataTable({
  headers,
  rows,
}: {
  headers: string[]
  rows: ReactNode[][]
}) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, cellIndex) => (
                <td key={cellIndex}>{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function KeyValueGrid({
  items,
}: {
  items: Array<{ label: string; value: ReactNode }>
}) {
  return (
    <div className="kv-grid">
      {items.map((item) => (
        <div key={item.label} className="kv-grid__item">
          <span>{item.label}</span>
          <strong>{item.value}</strong>
        </div>
      ))}
    </div>
  )
}

export function LoadingState({ label = '正在加载控制台数据...' }: { label?: string }) {
  return <div className="loading-state">{label}</div>
}

export function ErrorState({
  title = '请求失败',
  message,
}: {
  title?: string
  message: string
}) {
  return (
    <div className="error-state">
      <strong>{title}</strong>
      <p>{message}</p>
    </div>
  )
}
