const text = value => String(value ?? '').trim()

export function normalizeAgentConfig(config = {}) {
  return {configVersion: '2.0', agentId: String(config.agentId || ''), definitionHash: String(config.definitionHash || ''),
    ...(config.knowledgeId ? {knowledgeId: String(config.knowledgeId), knowledgeHash: String(config.knowledgeHash || '')} : {}),
    maxCalls: Number.isInteger(config.maxCalls) && config.maxCalls >= 0 && config.maxCalls <= 2 ? config.maxCalls : 2,
    maxWaitSeconds: Number.isInteger(config.maxWaitSeconds) && config.maxWaitSeconds >= 1 && config.maxWaitSeconds <= 180 ? config.maxWaitSeconds : 90}
}
export function agentSnapshot(resource) {
  const snapshot = resource?.attributes?.snapshot
  return resource?.available && snapshot?.configVersion === '2.0' && snapshot.agentId === resource.resourceId
    && /^[a-f0-9]{64}$/.test(snapshot.definitionHash || '') ? snapshot : null
}
export function agentSnapshotMatches(config, resource) {
  const snapshot = agentSnapshot(resource)
  return !!snapshot && config?.configVersion === '2.0' && snapshot.agentId === config.agentId && snapshot.definitionHash === config.definitionHash
}
export function agentKnowledgeSnapshot(resource) {
  const snapshot = resource?.attributes?.collaborationSnapshot
  return resource?.available && snapshot?.knowledgeId === resource.resourceId && /^[a-f0-9]{64}$/.test(snapshot.definitionHash || '') ? snapshot : null
}
export function confirmAgentSnapshot(config, resource) {
  const snapshot = agentSnapshot(resource)
  if (!snapshot) return null
  const tools = resource.attributes.toolBindings || []
  return {...normalizeAgentConfig(config), ...snapshot, maxCalls: tools.length ? Math.max(1, normalizeAgentConfig(config).maxCalls) : 0}
}
export function confirmAgentKnowledge(config, resource) {
  const result = normalizeAgentConfig(config)
  delete result.knowledgeId; delete result.knowledgeHash
  if (!resource) return result
  const snapshot = agentKnowledgeSnapshot(resource)
  return snapshot ? {...result, knowledgeId: snapshot.knowledgeId, knowledgeHash: snapshot.definitionHash} : null
}
export function agentNodeSchema(config, agent, knowledge, descriptor) {
  const properties = {question: {type: 'string', title: '本次问题', minLength: 1, maxLength: 4000},
    externalQuery: {type: 'string', title: '允许外发的查询文本', maxLength: 4000}}
  const hasTools = !!agent?.attributes?.toolBindings?.length
  const inputSchema = {type: 'object', properties, additionalProperties: false, required: ['question', ...(hasTools ? ['externalQuery'] : [])]}
  const confirmed = agentSnapshotMatches(config, agent) && (!config?.knowledgeId || agentKnowledgeSnapshot(knowledge)?.definitionHash === config.knowledgeHash)
  return {inputSchema, outputSchema: descriptor?.outputSchema || {type: 'object', properties: {text: {type: 'string'}}},
    source: '协作智能体契约', sourceCode: 'AGENT_COLLABORATION', sourceVersion: confirmed ? config.definitionHash : '',
    diagnostics: confirmed ? [] : ['智能体或知识快照未确认，请采用当前版本']}
}

export function filterAgentResources(resources = [], keyword = '', filter = 'ALL') {
  const normalizedKeyword = text(keyword).toLowerCase()
  return resources.filter(resource => {
    const attributes = resource?.attributes || {}
    if (filter === 'AVAILABLE' && !resource?.available) return false
    if (filter === 'WITH_TOOLS' && !attributes.hasTools) return false
    if (!normalizedKeyword) return true
    return [
      resource?.name,
      resource?.description,
      attributes.agentCode,
      attributes.modelName
    ].filter(Boolean).some(value => String(value).toLowerCase().includes(normalizedKeyword))
  })
}

export function agentToolAccessView(resource = {}) {
  const attributes = resource?.attributes || {}, tools = attributes.toolBindings || []
  const enabled = attributes.toolAccessMode === 'MCP_READ_ONLY' && tools.length > 0
  return {hasTools: enabled, hasInternalReadTools: false, internalReadEnabled: false, callLimit: enabled ? 2 : 0,
    resultLimitChars: 12000, tags: enabled ? [{label: 'MCP 只读查询', type: 'success'}] : [],
    capabilityLabel: enabled ? '可选择最多 2 项查询' : '仅使用知识证据', capabilityType: enabled ? 'success' : 'info',
    notice: attributes.toolNotice || '内部工具类不参与本节点执行；未绑定工具时需选择知识库。', alertType: 'info'}
}

export function agentTaskSummary(config = {}) {
  return config.configVersion === '2.0' ? (config.knowledgeId ? '知识依据与可选 MCP 查询' : '独立外发查询 · 有界协作') : '旧 Agent 契约，需重新配置'
}

