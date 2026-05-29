import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import type { ComponentProps } from 'react'
import MessageList from '../app/platform/chat/MessageList'
import { UserProvider } from '../app/platform/providers/UserContext'
import { PreviewProvider } from '../app/platform/providers/PreviewContext'
import i18n from '../i18n'
import type { ChatMessage } from '../types/message'

function renderMessageList(messages: ChatMessage[], props: Partial<ComponentProps<typeof MessageList>> = {}, withPreview = false) {
    const inner = <MessageList messages={messages} {...props} />
    const wrapped = withPreview ? <PreviewProvider>{inner}</PreviewProvider> : inner
    return render(
        <I18nextProvider i18n={i18n}>
            <UserProvider>
                {wrapped}
            </UserProvider>
        </I18nextProvider>
    )
}

function renderMessageListWithPreview(messages: ChatMessage[], props: Partial<ComponentProps<typeof MessageList>> = {}) {
    return renderMessageList(messages, props, true)
}

function rerenderWithPreview(view: ReturnType<typeof render>, messages: ChatMessage[], props: Partial<ComponentProps<typeof MessageList>> = {}) {
    view.rerender(
        <I18nextProvider i18n={i18n}>
            <UserProvider>
                <PreviewProvider>
                    <MessageList messages={messages} {...props} />
                </PreviewProvider>
            </UserProvider>
        </I18nextProvider>
    )
}

function createFetchMock(options: {
    persistedEntries?: Record<string, unknown[]>
    delayPersisted?: boolean
}) {
    let resolvePersisted: ((response: Response) => void) | null = null
    const persistedLoad = options.delayPersisted
        ? new Promise<Response>(resolve => { resolvePersisted = resolve })
        : null

    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        if (url.includes('/config')) {
            return Promise.resolve({ ok: false }) as Promise<Response>
        }
        if (url.includes('/file-capsules') && init?.method === 'POST') {
            return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
        }
        if (url.includes('/file-capsules') && options.persistedEntries) {
            if (persistedLoad) return persistedLoad
            return Promise.resolve({
                ok: true,
                json: async () => ({ entries: options.persistedEntries }),
            }) as Promise<Response>
        }
        if (url.includes('/file-capsules')) {
            return Promise.resolve({ ok: true, json: async () => ({ entries: {} }) }) as Promise<Response>
        }
        return Promise.resolve({ ok: true, json: async () => ({}) }) as Promise<Response>
    })

    return { fetchMock, resolvePersisted }
}

function createScrollContainer(scrollHeight = 600, clientHeight = 200) {
    const el = document.createElement('div')
    Object.defineProperty(el, 'scrollHeight', { configurable: true, value: scrollHeight })
    Object.defineProperty(el, 'clientHeight', { configurable: true, value: clientHeight })
    Object.defineProperty(el, 'scrollTop', { configurable: true, get: () => 0 })
    el.scrollTo = vi.fn()
    return el
}

function createScrollRoot() {
    const el = document.createElement('div')
    Object.defineProperty(el, 'scrollHeight', { configurable: true, value: 1200 })
    Object.defineProperty(el, 'clientHeight', { configurable: true, value: 600 })
    let top = 0
    Object.defineProperty(el, 'scrollTop', {
        configurable: true,
        get: () => top,
        set: (v: number) => { top = v },
    })
    el.scrollTo = vi.fn(({ top: t }: ScrollToOptions) => { top = Number(t ?? 0) })
    return el
}

function withScrollRootMock(scrollRoot: HTMLElement, fn: () => Promise<void>) {
    const original = Object.getOwnPropertyDescriptor(document, 'scrollingElement')
    Object.defineProperty(document, 'scrollingElement', { configurable: true, value: scrollRoot })
    return fn().finally(() => {
        if (original) {
            Object.defineProperty(document, 'scrollingElement', original)
        } else {
            // @ts-expect-error test cleanup for configurable property
            delete document.scrollingElement
        }
    })
}

