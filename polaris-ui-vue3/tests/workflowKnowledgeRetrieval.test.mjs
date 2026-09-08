import test from 'node:test'
import assert from 'node:assert/strict'
import {
    filterKnowledgeResources,
    KNOWLEDGE_RETRIEVAL_DEFAULT_CONFIG,
    knowledgeRetrievalConfigurationState,
    knowledgeRetrievalSummary,
    knowledgeTestResultView,
    normalizeKnowledgeRetrievalConfig
} from '../src/components/workflow/workflowKnowledgeRetrieval.js'

const resources = [
  {resourceId: '1', name: '产品手册', description: '产品使用说明', available: true},
  {resourceId: '2', name: '售后制度', description: '退款和换货规则', available: true},
  {resourceId: '3', name: '旧资料', available: false, unavailableReason: '索引尚未就绪'}
]

test('检索设置使用业务化且安全的默认值', () => {
  assert.deepEqual(normalizeKnowledgeRetrievalConfig(), KNOWLEDGE_RETRIEVAL_DEFAULT_CONFIG)
  assert.equal(normalizeKnowledgeRetrievalConfig({retrievalMode: 'UNKNOWN'}).retrievalMode, 'BALANCED')
  assert.equal(normalizeKnowledgeRetrievalConfig({resultLimit: 99}).resultLimit, 20)
  assert.equal(normalizeKnowledgeRetrievalConfig({maxContextTokens: 1}).maxContextTokens, 256)
  assert.equal(knowledgeRetrievalSummary({retrievalMode: 'PRECISE', resultLimit: 3}), '精准 · 最多 3 条')
})

test('知识库列表支持名称说明搜索和可用状态筛选', () => {
  assert.deepEqual(filterKnowledgeResources(resources, '产品').map(item => item.resourceId), ['1'])
  assert.deepEqual(filterKnowledgeResources(resources, '退款').map(item => item.resourceId), ['2'])
  assert.deepEqual(filterKnowledgeResources(resources, '', true).map(item => item.resourceId), ['1', '2'])
})

test('配置状态依次检查知识库、可用性和查询内容', () => {
  assert.equal(knowledgeRetrievalConfigurationState().code, 'UNCONFIGURED')
  assert.equal(knowledgeRetrievalConfigurationState({resources: [resources[2]]}).code, 'UNAVAILABLE')
  assert.equal(knowledgeRetrievalConfigurationState({resources: [resources[0]]}).code, 'CONFIGURING')
  assert.equal(knowledgeRetrievalConfigurationState({
    resources: [resources[0]],
    inputMapping: {query: {expression: '$.input.question'}}
  }).code, 'READY')
  assert.equal(knowledgeRetrievalConfigurationState({
    resources: [resources[0]],
    inputMapping: {query: {expression: '$.input.missing'}},
    queryIssue: '查询来源字段已失效'
  }).code, 'INVALID')
})

test('试查输出转换为可读资料卡片', () => {
  const result = knowledgeTestResultView({
    state: 'FOUND',
    resultCount: 1,
    results: [{
      id: 'result-1', rank: 1, relevanceLevel: 'HIGH', score: 0.88, content: '退款需要在七天内申请。',
      source: {knowledgeSourceName: '售后制度'}, document: {documentName: '退款规则'}
    }],
    knowledgeContext: {
      tokenCount: 12,
      budgetTokens: 4000,
      truncated: true,
      truncationReasons: ['DOCUMENT_LIMIT']
    },
    retrievalInfo: {durationMs: 23, sourceCount: 2}
  })
  assert.equal(result.hasResults, true)
  assert.equal(result.count, 1)
  assert.equal(result.results[0].documentName, '退款规则')
  assert.equal(result.results[0].sourceName, '售后制度')
  assert.equal(result.tokenCount, 12)
  assert.equal(result.budgetTokens, 4000)
  assert.equal(result.truncated, false)
  assert.deepEqual(result.limitationLabels, ['已限制单个文档的片段数'])
})
