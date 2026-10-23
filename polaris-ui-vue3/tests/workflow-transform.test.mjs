import assert from 'node:assert/strict'
import test from 'node:test'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {parse} from '@vue/compiler-sfc'

const {descriptor} = parse(readFileSync(new URL('../src/components/workflow/WorkflowTransformEditor.vue', import.meta.url), 'utf8'))
const source = descriptor.script.content.match(/    addRule\(operation\) \{[\s\S]*?\n    \},\n    nextTargetName/)[0]
  .replace(/,\n    nextTargetName$/, '')
const addRule = vm.runInNewContext(`({${source}}).addRule`)

function state(mode, schema) {
  return {mode, rules: [], availableFields: [], sourceItemSchema: schema,
    selectedRuleField: () => ({path: '', type: schema.type, schema}),
    normalizedType: type => type || 'object', cloneSchema: value => value && structuredClone(value),
    nextTargetName: () => 'field1', targetPathFromSource: path => path || 'field1',
    updateConfig(config) {this.rules = config.rules}}
}

test('整份数据 COPY 保存根数组和基础值的发布契约，而非默认对象', () => {
  for (const schema of [{type: 'array', items: {type: 'object'}}, {type: 'string'},
    {type: 'number'}, {type: 'integer'}, {type: 'boolean'}, {type: 'object', properties: {title: {type: 'string'}}}]) {
    const context = state('VALUE', schema)
    addRule.call(context, 'COPY')
    const rule = context.rules[0]
    assert.equal(rule.targetPath, '$')
    assert.equal(rule.sourcePath, '')
    assert.equal(rule.sourceType, schema.type)
    assert.equal(rule.resultType, schema.type)
    assert.deepEqual(rule.resultSchema, schema)
    assert.notEqual(rule.resultSchema, schema)
  }
})

test('常量规则仍使用独立的字符串契约，不继承数组来源', () => {
  const context = state('VALUE', {type: 'array', items: {type: 'object'}})
  addRule.call(context, 'CONSTANT')
  assert.equal(context.rules[0].resultType, 'string')
  assert.equal(context.rules[0].resultSchema.type, 'string')
  assert.equal(context.rules[0].value, '')
})

test('对象映射仍优先选取未使用字段并保存其契约', () => {
  const context = state('OBJECT_MAP', {type: 'object'})
  context.availableFields = [{path: 'results', type: 'array', schema: {type: 'array', items: {type: 'object'}}}]
  addRule.call(context, 'COPY')
  assert.equal(context.rules[0].targetPath, 'results')
  assert.equal(context.rules[0].sourcePath, 'results')
  assert.equal(context.rules[0].resultSchema.type, 'array')
})
