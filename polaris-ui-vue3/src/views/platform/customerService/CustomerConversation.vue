<template>
  <section class="customer-conversation" :aria-busy="starting">
    <div v-if="starting" class="cs-loading-state"><span class="cs-assistant-symbol" aria-hidden="true">✦</span><p>正在准备接待会话…</p></div>
    <el-alert v-if="startError" :title="visitorClient ? '暂时无法开始接待，请稍后重试。' : '会话未创建，请检查渠道状态、登录与资源权限。'" type="warning" :closable="false" />
    <el-button v-if="startError" @click="start">重试创建会话</el-button>
    <template v-if="session">
      <header class="cs-reception-heading"><div><h2>{{ serviceName || session.avatar?.name || '智能客服' }}</h2><span class="cs-status" :class="{ 'is-live': session.status === 'ACTIVE' }">{{ session.status === 'ACTIVE' ? '会话进行中' : '会话已结束' }}</span><p>{{ visitorClient ? '本次接待' : '当前使用已发布版本' }} · {{ session.turns.length }} / {{ maxTurns }} 轮</p></div><div class="toolbar"><el-button v-if="allowTests" :class="{ 'is-selected': diagnosticOpen }" @click="toggleDiagnostics">测试诊断</el-button><el-button :loading="refreshing" @click="refreshSession">刷新状态</el-button><el-button :loading="closing" @click="endSession">结束会话</el-button></div></header>
      <div class="cs-reception-grid" :class="{ 'with-diagnostics': diagnosticOpen && allowTests, 'voice-active': conversationOn }">
        <aside class="cs-persona-pane" :class="{ 'voice-active': conversationOn }">
          <div class="cs-persona-label"><span class="cs-status is-accent">AI 数字人</span><span>{{ avatarState === 'SPEAKING' ? '语音播报中' : avatarState === 'LISTENING' ? '麦克风已开启' : '接待服务' }}</span></div>
          <CustomerAvatar :load-asset="visitorClient?.asset" v-if="session.avatar" :config="session.avatar" :session-id="session.id" :state="avatarState" :active="avatarActive" />
          <div v-else class="cs-avatar-empty"><span class="cs-assistant-symbol" aria-hidden="true">✦</span><strong>{{ serviceName || '智能客服' }}</strong><p>随时为你解答知识问题</p><small>此客服尚未配置人物素材</small></div>
          <section :aria-label="visitorClient ? '接待状态' : '连续语音对话'" class="continuous-voice">
            <div class="cs-waveform" :class="{ 'is-moving': voicePlaying || speechState === 'RECORDING' }" aria-hidden="true"><i v-for="n in 15" :key="n" :style="{ '--bar': `${(n * 7 % 19) + 6}px`, '--delay': `${n * 65}ms` }"></i></div>
            <p class="cs-voice-status" role="status">{{ stageStatus }}</p><p v-if="voiceHint" class="cs-voice-hint" role="status">{{ voiceHint }}</p>
            <div v-if="voiceState === 'OFF' && (voiceBusy || voicePlaying)" class="cs-voice-dock"><el-button v-if="voiceBusy" @click="voicePanel?.cancel()">取消本次语音</el-button><el-button v-if="voicePlaying" @click="voicePanel?.stopPlayback()">停止播报</el-button></div>
            <template v-else-if="speechEnabled && (voiceMode === 'continuous' || voiceState !== 'OFF')">
              <div class="cs-voice-dock">
                <el-button v-if="conversationOn && voiceState !== 'STOPPING'" class="cs-pause-button" @click="voiceConversation.pause">暂停</el-button>
                <el-button v-if="!conversationOn" :disabled="!canStartVoice" type="primary" class="cs-main-voice" @click="inputMode = 'voice'; startVoice()">{{ voiceState === 'PAUSED' ? '继续语音' : '开启连续语音' }}</el-button>
                <el-button v-else-if="voiceState === 'LISTENING'" type="primary" class="cs-main-voice" :disabled="speechState !== 'RECORDING'" @click="voicePanel?.finishRecording()">结束本句并发送</el-button>
                <el-button v-else-if="['THINKING', 'SPEAKING'].includes(voiceState)" type="primary" class="cs-main-voice" @click="voiceConversation.interrupt">打断并继续说话</el-button>
                <el-button v-else class="cs-main-voice" :loading="true" disabled>正在停止</el-button>
                <el-button v-if="voiceState !== 'OFF'" class="cs-end-voice" :disabled="voiceState === 'STOPPING'" @click="voiceConversation.stop">结束语音</el-button>
              </div>
              <p class="cs-voice-footnote">{{ voicePlaying ? '播报时麦克风已关闭' : '主动开启 · 停顿后自动发送 · 最多5分钟' }}</p>
              <p v-if="!voiceReady" class="muted">连续语音需要可用的识别模型和回答音色；可使用文字或短录音。</p>
            </template>
          </section>
          <div v-show="inputMode === 'voice'" class="cs-voice-options"><div class="cs-mode-switch" aria-label="语音输入方式"><button type="button" :disabled="modeChanging" :class="{ active: voiceMode === 'continuous' }" @click="selectVoiceMode('continuous')">连续对话</button><button type="button" :disabled="modeChanging" :class="{ active: voiceMode === 'short' }" @click="selectVoiceMode('short')">短录音</button></div></div>
          <CustomerVoicePanel v-if="speechEnabled" :speech-client="visitorClient" :recognition-enabled="!atTurnLimit" ref="voicePanel" :key="`voice:${session.id}`" :session-id="session.id" :compact="inputMode !== 'voice' || voiceMode === 'continuous'" :active="canSend && !sending && !pending && !contactOpen && !contactBusy" :answer-playback="streamPlaybackActive && conversationOn && pageVisible && !closing && !contactOpen && !contactBusy" :current-text="message" :continuous="conversationOn" @state="speechState = $event" @conversation-ready="voiceReady = $event" @busy="voiceBusy = $event" @speaking="voicePlaying = $event" @playback="devicePlayback = $event" @transcript="message = $event; inputMode = 'text'" />
        </aside>
        <section class="cs-chat-pane" aria-label="接待对话">
          <header class="cs-chat-heading"><h3>对话记录</h3><el-button v-if="session.contact" link type="primary" :loading="contactBusy" :disabled="closing" @click="openContact">联系人工</el-button></header>
          <div class="cs-chat-scroll">
            <div v-if="!session.turns.length && !streamPreview?.text" class="cs-chat-welcome"><span class="cs-assistant-symbol" aria-hidden="true">✦</span><h3>开始一段新的对话</h3><p>{{ session.welcomeMessage }}</p><span class="cs-status">回答依据已授权知识库</span></div>
        <div class="conversation" aria-live="polite">
          <div v-for="turn in session.turns" :key="turn.id" class="turn">
            <div class="question"><strong>您</strong><p>{{ turn.question }}</p></div>
            <div class="answer"><strong>客服</strong>
              <p>{{ turn.answer || (turn.id === streamPreview?.turnId ? streamPreview.text || turnHint(turn) : turnHint(turn)) }}</p>
              <el-button v-if="!conversationOn && devicePlayback && turn.status === 'COMPLETED' && turn.answer && turn.id === session.turns.at(-1)?.id" :disabled="conversationOn || !canInteract || sending || !!pending || playingCheck" @click="playAnswer(turn)">播报本轮回答</el-button>
              <section v-if="turn.intent" class="intent-summary" aria-label="能力参数摘要">
                <strong>{{ turn.intent.name }} · {{ turn.intent.state === 'COLLECTING' ? '待补充参数' : '参数已收齐' }}</strong>
                <dl><template v-for="item in customerIntentEntries(turn.intent)" :key="item.name"><dt>{{ customerIntentLabel(turn.intent, item.name) }}</dt><dd>{{ item.value }}</dd></template></dl>
                <p v-if="turn.intent.missingFields.length">请在下方继续补充：{{ turn.intent.missingFields.map(name => customerIntentLabel(turn.intent, name)).join('、') }}</p>
                <p v-for="issue in turn.intent.issues || []" :key="issue.name">{{ issue.label }}：{{ issue.message }}</p>
                <template v-if="turn.id === session.turns.at(-1)?.id"><p v-for="parameter in turn.intent.parameters || []" :key="parameter.name" class="muted">{{ customerParameterLabel(parameter) }}：{{ customerParameterHints(parameter).join('；') || '请按您的实际需求填写。' }}</p></template>
                <p v-if="turn.intent.started">测试任务已受理。<el-button v-if="allowTests" link type="primary" @click="diagnosticOpen = true">查看任务结果</el-button></p>
                <el-button v-else-if="customerIntentRunnable(turn, session.turns)" type="primary" :loading="confirmingIntent === turn.requestId" :disabled="conversationOn || !canInteract || sending || !!pending" @click="runIntent(turn)">运行此测试</el-button>
                <p v-else-if="turn.intent.state === 'READY'" class="muted">此摘要已被后续消息替代，请使用最新摘要。</p>
                <p v-if="intentError?.requestId === turn.requestId" class="muted">{{ intentError.message }}</p>
              </section>
              <div v-if="turn.sources.length" class="cs-citation-list"><span v-for="source in turn.sources" :key="source.citationId" class="cs-citation-chip"><el-button link type="primary" @click="showSource(turn, source)">[{{ source.citationId }}] {{ source.documentName }}</el-button></span></div>
            </div>
          </div>
        </div>

            <section v-if="streamPreview?.text && !session.turns.some(turn => turn.id === streamPreview.turnId)" class="cs-stream-answer" aria-label="正在生成的回答" aria-live="polite"><p>{{ streamPreview.text }}</p></section>
            <p v-if="streamPreview?.text" class="muted">正在返回已校验的回答…</p>
          </div>
          <footer class="cs-composer"><p v-if="atTurnLimit" class="muted" role="status">已达到本次咨询的问答轮数，可以结束后重新开始。</p><div class="cs-composer-top"><div v-if="speechEnabled" class="cs-mode-switch" aria-label="接待输入方式"><button type="button" :disabled="modeChanging" :class="{ active: inputMode === 'text' }" @click="selectInputMode('text')">文字</button><button type="button" :disabled="modeChanging" :class="{ active: inputMode === 'voice' }" @click="selectInputMode('voice')">语音</button></div><span>{{ voicePlaying ? '回答播报中' : conversationOn ? '连续语音进行中' : visitorClient ? '输入问题开始咨询' : '选择输入方式开始咨询' }}</span></div>
            <el-alert v-if="pending && !sending" title="发送结果尚未确认；刷新状态或重试同一请求，不会另建轮次。" type="warning" :closable="false" />
            <template v-if="inputMode === 'text'"><el-input v-model="message" type="textarea" :rows="3" maxlength="2000" show-word-limit :placeholder="allowTests ? '输入咨询问题，或描述要测试的能力…' : '输入你的问题…'" :disabled="conversationOn || sending || !!pending || !canInteract || atTurnLimit" aria-label="咨询问题" /><div class="cs-send-actions"><span>{{ visitorClient ? '回答依据服务方提供的资料' : '业务操作仍需确认' }}</span><div><el-button v-if="runningTurn && !conversationOn" @click="interrupt">停止回答</el-button><el-button type="primary" :loading="sending" :disabled="conversationOn || !canInteract || atTurnLimit || (!pending && !message.trim())" @click="send">{{ pending && !sending ? '重试同一请求' : '发送' }}</el-button></div></div></template>
            <p v-else class="cs-voice-input-hint">{{ conversationOn ? '正在使用语音，切换文字会先停止并释放录音与播报。' : '在人物区域开启连续对话，或选择短录音后核对文字。' }}</p>
          </footer>
        </section>
        <aside v-show="diagnosticOpen && allowTests" class="cs-diagnostic-pane" aria-label="测试诊断"><header class="cs-chat-heading"><h3>测试诊断</h3><el-button link @click="diagnosticOpen = false">收起</el-button></header><div class="cs-diagnostic-body"><h4>当前会话</h4><dl class="cs-diagnostic-values"><dt>会话状态</dt><dd>{{ session.status === 'ACTIVE' ? '进行中' : '已结束' }}</dd><dt>当前轮数</dt><dd>{{ session.turns.length }} / 50</dd><dt>回答状态</dt><dd>{{ sending || runningTurn ? '正在处理' : '等待输入' }}</dd><dt>语音状态</dt><dd>{{ voiceStatus }}</dd></dl><details class="cs-help"><summary>会话与发布标识</summary><p>{{ session.id }}</p><p>{{ session.releaseId }}</p></details>
          <h4>记录耗时</h4><el-button :loading="diagnosticLoading" size="small" @click="loadDiagnostics">读取本次记录</el-button><p v-if="diagnosticError" role="status" class="muted">{{ diagnosticError }}</p><dl class="cs-diagnostic-values"><dt>问答处理</dt><dd>{{ formatDiagnosticDuration(diagnosticRecord?.turns?.at(-1)?.elapsedMillis) }}</dd><dt>最近合成</dt><dd>{{ formatDiagnosticDuration(diagnosticRecord?.speech?.filter(item => item.kind === 'TTS').at(-1)?.elapsedMillis) }}</dd><dt>实际开声</dt><dd>未记录</dd></dl><p class="muted">合成不含播放，识别周期可能包含录音。读取记录不会重试任务。</p>
          <details class="cs-help"><summary>会话说明</summary><p class="muted">每会话最多50轮、30分钟。无流式能力时返回完整回答；未知结果沿用原请求核对，不自动补发。这里只运行无外部调用的工作流测试，真实订单尚未接入。</p></details>
          <CustomerOperationPanel v-if="allowTests" ref="operationPanel" :key="session.id" :session-id="session.id" :active="canInteract && !conversationOn" />
        </div></aside>
      </div>
      <el-dialog v-model="contactOpen" title="联系人工" class="customer-service-editor customer-contact-dialog" width="min(560px, 94vw)"><CustomerContactCard v-if="session.contact" :config="session.contact" /><template #footer><el-button @click="contactOpen = false">收起联系信息</el-button></template></el-dialog>
    </template>
    <el-dialog v-model="sourceOpen" :title="sourcePreview?.documentName || '资料来源'" class="customer-service-editor customer-source-dialog" width="min(760px, 94vw)"><template v-if="sourcePreview"><p v-if="sourcePreview.locations?.length" class="muted">{{ sourcePreview.locations.join('、') }}</p><pre class="source-text">{{ sourcePreview.text }}</pre><p v-if="sourcePreview.truncated" class="muted">当前显示本轮实际使用的资料片段，内容已截取。</p></template></el-dialog>
  </section>
