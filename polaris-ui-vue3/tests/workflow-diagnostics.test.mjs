import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {invocationSourceLabel, isWorkflowInvocation} from '../src/components/agent/agentBindings.js'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import {workflowUsageSummary} from '../src/components/workflow/workflowUsage.js'
import {buildDiagnosticSummary} from '../src/components/workflow/workflowDiagnostics.js'

function component(path, bindings = {}) {
  const {descriptor} = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
  const ast = compileScript(descriptor, {id: 'diagnostics'}).scriptAst
  const script = ast.filter(node => node.type === 'ImportDeclaration').sort((a, b) => b.start - a.start)
    .reduce((content, node) => content.slice(0, node.start) + content.slice(node.end), descriptor.script.content)
    .replace('export default {', 'const component = {')
  return vm.runInNewContext(`${script}\ncomponent`, {ArrowLeft: {}, Refresh: {}, McpToolInvoker: {},
    WorkflowProblemOverview: {}, WorkflowNodeDiagnostics: {}, isWorkflowInvocation, invocationSourceLabel, buildDiagnosticSummary, frontendPackage: {version: 'test'}, ...bindings})
}
const deferred = () => { let resolve; const promise = new Promise(accept => { resolve = accept }); return {promise, resolve} }

test('用量缺失不补零、不推算总量，真实零值正常显示', () => {
  assert.deepEqual(workflowUsageSummary('{"inputTokens":0,"outputTokens":12}').map(item => item.value), ['0', '12', '未记录'])
  for (const value of ['invalid', null, {inputTokens: null, outputTokens: '', totalTokens: -1}]) {
    assert.deepEqual(workflowUsageSummary(value).map(item => item.value), ['未记录', '未记录', '未记录'])
  }
})

