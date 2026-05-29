/**
 * Shared validation utilities for model configuration fields.
 * All fields are stored as strings in AgentModelConfig.
 */

export function validateContextLimit(value: string): 'format' | 'range' | null {
    const trimmed = value.trim()
    if (!trimmed) return null

    if (!/^[1-9]\d*$/.test(trimmed)) {
        return 'format'
    }

    const num = parseInt(trimmed, 10)
    if (num < 1 || num > 2000000) {
        return 'range'
    }

    return null
}

export function validateTemperature(value: string): 'format' | 'range' | null {
    const trimmed = value.trim()
    if (!trimmed) return null

    if (!/^\d*\.?\d+$/.test(trimmed)) {
        return 'format'
    }

    const num = parseFloat(trimmed)
    if (isNaN(num) || num < 0 || num > 1) {
        return 'range'
    }

    return null
}

export function validateMaxTokens(value: string): 'format' | 'range' | null {
    const trimmed = value.trim()
    if (!trimmed) return null

    if (!/^[1-9]\d*$/.test(trimmed)) {
        return 'format'
    }

    const num = parseInt(trimmed, 10)
    if (num < 1 || num > 128000) {
        return 'range'
    }

    return null
}

export const VALID_CONTEXT_STRATEGIES = ['summarize', 'truncate', 'clear', 'prompt'] as const
export type ContextStrategy = typeof VALID_CONTEXT_STRATEGIES[number]

export function validateContextStrategy(value: string): 'invalid' | null {
    const trimmed = value.trim()
    if (!trimmed) return null

    if (!VALID_CONTEXT_STRATEGIES.includes(trimmed as ContextStrategy)) {
        return 'invalid'
    }

    return null
}

export function validateAutoCompactThreshold(value: string): 'format' | 'range' | null {
    const trimmed = value.trim()
    if (!trimmed) return null

    if (!/^\d*\.?\d+$/.test(trimmed)) {
        return 'format'
    }

    const num = parseFloat(trimmed)
    if (isNaN(num) || num < 0 || num > 1) {
        return 'range'
    }

    return null
}

export function validateMaxTurns(value: string): 'format' | 'range' | null {
    const trimmed = value.trim()
    if (!trimmed) return null

    if (!/^[1-9]\d*$/.test(trimmed)) {
        return 'format'
    }

    const num = parseInt(trimmed, 10)
    if (num < 1 || num > 10000) {
        return 'range'
    }

    return null
}

export function removeLeadingZeros(value: string): string {
    const trimmed = value.trim()
    if (!trimmed) return ''
    if (trimmed === '0') return '0'
    return trimmed.replace(/^0+/, '') || '0'
}
