<template>
  <div v-loading="loading" class="agent-mcp-bindings">
    <el-alert title="协作工具仅供工作流 Agent 使用" type="info" :closable="false"
      description="最多绑定 6 个已审批的只读 MCP 工具，每次运行最多选择 2 项独立查询。普通对话和原内部工具配置单独管理。" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="binding-gap" />
    <el-button v-if="error && !loaded" class="binding-gap" @click="load">重试加载</el-button>
    <template v-if="loaded">
      <div class="binding-toolbar">
        <span>配置修订 {{ revision }} · {{ rows.length }}/6 个工具</span>
        <el-button :disabled="saving" @click="load">刷新配置</el-button>
        <el-button type="primary" :disabled="saving || rows.length >= 6 || !tools.length" @click="add">添加工具</el-button>
      </div>
      <el-empty v-if="!rows.length" description="尚未绑定协作查询工具" :image-size="64" />
      <el-form v-for="(row, index) in rows" :key="row.key" label-position="top" class="binding-card">
        <div class="binding-toolbar"><strong>tool_{{ index + 1 }}</strong><el-button type="danger" link :disabled="saving" @click="rows.splice(index, 1)">移除</el-button></div>
        <el-alert v-if="row.available === false" title="原绑定已失效，请重新选择当前审批版本后保存" type="warning" :closable="false" show-icon />
        <el-button v-if="row.available === false && tools.some(t => t.snapshot.toolId === row.toolId)" :disabled="saving"
          @click="row.refreshSnapshot = true; row.available = true">采用当前审批版本</el-button>
        <el-form-item label="已审批工具">
          <el-select v-model="row.toolId" :disabled="saving" filterable placeholder="选择工具" @change="row.available = true">
            <el-option v-for="tool in tools" :key="tool.snapshot.toolId" :value="tool.snapshot.toolId" :label="tool.label" />
            <el-option v-if="row.toolId && !tools.some(t => t.snapshot.toolId === row.toolId)" :value="row.toolId" :label="`工具 #${row.toolId}（不可用）`" disabled />
          </el-select>
        </el-form-item>
        <el-form-item label="用途说明（提供给规划模型）"><el-input v-model="row.purpose" :disabled="saving" maxlength="512" placeholder="例：查询公开产品说明" /></el-form-item>
        <el-form-item label="外发查询参数名"><el-input v-model="row.queryArgument" :disabled="saving" maxlength="64" placeholder="例：query；留空表示只使用固定参数" /></el-form-item>
        <el-form-item label="固定参数 JSON"><el-input v-model="row.fixedArgumentsJson" :disabled="saving" type="textarea" :rows="3" placeholder='{"limit": 5}' /></el-form-item>
        <el-collapse><el-collapse-item title="查看工具输入 Schema"><pre>{{ schema(row.toolId) }}</pre></el-collapse-item></el-collapse>
      </el-form>
      <el-button type="primary" :loading="saving" :disabled="loading || !dirty" @click="save">保存协作绑定</el-button>
      <el-divider />
      <el-form label-position="top" @submit.prevent="preflight">
        <el-form-item label="预检用的外发查询文本"><el-input v-model="externalQuery" :disabled="checking" maxlength="4000" placeholder="只用于本地参数校验，不发送给模型或远端工具" /></el-form-item>
        <el-button :loading="checking" :disabled="saving || loading || dirty || (rows.length > 0 && !externalQuery.trim())" @click="preflight">本地预检</el-button>
        <span class="binding-note">{{ dirty ? '先保存绑定，再执行预检。' : '检查当前权限、版本、参数和模型能力，不发起真实调用。' }}</span>
      </el-form>
      <el-alert v-if="ready" class="binding-gap" title="本地预检通过" type="success" :closable="false"
        description="当前配置可在协作智能体 2.0 节点中确认并发布；预检不保证远端服务此后仍可用。" show-icon />
    </template>
  </div>
</template>

<script setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {getAgentMcpBindings, preflightAgentMcpBindings, saveAgentMcpBindings} from '@/api/ai/agent'
import {bindingPayload, bindingsChanged} from './agentBindings'