export function agentTestResultView(output) {
  const value = output && typeof output === 'object' && !Array.isArray(output) ? output : {}
  return {
    text: String(value.text ?? ''),
    agentName: text(value.agentName) || 'AI 智能体',
    agentCode: text(value.agentCode)
  }
}

export function agentToolUsageView(usage = {}) {
  const number = key => Math.max(0, Number(usage?.[key]) || 0)
  const result = {
    attempts: number('toolAttempts'),
    calls: number('toolCalls'),
    succeeded: number('toolSucceeded'),
    failed: number('toolFailed'),
    blocked: number('toolBlocked'),
    durationMs: number('toolDurationMs'),
    truncated: number('toolResultTruncated')
  }
  result.visible = result.attempts > 0 || result.calls > 0
  result.type = result.failed > 0 || result.blocked > 0 ? 'warning' : 'success'
  result.summary = result.visible
    ? `调用 ${result.calls} 次，成功 ${result.succeeded} 次`
      + (result.failed ? `，失败 ${result.failed} 次` : '')
      + (result.blocked ? `，阻止 ${result.blocked} 次` : '')
    : '本次未调用工具'
  return result
}

export function agentToolEventView(event = {}) {
  let payload = event?.payloadJson || {}
  if (typeof payload === 'string') {
    try {
      payload = JSON.parse(payload)
    } catch (error) {
      payload = {}
    }
  }
  return {
    ...event,
    toolName: payload.toolName || '未知工具',
    callNo: Number(payload.callNo || 0),
    durationMs: Math.max(0, Number(payload.durationMs || 0)),
    reasonCode: payload.reasonCode || '',
    resultTruncated: payload.resultTruncated === true,
    status: String(event?.eventType || '').replace('AGENT_TOOL_', '')
  }
}

export function agentToolCalls(events = []) {
  const calls = new Map()
  events
    .filter(item => String(item?.eventType || '').startsWith('AGENT_TOOL_'))
    .map(agentToolEventView)
    .forEach(item => {
      const key = `${item.nodeRunId || item.nodeId}:${item.toolName}:${item.callNo}`
      const previous = calls.get(key)
      if (!previous || item.status !== 'STARTED') calls.set(key, item)
    })
  return [...calls.values()]
}

export function agentToolEventStatus(status) {
  return {
    STARTED: '开始',
    SUCCEEDED: '成功',
    FAILED: '失败',
    BLOCKED: '已阻止'
  }[status] || status
}

export function agentToolEventType(status) {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'BLOCKED') return 'warning'
  return 'info'
}

export function agentToolEventReason(reasonCode) {
  return {
    TOOL_CALL_LIMIT: '超过本节点调用上限',
    PRINCIPAL_UNAVAILABLE: '执行身份已失效',
    PERMISSION_REVOKED: '执行权限已被收回',
    SCOPE_DENIED: '当前身份不能使用该工具',
    PERMISSION_DENIED: '没有工具所需权限',
    CANCELLED: '节点或工作流已取消',
    TOOL_EXECUTION_FAILED: '工具执行异常'
  }[reasonCode] || (reasonCode ? '工具调用未完成' : '-')
}

export function agentConfigurationState({selectedAgent, selectedKnowledge, config = {}, inputMapping = {}, legacy = false} = {}) {
  const state = (code, label, tone, issue) => ({code, label, tone, issues: issue ? [issue] : []})
  if (legacy) return state('UNAVAILABLE', '旧契约不可运行', 'danger', '移除旧节点，添加 Agent 2.0 后重新配置并发布')
  if (!selectedAgent) return state('UNCONFIGURED', '未配置', 'warning', '请先选择智能体')
  if (!selectedAgent.available) return state('UNAVAILABLE', '不可用', 'danger', selectedAgent.unavailableReason || '当前智能体不可用')
  if (!agentSnapshotMatches(config, selectedAgent)) return state('CONFIGURING', '版本待确认', 'warning', '请采用当前智能体版本')
  if (config.knowledgeId && (!agentKnowledgeSnapshot(selectedKnowledge) || selectedKnowledge.resourceId !== config.knowledgeId || agentKnowledgeSnapshot(selectedKnowledge).definitionHash !== config.knowledgeHash))
    return state('CONFIGURING', '知识版本待确认', 'warning', '请重新确认当前知识库索引版本')
  const tools = selectedAgent.attributes?.toolBindings || []
  if (!tools.length && !config.knowledgeId) return state('CONFIGURING', '缺少证据来源', 'warning', '请选择知识库或在智能体管理中绑定 MCP 查询工具')
  if (!Object.hasOwn(inputMapping, 'question')) return state('CONFIGURING', '输入待配置', 'warning', '请显式映射本次问题 question')
  if (tools.length && !Object.hasOwn(inputMapping, 'externalQuery')) return state('CONFIGURING', '外发查询待配置', 'warning', '请单独映射允许外发的 externalQuery，不自动采用问题或知识正文')
  return state('READY', '可发布验证', 'success')
}
