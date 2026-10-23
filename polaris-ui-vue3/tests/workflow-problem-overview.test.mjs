import assert from 'node:assert/strict'
import test from 'node:test'
import {
    buildDiagnosticSummary,
    errorGuidance,
    latestNodeAttempts,
    nodeDurationMs,
    problemOverview
} from '../src/components/workflow/workflowDiagnostics.js'

test('成功执行中的历史失败不作为当前故障，分支与尝试分别保留', () => {
  const nodes = [{nodeId: 'n', branchPath: 'a', attemptNo: 1, status: 'FAILED'},
    {nodeId: 'n', branchPath: 'a', attemptNo: 2, status: 'SUCCEEDED'},
    {nodeId: 'n', branchPath: 'b', attemptNo: 1, status: 'FAILED'}]
  assert.equal(latestNodeAttempts(nodes).length, 2)
  assert.equal(problemOverview({status: 'SUCCEEDED'}, nodes).issues.length, 0)
  assert.equal(problemOverview({status: 'FAILED'}, nodes).issues.length, 1)
})

test('结果未知优先提示人工核对，等待和取消不误判失败', () => {
  assert.equal(problemOverview({status: 'FAILED'}, [{status: 'FAILED', sideEffectStatus: 'UNCONFIRMED'}]).tone, 'warning')
  assert.equal(problemOverview({status: 'NEEDS_ATTENTION'}).title, '结果需要人工核对')
  assert.equal(problemOverview({status: 'WAITING_APPROVAL'}).title, '执行正在等待')
  assert.equal(problemOverview({status: 'CANCELLED'}, [{status: 'FAILED'}]).issues.length, 0)
  assert.match(problemOverview({status: 'FAILED'}).description, /没有可定位/)
})

test('固定错误码解释覆盖配置、权限、输入及超时，未知错误不猜测', () => {
  assert.equal(errorGuidance('MCP_DEFINITION_CHANGED').category, '工具配置')
  assert.equal(errorGuidance('PERMISSION_DENIED').category, '权限')
  assert.equal(errorGuidance('MCP_INPUT_INVALID').category, '输入校验')
  assert.match(errorGuidance('MCP_TIMEOUT').suggestion, /不证明远端停止/)
  assert.equal(errorGuidance('CUSTOM_UNKNOWN').category, '待核对')
  assert.equal(errorGuidance('constructor').category, '待核对')
})

test('时间缺失或倒退不伪造耗时，真实零耗时保留', () => {
  assert.equal(nodeDurationMs({startTime: '2026-10-17T00:00:00Z', finishTime: '2026-10-17T00:00:00Z'}), 0)
  assert.equal(nodeDurationMs({startTime: '2026-10-17T00:00:01Z', finishTime: '2026-10-17T00:00:00Z'}), null)
  assert.equal(nodeDurationMs({startTime: 'invalid', finishTime: 'invalid'}), null)
})

test('摘要严格使用白名单，排除正文、资源配置、名称、身份和事件载荷', () => {
  const secret = 'SECRET_CREDENTIAL_PROMPT'
  const report = buildDiagnosticSummary({execution: {executionId: 'exec-1', status: 'FAILED', inputJson: secret,
    outputJson: secret, errorMessage: secret, principalId: secret, workflowCode: secret, eventSequence: 500},
    nodes: [{nodeRunId: 'node-1', nodeName: secret, resources: [{key: secret}], inputJson: secret, outputJson: secret, errorMessage: secret}],
    events: [{sequenceNo: 1, payloadJson: secret}], historyCursor: 200, mcpSnapshot: {executionId: 'other', records: [{inputJson: secret}]}})
  assert.ok(!JSON.stringify(report).includes(secret))
  assert.equal(report.eventCoverage.historyReadThrough, 200)
  assert.equal(report.eventCoverage.executionLatestSequence, 500)
  assert.equal(report.mcpAudit, null)
})

test('摘要显式声明截断及审计分页，不夹带其他执行或节点调用', () => {
  const report = buildDiagnosticSummary({execution: {executionId: 'exec-1'}, nodes: Array(205).fill({}), events: Array(210).fill({}),
    mcpSnapshot: {executionId: 'exec-1', nodeRunId: 'node-1', pageNum: 2, pageSize: 10, total: 21, records: [
      {executionId: 'other', nodeRunId: 'node-1'}, {executionId: 'exec-1', nodeRunId: 'node-2'},
      {executionId: 'exec-1', nodeRunId: 'node-1', invocationId: 'call-1', callDispatched: '0'}]}})
  assert.deepEqual(report.nodeCoverage, {loaded: 205, included: 200})
  assert.equal(report.events.length, 200)
  assert.equal(report.mcpAudit.records.length, 1)
  assert.equal(report.mcpAudit.records[0].dispatchIntentRecorded, false)
  assert.equal(report.mcpAudit.pageNum, 2)
  assert.equal(report.mcpAudit.total, 21)
})
