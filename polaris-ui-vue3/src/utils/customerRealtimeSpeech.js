// 按整个输入采样时间轴重采样，跨 AudioWorklet 消息保留插值边界，不逐包累积舍入误差。
export function createCustomerPcmFramer(sampleRate, onFrame) {
  if (!Number.isFinite(sampleRate) || sampleRate < 8000 || sampleRate > 192000) throw new Error('INVALID_SAMPLE_RATE')
  let received = 0, output = 0, previous = 0, used = 0, closed = false
  let tail = new Float32Array(0)
  let frame = new ArrayBuffer(6400), view = new DataView(frame)
  function sample(value) {
    const bounded = Math.max(-1, Math.min(1, Number.isFinite(value) ? value : 0))
    view.setInt16(used, Math.round(bounded * (bounded < 0 ? 32768 : 32767)), true); used += 2; output++
    if (used === 6400) { onFrame(frame); frame = new ArrayBuffer(6400); view = new DataView(frame); used = 0 }
  }
  return {
    push(input) {
      if (closed || !(input instanceof Float32Array)) throw new Error('INVALID_PCM_STATE')
      const start = received - tail.length, end = received + input.length
      const samples = new Float32Array(tail.length + input.length)
      samples.set(tail); samples.set(input, tail.length)
      while (output < Math.min(320000, Math.floor(end * 16000 / sampleRate))) {
        const position = output * sampleRate / 16000, left = Math.floor(position), fraction = position - left
        if (left >= end || fraction > 0 && left + 1 >= end) break
        const a = samples[left - start]
        const b = fraction ? samples[left + 1 - start] : a
        sample(a * (1 - fraction) + b * fraction)
      }
      received = end; if (input.length) previous = input.at(-1)
      tail = samples.slice(Math.max(0, Math.floor(output * sampleRate / 16000) - start))
    },
    finish() {
      if (closed) return
      closed = true
      const expected = Math.min(320000, Math.floor(received * 16000 / sampleRate))
      while (output < expected) sample(previous)
      if (used) onFrame(frame.slice(0, used))
      frame = null; view = null; tail = new Float32Array(0)
    }
  }
}

export function customerRealtimeUrl(baseApi, location) {
  const target = new URL(`${String(baseApi || '').replace(/\/$/, '')}/platform/runtime/customer-speech-stream`, location.origin)
  if (target.origin !== location.origin || target.search || target.hash) throw new Error('REALTIME_REQUIRES_SAME_ORIGIN')
  target.protocol = target.protocol === 'https:' ? 'wss:' : 'ws:'
  return target.href
}

// 只在 INIT 尚未发送时允许 HTTP 自动降级；已受理后断线不重放录音。
export function createCustomerRealtimeSpeech({ WebSocket, url, onPartial, onFailure }) {
  let socket, timer, pumpTimer, resolveReady, rejectReady, resolveResult, rejectResult
  let submitted = false, ready = false, terminal = false, cancelled = false, finishing = false, finishRequested = false, sequence = 0, queued = 0
  const frames = []
  const result = new Promise((resolve, reject) => { resolveResult = resolve; rejectResult = reject })
  result.catch(() => {})
  function failure() {
    if (terminal) return
    terminal = true; clearTimeout(timer); clearTimeout(pumpTimer); frames.length = 0; queued = 0
    const error = Object.assign(new Error('REALTIME_INTERRUPTED'), { fallbackAllowed: !submitted })
    rejectReady?.(error); rejectResult(error)
    if (socket) { socket.onopen = socket.onmessage = socket.onerror = socket.onclose = null; socket.close() }
    onFailure?.(error)
  }
  function pump() {
    clearTimeout(pumpTimer)
    if (!ready || terminal) return
    try {
      while (frames.length && socket.bufferedAmount <= 65536) {
        const frame = frames.shift(); queued -= frame.byteLength
        const packet = new Uint8Array(4 + frame.byteLength)
        new DataView(packet.buffer).setUint32(0, sequence++, false); packet.set(new Uint8Array(frame), 4)
        socket.send(packet.buffer)
      }
      if (frames.length) pumpTimer = setTimeout(pump, 10)
      else if (finishing) { finishing = false; socket.send(JSON.stringify({ type: 'FINISH', sequence })) }
    } catch { failure() }
  }
  return {
    get submitted() { return submitted },
    audio(frame) {
      if (terminal) return
      if (finishRequested) return failure()
      if (!(frame instanceof ArrayBuffer) || !frame.byteLength || frame.byteLength > 6400 || frame.byteLength % 2 || queued + frame.byteLength > 640000) return failure()
      frames.push(frame); queued += frame.byteLength; pump()
    },
    connect(ticket) {
      return new Promise((resolve, reject) => {
        resolveReady = resolve; rejectReady = reject
        if (terminal) return reject(Object.assign(new Error('REALTIME_CLOSED'), { fallbackAllowed: !cancelled && !submitted }))
        try {
          socket = new WebSocket(url)
          timer = setTimeout(failure, 2500)
          socket.onopen = () => {
            try {
              clearTimeout(timer); timer = setTimeout(failure, 10000)
              submitted = true; socket.send(JSON.stringify({ type: 'INIT', ticket }))
            } catch { failure() }
          }
          socket.onerror = socket.onclose = failure
          socket.onmessage = event => {
            try {
              if (terminal || typeof event.data !== 'string' || event.data.length > 16000) return failure()
              const value = JSON.parse(event.data)
              if (value.type === 'READY' && !ready) { ready = true; clearTimeout(timer); resolve(true); pump() }
              else if (value.type === 'PARTIAL' && ready && typeof value.text === 'string' && value.text.length <= 2000) onPartial?.(value.text)
              else if (value.type === 'COMPLETE' && ready && value.result) {
                terminal = true; clearTimeout(timer); clearTimeout(pumpTimer); frames.length = 0
                socket.onclose = socket.onerror = null; socket.close(); resolveResult(value.result)
              } else failure()
            } catch { failure() }
          }
        } catch { failure() }
      })
    },
    finish() { if (terminal || finishRequested) return result; finishRequested = true; finishing = true; timer = setTimeout(failure, 20000); pump(); return result },
    cancel() {
      terminal = true; cancelled = true; clearTimeout(timer); clearTimeout(pumpTimer); frames.length = 0; queued = 0
      if (socket) { socket.onopen = socket.onmessage = socket.onerror = socket.onclose = null; socket.close() }
      const error = Object.assign(new Error('REALTIME_CANCELLED'), { fallbackAllowed: false })
      rejectReady?.(error); rejectResult(error)
    }
  }
}
