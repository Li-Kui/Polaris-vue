import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import * as stateUtils from '../src/utils/customerServiceState.js'
import * as parameters from '../src/utils/customerParameterState.js'
import * as avatarUtils from '../src/utils/customerAvatarState.js'
import * as answerStream from '../src/utils/customerAnswerStream.js'
import {createCustomerVoiceConversation} from '../src/utils/customerVoiceConversation.js'

const noop = () => {}, tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
const source = await readFile(new URL('../src/views/platform/customerService/CustomerConversation.vue', import.meta.url), 'utf8')
const script = compileScript(parse(source).descriptor, { id: 'continuous-conversation' }).content
const compiled = await transformWithEsbuild(script, 'conversation.js', { format: 'cjs', target: 'esnext' })
function fixture({ sending, playing, refreshing, contact = null, streamParts = [], visitor = false, visitorVoiceEnabled = false, maxTurns = 50 } = {}) {
  let mount, unmount, count = 0, listenCount = 0, plays = 0, interrupts = 0, sends = 0, closes = 0
  const pageEvents = new Map(), emitted = []
  const browserPage = { addEventListener: (name, handler) => pageEvents.set(name, handler), removeEventListener: name => pageEvents.delete(name) }
  const captured = [], played = [], page = { hidden: false, addEventListener: noop, removeEventListener: noop }
  const snapshot = { id: 'session', status: 'ACTIVE', expiresAt: new Date(Date.now() + 60000).toISOString(), turns: [], contact }
  const api = {
    createCustomerSession: async () => ({ data: structuredClone(snapshot) }),
    getCustomerSession: async () => { if (refreshing) await refreshing(); return { data: structuredClone(snapshot) } },
    sendCustomerMessage: async (_, requestId, question, onEvent) => {
      sends++
      const turn = { id: `turn-${sends}`, requestId, question, status: 'RUNNING', sources: [] }; snapshot.turns.push(turn)
      if (streamParts.length) {
        onEvent({ type: 'accepted', turnId: turn.id, requestId })
        streamParts.forEach((text, index) => onEvent({ type: 'part', turnId: turn.id, requestId, partNo: index + 1, text }))
      }
      if (sending) await sending(turn)
      if (turn.status === 'RUNNING') { turn.status = 'COMPLETED'; turn.answer = streamParts.length ? streamParts.join('') : '合成知识回答' }
      if (streamParts.length) onEvent({ type: 'completed', turnId: turn.id, requestId, turn: structuredClone(turn) })
      return { data: structuredClone(turn) }
    },
    interruptCustomerTurn: async (_, id) => { interrupts++; snapshot.turns.find(turn => turn.id === id).status = 'INTERRUPTED' },
    closeCustomerSession: async () => { closes++ },
    runCustomerIntent: () => assert.fail('连续语音不得自动运行业务')
  }
  api.streamCustomerMessage = async (...args) => (await api.sendCustomerMessage(...args)).data
  let visitorClient
  if (visitor) {
    const originalSend = api.sendCustomerMessage
    visitorClient = { create: api.createCustomerSession, get: api.getCustomerSession, close: api.closeCustomerSession,
      stream: async (...args) => (await originalSend(...args)).data, interrupt: api.interruptCustomerTurn,
      source: async () => ({ data: { documentName: '访客来源', text: '合成片段' } }) }
    for (const name of Object.keys(api)) api[name] = () => assert.fail(`访客不得调用管理员接口 ${name}`)
  }
  const modules = { vue: { ...Vue, onMounted: fn => { mount = fn }, onBeforeUnmount: fn => { unmount = fn } }, 'element-plus': { ElMessage: { warning: noop } },
    './CustomerOperationPanel.vue': {}, './CustomerVoicePanel.vue': {}, './CustomerAvatar.vue': {}, './CustomerContactCard.vue': {}, '@/utils/customerAvatarState': avatarUtils, '@/utils/customerServiceState': stateUtils,
    '@/utils/customerAnswerStream': answerStream, '@/utils/customerParameterState': parameters, '@/utils/customerVoiceConversation': { createCustomerVoiceConversation }, '@/api/platform/customerService': api }
  const module = { exports: {} }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, require: name => { assert.ok(name in modules, name); return modules[name] },
    document: page, window: browserPage, setTimeout, clearTimeout, AbortController, crypto: { randomUUID: () => `request-${++count}` } })
  const scope = Vue.effectScope(), state = scope.run(() => module.exports.default.setup({ appId: 2, allowTests: !visitor, visitorClient, visitorVoiceEnabled, maxTurns }, { emit: (...args) => emitted.push(args), expose: noop }))
  state.voiceReady.value = true
  state.voicePanel.value = {
    listen: () => { listenCount++; state.speechState.value = 'RECORDING'; const input = deferred(); captured.push(input); return input.promise.finally(() => { state.speechState.value = 'IDLE' }) },
    playAndWait: async (text, turnId, partNo) => { plays++; played.push({ text, turnId, partNo }); state.voicePlaying.value = true; try { return playing ? await playing(text, turnId, partNo) : true } finally { state.voicePlaying.value = false } }, stopPlayback: noop,
    cancel: async () => { state.speechState.value = 'IDLE'; state.voicePlaying.value = false; captured.forEach(input => input.resolve({ text: '' })) }
  }
  return { state, snapshot, captured, played, page, pageEvents, emitted, mount: () => mount(), closes: () => closes, counts: () => ({ listenCount, plays, interrupts, sends }),
    dispose: async () => { unmount(); scope.stop(); await tick() } }
}
test('真实会话组件将一句语音只提交一次，实际播报结束前不打开下一段录音', async () => {
  const audio = deferred(), f = fixture({ playing: () => audio.promise })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({ text: '查询合成资料' }); await tick()
  assert.deepEqual(f.counts(), { listenCount: 1, sends: 1, plays: 1, interrupts: 0 })
  assert.equal(f.state.voiceState.value, 'SPEAKING')
  audio.resolve(true); await tick(); assert.equal(f.counts().listenCount, 2)
  await f.state.voiceConversation.stop(); await running; await f.dispose()
})
test('运行中问答打断后已在服务端停止，迟到HTTP结果不覆盖新一轮', async () => {
  const pendingAnswer = deferred(), f = fixture({ sending: () => pendingAnswer.promise })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({ text: '第一个问题' }); await tick()
  const interrupting = f.state.voiceConversation.interrupt(); await tick()
  assert.equal(f.counts().interrupts, 1); assert.equal(f.snapshot.turns[0].status, 'INTERRUPTED')
  assert.equal(f.counts().listenCount, 2); assert.equal(f.state.sending.value, false)
  pendingAnswer.resolve(); await running; await tick()
  assert.equal(f.counts().plays, 0); assert.equal(f.state.voiceState.value, 'LISTENING')
  await f.state.voiceConversation.stop(); await interrupting; await f.dispose()
})
test('访客会话读取真实中断快照后显示已停止，不能误报模型失败', async () => {
  const f = fixture({ visitor: true }); await f.state.start()
  f.snapshot.turns.push({ id: 'interrupted-turn', status: 'INTERRUPTED', errorCode: 'TURN_INTERRUPTED', answer: null, sources: [] })
  await f.state.refreshSession()
  assert.equal(f.state.turnHint(f.state.session.value.turns[0]), '本轮已停止，可以继续咨询。')
  assert.equal(f.state.turnHint({ status: 'FAILED', errorCode: 'MODEL_CALL_FAILED' }), '暂时无法完成回答，请稍后再试或联系人工。')
  assert.deepEqual(f.counts(), { listenCount: 0, plays: 0, interrupts: 0, sends: 0 })
  await f.dispose()
})
test('未输入完成的文字阻止开启，切到后台会暂停并释放录音', async () => {
  const f = fixture(); await f.state.start()
  f.state.message.value = '尚未发送的手动文字'; assert.equal(f.state.canStartVoice.value, false)
  f.state.startVoice(); await tick(); assert.equal(f.counts().listenCount, 0)
  f.state.message.value = ''; f.state.startVoice(); await tick(); assert.equal(f.counts().listenCount, 1)
  f.page.hidden = true; f.state.visibilityChanged(); await tick()
  assert.equal(f.state.voiceState.value, 'PAUSED'); assert.equal(f.state.canStartVoice.value, false)
  assert.equal(f.state.avatarActive.value, false)
  await f.dispose()
})
test('设备开启期间不能显示已经在倾听，录音就绪后再切换提示', async () => {
  const f = fixture(); await f.state.start()
  f.state.voiceState.value = 'LISTENING'; f.state.speechState.value = 'REQUESTING'
  assert.equal(f.state.voiceStatus.value, '正在开启麦克风，请稍候')
  f.state.speechState.value = 'RECORDING'
  assert.equal(f.state.voiceStatus.value, '正在倾听与识别')
  f.state.voiceState.value = 'THINKING'
  assert.equal(f.state.voiceStatus.value, '正在准备回答')
  await f.dispose()
})
test('并发刷新共用同一请求，停止期间不会用旧快照漏掉服务端中断', async () => {
  const read = deferred(); let reads = 0
  const f = fixture({ refreshing: () => { reads++; return read.promise } }); await f.state.start()
  const first = f.state.refreshSession(), second = f.state.refreshSession()
  assert.equal(reads, 1); read.resolve(); await Promise.all([first, second]); await f.dispose()
})
test('状态查询失败会暂停连续语音，迟到识别不会提交', async () => {
  let fail = false
  const f = fixture({ refreshing: () => { if (fail) throw new Error('已撤权') } }); await f.state.start()
  const running = f.state.voiceConversation.start(); await tick(); fail = true
  await assert.rejects(f.state.refreshSession()); await tick()
  assert.equal(f.state.voiceState.value, 'PAUSED'); assert.equal(f.counts().sends, 0)
  assert.equal(f.state.avatarActive.value, false)
  await running; await f.dispose()
})
test('人物按真实会话状态切换，等待合成不能提前显示讲话状态', async () => {
  const f = fixture(); await f.state.start()
  assert.equal(f.state.avatarState.value, 'IDLE')
  f.state.speechState.value = 'RECORDING'; assert.equal(f.state.avatarState.value, 'LISTENING')
  f.state.speechState.value = 'SYNTHESIZING'; f.state.voiceState.value = 'SPEAKING'
  assert.equal(f.state.avatarState.value, 'THINKING')
  f.state.speechState.value = 'IDLE'; f.state.voicePlaying.value = true
  assert.equal(f.state.avatarState.value, 'SPEAKING')
  f.state.voicePlaying.value = false; f.state.voiceState.value = 'PAUSED'
  assert.equal(f.state.avatarState.value, 'PAUSED')
  f.state.closing.value = true; assert.equal(f.state.avatarActive.value, false)
  await f.dispose()
})
test('播报期间暂停立即切回封面，迟到音频结束不会再倾听，显式继续与结束恢复正确人物状态', async () => {
  const audio = deferred(), f = fixture({ playing: () => audio.promise })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  assert.equal(f.state.avatarState.value, 'LISTENING')
  f.captured[0].resolve({ text: '合成测试问题' }); await tick()
  assert.equal(f.state.avatarState.value, 'SPEAKING')
  await f.state.voiceConversation.pause()
  assert.equal(f.state.avatarState.value, 'PAUSED'); assert.equal(f.state.voicePlaying.value, false)
  audio.resolve(true); await running; await tick()
  assert.equal(f.counts().listenCount, 1); assert.equal(f.state.avatarState.value, 'PAUSED')
  const resumed = f.state.voiceConversation.start(); await tick()
  assert.equal(f.state.avatarState.value, 'LISTENING')
  await f.state.voiceConversation.stop(); await resumed
  assert.equal(f.state.avatarState.value, 'IDLE'); assert.equal(f.counts().sends, 1)
  await f.dispose()
})
test('播报打断后人物重新倾听，旧播报完成不能覆盖新一轮或重复发送', async () => {
  const audio = deferred(), f = fixture({ playing: () => audio.promise })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({ text: '合成测试问题' }); await tick()
  assert.equal(f.state.avatarState.value, 'SPEAKING')
  const interrupting = f.state.voiceConversation.interrupt(); await tick()
  assert.equal(f.counts().listenCount, 2); assert.equal(f.state.avatarState.value, 'LISTENING')
  audio.resolve(true); await running; await tick()
  assert.equal(f.state.avatarState.value, 'LISTENING'); assert.equal(f.counts().sends, 1)
  await f.state.voiceConversation.stop(); await interrupting; await f.dispose()
})