</template>
<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {createCustomerVoiceConversation} from '@/utils/customerVoiceConversation'
import {createCustomerAnswerPlayback} from '@/utils/customerAnswerStream'
import {ElMessage} from 'element-plus'
import CustomerOperationPanel from './CustomerOperationPanel.vue'
import CustomerVoicePanel from './CustomerVoicePanel.vue'
import CustomerAvatar from './CustomerAvatar.vue'
import CustomerContactCard from './CustomerContactCard.vue'
import {customerAvatarState} from '@/utils/customerAvatarState'
import {
  customerIntentEntries,
  customerIntentLabel,
  customerIntentRunnable,
  customerMessageRequest,
  customerSnapshot,
  customerTurnHint,
  customerVisitorTurnHint
} from '@/utils/customerServiceState'
import {customerParameterHints, customerParameterLabel} from '@/utils/customerParameterState'
import {
  closeCustomerSession,
  createCustomerChannelSession,
  createCustomerSession,
  getCustomerRecord,
  getCustomerSession,
  getCustomerSource,
  interruptCustomerTurn,
  runCustomerIntent,
  streamCustomerMessage
} from '@/api/platform/customerService'

const props = defineProps({ appId: Number, serviceName: String, channelCode: String, visitorClient: Object, visitorVoiceEnabled: Boolean, maxTurns: { type: Number, default: 50 }, allowTests: { type: Boolean, default: true } })
const turnHint = turn => props.visitorClient
  ? customerVisitorTurnHint(turn)
  : customerTurnHint(turn)
