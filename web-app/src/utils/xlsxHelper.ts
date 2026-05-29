import * as XLSX from 'xlsx'
import type { ImportType } from '../types/importExport'
import { IMPORT_METADATA, getValidationRuleDescription, getRequiredLabel } from './importExportMetadata'

const MAX_CELL_LENGTH = 32767

function truncateCellValue(value: string): string {
    if (value && value.length > MAX_CELL_LENGTH) {
        return value.substring(0, MAX_CELL_LENGTH - 3) + '...'
    }
    return value || ''
}

export interface XlsxSheet {
    name: string
    data: (string | number | boolean)[][]
}

export interface XlsxParseResult {
    success: boolean
    data?: Record<string, string>[]
    error?: string
}

export function readXlsxFile(file: File): Promise<any> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = (e) => {
            try {
                const data = e.target?.result as ArrayBuffer
                const workbook = XLSX.read(data, { type: 'array' })
                resolve(workbook)
            } catch (error) {
                reject(error)
            }
        }
        reader.onerror = reject
        reader.readAsArrayBuffer(file)
    })
}

export function parseSheetToObjects(workbook: XLSX.WorkBook, sheetName: string): XlsxParseResult {
    const sheet = workbook.Sheets[sheetName]
    if (!sheet) {
        return { success: false, error: `Sheet "${sheetName}" not found` }
    }

    const jsonData = XLSX.utils.sheet_to_json(sheet, { header: 1, raw: false, defval: '' }) as (string | number | boolean)[][]

    if (jsonData.length < 2) {
        return { success: false, error: 'Sheet must have at least a header row and one data row' }
    }

    const headers = (jsonData[0] as (string | number | boolean)[]).map(h => String(h))
    const rows = jsonData.slice(1).map((row) => {
        const obj: Record<string, string> = {}
        headers.forEach((header, index) => {
            const value = row[index] !== undefined ? String(row[index]) : ''
            obj[header] = value
        })
        return obj
    })

    // Find which ImportType matches this sheet based on sheetName
    let importType: ImportType | null = null
    for (const [type, metadata] of Object.entries(IMPORT_METADATA)) {
        if (metadata.sheetName === sheetName) {
            importType = type as ImportType
            break
        }
    }

    if (!importType) {
        // No matching type found, return raw data
        return { success: true, data: rows }
    }

    // Map header labels to field names (support both English and Chinese labels)
    const metadata = IMPORT_METADATA[importType]
    const labelToFieldName = new Map<string, string>()
    metadata.fields.forEach((field) => {
        labelToFieldName.set(field.enLabel.toLowerCase(), field.name)
        labelToFieldName.set(field.zhLabel.toLowerCase(), field.name)
    })

    // Transform rows to use field names instead of header labels
    const transformedRows = rows.map((row) => {
        const transformedRow: Record<string, string> = {}
        Object.keys(row).forEach((header) => {
            const fieldName = labelToFieldName.get(header.toLowerCase())
            if (fieldName) {
                transformedRow[fieldName] = row[header]
            } else {
                // Keep unknown headers as-is
                transformedRow[header] = row[header]
            }
        })
        return transformedRow
    })

    console.log('[parseSheetToObjects] Sheet:', sheetName)
    console.log('[parseSheetToObjects] Original headers:', headers)
    console.log('[parseSheetToObjects] Label to field mapping:', Array.from(labelToFieldName.entries()))
    console.log('[parseSheetToObjects] Transformed rows (first 3):', transformedRows.slice(0, 3).map(r => JSON.stringify(r, null, 2)))

    return { success: true, data: transformedRows }
}

export function generateSampleXlsx(importType: ImportType, t: (key: string, params?: Record<string, any>) => string): XLSX.WorkBook {
    const metadata = IMPORT_METADATA[importType]
    const workbook = XLSX.utils.book_new()

    const fieldLabelTitle = t('importExport.fieldName')
    const requiredTitle = t('importExport.required')
    const validationTitle = t('importExport.validationRules')
    const descriptionTitle = t('importExport.description')

    const descriptionData: (string | number | boolean)[][] = [
        [fieldLabelTitle, requiredTitle, validationTitle, descriptionTitle],
    ]

    metadata.fields.forEach((field) => {
        const label = t(`hostResource.${field.labelKey}`)
        const description = getFieldDescription(field.labelKey, label)
        descriptionData.push([
            label,
            getRequiredLabel(field.required, t as (key: string) => string),
            getValidationRuleDescription(field.validation, t),
            description,
        ])
    })

    const descriptionSheet = XLSX.utils.aoa_to_sheet(descriptionData)
    XLSX.utils.book_append_sheet(workbook, descriptionSheet, t('importExport.fieldDescriptionSheet'))

    const sampleData = metadata.sampleData || []
    const sheetData: (string | number | boolean)[][] = []
    if (sampleData.length > 0) {
        sheetData.push(metadata.fields.map((f) => t(`hostResource.${f.labelKey}`)))
        sampleData.forEach((row) => {
            sheetData.push(metadata.fields.map((f) => (row as Record<string, string>)[f.name] ?? ''))
        })
    } else {
        sheetData.push(metadata.fields.map((f) => t(`hostResource.${f.labelKey}`)))
    }

    const dataSheet = XLSX.utils.aoa_to_sheet(sheetData)
    XLSX.utils.book_append_sheet(workbook, dataSheet, metadata.sheetName)

    return workbook
}

