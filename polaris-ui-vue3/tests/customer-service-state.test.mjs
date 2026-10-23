import assert from 'node:assert/strict'
import test from 'node:test'
import {
    customerConfirmation,
    customerIntentEntries,
    customerIntentRunnable,
    customerMessageRequest,
    customerSnapshot,
    customerTurnHint,
    customerVisitorTurnHint
} from '../src/utils/customerServiceState.js'

test('超时后重试保留请求标识和已提交的问题，不生成第二个请求', () => {
  const first = customerMessageRequest('  查询规则  ', null, () => 'request-1')
  const retry = customerMessageRequest('用户随后编辑的新问题', first, () => { throw new Error('不得生成新标识') })
  assert.equal(retry, first)
  assert.deepEqual(retry, { requestId: 'request-1', text: '查询规则' })
})

test('仅在服务端快照确认受理同一请求后清除重试状态', () => {
  const pending = { requestId: 'r1', text: '问题' }
  const current = { id: 'session-1', turns: [] }
  const unresolved = customerSnapshot(current, { ...current }, pending)
  assert.equal(unresolved.pending, pending)
  assert.equal(unresolved.accepted, false)
  const running = customerSnapshot(current, { ...current, turns: [{ requestId: 'r1', status: 'RUNNING' }] }, pending)
  assert.equal(running.pending, null)
  assert.equal(running.accepted, true)
})

test('已关闭会话的迟到响应不覆盖新会话或丢弃新输入', () => {
  const current = { id: 'session-new', turns: [] }
  const pending = { requestId: 'new-request', text: '新问题' }
  const result = customerSnapshot(current, { id: 'session-old', turns: [{ requestId: 'new-request' }] }, pending)
  assert.equal(result.session, current)
  assert.equal(result.pending, pending)
  assert.equal(result.accepted, false)
})

test('拒绝空白和超长输入；已完成请求后新的输入使用新的标识', () => {
  assert.throws(() => customerMessageRequest('  ', null))
  assert.throws(() => customerMessageRequest('x'.repeat(2001), null))
  assert.equal(customerMessageRequest('第二次提问', null, () => 'request-2').requestId, 'request-2')
})

test('发布或启停取消时正常返回，不触发未处理异常；其他错误不吞掉', async () => {
  assert.equal(await customerConfirmation(async () => {}), true)
  assert.equal(await customerConfirmation(async () => { throw 'cancel' }), false)
  assert.equal(await customerConfirmation(async () => { throw 'close' }), false)
  await assert.rejects(customerConfirmation(async () => { throw new Error('network') }), /network/)
})

test('区分检索、回答和证据故障，未知错误不展示原始消息', () => {
  assert.match(customerTurnHint({ status: 'FAILED', errorCode: 'KNOWLEDGE_SEARCH_FAILED' }), /向量模型/)
  assert.match(customerTurnHint({ status: 'FAILED', errorCode: 'MODEL_CALL_FAILED' }), /回答模型/)
  assert.match(customerTurnHint({ status: 'FAILED', errorCode: 'EVIDENCE_NOT_READY' }), /快照/)
  assert.match(customerTurnHint({ status: 'FAILED', errorCode: 'RESPONSE_MODE_UNAVAILABLE' }), /响应模式/)
  assert.doesNotMatch(customerTurnHint({ status: 'FAILED', errorCode: 'secret-provider-payload' }), /secret/)
})

test('访客主动打断和轮次超时显示停止，真实失败保持通用提示且不泄露诊断', () => {
  for (const errorCode of ['TURN_INTERRUPTED', 'TURN_EXPIRED', null]) {
    assert.equal(customerVisitorTurnHint({ status: 'INTERRUPTED', errorCode }), '本轮已停止，可以继续咨询。')
  }
  for (const errorCode of ['MODEL_CALL_FAILED', 'QUOTA_EXCEEDED', 'secret-provider-payload', null]) {
    assert.equal(customerVisitorTurnHint({ status: 'FAILED', errorCode }), '暂时无法完成回答，请稍后再试或联系人工。')
  }
  assert.equal(customerVisitorTurnHint({ status: 'RUNNING', errorCode: null }), '正在为你准备回答…')
  assert.equal(customerVisitorTurnHint({ status: 'COMPLETED', errorCode: 'SOURCE_UNAVAILABLE' }), '暂时无法完成回答，请稍后再试或联系人工。')
})

test('只有最新完成且未受理的 READY 摘要可以显式运行，旧摘要和补参不能启动', () => {
  const old = { id: 'old', status: 'COMPLETED', intent: { state: 'READY', started: false } }
  const latest = { id: 'latest', status: 'COMPLETED', intent: { state: 'READY', started: false } }
  assert.equal(customerIntentRunnable(old, [old, latest]), false)
  assert.equal(customerIntentRunnable(latest, [old, latest]), true)
  for (const change of [{ status: 'RUNNING' }, { intent: { state: 'COLLECTING' } }, { intent: { state: 'READY', started: true } }]) {
    const turn = { ...latest, ...change }
    assert.equal(customerIntentRunnable(turn, [turn]), false)
  }
})

test('参数摘要保留零和 false，路由失败提示不声称业务已执行', () => {
  assert.deepEqual(customerIntentEntries({ input: { count: 0, enabled: false, message: '<script>unsafe</script>' } }), [
    { name: 'count', value: '0' }, { name: 'enabled', value: 'false' }, { name: 'message', value: '<script>unsafe</script>' }
  ])
  assert.match(customerTurnHint({ status: 'FAILED', errorCode: 'ROUTE_INVALID' }), /没有启动/)
})
