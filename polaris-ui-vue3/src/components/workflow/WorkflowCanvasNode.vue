<template>
  <article :class="['workflow-node-card', `workflow-node-card--${tone}`, {selected: data.selected}]">
    <Handle v-if="!data.start" type="target" :position="Position.Left" class="workflow-node-handle" />
    <header>
      <span class="node-icon"><el-icon><component :is="icon" /></el-icon></span>
      <strong>{{ data.label }}</strong>
      <el-icon v-if="data.status === 'SUCCEEDED'" class="node-status node-status--success"><CircleCheck /></el-icon>
      <el-icon v-else-if="data.status === 'RUNNING'" class="node-status node-status--running"><Loading /></el-icon>
      <el-icon v-else-if="data.status === 'FAILED'" class="node-status node-status--failed"><CircleClose /></el-icon>
      <span v-else class="node-status-dot" />
    </header>
    <div v-if="!data.start && !data.end" class="node-content">
      <div><span>输入</span><small>{{ data.inputSummary || '对象' }}</small></div>
      <div><span>输出</span><small>{{ data.outputSummary || '结果对象' }}</small></div>
      <div v-if="data.resourceName" class="node-resource">
        <el-icon><Link /></el-icon><small>{{ data.resourceName }}</small>
      </div>
    </div>
    <div v-else class="node-terminal-copy">{{ data.start ? '流程起始节点' : '流程结束节点' }}</div>
    <Handle v-if="!data.end" type="source" :position="Position.Right" class="workflow-node-handle" />
  </article>
</template>

<script>
import {Handle, Position} from '@vue-flow/core'
import {
  ChatDotRound,
  CircleCheck,
  CircleClose,
  Coin,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Finished,
  Grid,
  Link,
  Loading,
  MagicStick,
  Operation,
  Refresh,
  Share,
  Switch,
  Timer,
  User,
  VideoPlay
} from '@element-plus/icons-vue'

export default {
  name: 'WorkflowCanvasNode',
  components: {
    Handle,
    ChatDotRound,
    CircleCheck,
    CircleClose,
    Coin,
    Collection,
    Connection,
    Cpu,
    DataAnalysis,
    Finished,
    Grid,
    Link,
    Loading,
    MagicStick,
    Operation,
    Refresh,
    Share,
    Switch,
    Timer,
    User,
    VideoPlay
  },
  props: {
    data: {
      type: Object,
      required: true
    }
  },
  data() {
    return {Position}
  },
  computed: {
    tone() {
      if (this.data.start) return 'start'
      if (this.data.end) return 'end'
      return this.data.category || 'general'
    },
    icon() {
      const icons = {
        llm: 'Cpu',
        agent: 'ChatDotRound',
        llm_classifier: 'MagicStick',
        knowledge_rag: 'Collection',
        http_get: 'Connection',
        http_request: 'Connection',
        database_query: 'Coin',
        condition: 'Switch',
        parallel: 'Share',
        join: 'Grid',
        loop: 'Refresh',
        wait: 'Timer',
        approval: 'User',
        transform: 'Operation',
        artifact: 'DataAnalysis',
        sub_workflow: 'Finished'
      }
      if (this.data.start) return 'VideoPlay'
      if (this.data.end) return 'Finished'
      return icons[this.data.type] || 'Operation'
    }
  }
}
</script>

<style scoped>
.workflow-node-card {
  width: 190px;
  overflow: visible;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 12px;
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  box-shadow: 0 8px 24px rgb(15 23 42 / 9%);
  color: var(--workflow-text, var(--el-text-color-primary));
}

.workflow-node-card.selected {
  border-color: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 0 0 3px var(--workflow-primary-soft, var(--el-color-primary-light-9)),
    0 10px 28px rgb(15 23 42 / 14%);
}

.workflow-node-card header {
  height: 42px;
  padding: 0 11px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--workflow-border, var(--el-border-color-lighter));
}

.workflow-node-card header strong {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.workflow-node-card--data .node-icon {
  color: #0a9f70;
  background: var(--el-color-success-light-9);
}

.workflow-node-card--control .node-icon {
  color: #e08a13;
  background: var(--el-color-warning-light-9);
}

.workflow-node-card--integration .node-icon {
  color: #1476d4;
  background: var(--el-color-primary-light-9);
}

.workflow-node-card--start .node-icon,
.workflow-node-card--end .node-icon {
  color: #0d9c69;
  background: var(--el-color-success-light-9);
}

.node-status {
  font-size: 15px;
}

.node-status--success {
  color: #13ad7a;
}

.node-status--running {
  color: #625bf6;
  animation: workflow-node-spin 1s linear infinite;
}

.node-status--failed {
  color: #ef5261;
}

.node-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.node-content {
  padding: 9px 11px 11px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.node-content > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.node-content span,
.node-content small,
.node-terminal-copy {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.node-content small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-resource {
  justify-content: flex-start !important;
  padding-top: 6px;
  border-top: 1px dashed var(--workflow-border, var(--el-border-color-lighter));
  color: var(--workflow-primary, var(--el-color-primary));
}

.node-terminal-copy {
  padding: 10px 12px 12px;
}

.workflow-node-handle {
  width: 9px;
  height: 9px;
  border: 2px solid var(--workflow-surface-raised, var(--el-bg-color-overlay));
  background: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 0 0 1px var(--workflow-primary, var(--el-color-primary));
}

:global(html.dark) .workflow-node-card {
  box-shadow: 0 10px 28px rgb(0 0 0 / 32%);
}

@keyframes workflow-node-spin {
  to { transform: rotate(360deg); }
}
</style>
