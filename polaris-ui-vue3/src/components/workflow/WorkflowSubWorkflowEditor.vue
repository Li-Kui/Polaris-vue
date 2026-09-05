<template>
  <div class="sub-workflow-editor">
    <section>
      <header><strong>1. 选择要运行的工作流</strong><el-button text type="primary" :loading="loading" @click="$emit('refresh')">刷新列表</el-button></header>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-select :model-value="config.definitionId" filterable :loading="loading" :disabled="disabled" placeholder="搜索已发布的工作流" @change="selectWorkflow">
        <el-option v-for="item in catalog" :key="item.definitionId" :value="item.definitionId" :label="item.name" :disabled="!!item.unavailableReason">
          <span>{{ item.name }}</span><small class="option-meta">{{ item.unavailableReason || `v${item.versionNo}` }}</small>
        </el-option>
      </el-select>
      <p v-if="!loading && !catalog.length">暂无可调用的工作流。请先创建并发布一个工作流，再刷新列表。</p>
      <template v-if="contract">
        <p>{{ contract.description || '运行选定工作流，并把返回结果提供给后续节点。' }}</p>
        <small>已确认 v{{ contract.versionNo }} · 父工作流发布后固定使用此版本</small>
        <div v-if="latest && latest.versionId !== config.reviewedVersionId">
          <el-alert title="有新的发布版本；更新前请确认输入输出变化" type="warning" :closable="false" />
          <el-button type="primary" plain :disabled="disabled" @click="selectWorkflow(config.definitionId)">使用新版本并重新配置输入</el-button>
        </div>
        <el-alert v-if="contract.writes" title="此工作流包含写入操作，试运行也可能修改外部数据" type="warning" :closable="false" />
      </template>
    </section>
    <section v-if="contract && !contract.unavailableReason">
      <header><strong>2. 提供输入</strong><el-button text type="primary" :disabled="disabled" @click="match">匹配同名字段</el-button></header>
      <WorkflowSubWorkflowInput label="子工作流输入" :schema="contract.inputSchema || {}" :model-value="config.inputs" :fields="fields" :disabled="disabled" required @update:model-value="change({inputs: $event})" />
      <el-alert v-if="issues.length" :title="issues[0]" type="warning" :closable="false" />
    </section>
    <section>
      <header><strong>3. 完成后怎么处理</strong></header>
      <el-select :model-value="config.resultMode || 'STOP'" :disabled="disabled" @change="changeMode">
        <el-option label="成功后继续，未完成时停止" value="STOP" />
        <el-option label="分别处理完成和未完成" value="BRANCH" />
        <el-option label="分别处理完成、未通过和执行失败" value="DETAILED" />
      </el-select>
      <label v-for="branch in ports" :key="branch.port" class="sub-branch"><span>{{ branch.label }}后进入</span><el-select :model-value="branchTargets[branch.port]" filterable clearable :disabled="disabled" placeholder="选择下一节点" @change="$emit('branch-target', {port: branch.port, target: $event})"><el-option v-for="target in targets" :key="target.id" :value="target.id" :label="target.name" /></el-select></label>
      <small>等待审批或人工处理时，父工作流会一起等待。</small>
    </section>
  </div>
</template>
<script setup>
import {computed} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import WorkflowSubWorkflowInput from './WorkflowSubWorkflowInput.vue'
import {autoMatchInputs, initialInput, inputIssues, subWorkflowPorts} from './workflowSubWorkflow'

const props = defineProps({config: {type:Object, default:()=>({})}, selectedContract:Object, catalog:{type:Array,default:()=>[]}, fields:{type:Array,default:()=>[]}, targets:{type:Array,default:()=>[]}, branchTargets:{type:Object,default:()=>({})}, disabled:Boolean, loading:Boolean, error:String})
const emit=defineEmits(['update:config','refresh','branch-target'])
const latest=computed(()=>props.catalog.find(x=>x.definitionId===props.config.definitionId))
const contract=computed(()=>props.selectedContract || (latest.value?.versionId === props.config.reviewedVersionId ? latest.value : null))
const ports=computed(()=>subWorkflowPorts(props.config.resultMode))
const issues=computed(()=>contract.value ? inputIssues(props.config.inputs, contract.value.inputSchema) : [])
const change=value=>emit('update:config',{...props.config,...value})
async function selectWorkflow(id) {
  if (props.config.definitionId && (props.config.definitionId !== id || latest.value?.versionId !== props.config.reviewedVersionId)) {
    try { await ElMessageBox.confirm('更换工作流或版本后，需要重新配置输入。现有输入配置将清除。', '确认更换', {type:'warning', confirmButtonText:'更换并重新配置', cancelButtonText:'保留当前配置'}) } catch { return }
  }
  const item=props.catalog.find(x=>x.definitionId===id)
  if (!item || item.unavailableReason) return
  change({definitionId:id, reviewedVersionId:item.versionId, inputs:initialInput(item.inputSchema || {type:'object'}), versionPolicy:'LATEST'})
}
async function changeMode(resultMode) {
  const removed = subWorkflowPorts(props.config.resultMode).filter(p=>!subWorkflowPorts(resultMode).some(n=>n.port===p.port) && props.branchTargets[p.port])
  if (removed.length) { try { await ElMessageBox.confirm(`切换后将移除${removed.map(p=>p.label).join('、')}的连线。`, '确认完成行为', {type:'warning'}) } catch { return } }
  change({resultMode})
}
async function match() {
  if(props.config.inputs?.mode && props.config.inputs.mode !== 'OBJECT') {
    try { await ElMessageBox.confirm('匹配同名字段会改为逐字段填写，并替换当前整份输入配置。是否继续？', '确认输入方式', {type:'warning'}) } catch { return }
  }
  const result=autoMatchInputs(contract.value.inputSchema || {},props.fields)
  if (!result.matched) { ElMessage.info('没有唯一且类型兼容的同名字段，请手动选择来源'); return }
  const fields={...(props.config.inputs?.fields || {})}
  Object.entries(result.tree.fields || {}).forEach(([key,value])=>{if(value.mode==='SOURCE' && (!fields[key] || fields[key].mode==='DEFAULT')) fields[key]=value})
  change({inputs:{mode:'OBJECT',fields}})
  ElMessage.success('已匹配未填写的同名字段，请检查输入')
}
</script>
<style scoped>
.sub-workflow-editor{display:grid;gap:16px}.sub-workflow-editor>section{padding:18px;border:1px solid var(--el-border-color);border-radius:12px;display:grid;gap:12px;background:var(--el-bg-color)}header{display:flex;align-items:center;justify-content:space-between;gap:12px}p{margin:0;color:var(--el-text-color-regular)}small{color:var(--el-text-color-secondary)}.option-meta{float:right;margin-left:18px}.sub-branch{display:grid;grid-template-columns:150px 1fr;align-items:center;gap:12px}.el-select{width:100%}@media(max-width:700px){.sub-branch{grid-template-columns:1fr}}
</style>
