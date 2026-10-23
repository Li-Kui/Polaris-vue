import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import * as voiceState from '../src/utils/customerVoiceState.js'
import * as realtime from '../src/utils/customerRealtimeSpeech.js'
import * as activity from '../src/utils/customerSpeechActivity.js'

const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
const source = await readFile(new URL('../src/views/platform/customerService/CustomerVoicePanel.vue', import.meta.url), 'utf8')
const compiled = await transformWithEsbuild(compileScript(parse(source).descriptor, { id: 'answer-voice' }).content, 'voice.js', { format: 'cjs', target: 'esnext', define: { 'import.meta.env.VITE_APP_BASE_API': '"/dev-api"' } })
const recorderCode = await transformWithEsbuild(await readFile(new URL('../src/utils/customerRecorder.js', import.meta.url), 'utf8'), 'recorder.js', { format: 'cjs', target: 'esnext' })
const recorderModule = { exports: {} }
vm.runInNewContext(recorderCode.code, { module: recorderModule, exports: recorderModule.exports, require: () => voiceState, Float32Array, setTimeout, clearTimeout })
function fixture({ synthesis, audio, transcription, visitor = false, limited = false, nativeRecorder = false, streaming = false, permission, worklet, ticket } = {}) {
  const calls = [], audios = [], removed = [], recognitions = [], emitted = [], cancellations = [], nodes = [], sockets = [], contexts = []
  let unmount, starts = 0, tracksStopped = 0
  const stream = { getTracks: () => [{ stop: () => { tracksStopped++ } }] }
  class Context {
    state = 'running'; sampleRate = 48000; destination = {}
    audioWorklet = { addModule: async () => { if (worklet) await worklet() } }
    constructor() { contexts.push(this) }
    async resume() {}
    async close() { this.state = 'closed' }
    createMediaStreamSource() { return { connect() {}, disconnect() {} } }
    createGain() { return { gain: { value: 1 }, connect() {}, disconnect() {} } }
  }
  class WorkletNode {
    port = {}
    constructor() { nodes.push(this) }
    connect() {}
    disconnect() { this.disconnected = true }
  }
  class Socket {
    bufferedAmount = 0; sent = []
    constructor() { sockets.push(this) }
    send(value) { this.sent.push(value) }
    close() { this.closed = true }
    event(value) { this.onmessage?.({ data: JSON.stringify(value) }) }
  }
  class Audio {
    constructor(url) { this.src = url; audios.push(this) }
    async play() { this.played = true }
    pause() { this.paused = true }
    removeAttribute() {} load() {}
  }
  class TestURL extends URL {
    constructor(path, base) { super(path, base || 'https://fixture.invalid/') }
    static createObjectURL() { return 'blob:fixture' }
    static revokeObjectURL(value) { removed.push(value) }
  }
  const modules = {
    vue: { ...Vue, onMounted() {}, onBeforeUnmount: fn => { unmount = fn } },
    '@/utils/customerVoiceState': voiceState,
    '@/utils/customerSpeechActivity': activity, '@/utils/customerRealtimeSpeech': realtime,
    '@/utils/customerRecorder': nativeRecorder ? recorderModule.exports : { createCustomerRecorder: () => ({ start: async () => { starts++; return true }, stop: async () => new ArrayBuffer(6444), cancel: async () => {} }) },
    '@/api/platform/customerService': {
      getCustomerSpeechSettings: async () => ({data:{recognitionAvailable:true,providerPlayback:true,devicePlayback:false}}),
      getCustomerRealtimeTicket: async () => { if (ticket) await ticket(); return { data: { available: true, ticket: 'fixture-ticket' } } },
      transcribeCustomerSpeech: async (...args) => { recognitions.push(args); if(limited)throw new Error('RATE_LIMITED: 本次请求已达上限'); if (transcription) await transcription(); return {data:{status:'COMPLETED',text:'待核对的访客识别'}} },
      synthesizeCustomerSpeech: async (...args) => { calls.push(args); if (synthesis) await synthesis(); return { data: { status: 'COMPLETED' } } },
      getCustomerSpeechAudio: async () => { if (audio) await audio(); return new Blob(['audio'], { type: 'audio/mpeg' }) },
      cancelCustomerSpeech: async (...args) => { cancellations.push(args) }
    }
  }
  let speechClient
  if (visitor) {
    const api=modules['@/api/platform/customerService'], original={...api}
    speechClient={speechSettings:original.getCustomerSpeechSettings,realtimeTicket:original.getCustomerRealtimeTicket,transcribe:original.transcribeCustomerSpeech,synthesize:original.synthesizeCustomerSpeech,speechAudio:original.getCustomerSpeechAudio,speechCancel:original.cancelCustomerSpeech}
    for(const name of Object.keys(api))api[name]=()=>assert.fail(`访客语音不得调用管理员接口 ${name}`)
  }
  const module = { exports: {} }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, require: name => { assert.ok(name in modules, name); return modules[name] },
    window: { isSecureContext: true, AudioContext: Context, AudioWorkletNode: WorkletNode, ...(streaming ? { WebSocket: Socket } : {}), location: { origin: 'https://fixture.invalid' }, speechSynthesis: { getVoices: () => [], cancel() {}, removeEventListener() {} } },
    navigator: {mediaDevices:{getUserMedia: async () => { starts++; if (permission) await permission(); return stream }}},
    URL: TestURL, Audio, Blob, Float32Array, crypto: globalThis.crypto, setTimeout, clearTimeout, setInterval, clearInterval })
  const props = Vue.reactive({ sessionId: 'session', active: false, answerPlayback: true, continuous: true, currentText: '', speechClient })
  const scope = Vue.effectScope(), state = scope.run(() => module.exports.default.setup(props, { emit: (...args) => emitted.push(args), expose() {} }))
  state.settings.value = { providerPlayback: true, recognitionAvailable: true, devicePlayback: false }
  return { state, props, calls, audios, removed, recognitions, emitted, cancellations, nodes, contexts, sockets, tracksStopped: () => tracksStopped,
    starts: () => starts, async dispose() { unmount(); scope.stop(); await tick() } }
}
test('生成期间active为false时仅允许已校验分段播报，合成只提交轮次与序号，等实际音频结束才完成', async () => {
  const f = fixture(), result = f.state.playAndWait('首句[S1]。', 'turn', 1)
  await tick(); assert.equal(f.calls.length, 1); assert.equal(f.calls[0][2], 'turn'); assert.equal(f.calls[0][3], 1)
  assert.equal(f.audios.length, 1); assert.equal(f.state.speaking.value, true)
  let finished = false; result.then(() => { finished = true }); await tick(); assert.equal(finished, false)
  f.audios[0].onended(); assert.equal(await result, true)
  assert.equal(await f.state.playAndWait('任意完整回答', 'turn'), false); assert.equal(f.calls.length, 1)
  await f.dispose()
})
test('取消正在合成的分段后，迟到结果不能拉取或播放音频', async () => {
  const synthesis = deferred(), f = fixture({ synthesis: () => synthesis.promise })
  const result = f.state.playAndWait('首句', 'turn', 1); await tick()
  await f.state.cancel(); assert.equal(await result, false)
  synthesis.resolve(); await tick(); assert.equal(f.audios.length, 0); assert.equal(f.state.speaking.value, false)
  await f.dispose()
})
test('失去播放资格时停止并撤销资源，迟到音频下载不能恢复播放', async () => {
  const audio = deferred(), f = fixture({ audio: () => audio.promise })
  const result = f.state.playAndWait('首句', 'turn', 1); await tick()
  f.props.answerPlayback = false; await Vue.nextTick(); assert.equal(await result, false)
  audio.resolve(); await tick(); assert.equal(f.audios.length, 0)
  await f.dispose()
})

