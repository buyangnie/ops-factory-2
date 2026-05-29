import { useState, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import TypeCard from './TypeCard'
import TypeFormModal from './TypeFormModal'
import ListSearchInput from '../../../platform/ui/list/ListSearchInput'
import ListResultsMeta from '../../../platform/ui/list/ListResultsMeta'
import { useToast } from '../../../platform/providers/ToastContext'
import { useConfirmDialog } from '../../../platform/providers/ConfirmDialogContext'
import type { BusinessType } from '../../../../types/host'

type Props = {
    businessTypes: BusinessType[]
    loading: boolean
    onCreate: (body: Partial<BusinessType>) => Promise<BusinessType>
    onUpdate: (id: string, body: Partial<BusinessType>) => Promise<BusinessType>
    onDelete: (id: string) => Promise<boolean>
}

type FormData = {
    name: string
    code: string
    description: string
    color: string
    knowledge: string
}

const emptyForm: FormData = { name: '', code: '', description: '', color: '#6366f1', knowledge: '' }

export default function BusinessTypeTab({ businessTypes, loading, onCreate, onUpdate, onDelete }: Props) {
    const { t } = useTranslation()
    const { showToast } = useToast()
    const { requestConfirm } = useConfirmDialog()
    const [showModal, setShowModal] = useState(false)
    const [editing, setEditing] = useState<BusinessType | null>(null)
    const [form, setForm] = useState<FormData>(emptyForm)
    const [saving, setSaving] = useState(false)
    const [searchTerm, setSearchTerm] = useState('')

    const filteredTypes = useMemo(() => {
        if (!searchTerm.trim()) return businessTypes
        const term = searchTerm.toLowerCase()
        return businessTypes.filter(bt => bt.name.toLowerCase().includes(term))
    }, [businessTypes, searchTerm])

    const openCreate = useCallback(() => {
        setEditing(null)
        setForm(emptyForm)
        setShowModal(true)
    }, [])

    const openEdit = useCallback((item: BusinessType) => {
        setEditing(item)
        setForm({
            name: item.name,
            code: item.code,
            description: item.description,
            color: item.color,
            knowledge: item.knowledge,
        })
        setShowModal(true)
    }, [])

    const handleSave = useCallback(async () => {
        if (!form.name.trim() || !form.code.trim()) return
        setSaving(true)
        try {
            // Check for duplicate name
            const duplicateName = businessTypes.find(bt => bt.name === form.name && bt.id !== editing?.id)
            if (duplicateName) {
                showToast('error', t('hostResource.duplicateName', { name: form.name }))
                setSaving(false)
                return
            }

            // Check for duplicate code
            const duplicateCode = businessTypes.find(bt => bt.code === form.code && bt.id !== editing?.id)
            if (duplicateCode) {
                showToast('error', t('hostResource.duplicateCode', { code: form.code }))
                setSaving(false)
                return
            }

            if (editing) {
                await onUpdate(editing.id, form)
            } else {
                await onCreate(form)
            }
            setShowModal(false)
        } catch (err) {
            showToast('error', err instanceof Error ? err.message : 'Failed')
        } finally {
            setSaving(false)
        }
    }, [editing, form, businessTypes, onCreate, onUpdate, showToast, t])

    const handleDelete = useCallback(async (item: BusinessType) => {
        const confirmed = await requestConfirm({
            title: t('common.confirmTitle'),
            message: t('hostResource.confirmDeleteBusinessType'),
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
    }, [onDelete, t, requestConfirm, showToast])

    return (
        <div className="hr-type-tab-content">
            <div className="hr-type-tab-header">
                <span className="hr-type-tab-heading">
                    {t('hostResource.tabBusinessTypes')} ({businessTypes.length})
                </span>
                <button className="btn btn-primary btn-sm" onClick={openCreate}>
                    + {t('hostResource.createBusinessType')}
                </button>
            </div>

            {loading && (
                <div className="hr-empty">{t('common.loading')}</div>
            )}
            {!loading && businessTypes.length === 0 && (
                <div className="hr-type-tab-empty">
                    <div className="hr-type-tab-empty-text">{t('hostResource.noBusinessTypes')}</div>
                </div>
            )}
            {!loading && businessTypes.length > 0 && (
                <>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--spacing-3)', marginBottom: 'var(--spacing-3)' }}>
                        <ListSearchInput
                            value={searchTerm}
                            placeholder={t('hostResource.searchBusinessTypes')}
                            onChange={setSearchTerm}
                        />
                        {searchTerm && (
                            <ListResultsMeta>
                                {t('common.resultsFound', { count: filteredTypes.length })}
                            </ListResultsMeta>
                        )}
                    </div>
                    <div className="hr-type-def-grid">
                        {filteredTypes.map(bt => (
                            <TypeCard
                                key={bt.id}
                                item={bt}
                                onEdit={openEdit}
                                onDelete={handleDelete}
                            />
                        ))}
                    </div>
                </>
            )}

            {showModal && (
                <TypeFormModal
                    title={editing ? t('hostResource.editBusinessType') : t('hostResource.createBusinessType')}
                    form={form}
                    setForm={setForm}
                    saving={saving}
                    onSave={handleSave}
                    onClose={() => setShowModal(false)}
                />
            )}
        </div>
    )
}
