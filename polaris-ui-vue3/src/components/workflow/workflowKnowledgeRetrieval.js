const text = value => String(value ?? '').trim()

export const KNOWLEDGE_RETRIEVAL_DEFAULT_CONFIG = Object.freeze({
  configVersion: '2.0',
  retrievalMode: 'BALANCED',
  resultLimit: 5,
  contextBudgetMode: 'AUTO',
  maxContextTokens: 4000,
  maxChunksPerDocument: 3,
  emptyPolicy: 'CONTINUE',
  degradationPolicy: 'DENY'
})

export function normalizeKnowledgeRetrievalConfig(config = {}) {
  const mode = ['PRECISE', 'BALANCED', 'BROAD'].includes(config.retrievalMode)
    ? config.retrievalMode : 'BALANCED'
  const resultLimit = Math.max(1, Math.min(20, Number(config.resultLimit) || 5))
  const maxContextTokens = Math.max(256, Math.min(32000, Number(config.maxContextTokens) || 4000))
  const maxChunksPerDocument = Math.max(1, Math.min(10, Number(config.maxChunksPerDocument) || 3))
  return {
    ...KNOWLEDGE_RETRIEVAL_DEFAULT_CONFIG,
    ...config,
    configVersion: '2.0',
    retrievalMode: mode,
    resultLimit,
    contextBudgetMode: config.contextBudgetMode === 'MANUAL' ? 'MANUAL' : 'AUTO',
    maxContextTokens,
    maxChunksPerDocument,
    emptyPolicy: config.emptyPolicy === 'FAIL' ? 'FAIL' : 'CONTINUE',
    degradationPolicy: config.degradationPolicy === 'ALLOW' ? 'ALLOW' : 'DENY'
  }
}

export function filterKnowledgeResources(resources = [], keyword = '', availableOnly = false) {
  const normalizedKeyword = text(keyword).toLowerCase()
  return resources.filter(resource => {
    if (availableOnly && !resource?.available) return false
    if (!normalizedKeyword) return true
    return [resource?.name, resource?.description, resource?.attributes?.indexStatus]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(normalizedKeyword))
  })
}

export function knowledgeRetrievalConfigurationState({resources = [], config = {}, inputMapping = {}, queryIssue = ''} = {}) {
  if (!resources.length) {
    return {code: 'UNCONFIGURED', label: '未选择知识库', tone: 'warning', issues: ['请至少选择一个可用的知识库']}
  }
  const unavailable = resources.find(resource => !resource?.available)
  if (unavailable) {
    return {
      code: 'UNAVAILABLE',
      label: '知识库不可用',
      tone: 'danger',
      issues: [unavailable.unavailableReason || `${unavailable.name || '所选知识库'}当前不可用`]
    }
  }
  const normalized = normalizeKnowledgeRetrievalConfig(config)
  if (!inputMapping?.query) {
    return {code: 'CONFIGURING', label: '待设置查询', tone: 'warning', issues: ['请选择要检索的内容']}
  }
  if (queryIssue) {
    return {code: 'INVALID', label: '查询来源不可用', tone: 'danger', issues: [queryIssue]}
  }
  if (normalized.contextBudgetMode === 'MANUAL' && normalized.maxContextTokens < 256) {
    return {code: 'INVALID', label: '设置有误', tone: 'danger', issues: ['上下文长度不能少于 256 Token']}
  }
  return {code: 'READY', label: '配置完成', tone: 'success', issues: []}
}

export function knowledgeRetrievalSummary(config = {}) {
  const normalized = normalizeKnowledgeRetrievalConfig(config)
  const modeLabel = {PRECISE: '精准', BALANCED: '均衡', BROAD: '覆盖'}[normalized.retrievalMode]
  return `${modeLabel} · 最多 ${normalized.resultLimit} 条`
}

export function knowledgeTestResultView(output) {
  const value = output && typeof output === 'object' && !Array.isArray(output) ? output : {}
  const results = Array.isArray(value.results) ? value.results : []
  const truncationReasons = Array.isArray(value.knowledgeContext?.truncationReasons)
    ? value.knowledgeContext.truncationReasons : []
  const reasonLabels = {
    RESULT_LIMIT: '已达到返回数量上限',
    DOCUMENT_LIMIT: '已限制单个文档的片段数',
    CONTEXT_BUDGET: '已按上下文长度截取'
  }
  return {
    state: value.state || (results.length ? 'FOUND' : 'EMPTY'),
    hasResults: value.hasResults === true || results.length > 0,
    count: Number(value.resultCount ?? results.length) || 0,
    degraded: value.degraded === true,
    complete: value.complete !== false,
    durationMs: Number(value.retrievalInfo?.durationMs) || 0,
    sourceCount: Number(value.retrievalInfo?.sourceCount) || 0,
    tokenCount: Number(value.knowledgeContext?.tokenCount) || 0,
    budgetTokens: Number(value.knowledgeContext?.budgetTokens) || 0,
    truncated: truncationReasons.includes('CONTEXT_BUDGET'),
    limitationLabels: truncationReasons.map(reason => reasonLabels[reason]).filter(Boolean),
    results: results.map((item, index) => ({
      id: item?.id || `result-${index + 1}`,
      rank: Number(item?.rank) || index + 1,
      relevanceLevel: item?.relevanceLevel || 'LOW',
      score: Number(item?.score) || 0,
      content: String(item?.content ?? ''),
      sourceName: text(item?.source?.knowledgeSourceName) || '知识库',
      documentName: text(item?.document?.documentName) || '未命名资料',
      citationLabel: text(item?.citation?.label)
    }))
  }
}
