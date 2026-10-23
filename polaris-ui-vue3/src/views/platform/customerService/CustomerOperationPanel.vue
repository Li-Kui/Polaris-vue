<template>
  <section aria-label="工作流能力测试" class="customer-operation-panel">
    <el-divider content-position="left">工作流能力测试</el-divider>
    <p class="muted">仅运行无外部调用的工作流。填写参数后点击测试；身份参数由服务端填入，真实订单尚未接入。</p>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <el-button v-if="!ready" :loading="loading" @click="load">加载测试能力</el-button>
    <template v-else-if="capabilities.length">
      <el-select v-model="selected" :disabled="sending || !!pending" placeholder="选择要测试的能力" class="full-width" @change="choose" popper-class="customer-service-menu">
        <el-option v-for="item in capabilities" :key="item.code" :value="item.code" :label="item.name" :disabled="!item.available" />
      </el-select>
      <p v-if="capabilities.every(item => !item.available)" class="muted">当前绑定包含外部资源或版本不可用；此阶段不执行这些能力。</p>
      <el-form v-if="current" label-position="top" class="operation-form">
        <el-form-item v-for="parameter in current.inputs" :key="parameter.name" :label="customerParameterLabel(parameter)" :required="parameter.required">
          <el-select v-if="customerParameterChoices(parameter).length" v-model="values[parameter.name]" :disabled="sending || !!pending || !active" clearable class="full-width" placeholder="选择允许的值" popper-class="customer-service-menu"><el-option v-for="choice in customerParameterChoices(parameter)" :key="String(choice.value)" :label="choice.label" :value="choice.value" /></el-select>
          <el-select v-else-if="parameter.type === 'boolean'" v-model="values[parameter.name]" :disabled="sending || !!pending || !active" clearable class="full-width" placeholder="选择是或否" popper-class="customer-service-menu"><el-option label="是" :value="true" /><el-option label="否" :value="false" /></el-select>
          <el-input v-else v-model="values[parameter.name]" :disabled="sending || !!pending || !active" :maxlength="parameter.type === 'string' ? 2000 : 100" :inputmode="parameter.type === 'string' ? 'text' : 'decimal'" />
          <p v-for="hint in customerParameterHints(parameter)" :key="hint" class="parameter-hint muted">{{ hint }}</p>
        </el-form-item>
      </el-form>
      <div class="operation-actions">
        <el-button type="primary" :loading="sending" :disabled="!active || !current || running && !pending" @click="start">{{ pending && !sending ? '重试同一任务' : '启动测试' }}</el-button>
        <el-button :disabled="refreshing" @click="refreshManually">刷新任务状态</el-button>
        <el-button v-if="pending" :disabled="sending || cancelling" @click="cancel(pending.requestId)">取消本次任务</el-button>
      </div>
    </template>
    <p v-else class="muted">此发布版本没有绑定测试能力。</p>
    <article v-for="operation in operations" :key="operation.id" class="operation-result">
      <strong>{{ operation.name }} · {{ customerOperationLabel(operation.status) }}</strong>
      <dl v-if="operation.result"><template v-for="(value, field) in operation.result" :key="field"><dt>{{ field }}</dt><dd>{{ value === null ? '—' : String(value) }}</dd></template></dl>
      <p v-if="operation.errorCode" class="muted">{{ operation.errorCode === 'OUTPUT_NOT_ALLOWED' ? '工作流已完成，但结果格式或大小不符合展示规则。' : '任务未正常完成，请检查工作流运行记录。' }}</p>
      <el-button v-if="customerOperationActive(operation)" :disabled="cancelling" @click="cancel(operation.requestId)">取消任务</el-button>
    </article>
  </section>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {
  cancelCustomerOperation,
  getCustomerOperation,
  listCustomerOperations,
  listCustomerSessionCapabilities,
  startCustomerOperation
} from '@/api/platform/customerService'
import {
  customerOperationActive,
  customerOperationBlocking,
  customerOperationLabel,
  customerOperationRequest,
  mergeCustomerOperation
} from '@/utils/customerOperationState'
import {customerParameterChoices, customerParameterHints, customerParameterLabel} from '@/utils/customerParameterState'

const props = defineProps({ sessionId: { type: String, required: true }, active: Boolean })
const capabilities = ref([]), operations = ref([]), selected = ref(''), values = ref({}), pending = ref(null)
const loading = ref(false), ready = ref(false), sending = ref(false), refreshing = ref(false), cancelling = ref(false), error = ref('')
const current = computed(() => capabilities.value.find(item => item.code === selected.value && item.available))
const running = computed(() => operations.value.some(customerOperationBlocking))
let disposed = false, timer