test('访客短录音走专属客户端，识别仅生成草稿，达到问答上限后不再索取麦克风', async () => {
  const f=fixture({visitor:true}); f.props.active=true; f.state.preferRealtime.value=false
  await f.state.record(); assert.equal(f.starts(),1); await f.state.finishRecording()
  assert.equal(f.recognitions.length,1); assert.equal(f.state.draft.value,'待核对的访客识别')
  assert.equal(f.emitted.some(([event])=>event==='transcript'),false)
  f.state.useDraft(); assert.equal(f.emitted.find(([event])=>event==='transcript')[1],'待核对的访客识别')
  f.props.recognitionEnabled=false; await tick(); await f.state.record(); assert.equal(f.starts(),1)
  const playing=f.state.playAndWait('已授权回答','turn'); await tick()
  assert.equal(f.calls.length,1); f.audios[0].onended(); assert.equal(await playing,true)
  await f.dispose()
})

test('访客语音明确被限额拒绝时恢复文字输入，不进入未知受理或自动重试', async () => {
  const f=fixture({visitor:true,limited:true}); f.props.active=true; f.state.preferRealtime.value=false
  await f.state.record(); await f.state.finishRecording()
  assert.equal(f.recognitions.length,1); assert.equal(f.state.state.value,'IDLE'); assert.equal(f.state.pending.value,null)
  assert.match(f.state.error.value,/限额/); assert.equal(f.state.draft.value,'')
  await f.dispose()
})

test('关闭录音组件后迟到麦克风授权立即释放轨道，连续识别不会回调提问', async () => {
  const permission = deferred(), f = fixture({ visitor: true, nativeRecorder: true, permission: () => permission.promise })
  f.props.active = true; f.state.preferRealtime.value = false
  const listening = f.state.listen(); await tick(); assert.equal(f.starts(), 1)
  await f.dispose(); assert.equal((await listening).text, '')
  permission.resolve(); await tick()
  assert.equal(f.tracksStopped(), 1); assert.equal(f.contexts.length, 0)
  assert.equal(f.recognitions.length, 0); assert.equal(f.emitted.some(([name]) => name === 'transcript'), false)
})

