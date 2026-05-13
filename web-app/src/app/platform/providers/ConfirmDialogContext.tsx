import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

interface ConfirmOptions {
    title?: string
    message: string
    confirmLabel?: string
    cancelLabel?: string
    variant?: 'danger' | 'default'
}

interface ConfirmDialogState {
    open: boolean
    title: string
    message: string
    confirmLabel: string
    cancelLabel: string
    variant: 'danger' | 'default'
    resolve: ((value: boolean) => void) | null
}

const initialState: ConfirmDialogState = {
    open: false,
    title: '',
    message: '',
    confirmLabel: '',
    cancelLabel: '',
    variant: 'default',
    resolve: null,
}

interface ConfirmDialogContextType {
    requestConfirm: (options: ConfirmOptions) => Promise<boolean>
}

const ConfirmDialogContext = createContext<ConfirmDialogContextType | null>(null)

export function useConfirmDialog() {
    const context = useContext(ConfirmDialogContext)
    if (!context) {
        throw new Error('useConfirmDialog must be used within a ConfirmDialogProvider')
    }
    return context
}

interface ConfirmDialogProviderProps {
    children: ReactNode
}

export function ConfirmDialogProvider({ children }: ConfirmDialogProviderProps) {
    const { t } = useTranslation()
    const [state, setState] = useState<ConfirmDialogState>(initialState)

    const requestConfirm = useCallback((options: ConfirmOptions): Promise<boolean> => {
        return new Promise<boolean>((resolve) => {
            setState({
                open: true,
                title: options.title || t('common.confirmTitle'),
                message: options.message,
                confirmLabel: options.confirmLabel || t('common.confirm'),
                cancelLabel: options.cancelLabel || t('common.cancel'),
                variant: options.variant || 'default',
                resolve,
            })
        })
    }, [t])

    const handleClose = useCallback((value: boolean) => {
        state.resolve?.(value)
        setState(initialState)
    }, [state.resolve])

    return (
        <ConfirmDialogContext.Provider value={{ requestConfirm }}>
            {children}
            {state.open && (
                <div className="modal-overlay" role="dialog" aria-modal="true">
                    <div className="modal-content modal-default">
                        <div className="modal-header">
                            <h3 className="modal-title">{state.title}</h3>
                            <button type="button" className="modal-close" onClick={() => handleClose(false)} aria-label="Close dialog">
                                ×
                            </button>
                        </div>
                        <div className="modal-body">
                            <p>{state.message}</p>
                        </div>
                        <div className="modal-footer">
                            <button className="btn btn-secondary" onClick={() => handleClose(false)}>
                                {state.cancelLabel}
                            </button>
                            <button
                                className={`btn ${state.variant === 'danger' ? 'btn-danger' : 'btn-primary'}`}
                                onClick={() => handleClose(true)}
                            >
                                {state.confirmLabel}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </ConfirmDialogContext.Provider>
    )
}
