import type { ComponentType, CSSProperties, ElementType, HTMLAttributes, ReactElement } from 'react'
import {
    AppstoreOutlined,
    BarChartOutlined,
    CheckCircleOutlined,
    CheckOutlined,
    ClockCircleOutlined,
    CloseOutlined,
    CodeOutlined,
    CopyOutlined,
    DeleteOutlined,
    DollarCircleOutlined,
    DownOutlined,
    DownloadOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    ExportOutlined,
    EyeOutlined,
    FilePptOutlined,
    FileTextOutlined,
    FileZipOutlined,
    FullscreenExitOutlined,
    FullscreenOutlined,
    ImportOutlined,
    InboxOutlined,
    InfoCircleOutlined,
    LoadingOutlined,
    MessageOutlined,
    MinusOutlined,
    NodeIndexOutlined,
    PlayCircleOutlined,
    PlusOutlined,
    RadarChartOutlined,
    ReloadOutlined,
    RobotOutlined,
    SaveOutlined,
    SearchOutlined,
    SettingOutlined,
    StopOutlined,
    TeamOutlined,
    ThunderboltOutlined,
    UploadOutlined,
    WarningOutlined,
} from '@ant-design/icons'

export interface AppIconProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
    size?: number | string
    strokeWidth?: number
}

export type AppIcon = ComponentType<AppIconProps>

type AntIconProps = Omit<HTMLAttributes<HTMLSpanElement>, 'children'> & {
    spin?: boolean
}

type AntIconComponent = ElementType<AntIconProps>

function resolveIconStyle(size: AppIconProps['size'], style?: CSSProperties): CSSProperties | undefined {
    if (size === undefined) return style
    return { ...style, fontSize: size }
}

function createAppIcon(AntIcon: AntIconComponent, defaultProps: Partial<AntIconProps> = {}): AppIcon {
    return function AppIconAdapter({ size, strokeWidth: _strokeWidth, style, ...props }: AppIconProps): ReactElement {
        return (
            <AntIcon
                {...defaultProps}
                {...props}
                style={resolveIconStyle(size, style)}
            />
        )
    }
}

export const Activity = createAppIcon(BarChartOutlined)
export const AlertCircle = createAppIcon(ExclamationCircleOutlined)
export const AlertTriangle = createAppIcon(WarningOutlined)
export const Archive = createAppIcon(InboxOutlined)
export const BarChart2 = createAppIcon(BarChartOutlined)
export const Bot = createAppIcon(RobotOutlined)
export const Box = createAppIcon(AppstoreOutlined)
export const Check = createAppIcon(CheckOutlined)
export const CheckCircle2 = createAppIcon(CheckCircleOutlined)
export const ChevronDown = createAppIcon(DownOutlined)
export const Clock = createAppIcon(ClockCircleOutlined)
export const Code2 = createAppIcon(CodeOutlined)
export const CodeXml = createAppIcon(CodeOutlined)
export const Coins = createAppIcon(DollarCircleOutlined)
export const Copy = createAppIcon(CopyOutlined)
export const CornerDownRight = createAppIcon(ImportOutlined)
export const CornerUpRight = createAppIcon(ExportOutlined)
export const Download = createAppIcon(DownloadOutlined)
export const Eye = createAppIcon(EyeOutlined)
export const FileArchive = createAppIcon(FileZipOutlined)
export const FileDown = createAppIcon(DownloadOutlined)
export const FileJson = createAppIcon(FileTextOutlined)
export const Inbox = createAppIcon(InboxOutlined)
export const Info = createAppIcon(InfoCircleOutlined)
export const Loader2 = createAppIcon(LoadingOutlined, { spin: true })
export const Maximize2 = createAppIcon(FullscreenOutlined)
export const MessageSquare = createAppIcon(MessageOutlined)
export const Minimize2 = createAppIcon(FullscreenExitOutlined)
export const Minus = createAppIcon(MinusOutlined)
export const Network = createAppIcon(NodeIndexOutlined)
export const Pencil = createAppIcon(EditOutlined)
export const Play = createAppIcon(PlayCircleOutlined)
export const Plus = createAppIcon(PlusOutlined)
export const Presentation = createAppIcon(FilePptOutlined)
export const Radar = createAppIcon(RadarChartOutlined)
export const RefreshCw = createAppIcon(ReloadOutlined)
export const RotateCcw = createAppIcon(ReloadOutlined)
export const Save = createAppIcon(SaveOutlined)
export const Search = createAppIcon(SearchOutlined)
export const Settings = createAppIcon(SettingOutlined)
export const Square = createAppIcon(StopOutlined)
export const Trash2 = createAppIcon(DeleteOutlined)
export const TriangleAlert = createAppIcon(WarningOutlined)
export const Upload = createAppIcon(UploadOutlined)
export const Users = createAppIcon(TeamOutlined)
export const X = createAppIcon(CloseOutlined)
export const Zap = createAppIcon(ThunderboltOutlined)