test('关闭正在初始化Worklet的组件会关闭轨道和上下文，迟到初始化不能恢复录音', async () => {
  const worklet = deferred(), f = fixture({ visitor: true, nativeRecorder: true, worklet: () => worklet.promise })
  f.props.active = true; f.state.preferRealtime.value = false
  const listening = f.state.listen(); await tick(); assert.equal(f.contexts.length, 1)
  await f.dispose(); worklet.resolve(); await tick()
  assert.equal((await listening).text, ''); assert.equal(f.tracksStopped(), 1)
  assert.equal(f.contexts[0].state, 'closed'); assert.equal(f.nodes.length, 0); assert.equal(f.recognitions.length, 0)
})

test('真实录音器与实时客户端在组件关闭时停止轨道、节点和WebSocket，迟到字幕及结果不恢复', async () => {
  const f = fixture({ visitor: true, nativeRecorder: true, streaming: true }); f.props.active = true
  const listening = f.state.listen(); await tick()
  assert.equal(f.state.state.value, 'RECORDING'); assert.equal(f.sockets.length, 1)
  const socket = f.sockets[0]; socket.onopen(); socket.event({ type: 'READY' }); await tick()
  const samples = f.nodes[0].port.onmessage, late = socket.onmessage
  samples({ data: new Float32Array(48000).fill(.2) }); assert.ok(socket.sent.length > 1)
  await f.dispose(); assert.equal((await listening).text, '')
  const sent = socket.sent.length
  samples({ data: new Float32Array(48000).fill(.2) })
  late({ data: JSON.stringify({ type: 'PARTIAL', text: '关闭后的字幕' }) })
  late({ data: JSON.stringify({ type: 'COMPLETE', result: { status: 'COMPLETED', text: '关闭后的问题' } }) })
  await tick()
  assert.equal(f.tracksStopped(), 1); assert.equal(f.contexts[0].state, 'closed'); assert.equal(f.nodes[0].disconnected, true)
  assert.equal(f.nodes[0].port.onmessage, null); assert.equal(socket.closed, true); assert.equal(socket.onmessage, null)
  assert.equal(socket.sent.length, sent); assert.equal(f.cancellations.length, 1); assert.equal(f.recognitions.length, 0)
  assert.equal(f.state.liveCaption.value, ''); assert.equal(f.state.draft.value, ''); assert.equal(f.calls.length, 0)
})

test('实时票据迟于组件关闭时不能新建WebSocket或回退上传HTTP录音', async () => {
  const ticket = deferred(), f = fixture({ visitor: true, nativeRecorder: true, streaming: true, ticket: () => ticket.promise })
  f.props.active = true; const listening = f.state.listen(); await tick()
  assert.equal(f.state.state.value, 'RECORDING'); assert.equal(f.sockets.length, 0)
  await f.dispose(); ticket.resolve(); await tick()
  assert.equal((await listening).text, ''); assert.equal(f.tracksStopped(), 1); assert.equal(f.sockets.length, 0)
  assert.equal(f.recognitions.length, 0); assert.equal(f.calls.length, 0)
})

test('关闭正在HTTP识别的组件后迟到文字不会生成草稿、填入输入框或触发连续提问', async () => {
  const transcription = deferred(), f = fixture({ visitor: true, nativeRecorder: true, transcription: () => transcription.promise })
  f.props.active = true; f.state.preferRealtime.value = false
  const listening = f.state.listen(); await tick()
  f.nodes[0].port.onmessage({ data: new Float32Array(48000).fill(.2) })
  const finishing = f.state.finishRecording(); await tick(); assert.equal(f.recognitions.length, 1)
  await f.dispose(); assert.equal((await listening).text, '')
  transcription.resolve(); await finishing
  assert.equal(f.tracksStopped(), 1); assert.equal(f.cancellations.length, 1); assert.equal(f.calls.length, 0)
  assert.equal(f.state.draft.value, ''); assert.equal(f.emitted.some(([name]) => name === 'transcript'), false)
})

test('关闭正在播报的组件立即暂停音频并回收URL，迟到结束事件不会完成旧队列', async () => {
  const f = fixture({ visitor: true }), playing = f.state.playAndWait('已校验分段', 'turn', 1)
  await tick(); const audio = f.audios[0], lateEnd = audio.onended
  assert.equal(audio.played, true); assert.equal(f.state.speaking.value, true)
  await f.dispose(); assert.equal(await playing, false)
  assert.equal(audio.paused, true); assert.equal(audio.onended, null); assert.deepEqual(f.removed, ['blob:fixture'])
  lateEnd(); await tick(); assert.equal(f.state.speaking.value, false); assert.equal(f.calls.length, 1)
})
