import FilePreview from './preview/FilePreview'
import CapabilityMarketPanel from './panels/CapabilityMarketPanel'
import { useRightPanel } from './providers/RightPanelContext'
import { usePreview } from './providers/PreviewContext'

export function RightPanelHost() {
    const { previewFile, isPreviewFullscreen } = usePreview()
    const { isMarketOpen, marketActiveTab, closeMarket, setMarketActiveTab } = useRightPanel()
    const isPreviewOpen = !!previewFile
    const isRightPanelOpen = isMarketOpen || isPreviewOpen
    const panelMode = (() => {
        if (isMarketOpen) return 'drawer'
        if (isPreviewOpen) return `preview${isPreviewFullscreen ? ' fullscreen' : ''}`
        return ''
    })()

    return (
        <div className={`right-panel-host ${isRightPanelOpen ? 'open' : ''} ${panelMode}`}>
            {isMarketOpen ? (
                <CapabilityMarketPanel
                    isOpen={isMarketOpen}
                    activeTab={marketActiveTab}
                    onClose={closeMarket}
                    onTabChange={setMarketActiveTab}
                />
            ) : (
                <FilePreview embedded />
            )}
        </div>
    )
}
