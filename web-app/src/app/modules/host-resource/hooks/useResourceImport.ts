import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { readXlsxFile, parseSheetToObjects } from '../../../../utils/xlsxHelper'
import { validateSheetStructure } from '../../../../utils/xlsxValidator'
import { isValidIp } from '../../../../utils/ip-validation'
import { validateAndSanitize } from '../../../../utils/inputValidation'
import { IMPORT_METADATA } from '../../../../utils/importExportMetadata'
import type { HostGroup, Cluster, Host, HostCreateRequest, BusinessService, ClusterType, BusinessType, HostRelation } from '../../../../types/host'
import type { SopCreateRequest } from '../../../../types/sop'
import type { WhitelistCommand } from '../../../../types/commandWhitelist'

export type ImportType =
    | 'ClusterTypes'
    | 'BusinessTypes'
    | 'HostGroups'
    | 'Clusters'
    | 'Hosts'
    | 'BusinessServices'
    | 'Relations'
    | 'SOPs'
    | 'Whitelist'

export interface ImportError {
    row: number
    code: string
    params?: Record<string, string>
}

export interface ImportProgress {
    current: number
    total: number
    phase: string
}

export interface ImportResult {
    success: number
    failed: number
    errors: ImportError[]
}

interface ImportDeps {
    fetchGroups: () => Promise<void>
    fetchAllClusters: () => Promise<void>
    fetchAllHosts: () => Promise<void>
    fetchHostRelations: () => Promise<void>
    fetchBusinessServices: () => Promise<void>
    fetchGraph: (clusterId?: string, groupId?: string) => Promise<void>
    fetchWhitelist: () => Promise<void>

    groups: HostGroup[]
    clusters: Cluster[]
    allHosts: Host[]
    businessServices: BusinessService[]
    relations: HostRelation[]
    clusterTypes: ClusterType[]
    businessTypes: BusinessType[]

    createGroup: (body: Partial<HostGroup>) => Promise<HostGroup>
    updateGroup: (id: string, body: Partial<HostGroup>) => Promise<HostGroup>
    createCluster: (body: Partial<Cluster>) => Promise<Cluster>
    createHost: (body: HostCreateRequest) => Promise<Host>
    createBusinessService: (body: Partial<BusinessService>) => Promise<BusinessService>
    createRelation: (body: Partial<HostRelation>) => Promise<unknown>
    createClusterType: (body: Partial<ClusterType>) => Promise<unknown>
    createBusinessType: (body: Partial<BusinessType>) => Promise<unknown>
    createSop: (body: SopCreateRequest) => Promise<unknown>
    addWhitelistCommand: (cmd: WhitelistCommand) => Promise<boolean>
}