describe('MessageList tool error rendering', () => {
    const originalScrollIntoView = Element.prototype.scrollIntoView
    const originalRequestAnimationFrame = window.requestAnimationFrame
    const originalCancelAnimationFrame = window.cancelAnimationFrame

    beforeEach(() => {
        Element.prototype.scrollIntoView = () => {}
        window.requestAnimationFrame = ((callback: FrameRequestCallback) => {
            callback(0)
            return 1
        }) as typeof window.requestAnimationFrame
        window.cancelAnimationFrame = (() => {}) as typeof window.cancelAnimationFrame
    })

    afterEach(() => {
        Element.prototype.scrollIntoView = originalScrollIntoView
        window.requestAnimationFrame = originalRequestAnimationFrame
        window.cancelAnimationFrame = originalCancelAnimationFrame
        vi.unstubAllGlobals()
    })

    it('renders tool steps as error when toolResult.isError is true', () => {

        const messages: ChatMessage[] = [
            {
                id: 'assistant-tool-request',
                role: 'assistant',
                content: [
                    {
                        type: 'toolRequest',
                        id: 'tool-1',
                        toolCall: {
                            status: 'completed',
                            value: {
                                name: 'developer__extension_manager',
                                arguments: {
                                    action: 'enable',
                                    extension_name: 'control_center',
                                },
                            },
                        },
                    },
                ],
            },
            {
                id: 'assistant-tool-response',
                role: 'assistant',
                content: [
                    {
                        type: 'toolResponse',
                        id: 'tool-1',
                        toolResult: {
                            isError: true,
                            value: {
                                content: [
                                    {
                                        type: 'text',
                                        text: 'Extension operation failed',
                                    },
                                ],
                            },
                        },
                    },
                ],
            },
        ]

        const { container } = renderMessageList(messages)
        const errorNode = container.querySelector('.process-step-node.error')
        expect(errorNode).toBeTruthy()
    })

    it('marks a thinking step complete once a following tool step is streaming', () => {
        const messages: ChatMessage[] = [
            {
                id: 'assistant-tool-chain',
                role: 'assistant',
                content: [
                    { type: 'thinking', thinking: '检查服务状态', signature: '' },
                    {
                        type: 'toolRequest',
                        id: 'tool-1',
                        toolCall: {
                            status: 'pending',
                            value: {
                                name: 'execute_remote_command',
                                arguments: { command: 'ps -ef' },
                            },
                        },
                    },
                ],
            },
        ]

        const { container } = renderMessageList(messages, { isLoading: true })

        expect(container.querySelector('.process-thinking-status-spinning')).toBeNull()
        expect(container.querySelector('.process-thinking-status-chevron')).toBeTruthy()
        expect(container.querySelector('.process-step-node.pending')).toBeTruthy()
    })

    it('allows completed thinking steps to collapse while a later tool step is streaming', () => {
        const messages: ChatMessage[] = [
            {
                id: 'assistant-tool-chain',
                role: 'assistant',
                content: [
                    { type: 'thinking', thinking: '检查服务状态', signature: '' },
                    {
                        type: 'toolRequest',
                        id: 'tool-1',
                        toolCall: {
                            status: 'pending',
                            value: {
                                name: 'execute_remote_command',
                                arguments: { command: 'ps -ef' },
                            },
                        },
                    },
                ],
            },
        ]

        renderMessageList(messages, { isLoading: true })

        fireEvent.click(screen.getByRole('button', { name: /思考过程|Thinking/ }))
        expect(screen.getByText('检查服务状态')).toBeTruthy()
        fireEvent.click(screen.getByRole('button', { name: /思考过程|Thinking/ }))

        expect(screen.queryByText('检查服务状态')).toBeNull()
    })

    it('keeps the current thinking step streaming when no later content exists yet', () => {
        const messages: ChatMessage[] = [
            {
                id: 'assistant-thinking',
                role: 'assistant',
                content: [{ type: 'thinking', thinking: '正在分析', signature: '' }],
            },
        ]

        const { container } = renderMessageList(messages, { isLoading: true })

        expect(container.querySelector('.process-thinking-status-spinning')).toBeTruthy()
    })

    it('marks fallback think-tag content complete once answer text follows it', () => {
        const messages: ChatMessage[] = [
            {
                id: 'assistant-think-tag',
                role: 'assistant',
                content: [{ type: 'text', text: '<think>分析中</think>结论正文' }],
            },
        ]

        const { container } = renderMessageList(messages, { isLoading: true })

        expect(container.querySelector('.process-thinking-status-spinning')).toBeNull()
        expect(container.querySelector('.process-thinking-status-chevron')).toBeTruthy()
        expect(screen.getByText('结论正文')).toBeTruthy()
    })

    it('marks a closed fallback think-tag complete even before answer text arrives', () => {
        const messages: ChatMessage[] = [
            {
                id: 'assistant-closed-think-tag',
                role: 'assistant',
                content: [{ type: 'text', text: '<think>分析中</think>' }],
            },
        ]

        const { container } = renderMessageList(messages, { isLoading: true })

        expect(container.querySelector('.process-thinking-status-spinning')).toBeNull()
        expect(container.querySelector('.process-thinking-status-chevron')).toBeTruthy()
    })

    it('scrolls to bottom again when a resumed session changes with the same message count', async () => {
        const scrollContainer = createScrollContainer()

        const firstSessionMessages: ChatMessage[] = [
            {
                id: 'user-1',
                role: 'user',
                content: [{ type: 'text', text: 'First session' }],
            },
            {
                id: 'assistant-1',
                role: 'assistant',
                content: [{ type: 'text', text: 'Reply one' }],
            },
        ]

        const secondSessionMessages: ChatMessage[] = [
            {
                id: 'user-2',
                role: 'user',
                content: [{ type: 'text', text: 'Second session' }],
            },
            {
                id: 'assistant-2',
                role: 'assistant',
                content: [{ type: 'text', text: 'Reply two' }],
            },
        ]

        const view = renderMessageList(firstSessionMessages, {
            agentId: 'agent-a',
            sessionId: 'session-a',
            scrollContainerRef: { current: scrollContainer },
        })

        await waitFor(() => {
            expect(scrollContainer.scrollTo).toHaveBeenCalledTimes(1)
        })

        view.rerender(
            <I18nextProvider i18n={i18n}>
                <UserProvider>
                    <MessageList
                        messages={secondSessionMessages}
                        agentId="agent-a"
                        sessionId="session-b"
                        scrollContainerRef={{ current: scrollContainer }}
                    />
                </UserProvider>
            </I18nextProvider>
        )

        await waitFor(() => {
            expect(scrollContainer.scrollTo).toHaveBeenCalledTimes(2)
        })
    })

    it('falls back to the document scroll root when the provided message container does not overflow', async () => {
        const scrollContainer = createScrollContainer(600, 600)
        const scrollRoot = createScrollRoot()

        const messages: ChatMessage[] = [
            {
                id: 'user-1',
                role: 'user',
                content: [{ type: 'text', text: 'Hello' }],
            },
            {
                id: 'assistant-1',
                role: 'assistant',
                content: [{ type: 'text', text: 'Hi there' }],
            },
        ]

        await withScrollRootMock(scrollRoot, async () => {
            renderMessageList(messages, {
                agentId: 'agent-a',
                sessionId: 'session-a',
                scrollContainerRef: { current: scrollContainer },
            })

            await waitFor(() => {
                expect(scrollContainer.scrollTo).not.toHaveBeenCalled()
                expect(scrollRoot.scrollTo).toHaveBeenCalledTimes(1)
            })
        })
    })

    it('falls back to the document scroll root when no message container is provided', async () => {
        const scrollRoot = createScrollRoot()

        const messages: ChatMessage[] = [
            {
                id: 'user-1',
                role: 'user',
                content: [{ type: 'text', text: 'Hello' }],
            },
            {
                id: 'assistant-1',
                role: 'assistant',
                content: [{ type: 'text', text: 'Hi there' }],
            },
        ]

        await withScrollRootMock(scrollRoot, async () => {
            renderMessageList(messages, {
                agentId: 'agent-a',
                sessionId: 'session-a',
            })

            await waitFor(() => {
                expect(scrollRoot.scrollTo).toHaveBeenCalledTimes(1)
            })
        })
    })

    it('renders and persists output file capsules from a live OutputFiles event', async () => {
        const { fetchMock } = createFetchMock({})
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const view = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        rerenderWithPreview(view, messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                files: [{
                    path: 'goose-intro.md',
                    name: 'goose-intro.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'goose-intro.md',
                }],
            },
        })

        await waitFor(() => {
            expect(view.container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('goose-intro.md')).toBeTruthy()
        })

        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalledWith(
                expect.stringContaining('/agents/universal-agent/file-capsules'),
                expect.objectContaining({
                    method: 'POST',
                    body: expect.stringContaining('"messageId":"assistant-final"'),
                })
            )
        })
    })

    it('attaches late OutputFiles to the assistant message with the matching request id', async () => {
        const { fetchMock } = createFetchMock({})
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-a',
                role: 'assistant',
                content: [{ type: 'text', text: 'First done' }],
                metadata: { requestId: 'req-a' },
            },
            {
                id: 'assistant-b',
                role: 'assistant',
                content: [{ type: 'text', text: 'Second done' }],
                metadata: { requestId: 'req-b' },
            },
        ]

        renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                requestId: 'req-a',
                files: [{
                    path: 'first-report.md',
                    name: 'first-report.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'first-report.md',
                }],
            },
        })

        await waitFor(() => {
            const postCall = fetchMock.mock.calls.find(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )
            expect(postCall).toBeTruthy()
            const body = JSON.parse(String(postCall?.[1]?.body))
            expect(body.messageId).toBe('assistant-a')
            expect(body.requestId).toBe('req-a')
        })
    })

    it('does not attach request-scoped OutputFiles when no assistant request id matches', async () => {
        const { fetchMock } = createFetchMock({})
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-latest',
                role: 'assistant',
                content: [{ type: 'text', text: 'Latest done' }],
                metadata: { requestId: 'req-latest' },
            },
        ]

        renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                requestId: 'req-missing',
                files: [{
                    path: 'missing-request.md',
                    name: 'missing-request.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'missing-request.md',
                }],
            },
        })

        await waitFor(() => {
            expect(fetchMock.mock.calls.some(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )).toBe(false)
        })
        expect(screen.queryByText('missing-request.md')).toBeNull()
    })

    it('keeps request-scoped OutputFiles pending until the matching assistant message appears', async () => {
        const { fetchMock } = createFetchMock({})
        vi.stubGlobal('fetch', fetchMock)

        const initialMessages: ChatMessage[] = [
            {
                id: 'assistant-latest',
                role: 'assistant',
                content: [{ type: 'text', text: 'Latest done' }],
                metadata: { requestId: 'req-latest' },
            },
        ]

        const view = renderMessageListWithPreview(initialMessages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                requestId: 'req-pending',
                files: [{
                    path: 'pending-report.md',
                    name: 'pending-report.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'pending-report.md',
                }],
            },
        })

        await waitFor(() => {
            expect(fetchMock.mock.calls.some(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )).toBe(false)
        })
        expect(screen.queryByText('pending-report.md')).toBeNull()

        const updatedMessages: ChatMessage[] = [
            {
                id: 'assistant-match',
                role: 'assistant',
                content: [{ type: 'text', text: 'Matched done' }],
                metadata: { requestId: 'req-pending' },
            },
            ...initialMessages,
        ]

        rerenderWithPreview(view, updatedMessages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: null,
        })

        await waitFor(() => {
            const postCall = fetchMock.mock.calls.find(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )
            expect(postCall).toBeTruthy()
            const body = JSON.parse(String(postCall?.[1]?.body))
            expect(body.messageId).toBe('assistant-match')
        })
        expect(screen.getByText('pending-report.md')).toBeTruthy()
    })

    it('does not fall back a pending request-scoped file to a later assistant reply', async () => {
        const { fetchMock } = createFetchMock({})
        vi.stubGlobal('fetch', fetchMock)

        const firstMessages: ChatMessage[] = [
            {
                id: 'assistant-first',
                role: 'assistant',
                content: [{ type: 'text', text: 'First done' }],
                metadata: { requestId: 'req-first' },
            },
        ]

        const view = renderMessageListWithPreview(firstMessages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                requestId: 'req-missing',
                files: [{
                    path: 'first-report.md',
                    name: 'first-report.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'first-report.md',
                }],
            },
        })

        const secondMessages: ChatMessage[] = [
            ...firstMessages,
            {
                id: 'assistant-second',
                role: 'assistant',
                content: [{ type: 'text', text: 'Second done' }],
                metadata: { requestId: 'req-second' },
            },
        ]

        rerenderWithPreview(view, secondMessages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: null,
        })

        await waitFor(() => {
            expect(fetchMock.mock.calls.some(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )).toBe(false)
        })
        expect(screen.queryByText('first-report.md')).toBeNull()
    })

    it('restores output file capsules from persisted session entries', async () => {
        const { fetchMock } = createFetchMock({
            persistedEntries: {
                'assistant-final': [{
                    path: 'goose-intro.md',
                    name: 'goose-intro.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'goose-intro.md',
                }],
            },
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const { container } = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        await waitFor(() => {
            expect(container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('goose-intro.md')).toBeTruthy()
        })
    })

    it('restores output file capsules from persisted request-scoped entries', async () => {
        const { fetchMock } = createFetchMock({
            persistedEntries: {
                'stale-message-id': [{
                    path: 'goose-intro.md',
                    name: 'goose-intro.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'goose-intro.md',
                    requestId: 'req-final',
                }],
            },
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
                metadata: { requestId: 'req-final' },
            },
        ]

        const { container } = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        await waitFor(() => {
            expect(container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('goose-intro.md')).toBeTruthy()
        })
    })

    it('restores output file capsules by matching file names when persisted message ids drift', async () => {
        const { fetchMock } = createFetchMock({
            persistedEntries: {
                'stale-message-id': [{
                    path: 'reports/system-health-analysis-20260528211302.md',
                    name: 'system-health-analysis-20260528211302.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'system-health-analysis-20260528211302.md',
                }],
            },
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-summary',
                role: 'assistant',
                content: [{
                    type: 'text',
                    text: '报告已生成，文件名为 system-health-analysis-20260528211302.md，可在 output 目录查看。',
                }],
            },
        ]

        const { container } = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-legacy',
        })

        await waitFor(() => {
            expect(container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('system-health-analysis-20260528211302.md')).toBeTruthy()
        })
    })

    it('restores output file capsules when the persisted id belongs to a merged final answer', async () => {
        const { fetchMock } = createFetchMock({
            persistedEntries: {
                'gen-1777386391-Z5uapXjSllauI4F0BQjr': [{
                    path: 'example.md',
                    name: 'example.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'example.md',
                }],
            },
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'msg_20260428_2_1f25c68c-3454-4d42-aa0a-2c611a8eb1d9',
                role: 'user',
                content: [{ type: 'text', text: '随便输出一个 md 文件' }],
            },
            {
                id: 'gen-1777386384-TVNvBJlFddfstgvMjdrn',
                role: 'assistant',
                content: [
                    { type: 'thinking', thinking: '用户要求输出一个 md 文件', signature: '' },
                    { type: 'text', text: '我来为你创建一个简单的 markdown 文件：' },
                ],
            },
            {
                id: 'msg_8150af22-1207-4cbe-befd-8458b13c15b4',
                role: 'assistant',
                content: [{ type: 'thinking', thinking: '准备写入文件', signature: '' }],
            },
            {
                id: 'msg_67b4c79b-41bf-44eb-ab8d-ddd4e9275bb9',
                role: 'assistant',
                content: [
                    { type: 'thinking', thinking: '调用写文件工具', signature: '' },
                    {
                        type: 'toolRequest',
                        id: 'call_ec5140b3a82046f29905a5e5',
                        toolCall: {
                            status: 'success',
                            value: {
                                name: 'write',
                                arguments: { path: 'example.md' },
                            },
                        },
                    },
                ],
            },
            {
                id: 'msg_3763c20b-8ac6-4148-b3c9-a730f177caff',
                role: 'user',
                content: [
                    {
                        type: 'toolResponse',
                        id: 'call_ec5140b3a82046f29905a5e5',
                        toolResult: {
                            status: 'success',
                            value: {
                                content: [{ type: 'text', text: 'Created example.md' }],
                            },
                        },
                    },
                ],
            },
            {
                id: 'gen-1777386391-Z5uapXjSllauI4F0BQjr',
                role: 'assistant',
                content: [{ type: 'text', text: '已创建示例 Markdown 文件：example.md' }],
            },
        ]

        const { container } = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: '20260428_2',
        })

        await waitFor(() => {
            expect(container.querySelector('.file-capsule')).toBeTruthy()
            expect(screen.getByText('example.md')).toBeTruthy()
        })
    })

    it('merges live output files with existing persisted capsules for the same message', async () => {
        const { fetchMock } = createFetchMock({
            persistedEntries: {
                'assistant-final': [{
                    path: 'existing.md',
                    name: 'existing.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'existing.md',
                }],
            },
        })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const view = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        await waitFor(() => {
            expect(screen.getByText('existing.md')).toBeTruthy()
        })

        rerenderWithPreview(view, messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                files: [{
                    path: 'new.md',
                    name: 'new.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'new.md',
                }],
            },
        })

        await waitFor(() => {
            expect(screen.getByText('existing.md')).toBeTruthy()
            expect(screen.getByText('new.md')).toBeTruthy()
        })

        await waitFor(() => {
            const postCall = fetchMock.mock.calls.find(([input, init]) =>
                String(input).includes('/file-capsules') && init?.method === 'POST'
            )
            expect(postCall).toBeTruthy()
            const body = JSON.parse(String(postCall?.[1]?.body))
            expect(body.files.map((file: { name: string }) => file.name)).toEqual(['existing.md', 'new.md'])
        })
    })

    it('keeps live output file capsules when a stale persisted load resolves later', async () => {
        const { fetchMock, resolvePersisted } = createFetchMock({ delayPersisted: true })
        vi.stubGlobal('fetch', fetchMock)

        const messages: ChatMessage[] = [
            {
                id: 'assistant-final',
                role: 'assistant',
                content: [{ type: 'text', text: 'Done' }],
            },
        ]

        const view = renderMessageListWithPreview(messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
        })

        rerenderWithPreview(view, messages, {
            agentId: 'universal-agent',
            sessionId: 'session-1',
            outputFilesEvent: {
                sessionId: 'session-1',
                files: [{
                    path: 'aaa.md',
                    name: 'aaa.md',
                    ext: 'md',
                    rootId: 'workingDir',
                    displayPath: 'aaa.md',
                }],
            },
        })

        await waitFor(() => {
            expect(screen.getByText('aaa.md')).toBeTruthy()
        })

        await act(async () => {
            resolvePersisted?.({
                ok: true,
                json: async () => ({
                    entries: {
                        'assistant-final': [{
                            path: 'old.md',
                            name: 'old.md',
                            ext: 'md',
                            rootId: 'workingDir',
                            displayPath: 'old.md',
                        }],
                    },
                }),
            } as Response)
        })

        await waitFor(() => {
            expect(screen.getByText('aaa.md')).toBeTruthy()
            expect(screen.queryByText('old.md')).toBeNull()
        })
    })

})
