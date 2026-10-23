<template>
  <section class="problem-overview" aria-label="执行问题概览">
    <el-alert :title="overview.title" :type="overview.tone" :description="overview.description" :closable="false" show-icon />
    <div v-if="overview.executionReason && !overview.issues.length" class="execution-reason">
      <strong>{{ overview.executionReason.title }}</strong><p>{{ overview.executionReason.suggestion }}</p>
    </div>
    <article v-for="issue in overview.issues" :key="issue.node.nodeRunId" class="problem-item">
      <header><strong>{{ issue.node.nodeName || issue.node.nodeId }}</strong><el-tag size="small" type="info">{{ issue.category }}</el-tag><span>尝试 {{ issue.node.attemptNo ?? '未知' }}</span></header>
      <p>{{ issue.title }} <code>{{ issue.node.errorCode || '未记录错误码' }}</code></p>
      <p>{{ issue.suggestion }}</p>
      <el-button class="diagnostic-action" size="small" @click="$emit('inspect', issue.node)">查看此节点尝试</el-button>
    </article>
    <div class="overview-actions">
      <el-button class="diagnostic-action" @click="$emit('events')">查看事件记录</el-button>
      <el-button class="diagnostic-action" @click="$emit('summary')">预览诊断摘要</el-button>
    </div>
  </section>
</template>
<script setup>
import {computed} from 'vue'
import {problemOverview} from './workflowDiagnostics'

const props = defineProps({execution: {type: Object, required: true}, nodes: {type: Array, default: () => []}})
defineEmits(['inspect', 'events', 'summary'])
const overview = computed(() => problemOverview(props.execution, props.nodes))
</script>
<style scoped>
.problem-overview {margin-bottom:20px; min-width:0}
.problem-item,.execution-reason {margin-top:12px; padding:14px; border:1px solid var(--el-border-color); border-radius:10px; overflow-wrap:anywhere}
.problem-item header,.overview-actions {display:flex; align-items:center; flex-wrap:wrap; gap:10px}
.problem-item header span,.problem-item p,.execution-reason p {font-size:13px; line-height:1.7}
.problem-item code {font-size:12px; margin-left:8px}
.overview-actions {margin-top:12px}
.overview-actions .el-button {margin:0}
</style>
