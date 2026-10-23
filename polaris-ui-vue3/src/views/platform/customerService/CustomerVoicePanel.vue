<template>
  <section aria-label="短录音与回答播报" class="voice-panel" :class="{ 'is-compact': compact }">
    <header v-if="!compact" class="cs-section-heading"><strong>短录音输入</strong><span class="muted">最多20秒 · 识别后核对</span></header>
    <details class="cs-speech-settings"><summary>语音设置<span>{{ preferRealtime ? '实时字幕优先' : '短录音识别' }} · {{ settings.providerPlayback && useProvider ? '供应商播报' : '设备音色' }}</span></summary><div class="cs-speech-settings-body">
      <el-switch v-if="settings.recognitionAvailable" v-model="preferRealtime" active-text="实时字幕优先" inactive-text="短录音识别" :disabled="busy || speaking" />
      <el-switch v-if="settings.providerPlayback && settings.devicePlayback && voices.length" v-model="useProvider" active-text="供应商合成" inactive-text="设备本地音色" :disabled="busy || speaking" />
      <el-select v-if="settings.devicePlayback && !(settings.providerPlayback && useProvider) && voices.length" v-model="voiceName" aria-label="设备本地音色" :disabled="busy || speaking" class="voice-select" popper-class="customer-service-menu"><el-option v-for="voice in voices" :key="voice.voiceURI" :label="`${voice.name}（${voice.lang}）`" :value="voice.voiceURI" /></el-select>
      <p class="muted">供应商播报沿用已发布模型音色；整轮最多前600字，连续语音按已校验分段播报。设备播报仅使用本地中文音色。</p>
    </div></details>
    <el-alert v-if="realtimeNotice" class="voice-notice" role="status" :title="recognitionMode === 'HTTP' ? '已自动切换为 HTTP 短录音' : realtimeFinished ? 'WebSocket 实时识别已完成' : '正在使用 WebSocket 实时识别'" :description="realtimeNotice" type="info" show-icon :closable="false" />
    <div v-if="liveCaption" role="status" class="live-caption"><span>实时字幕 · 待定稿</span><p>{{ liveCaption }}</p></div>
    <p v-if="!settings.recognitionAvailable" class="muted">未启用可用的语音识别模型，当前使用文字接待。</p>
    <p v-if="!compact && settings.devicePlayback && !(settings.providerPlayback && useProvider) && !voices.length" class="muted">设备未提供本地中文音色，播报暂不可用。</p>
    <div class="toolbar cs-short-record-actions" v-if="!compact || state === 'UNKNOWN' || readyAudio && !speaking || readyLocal">
      <el-button v-if="!compact && state === 'RECORDING'" type="primary" @click="finishRecording">{{ continuous ? '结束本句并发送' : '停止录音并识别' }}</el-button>
      <el-button v-else-if="!compact" :disabled="continuous || !active || busy || !settings.recognitionAvailable || !recordingSupported || !recognitionAllowed" @click="record">{{ state === 'REQUESTING' ? '等待麦克风授权…' : '开始短录音' }}</el-button>
      <el-button v-if="busy && (!compact || state === 'UNKNOWN')" @click="cancel">取消本次语音</el-button>
      <el-button v-if="state === 'UNKNOWN' && pending" @click="retry">核对原请求</el-button>
      <el-button v-if="readyLocal" :disabled="!active || busy" @click="retryLocal">重试设备播报</el-button>
      <el-button v-if="readyAudio && !speaking" :disabled="!(active || answerPlayback) || busy" @click="playReady">播放已就绪音频</el-button>
      <el-button v-if="speaking && !compact" @click="stopPlayback">停止播报</el-button>
    </div>
    <p v-if="state === 'RECORDING'" role="status" class="muted">正在录音，{{ seconds }} / 20 秒；{{ continuous ? '停顿后自动发送本句。' : '识别文字需核对后发送。' }}</p>
    <p v-if="state === 'SYNTHESIZING'" role="status" class="muted">正在合成当前回答…</p>
    <p v-if="state === 'RECOGNIZING'" role="status" class="muted">{{ continuous ? '正在识别，完成后自动提问；业务操作仍需确认…' : '正在识别，本段不会自动发送或启动任务…' }}</p>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <div v-if="draft" class="cs-transcript-draft"><strong>核对识别文字</strong><el-input v-model="draft" type="textarea" :rows="3" maxlength="2000" show-word-limit aria-label="待核对的识别文字" /><div class="toolbar"><el-button type="primary" :disabled="busy || !active" @click="useDraft">{{ currentText.trim() ? '追加到输入框' : '填入输入框' }}</el-button><el-button @click="draft = ''">丢弃识别文字</el-button></div></div>
  </section>
