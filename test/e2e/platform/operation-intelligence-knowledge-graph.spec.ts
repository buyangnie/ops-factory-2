import { expect, test, type Page } from '@playwright/test'

const USER_STORAGE_KEY = 'opsfactory:userId'

async function login(page: Page) {
  await page.goto('/#/')
  await page.evaluate(([storageKey, userId]) => {
    localStorage.setItem(storageKey, userId)
  }, [USER_STORAGE_KEY, 'kg-e2e-user'])
}

async function mockKnowledgeGraphApi(page: Page) {
  await page.route('**/gateway/me', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ userId: 'kg-e2e-user', role: 'user' }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/environments?ontologyId=b2b-callchain-v1', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        result: [
          {
            envCode: 'prod',
            envName: 'Production',
          },
          {
            envCode: 'test',
            envName: 'Test',
          },
        ],
      }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/ontologies', async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          result: {
            ontologyId: 'b2b-callchain-v1',
            name: 'B2B Call Chain Ontology',
            version: '1.0',
            entityTypes: [],
            relationTypes: [],
          },
        }),
      })
      return
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        result: [
          {
            ontologyId: 'b2b-callchain-v1',
            name: 'B2B Call Chain Ontology',
            version: '1.0',
            entityTypes: [
              { type: 'BusinessCapability', requiredProperties: ['menuId'] },
              { type: 'Service', requiredProperties: ['serviceName'] },
              { type: 'Cluster', requiredProperties: ['clusterName'] },
            ],
            relationTypes: [
              { type: 'deployed_in', layer: 'deployment', from: ['Service'], to: ['Cluster'] },
            ],
          },
        ],
      }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/resources/tree?envCode=prod**', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        result: {
          envCode: 'prod',
          total: 3,
          roots: [
            {
              id: 'Service',
              type: 'Service',
              name: 'Service',
              count: 1,
              children: [
                {
                  id: 'svc-prod-b2b-query',
                  type: 'Service',
                  displayName: 'bes.business.common.SysParamBS',
                  status: 'Normal',
                },
              ],
            },
          ],
        },
      }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/resources/tree?envCode=test**', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        result: {
          envCode: 'test',
          total: 0,
          roots: [],
        },
      }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/observations/query', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        result: {
          total: 1,
          results: [
            {
              id: 'obs-1',
              entityId: 'biz-prod-604015020',
              observedAt: '2026-05-20T00:00:00+08:00',
              name: 'business_flow_success_rate',
              severity: 'warning',
              value: 0.625,
              unit: 'ratio',
            },
          ],
        },
      }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/subgraph', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        result: {
          envCode: 'prod',
          entities: [
            { id: 'svc-prod-b2b-query', type: 'Service', displayName: 'bes.business.common.SysParamBS' },
            { id: 'cluster-prod-rsp', type: 'Cluster', displayName: 'RSP' },
          ],
          relations: [
            { id: 'rel-1', type: 'deployed_in', from: 'svc-prod-b2b-query', to: 'cluster-prod-rsp' },
          ],
          observations: [],
        },
      }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/diagnosis/context', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        result: {
          entity: { id: 'biz-prod-604015020', type: 'BusinessCapability', displayName: 'B2B' },
          subgraph: {
            envCode: 'prod',
            entities: [{ id: 'biz-prod-604015020', type: 'BusinessCapability', displayName: 'B2B' }],
            relations: [{ id: 'rel-1', type: 'deployed_in', from: 'svc-prod-b2b-query', to: 'cluster-prod-rsp' }],
            observations: [],
          },
          observations: [],
          rootCauseCandidates: [
            {
              entityId: 'biz-prod-604015020',
              entityType: 'BusinessCapability',
              displayName: 'B2B',
              severity: 'warning',
              score: 60,
              evidence: { id: 'obs-1', name: 'business_flow_success_rate' },
            },
          ],
        },
      }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/admin/import', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, result: { entityCount: 3, relationCount: 1, observationCount: 1 } }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/admin/delete-ontology', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, result: { ontologyId: 'b2b-callchain-v1', deleted: true } }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/admin/delete-entities', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, result: { ontologyId: 'b2b-callchain-v1', envCode: 'prod', deleted: true } }),
    })
  })
  await page.route('**/gateway/operation-intelligence/graph/admin/export', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        result: {
          manifest: {
            packageVersion: '1.0',
            format: 'KG_NATIVE_JSON',
            envCode: 'prod',
            entityCount: 2,
            relationCount: 1,
            observationCount: 1,
          },
          schemaDsl: [
            'schemaVersion: "1.0"',
            'entityTypes:',
            '  - type: "BusinessCapability"',
            '    requiredProperties:',
            '      - "menuId"',
            '  - type: "Service"',
            '    requiredProperties:',
            '      - "serviceName"',
            'relationTypes:',
            '  - type: "deployed_in"',
            '    from: "Service"',
            '    to: "Cluster"',
          ].join('\n'),
          snapshot: {
            envCode: 'prod',
            schemaVersion: '1.0',
            entities: [
              { id: 'biz-prod-604015020', type: 'BusinessCapability', displayName: 'B2B' },
              {
                id: 'svc-prod-b2b-query',
                type: 'Service',
                displayName: 'bes.business.common.SysParamBS',
                status: 'Normal',
                properties: { serviceName: 'bes.business.common.SysParamBS' },
              },
            ],
            relations: [
              { id: 'rel-1', type: 'deployed_in', from: 'svc-prod-b2b-query', to: 'cluster-prod-rsp' },
            ],
            observations: [
              { id: 'obs-1', entityId: 'biz-prod-604015020', name: 'business_flow_success_rate' },
            ],
          },
        },
      }),
    })
  })
}

