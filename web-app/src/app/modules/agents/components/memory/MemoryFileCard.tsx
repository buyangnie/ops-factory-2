import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import Button from '../../../../platform/ui/primitives/Button'
import './Memory.css'
import '../prompt/PromptsSection.css'

function EditIcon() {
    return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path
                d="M4.75 13.95 4 16l2.05-.75 8.5-8.5-1.3-1.3-8.5 8.5Z"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="m11.95 6.05 1.3 1.3m.65-.65 1.05-1.05a1.15 1.15 0 0 0 0-1.6l-.5-.5a1.15 1.15 0 0 0-1.6 0L11.8 4.6"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="M4 16h12"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
            />
        </svg>
    )
}

function TrashIcon() {
    return (
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path
                d="M6.5 5.5h7m-6 0V4.75A1.75 1.75 0 0 1 9.25 3h1.5A1.75 1.75 0 0 1 12.5 4.75v.75m-8 0h11m-1 0-.6 8.39a1.75 1.75 0 0 1-1.75 1.61H7.85A1.75 1.75 0 0 1 6.1 13.89L5.5 5.5m2.75 2.5v4m4-4v4"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    )
}

export interface MemoryEntry {
    tags: string[]
    content: string
}

// Muted palette — low saturation, enough to tell apart but not shout
const TAG_PALETTE = [
    { bg: 'rgba(59, 130, 246, 0.08)',  fg: '#4b7cc4' },  // steel blue
    { bg: 'rgba(16, 185, 129, 0.08)',  fg: '#3a9a7e' },  // sage
    { bg: 'rgba(139, 92, 246, 0.08)',  fg: '#8872b8' },  // lavender
    { bg: 'rgba(245, 158, 11, 0.07)',  fg: '#b8923a' },  // sand
    { bg: 'rgba(236, 72, 153, 0.07)', fg: '#b8648a' },   // dusty rose
    { bg: 'rgba(6, 182, 212, 0.08)',  fg: '#3a8f9a' },   // teal
    { bg: 'rgba(234, 88, 12, 0.07)',  fg: '#b07040' },   // terracotta
    { bg: 'rgba(99, 102, 241, 0.08)', fg: '#6e70b8' },   // periwinkle
]

function hashTag(s: string): number {
    let h = 0
    for (let i = 0; i < s.length; i++) h = ((h << 5) - h + s.charCodeAt(i)) | 0
    return Math.abs(h)
}

export function getTagColor(tag: string) {
    return TAG_PALETTE[hashTag(tag) % TAG_PALETTE.length]
}

