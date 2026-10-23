<template>
  <section class="agent-chat-message" aria-label="协作运行与来源">
    <div class="agent-chat-message-heading">
      <strong>协作回答</strong>
      <span v-if="view"> · {{ chatStatusLabel(view) }}</span>
      <button type="button" :disabled="loading" :aria-expanded="expanded" @click="toggle">{{ loading ? '正在读取' : expanded ? '收起来源与运行状态' : '查看来源与运行状态' }}</button>
    </div>
    <div v-if="expanded">
      <p v-if="error" role="alert">{{ error }}</p>
      <template v-if="view">
        <p>{{ view.answerMode === 'CONTINUE_HISTORY' ? '基于前文回答，本轮无新查询' : '查询资料后回答' }} · 承接 {{ view.historyTurns }} 轮前文</p>
        <p v-if="view.errorCode">{{ chatErrorLabel(view.errorCode) }}</p>
        <p v-if="view.run">规划 {{ view.run.planCount || 0 }} 次 · 回答 {{ view.run.answerCount || 0 }} 次 · MCP {{ view.run.toolCount || 0 }} 次
          <span v-if="tokens != null"> · {{ tokens }} tokens</span></p>
        <p v-if="view.status === 'NEEDS_ATTENTION'">原请求结果不确定，请核对运行与调用记录，不会自动重试。</p>
      </template>
      <template v-if="sources">
        <p v-if="sources.knowledge?.length">本轮知识来源：请查看上方参考资料。</p>
        <div v-for="tool in sources.tools" :key="tool.invocationId" class="agent-source">
          <strong>[T{{ tool.callNo }}] 本轮查询 · {{ tool.toolName || 'MCP 工具' }}</strong>
          <p>{{ tool.connectionName || '' }} · {{ formatTime(tool.startedTime) }} · 已完成</p>
        </div>
        <div v-for="old in sources.history" :key="`${old.reference.messageId}:${old.reference.citationId}`" class="agent-source">
          <strong>[H{{ old.reference.messageId }}:{{ old.reference.citationId }}] 历史来源</strong>
          <p v-if="!old.available">来源已失效或当前不可访问，不以新资料替代原依据。</p>
          <template v-else-if="old.tool">
            <p>{{ old.tool.toolName || 'MCP 工具' }} · 原查询时间 {{ formatTime(old.tool.startedTime) }}</p>
            <p>来自前文，未重新查询；仅保留调用记录，无原始返回正文预览。</p>
          </template>
          <template v-else>
            <button v-for="doc in old.knowledge" :key="doc.docId" type="button" @click="openHistorical(old, doc)">{{ doc.docName || '历史知识片段' }} · 查看原片段</button>
          </template>
        </div>
        <p v-if="!sources.knowledge?.length && !sources.tools?.length && !sources.history?.length">本条回答没有可展示的来源记录。</p>
      </template>
      <p>历史来源不代表本轮实时查询；编号与来源对应不等于结论已核实。</p>
      <button type="button" :disabled="loading" @click="load">重新核对状态与来源</button>
    </div>
  </section>
</template>
<script setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {getAgentChatRun, getAgentChatSources} from '@/api/ai/agentChat'
import {chatErrorLabel, chatStatusLabel} from './agentChat'

const props = defineProps({conversationId: [Number, String], message: Object})
const emit = defineEmits(['sources', 'open-history'])
const expanded = ref(false), loading = ref(false), error = ref(''), sources = ref(null), view = ref(null)
let version = 0
const tokens = computed(() => {
  const run = view.value?.run
  const values = [run?.planningUsage?.totalTokens, run?.answerUsage?.totalTokens].filter(n => Number.isFinite(n))
  return values.length ? values.reduce((sum, n) => sum + n, 0) : null
})
function formatTime(value) {return value ? String(value).replace('T', ' ') : '原查询时间未记录'}
function openHistorical(old, doc) {
  if (!old.available || !old.reference.citationId.startsWith('S')) return
  emit('open-history', {conversationId: props.conversationId, messageId: props.message.id,
    originId: old.reference.messageId, citationId: old.reference.citationId, documentName: doc.docName})
}
async function load() {
  if (!props.message.id || !props.message.agentRunId) return
  const current = ++version; loading.value = true; error.value = ''; sources.value = null; view.value = null
  const results = await Promise.allSettled([getAgentChatRun(props.message.agentRunId), getAgentChatSources(props.conversationId, props.message.id)])
  if (current !== version) return
  const metadata = results[0], evidence = results[1]
  if (metadata.status === 'fulfilled' && metadata.value.code === 200) view.value = metadata.value.data
  if (evidence.status === 'fulfilled' && evidence.value.code === 200) {
    sources.value = evidence.value.data; emit('sources', evidence.value.data)
  }
  if (!view.value || !sources.value) error.value = '部分状态或来源暂不可用，可能已删除或权限已变化；不会重新查询。'
  loading.value = false
}
function toggle() {expanded.value = !expanded.value; if (expanded.value) load()}
watch(() => [props.conversationId, props.message.id, props.message.agentRunId], () => {
  version++; sources.value = null; view.value = null; loading.value = false
  if (expanded.value) load()
})
onBeforeUnmount(() => {version++})
</script>
<style scoped>
.agent-chat-message {margin: 12px 0; padding: 10px 12px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); color: var(--el-text-color-primary); font-size: 12px; overflow-wrap: anywhere}
.agent-chat-message-heading {display: flex; flex-wrap: wrap; align-items: center; gap: 6px}
p {line-height: 1.8; margin: 6px 0; color: var(--el-text-color-regular)}
.agent-source {margin: 10px 0; padding: 8px; background: var(--el-fill-color-light); border-radius: 6px}
button {font: inherit; color: var(--el-text-color-primary) !important; background: var(--el-bg-color) !important; border: 1px solid var(--el-border-color) !important; border-radius: 6px; padding: 5px 8px; cursor: pointer}
button:not(:disabled):hover {color: var(--el-color-primary) !important; border-color: var(--el-color-primary) !important; background: var(--el-color-primary-light-9) !important}
button:focus-visible {outline: 2px solid var(--el-color-primary); outline-offset: 2px}
button:disabled {opacity: .5; cursor: default}
</style>
