// 连续语音只编排识别、问答与播报，不接收或调用任何工作流执行动作。
export function createCustomerVoiceConversation({ listen, ask, play, cancel, interrupt, onState,
  maxRounds = 10, maxMillis = 300000, setTimer = setTimeout, clearTimer = clearTimeout }) {
  let generation = 0, active = false, timer, cleanup = Promise.resolve(), state = 'OFF'
  const update = (value, hint = '') => { state = value; onState(value, hint) }
  async function halt(next, hint) {
    const ticket = ++generation
    active = false; clearTimer(timer); update('STOPPING')
    const previous = cleanup
    cleanup = (async () => { await previous; return cancel() })().catch(() => false)
    await cleanup
    if (ticket === generation) update(next, hint)
  }
  async function start() {
    if (active || state === 'STOPPING') return
    const ticket = ++generation
    active = true
    timer = setTimer(() => { halt('PAUSED', '本次连续语音已达到 5 分钟，请确认后再继续。') }, maxMillis)
    const current = () => active && ticket === generation
    try {
      for (let round = 0; round < maxRounds && current(); round++) {
        update('LISTENING')
        const input = await listen(current)
        if (!current()) return
        if (!input?.text) { await halt('PAUSED', input?.hint || '未获得可用语音，已暂停，请检查后继续。'); return }
        update('THINKING')
        const answer = await ask(input.text, current, () => { if (current()) update('SPEAKING') })
        if (!current()) return
        if (!answer?.answer || answer.status !== 'COMPLETED' || answer.errorCode) throw new Error('回答未确认完成，已暂停；请核对本轮状态，不会自动重发。')
        if (!answer.streamPlayback) update('SPEAKING')
        const completed = await (answer.streamPlayback || play(answer, current))
        if (!current()) return
        if (!completed) throw new Error('播报未完成，已暂停；可检查声音权限或使用文字。')
        if (answer.intent?.state === 'READY') { await halt('PAUSED', '业务参数已收齐，请先核对摘要；运行操作仍需点击确认。'); return }
      }
      if (current()) await halt('PAUSED', '本次连续语音已完成 10 轮，请确认后再继续。')
    } catch (error) {
      if (current()) await halt('PAUSED', error?.message || '连续语音未完成，已暂停，请核对状态。')
    }
  }
  return {
    start,
    stop: () => halt('OFF'),
    pause: () => halt('PAUSED', '麦克风已关闭，点击继续后才会重新倾听。'),
    async interrupt() {
      if (!active || state === 'STOPPING') return
      const ticket = generation + 1
      await halt('PAUSED', '正在核对上一轮停止状态…')
      if (ticket !== generation) return
      try {
        if (!await interrupt() || ticket !== generation) {
          if (ticket === generation) update('PAUSED', '上一轮停止状态尚未确认，请刷新核对后继续。')
          return
        }
        await start()
      } catch { if (ticket === generation) update('PAUSED', '上一轮停止状态尚未确认，请刷新核对后继续。') }
    }
  }
}