</template>
<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {createCustomerSpeechActivity} from '@/utils/customerSpeechActivity'
import {createCustomerRecorder} from '@/utils/customerRecorder'
import {
  createCustomerPcmFramer,
  createCustomerRealtimeSpeech,
  customerRealtimeUrl
} from '@/utils/customerRealtimeSpeech'
import {
  customerLocalVoices,
  customerSpeechHint,
  customerSpokenText,
  customerTranscript
} from '@/utils/customerVoiceState'
import {
  cancelCustomerSpeech,
  getCustomerRealtimeTicket,
  getCustomerSpeech,
  getCustomerSpeechAudio,
  getCustomerSpeechSettings,
  synthesizeCustomerSpeech,
  transcribeCustomerSpeech
} from '@/api/platform/customerService'

const props = defineProps({ sessionId: { type: String, required: true }, active: Boolean, compact: Boolean, answerPlayback: Boolean, continuous: Boolean, speechClient: Object, recognitionEnabled: { type: Boolean, default: true }, currentText: { type: String, default: '' } })
const speechApi = {
  settings: (...args) => props.speechClient ? props.speechClient.speechSettings(...args) : getCustomerSpeechSettings(...args),
  ticket: (...args) => props.speechClient ? props.speechClient.realtimeTicket(...args) : getCustomerRealtimeTicket(...args),
  transcribe: (...args) => props.speechClient ? props.speechClient.transcribe(...args) : transcribeCustomerSpeech(...args),
  get: (...args) => props.speechClient ? props.speechClient.speechGet(...args) : getCustomerSpeech(...args),
  cancel: (...args) => props.speechClient ? props.speechClient.speechCancel(...args) : cancelCustomerSpeech(...args),
  synthesize: (...args) => props.speechClient ? props.speechClient.synthesize(...args) : synthesizeCustomerSpeech(...args),
  audio: (...args) => props.speechClient ? props.speechClient.speechAudio(...args) : getCustomerSpeechAudio(...args)
}
const emit = defineEmits(['busy', 'transcript', 'playback', 'speaking', 'conversation-ready', 'state'])
const recognitionAllowed = computed(() => props.recognitionEnabled !== false)
const settings = ref({ recognitionAvailable: false, devicePlayback: false, providerPlayback: false }), state = ref('IDLE'), error = ref(''), draft = ref(''), seconds = ref(0)
const voices = ref([]), voiceName = ref(''), speaking = ref(false), pending = ref(null)
const useProvider = ref(true), readyAudio = ref(false), readyLocal = ref('')
const preferRealtime = ref(true), liveCaption = ref(''), realtimeNotice = ref(''), realtimeFinished = ref(false), recognitionMode = ref('HTTP')
let realtime, framer, realtimeConnecting
let localRetryUntil = 0, listeningDone, playbackDone, activity
function settleListening(text = '', hint = '') { const done = listeningDone; listeningDone = null; activity = null; done?.({ text, hint }) }
function settlePlayback(completed) { const done = playbackDone; playbackDone = null; done?.(completed) }
const busy = computed(() => ['REQUESTING', 'RECORDING', 'RECOGNIZING', 'SYNTHESIZING', 'UNKNOWN'].includes(state.value))
const recordingSupported = !!(window.isSecureContext && navigator.mediaDevices?.getUserMedia && window.AudioContext && window.AudioWorkletNode)
let disposed = false, generation = 0, playbackGeneration = 0, clock, utterance, audioElement, audioUrl, cancellation
const playbackRequests = new Map()
const recorder = createCustomerRecorder({ mediaDevices: navigator.mediaDevices, AudioContext: window.AudioContext,
  AudioWorkletNode: window.AudioWorkletNode, processorUrl: new URL('./audio/customerRecorder.worklet.js', import.meta.url).href,
  onLimit: () => { if (state.value === 'RECORDING') finishRecording() } })
