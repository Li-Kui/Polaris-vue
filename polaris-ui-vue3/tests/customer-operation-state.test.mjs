import assert from 'node:assert/strict'
import test from 'node:test'
import {
    customerOperationActive,
    customerOperationBlocking,
    customerOperationInput,
    customerOperationRequest,
    mergeCustomerOperation
} from '../src/utils/customerOperationState.js'

const capability = { code: 'echo_test', inputs: [{ name: 'message', type: 'string', required: true }, { name: 'count', type: 'integer', required: false }, { name: 'enabled', type: 'boolean', required: false }] }

test('任务参数只收集声明的用户字段，保留 false 和 0，拒绝漏填和错误类型', () => {
  assert.deepEqual(customerOperationInput(capability, { message: 'hello', count: '0', enabled: false, subject: 999, definitionId: 5 }), { message: 'hello', count: 0, enabled: false })
  assert.throws(() => customerOperationInput(capability, {}), /请填写 message/)
  assert.throws(() => customerOperationInput(capability, { message: 'hello', count: '9007199254740993' }), /安全精度/)
  assert.throws(() => customerOperationInput(capability, { message: 'hello', enabled: 'true' }), /选择是或否/)
})

test('结果不明确时重试保留原能力、参数和去重标识，不接受随后编辑的输入', () => {
  const values = { message: 'first', count: '1' }
  const pending = customerOperationRequest(capability, values, null, () => 'r1')
  values.message = 'changed'
  assert.equal(customerOperationRequest({ code: 'different' }, values, pending, () => { throw new Error('不应新建') }), pending)
  assert.deepEqual(pending, { requestId: 'r1', capabilityCode: 'echo_test', input: { message: 'first', count: 1 } })
})

test('迟到的其他请求结果不能替换当前任务，已受理任务按请求更新而非重复插入', () => {
  const current = [{ id: 'one', requestId: 'r1', status: 'QUEUED' }]
  assert.equal(mergeCustomerOperation(current, { requestId: 'r2' }, 'r1'), current)
  const updated = mergeCustomerOperation(current, { id: 'one', requestId: 'r1', status: 'SUCCEEDED' }, 'r1')
  assert.equal(updated.length, 1)
  assert.equal(updated[0].status, 'SUCCEEDED')
  assert.equal(current[0].status, 'QUEUED')
})

test('成功失败和取消停止轮询，恢复或需处理状态不自动启动新任务', () => {
  for (const status of ['SUCCEEDED', 'FAILED', 'CANCELLED', 'REJECTED', 'RESULT_REJECTED', 'UNAVAILABLE']) assert.equal(customerOperationActive({ status }), false)
  for (const status of ['QUEUED', 'RUNNING', 'RECOVERING', 'NEEDS_ATTENTION']) assert.equal(customerOperationActive({ status }), true)
  for (const status of ['UNAVAILABLE', 'FUTURE_UNKNOWN', 'QUEUED']) assert.equal(customerOperationBlocking({ status }), true)
  for (const status of ['SUCCEEDED', 'CANCELLED', 'RESULT_REJECTED']) assert.equal(customerOperationBlocking({ status }), false)
})
