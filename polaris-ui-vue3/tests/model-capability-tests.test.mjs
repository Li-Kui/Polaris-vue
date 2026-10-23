import assert from 'node:assert/strict'
import test from 'node:test'
import {computed, effectScope, ref} from 'vue'
import {useCapabilityTests} from '../src/components/model-center/useCapabilityTests.js'

const capability = code => ({key: code, capabilityCode: code, schema: {name: code === 'CHAT_COMPLETION' ? '对话' : '向量'}})
function setup(request) {
  const scope = effectScope()
  const capabilities = ref([capability('CHAT_COMPLETION'), capability('TEXT_EMBEDDING')])
  const draft = ref('original')
  const calls = []
  const state = scope.run(() => useCapabilityTests({capabilities, fingerprint: computed(() => draft.value),
    makeRequest: item => ({capabilityCode: item.capabilityCode, testCapability: item.capabilityCode}),
    request: payload => { calls.push(payload); return request(payload) }}))
  return {scope, state, capabilities, draft, calls}
}
const deferred = () => { let resolve; const promise = new Promise(accept => { resolve = accept }); return {promise, resolve} }

test('仅调用用户选择的能力，其他能力保持未测试，保留真实零耗时', async () => {
  const {scope, state, calls} = setup(async () => ({success: true, metrics: {latencyMs: 0}}))
  state.selectedKey.value = 'TEXT_EMBEDDING'
  await state.run()
  assert.deepEqual(calls, [{capabilityCode: 'TEXT_EMBEDDING', testCapability: 'TEXT_EMBEDDING'}])
  assert.equal(state.results.value.CHAT_COMPLETION, undefined)
  assert.equal(state.results.value.TEXT_EMBEDDING.message, '向量能力测试通过')
  assert.equal(state.results.value.TEXT_EMBEDDING.latencyMs, 0)
  scope.stop()
})

test('配置变化清空结果，切回原配置也不能复用测试结果', async () => {
  const {scope, state, draft} = setup(async () => ({success: true}))
  await state.run()
  assert.equal(state.results.value.CHAT_COMPLETION.success, true)
  draft.value = 'changed'; draft.value = 'original'
  assert.deepEqual(state.results.value, {})
  scope.stop()
})

test('测试中不重复调用，配置变化后迟到成功不得认证新草稿', async () => {
  const pending = deferred()
  const {scope, state, draft, calls} = setup(() => pending.promise)
  const running = state.run()
  await state.run()
  assert.equal(calls.length, 1)
  draft.value = 'changed'; draft.value = 'original'
  pending.resolve({success: true})
  await running
  assert.deepEqual(state.results.value, {})
  assert.equal(state.testingKey.value, '')
  scope.stop()
})

test('失败与无效响应不显示通过，也不自动重试', async () => {
  for (const response of [{success: false, message: '不支持此能力'}, {message: '模型测试成功'}]) {
    const {scope, state, calls} = setup(async () => response)
    await state.run()
    assert.equal(state.results.value.CHAT_COMPLETION.success, false)
    assert.equal(calls.length, 1)
    scope.stop()
  }
})

test('离开编辑器后丢弃响应；移除所选能力后选择有效能力', async () => {
  const pending = deferred()
  const {scope, state, capabilities} = setup(() => pending.promise)
  state.selectedKey.value = 'TEXT_EMBEDDING'
  capabilities.value = [capability('CHAT_COMPLETION')]
  assert.equal(state.selectedKey.value, 'CHAT_COMPLETION')
  const running = state.run()
  scope.stop()
  pending.resolve({success: true})
  await running
  assert.deepEqual(state.results.value, {})
})
