import {customerWav} from './customerVoiceState'

// 注入浏览器依赖以验证迟到授权和销毁释放，不使用远程浏览器语音识别服务。
export function createCustomerRecorder({ mediaDevices, AudioContext, AudioWorkletNode, processorUrl, onLimit }) {
  let generation = 0, stream, context, source, node, gain, chunks = [], count = 0, rate = 0, timer
  const tracksStop = value => value?.getTracks().forEach(track => track.stop())
  async function release() {
    clearTimeout(timer)
    if (node) { node.port.onmessage = null; node.disconnect(); node = null }
    source?.disconnect(); gain?.disconnect(); source = null; gain = null
    tracksStop(stream); stream = null
    const closing = context; context = null
    if (closing && closing.state !== 'closed') await closing.close().catch(() => {})
  }
  async function cancel() { generation++; chunks = []; count = 0; await release() }
  async function start(onSamples) {
    const ticket = ++generation
    chunks = []; count = 0
    await release()
    if (ticket !== generation) return false
    if (!mediaDevices?.getUserMedia || !AudioContext || !AudioWorkletNode) throw new Error('当前浏览器不支持录音，请使用文字接待')
    let opened
    try {
      opened = await mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }, video: false })
      if (ticket !== generation) { tracksStop(opened); return false }
      stream = opened
      context = new AudioContext()
      const current = context
      await current.audioWorklet.addModule(processorUrl)
      if (ticket !== generation) return false
      await current.resume()
      if (ticket !== generation) return false
      rate = current.sampleRate
      source = current.createMediaStreamSource(opened)
      node = new AudioWorkletNode(current, 'customer-recorder')
      gain = current.createGain(); gain.gain.value = 0
      node.port.onmessage = event => {
        if (ticket !== generation) return
        const remaining = Math.floor(rate * 20) - count
        if (remaining <= 0) return
        const copy = new Float32Array(event.data).slice(0, remaining)
        chunks.push(copy); count += copy.length
        onSamples?.(copy, rate)
      }
      source.connect(node); node.connect(gain); gain.connect(current.destination)
      timer = setTimeout(() => { if (ticket === generation) onLimit?.() }, 20000)
      return true
    } catch (error) {
      tracksStop(opened)
      if (ticket === generation) await cancel()
      throw error
    }
  }
  async function stop() {
    generation++
    const saved = chunks, length = count, sampleRate = rate
    chunks = []; count = 0
    await release()
    const samples = new Float32Array(length)
    let offset = 0
    for (const chunk of saved) { samples.set(chunk, offset); offset += chunk.length }
    return customerWav(samples, sampleRate)
  }
  return { start, stop, cancel }
}