export function useResourceImport(deps: ImportDeps) {
    const { t } = useTranslation()
    const [importing, setImporting] = useState(false)
    const [progress, setProgress] = useState<ImportProgress | null>(null)

    const importXlsx = useCallback(async (type: ImportType, file: File): Promise<ImportResult> => {
        try {
            const workbook = await readXlsxFile(file)
            const structureValidation = validateSheetStructure(workbook, type)

            if (!structureValidation.valid) {
                const sheetError = structureValidation.sheetErrors[0]
                return {
                    success: 0,
                    failed: 0,
                    errors: [{
                        row: 0,
                        code: 'import.invalidFile',
                        params: { message: t('hostResource.importErrorInvalidFile', { message: sheetError.params ? JSON.stringify(sheetError.params) : '' }) }
                    }]
                }
            }

            const parseResult = parseSheetToObjects(workbook, IMPORT_METADATA[type].sheetName)
            if (!parseResult.success) {
                return {
                    success: 0,
                    failed: 0,
                    errors: [{
                        row: 0,
                        code: 'import.parseError',
                        params: { message: t('hostResource.importErrorParseError', { message: parseResult.error || 'Unknown parse error' }) }
                    }]
                }
            }

            const rows = parseResult.data || []
            if (rows.length === 0) {
                return { success: 0, failed: 0, errors: [{ row: 0, code: 'import.noDataRows' }] }
            }

            setImporting(true)
            setProgress({ current: 0, total: rows.length, phase: type })

            try {
                const errors: ImportError[] = []
                let success = 0

                const groupNameToId = new Map(deps.groups.map(g => [g.name, g.id]))
                const groupCodeToId = new Map(deps.groups.map(g => [g.code ?? '', g.id]))
                const clusterGroupedKeyToId = new Map(deps.clusters.map(c => [`${c.groupId ?? ''}:${c.name}`, c.id]))
                const clusterNameToId = new Map(deps.clusters.map(c => [c.name, c.id]))
                const clusterTypeNameSet = new Set(deps.clusterTypes.map(ct => ct.name))
                const clusterTypeCodeToName = new Map(deps.clusterTypes.map(ct => [ct.code, ct.name]))
                const businessTypeNameToId = new Map(deps.businessTypes.map(bt => [bt.name, bt.id]))
                const hostNameToId = new Map(deps.allHosts.map(h => [h.name, h.id]))
                const bsNameToId = new Map(deps.businessServices.map(bs => [bs.name, bs.id]))
                const existingRelationKeys = new Set(deps.relations.map(r => `${r.sourceHostId}->${r.targetHostId}`))

                const createdClusterTypeNames = new Set(deps.clusterTypes.map(ct => ct.name))
                const createdBusinessTypeNames = new Set(deps.businessTypes.map(bt => bt.name))
                const createdSopNames = new Set<string>()
                const createdPatterns = new Set<string>()

                for (let i = 0; i < rows.length; i++) {
                    const row = rows[i]
                    setProgress({ current: i + 1, total: rows.length, phase: type })

                    try {
                        switch (type) {
                            case 'ClusterTypes': {
                                if (createdClusterTypeNames.has(row.name)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'ClusterType', name: row.name } })
                                    continue
                                }
                                await deps.createClusterType({
                                    name: row.name,
                                    code: row.code,
                                    description: row.description || '',
                                    knowledge: row.knowledge || '',
                                    commandPrefix: row.commandPrefix || '',
                                    envVariables: row.envVariables
                                        ? row.envVariables.split(';').filter(Boolean).map(pair => {
                                            const eq = pair.indexOf('=')
                                            return { key: eq > 0 ? pair.slice(0, eq) : pair, value: eq > 0 ? pair.slice(eq + 1) : '' }
                                        })
                                        : undefined,
                                })
                                createdClusterTypeNames.add(row.name)
                                success++
                                break
                            }

                            case 'BusinessTypes': {
                                if (createdBusinessTypeNames.has(row.name)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'BusinessType', name: row.name } })
                                    continue
                                }
                                await deps.createBusinessType({
                                    name: row.name,
                                    code: row.code,
                                    description: row.description || '',
                                    knowledge: row.knowledge || '',
                                })
                                createdBusinessTypeNames.add(row.name)
                                success++
                                break
                            }

                            case 'HostGroups': {
                                const nameResult = validateAndSanitize(row.name, 'Name')
                                if (!nameResult.valid) {
                                    errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Name' } })
                                    continue
                                }
                                if (row.code) {
                                    const codeResult = validateAndSanitize(row.code, 'Code')
                                    if (!codeResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Code' } })
                                        continue
                                    }
                                }
                                if (row.description) {
                                    const descResult = validateAndSanitize(row.description, 'Description')
                                    if (!descResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Description' } })
                                        continue
                                    }
                                }
                                if (groupNameToId.has(row.name)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'HostGroup', name: row.name } })
                                    continue
                                }
                                const created = await deps.createGroup({
                                    name: nameResult.sanitized,
                                    code: row.code ? validateAndSanitize(row.code, 'Code').sanitized : undefined,
                                    description: row.description ? validateAndSanitize(row.description, 'Description').sanitized : '',
                                })
                                groupNameToId.set(row.name, created.id)
                                if (row.code) groupCodeToId.set(row.code, created.id)
                                success++
                                break
                            }

                            case 'Clusters': {
                                const nameResult = validateAndSanitize(row.name, 'Name')
                                if (!nameResult.valid) {
                                    errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Name' } })
                                    continue
                                }
                                if (row.type) {
                                    const typeResult = validateAndSanitize(row.type, 'Type')
                                    if (!typeResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Type' } })
                                        continue
                                    }
                                }
                                if (row.purpose) {
                                    const purposeResult = validateAndSanitize(row.purpose, 'Purpose')
                                    if (!purposeResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Purpose' } })
                                        continue
                                    }
                                }
                                if (row.description) {
                                    const descResult = validateAndSanitize(row.description, 'Description')
                                    if (!descResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Description' } })
                                        continue
                                    }
                                }
                                const groupId = row.group
                                    ? (groupNameToId.get(row.group) || groupCodeToId.get(row.group))
                                    : undefined
                                const clusterKey = `${groupId ?? ''}:${row.name}`
                                if (clusterGroupedKeyToId.has(clusterKey)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'Cluster', name: row.name } })
                                    continue
                                }
                                let typeName = row.type || ''
                                if (typeName && !clusterTypeNameSet.has(typeName)) {
                                    typeName = clusterTypeCodeToName.get(typeName) || typeName
                                }
                                if (!groupId && row.group) {
                                    errors.push({ row: i + 2, code: 'import.groupNotFound', params: { group: row.group } })
                                    continue
                                }
                                const created = await deps.createCluster({
                                    name: nameResult.sanitized,
                                    type: typeName,
                                    purpose: row.purpose ? validateAndSanitize(row.purpose, 'Purpose').sanitized : '',
                                    groupId,
                                    description: row.description ? validateAndSanitize(row.description, 'Description').sanitized : '',
                                })
                                clusterGroupedKeyToId.set(clusterKey, created.id)
                                clusterNameToId.set(row.name, created.id)
                                success++
                                break
                            }

                            case 'Hosts': {
                                const hostName = row.name?.trim() || ''
                                const hostIp = row.ip?.trim() || ''
                                const hostUsername = row.username?.trim() || ''
                                if (!hostName) {
                                    errors.push({ row: i + 2, code: 'import.hostNameRequired' })
                                    continue
                                }
                                const nameResult = validateAndSanitize(hostName, 'Name')
                                if (!nameResult.valid) {
                                    errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Name' } })
                                    continue
                                }
                                if (hostName.length > 100) {
                                    errors.push({ row: i + 2, code: 'import.hostNameTooLong', params: { length: String(hostName.length) } })
                                    continue
                                }
                                if (!hostIp) {
                                    errors.push({ row: i + 2, code: 'import.hostIpRequired' })
                                    continue
                                }
                                if (!isValidIp(hostIp)) {
                                    errors.push({ row: i + 2, code: 'import.hostIpInvalid', params: { ip: hostIp } })
                                    continue
                                }
                                if (row.businessIp && !isValidIp(row.businessIp)) {
                                    errors.push({ row: i + 2, code: 'import.businessIpInvalid', params: { ip: row.businessIp } })
                                    continue
                                }
                                if (!hostUsername) {
                                    errors.push({ row: i + 2, code: 'import.hostUsernameRequired' })
                                    continue
                                }
                                if (hostUsername && !/^[\x00-\x7F]*$/.test(hostUsername)) {
                                    errors.push({ row: i + 2, code: 'import.usernameInvalidChars' })
                                    continue
                                }
                                if (row.credential && row.credential !== '***' && !/^[\x00-\x7F]*$/.test(row.credential)) {
                                    errors.push({ row: i + 2, code: 'import.credentialInvalidChars' })
                                    continue
                                }
                                if (row.hostname) {
                                    const hostnameResult = validateAndSanitize(row.hostname, 'Hostname')
                                    if (!hostnameResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Hostname' } })
                                        continue
                                    }
                                }
                                if (row.os) {
                                    const osResult = validateAndSanitize(row.os, 'OS')
                                    if (!osResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'OS' } })
                                        continue
                                    }
                                }
                                if (row.location) {
                                    const locationResult = validateAndSanitize(row.location, 'Location')
                                    if (!locationResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Location' } })
                                        continue
                                    }
                                }
                                if (row.purpose) {
                                    const purposeResult = validateAndSanitize(row.purpose, 'Purpose')
                                    if (!purposeResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Purpose' } })
                                        continue
                                    }
                                }
                                if (row.business) {
                                    const businessResult = validateAndSanitize(row.business, 'Business')
                                    if (!businessResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Business' } })
                                        continue
                                    }
                                }
                                if (row.description) {
                                    const descResult = validateAndSanitize(row.description, 'Description')
                                    if (!descResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Description' } })
                                        continue
                                    }
                                }
                                if (hostNameToId.has(hostName)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'Host', name: hostName } })
                                    continue
                                }
                                const clusterId = row.cluster
                                    ? clusterNameToId.get(row.cluster)
                                    : undefined
                                if (!clusterId && row.cluster) {
                                    errors.push({ row: i + 2, code: 'import.clusterNotFound', params: { cluster: row.cluster } })
                                    continue
                                }
                                const roleValue = row.role as string | undefined
                                const created = await deps.createHost({
                                    name: nameResult.sanitized,
                                    ip: hostIp,
                                    port: row.port ? parseInt(row.port, 10) : 22,
                                    username: hostUsername,
                                    authType: (row.authType === 'key' ? 'key' : 'password') as 'password' | 'key',
                                    credential: row.credential || '',
                                    hostname: row.hostname ? validateAndSanitize(row.hostname, 'Hostname').sanitized : undefined,
                                    businessIp: row.businessIp || undefined,
                                    os: row.os ? validateAndSanitize(row.os, 'OS').sanitized : undefined,
                                    location: row.location ? validateAndSanitize(row.location, 'Location').sanitized : undefined,
                                    business: row.business ? validateAndSanitize(row.business, 'Business').sanitized : undefined,
                                    clusterId,
                                    purpose: row.purpose ? validateAndSanitize(row.purpose, 'Purpose').sanitized : undefined,
                                    role: (roleValue === 'primary' || roleValue === 'backup') ? roleValue : undefined,
                                    tags: row.tags ? row.tags.split(';').map(t => t.trim()).filter(Boolean) : [],
                                    description: row.description ? validateAndSanitize(row.description, 'Description').sanitized : undefined,
                                })
                                hostNameToId.set(row.name, created.id)
                                success++
                                break
                            }

                            case 'BusinessServices': {
                                const nameResult = validateAndSanitize(row.name, 'Name')
                                if (!nameResult.valid) {
                                    errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Name' } })
                                    continue
                                }
                                if (row.code) {
                                    const codeResult = validateAndSanitize(row.code, 'Code')
                                    if (!codeResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Code' } })
                                        continue
                                    }
                                }
                                if (row.description) {
                                    const descResult = validateAndSanitize(row.description, 'Description')
                                    if (!descResult.valid) {
                                        errors.push({ row: i + 2, code: 'import.invalidChars', params: { field: 'Description' } })
                                        continue
                                    }
                                }
                                if (bsNameToId.has(row.name)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'BusinessService', name: row.name } })
                                    continue
                                }
                                const groupId = row.group
                                    ? (groupNameToId.get(row.group) || groupCodeToId.get(row.group))
                                    : undefined
                                const businessTypeId = row.businessType
                                    ? businessTypeNameToId.get(row.businessType)
                                    : undefined
                                if (!groupId && row.group) {
                                    errors.push({ row: i + 2, code: 'import.groupNotFound', params: { group: row.group } })
                                    continue
                                }
                                const created = await deps.createBusinessService({
                                    name: nameResult.sanitized,
                                    code: row.code ? validateAndSanitize(row.code, 'Code').sanitized : row.code,
                                    groupId,
                                    businessTypeId,
                                    description: row.description ? validateAndSanitize(row.description, 'Description').sanitized : '',
                                    tags: row.tags ? row.tags.split(';').map(t => t.trim()).filter(Boolean) : [],
                                    priority: row.priority || '',
                                    contactInfo: row.contactInfo || '',
                                })
                                bsNameToId.set(row.name, created.id)
                                success++
                                break
                            }

                            case 'Relations': {
                                const sourceBsId = bsNameToId.get(row.sourceNode)
                                const sourceHostId = hostNameToId.get(row.sourceNode)
                                const destHostId = hostNameToId.get(row.destNode)
                                if (!destHostId) {
                                    errors.push({ row: i + 2, code: 'import.targetHostNotFound', params: { host: row.destNode } })
                                    continue
                                }
                                if (!sourceBsId && !sourceHostId) {
                                    errors.push({ row: i + 2, code: 'import.sourceNodeNotFound', params: { node: row.sourceNode } })
                                    continue
                                }
                                const relationKey = `${sourceBsId || sourceHostId}->${destHostId}`
                                if (existingRelationKeys.has(relationKey)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'Relation', name: `${row.sourceNode} -> ${row.destNode}` } })
                                    continue
                                }
                                existingRelationKeys.add(relationKey)
                                await deps.createRelation({
                                    sourceHostId: sourceBsId || sourceHostId!,
                                    targetHostId: destHostId,
                                    description: row.description || '',
                                    sourceType: sourceBsId ? 'business-service' : 'host',
                                })
                                success++
                                break
                            }

                            case 'SOPs': {
                                if (createdSopNames.has(row.name)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'SOP', name: row.name } })
                                    continue
                                }
                                const tags = row.tags
                                    ? row.tags.split(';').map(t => t.trim()).filter(Boolean)
                                    : []
                                await deps.createSop({
                                    name: row.name,
                                    description: row.description || '',
                                    version: row.version || '',
                                    triggerCondition: row.triggerCondition || '',
                                    enabled: row.enabled !== 'false',
                                    mode: (row.mode === 'natural_language' ? 'natural_language' : 'structured') as 'structured' | 'natural_language',
                                    stepsDescription: row.stepsDescription || '',
                                    tags,
                                })
                                createdSopNames.add(row.name)
                                success++
                                break
                            }

                            case 'Whitelist': {
                                if (createdPatterns.has(row.pattern)) {
                                    errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'Whitelist', name: row.pattern } })
                                    continue
                                }
                                if (!/^[a-zA-Z0-9_\-./\s]+$/.test(row.pattern)) {
                                    errors.push({ row: i + 2, code: 'import.whitelistInvalidPattern', params: { pattern: row.pattern } })
                                    continue
                                }
                                await deps.addWhitelistCommand({
                                    pattern: row.pattern,
                                    description: row.description || '',
                                    enabled: row.enabled !== 'false',
                                })
                                createdPatterns.add(row.pattern)
                                success++
                                break
                            }
                            default:
                                break
                        }
                    } catch (err) {
                        const msg = err instanceof Error ? err.message : String(err)
                        if (msg === 'Command whitelist entry conflict') {
                            errors.push({ row: i + 2, code: 'import.duplicate', params: { type: 'Whitelist', name: row.pattern } })
                        } else {
                            errors.push({ row: i + 2, code: 'import.rowError', params: { message: msg } })
                        }
                    }
                }

                if (type === 'HostGroups') {
                    const parentRows = rows.filter(r => r.parentGroup)
                    for (let i = 0; i < parentRows.length; i++) {
                        const row = parentRows[i]
                        const groupId = groupNameToId.get(row.name)
                        const parentId = groupNameToId.get(row.parentGroup) || groupCodeToId.get(row.parentGroup)
                        if (groupId && parentId) {
                            try {
                                await deps.updateGroup(groupId, { parentId })
                            } catch (err) {
                                const msg = err instanceof Error ? err.message : String(err)
                                errors.push({ row: i + 2, code: 'import.setParentFailed', params: { message: msg } })
                            }
                        }
                    }
                }

                try {
                    await Promise.all([
                        deps.fetchGroups(),
                        deps.fetchAllClusters(),
                        deps.fetchAllHosts(),
                        deps.fetchHostRelations(),
                        deps.fetchBusinessServices(),
                        deps.fetchGraph(),
                        deps.fetchWhitelist(),
                    ])
                } catch {}

                setImporting(false)
                setProgress(null)
                return { success, failed: errors.length, errors }
            } catch (err) {
                const msg = err instanceof Error ? err.message : String(err)
                setImporting(false)
                setProgress(null)
                return {
                    success: 0,
                    failed: rows.length,
                    errors: [{ row: 0, code: 'import.importFailed', params: { message: t('hostResource.importErrorImportFailed', { message: msg }) } }]
                }
            }
        } catch (err) {
            const msg = err instanceof Error ? err.message : String(err)
            return {
                success: 0,
                failed: 0,
                errors: [{ row: 0, code: 'import.fileReadError', params: { message: t('hostResource.importErrorFileReadError', { message: msg }) } }]
            }
        }
    }, [deps])

    return { importing, progress, importXlsx }
}