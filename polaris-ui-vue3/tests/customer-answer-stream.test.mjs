import test from 'node:test'
import assert from 'node:assert/strict'
import {createCustomerAnswerPlayback, readCustomerAnswerStream} from '../src/utils/customerAnswerStream.js'

const turnId = '11111111-1111-4111-8111-111111111111', requestId = 'request-1'
const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
const accepted = { type: 'accepted', turnId, requestId }
const part = (partNo, text = `第${partNo}句[S1]。`) => ({ type: 'part', turnId, requestId, partNo, text })
const completed = (answer, status = 'COMPLETED') => ({ type: 'completed', turnId, requestId, turn: { id: turnId, requestId, status, answer } })
function response(values) {
  const bytes = new TextEncoder().encode(values.map(value => typeof value === 'string' ? value : JSON.stringify(value)).join('\n') + '\n')
  return new Response(new ReadableStream({ start(controller) {
    // 故意逐字节分片，包括中文多字节字符与JSON边界。
    for (const byte of bytes) controller.enqueue(new Uint8Array([byte]))
    controller.close()
  } }), { headers: { 'Content-Type': 'application/x-ndjson;charset=UTF-8' } })
}
test('真实HTTP流能在终态前交付中文句段，UTF8任意分片不会乱码', async () => {
  const release = deferred(), events = []
  let output
  const response = new Response(new ReadableStream({ start(controller) { output = controller } }), { headers: { 'content-type': 'application/x-ndjson' } })
  const reading = readCustomerAnswerStream(response, requestId, event => { events.push(event); if (event.type === 'part') release.resolve() })
  output.enqueue(new TextEncoder().encode(JSON.stringify(accepted) + '\n' + JSON.stringify(part(1)) + '\n'))
  await release.promise
  assert.equal(events.at(-1).type, 'part')
  output.enqueue(new TextEncoder().encode(JSON.stringify(completed(part(1).text)) + '\n')); output.close()
  assert.equal((await reading).answer, part(1).text)
  assert.equal((await readCustomerAnswerStream(responseForChinese(), requestId, () => {})).answer, '中文[S1]。')
})
function responseForChinese() { return response([accepted, part(1, '中文[S1]。'), completed('中文[S1]。')]) }
test('断线、缺终态、重复分段、乱序及跨请求事件都不当作成功，不自动重发', async () => {
  for (const values of [[accepted, part(1)], [part(1)], [accepted, part(2)], [accepted, part(1), part(1)], [accepted, { ...part(1), requestId: 'other' }], [accepted, { ...part(1), turnId: 'other' }], [accepted, { ...part(1), text: 'x'.repeat(601) }], ['invalid-json']]) {
    await assert.rejects(readCustomerAnswerStream(response(values), requestId, () => {}))
  }
  await assert.rejects(readCustomerAnswerStream(new Response('{}'), requestId, () => {}))
})
test('同一请求恢复只接收终态，运行中不能伪装成已完成', async () => {
  const result = await readCustomerAnswerStream(response([completed(null, 'RUNNING')]), requestId, () => {})
  assert.equal(result.status, 'RUNNING')
})
test('首段即播放，后续生成与播放并行，播放始终串行且不重复', async () => {
  const first = deferred(), second = deferred(), calls = []
  const queue = createCustomerAnswerPlayback({ current: () => true, play: (text, id, n) => { calls.push(n); return n === 1 ? first.promise : second.promise } })
  queue.push(part(1)); await tick(); assert.deepEqual(calls, [1])
  queue.push(part(2)); const done = queue.finish(completed(part(1).text + part(2).text).turn)
  await tick(); assert.deepEqual(calls, [1])
  first.resolve(true); await tick(); assert.deepEqual(calls, [1, 2])
  let finished = false; done.then(() => { finished = true }); await tick(); assert.equal(finished, false)
  second.resolve(true); assert.equal(await done, true)
})
test('取消立即清理播放，迟到合成、排队内容和旧代次不能恢复', async () => {
  const first = deferred(), calls = []; let stops = 0, current = true
  const queue = createCustomerAnswerPlayback({ current: () => current, stop: () => { stops++ }, play: (_, __, n) => { calls.push(n); return first.promise } })
  queue.push(part(1)); queue.push(part(2)); await tick(); queue.cancel(); current = false
  queue.push(part(3)); first.resolve(true)
  assert.equal(await queue.finish(completed(part(1).text + part(2).text).turn), false)
  assert.deepEqual(calls, [1]); assert.ok(stops > 0)
})
test('无分段保留整轮兜底；终态失败或漏段时不重播整轮，不继续队列', async () => {
  const fresh = createCustomerAnswerPlayback({ current: () => true, play: () => assert.fail('不应自动播放') })
  assert.equal(fresh.finish(completed('完整回答').turn), null)
  for (const result of [completed('不同的回答').turn, completed(part(1).text, 'FAILED').turn, { ...completed(part(1).text).turn, errorCode: 'CONTENT_BLOCKED' }]) {
    let calls = 0, stops = 0
    const queue = createCustomerAnswerPlayback({ current: () => true, play: () => { calls++; return true }, stop: () => { stops++ } })
    queue.push(part(1)); assert.equal(await queue.finish(result), false); assert.equal(calls, 0); assert.equal(stops, 1)
  }
})

test('首段播放失败立即停止队列并取消读取，不等后续模型生成完再报告失败', async () => {
  let failed = 0, stops = 0, plays = 0
  const queue = createCustomerAnswerPlayback({ current: () => true, stop: () => { stops++ }, onFailure: () => { failed++ }, play: () => { plays++; return false } })
  queue.push(part(1)); queue.push(part(2)); await tick()
  assert.equal(failed, 1); assert.equal(stops, 1); assert.equal(plays, 1)
  assert.equal(await queue.finish(completed(part(1).text + part(2).text).turn), false)
})
