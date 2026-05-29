import { useState, useCallback } from 'react'
import { createZip } from '../../../../utils/zipHelper'
import { generateExportXlsx } from '../../../../utils/xlsxHelper'
import * as XLSX from 'xlsx'
import type { HostGroup, Cluster, Host, BusinessService, HostRelation, ClusterType, BusinessType } from '../../../../types/host'
import type { Sop } from '../../../../types/sop'
import type { WhitelistCommand } from '../../../../types/commandWhitelist'

type EnvVariable = { key: string; value: string }

export function useResourceExport() {
    const [exporting, setExporting] = useState(false)

    const exportAllAsZip = useCallback(async (params: {
        groups: HostGroup[]
        clusters: Cluster[]
        allHosts: Host[]
        hostRelations: HostRelation[]
        businessServices: BusinessService[]
        clusterTypes: ClusterType[]
        businessTypes: BusinessType[]
        whitelistCommands: WhitelistCommand[]
        sops: Sop[]
    }, t: (key: string) => string) => {
        setExporting(true)
        try {
            const {
                groups, clusters, allHosts, hostRelations,
                businessServices, clusterTypes, businessTypes,
                whitelistCommands, sops,
            } = params

            const groupMap = new Map(groups.map(g => [g.id, g]))
            const clusterMap = new Map(clusters.map(c => [c.id, c]))
            const businessTypeMap = new Map(businessTypes.map(bt => [bt.id, bt]))

            const files = []

            // 1. Cluster Types XLSX
            const ctData = clusterTypes.map(ct => ({
                name: ct.name,
                code: ct.code,
                description: ct.description || '',
                typeColor: ct.color || '',
                knowledge: ct.knowledge || '',
                clusterMode: ct.mode === 'peer' ? 'Peer' : (ct.mode === 'primary-backup' ? 'Primary-Backup' : ''),
                commandPrefix: ct.commandPrefix || '',
                envVariables: ct.envVariables
                    ? (ct.envVariables as EnvVariable[]).map((v: EnvVariable) => `${v.key}=${v.value}`).join(';')
                    : '',
            }))
            const ctWorkbook = generateExportXlsx('ClusterTypes', ctData, t)
            const ctBlob = workbookToBlob(ctWorkbook)
            files.push({ name: 'cluster_types.xlsx', data: ctBlob })

            // 2. Business Types XLSX
            const btData = businessTypes.map(bt => ({
                name: bt.name,
                code: bt.code,
                description: bt.description || '',
                typeColor: bt.color || '',
                knowledge: bt.knowledge || '',
            }))
            const btWorkbook = generateExportXlsx('BusinessTypes', btData, t)
            const btBlob = workbookToBlob(btWorkbook)
            files.push({ name: 'business_types.xlsx', data: btBlob })

            // 3. Host Groups XLSX
            const groupData = groups.map(g => ({
                name: g.name,
                code: g.code || '',
                parentGroup: g.parentId ? (groupMap.get(g.parentId)?.name ?? '') : '',
                description: g.description || '',
                enabled: g.enabled ? 'true' : 'false',
            }))
            const groupWorkbook = generateExportXlsx('HostGroups', groupData, t)
            const groupBlob = workbookToBlob(groupWorkbook)
            files.push({ name: 'groups.xlsx', data: groupBlob })

            // 4. Clusters XLSX
            const clusterData = clusters.map(c => ({
                name: c.name,
                type: c.type || '',
                purpose: c.purpose || '',
                group: c.groupId ? (groupMap.get(c.groupId)?.name ?? '') : '',
                description: c.description || '',
            }))
            const clusterWorkbook = generateExportXlsx('Clusters', clusterData, t)
            const clusterBlob = workbookToBlob(clusterWorkbook)
            files.push({ name: 'clusters.xlsx', data: clusterBlob })

            // 5. Hosts XLSX
            const hostData = allHosts.map(h => ({
                name: h.name,
                hostname: h.hostname || '',
                ip: h.ip,
                port: String(h.port),
                businessIp: h.businessIp || '',
                os: h.os || '',
                location: h.location || '',
                username: h.username,
                authType: h.authType || '',
                credential: '',
                business: h.business || '',
                cluster: h.clusterId ? (clusterMap.get(h.clusterId)?.name ?? '') : '',
                purpose: h.purpose || '',
                role: h.role === 'primary' ? 'primary' : (h.role === 'backup' ? 'backup' : ''),
                tags: Array.isArray(h.tags) ? h.tags.join(';') : '',
                description: h.description || '',
            }))
            const hostWorkbook = generateExportXlsx('Hosts', hostData, t)
            const hostBlob = workbookToBlob(hostWorkbook)
            files.push({ name: 'hosts.xlsx', data: hostBlob })

            // 6. Business Services XLSX
            const bsData = businessServices.map(bs => ({
                name: bs.name,
                code: bs.code,
                group: bs.groupId ? (groupMap.get(bs.groupId)?.name ?? '') : '',
                businessType: bs.businessTypeId ? (businessTypeMap.get(bs.businessTypeId)?.name ?? '') : '',
                description: bs.description || '',
                tags: Array.isArray(bs.tags) ? bs.tags.join(';') : '',
                priority: bs.priority || '',
            }))
            const bsWorkbook = generateExportXlsx('BusinessServices', bsData, t)
            const bsBlob = workbookToBlob(bsWorkbook)
            files.push({ name: 'business_services.xlsx', data: bsBlob })

            // 7. Relations XLSX
            const allHostMap = new Map(allHosts.map(h => [h.id, h]))
            const bsMap = new Map(businessServices.map(bs => [bs.id, bs]))
            const relData: { sourceNode: string; destNode: string; description: string }[] = []

            for (const r of hostRelations) {
                const sourceName = r.sourceType === 'business-service'
                    ? (bsMap.get(r.sourceHostId)?.name ?? '')
                    : (allHostMap.get(r.sourceHostId)?.name ?? '')
                const destName = allHostMap.get(r.targetHostId)?.name ?? ''
                if (sourceName && destName) {
                    relData.push({
                        sourceNode: sourceName,
                        destNode: destName,
                        description: r.description || '',
                    })
                }
            }

            for (const bs of businessServices) {
                for (const hostId of bs.hostIds) {
                    const hostName = allHostMap.get(hostId)?.name
                    if (hostName) {
                        const exists = relData.some(r =>
                            r.sourceNode === bs.name && r.destNode === hostName
                        )
                        if (!exists) {
                            relData.push({
                                sourceNode: bs.name,
                                destNode: hostName,
                                description: '',
                            })
                        }
                    }
                }
            }

            const relWorkbook = generateExportXlsx('Relations', relData, t)
            const relBlob = workbookToBlob(relWorkbook)
            files.push({ name: 'relations.xlsx', data: relBlob })

            // 8. SOPs XLSX
            const sopData = sops.map(s => ({
                name: s.name,
                description: s.description || '',
                version: s.version || '',
                triggerCondition: s.triggerCondition || '',
                enabled: String(s.enabled ?? true),
                mode: s.mode || 'structured',
                stepsDescription: s.stepsDescription || '',
                targetTags: Array.isArray(s.tags) ? s.tags.join(';') : '',
                nodes: Array.isArray(s.nodes) && s.nodes.length > 0 ? JSON.stringify(s.nodes) : '',
            }))
            const sopWorkbook = generateExportXlsx('SOPs', sopData, t)
            const sopBlob = workbookToBlob(sopWorkbook)
            files.push({ name: 'sops.xlsx', data: sopBlob })

            // 9. Whitelist XLSX
            const wlData = whitelistCommands.map(cmd => ({
                pattern: cmd.pattern,
                description: cmd.description || '',
                enabled: String(cmd.enabled),
            }))
            const wlWorkbook = generateExportXlsx('Whitelist', wlData, t)
            const wlBlob = workbookToBlob(wlWorkbook)
            files.push({ name: 'trustlist.xlsx', data: wlBlob })

            // Create ZIP and download
            const zipBlob = createZip(files)
            const url = URL.createObjectURL(zipBlob)
            const a = document.createElement('a')
            a.href = url
            a.download = `ops-resources-${new Date().toISOString().slice(0, 10)}.zip`
            a.click()
            URL.revokeObjectURL(url)
        } finally {
            setExporting(false)
        }
    }, [])

    return { exporting, exportAllAsZip }
}

// Helper function to convert workbook to blob
function workbookToBlob(workbook: XLSX.WorkBook): Uint8Array {
    const buffer = XLSX.write(workbook, { type: 'array', bookType: 'xlsx' })
    return new Uint8Array(buffer)
}
