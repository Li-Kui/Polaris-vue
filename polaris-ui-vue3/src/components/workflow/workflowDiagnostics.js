const reasons = {
  AGENT_SNAPSHOT_INVALID: ['配置', '协作快照未确认或已变化', '重新选择当前 Agent 与知识索引版本，保存后重新发布。'],
  AGENT_SNAPSHOT_CHANGED: ['配置', '协作资源版本已变化', '检查 Agent、模型、知识索引与工具审批；确认当前版本后重新发布。'],
  AGENT_INSUFFICIENT_EVIDENCE: ['证据不足', '没有足够依据生成回答', '核对绑定的知识库和本次独立查询；未生成无依据回答。'],
  AGENT_TOOL_FAILED: ['工具结果', '协作查询未成功，回答已停止', '按查询序号查看 MCP 审计，不自动重试。'],
  AGENT_AUDIT_INCOMPLETE: ['审计', '协作台账或 MCP 审计未完整提交', '先核对本次运行与远端状态，不重新派发以补历史记录。'],
  AGENT_DUPLICATE_RUN: ['结果未知', '同一节点尝试已有协作运行', '查看原运行台账与 MCP 审计，不重新领取或重放。'],
  AGENT_ARGUMENTS_REJECTED: ['输入', '查询参数不符合授权来源', '核对固定参数与 externalQuery；问题和知识正文不能替代外发查询。'],
  AGENT_CANCELLED: ['结果未知', '协作运行已取消', '后续调用已停止；已准备派发的查询仍需核对 MCP 审计。'],
  AGENT_DEADLINE_EXCEEDED: ['结果未知', '协作总预算已用尽', '核对已准备派发的查询，不自动重试。'],
  DEFINITION_INVALID: ['配置校验', '流程定义未通过校验', '核对发布版本中的节点配置和连线。'],
  PLAN_INCOMPATIBLE: ['版本配置', '执行计划不兼容', '核对执行绑定的发布版本与节点处理器版本。'],
  EXPRESSION_INVALID: ['输入映射', '表达式格式无效', '检查节点的字段映射与表达式。'],
  EXPRESSION_EVALUATION_FAILED: ['输入映射', '表达式求值失败', '检查该次执行的输入和上游输出是否符合映射。'],
  OUTPUT_SCHEMA_MISMATCH: ['输出校验', '输出结构与声明不一致', '核对节点输出与发布版本中的输出结构。'],
  PERMISSION_DENIED: ['权限', '执行权限不足', '检查执行主体与资源授权。'],
  TENANT_CONTEXT_MISSING: ['权限', '执行租户身份缺失', '核对执行身份，不要借用其他租户资源。'],
  RESOURCE_NOT_FOUND: ['资源', '引用的资源不可用', '检查模型、知识库或连接绑定。'],
  RESOURCE_DISABLED: ['资源', '引用的资源已停用', '核对资源当前启停状态。'],
  RESOURCE_TEMPORARILY_UNAVAILABLE: ['资源', '资源暂时不可用', '查看资源状态和本次错误记录。'],
  QUOTA_EXCEEDED: ['配额', '执行用量超过限制', '核对本次用量和执行预算。'],
  RATE_LIMITED: ['限流', '请求超过速率限制', '核对当前并发与速率限制。'],
  NODE_TIMEOUT: ['超时', '节点执行超时', '核对节点耗时和外部调用记录；超时不证明远端未执行。'],
  EXECUTION_TIMEOUT: ['超时', '工作流执行超时', '核对各节点耗时和执行时限。'],
  APPROVAL_REJECTED: ['审批', '审批未通过', '查看已有审批决定。'],
  APPROVAL_EXPIRED: ['审批', '审批已超时', '查看审批期限和处理记录。'],
  SIDE_EFFECT_UNCONFIRMED: ['结果未知', '外部调用结果未确认', '先核对外部状态和调用记录，再决定后续操作。'],
  EXECUTION_CONFLICT: ['执行状态', '执行状态发生冲突', '查看事件记录及当前执行状态。'],
  MCP_DEFINITION_CHANGED: ['工具配置', '远端工具定义已变化', '核对工具目录与审批版本，确认后再更新流程。'],
  MCP_POLICY_CHANGED: ['工具配置', '工具绑定或审批已变化', '核对连接、工具审批及发布时的工具版本。'],
  MCP_INPUT_INVALID: ['输入校验', '工具参数不符合约束', '检查节点参数映射与工具输入结构。'],
  MCP_OUTPUT_INVALID: ['输出校验', '工具结果不符合约束', '核对调用记录与工具输出结构。'],
  MCP_RESULT_TOO_LARGE: ['工具结果', '工具响应超过大小限制', '缩小查询范围，并核对工具返回规模。'],
  MCP_AUTHENTICATION_FAILED: ['连接认证', '工具服务认证失败', '由管理员检查连接凭据及其有效性。'],
  MCP_RATE_LIMITED: ['限流', '工具调用超过并发或速率限制', '核对 MCP 连接的调用限制。'],
  MCP_TIMEOUT: ['结果未知', '工具调用超时', '查看 MCP 审计；本地超时不证明远端停止。'],
  MCP_CANCELLED: ['结果未知', '工具调用取消后状态待核对', '查看 MCP 审计和远端状态。'],
  MCP_TRANSPORT_ERROR: ['连接', '工具网络连接失败', '检查连接与对应调用记录。'],
  MCP_HTTP_ERROR: ['连接', '工具服务返回 HTTP 错误', '检查服务状态与调用审计。'],
  MCP_TOOL_ERROR: ['工具结果', '工具返回业务错误', '核对该次工具调用的业务结果。']
}

