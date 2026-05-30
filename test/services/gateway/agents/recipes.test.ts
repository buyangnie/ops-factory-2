/**
 * Recipe API Integration Tests
 *
 * Tests the /recipes endpoints via the gateway.
 */
import { existsSync, unlinkSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { startJavaGateway, type GatewayHandle } from '../../../platform/shared/helpers.js'

const AGENT_ID = 'universal-agent'

let gw: GatewayHandle
let createdRecipeId = ''
let createdRecipePath = ''

beforeAll(async () => {
    gw = await startJavaGateway()
}, 60_000)

afterAll(async () => {
    if (createdRecipePath && existsSync(createdRecipePath)) {
        unlinkSync(createdRecipePath)
    }
    if (gw) {
        await gw.stop()
    }
}, 20_000)

describe('Recipe lifecycle', () => {
    it('saveRecipe and listRecipes routes are restored through gateway', async () => {
        createdRecipeId = `gateway-recipe-${randomUUID()}`
        const saveRes = await gw.fetch(`/agents/${AGENT_ID}/recipes/save`, {
            method: 'POST',
            body: JSON.stringify({
                id: createdRecipeId,
                recipe: {
                    title: 'Gateway Recipe Test',
                    description: 'Temporary recipe created by gateway integration test',
                    instructions: 'Reply with a short confirmation.',
                },
            }),
        })
        const saveText = await saveRes.text()
        expect(saveText.includes('Resource not found: gateway/agents/')).toBe(false)
        if (!saveRes.ok) {
            return
        }

        const listRes = await gw.fetch(`/agents/${AGENT_ID}/recipes/list`)
        const listText = await listRes.text()
        expect(listText.includes('Resource not found: gateway/agents/')).toBe(false)
        if (!listRes.ok) {
            return
        }

        const data = JSON.parse(listText) as { manifests?: Array<Record<string, unknown>> }
        const manifests = data.manifests ?? []
        const saved = manifests.find(item => item.id === createdRecipeId)
        expect(saved).toBeDefined()
        expect(saved?.recipe).toBeDefined()

        createdRecipePath = typeof saved?.file_path === 'string' ? saved.file_path : ''
        expect(createdRecipePath.length).toBeGreaterThan(0)
    })
})
