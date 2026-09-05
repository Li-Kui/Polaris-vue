<template>
  <el-dialog :model-value="modelValue" :title="`试运行子工作流 · ${contract?.name || ''}`" width="min(860px, 94vw)" append-to-body @update:model-value="$emit('update:modelValue', $event)">
    <template v-if="contract">
      <el-alert :title="contract.writes ? '测试环境中的真实执行，包含外部写入操作。请确认测试资源。' : '在测试环境运行已确认版本；可使用运行记录继续查看审批和等待进度。'" :type="contract.writes ? 'warning' : 'info'" :closable="false" />
      <p>这里验证子工作流本身；上游参数映射和完成分支请通过父工作流的完整运行验证。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <WorkflowExecutionInput v-if="!execution" v-model="input" :schema="contract.inputSchema || {}" @validation="validation = $event" />
      <div v-if="execution" class="child-test-result">
        <strong>{{ statusLabel }}</strong>
        <small>运行编号：{{ execution.executionId }}</small>
        <p v-if="execution.errorMessage">{{ execution.errorMessage }}</p>
        <p>关闭后任务仍可在工作流运行记录中查看。</p>
        <pre v-if="execution.outputJson">{{ outputText }}</pre>
        <el-button v-if="active" type="danger" plain @click="cancel">取消这次试运行</el-button>
      </div>
    </template>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button v-if="execution" :loading="loading" @click="refresh">刷新状态</el-button>
      <el-button v-if="execution && !active" @click="restart">再试一次</el-button>
      <el-button v-if="!execution" type="primary" :loading="loading" :disabled="!validation.valid || !contract" @click="start">开始试运行</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {ElMessageBox} from 'element-plus'
import WorkflowExecutionInput from './WorkflowExecutionInput.vue'
import {cancelWorkflowExecution, getWorkflowExecution, startWorkflowExecution} from '@/api/ai/workflow'

const props=defineProps({modelValue:Boolean,contract:Object})
defineEmits(['update:modelValue'])
const input=ref({}), validation=ref({valid:false}), execution=ref(null), loading=ref(false), error=ref('')
let timer, key, requestSnapshot
const active=computed(()=>execution.value && !['SUCCEEDED','FAILED','CANCELLED','REJECTED'].includes(execution.value.status))
const statusLabel=computed(()=>({QUEUED:'等待执行',RUNNING:'执行中',RECOVERING:'恢复中',WAITING_EVENT:'等待子流程或事件',WAITING_APPROVAL:'等待审批',NEEDS_ATTENTION:'需要人工处理',SUCCEEDED:'执行成功',FAILED:'执行失败',CANCELLED:'已取消',REJECTED:'未通过'})[execution.value?.status] || execution.value?.status)
const outputText=computed(()=>{try{return JSON.stringify(JSON.parse(execution.value.outputJson),null,2)}catch{return execution.value.outputJson}})
watch(()=>props.contract?.versionId,()=>{clearTimeout(timer);execution.value=null;input.value={};key=null;requestSnapshot=null;error.value=''})
watch(()=>props.modelValue,open=>{clearTimeout(timer);if(open && execution.value)refresh()})
onBeforeUnmount(()=>clearTimeout(timer))
async function start(){
  if(loading.value || !props.contract || !validation.value.valid)return
  if(props.contract.writes){try{await ElMessageBox.confirm('此次测试会执行子流程中的写入节点，是否使用当前 TEST 资源开始？','确认测试写入',{type:'warning'})}catch{return}}
  const snapshot=JSON.stringify(input.value)
  if(key && requestSnapshot!==snapshot){
    try{await ElMessageBox.confirm('上一次请求的启动结果尚未确认。更改输入会创建新执行，可能与上一次同时运行。请先检查运行记录，确认继续？','确认新执行',{type:'warning'})}catch{return}
  }
  loading.value=true
  error.value=''
  const selectedVersion=props.contract.versionId
  if(!key || requestSnapshot!==snapshot){key=crypto.randomUUID();requestSnapshot=snapshot}
  try{const response=await startWorkflowExecution({definitionId:props.contract.definitionId,workflowVersionId:selectedVersion,environment:'TEST',input:JSON.parse(snapshot),idempotencyKey:key});if(props.contract?.versionId!==selectedVersion)return;execution.value=response.data;await refresh()}
  catch(e){error.value=e.message || '启动失败；如网络中断，可用相同输入重试以恢复同一次执行'}finally{loading.value=false}
}
async function refresh(){
  if(!execution.value)return
  clearTimeout(timer)
  const executionId=execution.value.executionId
  try{const response=await getWorkflowExecution(executionId);if(execution.value?.executionId!==executionId)return;execution.value=response.data;error.value=''}
  catch(e){error.value='暂时无法获取状态，请点击刷新重试';return}
  if(props.modelValue && active.value)timer=setTimeout(refresh,2500)
}
async function cancel(){if(execution.value){await cancelWorkflowExecution(execution.value.executionId);await refresh()}}
function restart(){clearTimeout(timer);execution.value=null;key=null;requestSnapshot=null;error.value=''}
</script>
<style scoped>
.child-test-result{display:grid;gap:12px;padding-top:16px}.child-test-result pre{overflow:auto;max-height:360px;background:var(--el-fill-color-light);padding:16px;border-radius:8px}.child-test-result p{margin:0}
</style>
