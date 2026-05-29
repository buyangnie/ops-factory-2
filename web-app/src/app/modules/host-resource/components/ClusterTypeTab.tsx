import { useState, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import TypeCard from './TypeCard'
import TypeFormModal from './TypeFormModal'
import ListSearchInput from '../../../platform/ui/list/ListSearchInput'
import ListResultsMeta from '../../../platform/ui/list/ListResultsMeta'
import { useToast } from '../../../platform/providers/ToastContext'
import { useConfirmDialog } from '../../../platform/providers/ConfirmDialogContext'
import type { ClusterType, Cluster } from '../../../../types/host'

type Props = {
    clusterTypes: ClusterType[]
    clusters: Cluster[]
    loading: boolean
    onCreate: (body: Partial<ClusterType>) => Promise<ClusterType>
    onUpdate: (id: string, body: Partial<ClusterType>) => Promise<ClusterType>
    onDelete: (id: string) => Promise<boolean>
}

type FormData = {
    name: string
    code: string
    description: string
    color: string
    knowledge: string
    commandPrefix: string
    envVariables: { key: string; value: string }[]
    mode: 'peer' | 'primary-backup'
}

const emptyForm: FormData = {
    name: '', code: '', description: '', color: '#10b981', knowledge: '',
    commandPrefix: '',
    envVariables: [],
    mode: 'peer',
}

export default function ClusterTypeTab({ clusterTypes, clusters, loading, onCreate, onUpdate, onDelete }: Props) {
    const { t } = useTranslation()
    const { showToast } = useToast()
    const { requestConfirm } = useConfirmDialog()
    const [showModal, setShowModal] = useState(false)
    const [editing, setEditing] = useState<ClusterType | null>(null)
    const [form, setForm] = useState<FormData>(emptyForm)
    const [saving, setSaving] = useState(false)
    const [searchTerm, setSearchTerm] = useState('')
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

    const filteredTypes = useMemo(() => {
        if (!searchTerm.trim()) return clusterTypes
        const term = searchTerm.toLowerCase()
        return clusterTypes.filter(ct => ct.name.toLowerCase().includes(term))
    }, [clusterTypes, searchTerm])

    const openCreate = useCallback(() => {
        setEditing(null)
        setForm(emptyForm)
        setShowModal(true)
    }, [])

    const openEdit = useCallback((item: ClusterType) => {
        setEditing(item)
        setForm({
            name: item.name,
            code: item.code,
            description: item.description,
            color: item.color,
            knowledge: item.knowledge,
            commandPrefix: item.commandPrefix ?? '',
            envVariables: item.envVariables ?? [],
            mode: item.mode ?? 'peer',
        })
        setShowModal(true)
    }, [])

    const handleSave = useCallback(async () => {
        if (!form.name.trim() || !form.code.trim()) return
        setSaving(true)
        try {
            const cleanedForm = {
                ...form,
                envVariables: form.envVariables.filter(ev => ev.key.trim() !== ''),
            }

            // Check for duplicate name
            const duplicateName = clusterTypes.find(ct => ct.name === form.name && ct.id !== editing?.id)
            if (duplicateName) {
                showToast('error', t('hostResource.duplicateName', { name: form.name }))
                setSaving(false)
                return
            }

            // Check for duplicate code
            const duplicateCode = clusterTypes.find(ct => ct.code === form.code && ct.id !== editing?.id)
            if (duplicateCode) {
                showToast('error', t('hostResource.duplicateCode', { code: form.code }))
                setSaving(false)
                return
            }

            if (editing) {
                const inUseByName = clusters.filter(c => c.type === form.name)
                // Only check code if it's not empty (empty string is not a valid type identifier)
                const inUseByCode = editing.code ? clusters.filter(c => c.type === editing.code) : []
                const nameChanged = form.name !== editing.name
                const codeChanged = form.code !== editing.code

                if (codeChanged && inUseByCode.length > 0) {
                    const inUseClusters = inUseByCode.map(c => c.name).join(', ')
                    showToast('warning', t('hostResource.clusterTypeInUse', { name: form.code, clusters: inUseClusters }))
                    setSaving(false)
                    return
                }

                if (nameChanged && inUseByName.length > 0) {
                    const inUseClusters = inUseByName.map(c => c.name).join(', ')
                    showToast('warning', t('hostResource.clusterTypeInUse', { name: form.name, clusters: inUseClusters }))
                    setSaving(false)
                    return
                }

                await onUpdate(editing.id, cleanedForm)
            } else {
                await onCreate(cleanedForm)
            }
            setShowModal(false)
        } catch (err) {
            showToast('error', err instanceof Error ? err.message : 'Failed')
        } finally {
            setSaving(false)
        }
    }, [editing, form, clusterTypes, clusters, onCreate, onUpdate, showToast, t])

    const handleDelete = useCallback(async (item: ClusterType) => {
        const inUseByName = clusters.filter(c => c.type === item.name)
        // Only check code if it's not empty (empty string is not a valid type identifier)
        const inUseByCode = item.code ? clusters.filter(c => c.type === item.code) : []
        if (inUseByName.length > 0 || inUseByCode.length > 0) {
            // Combine both sets of clusters, avoiding duplicates
            const allInUse = [...inUseByName, ...inUseByCode.filter(c => !inUseByName.includes(c))]
            const clusterNames = allInUse.map(c => c.name).join(', ')
            showToast('warning', t('hostResource.clusterTypeInUse', { name: item.name, clusters: clusterNames }))
            return
        }
        const confirmed = await requestConfirm({
            title: t('common.confirmTitle'),
            message: t('hostResource.confirmDeleteClusterType'),
            variant: 'danger',
            confirmLabel: t('common.delete'),
        })
        if (confirmed) {
            try {
                await onDelete(item.id)
            } catch (err) {
                showToast('error', err instanceof Error ? err.message : 'Failed')
            }
        }
    }, [clusters, onDelete, t, requestConfirm, showToast])

    const handleToggleSelect = useCallback((item: ClusterType) => {
        setSelectedIds(prev => {
            const newSet = new Set(prev)
            if (newSet.has(item.id)) {
                newSet.delete(item.id)
            } else {
                newSet.add(item.id)
            }
            return newSet
        })
    }, [])

    const handleSelectAll = useCallback(() => {
        if (selectedIds.size === filteredTypes.length) {
            setSelectedIds(new Set())
        } else {
            setSelectedIds(new Set(filteredTypes.map(ct => ct.id)))
        }
    }, [filteredTypes.length, selectedIds.size])

    const handleBatchDelete = useCallback(async () => {
        const selectedItems = clusterTypes.filter(ct => selectedIds.has(ct.id))
        if (selectedItems.length === 0) return

        const inUseItems: ClusterType[] = []
        for (const item of selectedItems) {
            const inUse = clusters.filter(c => c.type === item.name)
            if (inUse.length > 0) {
                inUseItems.push(item)
            }
        }
        if (inUseItems.length > 0) {
            const names = inUseItems.map(item => item.name).join(', ')
            showToast('warning', t('hostResource.clusterTypesInUseBatch', { names }))
            return
        }

        const confirmed = await requestConfirm({
            title: t('common.confirmTitle'),
            message: t('hostResource.confirmDeleteClusterTypes', { count: selectedItems.length }),
            variant: 'danger',
            confirmLabel: t('common.delete'),
        })

        if (!confirmed) return

        try {
            const results = await Promise.allSettled(
                Array.from(selectedIds).map(id => onDelete(id))
            )

            const successful = results.filter(r => r.status === 'fulfilled').length
            const failed = results.filter(r => r.status === 'rejected').length
            const totalCount = selectedIds.size

            if (failed > 0) {
                showToast('warning', t('hostResource.deletePartialResult', {
                    success: successful,
                    failed,
                    total: totalCount
                }))
            } else {
                showToast('success', t('hostResource.deleteSuccess', { count: totalCount }))
            }

            setSelectedIds(new Set())
        } catch (err) {
            showToast('error', err instanceof Error ? err.message : 'Failed')
        }
    }, [clusterTypes, clusters, selectedIds, onDelete, t, requestConfirm, showToast])

    const updateEnvVar = useCallback((index: number, field: 'key' | 'value', val: string) => {
        setForm(f => {
            const envVariables = [...f.envVariables]
            envVariables[index] = { ...envVariables[index], [field]: val }
            return { ...f, envVariables }
        })
    }, [])

    const removeEnvVar = useCallback((index: number) => {
        setForm(f => ({
            ...f,
            envVariables: f.envVariables.filter((_, i) => i !== index),
        }))
    }, [])

    const addEnvVar = useCallback(() => {
        setForm(f => ({
            ...f,
            envVariables: [...f.envVariables, { key: '', value: '' }],
        }))
    }, [])

    return (
        <div className="hr-type-tab-content">
            <div className="hr-type-tab-header">
                <span className="hr-type-tab-heading">
                    {t('hostResource.tabClusterTypes')} ({clusterTypes.length})
                </span>
                <button className="btn btn-primary btn-sm" onClick={openCreate}>
                    + {t('hostResource.createClusterType')}
                </button>
            </div>

            {loading && (
                <div className="hr-empty">{t('common.loading')}</div>
            )}
            {!loading && clusterTypes.length === 0 && (
                <div className="hr-type-tab-empty">
                    <div className="hr-type-tab-empty-text">{t('hostResource.noClusterTypes')}</div>
                </div>
            )}
            {!loading && clusterTypes.length > 0 && (
                <>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--spacing-3)', marginBottom: 'var(--spacing-3)' }}>
                        <ListSearchInput
                            value={searchTerm}
                            placeholder={t('hostResource.searchClusterTypes')}
                            onChange={setSearchTerm}
                        />
                        {searchTerm && (
                            <ListResultsMeta>
                                {t('common.resultsFound', { count: filteredTypes.length })}
                            </ListResultsMeta>
                        )}
                    </div>
                    {selectedIds.size > 0 && (
                        <div className="hr-batch-bar">
                            <input
                                type="checkbox"
                                checked={selectedIds.size === filteredTypes.length}
                                onChange={handleSelectAll}
                                className="hr-batch-bar-checkbox"
                            />
                            <span className="hr-batch-bar-label">
                                {selectedIds.size > 0 ? t('common.selectedCount', { count: selectedIds.size }) : t('common.selectAll')}
                            </span>
                            <div className="hr-batch-bar-spacer" />
                            <button className="btn btn-secondary btn-sm" onClick={() => setSelectedIds(new Set())}>
                                {t('common.cancel')}
                            </button>
                            <button
                                className="btn btn-primary btn-sm hr-batch-bar-delete"
                                onClick={handleBatchDelete}
                            >
                                {t('common.delete')} ({selectedIds.size})
                            </button>
                        </div>
                    )}
                    <div className="hr-type-def-grid">
                        {filteredTypes.map(ct => (
                            <TypeCard
                                key={ct.id}
                                item={ct}
                                selected={selectedIds.has(ct.id)}
                                onSelect={handleToggleSelect}
                                onEdit={openEdit}
                                onDelete={handleDelete}
                            />
                        ))}
                    </div>
                </>
            )}

            {showModal && (
                <TypeFormModal
                    title={editing ? t('hostResource.editClusterType') : t('hostResource.createClusterType')}
                    form={form}
                    setForm={setForm}
                    saving={saving}
                    onSave={handleSave}
                    onClose={() => setShowModal(false)}
                    extraFields={
                        <>
                            <div className="form-group">
                                <label className="form-label">{t('hostResource.clusterMode')}</label>
                                <select
                                    className="form-input"
                                    value={form.mode}
                                    onChange={e => setForm(f => ({ ...f, mode: e.target.value as 'peer' | 'primary-backup' }))}
                                >
                                    <option value="peer">{t('hostResource.clusterModePeer')}</option>
                                    <option value="primary-backup">{t('hostResource.clusterModePrimaryBackup')}</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('hostResource.commandPrefix')}</label>
                                <input
                                    className="form-input"
                                    value={form.commandPrefix}
                                    onChange={e => setForm(f => ({ ...f, commandPrefix: e.target.value }))}
                                    placeholder={t('hostResource.commandPrefixPlaceholder')}
                                />
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('hostResource.envVariables')}</label>
                                {form.envVariables.map((ev, i) => (
                                    <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 4 }}>
                                        <input
                                            className="form-input"
                                            value={ev.key}
                                            placeholder={t('hostResource.varKey')}
                                            onChange={e => updateEnvVar(i, 'key', e.target.value)}
                                            style={{ flex: 1 }}
                                        />
                                        <input
                                            className="form-input"
                                            value={ev.value}
                                            placeholder={t('hostResource.varValue')}
                                            onChange={e => updateEnvVar(i, 'value', e.target.value)}
                                            style={{ flex: 1 }}
                                        />
                                        <button className="btn btn-secondary btn-sm" onClick={() => removeEnvVar(i)}>×</button>
                                    </div>
                                ))}
                                <button className="btn btn-secondary btn-sm" onClick={addEnvVar}>
                                    + {t('hostResource.addEnvVar')}
                                </button>
                            </div>
                        </>
                    }
                />
            )}
        </div>
    )
}
