<template>
  <section class="node-diagnostics" aria-label="节点尝试详情">
    <header><h3>{{ node.nodeName || node.nodeId }} · 尝试 {{ node.attemptNo ?? '未知' }}</h3><code>{{ node.nodeRunId }}</code></header>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="节点类型">{{ node.nodeType || '未取得发布版本元数据' }}</el-descriptions-item>
      <el-descriptions-item label="处理器版本">{{ node.handlerVersion || '未记录' }}</el-descriptions-item>
      <el-descriptions-item label="分支路径">{{ node.branchPath || '主路径' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ workflowStatusLabel(node.status) }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ node.startTime || '未记录' }}</el-descriptions-item>
      <el-descriptions-item label="结束时间">{{ node.finishTime || '尚未记录' }}</el-descriptions-item>
      <el-descriptions-item label="耗时">{{ duration == null ? '未取得完整时间' : `${duration} ms` }}</el-descriptions-item>
      <el-descriptions-item label="错误码">{{ node.errorCode || '未记录' }}</el-descriptions-item>
      <el-descriptions-item label="错误信息">{{ node.errorMessage || '未记录' }}</el-descriptions-item>
      <el-descriptions-item label="发布时的资源引用">
        <span v-if="!node.resources?.length">未提供资源引用</span>
        <ul v-else><li v-for="resource in node.resources" :key="`${resource.kind}:${resource.key}`">{{ resource.kind }} · {{ resource.key }}</li></ul>
      </el-descriptions-item>
    </el-descriptions>
    <p>节点输入未由当前诊断接口提供。可在“输入 / 输出”核对执行输入；节点输出沿用服务端脱敏视图。</p>
    <el-collapse><el-collapse-item title="查看节点输出" name="output"><pre>{{ prettyOutput }}</pre></el-collapse-item></el-collapse>
    <div class="node-actions"><el-button v-if="canViewMcpRecords" class="diagnostic-action" @click="$emit('mcp', node)">查看此尝试的 MCP 记录</el-button></div>
    <AgentRunDetails v-if="canViewMcpRecords && executionId && node.nodeType === 'agent' && node.handlerVersion === '2.0'"
      :execution-id="executionId" :node-run-id="node.nodeRunId" />
    <h4>此尝试的相关事件 · 已加载 {{ relatedEvents.length }} 条</h4>
    <p>只关联相同节点尝试 ID 的事件。已读取历史至 #{{ historyCursor }}，执行最新事件 #{{ latestSequence ?? '未知' }}；其他历史事件可继续加载。</p>
    <el-table :data="relatedEvents" size="small">
      <el-table-column prop="sequenceNo" label="#" width="70" />
      <el-table-column prop="eventType" label="事件" min-width="180" />
      <el-table-column prop="createTime" label="时间" min-width="170" />
    </el-table>
    <el-button class="diagnostic-action" @click="$emit('events', node)">查看事件载荷与历史</el-button>
  </section>
</template>
<script setup>
import {computed} from 'vue'
import {nodeDurationMs} from './workflowDiagnostics'
import {workflowStatusLabel} from './workflowStatus'
import AgentRunDetails from '@/components/agent/AgentRunDetails.vue'

const props = defineProps({node: {type: Object, required: true}, events: {type: Array, default: () => []},
  historyCursor: {type: Number, default: 0}, latestSequence: {type: Number, default: null}, canViewMcpRecords: Boolean,
  executionId: {type: String, default: ''}})
defineEmits(['mcp', 'events'])
const duration = computed(() => nodeDurationMs(props.node))
const relatedEvents = computed(() => props.events.filter(event => event.nodeRunId && event.nodeRunId === props.node.nodeRunId))
const prettyOutput = computed(() => {
  if (props.node.outputJson == null) return '未记录输出'
  try {return JSON.stringify(JSON.parse(props.node.outputJson), null, 2)} catch {return props.node.outputJson}
})
</script>
<style scoped>
.node-diagnostics {margin-top:20px; padding:16px; border:1px solid var(--el-border-color); border-radius:12px; overflow-wrap:anywhere}
.node-diagnostics h3 {margin:0 0 8px; font-size:16px}
.node-diagnostics header {margin-bottom:16px}
.node-diagnostics code,.node-diagnostics p {font-size:12px; line-height:1.7}
.node-diagnostics ul {padding-left:18px; margin:0}
.node-diagnostics pre {max-height:320px; overflow:auto; white-space:pre-wrap; overflow-wrap:anywhere}
.node-actions {margin:12px 0}
</style>