test('operation intelligence knowledge graph page renders and imports sample', async ({ page }) => {
  await mockKnowledgeGraphApi(page)
  await login(page)

  await page.goto('/#/operation-intelligence/knowledge-graph')

  await expect(page.getByRole('heading', { name: '智能运维知识图谱' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '知识图谱工作台' })).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '本体' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tab', { name: '实体' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '本体图' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Service' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'RSP' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '导入本体' })).toBeVisible()
  await expect(page.getByRole('button', { name: '删除本体' })).toBeVisible()

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出本体' }).click()
  expect((await download).suggestedFilename()).toContain('kg-ontology-b2b-callchain-v1')

  await page.getByRole('tab', { name: '实体' }).click()
  await expect(page.getByRole('tab', { name: '实体' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('button', { name: '导入实体' })).toBeVisible()
  await expect(page.getByRole('button', { name: '删除实体' })).toBeVisible()
  await expect(page.getByRole('button', { name: '导入示例' })).toHaveCount(0)
  await expect(page.getByLabel('环境')).toHaveValue('prod')
  await page.getByLabel('环境').selectOption('test')
  await expect(page.getByLabel('环境')).toHaveValue('test')
  await page.getByLabel('环境').selectOption('prod')
  await expect(page.getByText('根因候选')).toHaveCount(0)
  await page.getByRole('button', { name: /Service 1/ }).click()
  await expect(page.getByText('bes.business.common.SysParamBS').first()).toBeVisible()
  await page.getByRole('button', { name: 'bes.business.common.SysParamBS Normal' }).click()
  await expect(page.getByRole('heading', { name: '资源详情' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '实体总览图' })).toBeVisible()

  await page.getByLabel('实体 ID / 名称').fill('SysParamBS')
  await page.getByLabel('跳数').fill('2')
  await page.getByRole('button', { name: '查询子图' }).click()
  await expect(page.getByRole('heading', { name: '实体子图' })).toBeVisible()
  await expect(page.getByText('展示 bes.business.common.SysParamBS 周边 2 跳以内的实体和关系')).toBeVisible()
  await expect(page.getByText('business_flow_success_rate').first()).toBeVisible()

  await page.getByRole('button', { name: 'bes.business.common.SysParamBS Service' }).click()
  await expect(page.getByText('节点属性')).toBeVisible()
  await expect(page.locator('.kg-property-row').filter({ hasText: 'id' })).toBeVisible()
  await expect(page.locator('.kg-graph-node[data-selected="true"]')).toHaveCount(1)
  await expect(page.locator('.kg-graph-edge-related')).toHaveCount(1)

  await page.locator('.kg-graph-edge').first().click({ force: true })
  await expect(page.locator('.kg-graph-edge-selected')).toHaveCount(1)
  await expect(page.locator('.kg-graph-node[data-selected="true"]')).toHaveCount(0)

  page.once('dialog', async dialog => {
    await dialog.accept()
  })
  await page.getByRole('button', { name: '删除实体' }).click()
  await expect(page.getByText('实体已删除')).toBeVisible()
})
