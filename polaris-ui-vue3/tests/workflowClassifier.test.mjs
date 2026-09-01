import test from 'node:test'
import assert from 'node:assert/strict'
import {
  classifierSchemaOptions,
  classifierTargetOptions,
  isClassifierTargetAllowed,
  workflowUpstreamNodeIds
} from '../src/components/workflow/workflowClassifier.js'

const definition = {
  nodes: [
    {id: 'source', name: '来源'},
    {id: 'classifier', name: '语义分类'},
    {id: 'downstream', name: '下游'}
  ],
  edges: [
    {source: '__start__', target: 'source'},
    {source: 'source', target: 'classifier'}
  ]
}

test('分类目标排除自身、开始节点与全部上游节点', () => {
  assert.deepEqual([...workflowUpstreamNodeIds(definition, 'classifier')], ['source'])
  assert.equal(isClassifierTargetAllowed(definition, 'classifier', 'source'), false)
  assert.equal(isClassifierTargetAllowed(definition, 'classifier', 'classifier'), false)
  assert.equal(isClassifierTargetAllowed(definition, 'classifier', '__start__'), false)
  assert.equal(isClassifierTargetAllowed(definition, 'classifier', 'downstream'), true)
  assert.equal(isClassifierTargetAllowed(definition, 'classifier', '__end__'), true)
  assert.deepEqual(classifierTargetOptions(definition, 'classifier'), [
    {id: 'downstream', name: '下游'},
    {id: '__end__', name: '结束流程'}
  ])
})

test('字段选择可展开对象、数组、数组对象与嵌套数组', () => {
  const schema = {
    type: 'object',
    properties: {
      customer: {type: 'object', properties: {name: {type: 'string'}}},
      orders: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            id: {type: 'integer'},
            items: {
              type: 'array',
              items: {type: 'object', properties: {name: {type: 'string'}}}
            }
          }
        }
      },
      matrix: {type: 'array', items: {type: 'array', items: {type: 'number'}}}
    },
    allOf: [{type: 'object', properties: {extended: {type: 'boolean'}}}]
  }

  const expressions = classifierSchemaOptions(schema, '$.input', '完整流程输入')
    .map(option => option.expression)
  assert.ok(expressions.includes('$.input'))
  assert.ok(expressions.includes('$.input.customer.name'))
  assert.ok(expressions.includes('$.input.orders[]'))
  assert.ok(expressions.includes('$.input.orders[].id'))
  assert.ok(expressions.includes('$.input.orders[].items[].name'))
  assert.ok(expressions.includes('$.input.matrix[][]'))
  assert.ok(expressions.includes('$.input.extended'))

  const rootArrayExpressions = classifierSchemaOptions({
    type: 'array', items: {type: 'object', properties: {name: {type: 'string'}}}
  }, '$.input', '完整流程输入').map(option => option.expression)
  assert.ok(rootArrayExpressions.includes('$.input[].name'))
})

test('递归结构受深度和数量上限保护', () => {
  const recursive = {type: 'object', properties: {}}
  recursive.properties.next = recursive
  const options = classifierSchemaOptions(recursive, '$.input', '输入', {
    maxDepth: 3,
    maxOptions: 4
  })
  assert.equal(options.length, 4)
})