test('执行审计使用专用接口，普通记录查询仍使用原接口', () => {
  const source = readFileSync(new URL('../src/api/platform/mcp.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '').replaceAll('export function ', 'function ')
  const calls = []
  const list = vm.runInNewContext(`${source}\nlistMcpInvocations`, {request: options => calls.push(options)})
  list({executionId: 'execution-1', status: 'TIMEOUT', pageNum: 2, pageSize: 10})
  assert.equal(calls[0].url, '/platform/console/mcp/invocations/workflow/execution-1')
  assert.equal(calls[0].params.status, 'TIMEOUT')
  list({executionId: 'bad/id', pageNum: 1})
  assert.equal(calls[1].url, '/platform/console/mcp/invocations/workflow/bad%2Fid')
  list({connectionId: 42})
  assert.equal(calls[2].url, '/platform/console/mcp/invocations/list')
  list({executionId: 'execution-1', nodeRunId: 'node-1', pageNum: 2})
  assert.equal(calls[3].url, '/platform/console/mcp/invocations/workflow/execution-1/nodes/node-1')
})

test('MCP 状态筛选与分页保持执行范围，切换执行丢弃旧响应', async () => {
  const first = deferred(), second = deferred(), calls = []
  const c = component('../src/components/platform/McpToolInvoker.vue', {listMcpInvocations: query => {
    calls.push(query); return calls.length === 1 ? first.promise : second.promise
  }})
  const state = {...c.data(), $emit() {}}
  for (const [name, method] of Object.entries(c.methods)) state[name] = method.bind(state)
  state.showWorkflowRecords('execution-1')
  state.showWorkflowRecords('execution-2')
  second.resolve({data: {rows: [{invocationId: 'new', source: 'WORKFLOW', executionId: 'execution-2'}], total: 1}})
  await new Promise(resolve => setImmediate(resolve))
  first.resolve({data: {rows: [{invocationId: 'old', source: 'WORKFLOW', executionId: 'execution-1'}], total: 5}})
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(state.records[0].invocationId, 'new')
  assert.deepEqual(calls.map(item => item.executionId), ['execution-1', 'execution-2'])
  state.query.status = 'TIMEOUT'; state.query.pageNum = 2
  await state.loadRecords()
  assert.equal(calls[2].executionId, 'execution-2')
  assert.equal(calls[2].status, 'TIMEOUT')
  state.showRecords(42)
  assert.equal(state.query.executionId, undefined)
  state.closeRecords()
})

test('关闭 MCP 记录后迟到响应不能恢复旧记录，空执行 ID 不查询', async () => {
  const pending = deferred(), calls = []
  const c = component('../src/components/platform/McpToolInvoker.vue', {listMcpInvocations: query => {
    calls.push(query); return pending.promise
  }})
  const state = {...c.data()}
  for (const [name, method] of Object.entries(c.methods)) state[name] = method.bind(state)
  state.showWorkflowRecords('')
  assert.equal(calls.length, 0)
  state.showWorkflowRecords('execution-1')
  state.closeRecords()
  pending.resolve({data: {rows: [{invocationId: 'old'}], total: 1}})
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(state.records.length, 0)
  assert.equal(state.recordsOpen, false)
})

test('MCP 入口仅在允许时打开当前执行；切换执行不被迟到详情覆盖', async () => {
  const first = deferred(), second = deferred(), opened = []
  const c = component('../src/components/workflow/WorkflowExecutionMonitor.vue', {
    getWorkflowExecution: id => id === 'one' ? first.promise : second.promise,
    listWorkflowNodeRuns: async () => ({data: []}), listWorkflowExecutionEvents: async () => ({data: []}),
    listWorkflowArtifacts: async () => ({data: []})
  })
  const state = {...c.data(), $refs: {mcpRecords: {showWorkflowRecords: id => opened.push(id), closeRecords() {}}},
    $message: {error: message => assert.fail(message)}, stopEventStream() {}, startEventStream() {}}
  const one = c.methods.openDetail.call(state, {executionId: 'one'})
  const two = c.methods.openDetail.call(state, {executionId: 'two'})
  second.resolve({data: {executionId: 'two'}}); await two
  first.resolve({data: {executionId: 'one'}}); await one
  assert.equal(state.selected.executionId, 'two')
  state.canViewMcpRecords = false; c.methods.openMcpRecords.call(state)
  assert.deepEqual(opened, [])
  state.canViewMcpRecords = true; c.methods.openMcpRecords.call(state)
  assert.deepEqual(opened, ['two'])
})

test('修改的 Vue 组件模板和脚本可编译', () => {
  for (const path of ['../src/components/model-center/ModelEditor.vue', '../src/components/platform/McpToolInvoker.vue',
    '../src/components/workflow/WorkflowExecutionMonitor.vue', '../src/components/workflow/WorkflowPage.vue',
    '../src/views/platform/workflow/index.vue', '../src/components/workflow/WorkflowProblemOverview.vue',
    '../src/components/workflow/WorkflowNodeDiagnostics.vue']) {
    const {descriptor} = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
    const script = compileScript(descriptor, {id: 'diagnostics'})
    const result = compileTemplate({source: descriptor.template.content, filename: path, id: 'diagnostics',
      compilerOptions: {bindingMetadata: script.bindings}})
    assert.deepEqual(result.errors, [], path)
  }
})

test('节点审计筛选由服务端执行，错误范围响应不展示或进入摘要', async () => {
  const calls = [], emitted = []
  const c = component('../src/components/platform/McpToolInvoker.vue', {listMcpInvocations: async query => {
    calls.push(query); return {data: {rows: [{executionId: 'execution-1', nodeRunId: 'wrong-node', source: 'WORKFLOW'}], total: 1}}
  }})
  const state = {...c.data(), $emit: (...args) => emitted.push(args)}
  for (const [name, method] of Object.entries(c.methods)) state[name] = method.bind(state)
  state.showWorkflowRecords('execution-1', 'node-1')
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(calls[0].nodeRunId, 'node-1')
  assert.equal(state.recordsError, true)
  assert.equal(state.records.length, 0)
  assert.equal(emitted.length, 0)
  state.query.status = 'SUCCESS'; state.query.pageNum = 2
  await state.loadRecords()
  assert.equal(calls[1].nodeRunId, 'node-1')
  assert.equal(calls[1].pageNum, 2)
})

test('节点尝试关联不扩大执行范围，预览摘要不发起任何读取或调用', () => {
  const opened = [], c = component('../src/components/workflow/WorkflowExecutionMonitor.vue')
  const state = {...c.data(), selected: {executionId: 'one', inputJson: 'SECRET'},
    nodeRuns: [{nodeRunId: 'node-1'}], canViewMcpRecords: true,
    $refs: {mcpRecords: {showWorkflowRecords: (...args) => opened.push(args)}}}
  c.methods.openNodeMcpRecords.call(state, {nodeRunId: 'other'})
  c.methods.openNodeMcpRecords.call(state, {nodeRunId: 'node-1'})
  assert.deepEqual(opened, [['one', 'node-1']])
  c.methods.previewSummary.call(state)
  assert.equal(state.summaryOpen, true)
  assert.ok(!state.summaryText.includes('SECRET'))
  assert.equal(JSON.parse(state.summaryText).execution.executionId, 'one')
})

test('历史分页在切换执行后丢弃旧响应，合并实时事件时不丢失或重复', async () => {
  const pending = deferred(), requests = []
  const c = component('../src/components/workflow/WorkflowExecutionMonitor.vue', {listWorkflowExecutionEvents: (...args) => {
    requests.push(args); return pending.promise
  }})
  const state = {...c.data(), selected: {executionId: 'one', eventSequence: 3}, hasMoreHistory: true,
    historyCursor: 1, lastSequence: 3, events: [{sequenceNo: 1}, {sequenceNo: 3}], $message: {info() {}, error() {}}}
  const loading = c.methods.loadMoreEvents.call(state)
  pending.resolve({data: [{sequenceNo: 2}, {sequenceNo: 3}]})
  await loading
  assert.deepEqual(requests, [['one', 1, 200]])
  assert.equal(state.events.map(event => event.sequenceNo).join(','), '1,2,3')
  assert.equal(state.historyCursor, 3)
  state.historyCursor = 1
  const late = c.methods.loadMoreEvents.call(state)
  state.detailSequence++; state.selected = {executionId: 'two'}; state.events = []
  await late
  assert.equal(state.events.length, 0)
})

test('摘要复制防止重复点击，关闭预览后忽略迟到提示，剪贴板失败支持手动复制', async () => {
  const pending = deferred(), writes = [], messages = []
  const c = component('../src/components/workflow/WorkflowExecutionMonitor.vue', {
    navigator: {clipboard: {writeText: text => { writes.push(text); return pending.promise }}}
  })
  const state = {...c.data(), summaryOpen: true, summaryText: '{"executionId":"one"}',
    $message: {success: text => messages.push(text), warning: text => messages.push(text)}}
  const copy = c.methods.copySummary.call(state)
  await c.methods.copySummary.call(state)
  assert.equal(writes.length, 1)
  state.summaryOpen = false
  pending.resolve()
  await copy
  assert.equal(messages.length, 0)
  assert.equal(state.copyingSummary, false)
  const denied = component('../src/components/workflow/WorkflowExecutionMonitor.vue', {
    navigator: {clipboard: {writeText: async () => { throw new Error('denied') }}}
  })
  state.summaryOpen = true
  await denied.methods.copySummary.call(state)
  assert.match(messages[0], /手动选择并复制/)
  assert.equal(state.copyingSummary, false)
})
