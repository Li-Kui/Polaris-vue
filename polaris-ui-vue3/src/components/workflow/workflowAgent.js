const text = value => String(value ?? '').trim()

export function normalizeAgentConfig(config = {}) {
  const waitSeconds = Number(config.maxWaitSeconds)
  return {
    ...config,
    task: String(config.task ?? ''),
    allowInternalReadTools: config.allowInternalReadTools !== false,
    maxWaitSeconds: Number.isFinite(waitSeconds) && waitSeconds >= 1 && waitSeconds <= 600
      ? waitSeconds
      : 300
  }
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
  const attributes = resource?.attributes || {}
  if (!attributes.hasTools) {
    return {
      hasTools: false,
      hasInternalReadTools: false,
      internalReadEnabled: false,
      callLimit: 0,
      resultLimitChars: 0,
      tags: [{label: '不使用工具', type: 'info'}],
      capabilityLabel: '',
      capabilityType: 'info',
      notice: '',
      alertType: 'info'
    }
  }

  const tags = []
  const internalReadEnabled = attributes.toolAccessMode === 'INTERNAL_READ_ONLY'
  const hasInternalReadTools = attributes.hasInternalReadTools === true
  const callLimit = Number(attributes.toolCallLimit) > 0
    ? Number(attributes.toolCallLimit) : 5
  const resultLimitChars = Number(attributes.toolResultLimitChars) > 0
    ? Number(attributes.toolResultLimitChars) : 20000
  if (hasInternalReadTools) {
    tags.push({
      label: internalReadEnabled ? '可用内部只读工具' : '包含内部只读工具',
      type: internalReadEnabled ? 'success' : 'info'
    })
  }
  if (attributes.hasUnclassifiedTools) tags.push({label: '工具未分类', type: 'danger'})
  if (attributes.hasWriteTools) tags.push({label: '含写操作', type: 'danger'})
  if (!hasInternalReadTools
    && !attributes.hasUnclassifiedTools
    && !attributes.hasWriteTools
    && !attributes.hasExternalTools) {
    tags.push({label: '包含只读工具', type: 'info'})
  }
  if (attributes.hasExternalTools) tags.push({label: '调用外部服务', type: 'warning'})

  const capabilityLabel = internalReadEnabled
    ? '可使用内部只读工具'
    : attributes.hasUnclassifiedTools
      ? '存在未分类工具'
      : attributes.hasWriteTools ? '包含写操作工具' : '包含只读工具'
  const capabilityType = internalReadEnabled
    ? 'success'
    : attributes.hasUnclassifiedTools || attributes.hasWriteTools ? 'danger' : 'info'
  const notice = attributes.toolNotice || (internalReadEnabled
    ? '当前工作流仅允许使用内部只读工具；写操作、外部服务和未分类工具不会执行。'
    : attributes.hasUnclassifiedTools
    ? '该智能体包含尚未完成安全分类的工具。当前工作流节点不会执行这些工具。'
    : attributes.hasWriteTools
      ? '该智能体包含会修改数据或创建内容的工具。当前工作流节点不会执行这些工具。'
      : attributes.hasExternalTools
        ? '该智能体包含会向外部服务发送数据的工具。当前工作流节点不会执行这些工具。'
        : '该智能体包含只读工具。当前工作流节点只使用基础指令和模型，暂不执行工具。')
  return {
    hasTools: true,
    hasInternalReadTools,
    internalReadEnabled,
    callLimit,
    resultLimitChars,
    tags,
    capabilityLabel,
    capabilityType,
    notice,
    alertType: attributes.hasUnclassifiedTools || attributes.hasWriteTools ? 'warning' : 'info'
  }
}

export function agentTaskSummary(config = {}, maximum = 42) {
  const task = text(config.task)
  if (!task) return '使用智能体默认任务'
  return task.length > maximum ? `${task.slice(0, maximum)}…` : task
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

export function agentConfigurationState({
  selectedAgent,
  config = {},
  inputMapping = {},
  testSucceeded = false
} = {}) {
  if (!selectedAgent) {
    return {
      code: 'UNCONFIGURED',
      label: '未配置',
      tone: 'warning',
      issues: ['请先选择一个智能体']
    }
  }
  if (!selectedAgent.available) {
    return {
      code: 'UNAVAILABLE',
      label: '不可用',
      tone: 'danger',
      issues: [selectedAgent.unavailableReason || '当前智能体不可用，请重新选择']
    }
  }
  const hasTask = !!text(config.task)
  const hasInput = Object.keys(inputMapping || {}).length > 0
  if (!hasTask && !hasInput) {
    return {
      code: 'CONFIGURING',
      label: '配置中',
      tone: 'warning',
      issues: ['请填写本次任务要求，或为智能体配置输入']
    }
  }
  if (testSucceeded) {
    return {code: 'VERIFIED', label: '已验证', tone: 'success', issues: []}
  }
  return {code: 'READY', label: '可测试', tone: 'primary', issues: []}
}
