/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

import process from 'node:process'
import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js'
import { tools, dispatch } from './handlers.js'

// ASCII-safe stdout for Windows
type StdoutWriteArgs = Parameters<typeof process.stdout.write>
type StdoutWriteReturn = ReturnType<typeof process.stdout.write>

const _origWrite = process.stdout.write.bind(process.stdout)
function escapeNonAscii(s: string): string {
  return s.replace(/[\u007f-\uffff]/g, c => '\\u' + ('0000' + c.charCodeAt(0).toString(16)).slice(-4))
}
process.stdout.write = ((...args: StdoutWriteArgs): StdoutWriteReturn => {
  const chunk = args[0]
  if (typeof chunk === 'string') {
    const patched = [escapeNonAscii(chunk), ...args.slice(1)] as unknown as StdoutWriteArgs
    return _origWrite(...patched)
  }
  return _origWrite(...args)
}) as typeof process.stdout.write

const server = new Server(
  { name: 'browser-use', version: '1.0.0' },
  { capabilities: { tools: {} } },
)

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools }))

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params
  try {
    const result = await dispatch(name, args ?? {})
    return { content: [{ type: 'text', text: result }] }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    return { content: [{ type: 'text', text: `Error: ${message}` }] }
  }
})

const transport = new StdioServerTransport()
await server.connect(transport)
process.stderr.write('[browser-use] MCP server running on stdio\n')
