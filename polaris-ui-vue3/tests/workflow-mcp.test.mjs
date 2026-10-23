import assert from 'node:assert/strict'
import test from 'node:test'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {parse} from '@vue/compiler-sfc'
import {mcpNodeSchema, mcpSnapshot, mcpSnapshotMatches} from '../src/components/workflow/workflowMcp.js'

function resource() {
  return {kind: 'MCP_TOOL', resourceId: '9', available: true, attributes: {
    snapshot: {toolId: '9', revision: 6, connectionRevision: 4, catalogRevision: 5, structureHash: 'a'.repeat(64)},
    inputSchema: {type: 'object', properties: {query: {type: 'string'}}},
    outputSchema: {type: 'object', properties: {content: {type: 'array'}}}
  }}
}

test('只提取已审批工具的本地快照，不复制端点或凭证', () => {
  const selected = resource()
  selected.attributes.snapshot.token = 'SECRET'
  const snapshot = mcpSnapshot(selected)
  assert.equal(Object.keys(snapshot).length, 5)
  assert.equal(snapshot.token, undefined)
  snapshot.revision++
  assert.equal(selected.attributes.snapshot.revision, 6)
})

test('不可用、错绑身份、无效修订和 JS 精度溢出都不得确认', () => {
  for (const modify of [item => {item.available = false}, item => {item.kind = 'MODEL'},
    item => {item.resourceId = '10'}, item => {item.attributes.snapshot.revision = 0},
    item => {item.attributes.snapshot.revision = '6'}, item => {item.attributes.snapshot.revision = 2 ** 53},
    item => {item.attributes.snapshot.structureHash = 'invalid'}]) {
    const selected = resource(); modify(selected)
    assert.equal(mcpSnapshot(selected), null)
  }
})

test('工具、连接、目录、审批及结构变化都使旧快照失效', () => {
  const selected = resource(), snapshot = mcpSnapshot(selected)
  assert.equal(mcpSnapshotMatches(snapshot, selected), true)
  for (const field of ['toolId', 'revision', 'connectionRevision', 'catalogRevision', 'structureHash']) {
    assert.equal(mcpSnapshotMatches({...snapshot, [field]: 'changed'}, selected), false)
  }
})

test('仅相同快照可展示动态 Schema，漂移时明确回退并给出诊断', () => {
  const selected = resource(), snapshot = mcpSnapshot(selected)
  const schema = mcpNodeSchema(snapshot, selected)
  assert.equal(schema.sourceCode, 'MCP_TOOL')
  assert.equal(schema.inputSchema.properties.query.type, 'string')
  assert.equal(schema.diagnostics.length, 0)
  selected.attributes.snapshot.revision++
  const stale = mcpNodeSchema(snapshot, selected)
  assert.equal(stale.sourceCode, 'NODE_CONTRACT')
  assert.equal(stale.diagnostics.length, 1)
  assert.equal(stale.inputSchema.properties.query, undefined)
})

// 从真实组件源码提取单个方法，避免建立另一套模拟实现。
const {descriptor} = parse(readFileSync(new URL('../src/components/workflow/WorkflowWorkbench.vue', import.meta.url), 'utf8'))
const methodSource = descriptor.script.content.match(/    async selectMcpTool\(resourceId\) \{[\s\S]*?\n    \},\n    async selectAgentResource/)[0]
  .replace(/,\n    async selectAgentResource$/, '')
const selectMcpTool = vm.runInNewContext(`({${methodSource}}).selectMcpTool`, {mcpSnapshot})

function state(bindResource) {
  const node = {id: 'n1', config: {old: true}, retryPolicy: {maxAttempts: 2}, onError: 'SKIP', outputSchemaOverride: {}}
  return {canEdit: true, resourceLoading: false, selectedNode: node,
    mcpToolReference: {kind: 'MCP_TOOL', key: 'mcp.n1'}, resourceCatalog: {MCP_TOOL: [resource()]},
    definition: {nodes: [node]}, bindResource, $message: {warning() {}},
    invalidateSelectedInferredSchema() {}, refreshCanvasNodeData() {}, markDirty() {this.dirty = true}}
}

test('绑定失败不修改节点配置和审批快照', async () => {
  const context = state(async () => false)
  await selectMcpTool.call(context, '9')
  assert.deepEqual(context.selectedNode.config, {old: true})
  assert.equal(context.dirty, undefined)
})

test('绑定期间目录变更不自动采用新版本，明确确认版本才替换配置', async () => {
  const context = state(async () => {
    context.resourceCatalog.MCP_TOOL[0].attributes.snapshot.revision = 7
    return true
  })
  await selectMcpTool.call(context, '9')
  assert.equal(context.selectedNode.config.revision, 6)
  assert.equal(mcpSnapshotMatches(context.selectedNode.config, context.resourceCatalog.MCP_TOOL[0]), false)
  assert.equal(context.selectedNode.retryPolicy, null)
  assert.equal(context.selectedNode.onError, 'FAIL')
  assert.equal(context.selectedNode.outputSchemaOverride, undefined)
  assert.equal(context.selectedInspectorTab, 'mapping')
})

test('无编辑权限和不可用工具不会发起绑定', async () => {
  let calls = 0
  const context = state(async () => {calls++; return true})
  context.canEdit = false
  await selectMcpTool.call(context, '9')
  context.canEdit = true
  context.resourceCatalog.MCP_TOOL[0].available = false
  await selectMcpTool.call(context, '9')
  assert.equal(calls, 0)
})