function choose() { values.value = {}; error.value = '' }
async function load() {
  if (loading.value || disposed) return
  loading.value = true
  try {
    const available = (await listCustomerSessionCapabilities(props.sessionId)).data || []
    const history = available.length ? (await listCustomerOperations(props.sessionId)).data || [] : []
    if (disposed) return
    capabilities.value = available
    operations.value = history
    ready.value = true
    error.value = ''
    schedule()
  } catch {
    if (!disposed) error.value = '测试能力或任务接口尚未就绪，或版本已不可用；请核对新增 SQL 和后端部署后重试。'
  } finally { if (!disposed) loading.value = false }
}
function accept(operation, requestId) {
  operations.value = mergeCustomerOperation(operations.value, operation, requestId)
  if (operation.requestId === pending.value?.requestId) pending.value = null
}
async function start() {
  if (sending.value || !props.active || !current.value || running.value && !pending.value) return
  let request
  try { request = customerOperationRequest(current.value, values.value, pending.value) }
  catch (invalid) { ElMessage.warning(invalid.message); return }
  pending.value = request
  sending.value = true
  error.value = ''
  try {
    const reply = (await startCustomerOperation(props.sessionId, request)).data
    if (disposed) return
    if (reply.state === 'ACCEPTED') accept(reply.operation, request.requestId)
    else if (reply.state === 'INPUT_REQUIRED' || reply.state === 'INPUT_INVALID' || reply.state === 'REJECTED') {
      pending.value = null
      error.value = reply.state === 'INPUT_REQUIRED' ? `请补齐参数：${reply.missingFields.map(name => current.value.inputs.find(item => item.name === name)?.label || name).join('、')}`
        : reply.state === 'INPUT_INVALID' ? reply.issues.map(issue => `${issue.label}：${issue.message}`).join(' ') : '工作流未受理，请检查输入约束、运行开关和并发额度后重试。'
    } else throw new Error('unknown operation state')
    schedule()
  } catch {
    if (!disposed) error.value = '任务受理结果尚未确认；刷新状态或重试同一任务，将沿用原请求和参数。'
  } finally { if (!disposed) sending.value = false }
}
async function refresh(all = true) {
  if (refreshing.value || disposed) return false
  const requests = pending.value ? [pending.value.requestId] : operations.value.filter(item => all || customerOperationActive(item)).map(item => item.requestId)
  if (!requests.length) return true
  refreshing.value = true
  try {
    for (const requestId of requests) {
      const value = (await getCustomerOperation(props.sessionId, requestId)).data
      if (disposed) return false
      accept(value, requestId)
    }
    error.value = ''
    return true
  } catch {
    if (!disposed) {
      operations.value = operations.value.map(item => ({ ...item, result: null, status: 'UNAVAILABLE' }))
      error.value = '未能核对任务状态，或当前资源已不可用；不会自动再次启动，请刷新或重试原请求。'
    }
    return false
  } finally { if (!disposed) refreshing.value = false }
}
async function refreshManually() { if (await refresh()) schedule() }
async function cancel(requestId) {
  if (cancelling.value || disposed) return
  cancelling.value = true
  try {
    await cancelCustomerOperation(props.sessionId, requestId)
    if (!disposed) { await refresh(); schedule() }
  } catch {
    if (!disposed) error.value = '取消状态尚未确认，请继续核对原任务。'
  } finally { if (!disposed) cancelling.value = false }
}
function schedule() {
  clearTimeout(timer)
  if (disposed || !pending.value && !operations.value.some(customerOperationActive)) return
  timer = setTimeout(async () => { if (await refresh(false)) schedule() }, 2000)
}
onMounted(load)
onBeforeUnmount(() => { disposed = true; clearTimeout(timer) })
defineExpose({ reload: load })
</script>

<style scoped>
.muted { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
.full-width { width: 100%; }
.operation-form { margin-top: 16px; }
.parameter-hint { width: 100%; margin: 4px 0 0; }
.operation-actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
.operation-result { margin: 12px 0; padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; overflow-wrap: anywhere; }
dl { display: grid; grid-template-columns: minmax(80px, 1fr) 2fr; gap: 8px; }
dd { margin: 0; white-space: pre-wrap; }
</style>
