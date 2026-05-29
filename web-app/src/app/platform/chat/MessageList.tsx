import { useRef, useEffect, useMemo, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import Message from './Message'
import { ChatState, type OutputFilesEvent, isChatOrderDebugEnabled, buildChatMessageOrderDigest } from './useChat'
import GooseAvatarIcon from './GooseAvatarIcon'
import { extractFetchedDocuments, extractSourceDocuments, type Citation } from '../../../utils/citationParser'
import { getReasoningContent, getThinkingContent, hasDisplayTextContent, hasTextContent, hasToolContent } from '../../../utils/messageContent'
import { useUser } from '../providers/UserContext'
import { runtime } from '../../../config/runtime'
import type { ChatMessage, DetectedFile, ToolResponseMap } from '../../../types/message'

const BOTTOM_THRESHOLD_PX = 24
const PENDING_OUTPUT_FILES_TTL_MS = 60_000

function isSameDay(a: number, b: number): boolean {
    const da = new Date(a * 1000)
    const db = new Date(b * 1000)
    return da.getFullYear() === db.getFullYear() &&
           da.getMonth() === db.getMonth() &&
           da.getDate() === db.getDate()
}

type ScrollContainerRef = {
    current: HTMLDivElement | null
}

type ActiveScrollElement = HTMLElement

function resolveActiveScrollElement(containerRef?: ScrollContainerRef): ActiveScrollElement | null {
    const container = containerRef?.current
    if (container) {
        const canContainerScroll = container.scrollHeight - container.clientHeight > BOTTOM_THRESHOLD_PX
        if (canContainerScroll) {
        return container
        }
    }

    return document.scrollingElement instanceof HTMLElement ? document.scrollingElement : document.documentElement
}

function setScrollTop(element: ActiveScrollElement, top: number, behavior: ScrollBehavior) {
    if (typeof element.scrollTo === 'function') {
        element.scrollTo({ top, behavior })
        return
    }

    element.scrollTop = top
}

const fileIdentity = (file: DetectedFile): string =>
    `${file.rootId ?? ''}:${file.path || file.displayPath || file.name}`

const fileEventSignature = (files: DetectedFile[]): string =>
    files.map(fileIdentity).join('|')

const buildPendingOutputFilesKey = (event: OutputFilesEvent): string =>
    `${event.sessionId}:${event.requestId ?? 'no-request'}:${fileEventSignature(event.files)}`

const mergeDetectedFiles = (existing: DetectedFile[], incoming: DetectedFile[]): DetectedFile[] => {
    const order: string[] = []
    const byKey = new Map<string, DetectedFile>()

    for (const file of [...existing, ...incoming]) {
        const key = fileIdentity(file)
        if (!byKey.has(key)) {
            order.push(key)
        }
        byKey.set(key, file)
    }

    return order.map(key => byKey.get(key)!)
}

const messageIdentityIds = (message: ChatMessage): string[] => {
    const ids = new Set<string>()
    if (message.id) ids.add(message.id)
    for (const id of message.metadata?.sourceMessageIds ?? []) {
        if (id) ids.add(id)
    }
    return Array.from(ids)
}

function getMessageOutputFiles(
    message: ChatMessage,
    messageOutputFiles: Map<string, DetectedFile[]>
): DetectedFile[] | undefined {
    const files: DetectedFile[] = []
    for (const id of messageIdentityIds(message)) {
        const matched = messageOutputFiles.get(id)
        if (matched) {
            files.push(...matched)
        }
    }
    return files.length > 0 ? mergeDetectedFiles([], files) : undefined
}

function hasOnlyToolResponse(message: ChatMessage): boolean {
    return message.role === 'user' &&
        message.content.length > 0 &&
        message.content.every(content => content.type === 'toolResponse')
}

function hasOnlyProcessContent(message: ChatMessage): boolean {
    return message.role === 'assistant' &&
        !hasDisplayTextContent(message) &&
        !message.content.some(content => content.type === 'toolRequest') &&
        (!!getReasoningContent(message) || !!getThinkingContent(message))
}

function hasToolRequest(message: ChatMessage): boolean {
    return message.role === 'assistant' &&
        message.content.some(content => content.type === 'toolRequest')
}

function buildDisplayMessages(messages: ChatMessage[]): ChatMessage[] {
    const displayMessages: ChatMessage[] = []

    for (let i = 0; i < messages.length; i++) {
        const message = messages[i]

        if (hasOnlyToolResponse(message)) {
            continue
        }

        const startsToolChain = hasOnlyProcessContent(message) ||
            (hasToolRequest(message) && !hasDisplayTextContent(message))

        if (!startsToolChain) {
            displayMessages.push(message)
            continue
        }

        let nextIndex = i
        let sawToolRequest = false
        const mergedContent = [...message.content]
        const sourceMessageIds = message.id ? [message.id] : []

        if (hasToolRequest(message)) {
            sawToolRequest = true
        }

        while (nextIndex + 1 < messages.length) {
            const nextMessage = messages[nextIndex + 1]

            if (hasOnlyToolResponse(nextMessage)) {
                if (nextMessage.id) {
                    sourceMessageIds.push(nextMessage.id)
                }
                nextIndex += 1
                continue
            }

            const nextHasDisplayText = hasDisplayTextContent(nextMessage)
            const canMergeAssistant = hasOnlyProcessContent(nextMessage) ||
                (hasToolRequest(nextMessage) && !nextHasDisplayText)

            if (!canMergeAssistant) {
                const canMergeFinalAnswer = sawToolRequest &&
                    nextMessage.role === 'assistant' &&
                    nextHasDisplayText

                if (canMergeFinalAnswer) {
                    if (nextMessage.id) {
                        sourceMessageIds.push(nextMessage.id)
                    }
                    mergedContent.push(...nextMessage.content)
                    nextIndex += 1
                }

                break
            }

            if (hasToolRequest(nextMessage)) {
                sawToolRequest = true
            }

            if (nextMessage.id) {
                sourceMessageIds.push(nextMessage.id)
            }
            mergedContent.push(...nextMessage.content)
            nextIndex += 1
        }

        if (sawToolRequest) {
            displayMessages.push({
                ...message,
                id: message.id || `merged-chain-${i}`,
                content: mergedContent,
                metadata: {
                    ...(message.metadata || {}),
                    sourceMessageIds,
                },
            })
            i = nextIndex
            continue
        }

        displayMessages.push(message)
    }

    return displayMessages
}

interface MessageListProps {
    messages: ChatMessage[]
    isLoading?: boolean
    chatState?: ChatState
    agentId?: string
    sessionId?: string | null
    outputFilesEvent?: OutputFilesEvent | null
    onRetry?: () => void
    onCancelRequest?: () => void
    scrollContainerRef?: ScrollContainerRef
    showAnchorSpacer?: boolean
}

interface PendingOutputFilesEntry {
    event: OutputFilesEvent
    receivedAt: number
}

interface PersistedDetectedFile extends DetectedFile {
    requestId?: string
}

interface PersistedOutputFilesEntry {
    messageId: string
    requestId?: string
    files: DetectedFile[]
}

const buildDisplayMessageId = (message: ChatMessage, index: number): string =>
    message.id ?? `display-message-${index}`

const getMessageSearchText = (message: ChatMessage): string => {
    const parts: string[] = []
    for (const content of message.content) {
        if (content.type === 'text' && content.text) {
            parts.push(content.text.toLowerCase())
        }
    }
    return parts.join('\n')
}

const findAssistantMessageIdByFileMention = (
    displayMessages: ChatMessage[],
    files: DetectedFile[]
): string | undefined => {
    const needles = Array.from(new Set(
        files.flatMap(file => [file.name, file.displayPath, file.path])
            .filter((value): value is string => Boolean(value))
            .map(value => value.toLowerCase())
    ))

    if (needles.length === 0) {
        return undefined
    }

    for (let i = displayMessages.length - 1; i >= 0; i--) {
        const message = displayMessages[i]
        if (message.role !== 'assistant' || !hasDisplayTextContent(message)) {
            continue
        }

        const haystack = getMessageSearchText(message)
        if (needles.some(needle => haystack.includes(needle))) {
            return buildDisplayMessageId(message, i)
        }
    }

    return undefined
}

const parsePersistedOutputFiles = (
    entries: Record<string, PersistedDetectedFile[]>
): PersistedOutputFilesEntry[] => {
    const persistedEntries: PersistedOutputFilesEntry[] = []

    for (const [messageId, files] of Object.entries(entries)) {
        if (!Array.isArray(files) || files.length === 0) {
            continue
        }

        persistedEntries.push({
            messageId,
            requestId: files.find(file => typeof file.requestId === 'string' && file.requestId)?.requestId,
            files: files.map(({ requestId: _requestId, ...file }) => file),
        })
    }

    return persistedEntries
}

const resolvePersistedOutputFilesTargetMessageId = (
    entry: PersistedOutputFilesEntry,
    displayMessages: ChatMessage[],
    assistantMessageIdByRequestId: Map<string, string>
): string | undefined => {
    if (entry.requestId) {
        const matchedByRequestId = assistantMessageIdByRequestId.get(entry.requestId)
        if (matchedByRequestId) {
            return matchedByRequestId
        }
    }

    for (let i = 0; i < displayMessages.length; i++) {
        const message = displayMessages[i]
        if (messageIdentityIds(message).includes(entry.messageId)) {
            return buildDisplayMessageId(message, i)
        }
    }

    return findAssistantMessageIdByFileMention(displayMessages, entry.files)
}

export default function MessageList({
    messages,
    isLoading = false,
    chatState = ChatState.Idle,
    agentId,
    sessionId,
    outputFilesEvent,
    onRetry,
    onCancelRequest,
    scrollContainerRef,
    showAnchorSpacer = false,
}: MessageListProps) {
    const { t } = useTranslation()
    const { userId } = useUser()
    const containerRef = useRef<HTMLDivElement>(null)
    const bottomRef = useRef<HTMLDivElement>(null)
    const [messageOutputFiles, setMessageOutputFiles] = useState<Map<string, DetectedFile[]>>(new Map())
    const [pendingOutputFiles, setPendingOutputFiles] = useState<Map<string, PendingOutputFilesEntry>>(new Map())
    const processedOutputFilesRef = useRef<Set<string>>(new Set())
    const liveOutputFileMessageIdsRef = useRef<Set<string>>(new Set())
    const hasInitializedScrollRef = useRef(false)

    const visibleMessages = useMemo(() => {
        const userVisibleMessages = messages.filter(msg => !msg.metadata || msg.metadata.userVisible !== false)

        return userVisibleMessages.filter((msg, index) => {
            if (msg.role !== 'assistant') {
                return true
            }

            const hasPrimaryContent = hasTextContent(msg) || hasToolContent(msg)
            if (hasPrimaryContent) {
                return true
            }

            const hasReasoningOnly = !!getReasoningContent(msg) || !!getThinkingContent(msg)
            if (hasReasoningOnly) {
                return true
            }

            const isLastMessage = index === userVisibleMessages.length - 1
            return isLastMessage
        })
    }, [messages])

    const displayMessages = useMemo(() => buildDisplayMessages(visibleMessages), [visibleMessages])

    useEffect(() => {
        if (!isChatOrderDebugEnabled()) return
        console.debug('[chat-order] message-list', {
            sessionId,
            agentId,
            raw: buildChatMessageOrderDigest(messages),
            visible: buildChatMessageOrderDigest(visibleMessages),
            display: buildChatMessageOrderDigest(displayMessages),
        })
    }, [agentId, sessionId, messages, visibleMessages, displayMessages])

    const finalAssistantTextMessageId = useMemo(() => {
        for (let i = displayMessages.length - 1; i >= 0; i--) {
            const msg = displayMessages[i]
            if (msg.role === 'assistant' && hasDisplayTextContent(msg)) {
                return msg.id
            }
        }
        return undefined
    }, [displayMessages])

    const assistantMessageIdByRequestId = useMemo(() => {
        const next = new Map<string, string>()
        for (let i = displayMessages.length - 1; i >= 0; i--) {
            const msg = displayMessages[i]
            const requestId = msg.metadata?.requestId
            if (
                msg.role === 'assistant' &&
                hasDisplayTextContent(msg) &&
                requestId &&
                !next.has(requestId)
            ) {
                next.set(requestId, buildDisplayMessageId(msg, i))
            }
        }
        return next
    }, [displayMessages])

    const toolResponses = useMemo<ToolResponseMap>(() => {
        const map: ToolResponseMap = new Map()
        for (const msg of visibleMessages) {
            for (const content of msg.content) {
                if (content.type === 'toolResponse' && content.id) {
                    const toolResult = content.toolResult
                    const toolResultIsError = Boolean(
                        toolResult?.status === 'error' ||
                        (toolResult && typeof toolResult === 'object' && 'isError' in toolResult && toolResult.isError === true)
                    )
                    map.set(content.id, {
                        result: toolResult?.status === 'success' ? toolResult.value : toolResult,
                        isError: toolResultIsError
                    })
                }
            }
        }
        return map
    }, [visibleMessages])

    // Extract source documents from tool call results for fallback references
    const sourceDocuments = useMemo<Citation[]>(() => {
        return extractSourceDocuments(visibleMessages)
    }, [visibleMessages])

    const fetchedDocuments = useMemo<Citation[]>(() => {
        return extractFetchedDocuments(visibleMessages)
    }, [visibleMessages])

    const gatewayHeaders = useCallback((): Record<string, string> => {
        const h: Record<string, string> = { 'x-secret-key': runtime.GATEWAY_SECRET_KEY }
        if (userId) h['x-user-id'] = userId
        return h
    }, [userId])

    const scrollToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
        const scrollContainer = resolveActiveScrollElement(scrollContainerRef)
        if (scrollContainer) {
            setScrollTop(scrollContainer, scrollContainer.scrollHeight, behavior)
        } else if (bottomRef.current) {
            bottomRef.current.scrollIntoView({ behavior, block: 'end' })
        }
    }, [scrollContainerRef])

    // ── Real-time: stage OutputFiles SSE event ──────────────────────
    // Request-scoped events stay pending until their exact assistant message is present.
    useEffect(() => {
        if (!outputFilesEvent || outputFilesEvent.files.length === 0) return
        if (!sessionId || outputFilesEvent.sessionId !== sessionId) return

        const targetMessageId = outputFilesEvent.requestId
            ? assistantMessageIdByRequestId.get(outputFilesEvent.requestId)
            : finalAssistantTextMessageId

        const files: DetectedFile[] = outputFilesEvent.files.map(f => ({
            path: f.path,
            name: f.name,
            ext: f.ext,
            rootId: f.rootId,
            displayPath: f.displayPath,
        }))

        if (targetMessageId) {
            const eventKey = `${outputFilesEvent.sessionId}:${targetMessageId}:${fileEventSignature(files)}`
            if (processedOutputFilesRef.current.has(eventKey)) {
                return
            }

            processedOutputFilesRef.current.add(eventKey)
            liveOutputFileMessageIdsRef.current.add(targetMessageId)

            const filesToPersist = mergeDetectedFiles(messageOutputFiles.get(targetMessageId) ?? [], files)
            setMessageOutputFiles(prev => {
                const next = new Map(prev)
                next.set(targetMessageId, mergeDetectedFiles(next.get(targetMessageId) ?? [], files))
                return next
            })

            fetch(`${runtime.GATEWAY_URL}/agents/${agentId}/file-capsules`, {
                method: 'POST',
                headers: { ...gatewayHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionId: outputFilesEvent.sessionId,
                    requestId: outputFilesEvent.requestId,
                    messageId: targetMessageId,
                    files: filesToPersist,
                }),
            }).catch(() => { /* best-effort persistence */ })
            return
        }

        const pendingKey = buildPendingOutputFilesKey(outputFilesEvent)
        setPendingOutputFiles(prev => {
            const existing = prev.get(pendingKey)
            if (
                existing &&
                existing.event.requestId === outputFilesEvent.requestId &&
                existing.event.sessionId === outputFilesEvent.sessionId &&
                fileEventSignature(existing.event.files) === fileEventSignature(outputFilesEvent.files)
            ) {
                return prev
            }
            const next = new Map(prev)
            next.set(pendingKey, {
                event: outputFilesEvent,
                receivedAt: Date.now(),
            })
            return next
        })
    }, [
        agentId,
        sessionId,
        outputFilesEvent,
        assistantMessageIdByRequestId,
        finalAssistantTextMessageId,
        gatewayHeaders,
        messageOutputFiles,
    ])

    // ── Real-time: resolve staged OutputFiles to the right assistant message ──
    useEffect(() => {
        if (!agentId || !sessionId || pendingOutputFiles.size === 0) return

        const now = Date.now()
        const remaining = new Map<string, PendingOutputFilesEntry>()
        const nextMessageOutputFiles = new Map(messageOutputFiles)
        const payloads: Array<{ sessionId: string; requestId?: string; messageId: string; files: DetectedFile[] }> = []

        for (const [pendingKey, entry] of pendingOutputFiles.entries()) {
            if (entry.event.sessionId !== sessionId) {
                continue
            }
            if (now - entry.receivedAt > PENDING_OUTPUT_FILES_TTL_MS) {
                continue
            }

            const targetMessageId = entry.event.requestId
                ? assistantMessageIdByRequestId.get(entry.event.requestId)
                : finalAssistantTextMessageId

            if (!targetMessageId) {
                remaining.set(pendingKey, entry)
                continue
            }

            const files: DetectedFile[] = entry.event.files.map(f => ({
                path: f.path,
                name: f.name,
                ext: f.ext,
                rootId: f.rootId,
                displayPath: f.displayPath,
            }))

            const eventKey = `${entry.event.sessionId}:${targetMessageId}:${fileEventSignature(files)}`
            if (processedOutputFilesRef.current.has(eventKey)) {
                continue
            }

            processedOutputFilesRef.current.add(eventKey)
            liveOutputFileMessageIdsRef.current.add(targetMessageId)

            const filesToPersist = mergeDetectedFiles(nextMessageOutputFiles.get(targetMessageId) ?? [], files)
            nextMessageOutputFiles.set(targetMessageId, filesToPersist)
            payloads.push({
                sessionId: entry.event.sessionId,
                requestId: entry.event.requestId,
                messageId: targetMessageId,
                files: filesToPersist,
            })
        }

        if (payloads.length > 0) {
            setMessageOutputFiles(nextMessageOutputFiles)
            for (const payload of payloads) {
                fetch(`${runtime.GATEWAY_URL}/agents/${agentId}/file-capsules`, {
                    method: 'POST',
                    headers: { ...gatewayHeaders(), 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload),
                }).catch(() => { /* best-effort persistence */ })
            }
        }

        if (payloads.length > 0 || remaining.size !== pendingOutputFiles.size) {
            setPendingOutputFiles(remaining)
        }
    }, [
        agentId,
        sessionId,
        pendingOutputFiles,
        messageOutputFiles,
        assistantMessageIdByRequestId,
        finalAssistantTextMessageId,
        gatewayHeaders,
    ])

    // ── Resume: load persisted file capsules from gateway ───────────
    useEffect(() => {
        if (!agentId || !sessionId || isLoading) return
        // Only fetch on initial load when we have messages but no output files yet
        if (messageOutputFiles.size > 0 || displayMessages.length === 0) return

        let cancelled = false
        const loadPersistedCapsules = async () => {
            try {
                const res = await fetch(
                    `${runtime.GATEWAY_URL}/agents/${agentId}/file-capsules?sessionId=${encodeURIComponent(sessionId)}`,
                    { headers: gatewayHeaders() }
                )
                if (!res.ok || cancelled) return
                const data = await res.json() as { entries?: Record<string, PersistedDetectedFile[]> }
                if (!data.entries || cancelled) return

                const map = new Map<string, DetectedFile[]>()
                for (const entry of parsePersistedOutputFiles(data.entries)) {
                    const targetMessageId = resolvePersistedOutputFilesTargetMessageId(
                        entry,
                        displayMessages,
                        assistantMessageIdByRequestId
                    )
                    if (!targetMessageId) {
                        continue
                    }

                    map.set(
                        targetMessageId,
                        mergeDetectedFiles(map.get(targetMessageId) ?? [], entry.files)
                    )
                }
                if (map.size > 0) {
                    setMessageOutputFiles(prev => {
                        const next = new Map(map)
                        for (const [messageId, files] of prev.entries()) {
                            if (files.length > 0) {
                                if (liveOutputFileMessageIdsRef.current.has(messageId)) {
                                    next.set(messageId, files)
                                } else {
                                    next.set(messageId, mergeDetectedFiles(next.get(messageId) ?? [], files))
                                }
                            }
                        }
                        return next
                    })
                }
            } catch {
                /* best-effort resume */
            }
        }

        loadPersistedCapsules()
        return () => { cancelled = true }
    }, [agentId, sessionId, isLoading, displayMessages, assistantMessageIdByRequestId, gatewayHeaders])

    // Reset state when agent or session changes
    useEffect(() => {
        setMessageOutputFiles(new Map())
        setPendingOutputFiles(new Map())
        processedOutputFilesRef.current = new Set()
        liveOutputFileMessageIdsRef.current = new Set()
        hasInitializedScrollRef.current = false
    }, [agentId, sessionId])

    useEffect(() => {
        if (hasInitializedScrollRef.current || displayMessages.length === 0) return

        hasInitializedScrollRef.current = true
        const frame = window.requestAnimationFrame(() => {
            scrollToBottom('auto')
        })

        return () => window.cancelAnimationFrame(frame)
    }, [agentId, sessionId, displayMessages.length, scrollToBottom])

    if (displayMessages.length === 0 && !isLoading) {
        return (
            <div className="empty-state">
                <svg
                    className="empty-state-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                >
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
                <h3 className="empty-state-title">{t('chat.noMessages')}</h3>
                <p className="empty-state-description">
                    {t('chat.startConversation')}
                </p>
            </div>
        )
    }

    return (
        <div className="chat-messages" ref={containerRef}>
            {displayMessages.map((message, index) => {
                const outputFiles = getMessageOutputFiles(message, messageOutputFiles)
                const isLastAssistant =
                    isLoading &&
                    message.role === 'assistant' &&
                    index === displayMessages.length - 1
                const isFinalAssistantResponse =
                    message.role === 'assistant' &&
                    !!message.id &&
                    message.id === finalAssistantTextMessageId
                const hasOutputFiles = !!outputFiles?.length
                const isContinuation =
                    message.role === 'assistant' &&
                    index > 0 &&
                    displayMessages[index - 1].role === 'assistant'
                const isLastInGroup =
                    message.role !== 'assistant' ||
                    index === displayMessages.length - 1 ||
                    displayMessages[index + 1]?.role !== 'assistant'
                const prevCreated = displayMessages[index - 1]?.created
                const showDateInTimestamp =
                    prevCreated != null &&
                    message.created != null &&
                    !isSameDay(prevCreated, message.created)
                return (
                    <div
                        key={message.id || index}
                        data-message-id={message.id || index}
                    >
                        <Message
                            message={message}
                            toolResponses={toolResponses}
                            agentId={agentId}
                            userId={userId}
                            isStreaming={isLastAssistant}
                            onRetry={message.role === 'assistant' && index === displayMessages.length - 1 ? onRetry : undefined}
                            onCancelRequest={message.role === 'assistant' && index === displayMessages.length - 1 ? onCancelRequest : undefined}
                            sourceDocuments={isFinalAssistantResponse ? sourceDocuments : undefined}
                            fetchedDocuments={isFinalAssistantResponse ? fetchedDocuments : undefined}
                            outputFiles={outputFiles}
                            showFileCapsules={hasOutputFiles}
                            showDateInTimestamp={showDateInTimestamp}
                            isContinuation={isContinuation}
                            isLastInGroup={isLastInGroup}
                        />
                    </div>
                )
            })}

            {isLoading && displayMessages[displayMessages.length - 1]?.role !== 'assistant' && (
                <div data-message-id="loading-placeholder">
                    <div className="message assistant animate-fade-in">
                        <div className="message-avatar">
                            <GooseAvatarIcon className="message-avatar-icon" />
                        </div>
                        <div className="message-body">
                            <div className="message-content">
                                <div className="loading-dots">
                                    <span></span>
                                    <span></span>
                                    <span></span>
                                </div>
                                {chatState === ChatState.Thinking && (
                                    <div className="loading-status-text">{t('chat.thinking')}</div>
                                )}
                                {chatState === ChatState.Compacting && (
                                    <div className="loading-status-text">{t('chat.compactingContext')}</div>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {showAnchorSpacer && <div className="chat-anchor-spacer" aria-hidden="true" />}

            <div ref={bottomRef} />
        </div>
    )
}
