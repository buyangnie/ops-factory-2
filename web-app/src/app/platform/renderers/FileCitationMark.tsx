import type { RefObject } from 'react'
import type { FileCitation } from '../../../utils/fileCitationParser'
import { getFileCitationDisplayPath } from '../../../utils/fileCitation'
import { useCitationCard } from './useCitationCard'
import './CitationMark.css'

interface FileCitationMarkProps {
    citation: FileCitation
}

function getFileName(filePath: string): string {
    const segments = filePath.split(/[\\/]/).filter(Boolean)
    return segments[segments.length - 1] || filePath
}

function getLineLabel(citation: FileCitation): string | null {
    if (citation.lineFrom == null && citation.lineTo == null) return null
    if (citation.lineFrom != null && citation.lineTo != null) {
        return citation.lineFrom === citation.lineTo
            ? `Line ${citation.lineFrom}`
            : `Lines ${citation.lineFrom}-${citation.lineTo}`
    }
    return `Line ${citation.lineFrom ?? citation.lineTo}`
}

export default function FileCitationMark({ citation }: FileCitationMarkProps) {
    const { markRef, show, hide, renderCard } = useCitationCard(citation)
    const lineLabel = getLineLabel(citation)
    const wrapperRef = markRef as RefObject<HTMLSpanElement>

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
                        <span>{getFileName(citation.path)}</span>
                    </div>
                    <div className="citation-card-meta">
                        {lineLabel ? (
                            <span className="citation-card-pill">{lineLabel}</span>
                        ) : null}
                    </div>
                    <div className="citation-card-link">{getFileCitationDisplayPath(citation.path)}</div>
                    {citation.snippet ? (
                        <div className="citation-card-snippet">{citation.snippet}</div>
                    ) : null}
                </>,
            )}
        </span>
    )
}
