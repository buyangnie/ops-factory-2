import { useTranslation } from 'react-i18next'
import { Radar } from 'lucide-react'
import type { Host, Cluster } from '../../../../types/host'
import {
    ResourceCardAction,
    ResourceCardActionGroup,
    ResourceCardDeleteAction,
    ResourceCardEditAction,
} from '../../../platform/ui/primitives/ResourceCard'

function TestResultIcon({ ok }: { ok: boolean }) {
    return ok ? (
        <svg viewBox="0 0 20 20" fill="none" width="14" height="14" aria-hidden="true">
            <path
                d="m5.75 10.2 2.45 2.45 6.05-6.05"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    ) : (
        <svg viewBox="0 0 20 20" fill="none" width="14" height="14" aria-hidden="true">
            <path
                d="M10 6.2v4.3"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
            />
            <path
                d="M10 13.4h.01"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
            />
            <path
                d="M10 17a7 7 0 1 0 0-14 7 7 0 0 0 0 14Z"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    )
}

type Props = {
    host: Host
    cluster?: Cluster
    selected?: boolean
    testing?: boolean
    testResult?: { ok: boolean; msg: string } | null
    onClick: () => void
    onEdit: () => void
    onDelete: () => void
    onTest?: () => void
}

export default function HostCard({ host, cluster, selected, testing, testResult, onClick, onEdit, onDelete, onTest }: Props) {
    const { t } = useTranslation()
    const roleLabel = (() => {
        if (host.role === 'primary') return t('hostResource.hostRolePrimary')
        if (host.role === 'backup') return t('hostResource.hostRoleBackup')
        return null
    })()
    const testActionLabel = testing ? t('remoteDiagnosis.hosts.testing') : t('remoteDiagnosis.hosts.testConnection')

    return (
        <div
            className={`hr-host-card ${selected ? 'hr-host-card-selected' : ''}`}
            onClick={onClick}
        >
            <div className="hr-host-card-header">
                <div className="hr-host-card-title-block">
                    <div className="hr-host-card-tags">
                        {roleLabel && (
                            <span className={`hr-host-card-tag hr-host-card-tag-role hr-host-card-tag-role-${host.role}`}>
                                {roleLabel}
                            </span>
                        )}
                    </div>
                    <div className="hr-host-card-title-line">
                        <h3 className="hr-host-card-name">{host.name}</h3>
                        {host.description && (
                            <p className="hr-host-card-desc">{host.description}</p>
                        )}
                    </div>
                </div>
            </div>

            <div className="hr-host-card-meta">
                <div className="hr-host-card-meta-field">
                    <span className="hr-host-card-meta-label">{t('hostResource.ipPort')}</span>
                    <span className="hr-host-card-meta-value hr-host-card-address-row">
                        <span className="hr-host-card-mono">{host.ip}:{host.port}</span>
                        {testResult && (
                            <button
                                type="button"
                                className={`hr-host-card-result-trigger ${testResult.ok ? 'hr-host-card-result-trigger-ok' : 'hr-host-card-result-trigger-fail'}`}
                                title={testResult.msg}
                                aria-label={testResult.msg}
                                onClick={event => event.stopPropagation()}
                            >
                                <TestResultIcon ok={testResult.ok} />
                            </button>
                        )}
                    </span>
                </div>
                {host.businessIp && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.businessIp')}</span>
                        <span className="hr-host-card-meta-value hr-host-card-mono">{host.businessIp}</span>
                    </div>
                )}
                <div className="hr-host-card-meta-field">
                    <span className="hr-host-card-meta-label">{t('hostResource.username')}</span>
                    <span className="hr-host-card-meta-value">{host.username}</span>
                </div>
                {host.location && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.location')}</span>
                        <span className="hr-host-card-meta-value">{host.location}</span>
                    </div>
                )}
                {host.business && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.business')}</span>
                        <span className="hr-host-card-meta-value">{host.business}</span>
                    </div>
                )}
                {cluster && (
                    <div className="hr-host-card-meta-field">
                        <span className="hr-host-card-meta-label">{t('hostResource.clusterName')}</span>
                        <span className="hr-host-card-meta-value">{cluster.name}</span>
                    </div>
                )}
            </div>

            <ResourceCardActionGroup className="hr-host-card-footer" onClick={e => e.stopPropagation()}>
                {onTest && (
                    <ResourceCardAction
                        icon={Radar}
                        onClick={onTest}
                        disabled={testing}
                        label={testActionLabel}
                        tone="success"
                    />
                )}
                <ResourceCardEditAction
                    onClick={onEdit}
                    label={t('common.edit')}
                />
                <ResourceCardDeleteAction
                    onClick={onDelete}
                    label={t('common.delete')}
                />
            </ResourceCardActionGroup>
        </div>
    )
}
