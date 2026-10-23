<template>
  <section class="agent-chat-controls" aria-label="协作对话设置">
    <div class="agent-chat-row">
      <strong>协作对话</strong>
      <el-switch :model-value="modelValue.enabled" :disabled="busy" aria-label="启用协作对话" @change="change('enabled', $event)" />
      <span class="agent-chat-note">使用智能体的模型与已绑定工具</span>
    </div>
    <template v-if="modelValue.enabled">
      <div class="agent-chat-row">
        <el-select :model-value="modelValue.agentId" :disabled="busy" placeholder="选择协作智能体" aria-label="协作智能体" @change="change('agentId', $event)">
          <el-option v-for="agent in agents" :key="agent.id" :label="agent.agentName" :value="agent.id" />
        </el-select>
        <el-select :model-value="modelValue.answerMode" :disabled="busy" aria-label="本轮回答方式" @change="changeMode">
          <el-option label="查询资料后回答" value="QUERY_EVIDENCE" />
          <el-option label="基于前文继续回答" value="CONTINUE_HISTORY" />
        </el-select>
      </div>
      <p class="agent-chat-note">{{ modelValue.answerMode === 'CONTINUE_HISTORY' ? '本轮使用可用前文，不查询新资料或 MCP；仍会调用回答模型。' : '本轮使用所选知识库，并可单独开启 MCP 查询。' }}</p>
      <details>
        <summary>前文范围（最多 6 轮）</summary>
        <div class="agent-chat-row agent-chat-detail">
          <el-select :model-value="modelValue.contextStartMessageId" :disabled="busy" clearable placeholder="延续当前话题" aria-label="前文起点" @change="change('contextStartMessageId', $event || null)">
            <el-option v-for="turn in historyOptions" :key="turn.id" :value="turn.id" :label="turn.label || '协作轮次'" />
            <el-option v-if="latestMessageId" :value="latestMessageId" label="新话题：从本轮开始" />
          </el-select>
          <el-checkbox :model-value="modelValue.inheritPreviousContext" :disabled="busy" @change="change('inheritPreviousContext', $event)">承接切换智能体／资料前的协作前文</el-checkbox>
        </div>
        <p class="agent-chat-note">切换智能体、模型或知识库默认开始新段。仅承接已完成且来源仍可用的协作轮次，实际范围在发送前显示。</p>
      </details>
      <div v-if="modelValue.answerMode === 'QUERY_EVIDENCE'" class="agent-chat-detail">
        <el-checkbox :model-value="modelValue.allowExternalTools" :disabled="busy" @change="change('allowExternalTools', $event)">允许本轮 MCP 查询</el-checkbox>
        <el-input v-if="modelValue.allowExternalTools" :model-value="modelValue.externalQuery" :disabled="busy" type="textarea" :rows="2" :maxlength="4000" show-word-limit
                  placeholder="仅填写本轮允许发送给外部工具的查询内容" aria-label="本轮外发查询" @update:model-value="change('externalQuery', $event)" />
        <p v-if="modelValue.allowExternalTools" class="agent-chat-note">外发内容不会自动复制问题或前文；每轮成功后清空授权。</p>
      </div>
    </template>
    <div v-if="observed" class="agent-chat-recovery" role="status">
      <strong>{{ chatStatusLabel(observed) }}</strong>
      <span v-if="observed.errorCode"> · {{ chatErrorLabel(observed.errorCode) }}</span>
      <span v-if="observed.status === 'RUNNING'"> · {{ chatStageLabel(observed.run?.stage) }}；刷新只核对状态，不重执行</span>
      <span v-if="observed.status === 'PREPARED' && !confirmation"> · 尚未执行，可取消后重新确认</span>
      <button type="button" :disabled="busy" @click="emit('refresh')">刷新状态</button>
      <button v-if="isActiveChatRequest(observed)" type="button" :disabled="stopping" @click="emit('stop')">{{ stopping ? '正在停止' : '停止本轮' }}</button>
      <details v-if="observed.run">
        <summary>查看本轮运行记录</summary>
        <p>规划 {{ observed.run.planCount || 0 }} 次 · 回答 {{ observed.run.answerCount || 0 }} 次 · 已预留查询 {{ observed.run.toolCount || 0 }} 次</p>
        <p v-for="call in observed.run.calls" :key="call.callNo">
          查询 {{ call.callNo }}：{{ ({RESERVED: '已预留', SUCCESS: '已完成', FAILED: '失败'})[call.status] || '待核对' }}
          · {{ ({NOT_DISPATCHED: '未记录派发', PREPARED: '已记录派发意图', UNKNOWN: '派发结果待核对'})[call.dispatch] || '待核对' }}
        </p>
        <p class="agent-chat-note">预留或派发意图不代表查询成功；结果不确定的请求不会自动重试。</p>
      </details>
    </div>

    <el-dialog :model-value="Boolean(confirmation)" title="确认本轮协作" width="min(640px, 95vw)" append-to-body class="agent-chat-confirmation"
               :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <template v-if="confirmation">
        <p>智能体：{{ selectedAgent?.agentName || '所选智能体' }} · 回答模型：{{ modelName }}</p>
        <p>本轮{{ confirmation.input.answerMode === 'CONTINUE_HISTORY' ? '基于前文回答，不进行新查询' : '查询资料后回答' }}；{{ confirmation.view.historyTurns }} 轮前文实际入选。
          <span v-if="confirmation.view.historyTruncated">已裁剪较早前文。</span>
          <span v-if="confirmation.view.excludedTurns">{{ confirmation.view.excludedTurns }} 轮未完成或来源不可用，已排除。</span></p>
        <details><summary>查看实际承接的前文</summary>
          <p v-if="!confirmedHistory.length">本轮无前文</p>
          <p v-for="turn in confirmedHistory" :key="turn.id">{{ turn.content }}</p>
        </details>
        <p v-if="confirmation.input.answerMode === 'QUERY_EVIDENCE'">知识库：{{ knowledgeName || '未选择' }}</p>
        <p>本轮调用上限：{{ confirmation.input.allowExternalTools ? '1 次规划、2 次 MCP 查询、1 次回答' : '1 次回答' }}；最长 {{ confirmation.view.maxWaitSeconds }} 秒。真实请求可能产生费用。</p>
        <p class="agent-chat-note">问题</p><pre>{{ confirmation.input.question }}</pre>
        <template v-if="confirmation.input.allowExternalTools">
          <p>允许外发的查询</p><pre>{{ confirmation.input.externalQuery }}</pre>
          <p>可用工具（实际选择由本轮规划决定）：</p>
          <ul><li v-for="tool in confirmation.view.toolTargets" :key="tool.toolId">{{ tool.label || tool.alias }}：{{ tool.purpose }}</li></ul>
        </template>
        <p v-else>本轮不调用 MCP 工具。</p>
      </template>
      <template #footer>
        <el-button :disabled="stopping" @click="emit('dismiss')">返回修改</el-button>
        <el-button type="primary" :disabled="stopping" @click="emit('confirm')">确认发送</el-button>
      </template>
    </el-dialog>
  </section>
