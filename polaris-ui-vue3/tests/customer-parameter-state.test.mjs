import assert from 'node:assert/strict'
import test from 'node:test'
import {
    customerParameterChoices,
    customerParameterHints,
    customerParameterIssue,
    customerParameterLabel
} from '../src/utils/customerParameterState.js'
import {customerOperationInput} from '../src/utils/customerOperationState.js'
import {customerIntentLabel} from '../src/utils/customerServiceState.js'

test('名称说明示例用于提示且不填入用户参数，旧字段继续显示原名称', () => {
  const parameter = { name: 'message', label: '测试内容', type: 'string', required: true, description: '请提供实际文本', example: '示例文本', constraints: { minLength: 3, maxLength: 5 } }
  assert.equal(customerParameterLabel(parameter), '测试内容')
  assert.equal(customerParameterLabel({ name: 'legacy' }), 'legacy')
  assert.deepEqual(customerParameterHints(parameter), ['请提供实际文本', '至少 3 个字符', '最多 5 个字符', '示例（不会自动填写）：示例文本'])
  const values = {}
  assert.throws(() => customerOperationInput({ inputs: [parameter] }, values), /请填写 测试内容/)
  assert.deepEqual(values, {})
  assert.equal(customerIntentLabel({ parameters: [parameter] }, 'message'), '测试内容')
  assert.equal(customerIntentLabel({}, 'message'), 'message')
})

test('枚举和常量使用原始标量值，false 与 0 不变成空值或自动默认值', () => {
  const enabled = { name: 'enabled', type: 'boolean', required: true, label: '是否启用', constraints: { enum: [false, true], const: false } }
  const count = { name: 'count', type: 'integer', required: true, label: '次数', constraints: { enum: [0, 1] } }
  assert.deepEqual(customerParameterChoices(enabled), [{ label: 'false', value: false }])
  assert.deepEqual(customerParameterChoices(count), [{ label: '0', value: 0 }, { label: '1', value: 1 }])
  assert.deepEqual(customerOperationInput({ inputs: [enabled, count] }, { enabled: false, count: 0 }), { enabled: false, count: 0 })
  assert.throws(() => customerOperationInput({ inputs: [enabled] }, { enabled: true }), /允许的值/)
  assert.throws(() => customerOperationInput({ inputs: [count] }, { count: 2 }), /允许的值/)
})

test('字符长度按 Unicode 码点计算，数值包含边界和排除边界都提前提示', () => {
  const text = { name: 'message', type: 'string', label: '内容', constraints: { minLength: 2, maxLength: 2 } }
  assert.match(customerParameterIssue(text, '😀'), /至少/)
  assert.equal(customerParameterIssue(text, '😀😀'), '')
  assert.match(customerParameterIssue(text, '😀😀😀'), /最多/)
  const number = { name: 'amount', type: 'number', label: '金额', constraints: { minimum: 0, exclusiveMaximum: 10 } }
  assert.equal(customerParameterIssue(number, 0), '')
  assert.match(customerParameterIssue(number, -1), /范围/)
  assert.match(customerParameterIssue(number, 10), /范围/)
})

test('格式由后端判定，提示只使用文本，示例 false 和 0 仍可见', () => {
  const parameter = { name: 'id', description: '<script>example</script>', example: 0, constraints: { pattern: '^[A-Z][0-9]+$' } }
  assert.deepEqual(customerParameterHints(parameter), ['<script>example</script>', '须符合配置的文本格式', '示例（不会自动填写）：0'])
  assert.equal(customerParameterIssue(parameter, 'wrong'), '')
  assert.match(customerParameterHints({ example: false }).join(''), /false/)
})