export function parseMemoryContent(raw: string): MemoryEntry[] {
    if (!raw.trim()) return []
    const blocks = raw.split(/\n\n+/)
    const entries: MemoryEntry[] = []
    for (const block of blocks) {
        const trimmed = block.trim()
        if (!trimmed) continue
        const lines = trimmed.split('\n')
        if (lines[0].startsWith('#')) {
            const tagLine = lines[0].replace(/^#+\s*/, '')
            const tags = tagLine.split(/\s+/).filter(Boolean)
            const content = lines.slice(1).join('\n').trim()
            entries.push({ tags, content })
        } else {
            entries.push({ tags: [], content: trimmed })
        }
    }
    return entries
}

interface MemoryFileCardProps {
    category: string
    content: string
    onSave: (content: string) => Promise<boolean>
    onDelete: () => void
    autoEdit?: boolean
    isDeleting?: boolean
}

const MAX_PREVIEW_LINES = 3
const MAX_PREVIEW_CHARS = 50
const MAX_CONTENT_CHARS = 20000
const MAX_VISIBLE_TAGS = 3
const MAX_VISIBLE_ENTRIES = 3

export default function MemoryFileCard({ category, content, onSave, onDelete, autoEdit, isDeleting = false }: MemoryFileCardProps) {
    const { t } = useTranslation()
    const [isEditing, setIsEditing] = useState(autoEdit || false)
    const [editContent, setEditContent] = useState(content)
    const [isSaving, setIsSaving] = useState(false)
    const [hasChanges, setHasChanges] = useState(false)
    const [expandedEntries, setExpandedEntries] = useState<Set<number>>(new Set())
    const [showAllEntries, setShowAllEntries] = useState(false)

    const entries = useMemo(() => isEditing ? [] : parseMemoryContent(content), [isEditing, content])

    const handleEdit = () => {
        setEditContent(content)
        setIsEditing(true)
        setHasChanges(false)
    }

    const handleCancel = () => {
        setEditContent(content)
        setIsEditing(false)
        setHasChanges(false)
    }

    const handleSave = async () => {
        if (editContent.length > MAX_CONTENT_CHARS) {
            alert(t('memory.contentTooLong', { max: MAX_CONTENT_CHARS.toLocaleString() }))
            return
        }
        setIsSaving(true)
        const ok = await onSave(editContent)
        setIsSaving(false)
        if (ok) {
            setIsEditing(false)
            setHasChanges(false)
        }
    }

    const handleChange = (val: string) => {
        setEditContent(val)
        setHasChanges(val !== content)
    }

    const toggleExpanded = (idx: number) => {
        setExpandedEntries(prev => {
            const next = new Set(prev)
            if (next.has(idx)) {
                next.delete(idx)
            } else {
                next.add(idx)
            }
            return next
        })
    }

    const needsTruncate = (text: string): boolean => {
        const lines = text.split('\n')
        return lines.length > MAX_PREVIEW_LINES || text.length > MAX_PREVIEW_CHARS
    }

    const getPreviewContent = (text: string): string => {
        const lines = text.split('\n')
        if (lines.length > MAX_PREVIEW_LINES) {
            return lines.slice(0, MAX_PREVIEW_LINES).join('\n').trim()
        }
        if (text.length > MAX_PREVIEW_CHARS) {
            return text.slice(0, MAX_PREVIEW_CHARS)
        }
        return text
    }

    return (
        <div className={`memory-file-card ${isEditing ? 'memory-file-card-editing' : ''}`}>
            <div className="memory-file-header">
                <div className="memory-file-title">
                    <span className="memory-file-name" title={category}>{category}</span>
                    <span className="memory-file-count">
                        {entries.length > 0 && t('memory.entryCount', { count: entries.length })}
                    </span>
                </div>
                <div className="memory-file-actions">
                    {isEditing ? (
                        <Button variant="secondary" size="sm" className="prompts-edit-btn" onClick={handleCancel}>
                            {t('prompts.collapse')}
                        </Button>
                    ) : (
                        <button
                            type="button"
                            className="card-icon-action"
                            onClick={handleEdit}
                            title={t('common.edit')}
                            aria-label={t('common.edit')}
                        >
                            <EditIcon />
                        </button>
                    )}
                    <button
                        type="button"
                        className="card-icon-action card-icon-action-danger"
                        onClick={onDelete}
                        title={t('common.delete')}
                        aria-label={t('common.delete')}
                    >
                        <TrashIcon />
                    </button>
                </div>
            </div>

            {isDeleting && (
                <div className="memory-delete-confirm">
                    {t('memory.deleteConfirm')} <span className="memory-delete-confirm-name">「{category}」</span>
                </div>
            )}

            {isEditing ? (
                <div className="memory-file-editor">
                    <textarea
                        className="prompts-textarea"
                        value={editContent}
                        onChange={e => handleChange(e.target.value)}
                        rows={10}
                    />
                    <div className="memory-editor-footer">
                        <div className="memory-format-hint">
                            {t('memory.formatHint')}
                        </div>
                        <div className={`memory-char-count ${editContent.length > MAX_CONTENT_CHARS ? 'memory-char-count-error' : ''}`}>
                            {editContent.length.toLocaleString()} / {MAX_CONTENT_CHARS.toLocaleString()}
                        </div>
                    </div>
                    <div className="prompts-editor-actions">
                        <div className="prompts-editor-actions-left" />
                        <div className="prompts-editor-actions-right">
                            <Button variant="secondary" onClick={handleCancel}>
                                {t('common.cancel')}
                            </Button>
                            <Button variant="primary" onClick={handleSave} disabled={isSaving || !hasChanges || editContent.length > MAX_CONTENT_CHARS}>
                                {isSaving ? t('agentConfigure.saving') : t('common.save')}
                            </Button>
                        </div>
                    </div>
                </div>
            ) : (
                <div className="memory-entries">
                    {entries.length === 0 ? (
                        <div className="memory-entry-empty">{t('memory.emptyFile')}</div>
                    ) : (
                        <>
                            {entries.slice(0, showAllEntries ? entries.length : MAX_VISIBLE_ENTRIES).map((entry, idx) => {
                            const isExpanded = expandedEntries.has(idx)
                            const shouldTruncate = !isExpanded && needsTruncate(entry.content)
                            const displayContent = shouldTruncate ? getPreviewContent(entry.content) : entry.content

                            return (
                                <div key={idx} className="memory-entry">
                                    <div className="memory-entry-tags">
                                        {entry.tags.length > 0 ? (
                                            <>
                                                {entry.tags.slice(0, MAX_VISIBLE_TAGS).map(tag => {
                                                    const c = getTagColor(tag)
                                                    return (
                                                        <span
                                                            key={tag}
                                                            className="memory-tag"
                                                            style={{ background: c.bg, color: c.fg }}
                                                            title={tag}
                                                        >
                                                            {tag}
                                                        </span>
                                                    )
                                                })}
                                                {entry.tags.length > MAX_VISIBLE_TAGS && (
                                                    <span className="memory-tag memory-tag-more">
                                                        +{entry.tags.length - MAX_VISIBLE_TAGS}
                                                    </span>
                                                )}
                                            </>
                                        ) : (
                                            <span className="memory-tag memory-tag-untagged">{t('memory.untagged')}</span>
                                        )}
                                    </div>
                                    {entry.content && (
                                        <>
                                            <div className={`memory-entry-content ${shouldTruncate ? 'memory-entry-content-truncated' : ''}`}>
                                                {displayContent}
                                            </div>
                                            {needsTruncate(entry.content) && (
                                                <button
                                                    type="button"
                                                    className="memory-expand-button"
                                                    onClick={() => toggleExpanded(idx)}
                                                >
                                                    {isExpanded ? t('memory.collapse') : t('memory.expand')}
                                                </button>
                                            )}
                                        </>
                                    )}
                                </div>
                            )
                        })}
                        {entries.length > MAX_VISIBLE_ENTRIES && (
                            <button
                                type="button"
                                className="memory-show-all-button"
                                onClick={() => setShowAllEntries(!showAllEntries)}
                            >
                                {showAllEntries ? t('memory.showLess') : t('memory.showMore', { count: entries.length - MAX_VISIBLE_ENTRIES })}
                            </button>
                        )}
                        </>
                    )}
                </div>
            )}
        </div>
    )
}