const props = defineProps({agentId: {type: [Number, String], required: true}})
const emit = defineEmits(['saved'])
const rows = ref([]), tools = ref([]), revision = ref(null), baseline = ref('')
const loaded = ref(false), loading = ref(false), saving = ref(false), checking = ref(false)
const error = ref(''), externalQuery = ref(''), ready = ref(false)
let generation = 0, nextKey = 0
const dirty = computed(() => bindingsChanged(rows.value, baseline.value))
watch([rows, externalQuery], () => {ready.value = false}, {deep: true})
function apply(data) {
  if (!data || !Array.isArray(data.bindings) || !Array.isArray(data.tools) || !Number.isSafeInteger(data.revision)) throw new Error('配置响应无效，请更新后端并执行新增结构脚本')
  revision.value = data.revision; tools.value = data.tools
  rows.value = data.bindings.map(({binding, available}) => ({...binding, toolId: binding.snapshot.toolId, available, key: ++nextKey}))
  baseline.value = JSON.stringify(bindingPayload(rows.value)); loaded.value = true; ready.value = false
}
async function load() {
  const current = ++generation; loading.value = true; loaded.value = false; error.value = ''; ready.value = false
  try {
    const res = await getAgentMcpBindings(props.agentId)
    if (current !== generation) return
    if (res.code !== 200) throw new Error(res.msg || '加载协作工具失败')
    apply(res.data)
  } catch (e) {if (current === generation) error.value = e.message || '加载失败，请确认新增 SQL 已执行且后端已更新'}
  finally {if (current === generation) loading.value = false}
}
function add() {rows.value.push({key: ++nextKey, toolId: null, purpose: '', queryArgument: '', fixedArgumentsJson: '{}', available: true})}
function schema(id) {return tools.value.find(tool => tool.snapshot.toolId === id)?.inputSchemaJson || '当前工具不可用'}
async function save() {
  const current = generation; saving.value = true; error.value = ''; ready.value = false
  try {
    const res = await saveAgentMcpBindings(props.agentId, {revision: revision.value, bindings: bindingPayload(rows.value)})
    if (current !== generation) return
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    apply(res.data); emit('saved'); ElMessage.success('协作绑定已保存')
  } catch (e) {if (current === generation) error.value = e.message || '保存失败；配置可能已变化，请刷新后重试'}
  finally {saving.value = false}
}
async function preflight() {
  const current = generation; checking.value = true; error.value = ''; ready.value = false
  const checkedRevision = revision.value, checkedQuery = externalQuery.value
  try {
    const res = await preflightAgentMcpBindings(props.agentId, {revision: revision.value, externalQuery: externalQuery.value})
    if (current !== generation) return
    if (res.code !== 200 || res.data?.status !== 'LOCAL_READY') throw new Error(res.msg || '本地预检未通过')
    ready.value = !dirty.value && revision.value === checkedRevision && externalQuery.value === checkedQuery
  } catch (e) {if (current === generation) error.value = e.message || '预检失败，请核对当前绑定与参数'}
  finally {checking.value = false}
}
onBeforeUnmount(() => {++generation})
watch(() => props.agentId, load, {immediate: true})
</script>

<style scoped>
.binding-toolbar {display:flex;align-items:center;gap:12px;margin:16px 0;flex-wrap:wrap}
.binding-toolbar > span, .binding-toolbar > strong {margin-right:auto}
.binding-card {padding:16px;border:1px solid var(--el-border-color);border-radius:10px;margin-bottom:16px}
.binding-card .el-select {width:100%}
.binding-card pre {white-space:pre-wrap;overflow-wrap:anywhere;max-height:240px;overflow:auto}
.binding-gap {margin-top:16px}
.binding-note {display:inline-block;color:var(--el-text-color-secondary);margin:8px 12px;line-height:1.6}
.agent-mcp-bindings :deep(.el-button--primary:hover), .agent-mcp-bindings :deep(.el-button--primary:focus-visible) {background:var(--el-color-primary-light-3);color:white}
</style>