const sessionApi = {
  get: (...args) => props.visitorClient ? props.visitorClient.get(...args) : getCustomerSession(...args),
  stream: (...args) => props.visitorClient ? props.visitorClient.stream(...args) : streamCustomerMessage(...args),
  close: (...args) => props.visitorClient ? props.visitorClient.close(...args) : closeCustomerSession(...args),
  interrupt: (...args) => props.visitorClient ? props.visitorClient.interrupt(...args) : interruptCustomerTurn(...args),
  source: (...args) => props.visitorClient ? props.visitorClient.source(...args) : getCustomerSource(...args)
}
const emit = defineEmits(['closed', 'unavailable'])
const session = ref(null), sending = ref(false), refreshing = ref(false), closing = ref(false)
const starting = ref(false), startError = ref(false)
const contactOpen = ref(false), contactBusy = ref(false)
const message = ref(''), pending = ref(null), sourceOpen = ref(false), sourcePreview = ref(null)
const operationPanel = ref(), confirmingIntent = ref(null), intentError = ref(null)
const now = ref(Date.now())
const voicePanel = ref(), voiceBusy = ref(false), voicePlaying = ref(false), devicePlayback = ref(false), playingCheck = ref(false)
const voiceReady = ref(false), voiceState = ref('OFF'), voiceHint = ref(''), pageVisible = ref(!document.hidden)
const speechState = ref('IDLE')
const avatarAvailable = ref(true), streamPreview = ref(null), streamPlaybackActive = ref(false)
const inputMode = ref('text'), voiceMode = ref('continuous'), diagnosticOpen = ref(false), modeChanging = ref(false)
const speechEnabled = computed(() => !props.visitorClient || props.visitorVoiceEnabled === true)
const stageStatus = computed(() => props.visitorClient && !speechEnabled.value ? sending.value || pending.value || runningTurn.value ? '正在准备回答' : '文字接待' : voiceState.value !== 'OFF' ? voiceStatus.value : voicePlaying.value ? '正在播报' : sending.value || pending.value || runningTurn.value ? '正在准备回答' : ({ REQUESTING: '正在开启麦克风', RECORDING: '正在录音', RECOGNIZING: '正在识别', SYNTHESIZING: '正在准备播报', UNKNOWN: '语音结果待核对' })[speechState.value] || (inputMode.value === 'voice' && voiceMode.value === 'short' ? '短录音输入' : '准备接待'))
const diagnosticRecord = ref(null), diagnosticLoading = ref(false), diagnosticError = ref('')
let diagnosticController
async function selectInputMode(mode) {
  if (modeChanging.value || mode === inputMode.value) return
  modeChanging.value = true
  try {
    if (voiceState.value !== 'OFF' || voiceBusy.value || voicePlaying.value) await voiceConversation.stop()
    if (!disposed) inputMode.value = mode
  } finally { modeChanging.value = false }
}
async function selectVoiceMode(mode) {
  if (modeChanging.value || mode === voiceMode.value) return
  modeChanging.value = true
  try {
    if (voiceState.value !== 'OFF' || voiceBusy.value || voicePlaying.value) await voiceConversation.stop()
    if (!disposed) voiceMode.value = mode
  } finally { modeChanging.value = false }
}
function toggleDiagnostics() { diagnosticOpen.value = !diagnosticOpen.value }
function formatDiagnosticDuration(value) { return Number.isFinite(value) && value >= 0 ? `${(value / 1000).toFixed(2)} 秒` : '未记录' }
async function loadDiagnostics() {
  if (diagnosticLoading.value || !session.value || !props.allowTests) return
  const id = session.value.id, controller = new AbortController()
  diagnosticController?.abort(); diagnosticController = controller
  diagnosticLoading.value = true; diagnosticError.value = ''; diagnosticRecord.value = null
  try {
    const value = (await getCustomerRecord(session.value.appId || props.appId, id, controller.signal)).data
    if (!disposed && session.value?.id === id && diagnosticController === controller) diagnosticRecord.value = value
  } catch { if (!disposed && session.value?.id === id && !controller.signal.aborted) diagnosticError.value = '记录暂不可用，请稍后重新读取。' }
  finally { if (diagnosticController === controller) diagnosticLoading.value = false }
}

