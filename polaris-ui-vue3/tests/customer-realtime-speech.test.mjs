import test from 'node:test'
import assert from 'node:assert/strict'
import {
    createCustomerPcmFramer,
    createCustomerRealtimeSpeech,
    customerRealtimeUrl
} from '../src/utils/customerRealtimeSpeech.js'
import {customerWav} from '../src/utils/customerVoiceState.js'

test('分片重采样与完整WAV一致，跨128样本分片不漂移，最后短帧保持正确', () => {
  for (const rate of [8000, 16000, 44100, 48000]) {
    const samples = Float32Array.from({ length: rate + 73 }, (_, i) => Math.sin(i / 8) * .7)
    const frames = [], framer = createCustomerPcmFramer(rate, frame => frames.push(frame))
    for (let start = 0; start < samples.length; start += 128) framer.push(samples.slice(start, start + 128))
    framer.finish(); framer.finish()
    assert.deepEqual(Buffer.concat(frames.map(frame => Buffer.from(frame))), Buffer.from(customerWav(samples, rate)).subarray(44))
    assert.ok(frames.every(frame => frame.byteLength <= 6400 && frame.byteLength % 2 === 0))
  }
})
test('WebSocket地址保持同源且不包含票据，跨域API配置降级HTTP', () => {
  assert.equal(customerRealtimeUrl('/dev-api', { origin: 'http://localhost' }), 'ws://localhost/dev-api/platform/runtime/customer-speech-stream')
  assert.equal(customerRealtimeUrl('', { origin: 'https://example.test' }), 'wss://example.test/platform/runtime/customer-speech-stream')
  assert.throws(() => customerRealtimeUrl('https://other.test/api', { origin: 'https://example.test' }))
})
function fixture() {
  const sent = [], partials = [], failures = []
  let current
  class Socket {
    bufferedAmount = 0
    constructor(url) { assert.equal(url, 'ws://fixture'); current = this }
    send(value) { sent.push(value) }
    close() { this.closed = true }
    event(value) { this.onmessage?.({ data: JSON.stringify(value) }) }
  }
  const stream = createCustomerRealtimeSpeech({ WebSocket: Socket, url: 'ws://fixture', onPartial: text => partials.push(text), onFailure: value => failures.push(value) })
  return { stream, sent, partials, failures, socket: () => current }
}
test('连接失败且尚未INIT允许HTTP降级；不上传任何音频', async () => {
  const f = fixture(), connecting = f.stream.connect('one-use-ticket')
  f.stream.audio(new ArrayBuffer(6400)); f.socket().onerror()
  await assert.rejects(connecting, error => error.fallbackAllowed === true)
  assert.equal(f.stream.submitted, false); assert.equal(f.sent.length, 0)
})
test('提交票据后断线不可HTTP重发，销毁后迟到回调无效', async () => {
  const f = fixture(), connecting = f.stream.connect('one-use-ticket')
  f.socket().onopen(); const late = f.socket().onmessage
  f.socket().onclose()
  await assert.rejects(connecting, error => error.fallbackAllowed === false)
  assert.equal(f.stream.submitted, true); assert.equal(f.sent.length, 1)
  late({ data: JSON.stringify({ type: 'PARTIAL', text: '迟到字幕' }) })
  assert.deepEqual(f.partials, [])
})
test('READY前不发送PCM，序号连续；临时字幕与最终结果分离', async () => {
  const f = fixture(), connecting = f.stream.connect('one-use-ticket')
  f.stream.audio(new ArrayBuffer(6400)); f.socket().onopen(); assert.equal(f.sent.length, 1)
  f.socket().event({ type: 'READY' }); await connecting
  assert.equal(new DataView(f.sent[1]).getUint32(0), 0)
  f.socket().event({ type: 'PARTIAL', text: '星河归档' }); assert.deepEqual(f.partials, ['星河归档'])
  f.stream.audio(new ArrayBuffer(3200)); assert.equal(new DataView(f.sent[2]).getUint32(0), 1)
  const finished = f.stream.finish()
  assert.equal(f.stream.finish(), finished)
  assert.equal(f.sent.length, 4)
  assert.deepEqual(JSON.parse(f.sent[3]), { type: 'FINISH', sequence: 2 })
  f.socket().event({ type: 'COMPLETE', result: { status: 'COMPLETED', text: '星河归档中心' } })
  assert.equal((await finished).text, '星河归档中心'); assert.equal(f.socket().closed, true)
})
test('队列有上限，取消清理音频与回调，不自动重连', async () => {
  const f = fixture(), connecting = f.stream.connect('one-use-ticket')
  f.stream.audio(new ArrayBuffer(6400)); f.stream.cancel(); await assert.rejects(connecting)
  assert.equal(f.socket().onopen, null); assert.equal(f.sent.length, 0)
  const g = fixture(), other = g.stream.connect('ticket')
  for (let i = 0; i < 101; i++) g.stream.audio(new ArrayBuffer(6400))
  await assert.rejects(other); assert.equal(g.failures.length, 1)
})
