import test from 'node:test'
import assert from 'node:assert/strict'
import {
    agentConfigurationState,
    agentTaskSummary,
    agentTestResultView,
    agentToolAccessView,
    agentToolCalls,
    agentToolEventReason,
    agentToolEventStatus,
    agentToolEventType,
    agentToolUsageView,
    filterAgentResources,
    normalizeAgentConfig
} from '../src/components/workflow/workflowAgent.js'

const agents = [
  {resourceId: '1', name: '订单审核助手', description: '检查订单', available: true, attributes: {agentCode: 'ORDER', hasTools: false}},
  {resourceId: '2', name: '客户通知助手', description: '生成客户通知', available: true, attributes: {agentCode: 'NOTICE', hasTools: true}},
  {resourceId: '3', name: '旧助手', available: false, attributes: {agentCode: 'OLD', hasTools: false}}
]

test('智能体搜索同时匹配名称、用途和编码', () => {
  assert.deepEqual(filterAgentResources(agents, '订单').map(item => item.resourceId), ['1'])
  assert.deepEqual(filterAgentResources(agents, 'notice').map(item => item.resourceId), ['2'])
  assert.deepEqual(filterAgentResources(agents, '客户通知').map(item => item.resourceId), ['2'])
})

test('智能体筛选区分可用状态和工具能力', () => {
  assert.deepEqual(filterAgentResources(agents, '', 'AVAILABLE').map(item => item.resourceId), ['1', '2'])
  assert.deepEqual(filterAgentResources(agents, '', 'WITH_TOOLS').map(item => item.resourceId), ['2'])
})

test('工具风险转换为易懂标签并保持默认阻断提示', () => {
  assert.deepEqual(agentToolAccessView(agents[0]).tags, [{label: '不使用工具', type: 'info'}])

  const unknown = agentToolAccessView({attributes: {
    hasTools: true,
    hasInternalReadTools: true,
    toolAccessMode: 'INTERNAL_READ_ONLY',
    hasUnclassifiedTools: true,
    hasExternalTools: true
  }})
  assert.equal(unknown.alertType, 'warning')
  assert.deepEqual(unknown.tags.map(tag => tag.label), [
    '可用内部只读工具', '工具未分类', '调用外部服务'
  ])
  assert.match(unknown.notice, /内部只读工具/)

  const readOnly = agentToolAccessView({attributes: {hasTools: true}})
  assert.equal(readOnly.alertType, 'info')
  assert.deepEqual(readOnly.tags.map(tag => tag.label), ['包含只读工具'])
})

test('配置状态按未配置、配置中、可测试和已验证递进', () => {
  assert.equal(agentConfigurationState().code, 'UNCONFIGURED')
  assert.equal(agentConfigurationState({selectedAgent: agents[0]}).code, 'CONFIGURING')
  assert.equal(agentConfigurationState({selectedAgent: agents[0], config: {task: '审核订单'}}).code, 'READY')
  assert.equal(agentConfigurationState({selectedAgent: agents[0], inputMapping: {order: {expression: '$.input.order'}}}).code, 'READY')
  assert.equal(agentConfigurationState({selectedAgent: agents[0], config: {task: '审核订单'}, testSucceeded: true}).code, 'VERIFIED')
  assert.equal(agentConfigurationState({selectedAgent: agents[2], config: {task: '检查'}}).code, 'UNAVAILABLE')
})

test('任务配置补全默认等待时间并生成易读摘要', () => {
  assert.deepEqual(normalizeAgentConfig({task: '  审核订单  '}), {
    task: '  审核订单  ',
    allowInternalReadTools: true,
    maxWaitSeconds: 300
  })
  assert.equal(normalizeAgentConfig({allowInternalReadTools: false}).allowInternalReadTools, false)
  assert.equal(normalizeAgentConfig({maxWaitSeconds: 999}).maxWaitSeconds, 300)
  assert.equal(agentTaskSummary({task: ''}), '使用智能体默认任务')
  assert.equal(agentTaskSummary({task: '检查订单并返回结果'}, 4), '检查订单…')
})

test('智能体试运行结果转换为可直接展示的内容', () => {
  assert.deepEqual(agentTestResultView({
    text: '订单可以通过',
    agentName: '订单审核助手',
    agentCode: 'ORDER_REVIEW'
  }), {
    text: '订单可以通过',
    agentName: '订单审核助手',
    agentCode: 'ORDER_REVIEW'
  })
  assert.deepEqual(agentTestResultView(null), {
    text: '',
    agentName: 'AI 智能体',
    agentCode: ''
  })
})

test('工具调用用量转换为易读摘要', () => {
  assert.deepEqual(agentToolUsageView({
    toolAttempts: 4,
    toolCalls: 3,
    toolSucceeded: 2,
    toolFailed: 1,
    toolBlocked: 1,
    toolDurationMs: 125,
    toolResultTruncated: 1
  }), {
    attempts: 4,
    calls: 3,
    succeeded: 2,
    failed: 1,
    blocked: 1,
    durationMs: 125,
    truncated: 1,
    visible: true,
    type: 'warning',
    summary: '调用 3 次，成功 2 次，失败 1 次，阻止 1 次'
  })
  assert.equal(agentToolUsageView().summary, '本次未调用工具')
})

test('工具事件按一次调用聚合并优先展示最终状态', () => {
  const events = [
    {sequenceNo: 1, eventType: 'NODE_STARTED', nodeId: 'agent_1'},
    {sequenceNo: 2, eventType: 'AGENT_TOOL_STARTED', nodeId: 'agent_1', nodeRunId: 8,
      payloadJson: JSON.stringify({toolName: 'queryTenant', callNo: 1})},
    {sequenceNo: 3, eventType: 'AGENT_TOOL_SUCCEEDED', nodeId: 'agent_1', nodeRunId: 8,
      payloadJson: {toolName: 'queryTenant', callNo: 1, durationMs: 18, resultTruncated: true}},
    {sequenceNo: 4, eventType: 'AGENT_TOOL_BLOCKED', nodeId: 'agent_1', nodeRunId: 8,
      payloadJson: '{"toolName":"queryUsers","callNo":2,"reasonCode":"PERMISSION_REVOKED"}'}
  ]

  const calls = agentToolCalls(events)
  assert.equal(calls.length, 2)
  assert.deepEqual(calls.map(item => item.status), ['SUCCEEDED', 'BLOCKED'])
  assert.equal(calls[0].durationMs, 18)
  assert.equal(calls[0].resultTruncated, true)
  assert.equal(calls[1].reasonCode, 'PERMISSION_REVOKED')
})

test('损坏的工具事件载荷安全降级且状态文案易懂', () => {
  const [call] = agentToolCalls([{
    eventType: 'AGENT_TOOL_FAILED',
    nodeId: 'agent_1',
    payloadJson: '{invalid'
  }])

  assert.equal(call.toolName, '未知工具')
  assert.equal(call.status, 'FAILED')
  assert.equal(agentToolEventStatus(call.status), '失败')
  assert.equal(agentToolEventType(call.status), 'danger')
  assert.equal(agentToolEventReason('CANCELLED'), '节点或工作流已取消')
  assert.equal(agentToolEventReason('UNKNOWN_REASON'), '工具调用未完成')
})
