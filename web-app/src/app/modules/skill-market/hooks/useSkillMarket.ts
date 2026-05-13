import { useCallback, useState } from 'react'
import type { SkillMarketDetail, SkillMarketEntry, SkillMarketListResponse, SkillMarketMutationResponse } from '../../../../types/skillMarket'
import { runtime, gatewayHeaders } from '../../../../config/runtime'
import { getErrorMessage } from '../../../../utils/errorMessages'
import { useUser } from '../../../platform/providers/UserContext'

interface ApiErrorBody {
    code?: string
    message?: string
    error?: string
}

interface UseSkillMarketResult {
    skills: SkillMarketEntry[]
    isLoading: boolean
    error: string | null
    fetchSkills: (query?: string) => Promise<void>
    fetchSkill: (skillId: string) => Promise<{ success: boolean; skill?: SkillMarketDetail; error?: string }>
    createSkill: (payload: { id: string; name: string; description: string; instructions: string }) => Promise<{ success: boolean; error?: string }>
    updateSkill: (skillId: string, payload: { name: string; description: string; instructions: string }) => Promise<{ success: boolean; error?: string }>
    importSkill: (file: File, id?: string) => Promise<{ success: boolean; error?: string }>
    deleteSkill: (skillId: string) => Promise<{ success: boolean; error?: string }>
    installSkill: (agentId: string, skillId: string) => Promise<{ success: boolean; error?: string }>
}

export function useSkillMarket(): UseSkillMarketResult {
    const { userId } = useUser()
    const [skills, setSkills] = useState<SkillMarketEntry[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchSkills = useCallback(async (query = '') => {
        setIsLoading(true)
        setError(null)
        try {
            const params = query.trim() ? `?q=${encodeURIComponent(query.trim())}` : ''
            const response = await fetch(`${runtime.SKILL_MARKET_SERVICE_URL}/skills${params}`, {
                signal: AbortSignal.timeout(10000),
            })
            if (!response.ok) throw new Error(await response.text())
            const data = await response.json() as SkillMarketListResponse
            setSkills(data.items || [])
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setIsLoading(false)
        }
    }, [])

    const createSkill = useCallback(async (payload: { id: string; name: string; description: string; instructions: string }) => {
        try {
            const response = await fetch(`${runtime.SKILL_MARKET_SERVICE_URL}/skills`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            })
            if (!response.ok) throw new Error(await response.text())
            await response.json() as SkillMarketMutationResponse
            await fetchSkills()
            return { success: true }
        } catch (err) {
            if (err instanceof Error && err.message === 'SKILL_ALREADY_EXISTS') {
                return { success: false, error: 'SKILL_ALREADY_EXISTS' }
            }
            return { success: false, error: getErrorMessage(err) }
        }
    }, [fetchSkills])

    const fetchSkill = useCallback(async (skillId: string) => {
        try {
            const response = await fetch(`${runtime.SKILL_MARKET_SERVICE_URL}/skills/${encodeURIComponent(skillId)}`, {
                signal: AbortSignal.timeout(10000),
            })
            if (!response.ok) throw new Error(await response.text())
            const skill = await response.json() as SkillMarketDetail
            return { success: true, skill }
        } catch (err) {
            return { success: false, error: getErrorMessage(err) }
        }
    }, [])

    const updateSkill = useCallback(async (skillId: string, payload: { name: string; description: string; instructions: string }) => {
        try {
            const response = await fetch(`${runtime.SKILL_MARKET_SERVICE_URL}/skills/${encodeURIComponent(skillId)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            })
            if (!response.ok) throw new Error(await response.text())
            await response.json() as SkillMarketMutationResponse
            await fetchSkills()
            return { success: true }
        } catch (err) {
            return { success: false, error: getErrorMessage(err) }
        }
    }, [fetchSkills])

    const importSkill = useCallback(async (file: File, id?: string) => {
        try {
            const formData = new FormData()
            formData.append('file', file)
            if (id?.trim()) formData.append('id', id.trim())
            const response = await fetch(`${runtime.SKILL_MARKET_SERVICE_URL}/skills:import`, {
                method: 'POST',
                body: formData,
            })
            if (!response.ok) {
                const text = await response.text()
                let message = text
                try {
                    const body = JSON.parse(text) as ApiErrorBody
                    message = body.message || body.error || text
                    if (body.code === 'SKILL_ALREADY_EXISTS') {
                        message = 'SKILL_ALREADY_EXISTS'
                    }
                } catch {
                    // Keep raw response text.
                }
                throw new Error(message)
            }
            await response.json() as SkillMarketMutationResponse
            await fetchSkills()
            return { success: true }
        } catch (err) {
            return { success: false, error: getErrorMessage(err) }
        }
    }, [fetchSkills])

    const deleteSkill = useCallback(async (skillId: string) => {
        try {
            const response = await fetch(`${runtime.SKILL_MARKET_SERVICE_URL}/skills/${encodeURIComponent(skillId)}`, {
                method: 'DELETE',
            })
            if (!response.ok) throw new Error(await response.text())
            await fetchSkills()
            return { success: true }
        } catch (err) {
            return { success: false, error: getErrorMessage(err) }
        }
    }, [fetchSkills])

    const installSkill = useCallback(async (agentId: string, skillId: string) => {
        try {
            const response = await fetch(`${runtime.GATEWAY_URL}/agents/${encodeURIComponent(agentId)}/skills/install`, {
                method: 'POST',
                headers: gatewayHeaders(userId),
                body: JSON.stringify({ skillId }),
            })
            if (!response.ok) throw new Error(await response.text())
            return { success: true }
        } catch (err) {
            return { success: false, error: getErrorMessage(err) }
        }
    }, [userId])

    return {
        skills,
        isLoading,
        error,
        fetchSkills,
        fetchSkill,
        createSkill,
        updateSkill,
        importSkill,
        deleteSkill,
        installSkill,
    }
}
