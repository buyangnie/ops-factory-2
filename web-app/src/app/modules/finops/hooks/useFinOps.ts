import { useCallback, useEffect, useState } from 'react'
import { fetchFinOpsOverview, refreshFinOpsSnapshot, type OverviewResponse } from '../../../../services/finopsAPI'

export function useFinOps() {
    const [data, setData] = useState<OverviewResponse | null>(null)
    const [loading, setLoading] = useState(true)
    const [refreshing, setRefreshing] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const load = useCallback(async () => {
        setLoading(true)
        setError(null)
        try {
            setData(await fetchFinOpsOverview())
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err))
        } finally {
            setLoading(false)
        }
    }, [])

    const refresh = useCallback(async () => {
        setRefreshing(true)
        setError(null)
        try {
            await refreshFinOpsSnapshot()
            setData(await fetchFinOpsOverview())
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err))
        } finally {
            setRefreshing(false)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    return { data, loading, refreshing, error, reload: load, refresh }
}
