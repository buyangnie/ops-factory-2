export interface FieldValidation {
    type: 'string' | 'number' | 'boolean' | 'enum' | 'ip' | 'regex' | 'array' | 'custom'
    maxLength?: number
    minLength?: number
    pattern?: string
    enumValues?: string[]
    customValidator?: (value: string, row: number) => ValidationResult
    separator?: string
}

export interface ValidationResult {
    valid: boolean
    error?: string
    sanitizedValue?: string
}

export interface FieldMetadata {
    name: string
    labelKey: string
    enLabel: string
    zhLabel: string
    required: boolean
    validation?: FieldValidation
}

export interface ResourceImportMetadata {
    resourceType: ImportType
    fields: FieldMetadata[]
    sheetName: string
    descriptionSheetName: string
    sampleData?: Record<string, string>[]
}

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