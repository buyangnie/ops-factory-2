import type { ButtonHTMLAttributes, HTMLAttributes, ReactElement, ReactNode } from 'react'
import {
    Download,
    Pencil,
    Play,
    RotateCcw,
    Settings,
    Square,
    Trash2,
    type AppIcon,
} from '../icons/AppIcons'
import { ItemActionButton, ItemActionGroup, type ItemActionTone } from './ItemAction'
import './ResourceCard.css'

export type ResourceStatusTone = 'neutral' | 'configured' | 'success' | 'warning' | 'danger'

export interface ResourceCardMetric {
    label: string
    value: ReactNode
    valueClassName?: string
}

interface ResourceCardProps {
    className?: string
    title: string
    statusLabel?: string
    statusTone?: ResourceStatusTone
    tags?: ReactNode
    summary?: ReactNode
    metrics: ResourceCardMetric[]
    footer?: ReactNode
}

interface ResourceCardActionGroupProps extends HTMLAttributes<HTMLDivElement> {
    children: ReactNode
}

interface ResourceCardActionProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
    icon: AppIcon
    label: string
    tone?: ItemActionTone
}

export function ResourceCardActionGroup({ children, className, ...props }: ResourceCardActionGroupProps): ReactElement {
    return (
        <ItemActionGroup {...props} className={['card-icon-actions', className].filter(Boolean).join(' ')}>
            {children}
        </ItemActionGroup>
    )
}

export function ResourceCardAction({
    icon: Icon,
    label,
    tone = 'default',
    className = '',
    type = 'button',
    ...props
}: ResourceCardActionProps): ReactElement {
    return (
        <ItemActionButton
            {...props}
            icon={Icon}
            label={label}
            tone={tone}
            type={type}
            className={['card-icon-action', className].filter(Boolean).join(' ')}
        />
    )
}

interface ResourceCardIconActionProps {
    onClick?: () => void
    label: string
    disabled?: boolean
}

export function ResourceCardConfigureAction({ onClick, label, disabled }: ResourceCardIconActionProps): ReactElement {
    return (
        <ResourceCardAction icon={Settings} label={label} tone="primary" onClick={onClick} disabled={disabled} />
    )
}

export function ResourceCardEditAction({ onClick, label, disabled }: ResourceCardIconActionProps): ReactElement {
    return (
        <ResourceCardAction icon={Pencil} label={label} onClick={onClick} disabled={disabled} />
    )
}

export function ResourceCardInstallAction({ onClick, label, disabled }: ResourceCardIconActionProps): ReactElement {
    return (
        <ResourceCardAction icon={Download} label={label} tone="success" onClick={onClick} disabled={disabled} />
    )
}

export function ResourceCardStartAction({ onClick, label, disabled }: ResourceCardIconActionProps): ReactElement {
    return (
        <ResourceCardAction icon={Play} label={label} tone="success" onClick={onClick} disabled={disabled} />
    )
}

export function ResourceCardRestartAction({ onClick, label, disabled }: ResourceCardIconActionProps): ReactElement {
    return (
        <ResourceCardAction icon={RotateCcw} label={label} tone="warning" onClick={onClick} disabled={disabled} />
    )
}

export function ResourceCardStopAction({ onClick, label, disabled }: ResourceCardIconActionProps): ReactElement {
    return (
        <ResourceCardAction icon={Square} label={label} tone="danger" onClick={onClick} disabled={disabled} />
    )
}

export function ResourceCardDeleteAction({ onClick, label, disabled }: ResourceCardIconActionProps): ReactElement {
    return (
        <ResourceCardAction icon={Trash2} label={label} tone="danger" onClick={onClick} disabled={disabled} />
    )
}

function getMetricColumnClass(count: number): string {
    if (count <= 1) return 'columns-1'
    if (count === 2) return 'columns-2'
    return 'columns-3'
}

export default function ResourceCard({
    className,
    title,
    statusLabel,
    statusTone = 'neutral',
    tags,
    summary,
    metrics,
    footer,
}: ResourceCardProps) {
    const cardClassName = ['resource-card', className].filter(Boolean).join(' ')
    const metricClassName = ['resource-card-metrics', getMetricColumnClass(metrics.length)].join(' ')

    return (
        <article className={cardClassName}>
            <div className="resource-card-header">
                <h3 className="resource-card-title" title={title}>
                    {title}
                </h3>
                {statusLabel && (
                    <span className={`resource-status resource-status-${statusTone}`}>
                        {statusLabel}
                    </span>
                )}
            </div>

            {(tags || summary) && (
                <div className="resource-card-summary">
                    {tags && <div className="resource-card-tags-slot">{tags}</div>}
                    {summary}
                </div>
            )}

            <div className={metricClassName}>
                {metrics.map(metric => (
                    <div key={metric.label} className="resource-card-metric">
                        <span className="resource-card-metric-label">{metric.label}</span>
                        <span className={['resource-card-metric-value', metric.valueClassName].filter(Boolean).join(' ')}>
                            {metric.value}
                        </span>
                    </div>
                ))}
            </div>

            {footer && (
                <div className="resource-card-footer">
                    {footer}
                </div>
            )}
        </article>
    )
}
