import assert from 'node:assert/strict'
import test from 'node:test'
import {
    customerCapabilityContract,
    customerCapabilityDraft,
    customerCapabilitySave
} from '../src/utils/customerCapabilityState.js'

const binding = () => ({ code: 'order_query', name: '订单查询', description: '测试契约', definitionId: 30, versionId: 'v1', inputSources: { orderNo: 'USER_INPUT', subject: 'PLATFORM_USER_ID' }, outputFields: ['status'] })

test('编辑能力使用独立副本，取消不会污染原有草稿', () => {
  const original = binding()
  const draft = customerCapabilityDraft(original)
  draft.inputSources.subject = 'TENANT_ID'
  draft.outputFields.push('internal')
  assert.equal(original.inputSources.subject, 'PLATFORM_USER_ID')
  assert.deepEqual(original.outputFields, ['status'])
})

test('版本切换的迟到契约不得覆盖当前版本或工作流', () => {
  const contract = { definitionId: 30, versionId: 'v1', inputs: [], outputFields: [] }
  assert.equal(customerCapabilityContract({ ...binding(), versionId: 'v2' }, contract), null)
  assert.equal(customerCapabilityContract({ ...binding(), definitionId: 31 }, contract), null)
})

test('契约变化清除过时字段，新增输入显式设置来源，不默认展示全部输出', () => {
  const original = binding()
  const updated = customerCapabilityContract(original, { definitionId: 30, versionId: 'v1', inputs: [{ name: 'subject' }, { name: 'newParam' }], outputFields: ['internal'] })
  assert.deepEqual(updated.inputSources, { subject: 'PLATFORM_USER_ID', newParam: 'USER_INPUT' })
  assert.deepEqual(updated.outputFields, [])
  assert.deepEqual(original.outputFields, ['status'])
})

test('加入草稿拒绝重复能力、超过上限和失效编辑位置', () => {
  assert.throws(() => customerCapabilitySave([binding()], null, binding()), /不能重复/)
  assert.throws(() => customerCapabilitySave(Array.from({ length: 5 }, (_, index) => ({ ...binding(), code: `code_${index}` })), null, binding()), /最多/)
  assert.throws(() => customerCapabilitySave([], 0, binding()), /已变化/)
  const updated = customerCapabilitySave([binding()], 0, { ...binding(), name: '新名称' })
  assert.equal(updated[0].name, '新名称')
})
