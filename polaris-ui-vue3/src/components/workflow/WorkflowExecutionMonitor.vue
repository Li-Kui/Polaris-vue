<template>
  <div class="execution-monitor">
    <div class="monitor-header">
      <div class="monitor-heading">
        <el-button class="monitor-back" circle aria-label="返回工作流列表" title="返回工作流列表" @click="$emit('back')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="monitor-title-group">
          <span class="monitor-title">{{ workflowName }} · 运行记录</span>
          <span class="monitor-subtitle">{{ workflowCode }} · 仅显示当前工作流，实时追踪节点、事件与恢复状态</span>
        </div>
      </div>
      <el-button class="monitor-refresh" :loading="loading" @click="loadExecutions">
        <el-icon v-if="!loading"><Refresh /></el-icon>
        <span>刷新记录</span>
      </el-button>
    </div>

    <el-table v-loading="loading" :data="executions" class="polaris-el-table">
      <el-table-column prop="executionId" label="执行 ID" min-width="260">
        <template #default="{row}"><code>{{ row.executionId }}</code></template>
      </el-table-column>
      <el-table-column label="版本" width="80" align="center">
        <template #default="{row}">v{{ row.versionNo }}</template>
      </el-table-column>
      <el-table-column label="状态" width="150" align="center">
        <template #default="{row}">
          <span :class="['execution-status', `execution-status--${statusClass(row.status)}`]">
            <i></i>{{ row.status }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="canExecute && !terminal(row.status)" link type="danger" @click="cancel(row)">取消</el-button>
          <el-button v-if="canExecute && retryable(row.status)" link type="warning" @click="retry(row)">安全重试</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="该工作流暂无运行记录" :image-size="72" />
      </template>
    </el-table>

    <el-drawer
      v-model="drawerOpen"
      class="workflow-execution-drawer"
      size="min(1180px, 88%)"
      destroy-on-close
      @closed="stopEventStream"
    >
      <template #header>
        <div class="detail-heading">
          <div>
            <span class="detail-eyebrow">EXECUTION DETAIL</span>
            <strong>执行详情</strong>
          </div>
          <span v-if="selected" :class="['execution-status', `execution-status--${statusClass(selected.status)}`]">
            <i></i>{{ selected.status }}
          </span>
        </div>
      </template>
      <template v-if="selected">
        <div :class="['stream-state', {
          'stream-state--connected': streamConnected,
          'stream-state--terminal': terminal(selected.status)
        }]">
          <span class="stream-indicator"><i></i>{{ streamStateLabel }}</span>
          <span class="stream-sequence">已同步至事件 #{{ lastSequence }}</span>
        </div>
        <el-descriptions :column="2" border class="execution-summary">
          <el-descriptions-item label="执行 ID"><code>{{ selected.executionId }}</code></el-descriptions-item>
          <el-descriptions-item label="状态">
            <span :class="['execution-status', `execution-status--${statusClass(selected.status)}`]">
              <i></i>{{ selected.status }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="工作流">{{ selected.workflowCode }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ selected.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="父执行">{{ selected.parentExecutionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调用深度">{{ selected.executionDepth || 0 }}</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ selected.errorCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ selected.errorMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-tabs v-model="detailTab" class="detail-tabs">
          <el-tab-pane name="nodes">
            <template #label><span class="detail-tab-label">节点运行<em>{{ nodeRuns.length }}</em></span></template>
            <el-table :data="nodeRuns" size="small" class="detail-table">
              <el-table-column prop="nodeId" label="节点" min-width="130" />
              <el-table-column prop="attemptNo" label="Attempt" width="90" />
              <el-table-column label="状态" width="140">
                <template #default="{row}">
                  <span :class="['execution-status', `execution-status--${statusClass(row.status)}`]">
                    <i></i>{{ row.status }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="sideEffectStatus" label="副作用" width="140" />
              <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane name="events">
            <template #label><span class="detail-tab-label">事件日志<em>{{ events.length }}</em></span></template>
            <el-table :data="events" size="small" class="detail-table">
              <el-table-column prop="sequenceNo" label="#" width="70" />
              <el-table-column prop="eventType" label="事件" min-width="190" />
              <el-table-column prop="nodeId" label="节点" min-width="120" />
              <el-table-column prop="payloadJson" label="载荷" min-width="260" show-overflow-tooltip />
              <el-table-column prop="createTime" label="时间" width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane name="tools">
            <template #label><span class="detail-tab-label">工具调用<em>{{ toolCallCount }}</em></span></template>
            <el-empty v-if="!toolCalls.length" description="本次执行没有调用智能体工具" />
            <el-table v-else :data="toolCalls" size="small" class="detail-table">
              <el-table-column prop="sequenceNo" label="#" width="70" />
              <el-table-column prop="nodeId" label="节点" min-width="120" />
              <el-table-column prop="toolName" label="工具" min-width="180" />
              <el-table-column prop="callNo" label="调用序号" width="90" align="center" />
              <el-table-column label="结果" width="110" align="center">
                <template #default="{row}">
                  <el-tag :type="toolEventType(row.status)" size="small" effect="plain">
                    {{ toolEventStatus(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="耗时" width="100" align="right">
                <template #default="{row}">{{ row.status === 'STARTED' ? '-' : `${row.durationMs} ms` }}</template>
              </el-table-column>
              <el-table-column label="说明" min-width="180">
                <template #default="{row}">
                  <span v-if="row.reasonCode">{{ toolEventReason(row.reasonCode) }}</span>
                  <el-tag v-if="row.resultTruncated" type="warning" size="small" effect="plain">
                    结果已截断
                  </el-tag>
                  <span v-if="!row.reasonCode && !row.resultTruncated">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane name="artifacts">
            <template #label><span class="detail-tab-label">执行产物<em>{{ artifacts.length }}</em></span></template>
            <el-empty v-if="!artifacts.length" description="当前执行没有持久化产物" />
            <el-table v-else :data="artifacts" size="small">
              <el-table-column prop="fileName" label="文件名" min-width="220" />
              <el-table-column prop="mimeType" label="类型" min-width="150" />
              <el-table-column label="大小" width="110">
                <template #default="{row}">{{ formatBytes(row.sizeBytes) }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="生成时间" width="180" />
              <el-table-column prop="expiresTime" label="到期时间" width="180" />
              <el-table-column label="操作" width="100" align="center">
                <template #default="{row}">
                  <el-button link type="primary" icon="Download" @click="downloadArtifact(row)">
                    下载
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="输入 / 输出" name="io">
            <div class="json-grid">
              <div><strong>输入</strong><pre>{{ prettyJson(selected.inputJson) }}</pre></div>
              <div><strong>输出</strong><pre>{{ prettyJson(selected.outputJson) }}</pre></div>
              <div><strong>执行预算</strong><pre>{{ prettyJson(selected.budgetJson) }}</pre></div>
              <div><strong>实际用量</strong><pre>{{ prettyJson(selected.usageJson) }}</pre></div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script>
import {
  cancelWorkflowExecution,
  downloadWorkflowArtifact,
  getWorkflowExecution,
  listWorkflowArtifacts,
  listWorkflowExecutionEvents,
  listWorkflowExecutions,
  listWorkflowNodeRuns,
  retryWorkflowExecution,
  streamWorkflowExecutionEvents
} from '@/api/ai/workflow'
import {ArrowLeft, Refresh} from '@element-plus/icons-vue'
import {agentToolCalls, agentToolEventReason, agentToolEventStatus, agentToolEventType} from './workflowAgent'

export default {
  name: 'WorkflowExecutionMonitor',
  components: {ArrowLeft, Refresh},
  props: {
    canExecute: {
      type: Boolean,
      default: false
    },
    definitionId: {
      type: [Number, String],
      required: true
    },
    workflowName: {
      type: String,
      default: '工作流'
    },
    workflowCode: {
      type: String,
      default: ''
    }
  },
  emits: ['back'],
  data() {
    return {
      loading: false,
      executions: [],
      selected: null,
      nodeRuns: [],
      artifacts: [],
      events: [],
      drawerOpen: false,
      streamController: null,
      reconnectTimer: null,
      refreshTimer: null,
      streamConnected: false,
      lastSequence: 0,
      detailTab: 'nodes'
    }
  },
  computed: {
    streamStateLabel() {
      if (this.terminal(this.selected?.status)) return '执行已结束'
      return this.streamConnected ? '事件实时连接' : '事件重连中'
    },
    toolCalls() {
      return agentToolCalls(this.events)
    },
    toolCallCount() {
      return this.toolCalls.length
    }
  },
  created() {
    this.loadExecutions()
  },
  beforeUnmount() {
    this.stopEventStream()
  },
  methods: {
    async loadExecutions() {
      this.loading = true
      try {
        const response = await listWorkflowExecutions({definitionId: this.definitionId})
        this.executions = response.data || []
      } finally {
        this.loading = false
      }
    },
    async openDetail(row) {
      const [detail, runs, events, artifacts] = await Promise.all([
        getWorkflowExecution(row.executionId),
        listWorkflowNodeRuns(row.executionId),
        listWorkflowExecutionEvents(row.executionId),
        listWorkflowArtifacts(row.executionId)
      ])
      this.selected = detail.data
      this.nodeRuns = runs.data || []
      this.events = events.data || []
      this.artifacts = artifacts.data || []
      this.lastSequence = this.events.reduce((maximum, item) =>
        Math.max(maximum, item.sequenceNo || 0), 0)
      this.detailTab = 'nodes'
      this.drawerOpen = true
      this.startEventStream()
    },
    async cancel(row) {
      await this.$confirm('确认取消该工作流执行？', '取消执行', {type: 'warning'})
      await cancelWorkflowExecution(row.executionId)
      this.$message.success('已提交取消请求')
      await this.loadExecutions()
      if (this.drawerOpen) await this.openDetail(row)
    },
    async retry(row) {
      await this.$confirm(
        '将使用原输入和不可变发布版本创建一次新执行；包含写副作用的流程会被后端拒绝。',
        '安全重试',
        {type: 'warning'}
      )
      const response = await retryWorkflowExecution(
        row.executionId, `retry_${row.executionId}`
      )
      this.$message.success(`已创建重试执行：${response.data.executionId}`)
      await this.loadExecutions()
    },
    terminal(status) {
      return ['SUCCEEDED', 'FAILED', 'CANCELLED', 'REJECTED'].includes(status)
    },
    retryable(status) {
      return ['FAILED', 'CANCELLED', 'REJECTED'].includes(status)
    },
    statusClass(status) {
      if (status === 'SUCCEEDED') return 'success'
      if (['FAILED', 'REJECTED', 'NEEDS_ATTENTION'].includes(status)) return 'danger'
      if (status === 'CANCELLED') return 'neutral'
      return 'running'
    },
    toolEventStatus(status) {
      return agentToolEventStatus(status)
    },
    toolEventType(status) {
      return agentToolEventType(status)
    },
    toolEventReason(reasonCode) {
      return agentToolEventReason(reasonCode)
    },
    formatBytes(value) {
      const bytes = Number(value || 0)
      if (bytes < 1024) return `${bytes} B`
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
      return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    },
    async downloadArtifact(artifact) {
      const response = await downloadWorkflowArtifact(
        artifact.executionId, artifact.artifactId
      )
      const url = URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = url
      link.download = artifact.fileName || `${artifact.artifactId}.json`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    },
    startEventStream() {
      this.stopEventStream()
      if (!this.selected || this.terminal(this.selected.status)) return
      const executionId = this.selected.executionId
      this.streamController = streamWorkflowExecutionEvents(
        executionId,
        this.lastSequence,
        {
          onOpen: () => {
            this.streamConnected = true
          },
          onEvent: event => this.handleStreamEvent(executionId, event),
          onClose: () => this.scheduleReconnect(executionId),
          onError: () => this.scheduleReconnect(executionId)
        }
      )
    },
    handleStreamEvent(executionId, event) {
      if (event.event !== 'workflow' || executionId !== this.selected?.executionId) return
      const value = event.data
      const sequence = Number(value?.sequenceNo || event.id || 0)
      if (!sequence || sequence <= this.lastSequence) return
      this.lastSequence = sequence
      this.events.push(value)
      clearTimeout(this.refreshTimer)
      this.refreshTimer = setTimeout(async () => {
        if (executionId !== this.selected?.executionId) return
        const [detail, runs] = await Promise.all([
          getWorkflowExecution(executionId),
          listWorkflowNodeRuns(executionId)
        ])
        this.selected = detail.data
        this.nodeRuns = runs.data || []
        const index = this.executions.findIndex(item => item.executionId === executionId)
        if (index >= 0) this.executions.splice(index, 1, detail.data)
        if (this.terminal(this.selected.status)) this.stopEventStream()
      }, 150)
    },
    scheduleReconnect(executionId) {
      this.streamConnected = false
      if (!this.drawerOpen || executionId !== this.selected?.executionId
          || this.terminal(this.selected.status)) return
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = setTimeout(() => this.startEventStream(), 2000)
    },
    stopEventStream() {
      this.streamController?.abort()
      this.streamController = null
      this.streamConnected = false
      clearTimeout(this.refreshTimer)
      clearTimeout(this.reconnectTimer)
    },
    prettyJson(value) {
      if (!value) return '-'
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (error) {
        return value
      }
    }
  }
}
</script>

<style scoped>
.execution-monitor {
  padding: 24px;
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  border-radius: var(--workflow-radius, 18px);
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: var(--workflow-shadow, none);
}

.monitor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.monitor-heading {
  min-width: 0;
  display: flex;
  align-items: center;
}

.monitor-back.el-button {
  --el-button-text-color: var(--workflow-text-secondary, #64748b);
  --el-button-bg-color: var(--workflow-surface-raised, #fff);
  --el-button-border-color: var(--workflow-border-strong, #d9deea);
  --el-button-hover-text-color: var(--workflow-primary, #625bf6);
  --el-button-hover-bg-color: var(--workflow-primary-soft, #efedff);
  --el-button-hover-border-color: var(--workflow-primary, #625bf6);
  color: var(--workflow-text-secondary, #64748b) !important;
  background: var(--workflow-surface-raised, #fff) !important;
  border-color: var(--workflow-border-strong, #d9deea) !important;
}

.monitor-refresh.el-button {
  --el-button-text-color: #fff;
  --el-button-bg-color: var(--workflow-primary, #625bf6);
  --el-button-border-color: var(--workflow-primary, #625bf6);
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: var(--workflow-primary-hover, #5149e8);
  --el-button-hover-border-color: var(--workflow-primary-hover, #5149e8);
  min-width: 104px;
  color: #fff !important;
  background: var(--workflow-primary, #625bf6) !important;
  border-color: var(--workflow-primary, #625bf6) !important;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--workflow-primary, #625bf6) 22%, transparent);
  font-weight: 650;
}

.monitor-refresh.el-button:hover {
  color: #fff !important;
  background: var(--workflow-primary-hover, #5149e8) !important;
  border-color: var(--workflow-primary-hover, #5149e8) !important;
}

.monitor-back.el-button :deep(.el-icon),
.monitor-refresh.el-button :deep(.el-icon),
.monitor-refresh.el-button :deep(span) {
  color: inherit !important;
}

.execution-monitor :deep(.el-button.is-link) {
  --el-button-bg-color: transparent;
  --el-button-border-color: transparent;
  background: transparent !important;
  border-color: transparent !important;
  font-weight: 650;
}

.execution-monitor :deep(.el-button.is-link .el-icon),
.execution-monitor :deep(.el-button.is-link span) {
  color: inherit !important;
}

.execution-monitor :deep(.el-button.is-link.el-button--primary) {
  --el-button-text-color: #4f46e5;
  color: #4f46e5 !important;
}

.execution-monitor :deep(.el-button.is-link.el-button--danger) {
  --el-button-text-color: #dc2626;
  color: #dc2626 !important;
}

.execution-monitor :deep(.el-button.is-link.el-button--warning) {
  --el-button-text-color: #b45309;
  color: #b45309 !important;
}

:global(.workflow-execution-drawer .el-button.is-link.el-button--primary) {
  --el-button-text-color: #4f46e5;
  color: #4f46e5 !important;
  background: transparent !important;
}

:global(.workflow-execution-drawer .el-button.is-link.el-button--primary .el-icon),
:global(.workflow-execution-drawer .el-button.is-link.el-button--primary span) {
  color: inherit !important;
}

.monitor-title {
  font-size: 20px;
  font-weight: 700;
}

.monitor-title-group {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 12px;
}

.monitor-title-group span {
  display: block;
}

.monitor-subtitle {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-tabs {
  margin-top: 20px;
}

.detail-heading {
  min-width: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.detail-heading > div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.detail-heading strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 20px;
}

.detail-eyebrow {
  color: var(--workflow-primary, var(--el-color-primary));
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.stream-state {
  min-height: 40px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  border: 1px solid color-mix(in srgb, #d99215 24%, var(--workflow-border, var(--el-border-color-lighter)));
  border-radius: 9px;
  color: #a76508;
  background: color-mix(in srgb, #f5a524 8%, var(--workflow-surface, var(--el-bg-color)));
  font-size: 12px;
}

.stream-state--connected {
  border-color: color-mix(in srgb, #10a36e 24%, var(--workflow-border, var(--el-border-color-lighter)));
  color: #087c55;
  background: color-mix(in srgb, #10a36e 8%, var(--workflow-surface, var(--el-bg-color)));
}

.stream-state--terminal {
  border-color: var(--workflow-border-strong, var(--el-border-color));
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.stream-indicator,
.execution-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
  font-weight: 700;
}

.stream-indicator i,
.execution-status i {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 14%, transparent);
}

.stream-sequence {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
}

.execution-status {
  padding: 5px 9px;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 11px;
  line-height: 1;
}

.execution-status--success {
  color: #087c55;
  background: color-mix(in srgb, #10a36e 10%, var(--workflow-surface, #fff));
}

.execution-status--danger {
  color: #d63848;
  background: color-mix(in srgb, #ef5261 9%, var(--workflow-surface, #fff));
}

.execution-status--running {
  color: #a76508;
  background: color-mix(in srgb, #f5a524 10%, var(--workflow-surface, #fff));
}

.execution-status--neutral {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-light));
}

.execution-summary {
  overflow: hidden;
  border-radius: 11px;
}

.execution-summary :deep(.el-descriptions__label.el-descriptions__cell) {
  width: 128px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  font-weight: 700;
}

.execution-summary :deep(.el-descriptions__content.el-descriptions__cell) {
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface, var(--el-bg-color));
  overflow-wrap: anywhere;
}

.execution-summary code {
  color: var(--workflow-text, var(--el-text-color-primary));
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.detail-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--workflow-border, var(--el-border-color-lighter));
}

.detail-tabs :deep(.el-tabs__item) {
  height: 46px;
  padding: 0 18px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-weight: 650;
}

.detail-tabs :deep(.el-tabs__item:hover),
.detail-tabs :deep(.el-tabs__item.is-active) {
  color: var(--workflow-primary, var(--el-color-primary));
}

.detail-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--workflow-primary, var(--el-color-primary));
}

.detail-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.detail-tab-label em {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: inherit;
  background: var(--workflow-muted, var(--el-fill-color-light));
  font-size: 10px;
  font-style: normal;
}

.detail-tabs :deep(.el-tabs__item.is-active) .detail-tab-label em {
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.detail-table {
  overflow: hidden;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
}

.detail-table :deep(th.el-table__cell) {
  height: 44px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.detail-table :deep(td.el-table__cell) {
  height: 48px;
}

.json-grid {
  display: grid;
  gap: 16px;
}

.json-grid pre {
  min-height: 240px;
  padding: 12px;
  overflow: auto;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

:global(.workflow-execution-drawer.el-drawer) {
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: -18px 0 50px rgb(15 23 42 / 16%);
}

:global(.workflow-execution-drawer .el-drawer__header) {
  min-height: 76px;
  margin: 0;
  padding: 15px 24px;
  border-bottom: 1px solid var(--workflow-border, var(--el-border-color-lighter));
}

:global(.workflow-execution-drawer .el-drawer__body) {
  padding: 20px 24px 32px;
}

@media (max-width: 760px) {
  .monitor-subtitle {
    display: none;
  }

  .json-grid {
    grid-template-columns: 1fr;
  }

  .stream-state {
    align-items: flex-start;
    flex-direction: column;
    padding-block: 10px;
  }

  .execution-summary :deep(.el-descriptions__body .el-descriptions__table) {
    display: block;
  }

  .detail-tabs :deep(.el-tabs__nav-wrap) {
    overflow-x: auto;
  }

  :global(.workflow-execution-drawer.el-drawer) {
    width: 96% !important;
  }

  :global(.workflow-execution-drawer .el-drawer__header),
  :global(.workflow-execution-drawer .el-drawer__body) {
    padding-right: 16px;
    padding-left: 16px;
  }
}
</style>