test('打开人工入口先停止连续录音，清理期间不展示；关闭卡片也不会自动重开麦克风', async () => {
  const f = fixture({ contact: { name: '测试人工', webUrl: 'https://example.invalid/support' } })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  const cleanup = deferred(), cancel = f.state.voicePanel.value.cancel
  f.state.voicePanel.value.cancel = async () => { await cleanup.promise; await cancel() }
  const opening = f.state.openContact(); await tick()
  assert.equal(f.state.contactBusy.value,true); assert.equal(f.state.contactOpen.value,false); assert.equal(f.state.canStartVoice.value,false)
  cleanup.resolve(); await opening; await running
  assert.equal(f.state.contactOpen.value,true); assert.equal(f.state.voiceState.value,'OFF'); assert.equal(f.counts().sends,0)
  assert.equal(f.state.canStartVoice.value,false)
  f.state.contactOpen.value = false; await tick(); assert.equal(f.counts().listenCount,1)
  await f.dispose()
})
test('语音回答期间联系人工停止本轮，迟到回答不播报、不重新提问，文字会话保留', async () => {
  const pending = deferred(), f = fixture({ contact: { name: '测试人工', webUrl: 'https://example.invalid/support' }, sending: () => pending.promise })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({ text: '测试问题' }); await tick()
  await f.state.openContact(); assert.equal(f.state.contactOpen.value,true); assert.equal(f.state.session.value.status,'ACTIVE')
  assert.equal(f.counts().interrupts,1); pending.resolve(); await running
  assert.equal(f.counts().plays,0); assert.equal(f.counts().sends,1); assert.equal(f.counts().listenCount,1)
  await f.dispose()
})

