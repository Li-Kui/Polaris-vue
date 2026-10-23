import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import {useCapabilityTests} from '../src/components/model-center/useCapabilityTests.js'
import * as voiceState from '../src/utils/customerVoiceState.js'
import {createCustomerSpeechActivity} from '../src/utils/customerSpeechActivity.js'
import * as realtimeState from '../src/utils/customerRealtimeSpeech.js'

async function load(path, modules, globals = {}) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const script = compileScript(parse(source).descriptor, { id: 'customer-voice-regression' }).content
  const compiled = await transformWithEsbuild(script, 'fixture.js', { format: 'cjs', target: 'esnext', define: { 'import.meta.url': '"file:///fixture.vue"', 'import.meta.env': '{"VITE_APP_BASE_API":"/dev-api"}' } })
  const module = { exports: {} }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, ...globals, require: name => {
    assert.ok(name in modules, `未声明的依赖 ${name}`); return modules[name]
  } })
  return module.exports.default
}
const noop = () => {}
const element = { ElMessage: Object.assign(noop, { error: noop, warning: noop, success: noop }), ElMessageBox: {} }
const modelApi = {}
const editor = await load('../src/components/model-center/ModelEditor.vue', {
  vue: { ...Vue, onMounted: noop }, 'element-plus': element,
  './CapabilitySelector.vue': {}, './CapabilityCard.vue': {}, './RuntimePolicyEditor.vue': {},
  './useCapabilityTests': { useCapabilityTests }, '@/api/ai/model': modelApi, '@/api/ai/providerConnection': {}
})
const schema = code => ({ code, schemaVersion: 1, kind: 'INVOCATION', name: code, schema: { properties: {} }, uiSchema: {} })
test('首次开启识别能力立即更新已选能力和参数面板依赖', async () => {
  const scope = Vue.effectScope()
  const state = scope.run(() => editor.setup({ modelId: null }, { expose: noop, emit: noop }))
  let selected
  scope.run(() => Vue.watchSyncEffect(() => { selected = [...state.enabledCapabilityKeys.value] }))
  await state.toggleCapability(schema('AUDIO_STT'), '', true)
  assert.deepEqual(selected, ['AUDIO_STT']); assert.equal(state.enabledCapabilities.value.length, 1)
  await state.toggleCapability(schema('AUDIO_STT'), '', false)
  assert.deepEqual(selected, []); scope.stop()
})
test('首次启用合成能力保留其他手动能力，不出现隐藏启用状态', async () => {
  const scope = Vue.effectScope()
  const state = scope.run(() => editor.setup({ modelId: null }, { expose: noop, emit: noop }))
  let selected
  scope.run(() => Vue.watchSyncEffect(() => { selected = [...state.enabledCapabilityKeys.value] }))
  await state.toggleCapability(schema('AUDIO_STT'), '', true)
  await state.toggleCapability(schema('AUDIO_TTS'), '', true)
  assert.deepEqual(selected, ['AUDIO_STT', 'AUDIO_TTS']); scope.stop()
})
test('供应商参数晚到响应不能覆盖已切换的连接和模型', async () => {
  const scope = Vue.effectScope()
  const state = scope.run(() => editor.setup({ modelId: null }, { expose: noop, emit: noop }))
  const pending = []
  modelApi.getModelSchemaContext = () => new Promise(resolve => pending.push(resolve))
  state.state.connectionId = 11; state.state.modelName = 'qwen3-tts-flash'
  const first = state.refreshSchemas()
  state.state.connectionId = 12; state.state.modelName = 'seed-tts-2.0'
  const second = state.refreshSchemas()
  pending[1]({ data: [schema('AUDIO_TTS')] }); await second
  pending[0]({ data: [schema('AUDIO_STT')] }); assert.equal(await first, false)
  assert.equal(state.schemas.value[0].code, 'AUDIO_TTS'); scope.stop()
})
test('音色配置同参数并发加载只请求一次，切换模型后不接收旧选项', async () => {
  const scope = Vue.effectScope()
  const state = scope.run(() => editor.setup({ modelId: null }, { expose: noop, emit: noop }))
  let resolve, calls = 0
  modelApi.resolveSchemaOptions = () => { calls++; return new Promise(accept => { resolve = accept }) }
  state.state.connectionId = 11; state.state.modelName = 'qwen3-tts-flash'
  const item = { capabilityCode: 'AUDIO_TTS', options: {}, schema: { uiSchema: { voice: { optionsResolver: 'PROVIDER_VOICES' } } } }
  const first = state.loadDynamicOptions(item), second = state.loadDynamicOptions(item)
  assert.equal(calls, 1)
  state.state.modelName = 'other-model'; resolve({ data: [{ value: 'Cherry' }] }); await Promise.all([first, second])
  assert.equal(item.options.voice, undefined); scope.stop()
})
async function speakerFixture(options = {}) {
  let mounted, unmount, exposed, current, cancelled = 0, calls = 0
  class Utterance { constructor(text) { this.text = text } }
  const synthesis = { getVoices: () => [{ voiceURI: 'local', name: 'local', lang: 'zh-CN', localService: true }],
    addEventListener: noop, removeEventListener: noop, cancel: () => { cancelled++ }, speak: value => { current = value; calls++ } }
  const component = await load('../src/views/platform/customerService/CustomerVoicePanel.vue', {
    vue: { ...Vue, onMounted: fn => { mounted = fn }, onBeforeUnmount: fn => { unmount = fn } },
    '@/utils/customerSpeechActivity': { createCustomerSpeechActivity },
    '@/utils/customerRecorder': { createCustomerRecorder: () => options.recorder || ({ cancel: async () => {}, stop: async () => {} }) },
    '@/utils/customerVoiceState': voiceState,
    '@/utils/customerRealtimeSpeech': realtimeState,
    '@/api/platform/customerService': { getCustomerSpeechSettings: async () => ({ data: { recognitionAvailable: !!options.recorder, devicePlayback: true, providerPlayback: false } }), ...options.api }
  }, { window: { isSecureContext: true, speechSynthesis: synthesis, SpeechSynthesisUtterance: Utterance, AudioContext: class {}, AudioWorkletNode: class {}, location: { origin: 'http://localhost' }, WebSocket: options.WebSocket }, navigator: { mediaDevices: { getUserMedia: noop } }, SpeechSynthesisUtterance: Utterance,
    URL, Blob, Audio: options.Audio, clearInterval, setInterval, crypto: { randomUUID: () => 'test-id' } })
  const scope = Vue.effectScope()
  const state = scope.run(() => component.setup({ sessionId: 'session', active: true, currentText: '' }, { expose: value => { exposed = value }, emit: noop }))
  await mounted()
  return { state, exposed, current: () => current, calls: () => calls, cancelled: () => cancelled, dispose: () => { unmount(); scope.stop() } }
}
test('设备播报被浏览器拒绝时显示原因及显式重试，不静默失败或自动重试', async () => {
  const f = await speakerFixture()
  await f.exposed.play('已核验的短回答', 'turn')
  f.current().onerror({ error: 'not-allowed' })
  assert.equal(f.state.speaking.value, false); assert.match(f.state.error.value, /浏览器暂未允许/)
  assert.equal(f.state.readyLocal.value, '已核验的短回答'); assert.equal(f.calls(), 1)
  f.state.retryLocal(); assert.equal(f.calls(), 2); f.dispose()
})
test('停止设备播报清理重试正文并分离旧回调，迟到错误不能恢复播报', async () => {
  const f = await speakerFixture()
  await f.exposed.play('已核验的短回答', 'turn')
  const previous = f.current(), failure = previous.onerror
  f.exposed.stopPlayback(); failure({ error: 'not-allowed' })
  assert.equal(previous.onerror, null); assert.equal(previous.onend, null)
  assert.equal(f.state.readyLocal.value, ''); assert.equal(f.state.speaking.value, false)
  assert.equal(f.cancelled(), 1); f.dispose()
})
function recorderFixture() {
  return { start: async callback => { callback?.(new Float32Array(16000), 16000); return true },
    stop: async () => voiceState.customerWav(new Float32Array(16000), 16000), cancel: async () => {} }
}
test('不支持WebSocket时保留完整录音，仅调用一次HTTP识别并生成草稿', async () => {
  let uploads = 0
  const f = await speakerFixture({ recorder: recorderFixture(), api: {
    transcribeCustomerSpeech: async (_, id, wav) => { uploads++; assert.equal(wav.byteLength, 32044); return { data: { requestId: id, status: 'COMPLETED', text: 'HTTP草稿' } } }
  } })
  await f.state.record(); await f.state.finishRecording()
  assert.equal(uploads, 1); assert.equal(f.state.draft.value, 'HTTP草稿'); assert.equal(f.state.state.value, 'IDLE'); f.dispose()
})
test('模型不支持实时接口时自动HTTP降级，始终不建立WebSocket', async () => {
  let uploads = 0, tickets = 0
  const f = await speakerFixture({ recorder: recorderFixture(), WebSocket: class { constructor() { assert.fail('不得建连') } }, api: {
    getCustomerRealtimeTicket: async () => { tickets++; return { data: { available: false } } },
    transcribeCustomerSpeech: async () => { uploads++; return { data: { status: 'COMPLETED', text: '回退草稿' } } }
  } })
  await f.state.record(); await f.state.finishRecording()
  assert.equal(tickets, 1); assert.equal(uploads, 1); assert.equal(f.state.draft.value, '回退草稿')
  assert.equal(f.state.recognitionMode.value, 'HTTP'); assert.match(f.state.realtimeNotice.value, /已切换短录音/)
  f.state.preferRealtime.value = false
  await f.state.record(); await f.state.finishRecording()
  assert.equal(tickets, 1); assert.equal(uploads, 2); assert.equal(f.state.realtimeNotice.value, ''); f.dispose()
})
test('流式已经INIT后断线保留核对入口，停止或重试不改走HTTP重发', async () => {
  let socket, uploads = 0, checks = 0
  class Socket { bufferedAmount = 0; constructor() { socket = this } send() {} close() {} }
  const f = await speakerFixture({ recorder: recorderFixture(), WebSocket: Socket, api: {
    getCustomerRealtimeTicket: async () => ({ data: { available: true, ticket: 'ticket' } }),
    transcribeCustomerSpeech: async () => { uploads++; return {} },
    getCustomerSpeech: async () => { checks++; return { data: { status: 'INTERRUPTED', errorCode: 'SPEECH_INTERRUPTED' } } }
  } })
  await f.state.record(); await Vue.nextTick(); socket.onopen(); socket.onclose(); await Vue.nextTick()
  assert.equal(f.state.state.value, 'UNKNOWN'); assert.equal(f.state.pending.value.kind, 'STREAM')
  await f.state.finishRecording(); await f.state.retry()
  assert.equal(uploads, 0); assert.equal(checks, 1); f.dispose()
})