function method(name, next, globals = {}, component = descriptor) {
  const source = component.script.content.match(new RegExp(`    async ${name}\\([^]*?\\n    \\},\\n    (?:async )?${next}`))[0]
    .replace(new RegExp(`,\\n    (?:async )?${next}$`), '')
  return vm.runInNewContext(`({${source}}).${name}`, globals)
}

function admissionState() {
  return {canExecute: true, currentDefinition: {id: 1, currentPublishedVersionId: 'v1'},
    executionAdmissionSequence: 0, executionAdmissionLoading: false, executionAdmission: null, testRunError: ''}
}

test('准入接口失败或返回错误版本时默认拒绝，不产生未处理异常', async () => {
  for (const getWorkflowExecutionAdmission of [async () => {throw new Error('offline')},
    async () => ({data: {allowed: true, workflowVersionId: 'v0'}})]) {
    const context = admissionState()
    const refresh = method('refreshExecutionAdmission', 'openTestRun', {getWorkflowExecutionAdmission})
    assert.equal(await refresh.call(context), false)
    assert.equal(context.executionAdmission.allowed, false)
    assert.equal(context.executionAdmission.code, 'CHECK_UNAVAILABLE')
    assert.equal(context.executionAdmissionLoading, false)
  }
})

test('慢旧响应不得覆盖新发布版本的准入状态', async () => {
  let resolveOld
  const refresh = method('refreshExecutionAdmission', 'openTestRun', {getWorkflowExecutionAdmission:
    async (id, version) => version === 'v1' ? new Promise(resolve => {resolveOld = resolve})
      : {data: {allowed: false, workflowVersionId: 'v2', reason: '未启用 MCP'}}})
  const context = admissionState()
  const old = refresh.call(context)
  context.currentDefinition.currentPublishedVersionId = 'v2'
  await refresh.call(context)
  resolveOld({data: {allowed: true, workflowVersionId: 'v1'}})
  await old
  assert.equal(context.executionAdmission.workflowVersionId, 'v2')
  assert.equal(context.executionAdmission.allowed, false)
})

test('无执行权限不查询准入，已通过准入也不能替代启动前复查', async () => {
  const context = admissionState()
  context.canExecute = false
  const refresh = method('refreshExecutionAdmission', 'openTestRun', {getWorkflowExecutionAdmission: () => {assert.fail('不得查询')}})
  assert.equal(await refresh.call(context), false)
  context.canExecute = true
  context.testInputValidation = {valid: true}
  context.refreshExecutionAdmission = async () => false
  const start = method('startTestRun', 'publishedInputSchema', {startWorkflowExecution: () => {assert.fail('不得启动')}})
  await start.call(context)
  assert.equal(context.testStarting, false)
})

test('启动失败保留原因及弹窗，重复点击不重复创建执行', async () => {
  let attempts = 0
  const start = method('startTestRun', 'publishedInputSchema', {startWorkflowExecution: async () => {
    attempts++; throw new Error('工具审批已变化，请重新审批')
  }})
  const context = {...admissionState(), testInputValidation: {valid: true},
    testDialogOpen: true, testInput: {}, testApprovalNodes: [], resourceEnvironment: 'PROD',
    refreshExecutionAdmission: async () => true}
  context.testStarting = true
  await start.call(context)
  assert.equal(attempts, 0)
  context.testStarting = false
  await start.call(context)
  assert.equal(attempts, 1)
  assert.equal(context.testDialogOpen, true)
  assert.equal(context.testStarting, false)
  assert.equal(context.testRunError, '工具审批已变化，请重新审批')
})

const page = parse(readFileSync(new URL('../src/components/workflow/WorkflowPage.vue', import.meta.url), 'utf8')).descriptor

test('列表运行入口同样先检查准入，不把发布状态当作执行许可', async () => {
  const refresh = method('refreshRunAdmission', 'openRunDialog', {getWorkflowExecutionAdmission: async () => {
    throw new Error('offline')
  }}, page)
  const context = {canExecute: true, runTarget: {id: 1, currentPublishedVersionId: 'v1'},
    runAdmissionSequence: 0, runAdmissionLoading: false, runInputValidation: {valid: true}}
  assert.equal(await refresh.call(context), false)
  assert.equal(context.runAdmission.allowed, false)
  context.refreshRunAdmission = async () => false
  const start = method('startExecution', 'publishedInputSchema', {startWorkflowExecution: () => {assert.fail('不得启动')}}, page)
  await start.call(context)
  assert.equal(context.starting, false)
})

test('列表运行失败持久显示原因且不关闭弹窗或自动重试', async () => {
  let calls = 0
  const start = method('startExecution', 'publishedInputSchema', {startWorkflowExecution: async () => {
    calls++; throw new Error('MCP连接已停用')
  }}, page)
  const context = {canExecute: true, runTarget: {id: 1, currentPublishedVersionId: 'v1'},
    runInputValidation: {valid: true}, runForm: {environment: 'PROD'}, runDialogOpen: true,
    refreshRunAdmission: async () => true}
  await start.call(context)
  assert.equal(calls, 1)
  assert.equal(context.runDialogOpen, true)
  assert.equal(context.runError, 'MCP连接已停用')
  assert.equal(context.starting, false)
})
