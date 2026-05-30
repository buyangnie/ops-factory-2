import { AlertCircle, CheckCircle2, Info, Minus, TriangleAlert } from '../icons/AppIcons'
import './StatusIcon.css'

export type StatusTone = 'success' | 'warning' | 'danger' | 'neutral' | 'info'

interface StatusIconProps {
    tone: StatusTone
    size?: 14 | 16 | 18 | 20
    label?: string
    className?: string
}

function getStatusIcon(tone: StatusTone, size: number) {
    switch (tone) {
        case 'success':
            return <CheckCircle2 size={size} strokeWidth={2.1} />
        case 'warning':
            return <TriangleAlert size={size} strokeWidth={2.05} />
        case 'danger':
            return <AlertCircle size={size} strokeWidth={2.1} />
        case 'info':
            return <Info size={size} strokeWidth={2.1} />
        default:
            return <Minus size={size} strokeWidth={2.1} />
    }
}

export default function StatusIcon({
    tone,
    size = 16,
    label,
    className,
}: StatusIconProps) {
    const classes = ['status-icon', `status-icon-${tone}`, className].filter(Boolean).join(' ')

    return (
        <span className={classes} role={label ? 'img' : undefined} aria-label={label}>
            {getStatusIcon(tone, size)}
        </span>
    )
}