let answerConnection, answerPlayback
let pollTimer, avatarExpiryTimer, sendGeneration = 0, voiceRequest, refreshTask
let disposed = false
const runningTurn = computed(() => session.value?.turns?.find(turn => turn.status === 'RUNNING'))
const canSend = computed(() => session.value?.status === 'ACTIVE' && new Date(session.value.expiresAt).getTime() > now.value && !runningTurn.value && !confirmingIntent.value)
const atTurnLimit = computed(() => !!props.visitorClient && !pending.value && session.value?.turns.length >= (props.maxTurns || 50))
const canInteract = computed(() => canSend.value && !voiceBusy.value)
const conversationOn = computed(() => !['OFF', 'PAUSED'].includes(voiceState.value))
const canStartVoice = computed(() => speechEnabled.value && !contactOpen.value && !contactBusy.value && pageVisible.value && voiceReady.value && session.value?.turns.length < (props.maxTurns || 50) && canInteract.value && !sending.value && !pending.value && !message.value.trim() && !voicePlaying.value)
const voiceStatus = computed(() => voiceState.value === 'LISTENING' && speechState.value === 'REQUESTING'
  ? '正在开启麦克风，请稍候'
  : ({ OFF: '连续语音未开启', LISTENING: '正在倾听与识别', THINKING: '正在准备回答', SPEAKING: '正在播报', STOPPING: '正在停止并释放麦克风', PAUSED: '连续语音已暂停' })[voiceState.value])