test('流式会话首段在生成结束前播放，整轮与队列均完成后才再次倾听，不重播完整回答', async () => {
  const generation = deferred(), audio = deferred()
  const f = fixture({ streamParts: ['首句[S1]。', '第二句[S1]。'], sending: () => generation.promise, playing: () => audio.promise })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({ text: '流式问题' }); await tick()
  assert.deepEqual(f.counts(), { listenCount: 1, sends: 1, plays: 1, interrupts: 0 })
  assert.equal(f.state.sending.value, true); assert.equal(f.state.streamPlaybackActive.value, true)
  assert.equal(f.state.voiceState.value, 'SPEAKING'); assert.equal(f.state.streamPreview.value.text, '首句[S1]。第二句[S1]。')
  audio.resolve(true); await tick(); assert.equal(f.counts().plays, 2); assert.equal(f.counts().listenCount, 1)
  generation.resolve(); await tick(); await tick()
  assert.equal(f.counts().plays, 2); assert.equal(f.counts().listenCount, 2)
  await f.state.voiceConversation.stop(); await running; await f.dispose()
})
test('流式播报期间打断，未播放句段丢弃，原轮次确认停止后才开始下一轮', async () => {
  const generation = deferred(), audio = deferred()
  const f = fixture({ streamParts: ['首句[S1]。', '不能迟到播放[S1]。'], sending: () => generation.promise, playing: () => audio.promise })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({ text: '流式问题' }); await tick(); assert.equal(f.counts().plays, 1)
  const interrupted = f.state.voiceConversation.interrupt(); await tick()
  assert.equal(f.snapshot.turns[0].status, 'INTERRUPTED'); assert.equal(f.counts().listenCount, 2)
  audio.resolve(true); generation.resolve(); await running; await tick()
  assert.equal(f.counts().plays, 1); assert.equal(f.state.streamPreview.value, null)
  await f.state.voiceConversation.stop(); await interrupted; await f.dispose()
})

