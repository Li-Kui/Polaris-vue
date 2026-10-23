import test from 'node:test'
import assert from 'node:assert/strict'
import {parseWorkflowContract} from '../src/components/workflow/workflowContract.js'

test('流程契约编辑保留封闭输入 Schema 和输出表达式，创建独立对象', () => {
  const source = { type: 'object', properties: { message: { type: 'string', maxLength: 100 } }, additionalProperties: false }
  const result = parseWorkflowContract(JSON.stringify(source), '{"echo":{"expression":"$.input.message"}}')
  assert.deepEqual(result.inputs, source)
  assert.equal(result.outputs.echo.expression, '$.input.message')
  result.inputs.properties.message.maxLength = 1
  assert.equal(source.properties.message.maxLength, 100)
})

test('拒绝无效 JSON、空值、数组及错误根类型', () => {
  for (const input of ['{', 'null', '[]', '{"type":"array"}', '{"type":"object","properties":[]}']) assert.throws(() => parseWorkflowContract(input, '{}'))
  for (const output of ['{', 'null', '[]']) assert.throws(() => parseWorkflowContract('{"type":"object","properties":{}}', output))
})