export function errorGuidance(code) {
  const [category, title, suggestion] = (Object.hasOwn(reasons, code) ? reasons[code] : null) || ['待核对', '尚无此错误码的固定说明', '查看节点记录、相关事件和原始错误码，不据此推断可以重试。']
  return {category, title, suggestion}
}

export function nodeDurationMs(node) {
  if (!node?.startTime || !node?.finishTime) return null
  const milliseconds = Date.parse(node.finishTime) - Date.parse(node.startTime)
  return Number.isFinite(milliseconds) && milliseconds >= 0 ? milliseconds : null
}

export function latestNodeAttempts(nodes = []) {
  const latest = new Map()
  for (const node of nodes) {
    const key = JSON.stringify([node.nodeId, node.branchPath || ''])
    const previous = latest.get(key)
    if (!previous || Number(node.attemptNo || 0) >= Number(previous.attemptNo || 0)) latest.set(key, node)
  }
  return [...latest.values()]
}

export function problemOverview(execution = {}, nodes = []) {
  const status = execution.status
  const latest = latestNodeAttempts(nodes)
  const failures = latest.filter(node => ['FAILED', 'REJECTED', 'NEEDS_ATTENTION'].includes(node.status))
  const unknown = latest.some(node => node.sideEffectStatus === 'UNCONFIRMED') || status === 'NEEDS_ATTENTION'
  let title = '执行状态', tone = 'info', description = '查看节点记录与相关事件。'
  if (status === 'SUCCEEDED') {
    title = '本次执行成功'
    tone = 'success'
    description = nodes.some(node => node.status === 'FAILED') ? '记录中包含历史失败尝试；本次执行最终成功，可按尝试查看处理过程。' : '执行已完成，可查看节点结果和用量。'
  } else if (unknown) {
    title = '结果需要人工核对'; tone = 'warning'
    description = '已有记录不足以确认外部调用结果。先核对调用审计与外部状态，不直接重复执行。'
  } else if (status === 'FAILED' || status === 'REJECTED') {
    title = '本次执行未完成'; tone = 'error'
    description = failures.length ? `发现 ${failures.length} 个尚未被后续成功尝试替代的失败节点。` : '没有可定位的失败节点，先检查执行层错误和事件记录。'
  } else if (status === 'CANCELLED') {
    title = '本次执行已取消'; description = '取消状态不证明已派发的外部调用没有执行。'
  } else if (String(status).startsWith('WAITING') || status === 'WAITING') {
    title = '执行正在等待'; description = '查看等待节点及审批、定时或事件记录；等待不等于执行失败。'
  } else {
    title = '执行尚未结束'; description = '当前信息来自已读取的执行记录，后续状态可能变化。'
  }
  const issues = ['SUCCEEDED', 'CANCELLED'].includes(status) ? [] : failures.map(node => ({node, ...errorGuidance(node.errorCode)}))
  return {title, tone, description, issues, executionReason: execution.errorCode ? errorGuidance(execution.errorCode) : null}
}