test('切换文字先清理当前语音，视图切换不新建会话、不重复发送或自动开启麦克风', async () => {
  const f = fixture(); await f.state.start(); await f.state.selectInputMode('voice')
  assert.equal(f.counts().listenCount, 0)
  const running = f.state.voiceConversation.start(); await tick()
  await f.state.selectInputMode('text'); await running
  assert.equal(f.state.voiceState.value, 'OFF'); assert.equal(f.state.inputMode.value, 'text')
  assert.deepEqual(f.counts(), { listenCount: 1, plays: 0, interrupts: 0, sends: 0 })
  const id = f.state.session.value.id
  f.state.toggleDiagnostics(); f.state.toggleDiagnostics(); await f.state.selectInputMode('voice'); await f.state.selectVoiceMode('short')
  assert.equal(f.state.session.value.id, id); assert.equal(f.counts().listenCount, 1); await f.dispose()
})
test('手动短录音切换模式同样等待资源释放，清理期间不能再切换并恢复录音', async () => {
  const f = fixture(); await f.state.start(); await f.state.selectInputMode('voice'); await f.state.selectVoiceMode('short')
  const cleanup = deferred(); f.state.voiceBusy.value = true; f.state.speechState.value = 'RECORDING'
  f.state.voicePanel.value.cancel = () => cleanup.promise
  const switching = f.state.selectInputMode('text'); await tick()
  assert.equal(f.state.modeChanging.value, true); assert.equal(f.state.inputMode.value, 'voice')
  await f.state.selectVoiceMode('continuous'); assert.equal(f.state.voiceMode.value, 'short')
  cleanup.resolve(true); await switching
  assert.equal(f.state.inputMode.value, 'text'); assert.equal(f.state.modeChanging.value, false); assert.equal(f.counts().sends, 0)
  await f.dispose()
})