watch(busy, value => emit('busy', value))
watch(state, value => emit('state', value), { immediate: true })
watch(speaking, value => emit('speaking', value))
watch(draft, refreshVoices)
watch(() => props.recognitionEnabled, () => { refreshVoices(); if (!recognitionAllowed.value && ['REQUESTING', 'RECORDING'].includes(state.value)) cancel() })
function refreshVoices() {
  voices.value = customerLocalVoices(window.speechSynthesis?.getVoices() || [])
  if (!voices.value.some(voice => voice.voiceURI === voiceName.value)) voiceName.value = voices.value[0]?.voiceURI || ''
  emit('playback', settings.value.providerPlayback || settings.value.devicePlayback && !!voices.value.length)
  emit('conversation-ready', recordingSupported && recognitionAllowed.value && settings.value.recognitionAvailable && !draft.value.trim() && (settings.value.providerPlayback || settings.value.devicePlayback && !!voices.value.length))
}
function stopPlayback() {
  settlePlayback(false)
  playbackGeneration++; readyAudio.value = false; readyLocal.value = ''
  if (audioElement) { audioElement.onended = null; audioElement.onerror = null; audioElement.pause(); audioElement.removeAttribute('src'); audioElement.load(); audioElement = null }
  if (audioUrl) { URL.revokeObjectURL(audioUrl); audioUrl = null }
  if (utterance) { utterance.onend = null; utterance.onerror = null; window.speechSynthesis?.cancel(); utterance = null }
  speaking.value = false
}
async function play(text, turnId, completed, partNo) {
  stopPlayback(); playbackDone = typeof completed === 'function' ? completed : null
  if (settings.value.providerPlayback && useProvider.value) {
    if (disposed || !(props.active || props.answerPlayback && partNo != null) || busy.value || !turnId) return false
    const key = partNo == null ? turnId : `${turnId}:part:${partNo}`
    const requestId = playbackRequests.get(key) || crypto.randomUUID()
    playbackRequests.set(key, requestId)
    pending.value = { requestId, kind: 'TTS', turnId, partNo, key }; error.value = ''
    await recognize(); return true
  }
  if (disposed || !(props.active || props.answerPlayback && partNo != null) || busy.value || !settings.value.devicePlayback) return false
  const voice = voices.value.find(item => item.voiceURI === voiceName.value && item.localService === true)
  const safeText = customerSpokenText(text)
  if (!voice || !safeText || !window.SpeechSynthesisUtterance) return false
  utterance = new SpeechSynthesisUtterance(safeText); utterance.voice = voice; utterance.lang = voice.lang
  const current = utterance
  current.onend = () => { if (utterance === current) { utterance = null; speaking.value = false; settlePlayback(true) } }
  current.onerror = event => {
    if (utterance !== current) return
    utterance = null; speaking.value = false; settlePlayback(false)
    readyLocal.value = safeText; localRetryUntil = Date.now() + 30000
    error.value = event.error === 'not-allowed'
      ? '浏览器暂未允许设备播报，请点击“重试设备播报”，或检查此站点的声音权限。'
      : '设备本地音色播放失败，可点击重试、检查系统语音服务，或选择供应商播报。'
  }
  speaking.value = true
  error.value = ''
  window.speechSynthesis.speak(current)
  return true
}
function retryLocal() {
  if (disposed || !props.active || busy.value || !readyLocal.value) return
  if (Date.now() > localRetryUntil) { readyLocal.value = ''; error.value = '设备播报重试已过期，请重新点击本轮回答的播报按钮。'; return }
  play(readyLocal.value)
}
function listen() {
  if (disposed || busy.value || !props.active || !settings.value.recognitionAvailable || !recognitionAllowed.value || !recordingSupported || draft.value.trim()) return Promise.resolve({ hint: '当前会话暂不能录音，请先核对状态。' })
  return new Promise(resolve => { record({ onResult: resolve }).catch(() => settleListening('', '无法开始录音，请检查麦克风。')) })
}
function playAndWait(text, turnId, partNo) {
  return new Promise(resolve => {
    const done = value => resolve(value)
    play(text, turnId, done, partNo).then(started => { if (!started || !speaking.value) { if (playbackDone === done) playbackDone = null; done(false) } }).catch(() => { if (playbackDone === done) playbackDone = null; done(false) })
  })
}
async function record(options = {}) {
  if (disposed || busy.value || !props.active || !settings.value.recognitionAvailable || !recognitionAllowed.value || !recordingSupported) return
  settleListening(); listeningDone = typeof options.onResult === 'function' ? options.onResult : null
  activity = listeningDone ? createCustomerSpeechActivity() : null
  stopPlayback(); error.value = ''; draft.value = ''; liveCaption.value = ''; realtimeNotice.value = ''; realtimeFinished.value = false; state.value = 'REQUESTING'
  const ticket = ++generation
  try {
    const current = (await speechApi.settings(props.sessionId)).data
    if (disposed || ticket !== generation) return
    settings.value = current; refreshVoices()
    if (!current.recognitionAvailable || !props.active) { state.value = 'IDLE'; error.value = '当前语音识别不可用，请使用文字。'; settleListening('', error.value); return }
    const requestId = crypto.randomUUID()
    recognitionMode.value = 'HTTP'; realtime = null; framer = null; realtimeConnecting = null
    function fallback() {
      recognitionMode.value = 'HTTP'; realtime?.cancel(); realtime = null; framer = null; pending.value = null; liveCaption.value = ''
      realtimeNotice.value = '当前连接或模型不支持实时识别，已切换短录音；停止后再生成识别文字。'
    }
    function failed(value) {
      if (disposed || ticket !== generation) return
      if (value.fallbackAllowed) { fallback(); return }
      clearInterval(clock); recorder.cancel(); liveCaption.value = ''; realtimeNotice.value = ''; state.value = 'UNKNOWN'
      error.value = '实时识别连接已中断，请核对原请求或取消；本段不会自动重新上传。'; settleListening('', error.value)
    }
    if (preferRealtime.value && window.WebSocket) {
      try {
        realtime = createCustomerRealtimeSpeech({ WebSocket: window.WebSocket, url: customerRealtimeUrl(import.meta.env.VITE_APP_BASE_API, window.location),
          onPartial: text => { if (!disposed && ticket === generation) liveCaption.value = text }, onFailure: failed })
        recognitionMode.value = 'STREAM'
      } catch { fallback() }
    } else if (preferRealtime.value) realtimeNotice.value = '当前环境不支持实时连接，使用短录音识别。'
    const started = await recorder.start((samples, rate) => {
      if (disposed || ticket !== generation) return
      if (recognitionMode.value === 'STREAM' && realtime) {
        try { framer ||= createCustomerPcmFramer(rate, frame => realtime?.audio(frame)); framer.push(samples) }
        catch { failed({ fallbackAllowed: !realtime?.submitted }); return }
      }
      const boundary = activity?.push(samples, rate)
      if (boundary === 'END') finishRecording()
      else if (boundary === 'SILENCE') { settleListening('', '未检测到完整语句，麦克风已关闭，请点击继续。'); cancel() }
    })
    if (disposed || ticket !== generation) return
    if (!started) { settleListening(); return }
    state.value = 'RECORDING'; seconds.value = 0
    clock = setInterval(() => { seconds.value = Math.min(20, seconds.value + 1) }, 1000)
    if (realtime) {
      const connection = realtime
      realtimeConnecting = (async () => {
        try {
          const offer = (await speechApi.ticket(props.sessionId, requestId)).data
          if (disposed || ticket !== generation || connection !== realtime) return
          if (!offer?.available) { fallback(); return }
          pending.value = { requestId, kind: 'STREAM' }
          await connection.connect(offer.ticket)
          if (!disposed && ticket === generation) realtimeNotice.value = listeningDone ? '正在实时传输并识别；停顿后自动发送最终文字，业务操作仍需确认。' : '正在实时传输并识别；字幕可能更新，停止后仍需核对最终文字。'
        } catch (value) { if (!disposed && ticket === generation && connection === realtime) failed({ fallbackAllowed: !connection.submitted }) }
      })()
    }
  } catch { if (!disposed && ticket === generation) { state.value = 'IDLE'; error.value = '无法开启麦克风，请检查浏览器授权、HTTPS、宿主和每层 iframe 的麦克风委派，或使用文字。'; settleListening('', error.value) } }
}
async function playReady() {
  if (!audioElement || disposed || !(props.active || props.answerPlayback) || busy.value) return false
  const current = audioElement, ticket = playbackGeneration
  try {
    await current.play()
    if (disposed || ticket !== playbackGeneration || current !== audioElement) return false
    speaking.value = true; return true
  } catch {
    if (!disposed && ticket === playbackGeneration) { speaking.value = false; error.value = '音频已就绪，请点击“播放已就绪音频”，或检查浏览器声音权限。' }
    return false
  }
}
async function accept(view, requestId) {
  if (pending.value?.requestId !== requestId) return
  if (view.status === 'RUNNING') { state.value = 'UNKNOWN'; error.value = '原语音仍在处理，请稍后核对；不会自动重录或重发。'; settleListening('', error.value); return }
  const request = pending.value
  pending.value = null; state.value = 'IDLE'
  if (view.status === 'COMPLETED' && request.kind === 'TTS') {
    const ticket = playbackGeneration
    try {
      const blob = await speechApi.audio(props.sessionId, requestId)
      if (disposed || ticket !== playbackGeneration || !(props.active || props.answerPlayback)) return
      if (!(blob instanceof Blob) || blob.size === 0 || blob.size > 8 * 1024 * 1024 || !['audio/wav', 'audio/mpeg', 'audio/mp3'].includes(blob.type)) throw new Error('INVALID_AUDIO')
      audioUrl = URL.createObjectURL(blob); audioElement = new Audio(audioUrl); readyAudio.value = true
      const current = audioElement
      current.onended = () => { if (current === audioElement && ticket === playbackGeneration) { speaking.value = false; settlePlayback(true) } }
      current.onerror = () => { if (current === audioElement && ticket === playbackGeneration) { stopPlayback(); error.value = '音频无法播放，可使用文字或设备本地音色。' } }
      error.value = ''; await playReady()
    } catch { if (!disposed && ticket === playbackGeneration) error.value = '合成音频不可用、权限已变化或缓存已过期；不会自动重新合成，可使用文字或设备本地音色。' }
  } else if (view.status === 'COMPLETED' && view.text) {
    liveCaption.value = ''; error.value = ''
    if (request.kind === 'STREAM') { realtimeFinished.value = true; realtimeNotice.value = '本段实时识别已完成，麦克风已关闭。' }
    if (listeningDone) settleListening(view.text)
    else draft.value = view.text
  }
  else {
    if (request.kind === 'TTS' && request.partNo == null && view.status !== 'EXPIRED') playbackRequests.delete(request.key || request.turnId)
    if (request.kind === 'STREAM') realtimeNotice.value = ''
    error.value = customerSpeechHint(view.errorCode); settleListening('', error.value)
  }
}
async function recognize() {
  if (!pending.value) return
  const request = pending.value, ticket = generation
  state.value = request.kind === 'TTS' ? 'SYNTHESIZING' : 'RECOGNIZING'
  try {
    const result = (await (request.kind === 'TTS' ? speechApi.synthesize(props.sessionId, request.requestId, request.turnId, request.partNo) : speechApi.transcribe(props.sessionId, request.requestId, request.wav))).data
    if (!disposed && ticket === generation) await accept(result, request.requestId)
  } catch (failed) {
    if (!disposed && ticket === generation) {
      if (props.speechClient && String(failed?.message || '').startsWith('RATE_LIMITED:')) {
        pending.value = null; state.value = 'IDLE'; error.value = '本次语音请求已达限额或访问过于频繁，请稍后重试，或使用文字。'
      } else { state.value = 'UNKNOWN'; error.value = '语音受理或结果尚未确认，请核对原请求，或取消后继续；不要重新创建请求。' }
      settleListening('', error.value)
    }
  }
}
async function finishRecording() {
  if (state.value !== 'RECORDING') return
  clearInterval(clock); state.value = 'RECOGNIZING'
  const ticket = generation
  try {
    const wav = await recorder.stop()
    if (disposed || ticket !== generation) return
    framer?.finish()
    if (realtimeConnecting) await realtimeConnecting
    if (disposed || ticket !== generation || state.value === 'UNKNOWN') return
    if (recognitionMode.value === 'STREAM' && realtime) {
      const connection = realtime, requestId = pending.value.requestId
      const view = await connection.finish()
      if (!disposed && ticket === generation) await accept(view, requestId)
      connection.cancel(); if (realtime === connection) realtime = null
      return
    }
    pending.value = { requestId: crypto.randomUUID(), kind: 'STT', wav }
    await recognize()
  } catch {
    if (!disposed && ticket === generation && state.value !== 'UNKNOWN') {
      realtime?.cancel()
      if (pending.value?.kind === 'STREAM') { state.value = 'UNKNOWN'; realtimeNotice.value = ''; error.value = '实时识别结果尚未确认，请核对原请求或取消，不会自动重发。' }
      else { state.value = 'IDLE'; error.value = '录音不足 0.2 秒或格式不可用，请重新录制。' }
      settleListening('', error.value)
    }
  }
}
async function check() {
  if (!pending.value) return
  const id = pending.value.requestId, ticket = generation
  try { const view = (await speechApi.get(props.sessionId, id)).data; if (!disposed && ticket === generation) accept(view, id) }
  catch { if (!disposed && ticket === generation) error.value = '尚未确认原识别状态，可继续核对；结束整个会话也会使迟到识别失效。' }
}
async function retry() { if (state.value === 'UNKNOWN' && pending.value) { if (pending.value.kind === 'STREAM') await check(); else await recognize() } }
async function cancel() {
  if (cancellation) return cancellation
  cancellation = (async () => {
    generation++; settleListening('', '本次录音已停止。'); clearInterval(clock); stopPlayback(); draft.value = ''; liveCaption.value = ''; realtimeNotice.value = ''; realtime?.cancel(); realtime = null; framer = null
    const request = pending.value
    await recorder.cancel()
    if (request) {
      try {
        await speechApi.cancel(props.sessionId, request.requestId)
        if (request.kind === 'TTS' && request.partNo == null) playbackRequests.delete(request.key || request.turnId)
        if (pending.value?.requestId === request.requestId) pending.value = null
        state.value = 'IDLE'; error.value = ''
      } catch { if (!disposed) { state.value = 'UNKNOWN'; error.value = '取消状态尚未确认，请核对原请求，或结束整个会话。' } }
    } else state.value = 'IDLE'
  })()
  try { await cancellation } finally { cancellation = null }
}
function useDraft() {
  try { emit('transcript', customerTranscript(draft.value, props.currentText)); draft.value = '' }
  catch (invalid) { error.value = invalid.message }
}
watch(() => [props.active, props.answerPlayback], ([active, playback]) => { if (!active && !playback) { stopPlayback(); if (busy.value) cancel() } })
onMounted(async () => {
  window.speechSynthesis?.addEventListener('voiceschanged', refreshVoices)
  try { const value = (await speechApi.settings(props.sessionId)).data; if (!disposed) { settings.value = value; refreshVoices() } }
  catch { if (!disposed) error.value = '语音功能暂不可用，文字接待不受影响。' }
})
onBeforeUnmount(() => {
  disposed = true; generation++; settleListening(); clearInterval(clock); stopPlayback(); realtime?.cancel(); recorder.cancel()
  if (pending.value) speechApi.cancel(props.sessionId, pending.value.requestId).catch(() => {})
  window.speechSynthesis?.removeEventListener('voiceschanged', refreshVoices)
  emit('busy', false); emit('playback', false); emit('speaking', false); emit('state', 'IDLE')
})
defineExpose({ play, playAndWait, listen, stopPlayback, cancel, finishRecording })
</script>