const identifier = value => typeof value === 'string' && /^[A-Za-z0-9_.:-]{1,100}$/.test(value) ? value : null
const code = value => typeof value === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(value) ? value : null
const integer = value => Number.isSafeInteger(value) && value >= 0 ? value : null
const time = value => typeof value === 'string' && /^\d{4}-\d\d-\d\d[T ][\d:.+Z-]+$/.test(value) && Number.isFinite(Date.parse(value)) ? value : null

// Export an explicit metadata allowlist, never object spreads or free-form messages.
export function buildDiagnosticSummary({execution, nodes = [], events = [], historyCursor = 0, mcpSnapshot = null, frontendVersion = null}) {
  const scopedMcp = mcpSnapshot?.executionId === execution.executionId ? mcpSnapshot : null
  return {
    format: 'polaris.workflow-diagnostics.v1',
    frontendPackageVersion: identifier(frontendVersion),
    execution: {
      executionId: identifier(execution.executionId), workflowVersionId: identifier(execution.workflowVersionId),
      versionNo: integer(execution.versionNo), status: code(execution.status), errorCode: code(execution.errorCode),
      startTime: time(execution.startTime), finishTime: time(execution.finishTime), durationMs: nodeDurationMs(execution)
    },
    nodeAttempts: nodes.slice(0, 200).map(node => ({
      nodeRunId: identifier(node.nodeRunId), nodeId: identifier(node.nodeId), nodeType: identifier(node.nodeType),
      handlerVersion: identifier(node.handlerVersion), attemptNo: integer(node.attemptNo), status: code(node.status),
      sideEffectStatus: code(node.sideEffectStatus), errorCode: code(node.errorCode),
      startTime: time(node.startTime), finishTime: time(node.finishTime), durationMs: nodeDurationMs(node)
    })),
    nodeCoverage: {loaded: nodes.length, included: Math.min(nodes.length, 200)},
    events: events.slice(0, 200).map(event => ({sequenceNo: integer(event.sequenceNo), eventType: code(event.eventType),
      nodeRunId: identifier(event.nodeRunId), createTime: time(event.createTime)})),
    eventCoverage: {loaded: events.length, included: Math.min(events.length, 200),
      historyReadThrough: integer(historyCursor), executionLatestSequence: integer(execution.eventSequence)},
    mcpAudit: scopedMcp ? {
      nodeRunId: identifier(scopedMcp.nodeRunId), statusFilter: code(scopedMcp.status),
      pageNum: integer(scopedMcp.pageNum), pageSize: integer(scopedMcp.pageSize), total: integer(scopedMcp.total),
      records: scopedMcp.records.filter(row => row.executionId === execution.executionId
        && (!scopedMcp.nodeRunId || row.nodeRunId === scopedMcp.nodeRunId)).slice(0, 50).map(row => ({
        invocationId: identifier(row.invocationId), nodeRunId: identifier(row.nodeRunId), attemptNo: integer(row.attemptNo),
        source: code(row.source), agentRunId: identifier(row.agentRunId), agentCallNo: integer(row.agentCallNo),
        status: code(row.status), errorCode: code(row.errorCode), durationMs: integer(row.durationMs),
        dispatchIntentRecorded: row.callDispatched === '1' ? true : row.callDispatched === '0' ? false : null
      }))
    } : null
  }
}
