import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import DetailDialog from '../../../platform/ui/primitives/DetailDialog'
import type { ImportType, ImportResult, ImportProgress } from '../hooks/useResourceImport'
import { generateSampleXlsx, downloadWorkbook, readXlsxFile } from '../../../../utils/xlsxHelper'
import { validateSheetStructure, type SheetValidationError } from '../../../../utils/xlsxValidator'
import { IMPORT_METADATA } from '../../../../utils/importExportMetadata'

const IMPORT_TYPES: ImportType[] = [
    'ClusterTypes',
    'BusinessTypes',
    'HostGroups',
    'Clusters',
    'Hosts',
    'BusinessServices',
    'Relations',
    'SOPs',
    'Whitelist',
]

interface ImportDialogProps {
    open: boolean
    onClose: () => void
    importing: boolean
    progress: ImportProgress | null
    onImport: (type: ImportType, file: File) => Promise<ImportResult>
}

export default function ImportDialog({ open, onClose, importing, progress, onImport }: ImportDialogProps) {
    const { t } = useTranslation()
    const [selectedType, setSelectedType] = useState<ImportType | null>(null)
    const [selectedFile, setSelectedFile] = useState<File | null>(null)
    const [result, setResult] = useState<ImportResult | null>(null)
    const [fileValidation, setFileValidation] = useState<{ valid: boolean; message: string } | null>(null)
    const [validatingFile, setValidatingFile] = useState(false)
    const fileInputRef = useRef<HTMLInputElement>(null)

    const handleTypeSelect = (type: ImportType) => {
        setSelectedType(type)
        setSelectedFile(null)
        setResult(null)
        setFileValidation(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
    }

    const validateFile = async (file: File, type: ImportType) => {
        setValidatingFile(true)
        setFileValidation(null)
        try {
            const workbook = await readXlsxFile(file)
            const structureValidation = validateSheetStructure(workbook, type)

            if (!structureValidation.valid) {
                const sheetError = structureValidation.sheetErrors[0]
                setFileValidation({
                    valid: false,
                    message: translateSheetError(sheetError, type)
                })
                return
            }

            setFileValidation({
                valid: true,
                message: t('hostResource.importFileValid')
            })
        } catch (error) {
            setFileValidation({
                valid: false,
                message: t('hostResource.importErrorFileReadError', { message: error instanceof Error ? error.message : String(error) })
            })
        } finally {
            setValidatingFile(false)
        }
    }

    const translateSheetError = (error: SheetValidationError, resourceType: ImportType): string => {
        switch (error.code) {
            case 'import.sheetNotFound':
                return t('hostResource.importErrorSheetNotFound', { sheet: error.params?.sheet })
            case 'import.wrongColumnCount':
                return t('hostResource.importErrorWrongColumnCount', {
                    expected: error.params?.expected || '0',
                    actual: error.params?.actual || '0'
                })
            case 'import.missingColumns':
                return t('hostResource.importErrorMissingColumns', {
                    type: error.params?.type || resourceType,
                    columns: error.params?.columns || ''
                })
            case 'import.extraColumns':
                return t('hostResource.importErrorExtraColumns', {
                    type: error.params?.type || resourceType,
                    columns: error.params?.columns || ''
                })
            default:
                return error.code
        }
    }

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0]
        setSelectedFile(file ?? null)
        setResult(null)
        setFileValidation(null)

        if (file && selectedType) {
            validateFile(file, selectedType)
        }
    }

    // Trigger validation when type changes with a file selected
    useEffect(() => {
        if (selectedFile && selectedType) {
            validateFile(selectedFile, selectedType)
        }
    }, [selectedType])

    const handleImport = async () => {
        if (!selectedType || !selectedFile) return
        const res = await onImport(selectedType, selectedFile)
        setResult(res)
    }

    const downloadSample = () => {
        if (!selectedType) return
        const workbook = generateSampleXlsx(selectedType)
        const filename = selectedType === 'Whitelist' ? 'trustlist_sample.xlsx' : `${selectedType.toLowerCase()}_sample.xlsx`
        downloadWorkbook(workbook, filename)
    }

    const handleClose = () => {
        if (importing) return
        setSelectedType(null)
        setSelectedFile(null)
        setResult(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
        onClose()
    }

    const handleContinue = () => {
        setSelectedType(null)
        setSelectedFile(null)
        setResult(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
    }

    const typeLabel = (type: ImportType) => t(`hostResource.importType_${type}`)

    const translateError = (err: ImportResult['errors'][0]): string => {
        switch (err.code) {
            case 'import.noDataRows':
                return t('hostResource.importErrorNoDataRows')
            case 'import.invalidFile':
                return t('hostResource.importErrorInvalidFile', { message: err.params?.message })
            case 'import.parseError':
                return t('hostResource.importErrorParseError', { message: err.params?.message })
            case 'import.fileReadError':
                return t('hostResource.importErrorFileReadError', { message: err.params?.message })
            case 'import.missingRequiredColumns':
                return t('hostResource.importErrorMissingRequiredColumns', {
                    type: err.params?.type,
                    columns: err.params?.columns,
                })
            case 'import.wrongFileType.suggestBusinessTypes':
                return t('hostResource.importErrorSuggestBusinessTypes')
            case 'import.wrongFileType.suggestClusterTypes':
                return t('hostResource.importErrorSuggestClusterTypes')
            case 'import.wrongFileType.suggestOther':
                return t('hostResource.importErrorSuggestOther', { types: err.params?.types })
            case 'import.groupNotFound':
                return t('hostResource.importErrorGroupNotFound', { group: err.params?.group })
            case 'import.clusterNotFound':
                return t('hostResource.importErrorClusterNotFound', { cluster: err.params?.cluster })
            case 'import.targetHostNotFound':
                return t('hostResource.importErrorTargetHostNotFound', { host: err.params?.host })
            case 'import.sourceNodeNotFound':
                return t('hostResource.importErrorSourceNodeNotFound', { node: err.params?.node })
            case 'import.rowError':
                return t('hostResource.importErrorRowError', { message: err.params?.message })
            case 'import.hostNameRequired':
                return t('hostResource.importErrorHostNameRequired')
            case 'import.hostNameTooLong':
                return t('hostResource.importErrorHostNameTooLong', { length: err.params?.length })
            case 'import.hostIpRequired':
                return t('hostResource.importErrorHostIpRequired')
            case 'import.hostIpInvalid':
                return t('hostResource.importErrorHostIpInvalid', { ip: err.params?.ip })
            case 'import.hostUsernameRequired':
                return t('hostResource.importErrorHostUsernameRequired')
            case 'import.duplicate':
                return t('hostResource.importErrorDuplicate', { type: err.params?.type === 'Whitelist' ? t('hostResource.importType_Whitelist') : err.params?.type, name: err.params?.name })
            case 'import.whitelistInvalidPattern':
                return t('hostResource.importErrorWhitelistInvalidPattern', { pattern: err.params?.pattern })
            case 'import.invalidChars':
                return t('hostResource.importErrorInvalidChars', { field: err.params?.field })
            case 'import.usernameInvalidChars':
                return t('hostResource.importErrorUsernameInvalidChars')
            case 'import.credentialInvalidChars':
                return t('hostResource.importErrorCredentialInvalidChars')
            case 'import.businessIpInvalid':
                return t('hostResource.importErrorBusinessIpInvalid', { ip: err.params?.ip })
            case 'import.setParentFailed':
                return t('hostResource.importErrorSetParentFailed', { message: err.params?.message })
            case 'import.importFailed':
                return t('hostResource.importErrorImportFailed', { message: err.params?.message })
            default:
                return err.code
        }
    }

    if (!open) return null

    return (
        <DetailDialog
            title={t('hostResource.importDialogTitle')}
            onClose={handleClose}
            className="hr-import-dialog"
            bodyClassName="hr-import-dialog-body"
            footer={
                <div className="hr-import-dialog-footer">
                    {result ? (
                        <>
                            <button className="btn btn-secondary btn-sm" onClick={handleContinue}>
                                {t('hostResource.importContinue')}
                            </button>
                            <button className="btn btn-primary btn-sm" onClick={handleClose}>
                                {t('hostResource.importClose')}
                            </button>
                        </>
                    ) : (
                        <>
                            <button className="btn btn-secondary btn-sm" onClick={handleClose} disabled={importing}>
                                {t('hostResource.importClose')}
                            </button>
                            <button
                                className="btn btn-primary btn-sm"
                                onClick={handleImport}
                                disabled={!selectedType || !selectedFile || importing || !fileValidation?.valid || validatingFile}
                            >
                                {importing ? t('hostResource.importing', { current: progress?.current ?? 0, total: progress?.total ?? 0 }) : t('hostResource.importStart')}
                            </button>
                        </>
                    )}
                </div>
            }
        >
            {!result && (
                <div className="hr-import-step">
                    <div className="hr-import-step-label">
                        {t('hostResource.importStep1')}
                    </div>
                    <div className="hr-import-type-grid">
                        {IMPORT_TYPES.map(type => (
                            <button
                                key={type}
                                type="button"
                                className={`hr-import-type-btn ${selectedType === type ? 'hr-import-type-btn-active' : ''}`}
                                onClick={() => handleTypeSelect(type)}
                                disabled={importing}
                            >
                                {typeLabel(type)}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {!result && selectedType && (
                <div className="hr-import-step">
                    <div className="hr-import-step-label">
                        {t('hostResource.importStep2')}
                    </div>
                    <div className="hr-import-sample-area">
                        <div className="hr-import-sample-header">
                            <span>{t('hostResource.importSampleTitle')}</span>
                            <button
                                type="button"
                                className="btn btn-secondary btn-xs"
                                onClick={downloadSample}
                                disabled={importing}
                            >
                                {t('hostResource.importDownloadSample')}
                            </button>
                        </div>
                        <div className="hr-import-sample-content">
                            <table className="hr-import-sample-table">
                                <thead>
                                    <tr>
                                        <th>字段名称</th>
                                        <th>可选必选</th>
                                        <th>校验规则</th>
                                        <th>描述</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {IMPORT_METADATA[selectedType].fields.map((field, idx) => (
                                        <tr key={idx}>
                                            <td>{field.name}</td>
                                            <td>{field.required ? '必填' : '可选'}</td>
                                            <td>{field.validation ? JSON.stringify(field.validation) : '-'}</td>
                                            <td>{field.description || '-'}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div className="hr-import-file-area">
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept=".xlsx"
                            onChange={handleFileChange}
                            disabled={importing}
                            className="hr-import-file-input"
                            style={{ display: 'none' }}
                        />
                        <button
                            type="button"
                            className="hr-import-file-btn"
                            onClick={() => fileInputRef.current?.click()}
                            disabled={importing}
                            title={selectedFile ? selectedFile.name : t('hostResource.importSelectFile')}
                        >
                            {selectedFile ? selectedFile.name : t('hostResource.importSelectFile')}
                        </button>
                        {!selectedFile && (
                            <div className="hr-import-file-placeholder">{t('hostResource.importNoFileSelected')}</div>
                        )}
                    </div>

                    {validatingFile && (
                        <div className="hr-import-validating">
                            {t('hostResource.importValidatingFile')}
                        </div>
                    )}

                    {fileValidation && !validatingFile && (
                        <div className={`hr-import-file-validation ${fileValidation.valid ? 'hr-import-validation-success' : 'hr-import-validation-error'}`}>
                            {fileValidation.message}
                        </div>
                    )}
                </div>
            )}

            {importing && progress && (
                <div className="hr-import-progress">
                    <div className="hr-import-progress-bar-track">
                        <div
                            className="hr-import-progress-bar-fill"
                            style={{ width: `${progress.total > 0 ? (progress.current / progress.total) * 100 : 0}%` }}
                        />
                    </div>
                    <div className="hr-import-progress-text">
                        {t('hostResource.importProgress', { current: progress.current, total: progress.total, phase: t(`hostResource.importType_${progress.phase}`) })}
                    </div>
                </div>
            )}

            {result && (
                <div className="hr-import-result">
                    <div className="hr-import-result-summary">
                        {t('hostResource.importResultSummary', { success: result.success, failed: result.failed })}
                    </div>
                    {result.errors.length > 0 && (
                        <div className="hr-import-result-errors">
                            <div className="hr-import-result-errors-title">
                                {t('hostResource.importResultErrors')}
                            </div>
                            <ul className="hr-import-error-list">
                                {result.errors.map((err, idx) => (
                                    <li key={idx} className="hr-import-error-item">
                                        <span className="hr-import-error-row">{t('hostResource.importErrorRow', { row: err.row })}</span> {translateError(err)}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            )}
        </DetailDialog>
    )
}