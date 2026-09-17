import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {
    normalizeParallelConfig,
    parallelAvailableContinuationOptions,
    parallelEntryNodeOptions,
    parallelEntryOptionsForBranch,
    parallelResultNodeOptions,
    withSynchronizedParallelReturns,
    workflowNodeOptionLabel
} from '../src/components/workflow/workflowParallel.js'

const workbench = await readFile(
  new URL('../src/components/workflow/WorkflowWorkbench.vue', import.meta.url), 'utf8')
const parallelEditor = await readFile(
  new URL('../src/components/workflow/WorkflowParallelEditor.vue', import.meta.url), 'utf8')
const workflowApi = await readFile(
  new URL('../src/api/ai/workflow.js', import.meta.url), 'utf8')

test('并行任务组始终提供至少两条具有稳定标识的任务线', () => {
  const config = normalizeParallelConfig({branches: [{key: 'same', name: '任务 A'}, {key: 'same', name: '任务 B'}]})
  assert.equal(config.version, 2)
  assert.equal(config.completionMode, 'ALL_SUCCEEDED')
  assert.equal(config.branches.length, 2)
  assert.equal(new Set(config.branches.map(branch => branch.key)).size, 2)
})

test('每条并行任务线只列出自身路径中统一汇入的末节点', () => {
  const definition = {
    nodes: [
      {id: 'parallel', type: 'parallel'}, {id: 'left', type: 'transform', name: '左入口'},
      {id: 'left_last', type: 'transform', name: '左结果'},
      {id: 'right', type: 'transform', name: '右入口'},
      {id: 'after', type: 'transform', name: '后续'}
    ],
    edges: [
      {source: 'parallel', sourcePort: 'left', target: 'left', kind: 'PARALLEL'},
      {source: 'parallel', sourcePort: 'right', target: 'right', kind: 'PARALLEL'},
      {source: 'parallel', sourcePort: 'completed', target: 'after', kind: 'NORMAL'},
      {source: 'left', target: 'left_last', kind: 'NORMAL'},
      {source: 'left_last', target: 'after', kind: 'NORMAL'}
    ]
  }
  assert.deepEqual(parallelResultNodeOptions(
    definition, 'parallel', 'left', 'after', ['right']
  ), [{id: 'left_last', name: '左结果'}])
})

test('任务线入口排除结束节点、上游节点和已经接入其他路径的节点', () => {
  const definition = {
    nodes: [
      {id: 'before', name: '上游'}, {id: 'parallel', name: '并行任务组'},
      {id: 'main', name: '主流程节点'}, {id: 'free', name: '未接入节点'},
      {id: 'current', name: '当前任务线入口'}
    ],
    edges: [
      {source: '__start__', target: 'before', kind: 'NORMAL'},
      {source: 'before', target: 'parallel', kind: 'NORMAL'},
      {source: 'before', target: 'main', kind: 'NORMAL'},
      {source: 'parallel', sourcePort: 'left', target: 'current', kind: 'PARALLEL'}
    ]
  }
  assert.deepEqual(parallelEntryNodeOptions(definition, 'parallel'), [
    {id: 'free', name: '未接入节点'},
    {id: 'current', name: '当前任务线入口'}
  ])
})

test('任务线和完成去向在展示前排除已占用节点', () => {
  const options = [
    {id: 'left', name: '数据转换'},
    {id: 'right', name: '数据转换'},
    {id: 'after', name: '数据转换'}
  ]
  const targets = {branch_1: 'left', branch_2: 'right'}
  assert.deepEqual(parallelEntryOptionsForBranch(
    options, targets, 'branch_1', 'after'
  ), [{id: 'left', name: '数据转换'}])
  assert.deepEqual(parallelAvailableContinuationOptions(options, targets), [
    {id: 'after', name: '数据转换'}
  ])
})

test('同名节点选项显示稳定节点标识', () => {
  assert.equal(workflowNodeOptionLabel({id: 'transform_2', name: '数据转换'}), '数据转换 · transform_2')
  assert.equal(workflowNodeOptionLabel({id: 'transform_2', name: 'transform_2'}), 'transform_2')
  assert.match(parallelEditor, /entryOptionsForBranch\(branch\.key\)/)
  assert.match(parallelEditor, /availableContinuationOptions/)
  assert.match(parallelEditor, /:label="nodeOptionLabel\(node\)"/)
})

test('校验当前定义不自动保存，并行节点不提供隔离试运行', () => {
  const validateMethod = workbench.slice(
    workbench.indexOf('async validate()'),
    workbench.indexOf('async publish()'))
  assert.equal(validateMethod.includes('saveDraft'), false)
  assert.match(validateMethod, /validateWorkflowDraft\([\s\S]*this\.definitionJson\(\)/)
  assert.match(workbench, /node\?\.type === 'parallel'\) return false/)
  assert.match(workbench, /请选择尚未接入其他路径的普通任务节点作为任务线入口/)
  assert.match(workflowApi, /definitions\/validate/)
})

test('系统为每条任务线创建唯一返回边并移除末节点直达后续的边', () => {
  const definition = {
    edges: [
      {id: 'left-exit', source: 'left_last', target: 'after', kind: 'NORMAL'},
      {id: 'right-exit', source: 'right_last', target: '__end__', kind: 'NORMAL'},
      {id: 'old-return', source: 'old', target: 'parallel', targetPort: 'parallel-return:old', kind: 'NORMAL'}
    ]
  }
  let sequence = 0
  const edges = withSynchronizedParallelReturns(definition, 'parallel', [
    {key: 'left', resultNodeId: 'left_last'},
    {key: 'right', resultNodeId: 'right_last'}
  ], 'after', () => `return-${++sequence}`)
  assert.equal(edges.some(edge => ['left-exit', 'right-exit', 'old-return'].includes(edge.id)), false)
  assert.deepEqual(edges.map(edge => edge.targetPort).sort(), [
    'parallel-return:left', 'parallel-return:right'
  ])
})