const avatarActive = computed(() => avatarAvailable.value && !!session.value && session.value.status === 'ACTIVE' && new Date(session.value.expiresAt).getTime() > now.value && pageVisible.value && !closing.value)
const avatarState = computed(() => customerAvatarState({ enabled: avatarActive.value, visible: pageVisible.value, closing: closing.value,
  speaking: voicePlaying.value, speechState: speechState.value, thinking: sending.value || !!pending.value || !!runningTurn.value || !!confirmingIntent.value, voiceState: voiceState.value }))
const voiceConversation = createCustomerVoiceConversation({
  listen: async current => {
    await nextTick()
    if (disposed || !current() || document.hidden) return { hint: '页面已暂停，请重新确认后继续。' }
    return voicePanel.value?.listen()
  },
  ask: askVoice,
  play: async (turn, current) => {
    await nextTick()
    if (disposed || !current() || document.hidden) return false
    return voicePanel.value?.playAndWait(turn.answer, turn.id)
  },
  cancel: stopVoiceResources,
  interrupt: () => !pending.value && !runningTurn.value && !sending.value,
  onState: (state, hint) => { voiceState.value = state; voiceHint.value = hint }
})
function startVoice() { if (!disposed && canStartVoice.value) voiceConversation.start() }
async function openContact() {
  if (!session.value?.contact || contactBusy.value || closing.value || disposed) return
  const id = session.value.id
  contactBusy.value = true
  try {
    await voiceConversation.stop()
    if (!disposed && !closing.value && session.value?.id === id) contactOpen.value = true
  } finally { if (!disposed) contactBusy.value = false }
}
async function askVoice(text, current, onSpeak) {
  await nextTick()
  if (disposed || !current() || document.hidden || !canSend.value || session.value.turns.length >= (props.maxTurns || 50) || sending.value || pending.value || message.value.trim()) throw new Error('会话或输入状态已变化，请核对文字后继续。')
  const id = session.value.id, request = customerMessageRequest(text, null), ticket = ++sendGeneration
  voiceRequest = request; pending.value = request; message.value = request.text; sending.value = true
  const connection = new AbortController()
  answerConnection = connection
  const valid = () => !disposed && current() && ticket === sendGeneration && session.value?.id === id && !document.hidden
  const queue = createCustomerAnswerPlayback({ current: valid, onSpeak, onFailure: () => connection.abort(),
    play: async (part, turnId, partNo) => { await nextTick(); return valid() && voicePanel.value?.playAndWait(part, turnId, partNo) },
    stop: () => voicePanel.value?.stopPlayback() })
  answerPlayback = queue
  streamPlaybackActive.value = true
  try {
    const completed = await sessionApi.stream(id, request.requestId, request.text, event => {
      if (!valid()) return
      acceptAnswerEvent(event)
      if (event.type === 'part') queue.push(event)
    }, connection.signal)
    if (!valid()) { queue.cancel(); return null }
    if (completed.status !== 'COMPLETED' || completed.errorCode) { queue.cancel(); await voicePanel.value?.cancel() }
    await refreshSession()
    const turn = session.value?.turns.find(turn => turn.requestId === request.requestId)
    const playback = queue.finish(turn || completed)
    if (!playback) streamPlaybackActive.value = false
    else playback.finally(() => { if (answerPlayback === queue) streamPlaybackActive.value = false })
    return turn && { ...turn, streamPlayback: playback }
  } catch {
    queue.cancel()
    if (answerPlayback === queue) streamPlaybackActive.value = false
    if (!disposed && current() && ticket === sendGeneration && session.value?.id === id) await refreshSession().catch(() => {})
    throw new Error('本轮受理或回答状态尚未确认，已暂停，请刷新核对，不会自动重发。')
  } finally {
    if (answerConnection === connection) answerConnection = null
    if (ticket === sendGeneration && session.value?.id === id) sending.value = false
  }
}
function acceptAnswerEvent(event) {
  if (event.type === 'accepted') streamPreview.value = { turnId: event.turnId, requestId: event.requestId, text: '' }
  else if (event.type === 'part' && streamPreview.value?.turnId === event.turnId) streamPreview.value.text += event.text
  else if (event.type === 'completed') streamPreview.value = null
}
async function stopVoiceResources() {
  answerPlayback?.cancel(); answerPlayback = null
  answerConnection?.abort(); answerConnection = null
  streamPlaybackActive.value = false; streamPreview.value = null
  voicePanel.value?.stopPlayback()
  await voicePanel.value?.cancel()
  const request = voiceRequest, id = session.value?.id
  if (!request || !id || disposed) return !pending.value && !runningTurn.value
  try {
    await refreshSession()
    if (session.value?.id !== id) return false
    const turn = session.value.turns.find(item => item.requestId === request.requestId)
    if (!turn) return false
    if (turn.status === 'RUNNING') { await sessionApi.interrupt(id, turn.id); await refreshSession() }
    const current = session.value?.turns.find(item => item.requestId === request.requestId)
    if (!current || current.status === 'RUNNING') return false
    sendGeneration++; sending.value = false
    if (pending.value?.requestId === request.requestId) pending.value = null
    voiceRequest = null
    return true
  } catch { return false }
}
function visibilityChanged() { pageVisible.value = !document.hidden; if (!pageVisible.value && conversationOn.value) voiceConversation.pause() }
watch(() => session.value?.expiresAt, expiry => {
  clearTimeout(avatarExpiryTimer)
  if (expiry) avatarExpiryTimer = setTimeout(() => { now.value = Date.now() }, Math.max(0, new Date(expiry).getTime() - Date.now()) + 1)
})
watch([canSend, sending], () => {
  if (conversationOn.value && voiceState.value !== 'STOPPING' && (!session.value || session.value.status !== 'ACTIVE' || new Date(session.value.expiresAt).getTime() <= now.value)) voiceConversation.stop()
})

