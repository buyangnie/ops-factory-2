import { useTranslation } from 'react-i18next'
import './Pagination.css'

interface PaginationProps {
    currentPage: number
    totalPages: number
    pageSize: number
    totalItems: number
    onPageChange: (page: number) => void
    onPageSizeChange?: (size: number) => void
    disabled?: boolean
    /** @deprecated Pagination now uses one shared visual treatment. Kept for backward-compatible callers. */
    variant?: 'default' | 'compact'
}

export default function Pagination({
    currentPage,
    totalPages,
    pageSize,
    totalItems,
    onPageChange,
    onPageSizeChange,
    disabled = false,
    variant = 'default'
}: PaginationProps) {
    const { t } = useTranslation()
    const safeTotalPages = Math.max(1, totalPages)
    const safeCurrentPage = Math.min(Math.max(1, currentPage), safeTotalPages)
    const startItem = totalItems === 0 ? 0 : Math.min((safeCurrentPage - 1) * pageSize + 1, totalItems)
    const endItem = Math.min(safeCurrentPage * pageSize, totalItems)

    return (
        <div className={`pagination pagination-${variant}`}>
            <div className="pagination-info">
                <span className="pagination-text">
                    {t('common.showing', {
                        start: startItem,
                        end: endItem,
                        total: totalItems
                    })}
                </span>

                {onPageSizeChange && (
                    <select
                        className="pagination-size-select"
                        value={pageSize}
                        onChange={(e) => onPageSizeChange(Number(e.target.value))}
                        disabled={disabled}
                    >
                        <option value={10}>10 {t('common.perPage')}</option>
                        <option value={20}>20 {t('common.perPage')}</option>
                        <option value={50}>50 {t('common.perPage')}</option>
                        <option value={100}>100 {t('common.perPage')}</option>
                    </select>
                )}
            </div>

            <div className="pagination-controls">
                <button
                    className="pagination-btn"
                    onClick={() => onPageChange(safeCurrentPage - 1)}
                    disabled={disabled || safeCurrentPage === 1}
                    aria-label={t('common.previousPage')}
                >
                    {t('common.previousPage')}
                </button>

                <span className="pagination-page-indicator">{safeCurrentPage} / {safeTotalPages}</span>

                <button
                    className="pagination-btn"
                    onClick={() => onPageChange(safeCurrentPage + 1)}
                    disabled={disabled || safeCurrentPage === safeTotalPages}
                    aria-label={t('common.nextPage')}
                >
                    {t('common.nextPage')}
                </button>
            </div>
        </div>
    )
}
