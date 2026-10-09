<template>
  <div class="runtime-runner" :class="{ 'is-idle': status === 'idle', 'is-stacked': stacked }">
    <section class="runner-input">
      <div class="runner-heading"><h2>{{ inputTitle }}</h2><span>填写后即可运行</span></div>
      <p v-if="manifest.pageConfig?.description" class="runner-description">{{ manifest.pageConfig.description }}</p>
      <div v-if="pendingRecovery" class="runner-description" role="status">
        <p>本页有上次执行记录。先读取状态与结果，避免重复运行；不会重新调用模型。</p>
        <el-button @click="resume(pendingRecovery)">恢复上次执行</el-button>
      </div>
      <slot name="before-input" :input="input" :set-input-field="setInputField" :running="isActive" />
      <fieldset :disabled="isActive" :inert="isActive" class="runner-fields">
        <WorkflowExecutionInput v-model="input" :schema="manifest.inputSchema || emptySchema" :hidden-fields="hiddenFields" simple @validation="validation = $event" />
      </fieldset>
      <div class="runner-actions">
        <el-button type="primary" :disabled="!validation.valid || disabled || isActive || pendingRecovery || status === 'unavailable' || status === 'attention'" :loading="status === 'running'" @click="submit">
          {{ status === 'success' ? '重新运行' : manifest.pageConfig?.submitButtonText || submitText }}
        </el-button>
        <el-button v-if="isActive && execution?.executionId" :loading="cancelling" @click="cancel">取消执行</el-button>
      </div>
    </section>
    <section v-if="status !== 'idle'" class="runner-output" aria-live="polite" aria-busy="status === 'running'">
      <RuntimeProgress v-if="status === 'running'" :events="events" :status="status" />
      <template v-if="status === 'success'">
        <div class="runner-heading"><h2>{{ resultTitle }}</h2><span class="runner-success">已完成</span></div>
        <slot :result="result" :execution="execution" :submitted-input="submittedInput"><RuntimeResult :output="result" /></slot>
      </template>
      <el-alert v-if="['error', 'attention', 'disconnected', 'cancelled', 'unavailable'].includes(status)" :title="status === 'cancelled' ? '执行已取消，可调整输入后重新运行' : errorMessage"
        :type="status === 'error' ? 'error' : 'info'" :closable="false" show-icon />
      <el-button v-if="['disconnected', 'attention'].includes(status)" class="runner-recover" @click="resume(execution.executionId)">{{ status === 'attention' ? '读取确认后状态' : '恢复执行状态' }}</el-button>
      <div v-if="status === 'unavailable'" class="runner-recover">
        <p class="runner-description">无法确认原任务状态。清除记录不会取消原任务，也不会重新运行；请勿直接重复生成。</p>
        <el-button @click="clearUnavailable">清除本页记录</el-button>
      </div>
      <p v-if="isActive && errorMessage && status !== 'disconnected'" class="runner-description">{{ errorMessage }}</p>
    </section>
  </div>
</template>

<script setup>
import {ref} from 'vue'
import WorkflowExecutionInput from '@/components/workflow/WorkflowExecutionInput.vue'
import RuntimeProgress from './RuntimeProgress.vue'
import RuntimeResult from './RuntimeResult.vue'
import {useShareExecution} from './useShareExecution'
import {setRuntimeInputValue} from '@/utils/workflowInputMapping'

const props = defineProps({
  manifest: { type: Object, required: true },
  shareCode: { type: String, required: true },
  submitText: { type: String, default: '开始运行' },
  inputTitle: { type: String, default: '输入参数' },
  resultTitle: { type: String, default: '运行结果' },
  stacked: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  hiddenFields: { type: Array, default: () => [] }
})
const emit = defineEmits(['started', 'settled', 'completed'])
const input = ref({})
const setInputField = (name, value) => { input.value = setRuntimeInputValue(props.manifest.inputSchema, input.value, name, value) }
const submittedInput = ref({})
const validation = ref({ valid: true })
const emptySchema = { type: 'object', properties: {} }
const { status, execution, result, events, errorMessage, cancelling, isActive, pendingRecovery, clearUnavailable, run, resume, cancel } = useShareExecution(() => props.shareCode, {
  onStarted: payload => emit('started', payload),
  onSettled: payload => emit('settled', payload),
  onCompleted: payload => emit('completed', payload)
})
defineExpose({ resume, isActive })
const submit = () => {
  if (!validation.value.valid || props.disabled || isActive.value || status.value === 'attention') return
  if (pendingRecovery.value) return
  if (status.value === 'unavailable') return
  submittedInput.value = JSON.parse(JSON.stringify(input.value))
  run(submittedInput.value)
}
</script>

<style scoped>
.runtime-runner { display: grid; grid-template-columns: minmax(280px, 360px) minmax(0, 1fr); align-items: start; gap: 24px; max-width: 1200px; margin: 0 auto; padding: 36px 24px; }
.runtime-runner.is-idle, .runtime-runner.is-stacked { grid-template-columns: minmax(0, 1fr); max-width: 880px; }
.runner-input, .runner-output { min-width: 0; padding: 28px; border-radius: 20px; border: 1px solid var(--runtime-border, #dbe2ea); background: var(--runtime-surface, #fff); box-shadow: var(--runtime-shadow, 0 8px 30px rgb(15 23 42 / 4%)); }
.runner-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.runner-heading h2 { margin: 0; font-size: 20px; font-weight: 600; letter-spacing: -0.3px; color: var(--runtime-text, #1e293b); }
.runner-heading span { font-size: 12px; color: var(--runtime-secondary, #64748b); }
.runner-heading .runner-success { color: var(--workflow-success, #15803d); padding: 5px 10px; border-radius: 20px; background: var(--runtime-muted, #f1f5f9); }
.runner-description { margin: 0 0 16px; color: var(--runtime-secondary, #64748b); line-height: 1.6; }
.runner-fields { border: 0; padding: 0; margin: 0; min-width: 0; }
.runner-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.runner-actions :deep(.el-button) { margin: 0; }
.runner-actions :deep(.el-button--primary) { flex: 1; }
.runner-recover { margin-top: 16px; }
.runtime-runner :deep(.workflow-execution-input) { min-height: 0; }
@media (max-width: 800px) { .runtime-runner { grid-template-columns: minmax(0, 1fr); padding: 14px; } .runner-input, .runner-output { padding: 18px; } }
</style>
