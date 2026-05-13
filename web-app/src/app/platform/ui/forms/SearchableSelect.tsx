import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import './SearchableSelect.css'

interface SearchableSelectProps {
    value: string
    onChange: (value: string) => void
    options: { value: string; label: string }[]
    placeholder?: string
    searchPlaceholder?: string
    className?: string
    style?: React.CSSProperties
}

export default function SearchableSelect({
    value,
    onChange,
    options,
    placeholder = '',
    searchPlaceholder,
    className,
    style,
}: SearchableSelectProps) {
    const { t } = useTranslation()
    const [open, setOpen] = useState(false)
    const [search, setSearch] = useState('')
    const [highlightIndex, setHighlightIndex] = useState(-1)
    const containerRef = useRef<HTMLDivElement>(null)
    const searchRef = useRef<HTMLInputElement>(null)

    const selectedOption = options.find(o => o.value === value)

    const filtered = useMemo(() => {
        if (!search) return options
        const lower = search.toLowerCase()
        return options.filter(o => o.label.toLowerCase().includes(lower))
    }, [options, search])

    // Focus search input when opening
    useEffect(() => {
        if (open && searchRef.current) {
            searchRef.current.focus()
        }
    }, [open])

    // Close on click outside
    useEffect(() => {
        if (!open) return
        const handler = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setOpen(false)
                setSearch('')
                setHighlightIndex(-1)
            }
        }
        document.addEventListener('mousedown', handler)
        return () => document.removeEventListener('mousedown', handler)
    }, [open])

    const handleSelect = useCallback((val: string) => {
        onChange(val)
        setOpen(false)
        setSearch('')
        setHighlightIndex(-1)
    }, [onChange])

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (!open) {
            if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
                e.preventDefault()
                setOpen(true)
            }
            return
        }
        switch (e.key) {
            case 'Escape':
                e.preventDefault()
                setOpen(false)
                setSearch('')
                setHighlightIndex(-1)
                break
            case 'ArrowDown':
                e.preventDefault()
                setHighlightIndex(i => (i + 1) % filtered.length)
                break
            case 'ArrowUp':
                e.preventDefault()
                setHighlightIndex(i => (i - 1 + filtered.length) % filtered.length)
                break
            case 'Enter':
                e.preventDefault()
                if (highlightIndex >= 0 && highlightIndex < filtered.length) {
                    handleSelect(filtered[highlightIndex].value)
                }
                break
            default:
                break
        }
    }, [open, filtered, highlightIndex, handleSelect])

    const triggerLabel = selectedOption?.label || placeholder

    return (
        <div
            ref={containerRef}
            className={`searchable-select${className ? ` ${className}` : ''}`}
            style={style}
            onKeyDown={handleKeyDown}
        >
            <button
                type="button"
                className="searchable-select-trigger form-input"
                onClick={() => { setOpen(prev => !prev); setSearch('') }}
            >
                <span className={!selectedOption ? 'searchable-select-placeholder' : ''}>
                    {triggerLabel}
                </span>
                <span className="searchable-select-chevron">&#9662;</span>
            </button>
            {open && (
                <div className="searchable-select-panel">
                    <input
                        ref={searchRef}
                        className="searchable-select-search"
                        type="text"
                        value={search}
                        onChange={e => { setSearch(e.target.value); setHighlightIndex(-1) }}
                        placeholder={searchPlaceholder || t('common.typeToSearch')}
                    />
                    <div className="searchable-select-list">
                        {filtered.length === 0 ? (
                            <div className="searchable-select-empty">{t('common.noResults')}</div>
                        ) : (
                            filtered.map((opt, i) => (
                                <div
                                    key={opt.value}
                                    className={`searchable-select-option${opt.value === value ? ' selected' : ''}${i === highlightIndex ? ' highlighted' : ''}`}
                                    onMouseDown={() => handleSelect(opt.value)}
                                    onMouseEnter={() => setHighlightIndex(i)}
                                >
                                    {opt.label}
                                </div>
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    )
}