function getFieldDescription(labelKey: string, label: string): string {
    const descriptions: Record<string, string> = {
        'field_sops_mode': 'Mode (structured/natural_language)',
        'field_sops_stepsDescription': 'Steps Description (valid in natural_language mode)',
        'field_sops_targetTags': 'Target Tags (for natural_language mode)',
        'field_sops_nodes': 'Nodes (JSON array, valid in structured mode)',
        'field_clusterTypes_clusterMode': 'Cluster Mode (Optional: Peer/Primary-Backup)',
        'field_hostGroups_enabled': 'Enabled (Optional: true/false)',
        'field_hosts_authType': 'Auth Type (Optional: password/key)',
        'field_hosts_role': 'Role (Optional: primary/backup)',
    }
    return descriptions[labelKey] || label
}

export function downloadWorkbook(workbook: XLSX.WorkBook, filename: string): void {
    XLSX.writeFile(workbook, filename)
}

export function generateExportXlsx(
    importType: ImportType,
    data: Record<string, string>[],
    t: (key: string, params?: Record<string, any>) => string
): XLSX.WorkBook {
    const metadata = IMPORT_METADATA[importType]
    const workbook = XLSX.utils.book_new()

    const fieldLabelTitle = t('importExport.fieldName')
    const requiredTitle = t('importExport.required')
    const validationTitle = t('importExport.validationRules')
    const descriptionTitle = t('importExport.description')

    const descriptionData: (string | number | boolean)[][] = [
        [fieldLabelTitle, requiredTitle, validationTitle, descriptionTitle],
    ]

    metadata.fields.forEach((field) => {
        const label = t(`hostResource.${field.labelKey}`)
        const description = getFieldDescription(field.labelKey, label)
        descriptionData.push([
            label,
            getRequiredLabel(field.required, t as (key: string) => string),
            getValidationRuleDescription(field.validation, t),
            description,
        ])
    })

    const descriptionSheet = XLSX.utils.aoa_to_sheet(descriptionData)
    XLSX.utils.book_append_sheet(workbook, descriptionSheet, t('importExport.fieldDescriptionSheet'))

    if (data.length > 0) {
        const sheetData: (string | number | boolean)[][] = []
        sheetData.push(metadata.fields.map((f) => t(`hostResource.${f.labelKey}`)))
        data.forEach((row) => {
            sheetData.push(metadata.fields.map((f) => truncateCellValue(String(row[f.name] ?? ''))))
        })
        const dataSheet = XLSX.utils.aoa_to_sheet(sheetData)
        XLSX.utils.book_append_sheet(workbook, dataSheet, metadata.sheetName)
    }

    return workbook
}

export function generateMultiSheetExportXlsx(
    sheets: { name: string; data: Record<string, string>[]; metadata: ImportType }[],
    t: (key: string, params?: Record<string, any>) => string
): XLSX.WorkBook {
    const workbook = XLSX.utils.book_new()

    const fieldLabelTitle = t('importExport.fieldName')
    const requiredTitle = t('importExport.required')
    const validationTitle = t('importExport.validationRules')
    const descriptionTitle = t('importExport.description')

    sheets.forEach((sheetInfo) => {
        const metadata = IMPORT_METADATA[sheetInfo.metadata]

        const descriptionData: (string | number | boolean)[][] = [
            [fieldLabelTitle, requiredTitle, validationTitle, descriptionTitle],
        ]

        metadata.fields.forEach((field) => {
            const label = t(`hostResource.${field.labelKey}`)
            const description = getFieldDescription(field.labelKey, label)
            descriptionData.push([
                label,
                getRequiredLabel(field.required, t as (key: string) => string),
                getValidationRuleDescription(field.validation, t),
                description,
            ])
        })

        const descriptionSheet = XLSX.utils.aoa_to_sheet(descriptionData)
        XLSX.utils.book_append_sheet(workbook, descriptionSheet, `${t('importExport.fieldDescriptionSheet')}_${sheetInfo.name}`)

        if (sheetInfo.data.length > 0) {
            const sheetData: (string | number | boolean)[][] = []
            sheetData.push(metadata.fields.map((f) => t(`hostResource.${f.labelKey}`)))
            sheetInfo.data.forEach((row) => {
                sheetData.push(metadata.fields.map((f) => truncateCellValue(String(row[f.name] ?? ''))))
            })
            const dataSheet = XLSX.utils.aoa_to_sheet(sheetData)
            XLSX.utils.book_append_sheet(workbook, dataSheet, sheetInfo.name)
        }
    })

    return workbook
}