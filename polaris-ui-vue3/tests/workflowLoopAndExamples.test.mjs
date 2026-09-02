import test from 'node:test'
import assert from 'node:assert/strict'
import {loopResultNodeOptions, withSynchronizedLoopReturn} from '../src/components/workflow/workflowLoop.js'
import {createOptimizedNodesExample} from '../src/components/workflow/workflowExamples.js'

test('循环末节点允许原先直达完成出口，并自动转换为返回路径', () => {
  const definition = {
    nodes: [{id: 'loop', name: '循环'}, {id: 'body', name: '处理'}, {id: 'after', name: '后续'}],
    edges: [
      {id: 'body-edge', source: 'loop', target: 'body', kind: 'LOOP'},
      {id: 'old-exit', source: 'body', target: 'after', kind: 'NORMAL'}
    ]
  }
  assert.deepEqual(loopResultNodeOptions(definition, 'loop', 'body', 'after'), [
    {id: 'body', name: '处理'}
  ])
  const edges = withSynchronizedLoopReturn(definition, 'loop', 'body', 'after', () => 'return')
  assert.equal(edges.some(item => item.id === 'old-exit'), false)
  assert.ok(edges.some(item => item.id === 'return' && item.source === 'body'
    && item.target === 'loop' && item.targetPort === 'loop-return'))
})

test('循环末节点不接受仍有普通下游处理的中间节点', () => {
  const definition = {
    nodes: [{id: 'loop'}, {id: 'first', name: '第一步'}, {id: 'last', name: '最后一步'}],
    edges: [
      {source: 'loop', target: 'first', kind: 'LOOP'},
      {source: 'first', target: 'last', kind: 'NORMAL'}
    ]
  }
  assert.deepEqual(loopResultNodeOptions(definition, 'loop', 'first', ''), [
    {id: 'last', name: '最后一步'}
  ])
})

test('循环分支必须先汇入同一节点，才提供本轮结束候选', () => {
  const split = {
    nodes: [
      {id: 'loop', type: 'loop'}, {id: 'branch', type: 'condition'},
      {id: 'left', type: 'transform', name: '左侧结果'},
      {id: 'right', type: 'transform', name: '右侧结果'}
    ],
    edges: [
      {source: 'loop', target: 'branch', kind: 'LOOP'},
      {source: 'branch', target: 'left', kind: 'CONDITION'},
      {source: 'branch', target: 'right', kind: 'CONDITION'}
    ]
  }
  assert.deepEqual(loopResultNodeOptions(split, 'loop', 'branch', ''), [])

  split.nodes.push({id: 'merged', type: 'transform', name: '统一结果'})
  split.edges.push(
    {source: 'left', target: 'merged', kind: 'NORMAL'},
    {source: 'right', target: 'merged', kind: 'NORMAL'}
  )
  assert.deepEqual(loopResultNodeOptions(split, 'loop', 'branch', ''), [
    {id: 'merged', name: '统一结果'}
  ])
})

test('优化节点示例包含完整循环、分类汇流和嵌套数据映射', () => {
  const definition = createOptimizedNodesExample({
    versionFor: type => `${type}-version`,
    resourceRefsFor: () => [{kind: 'MODEL', key: 'primary_model', required: true}]
  })
  const loop = definition.nodes.find(node => node.type === 'loop')
  const classifier = definition.nodes.find(node => node.type === 'llm_classifier')
  const transforms = definition.nodes.filter(node => node.type === 'transform')
  assert.equal(loop.config.resultNodeId, 'build_result')
  assert.equal(classifier.resourceRefs[0].kind, 'MODEL')
  assert.equal(definition.edges.filter(item => item.kind === 'SEMANTIC').length, 3)
  assert.ok(definition.edges.some(item => item.source === 'build_result'
    && item.target === loop.id && item.targetPort === 'loop-return'))
  assert.ok(transforms[0].config.rules.some(rule => rule.targetPath === 'details.attachments'))
  assert.equal(definition.outputs.results.expression, '$.nodes.loop_feedback.output.results')
})