test('文字问答处理中人物区同步显示准备回答，不能仍提示准备接待', async () => {
  const answer = deferred(), f = fixture({ sending: () => answer.promise })
  await f.state.start(); f.state.message.value = '知识咨询'
  const sending = f.state.send(); await tick()
  assert.equal(f.state.stageStatus.value, '正在准备回答')
  answer.resolve(); await sending
  assert.equal(f.state.stageStatus.value, '准备接待'); assert.equal(f.counts().sends, 1)
  await f.dispose()
})

test('访客会话、提问、刷新、来源和结束全部使用访客客户端，不触发管理员接口或语音', async () => {
  const f = fixture({ visitor: true }); await f.state.start()
  assert.equal(f.state.canStartVoice.value,false)
  f.state.message.value='访客问题'; await f.state.send()
  assert.equal(f.state.session.value.turns.length,1); assert.equal(f.counts().listenCount,0)
  await f.state.showSource(f.state.session.value.turns[0],{citationId:'S1'})
  assert.equal(f.state.sourcePreview.value.documentName,'访客来源')
  await f.state.endSession(); await f.dispose()
})
test('访客达到配置轮数后不能再提交新问题，已完成回答保留', async () => {
  const f = fixture({visitor:true,maxTurns:1}); await f.state.start()
  f.state.message.value='第一问'; await f.state.send(); assert.equal(f.state.atTurnLimit.value,true)
  f.state.message.value='第二问'; await f.state.send()
  assert.equal(f.counts().sends,1); assert.equal(f.state.session.value.turns[0].status,'COMPLETED')
  await f.dispose()
})

