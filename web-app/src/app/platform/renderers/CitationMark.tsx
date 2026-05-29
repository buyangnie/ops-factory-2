import { useState, useEffect } from 'react'
import type { RefObject } from 'react'
import { runtime } from '../../../config/runtime'
import type { Citation } from '../../../utils/citationParser'
import { useCitationCard } from './useCitationCard'
import './CitationMark.css'

interface CitationMarkProps {
    citation: Citation
}

const sourceNameCache = new Map<string, string>()

export default function CitationMark({ citation }: CitationMarkProps) {
    const { markRef, show, hide, renderCard } = useCitationCard(citation)
    const [sourceName, setSourceName] = useState<string | null>(citation.sourceId || null)
    const wrapperRef = markRef as RefObject<HTMLSpanElement>

    useEffect(() => {
        if (!citation.sourceId) {
            setSourceName(null)
            return
        }

        const cached = sourceNameCache.get(citation.sourceId)
        if (cached) {
            setSourceName(cached)
            return
        }

        let cancelled = false
        fetch(`${runtime.KNOWLEDGE_SERVICE_URL}/sources/${citation.sourceId}`, {
            signal: AbortSignal.timeout(10000),
        })
            .then(async response => {
                if (!response.ok) throw new Error(String(response.status))
                return response.json() as Promise<{ name?: string }>
            })
            .then(data => {
                const nextName = data.name?.trim() || citation.sourceId || ''
                sourceNameCache.set(citation.sourceId as string, nextName)
                if (!cancelled) setSourceName(nextName)
            })
            .catch(() => {
                if (!cancelled) setSourceName(citation.sourceId)
            })

        return () => {
            cancelled = true
        }
    }, [citation.sourceId])

    return (
        <span className="citation-mark-wrapper" ref={wrapperRef}>
            <span
                className="citation-mark"
                onMouseEnter={show}
                onMouseLeave={hide}
            >
                {citation.index}
            </span>

            {renderCard(
                <>
                    <div className="citation-card-title">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                            <polyline points="14 2 14 8 20 8" />
                        </svg>
                        <span>{citation.title}</span>
                    </div>
                    <div className="citation-card-meta">
                        {sourceName ? (
                            <span className="citation-card-pill">{sourceName}</span>
                        ) : null}
                        {citation.chunkId ? (
                            <span className="citation-card-pill">Chunk {citation.chunkId}</span>
                        ) : null}
                        {citation.pageLabel ? (
                            <span className="citation-card-pill">Page {citation.pageLabel}</span>
                        ) : null}
                    </div>
                    {citation.snippet ? (
                        <div className="citation-card-snippet">{citation.snippet}</div>
                    ) : null}
                </>,
            )}
        </span>
    )
}
