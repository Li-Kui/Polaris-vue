<template>
  <div class="execution-monitor">
    <div class="monitor-header">
      <div>
        <el-button icon="Back" circle @click="$emit('back')" />
        <span class="monitor-title">工作流运行记录</span>
        <span class="monitor-subtitle">实时追踪节点、事件与恢复状态</span>
      </div>
      <el-button :loading="loading" icon="Refresh" @click="loadExecutions">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="executions" class="polaris-el-table">
      <el-table-column prop="executionId" label="执行 ID" min-width="260">
        <template #default="{row}"><code>{{ row.executionId }}</code></template>
      </el-table-column>
      <el-table-column prop="workflowCode" label="工作流" min-width="150" />
      <el-table-column label="版本" width="80" align="center">
        <template #default="{row}">v{{ row.versionNo }}</template>
      </el-table-column>
      <el-table-column label="状态" width="150" align="center">
        <template #default="{row}">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
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
    </el-table>

    <el-drawer v-model="drawerOpen" title="执行详情" size="70%" destroy-on-close @closed="stopEventStream">
      <template v-if="selected">
        <div class="stream-state">
          <el-tag :type="streamConnected ? 'success' : 'warning'" size="small">
            {{ streamConnected ? '事件实时连接' : '事件重连中' }}
          </el-tag>
          <span>已同步至 #{{ lastSequence }}</span>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="执行 ID">{{ selected.executionId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selected.status }}</el-descriptions-item>
          <el-descriptions-item label="工作流">{{ selected.workflowCode }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ selected.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="父执行">{{ selected.parentExecutionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调用深度">{{ selected.executionDepth || 0 }}</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ selected.errorCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ selected.errorMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-tabs class="detail-tabs">
          <el-tab-pane label="节点运行">
            <el-table :data="nodeRuns" size="small">
              <el-table-column prop="nodeId" label="节点" min-width="130" />
              <el-table-column prop="attemptNo" label="Attempt" width="90" />
              <el-table-column prop="status" label="状态" width="130" />
              <el-table-column prop="sideEffectStatus" label="副作用" width="140" />
              <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="事件日志">
            <el-table :data="events" size="small">
              <el-table-column prop="sequenceNo" label="#" width="70" />
              <el-table-column prop="eventType" label="事件" min-width="190" />
              <el-table-column prop="nodeId" label="节点" min-width="120" />
              <el-table-column prop="payloadJson" label="载荷" min-width="260" show-overflow-tooltip />
              <el-table-column prop="createTime" label="时间" width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`执行产物 ${artifacts.length ? `(${artifacts.length})` : ''}`">
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
          <el-tab-pane label="输入/输出">
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

export default {
  name: 'WorkflowExecutionMonitor',
  props: {
    canExecute: {
      type: Boolean,
      default: false
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
      lastSequence: 0
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
        const response = await listWorkflowExecutions({})
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
    statusType(status) {
      if (status === 'SUCCEEDED') return 'success'
      if (['FAILED', 'REJECTED', 'NEEDS_ATTENTION'].includes(status)) return 'danger'
      if (status === 'CANCELLED') return 'info'
      return 'warning'
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

.monitor-title {
  margin-left: 12px;
  font-size: 20px;
  font-weight: 700;
}

.monitor-subtitle {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-tabs {
  margin-top: 18px;
}

.stream-state {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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

@media (max-width: 760px) {
  .monitor-subtitle {
    display: none;
  }

  .json-grid {
    grid-template-columns: 1fr;
  }
}
</style>
