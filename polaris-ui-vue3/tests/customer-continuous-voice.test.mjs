import test from 'node:test'
import assert from 'node:assert/strict'
import {createCustomerSpeechActivity} from '../src/utils/customerSpeechActivity.js'
import {createCustomerVoiceConversation} from '../src/utils/customerVoiceConversation.js'

const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
function fixture(overrides = {}) {
  const events = [], calls = { listen: 0, ask: 0, play: 0, cancel: 0 }
  let expiry
  const controller = createCustomerVoiceConversation({
    listen: async () => { calls.listen++; return { text: '合成知识问题' } },
    ask: async () => { calls.ask++; return { status: 'COMPLETED', answer: '合成知识回答' } },
    play: async () => { calls.play++; return true },
    cancel: async () => { calls.cancel++; return true }, interrupt: async () => true,
    onState: (state, hint) => events.push({ state, hint }),
    setTimer: callback => { expiry = callback }, clearTimer() {}, ...overrides
  })
  return { controller, events, calls, expire: () => expiry() }
}
test('连续语音按识别、问答、播报串行执行，播报完成后才重新倾听，到轮数上限暂停', async () => {
  const f = fixture({ maxRounds: 2 })
  await f.controller.start()
  assert.deepEqual(f.events.map(event => event.state), ['LISTENING', 'THINKING', 'SPEAKING', 'LISTENING', 'THINKING', 'SPEAKING', 'STOPPING', 'PAUSED'])
  assert.deepEqual(f.calls, { listen: 2, ask: 2, play: 2, cancel: 1 })
})
test('结束后迟到识别结果不能发送，清理期间不能重新开启麦克风', async () => {
  const input = deferred(), cleanup = deferred()
  const f = fixture({ listen: () => input.promise, cancel: () => cleanup.promise })
  const running = f.controller.start(), ending = f.controller.stop()
  await f.controller.start(); assert.equal(f.events.at(-1).state, 'STOPPING')
  input.resolve({ text: '迟到识别' }); await running
  assert.equal(f.calls.ask, 0)
  cleanup.resolve(true); await ending; assert.equal(f.events.at(-1).state, 'OFF')
})
test('思考期间打断先确认停止，再开启新倾听，旧回答不播报', async () => {
  const answer = deferred(), secondInput = deferred(); let listens = 0
  const f = fixture({ listen: () => ++listens === 1 ? { text: '问题' } : secondInput.promise, ask: () => answer.promise })
  const running = f.controller.start(); await tick()
  const interrupting = f.controller.interrupt(); await tick()
  assert.equal(listens, 2); assert.equal(f.events.at(-1).state, 'LISTENING')
  answer.resolve({ status: 'COMPLETED', answer: '被打断的旧回答' }); await running
  assert.equal(f.calls.play, 0)
  await f.controller.stop(); secondInput.resolve({ text: '迟到的新句' }); await interrupting
  assert.equal(f.calls.play, 0)
})
test('打断未确认时保持暂停，不打开新录音', async () => {
  const answer = deferred()
  const f = fixture({ ask: () => answer.promise, interrupt: async () => false })
  const running = f.controller.start(); await tick(); await f.controller.interrupt()
  assert.equal(f.calls.listen, 1); assert.equal(f.events.at(-1).state, 'PAUSED')
  answer.resolve({}); await running
})
test('问答未知结果只暂停，不重复发送、不播报', async () => {
  let requests = 0
  const f = fixture({ ask: async () => { requests++; throw new Error('本轮状态尚未确认') } })
  await f.controller.start()
  assert.equal(requests, 1); assert.equal(f.calls.listen, 1); assert.equal(f.calls.play, 0)
  assert.equal(f.events.at(-1).state, 'PAUSED')
})
test('播报失败、无语音和业务参数摘要均暂停，不自动执行业务', async () => {
  for (const overrides of [
    { play: async () => false },
    { listen: async () => ({ hint: '没有完整语句' }) },
    { ask: async () => ({ status: 'COMPLETED', answer: '请核对参数', intent: { state: 'READY' } }) }
  ]) {
    const f = fixture(overrides); await f.controller.start()
    assert.equal(f.events.at(-1).state, 'PAUSED'); assert.ok(f.calls.listen <= 1)
  }
})
test('时限到达立即取消，迟到结果不能恢复循环', async () => {
  const input = deferred(), f = fixture({ listen: () => input.promise })
  const running = f.controller.start(); f.expire(); await tick()
  assert.equal(f.events.at(-1).state, 'PAUSED'); assert.equal(f.calls.cancel, 1)
  input.resolve({ text: '超时后返回' }); await running; assert.equal(f.calls.ask, 0)
})
test('打断核对期间被结束，不会迟到重新开启', async () => {
  const answer = deferred(), confirmation = deferred()
  const f = fixture({ ask: () => answer.promise, interrupt: () => confirmation.promise })
  const running = f.controller.start(); await tick()
  const interrupting = f.controller.interrupt(); await tick()
  await f.controller.stop(); confirmation.resolve(true); await interrupting
  answer.resolve({}); await running
  assert.equal(f.calls.listen, 1); assert.equal(f.events.at(-1).state, 'OFF')
})
test('分句在不同采样率和分片下识别语音后的停顿，只返回一次结束事件', () => {
  for (const rate of [8000, 16000, 44100, 48000]) {
    const detector = createCustomerSpeechActivity(), samples = new Float32Array(rate * 4)
    samples.fill(.1, 0, Math.round(rate * .4))
    const results = []
    for (let offset = 0; offset < samples.length; offset += 128) {
      const value = detector.push(samples.slice(offset, offset + 128), rate)
      if (value) results.push(value)
    }
    assert.deepEqual(results, ['END'])
  }
})
test('起录短噪声后稍晚开始说话，不会在首个人声停顿前截断整句', () => {
  for (const rate of [16000, 44100, 48000]) {
    const detector = createCustomerSpeechActivity(), samples = new Float32Array(rate * 8)
    samples.fill(.1, 0, Math.round(rate * .4))
    samples.fill(.1, Math.round(rate * 2.4), Math.round(rate * 5.5))
    const ends = []
    for (let offset = 0; offset < samples.length; offset += 128) {
      if (detector.push(samples.slice(offset, offset + 128), rate) === 'END') ends.push((offset + 128) / rate)
    }
    assert.equal(ends.length, 1)
    assert.ok(ends[0] >= 7 && ends[0] < 7.05, `unexpected end ${ends[0]}`)
  }
})
test('起录保护不丢弃立即说出的短句，满三秒后结束且只返回一次', () => {
  const detector = createCustomerSpeechActivity()
  const short = new Float32Array(16000 * 2.9); short.fill(.1, 0, 6400)
  assert.equal(detector.push(short, 16000), null)
  assert.equal(detector.push(new Float32Array(3200), 16000), 'END')
  assert.equal(detector.push(new Float32Array(16000), 16000), null)
})
test('正常长句的短暂思考停顿不分句，最终停顿后及时结束', () => {
  const detector = createCustomerSpeechActivity(), rate = 16000
  assert.equal(detector.push(new Float32Array(rate * 3).fill(.1), rate), null)
  assert.equal(detector.push(new Float32Array(rate * 1.2), rate), null)
  assert.equal(detector.push(new Float32Array(rate).fill(.1), rate), null)
  assert.equal(detector.push(new Float32Array(rate * 1.6), rate), 'END')
})
test('静音及间歇短噪音不会拼成完整语句，等待超时返回静音', () => {
  for (const noise of [false, true]) {
    const detector = createCustomerSpeechActivity(), samples = new Float32Array(16000 * 9)
    if (noise) for (let offset = 0; offset < samples.length; offset += 16000) samples.fill(.1, offset, offset + 1600)
    assert.equal(detector.push(samples, 16000), 'SILENCE')
  }
})

test('补充参数的追问继续倾听，参数收齐后暂停，始终不执行操作', async () => {
  let asked = 0
  const f = fixture({ ask: async () => ({ status: 'COMPLETED', answer: '请核对', intent: { state: ++asked === 1 ? 'COLLECTING' : 'READY' } }) })
  await f.controller.start()
  assert.equal(asked, 2); assert.equal(f.calls.listen, 2); assert.equal(f.events.at(-1).state, 'PAUSED')
  assert.match(f.events.at(-1).hint, /参数已收齐/)
})