test('自动分句返回最终文字而不生成手动草稿，静音超时关闭录音且不上传HTTP', async () => {
  for (const silent of [false, true]) {
    let samples, uploads = 0, cancels = 0
    const recorder = { start: async callback => { samples = callback; return true }, stop: async () => voiceState.customerWav(new Float32Array(32000), 16000), cancel: async () => { cancels++ } }
    const f = await speakerFixture({ recorder, api: { transcribeCustomerSpeech: async () => { uploads++; return { data: { status: 'COMPLETED', text: '连续语音最终文字' } } } } })
    const input = f.exposed.listen(); await new Promise(resolve => setImmediate(resolve))
    if (!silent) {
      samples(new Float32Array(6400).fill(.1), 16000)
      samples(new Float32Array(19200), 16000)
      await Vue.nextTick(); assert.equal(uploads, 0) // 起录保护期内不会提前提交。
      samples(new Float32Array(28800), 16000)
    }
    else samples(new Float32Array(144000), 16000)
    const result = await input
    assert.equal(result.text, silent ? '' : '连续语音最终文字'); assert.equal(uploads, silent ? 0 : 1)
    assert.equal(f.state.draft.value, ''); if (silent) assert.ok(cancels > 0)
    f.dispose()
  }
})
test('连续语音等待实际播报结束，打断或浏览器拒绝均返回未完成', async () => {
  for (const outcome of ['end', 'stop', 'error']) {
    const f = await speakerFixture()
    let settled = false
    const playing = f.exposed.playAndWait('合成短回答', 'turn').then(value => { settled = true; return value })
    await Vue.nextTick(); assert.equal(settled, false)
    if (outcome === 'end') f.current().onend()
    else if (outcome === 'stop') f.exposed.stopPlayback()
    else f.current().onerror({ error: 'not-allowed' })
    assert.equal(await playing, outcome === 'end'); f.dispose()
  }
})
test('自动识别取消后迟到数据与结果不能变成提问，未核对草稿不能被连续语音覆盖', async () => {
  let samples, uploads = 0
  const f = await speakerFixture({ recorder: { start: async callback => { samples = callback; return true }, cancel: async () => {} }, api: { transcribeCustomerSpeech: async () => { uploads++; return {} } } })
  const input = f.exposed.listen(); await new Promise(resolve => setImmediate(resolve))
  f.state.realtimeNotice.value = '正在传输'; await f.exposed.cancel()
  assert.equal(f.state.realtimeNotice.value, '')
  samples(new Float32Array(6400).fill(.1), 16000); samples(new Float32Array(19200), 16000)
  assert.equal((await input).text, ''); assert.equal(uploads, 0)
  f.state.draft.value = '未确认的原草稿'
  assert.equal((await f.exposed.listen()).text, undefined); assert.equal(f.state.draft.value, '未确认的原草稿'); f.dispose()
})