test('访客 iframe 页面离开时关闭专属会话，迟到回答不能更新已销毁页面，恢复缓存页面需重新开始', async () => {
  const answer = deferred(), f = fixture({ visitor: true, sending: () => answer.promise })
  f.mount(); await tick()
  assert.equal(f.pageEvents.has('pagehide'), true)
  f.state.message.value = '即将离开的咨询'
  const sending = f.state.send(); await tick()
  f.pageEvents.get('pagehide')(); await tick()
  assert.equal(f.closes(), 1)
  answer.resolve(); await sending
  assert.equal(f.state.session.value.turns.length, 0)
  f.pageEvents.get('pageshow')({ persisted: true })
  assert.equal(f.emitted.some(([event]) => event === 'unavailable'), true)
  await f.dispose(); assert.equal(f.pageEvents.size, 0)
})
test('访客渠道撤销后立即清除会话及来源并显示失效入口，不再提交新问题', async () => {
  let revoked = false
  const f = fixture({ visitor: true, refreshing: () => { if (revoked) throw new Error('已撤销') } })
  await f.state.start(); f.state.sourceOpen.value = true; f.state.sourcePreview.value = { text: '旧来源' }
  revoked = true; await assert.rejects(f.state.refreshSession())
  assert.equal(f.state.session.value, null); assert.equal(f.state.sourceOpen.value, false); assert.equal(f.state.sourcePreview.value, null)
  assert.equal(f.emitted.some(([event]) => event === 'unavailable'), true)
  f.state.message.value = '不能发送'; await f.state.send(); assert.equal(f.counts().sends, 0)
  await f.dispose()
})
test('显式授权访客连续语音复用专属问答，关闭页面后迟到识别不能再提问', async () => {
  const f=fixture({visitor:true,visitorVoiceEnabled:true}); await f.state.start()
  assert.equal(f.state.speechEnabled.value,true); assert.equal(f.state.canStartVoice.value,true)
  const running=f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({text:'合成访客咨询'}); await tick()
  assert.equal(f.counts().sends,1); assert.equal(f.counts().plays,1)
  assert.equal(f.state.session.value.turns.length,1)
  f.state.visitorPageExit(); await tick()
  f.captured.at(-1).resolve({text:'离开后的迟到录音'}); await running; await tick()
  assert.equal(f.counts().sends,1); await f.dispose()
})

test('访客在倾听阶段离开页面即关闭会话，迟到识别不创建第一轮问答或播报', async () => {
  const f = fixture({ visitor: true, visitorVoiceEnabled: true }); f.mount(); await tick()
  const running = f.state.voiceConversation.start(); await tick()
  const lateInput = f.captured[0]
  f.pageEvents.get('pagehide')(); await tick()
  assert.equal(f.closes(), 1)
  lateInput.resolve({ text: '窗口关闭后的问题' }); await running; await tick()
  assert.equal(f.counts().sends, 0); assert.equal(f.counts().plays, 0); assert.equal(f.counts().listenCount, 1)
  assert.equal(f.snapshot.turns.length, 0); await f.dispose()
})

test('访客播报中打断后完成下一轮，旧分段和迟到播放结束不会混入新回答', async () => {
  const oldAudio = deferred(), nextAudio = deferred()
  const f = fixture({ visitor: true, visitorVoiceEnabled: true, streamParts: ['首句[S1]。', '后句[S1]。'],
    playing: (_, turnId, partNo) => turnId === 'turn-1' ? oldAudio.promise : partNo === 1 ? nextAudio.promise : true })
  await f.state.start(); const running = f.state.voiceConversation.start(); await tick()
  f.captured[0].resolve({ text: '第一轮问题' }); await tick()
  assert.equal(f.state.voiceState.value, 'SPEAKING'); assert.equal(f.played.length, 1)
  const interrupting = f.state.voiceConversation.interrupt(); await tick()
  assert.equal(f.state.voiceState.value, 'LISTENING'); assert.equal(f.counts().listenCount, 2)
  f.captured[1].resolve({ text: '打断后的第二轮问题' }); await tick()
  assert.equal(f.counts().sends, 2); assert.equal(f.state.voiceState.value, 'SPEAKING')
  oldAudio.resolve(true); await running; await tick()
  assert.equal(f.state.voiceState.value, 'SPEAKING')
  assert.deepEqual(f.played.map(({ turnId, partNo }) => [turnId, partNo]), [['turn-1', 1], ['turn-2', 1]])
  nextAudio.resolve(true); await tick()
  assert.deepEqual(f.played.map(({ turnId, partNo }) => [turnId, partNo]), [['turn-1', 1], ['turn-2', 1], ['turn-2', 2]])
  assert.equal(f.state.voiceState.value, 'LISTENING'); assert.equal(f.counts().listenCount, 3)
  assert.equal(f.counts().sends, 2); assert.equal(f.snapshot.turns.length, 2)
  await f.state.voiceConversation.stop(); await interrupting; await f.dispose()
})
