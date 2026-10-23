// 只解析服务端已校验句段；连接异常后不重发，也不把未完成事件当作最终回答。
export async function readCustomerAnswerStream(response, requestId, onEvent) {
  if (!response.ok || !response.headers.get('content-type')?.toLowerCase().startsWith('application/x-ndjson') || !response.body?.getReader) {
    throw new Error('回答连接不可用，请核对原请求与登录状态。')
  }
  const reader = response.body.getReader(), decoder = new TextDecoder('utf-8', { fatal: true })
  let buffer = '', bytes = 0, turnId, sequence = 0, completed
  function line(value) {
    if (!value.trim()) return
    const event = JSON.parse(value)
    if (!event || event.requestId !== requestId || typeof event.turnId !== 'string' || !/^[a-f0-9-]{36}$/.test(event.turnId)
      || turnId && turnId !== event.turnId || completed) throw new Error('回答事件无效')
    if (event.type === 'accepted') {
      if (turnId) throw new Error('重复受理事件')
      turnId = event.turnId
    } else if (event.type === 'part') {
      if (!turnId || event.partNo !== sequence + 1 || event.partNo > 8 || typeof event.text !== 'string'
        || !event.text.trim() || event.text.length > 600) throw new Error('回答分段无效')
      sequence++
    } else if (event.type === 'completed') {
      if (!event.turn || event.turn.id !== event.turnId || event.turn.requestId !== requestId
        || !['RUNNING', 'COMPLETED', 'INTERRUPTED', 'FAILED'].includes(event.turn.status)) throw new Error('回答终态无效')
      completed = event.turn
    } else throw new Error('未知回答事件')
    onEvent(event)
  }
  try {
    while (!completed) {
      const { value, done } = await reader.read()
      if (done) {
        buffer += decoder.decode()
        if (buffer.trim()) line(buffer)
        if (!completed) throw new Error('回答连接提前结束，请核对原轮次。')
        break
      }
      bytes += value.byteLength
      if (bytes > 262144) throw new Error('回答事件过大')
      buffer += decoder.decode(value, { stream: true })
      if (buffer.length > 65536) throw new Error('回答事件过长')
      let newline
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const next = buffer.slice(0, newline); buffer = buffer.slice(newline + 1); line(next)
      }
    }
    return completed
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
}

// 读取流与播放并行；播放串行，取消后正在合成的迟到结果和排队内容都不能恢复。
export function createCustomerAnswerPlayback({ play, current, onSpeak = () => {}, stop = () => {}, onFailure = () => {} }) {
  let stopped = false, count = 0, text = '', chain = Promise.resolve(true)
  return {
    push(part) {
      if (stopped || !current()) return
      if (part.partNo !== count + 1 || count >= 8) throw new Error('回答分段次序无效')
      count++; text += part.text
      chain = chain.then(async previous => {
        if (!previous || stopped || !current()) return false
        onSpeak()
        let played = false
        try { played = !!await play(part.text, part.turnId, part.partNo) } catch { /* 播放失败由当前队列统一停止。 */ }
        if (!played && !stopped && current()) { stopped = true; stop(); onFailure() }
        return played && !stopped && current()
      })
    },
    finish(answer) {
      if (!count) return null
      if (answer.status !== 'COMPLETED' || answer.errorCode || text.trim() !== answer.answer?.trim()) { stopped = true; stop() }
      return chain.then(ok => ok && !stopped && current())
    },
    cancel() { stopped = true; stop() },
    get count() { return count }
  }
}
