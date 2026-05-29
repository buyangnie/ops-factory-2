import * as XLSX from 'xlsx'
import type { ImportType, FieldMetadata, ValidationResult } from '../types/importExport'
import { IMPORT_METADATA } from './importExportMetadata'
import { isValidIp } from './ip-validation'

export interface SheetValidationError {
    type: 'missing-sheet' | 'wrong-columns' | 'wrong-column-count'
    code: string
    params?: Record<string, string>
}

export interface RowValidationError {
    row: number
    field?: string
    code: string
    params?: Record<string, string>
}

export interface ValidationResultData {
    valid: boolean
    sheetErrors: SheetValidationError[]
    rowErrors: RowValidationError[]
}

export function validateSheetStructure(
    workbook: any,
    importType: ImportType
): ValidationResultData {
    const metadata = IMPORT_METADATA[importType]
    const errors: SheetValidationError[] = []

    const dataSheetName = metadata.sheetName

    if (!workbook.SheetNames.includes(dataSheetName)) {
        errors.push({
            type: 'missing-sheet',
            code: 'import.sheetNotFound',
            params: { sheet: dataSheetName }
        })
        return { valid: false, sheetErrors: errors, rowErrors: [] }
    }

    const dataSheet = workbook.Sheets[dataSheetName]
    const dataRange = XLSX.utils.decode_range(dataSheet['!ref'] || 'A1')

    const expectedColumnCount = metadata.fields.length
    const actualColumnCount = dataRange.e.c + 1

    if (actualColumnCount !== expectedColumnCount) {
        errors.push({
            type: 'wrong-column-count',
            code: 'import.wrongColumnCount',
            params: { expected: String(expectedColumnCount), actual: String(actualColumnCount) }
        })
    }

    // Get headers from first row
    const headers: string[] = []
    for (let col = dataRange.s.c; col <= dataRange.e.c; col++) {
        const cellAddress = XLSX.utils.encode_cell({ r: dataRange.s.r, c: col })
        const cell = dataSheet[cellAddress]
        const header = cell?.v || ''
        headers.push(String(header).trim())
    }

    // Validate column names (support both English and Chinese labels)
    const actualColumns = headers.map((h) => h.toLowerCase())

    // Check for missing columns
    const missingColumns: string[] = []
    metadata.fields.forEach((field) => {
        const enLabel = field.enLabel.toLowerCase()
        const zhLabel = field.zhLabel.toLowerCase()
        // Column is present if either English or Chinese label matches
        const isPresent = actualColumns.includes(enLabel) || actualColumns.includes(zhLabel)
        if (!isPresent) {
            missingColumns.push(field.name)
        }
    })

    // Check for extra columns (columns that don't match any English or Chinese label)
    const allExpectedLabels = metadata.fields.flatMap((f) => [f.enLabel.toLowerCase(), f.zhLabel.toLowerCase()])
    const extraColumns = headers.filter((header) => {
        const lowerHeader = header.toLowerCase()
        return !allExpectedLabels.includes(lowerHeader)
    })

    if (missingColumns.length > 0) {
        // Get human-readable labels for missing columns (use English labels as fallback)
        const missingColumnLabels = missingColumns.map(fieldName => {
            const field = metadata.fields.find(f => f.name === fieldName)
            return field ? `${field.enLabel} (${field.name})` : fieldName
        }).join(', ')
        errors.push({
            type: 'wrong-columns',
            code: 'import.missingColumns',
            params: {
                type: importType,
                columns: missingColumnLabels,
                fieldNames: missingColumns.join(',')
            }
        })
    }

    if (extraColumns.length > 0) {
        // Try to map extra columns to their closest expected labels for better error messages
        const extraColumnLabels = extraColumns.join(', ')
        errors.push({
            type: 'wrong-columns',
            code: 'import.extraColumns',
            params: {
                type: importType,
                columns: extraColumnLabels
            }
        })
    }

    return {
        valid: errors.length === 0,
        sheetErrors: errors,
        rowErrors: [],
    }
}

export function validateField(
    value: string,
    field: FieldMetadata,
    rowNumber: number
): ValidationResult {
    const trimmedValue = value?.trim() || ''

    if (field.required && !trimmedValue) {
        return { valid: false, error: `${field.name} is required` }
    }

    if (!trimmedValue) {
        return { valid: true }
    }

    const validation = field.validation
    if (!validation) {
        return { valid: true }
    }

    switch (validation.type) {
        case 'string':
            if (validation.maxLength && trimmedValue.length > validation.maxLength) {
                return { valid: false, error: `${field.name} exceeds maximum length of ${validation.maxLength}` }
            }
            if (validation.minLength && trimmedValue.length < validation.minLength) {
                return { valid: false, error: `${field.name} must be at least ${validation.minLength} characters` }
            }
            break

        case 'number':
            if (isNaN(Number(trimmedValue))) {
                return { valid: false, error: `${field.name} must be a valid number` }
            }
            break

        case 'boolean':
            if (trimmedValue.toLowerCase() !== 'true' && trimmedValue.toLowerCase() !== 'false') {
                return { valid: false, error: `${field.name} must be true or false` }
            }
            break

        case 'enum':
            if (validation.enumValues && !validation.enumValues.includes(trimmedValue)) {
                return { valid: false, error: `${field.name} must be one of: ${validation.enumValues.join(', ')}` }
            }
            break

        case 'ip':
            if (!isValidIp(trimmedValue)) {
                return { valid: false, error: `${field.name} must be a valid IP address` }
            }
            break

        case 'regex':
            if (validation.pattern && !new RegExp(validation.pattern).test(trimmedValue)) {
                return { valid: false, error: `${field.name} does not match required pattern` }
            }
            break

        case 'array':
            if (validation.separator) {
                const parts = trimmedValue.split(validation.separator)
                if (parts.length === 0 || parts.every((p) => !p.trim())) {
                    return { valid: false, error: `${field.name} must contain at least one value` }
                }
            }
            break

        case 'custom':
            if (validation.customValidator) {
                return validation.customValidator(trimmedValue, rowNumber)
            }
            break
    }

    return { valid: true }
}