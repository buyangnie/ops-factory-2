import type { AnchorHTMLAttributes, ButtonHTMLAttributes, HTMLAttributes, ReactElement, ReactNode } from 'react'
import type { AppIcon } from '../icons/AppIcons'

export type ItemActionTone = 'default' | 'primary' | 'success' | 'warning' | 'danger'

interface ItemActionGroupProps extends HTMLAttributes<HTMLDivElement> {
    children: ReactNode
}

interface ItemActionBaseProps {
    icon: AppIcon
    label: string
    tone?: ItemActionTone
    iconClassName?: string
}

interface ItemActionButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'>, ItemActionBaseProps {}
interface ItemActionLinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'children'>, ItemActionBaseProps {}

function getActionClassName(tone: ItemActionTone, className = ''): string {
    return ['item-action', `item-action-${tone}`, className].filter(Boolean).join(' ')
}

export function ItemActionGroup({ children, className, ...props }: ItemActionGroupProps): ReactElement {
    return (
        <div {...props} className={['item-actions', className].filter(Boolean).join(' ')}>
            {children}
        </div>
    )
}

export function ItemActionButton({
    icon: Icon,
    label,
    tone = 'default',
    iconClassName,
    className = '',
    type = 'button',
    ...props
}: ItemActionButtonProps): ReactElement {
    const ariaLabel = props['aria-label'] || label

    return (
        <button
            {...props}
            type={type}
            className={getActionClassName(tone, className)}
            title={label}
            aria-label={ariaLabel}
        >
            <Icon className={iconClassName} aria-hidden="true" />
        </button>
    )
}

export function ItemActionLink({
    icon: Icon,
    label,
    tone = 'default',
    iconClassName,
    className = '',
    ...props
}: ItemActionLinkProps): ReactElement {
    const ariaLabel = props['aria-label'] || label

    return (
        <a
            {...props}
            className={getActionClassName(tone, className)}
            title={label}
            aria-label={ariaLabel}
        >
            <Icon className={iconClassName} aria-hidden="true" />
        </a>
    )
}
