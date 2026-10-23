<template>
  <section class="agent-run-details" v-loading="loading" aria-label="协作智能体运行台账">
    <header><h4>协作运行台账</h4><el-button :loading="loading" @click="load">刷新台账</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <p v-else-if="!loading && !run">尚无协作运行记录；节点可能尚未进入执行或在领取前被拒绝。</p>
    <template v-if="run">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="运行 ID">{{ run.runId }}</el-descriptions-item>
        <el-descriptions-item label="状态 / 阶段">{{ status(run.status) }} / {{ stage(run.stage) }}</el-descriptions-item>
        <el-descriptions-item label="调用预算">规划 {{ run.planCount ?? '未记录' }} 次 · 查询 {{ run.toolCount ?? '未记录' }}/{{ run.maxCalls ?? '未记录' }} 次 · 回答 {{ run.answerCount ?? '未记录' }} 次</el-descriptions-item>
        <el-descriptions-item label="规划用量">输入 {{ tokens(run.planningUsage?.inputTokens) }} · 输出 {{ tokens(run.planningUsage?.outputTokens) }} · 合计 {{ tokens(run.planningUsage?.totalTokens) }}</el-descriptions-item>
        <el-descriptions-item label="回答用量">输入 {{ tokens(run.answerUsage?.inputTokens) }} · 输出 {{ tokens(run.answerUsage?.outputTokens) }} · 合计 {{ tokens(run.answerUsage?.totalTokens) }}</el-descriptions-item>
        <el-descriptions-item label="错误码">{{ run.errorCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="派发证据">{{ dispatch(run.dispatch) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="run.calls || []" size="small">
        <el-table-column label="查询" width="70"><template #default="{row}">T{{ row.callNo }}</template></el-table-column>
        <el-table-column label="工具" width="100"><template #default="{row}">#{{ row.toolId }}</template></el-table-column>
        <el-table-column label="台账状态" width="110"><template #default="{row}">{{ status(row.status) }}</template></el-table-column>
        <el-table-column label="调用 ID" min-width="180"><template #default="{row}">{{ row.invocationId || '尚无终态关联' }}</template></el-table-column>
        <el-table-column label="派发证据" min-width="160"><template #default="{row}">{{ dispatch(row.dispatch) }}</template></el-table-column>
      </el-table>
      <p>原工具结果未保存。请结合此节点的 MCP 审计核对；已预留或已准备派发均不证明远端完成，刷新不会重新调用工具。</p>
    </template>
  </section>
</template>
<script setup>
import {onBeforeUnmount, ref, watch} from 'vue'
import {getAgentNodeRun} from '@/api/ai/agent'

const props = defineProps({executionId: {type: String, required: true}, nodeRunId: {type: String, required: true}})
const loading = ref(false), run = ref(null), error = ref('')
let sequence = 0
const tokens = value => value == null ? '未记录' : value
const status = value => ({RUNNING:'运行中', SUCCEEDED:'成功', FAILED:'失败', RESERVED:'已预留', SUCCESS:'成功'})[value] || value || '未记录'
const stage = value => ({KNOWLEDGE:'知识检索', PLAN:'规划', TOOL:'查询', ANSWER:'回答'})[value] || value || '未记录'
const dispatch = value => ({PREPARED:'已记录派发意图', NOT_DISPATCHED:'尚无派发意图记录', UNKNOWN:'结果未知'})[value] || '未记录'
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''; run.value = null
  try {
    const res = await getAgentNodeRun(props.executionId, props.nodeRunId)
    if (current !== sequence) return
    if (res.code !== 200) throw new Error(res.msg || '台账查询失败')
    if (res.data && (res.data.executionId !== props.executionId || res.data.nodeRunId !== props.nodeRunId)) throw new Error('台账响应范围不匹配')
    run.value = res.data || null
  } catch (e) {if (current === sequence) error.value = e.message || '台账加载失败，请更新后端'}
  finally {if (current === sequence) loading.value = false}
}
watch(() => [props.executionId, props.nodeRunId], load, {immediate: true})
onBeforeUnmount(() => {sequence++})
</script>
<style scoped>
.agent-run-details {margin-top:16px;padding:14px;border:1px solid var(--el-border-color);border-radius:10px;overflow-wrap:anywhere}
.agent-run-details header {display:flex;align-items:center;justify-content:space-between;gap:10px}
.agent-run-details p {font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}
</style>
