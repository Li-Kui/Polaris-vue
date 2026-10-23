import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
import {transformWithEsbuild} from 'vite'

const state = await import('../src/utils/customerVoiceState.js')
const compiled = await transformWithEsbuild(await readFile(new URL('../src/utils/customerRecorder.js', import.meta.url), 'utf8'), 'customerRecorder.js', { format: 'cjs', target: 'esnext' })
function factory() {
  const module = { exports: {} }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, Float32Array, setTimeout, clearTimeout, require: () => state })
  return module.exports.createCustomerRecorder
}
function browser(media) {
  const calls = { tracks: 0, closed: 0, nodes: [], gains: [] }
  const stream = { getTracks: () => [{ stop: () => { calls.tracks++ } }] }
  class Context {
    state = 'running'; sampleRate = 48000; destination = {}; audioWorklet = { addModule: async () => {} }
    async resume() {}
    async close() { this.state = 'closed'; calls.closed++ }
    createMediaStreamSource() { return { connect() {}, disconnect() {} } }
    createGain() { const value = { gain: { value: 1 }, connect() {}, disconnect() {} }; calls.gains.push(value); return value }
  }
  class Node { port = {}; constructor() { calls.nodes.push(this) } connect() {} disconnect() {} }
  const recorder = factory()({ mediaDevices: { getUserMedia: media || (async () => stream) }, AudioContext: Context, AudioWorkletNode: Node, processorUrl: 'fixture', onLimit() {} })
  return { recorder, calls, stream }
}
test('识别输入生成规范16k单声道WAV，限制20秒且不接受过短录音', async () => {
  const wav = state.customerWav(new Float32Array(48000).fill(.5), 48000)
  const view = new DataView(wav)
  assert.equal(view.getUint32(24, true), 16000); assert.equal(view.getUint16(22, true), 1); assert.equal(view.getUint16(34, true), 16)
  assert.equal(wav.byteLength, 32044)
  assert.throws(() => state.customerWav(new Float32Array(100), 48000))
  assert.throws(() => state.customerWav(new Float32Array(48000 * 21), 48000))
})
test('仅选择设备本地中文音色，草稿填入与发送分离', () => {
  const voices = state.customerLocalVoices([{ name: 'local', lang: 'zh-CN', localService: true }, { name: 'remote', lang: 'zh-CN', localService: false }, { name: 'en', lang: 'en-US', localService: true }])
  assert.deepEqual(voices.map(v => v.name), ['local'])
  assert.equal(state.customerTranscript(' 识别草稿 ', '已有文字'), '已有文字\n识别草稿')
  assert.throws(() => state.customerTranscript('x'.repeat(2001), ''))
})
test('麦克风授权迟于取消时立即停止轨道，不创建音频上下文', async () => {
  let grant
  const f = browser(() => new Promise(resolve => { grant = resolve }))
  const starting = f.recorder.start(); await new Promise(resolve => setImmediate(resolve))
  await f.recorder.cancel(); grant(f.stream)
  assert.equal(await starting, false); assert.equal(f.calls.tracks, 1); assert.equal(f.calls.nodes.length, 0)
})
test('录音无本地回声，停止后释放轨道和上下文，迟到片段不能改变WAV', async () => {
  const f = browser(); await f.recorder.start()
  assert.equal(f.calls.gains[0].gain.value, 0)
  const message = f.calls.nodes[0].port.onmessage
  message({ data: new Float32Array(48000) })
  const wav = await f.recorder.stop()
  assert.equal(f.calls.tracks, 1); assert.equal(f.calls.closed, 1); assert.equal(wav.byteLength, 32044)
  message({ data: new Float32Array(48000) })
  await assert.rejects(f.recorder.stop()); assert.equal(f.calls.closed, 1)
})
test('初始化期间被取消不会在资源清理完成后重新索取麦克风', async () => {
  let calls = 0
  const f = browser(async () => { calls++; return f.stream })
  const start = f.recorder.start(); await f.recorder.cancel()
  assert.equal(await start, false); assert.equal(calls, 0)
})
test('取消释放资源后允许下一次显式录制', async () => {
  const f = browser(); await f.recorder.start(); await f.recorder.cancel()
  assert.equal(f.calls.tracks, 1); assert.equal(f.calls.closed, 1)
  await f.recorder.start(); await f.recorder.cancel()
  assert.equal(f.calls.tracks, 2); assert.equal(f.calls.closed, 2)
})