</template>
<script setup>
import {computed} from 'vue'
import {chatErrorLabel, chatHistoryOptions, chatStageLabel, chatStatusLabel, isActiveChatRequest} from './agentChat'

const props = defineProps({modelValue: Object, agents: {type: Array, default: () => []}, models: Array, messages: Array,
  busy: Boolean, stopping: Boolean, confirmation: Object, observed: Object, knowledgeName: String})
const emit = defineEmits(['update:modelValue', 'confirm', 'dismiss', 'refresh', 'stop'])
const historyOptions = computed(() => chatHistoryOptions(props.messages))
const latestMessageId = computed(() => [...(props.messages || [])].reverse().find(m => m.id && m.role === 'assistant')?.id)
const selectedAgent = computed(() => props.agents.find(a => String(a.id) === String(props.confirmation?.input.agentId || props.modelValue.agentId)))
const modelName = computed(() => props.models?.find(m => String(m.id) === String(props.confirmation?.view.modelId))?.name || '智能体绑定模型')
const confirmedHistory = computed(() => (props.messages || []).filter(m => props.confirmation?.view.historyMessageIds?.some(id => String(id) === String(m.id)) && m.role === 'user'))
function change(key, value) { emit('update:modelValue', {...props.modelValue, [key]: value, ...(key === 'allowExternalTools' && !value ? {externalQuery: ''} : {})}) }
function changeMode(value) {emit('update:modelValue', {...props.modelValue, answerMode: value, allowExternalTools: false, externalQuery: ''})}
</script>
<style scoped>
.agent-chat-controls {padding: 12px 16px; margin-bottom: 10px; border: 1px solid var(--el-border-color); border-radius: 12px; background: var(--el-bg-color); color: var(--el-text-color-primary); font-size: 13px; max-height: min(36vh, 320px); overflow: auto}
.agent-chat-row {display: flex; align-items: center; flex-wrap: wrap; gap: 12px}
.agent-chat-row :deep(.el-select) {width: 220px; max-width: 100%}
.agent-chat-note {color: var(--el-text-color-regular); font-size: 12px; line-height: 1.7; margin: 6px 0}
.agent-chat-detail {margin-top: 10px}
summary {cursor: pointer; line-height: 1.8}
.agent-chat-recovery {margin-top: 8px; line-height: 1.8; overflow-wrap: anywhere}
button {font: inherit; color: var(--el-text-color-primary) !important; background: var(--el-fill-color-light) !important; border: 1px solid var(--el-border-color) !important; border-radius: 6px; padding: 4px 8px; margin: 4px; cursor: pointer}
button:not(:disabled):hover {color: var(--el-color-primary) !important; border-color: var(--el-color-primary) !important}
button:focus-visible, summary:focus-visible {outline: 2px solid var(--el-color-primary); outline-offset: 2px}
button:disabled {opacity: .5; cursor: default}
</style>
<style>
html body .agent-chat-confirmation.el-dialog {
  --agent-chat-text: #334155;
  --agent-chat-bg: #fff;
  --agent-chat-accent: #4338ca;
  --agent-chat-soft: #eef2ff;
  background: var(--agent-chat-bg) !important; color: var(--agent-chat-text) !important; max-height: 88vh; overflow: auto;
}
html.dark body .agent-chat-confirmation.el-dialog {
  --agent-chat-text: #e2e8f0;
  --agent-chat-bg: #151b2d;
  --agent-chat-accent: #93c5fd;
  --agent-chat-soft: #1e293b;
}
html body .agent-chat-confirmation.el-dialog :is(.el-dialog__title, .el-dialog__body, .el-dialog__header, .el-dialog__footer) {color: var(--agent-chat-text) !important}
.agent-chat-confirmation pre {white-space: pre-wrap; overflow-wrap: anywhere; background: var(--el-fill-color-light); padding: 12px; border-radius: 8px; font: inherit}
html body .agent-chat-confirmation.el-dialog .el-button.el-button.el-button.el-button.el-button.el-button {
  color: var(--agent-chat-text) !important; background: var(--agent-chat-bg) !important; background-image: none !important;
  border: 1px solid var(--el-border-color) !important; box-shadow: none !important; -webkit-text-fill-color: currentColor !important;
}
html body .agent-chat-confirmation.el-dialog .el-button.el-button.el-button.el-button.el-button.el-button:not(:disabled):is(:hover,:focus-visible),
html body .agent-chat-confirmation.el-dialog .el-button--primary.el-button.el-button.el-button.el-button.el-button.el-button {
  color: var(--agent-chat-accent) !important; background: var(--agent-chat-soft) !important; border-color: var(--agent-chat-accent) !important;
}
html body .agent-chat-confirmation.el-dialog .el-button.el-button.el-button.el-button.el-button.el-button span {color: inherit !important; -webkit-text-fill-color: currentColor !important}
</style>
