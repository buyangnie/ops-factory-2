import { useState, useRef, useEffect, useCallback, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import type { ReactNode } from 'react'

export interface CardStyle {
    left: number
    top: number
    width: number
}

export interface UseCitationCardReturn {
    showCard: boolean
    cardPosition: 'above' | 'below'
    cardStyle: CardStyle | null
    markRef: React.RefObject<HTMLSpanElement | null>
    cardRef: React.RefObject<HTMLDivElement | null>
    show: () => void
    hide: () => void
    renderCard: (children: ReactNode) => ReactNode
}

export function useCitationCard(dependency: unknown): UseCitationCardReturn {
    const [showCard, setShowCard] = useState(false)
    const [cardPosition, setCardPosition] = useState<'above' | 'below'>('above')
    const [cardStyle, setCardStyle] = useState<CardStyle | null>(null)
    const markRef = useRef<HTMLSpanElement>(null)
    const cardRef = useRef<HTMLDivElement>(null)
    const showTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
    const hideTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

    const updateCardPosition = useCallback((cardHeight = 0) => {
        if (!markRef.current) return

        const rect = markRef.current.getBoundingClientRect()
        const viewportPadding = 16
        const gap = 8
        const cardWidth = Math.min(360, window.innerWidth - viewportPadding * 2)
        const centerLeft = rect.left + rect.width / 2 - cardWidth / 2
        const left = Math.max(viewportPadding, Math.min(centerLeft, window.innerWidth - viewportPadding - cardWidth))
        const spaceAbove = rect.top - viewportPadding
        const spaceBelow = window.innerHeight - rect.bottom - viewportPadding
        const shouldShowBelow = cardHeight > 0
            ? (spaceAbove < cardHeight + gap && spaceBelow > spaceAbove)
            : rect.top < 200
        const top = shouldShowBelow
            ? Math.min(rect.bottom + gap, window.innerHeight - viewportPadding - Math.max(cardHeight, 120))
            : Math.max(viewportPadding, rect.top - gap - cardHeight)

        setCardPosition(shouldShowBelow ? 'below' : 'above')
        setCardStyle({ left, top, width: cardWidth })
    }, [])

    const show = useCallback(() => {
        clearTimeout(hideTimeout.current)
        showTimeout.current = setTimeout(() => {
            updateCardPosition()
            setShowCard(true)
        }, 200)
    }, [updateCardPosition])

    const hide = useCallback(() => {
        clearTimeout(showTimeout.current)
        hideTimeout.current = setTimeout(() => setShowCard(false), 150)
    }, [])

    useEffect(() => {
        if (!showCard) return

        const handleViewportChange = () => updateCardPosition(cardRef.current?.offsetHeight || 0)
        window.addEventListener('scroll', handleViewportChange, true)
        window.addEventListener('resize', handleViewportChange)

        return () => {
            window.removeEventListener('scroll', handleViewportChange, true)
            window.removeEventListener('resize', handleViewportChange)
        }
    }, [showCard, updateCardPosition])

    useLayoutEffect(() => {
        if (!showCard || !cardRef.current) return
        updateCardPosition(cardRef.current.offsetHeight)
    }, [showCard, dependency, updateCardPosition])

    useEffect(() => {
        return () => {
            clearTimeout(showTimeout.current)
            clearTimeout(hideTimeout.current)
        }
    }, [])

    const renderCard = useCallback((children: ReactNode) => {
        if (!showCard || !cardStyle) return null
        return createPortal(
            <div
                ref={cardRef}
                className={`citation-card ${cardPosition} is-portal`}
                style={{
                    left: `${cardStyle.left}px`,
                    width: `${cardStyle.width}px`,
                    top: `${cardStyle.top}px`,
                }}
                onMouseEnter={show}
                onMouseLeave={hide}
            >
                {children}
            </div>,
            document.body,
        )
    }, [showCard, cardStyle, cardPosition, show, hide])

    return {
        showCard,
        cardPosition,
        cardStyle,
        markRef,
        cardRef,
        show,
        hide,
        renderCard,
    }
}