async function playAnswer(turn) {
  if (conversationOn.value || !canInteract.value || sending.value || pending.value || playingCheck.value) return
  const id = session.value.id
  playingCheck.value = true
  try {
    await refreshSession()
    if (disposed || session.value?.id !== id || session.value.turns.at(-1)?.id !== turn.id) return
    const current = session.value.turns.find(item => item.id === turn.id)
    if (current?.status === 'COMPLETED' && current.answer && !current.errorCode) await voicePanel.value?.play(current.answer, current.id)
  } finally { playingCheck.value = false }
}
async function start() {
  if (starting.value || disposed) return
  starting.value = true
  startError.value = false
  contactOpen.value = false
  try {
    const created = (await (props.visitorClient ? props.visitorClient.create() : props.channelCode ? createCustomerChannelSession(props.channelCode) : createCustomerSession(props.appId))).data
    if (disposed) { sessionApi.close(created.id).catch(() => {}); return }
    session.value = created
    now.value = Date.now()
    startPolling()
  } catch { startError.value = true }
  finally { starting.value = false }
}
function startPolling() {
  clearTimeout(pollTimer)
  if (disposed || !session.value) return
  pollTimer = setTimeout(async () => {
    now.value = Date.now()
    if (disposed || !session.value) return
    try { if (props.channelCode || props.visitorClient || sending.value || pending.value || runningTurn.value || voiceBusy.value || voicePlaying.value) await refreshSession() }
    catch { clearTimeout(pollTimer); return }
    startPolling()
  }, (props.channelCode || props.visitorClient) && !sending.value && !pending.value && !runningTurn.value && !voiceBusy.value && !voicePlaying.value ? 10000 : 2000)
}
async function refreshSession() {
  if (refreshTask) return refreshTask
  if (!session.value) return
  refreshing.value = true
  const id = session.value.id
  refreshTask = (async () => {
    let value
    try { value = (await sessionApi.get(id)).data }
    catch (error) {
      avatarAvailable.value = false
      if (conversationOn.value && voiceState.value !== 'STOPPING') voiceConversation.pause()
      voicePanel.value?.stopPlayback()
      if (voiceBusy.value) voicePanel.value?.cancel().catch(() => {})
      if (props.channelCode || props.visitorClient) {
        sessionApi.close(id).catch(() => {})
        session.value = null
        sourcePreview.value = null
        sourceOpen.value = false
        emit('unavailable')
      }
      throw error
    }
    if (disposed || session.value?.id !== id) return
    avatarAvailable.value = true
    const merged = customerSnapshot(session.value, value, pending.value)
    session.value = merged.session
    if (streamPreview.value && value.turns.some(turn => turn.id === streamPreview.value.turnId && turn.status !== 'RUNNING')) streamPreview.value = null
    pending.value = merged.pending
    if (merged.accepted) message.value = ''
  })()
  try { return await refreshTask } finally { refreshing.value = false; refreshTask = null }
}
async function send() {
  if (conversationOn.value || sending.value || !canInteract.value || atTurnLimit.value) return
  voicePanel.value?.stopPlayback()
  const request = customerMessageRequest(message.value, pending.value)
  if (!request.text) return
  pending.value = request
  sending.value = true
  const id = session.value.id, ticket = ++sendGeneration
  try {
    const connection = new AbortController(); answerConnection = connection
    await sessionApi.stream(id, request.requestId, request.text, event => {
      if (!disposed && ticket === sendGeneration && session.value?.id === id) acceptAnswerEvent(event)
    }, connection.signal)
    if (answerConnection === connection) answerConnection = null
    if (!disposed && ticket === sendGeneration && session.value?.id === id) { message.value = ''; pending.value = null; await refreshSession() }
  } catch {
    if (!disposed && ticket === sendGeneration && session.value?.id === id) {
      streamPreview.value = null; answerConnection = null
      await refreshSession().catch(() => {})
    }
  } finally { if (ticket === sendGeneration && session.value?.id === id) sending.value = false }
}
async function interrupt() {
  if (!runningTurn.value) return
  await sessionApi.interrupt(session.value.id, runningTurn.value.id)
  answerConnection?.abort(); answerConnection = null; streamPreview.value = null
  pending.value = null
  await refreshSession()
}
async function runIntent(turn) {
  if (conversationOn.value || confirmingIntent.value || !canInteract.value || sending.value || pending.value || !customerIntentRunnable(turn, session.value.turns)) return
  voicePanel.value?.stopPlayback()
  const id = session.value.id
  confirmingIntent.value = turn.requestId
  intentError.value = null
  try {
    const result = (await runCustomerIntent(id, turn.requestId)).data
    if (disposed || session.value?.id !== id) return
    if (result.state !== 'ACCEPTED') intentError.value = { requestId: turn.requestId, message: result.state === 'INPUT_INVALID'
      ? result.issues.map(issue => `${issue.label}：${issue.message}`).join(' ') : '工作流未受理，请检查参数约束和运行准入；可继续对话修改参数。' }
    else ElMessage.success('测试任务已受理')
  } catch {
    if (!disposed && session.value?.id === id) intentError.value = { requestId: turn.requestId, message: '受理状态尚未确认，请刷新核对；再次点击将沿用同一请求，不会另建任务。' }
  } finally {
    if (!disposed && session.value?.id === id) {
      await refreshSession().catch(() => {})
      await operationPanel.value?.reload()
    }
    if (confirmingIntent.value === turn.requestId) confirmingIntent.value = null
  }
}
async function endSession() {
  if (!session.value) return
  closing.value = true
  await voiceConversation.stop()
  voicePanel.value?.stopPlayback()
  voicePanel.value?.cancel().catch(() => {})
  try {
    await sessionApi.close(session.value.id)
  } catch {
    ElMessage.warning('已关闭本地预览，服务端关闭状态尚未确认，将按会话时限结束。')
  } finally {
    clearTimeout(pollTimer)
    pending.value = null
    emit('closed')
    session.value = null
    confirmingIntent.value = null
    sending.value = false
    closing.value = false
  }
}
async function showSource(turn, source) {
  sourcePreview.value = null
  sourcePreview.value = (await sessionApi.source(session.value.id, turn.requestId, source.citationId)).data
  sourceOpen.value = true
}
// 关闭宿主 iframe 不会触发 Vue 卸载钩子；页面离开时也要尽力关闭访客凭证。
function visitorPageExit() {
  if (!props.visitorClient || disposed) return
  disposed = true; sendGeneration++; answerConnection?.abort()
  voiceConversation.stop(); voicePanel.value?.stopPlayback(); voicePanel.value?.cancel().catch(() => {})
  clearTimeout(pollTimer); clearTimeout(avatarExpiryTimer)
  if (session.value?.id) sessionApi.close(session.value.id).catch(() => {})
}
function visitorPageRestore(event) {
  if (event.persisted && props.visitorClient) emit('unavailable')
}
onMounted(() => {
  document.addEventListener('visibilitychange', visibilityChanged)
  if (props.visitorClient) {
    window.addEventListener('pagehide', visitorPageExit)
    window.addEventListener('pageshow', visitorPageRestore)
  }
  start()
})
onBeforeUnmount(() => {
  disposed = true; diagnosticController?.abort(); sendGeneration++; answerConnection?.abort(); answerPlayback?.cancel(); voiceConversation.stop()
  document.removeEventListener('visibilitychange', visibilityChanged)
  if (props.visitorClient) {
    window.removeEventListener('pagehide', visitorPageExit)
    window.removeEventListener('pageshow', visitorPageRestore)
  }
  clearTimeout(pollTimer)
  clearTimeout(avatarExpiryTimer)
  if (session.value?.id) sessionApi.close(session.value.id).catch(() => {})
})
</script>