test('供应商播报必须等待音频实际结束，合成完成不代表播报完成', async () => {
  const audio = []
  class Audio { constructor() { audio.push(this) } async play() {} pause() {} removeAttribute() {} load() {} }
  const f = await speakerFixture({ Audio, api: {
    getCustomerSpeechSettings: async () => ({ data: { recognitionAvailable: true, providerPlayback: true, devicePlayback: false } }),
    synthesizeCustomerSpeech: async () => ({ data: { status: 'COMPLETED' } }),
    getCustomerSpeechAudio: async () => new Blob([new Uint8Array(8)], { type: 'audio/mpeg' })
  } })
  let done = false
  const playing = f.exposed.playAndWait('合成问题的回答', 'turn').then(value => { done = true; return value })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(audio.length, 1); assert.equal(done, false); assert.equal(f.state.speaking.value, true)
  audio[0].onended(); assert.equal(await playing, true); f.dispose()
})
test('合成过程中打断，迟到供应商结果不创建播放器、不恢复播报', async () => {
  let complete, players = 0
  const f = await speakerFixture({ Audio: class { constructor() { players++ } }, api: {
    getCustomerSpeechSettings: async () => ({ data: { recognitionAvailable: true, providerPlayback: true, devicePlayback: false } }),
    synthesizeCustomerSpeech: () => new Promise(resolve => { complete = resolve }), cancelCustomerSpeech: async () => ({}),
    getCustomerSpeechAudio: () => assert.fail('取消后不获取迟到音频')
  } })
  const playing = f.exposed.playAndWait('合成问题的回答', 'turn')
  await f.exposed.cancel(); assert.equal(await playing, false)
  complete({ data: { status: 'COMPLETED' } }); await new Promise(resolve => setImmediate(resolve))
  assert.equal(players, 0); assert.equal(f.state.speaking.value, false); f.dispose()
})

test('旧播放器排队中的结束事件不能结束新播报或提前打开下一轮倾听', async () => {
  const audio = []
  class Audio { constructor() { audio.push(this) } async play() {} pause() {} removeAttribute() {} load() {} }
  const f = await speakerFixture({ Audio, api: {
    getCustomerSpeechSettings: async () => ({ data: { recognitionAvailable: true, providerPlayback: true, devicePlayback: false } }),
    synthesizeCustomerSpeech: async () => ({ data: { status: 'COMPLETED' } }),
    getCustomerSpeechAudio: async () => new Blob([new Uint8Array(8)], { type: 'audio/mpeg' })
  } })
  const first = f.exposed.playAndWait('第一轮回答', 'first'); await new Promise(resolve => setImmediate(resolve))
  const queuedEnd = audio[0].onended
  f.exposed.stopPlayback(); assert.equal(await first, false)
  let completed = false
  const second = f.exposed.playAndWait('第二轮回答', 'second').then(value => { completed = true; return value })
  await new Promise(resolve => setImmediate(resolve)); queuedEnd(); await Vue.nextTick()
  assert.equal(completed, false); assert.equal(f.state.speaking.value, true)
  audio[1].onended(); assert.equal(await second, true); f.dispose()
})
