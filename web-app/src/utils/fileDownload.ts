export function getFilenameFromDisposition(disposition: string | null, fallback: string): string {
    if (!disposition) return fallback

    const encodedMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (encodedMatch) {
        try {
            return decodeURIComponent(encodedMatch[1])
        } catch {
            return encodedMatch[1]
        }
    }

    const quotedMatch = disposition.match(/filename="([^"]+)"/i)
    if (quotedMatch) return quotedMatch[1]

    const bareMatch = disposition.match(/filename=([^;]+)/i)
    if (bareMatch) return bareMatch[1].trim()

    return fallback
}

export function triggerDownload(blob: Blob, filename: string): void {
    const objectUrl = window.URL.createObjectURL(blob)
    const anchor = window.document.createElement('a')
    anchor.href = objectUrl
    anchor.download = filename
    window.document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => window.URL.revokeObjectURL(objectUrl), 1000)
}
