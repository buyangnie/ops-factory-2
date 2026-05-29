import { type RefObject, useEffect } from 'react'

type ScrollTarget<T extends HTMLElement> = RefObject<T | null> | (() => T | null)

export function useScrollClass<T extends HTMLElement>(
    target: ScrollTarget<T>,
    className = 'is-scrolling',
    deps: unknown[] = [],
    eventTarget?: () => EventTarget | null
) {
    useEffect(() => {
        const el = typeof target === 'function' ? target() : target.current
        if (!el) return
        const scrollTarget = eventTarget?.() ?? el

        let timeoutId: number | undefined
        const handleScroll = () => {
            el.classList.add(className)
            if (timeoutId) {
                window.clearTimeout(timeoutId)
            }
            timeoutId = window.setTimeout(() => {
                el.classList.remove(className)
            }, 800)
        }

        scrollTarget.addEventListener('scroll', handleScroll, { passive: true })
        return () => {
            scrollTarget.removeEventListener('scroll', handleScroll)
            el.classList.remove(className)
            if (timeoutId) {
                window.clearTimeout(timeoutId)
            }
        }
    }, [target, className, eventTarget, ...deps])
}
