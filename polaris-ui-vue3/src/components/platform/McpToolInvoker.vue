<template>
  <el-dialog v-model="open" :title="`手工试调用 · ${tool?.toolName || ''}`" width="min(820px, calc(100vw - 32px))" append-to-body
             class="polaris-glass-dialog mcp-invoke-dialog" :close-on-click-modal="false" :close-on-press-escape="!invoking" :show-close="!invoking">
    <el-alert title="将真实请求外部服务；只读审批不保证远端实际行为。输入和结果不写入审计，只记录调用元数据。"
              type="warning" :closable="false" show-icon class="invoke-notice" />
    <template v-if="tool">
      <p>工具 v{{ tool.revision }} · 连接 v{{ tool.connectionRevision }} · 目录 v{{ tool.catalogRevision }}</p>
      <el-collapse><el-collapse-item title="查看当前输入/输出约束">
        <pre class="invoke-json">{{ pretty({ inputSchema: tool.inputSchema, outputSchema: tool.outputSchema }) }}</pre>
      </el-collapse-item></el-collapse>
      <p>调用参数（JSON 对象，最大 64 KiB，不自动填入 Schema 默认值）</p>
      <el-input v-model="argumentsJson" type="textarea" :rows="8" :disabled="invoking" placeholder="{}" />
      <p><el-checkbox v-model="confirmed" :disabled="invoking">我确认向此连接发送上述参数，并发起一次真实查询</el-checkbox></p>
      <el-alert v-if="transportFailed" title="未收到确定的调用结果，远端状态未知。请先检查调用记录，不要直接重复请求。"
                type="warning" :closable="false" class="invoke-notice" />
      <template v-if="result">
        <el-alert :title="`${statusLabel(result.status)}：${result.message}`" :type="result.status === 'SUCCESS' ? 'success' : 'warning'"
                  :closable="false" show-icon class="invoke-notice" />
        <p class="invoke-meta">调用 ID：{{ result.invocationId }} · 耗时 {{ result.durationMs }} ms · {{ result.errorCode || '无错误' }}</p>
        <el-alert v-if="!result.auditRecorded" title="终态审计写入失败，原始开始记录可能仍显示结果未知；此提示不表示可以安全重试。"
                  type="error" :closable="false" class="invoke-notice" />
        <p>结果仅作为数据展示，不打开链接、不渲染 HTML、不执行返回的代码。</p>
        <pre v-if="result.content != null || result.structuredContent != null" class="invoke-json">{{ pretty({ content: result.content, structuredContent: result.structuredContent, isError: result.isError }) }}</pre>
      </template>
    </template>
    <template #footer>
      <el-button class="action-btn-secondary" :disabled="invoking" @click="open = false">关闭</el-button>
      <el-button class="action-btn-secondary" :disabled="invoking" @click="showRecords(connectionId, tool?.id)">调用记录</el-button>
      <el-button type="primary" :loading="invoking" :disabled="!confirmed || !tool?.policyReady || !!result || transportFailed"
                 @click="invoke">发起一次调用</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="recordsOpen" title="MCP 调用记录" width="min(1100px, calc(100vw - 32px))"
             append-to-body class="polaris-glass-dialog mcp-invoke-dialog">
    <el-alert title="准备派发不代表远端已经执行或完成。超时、取消及未完成记录的远端状态均不确定，不自动重试。"
              type="info" :closable="false" class="invoke-notice" />
    <el-alert v-if="recordsError" title="调用记录加载失败，不代表没有记录。请检查网络或服务状态后重试。"
              type="error" :closable="false" class="invoke-notice" />
    <p v-if="query.executionId" class="invoke-meta">仅查看执行：<code>{{ query.executionId }}</code></p>
    <p v-if="query.nodeRunId" class="invoke-meta">节点尝试：<code>{{ query.nodeRunId }}</code></p>
    <div class="record-actions">
      <el-select v-model="query.status" clearable placeholder="全部调用状态" aria-label="调用状态" style="width: 200px"
                 :disabled="recordsLoading" @change="searchRecords">
        <el-option v-for="status in statuses" :key="status" :value="status" :label="statusLabel(status)" />
      </el-select>
      <el-button class="action-btn-secondary" icon="Refresh" :loading="recordsLoading" @click="loadRecords">刷新记录</el-button>
      <span class="record-tip">仅保存调用元数据，不保存输入与结果；展开行可查看完整 ID。</span>
    </div>
    <el-table v-loading="recordsLoading" :data="records" row-key="invocationId" class="polaris-el-table" aria-label="MCP 调用记录">
      <template #empty>
        <el-empty v-if="!recordsLoading" :image-size="64" :description="recordsError ? '暂时无法加载调用记录' : (query.status ? '没有匹配此状态的调用记录' : '暂无调用记录，发起工具调用后可在此查看结果与耗时')" />
      </template>
      <el-table-column type="expand" width="44">
        <template #default="scope">
          <dl class="record-detail">
            <div><dt>调用 ID</dt><dd>{{ scope.row.invocationId }}</dd></div>
            <div><dt>调用主体</dt><dd>{{ scope.row.actorName || '—' }}</dd></div>
            <div><dt>派发阶段</dt><dd>{{ scope.row.callDispatched === '1' ? '已准备派发（不代表远端完成）' : '未派发' }}</dd></div>
            <template v-if="scope.row.source === 'AGENT_CHAT'">
              <div><dt>对话会话 ID</dt><dd>{{ scope.row.conversationId || '—' }}</dd></div>
              <div><dt>协作请求 ID</dt><dd>{{ scope.row.chatRequestId || '—' }}</dd></div>
              <div><dt>智能体运行 ID</dt><dd>{{ scope.row.agentRunId || '—' }}</dd></div>
              <div><dt>查询序号</dt><dd>{{ scope.row.agentCallNo ?? '—' }}</dd></div>
            </template>
            <template v-if="isWorkflowInvocation(scope.row.source)">
              <div><dt>执行 ID</dt><dd>{{ scope.row.executionId || '—' }}</dd></div>
              <div><dt>节点尝试 ID</dt><dd>{{ scope.row.nodeRunId || '—' }}</dd></div>
              <div><dt>尝试次数</dt><dd>{{ scope.row.attemptNo ?? '—' }}</dd></div>
              <template v-if="scope.row.source === 'AGENT_WORKFLOW'">
                <div><dt>智能体运行 ID</dt><dd>{{ scope.row.agentRunId || '—' }}</dd></div>
                <div><dt>查询序号</dt><dd>{{ scope.row.agentCallNo ?? '—' }}</dd></div>
              </template>
            </template>
          </dl>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150"><template #default="scope"><el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="错误码" min-width="180" show-overflow-tooltip><template #default="scope"><span class="record-error">{{ scope.row.errorCode || '—' }}</span></template></el-table-column>
      <el-table-column label="来源 / 主体" width="110"><template #default="scope"><div>{{ invocationSourceLabel(scope.row.source) }}</div><small class="record-tip">{{ scope.row.actorName || '—' }}</small></template></el-table-column>
      <el-table-column label="工具 / 修订" width="100"><template #default="scope">#{{ scope.row.toolId }} · v{{ scope.row.toolRevision }}</template></el-table-column>
      <el-table-column label="耗时(ms)" width="90"><template #default="scope">{{ scope.row.durationMs ?? '—' }}</template></el-table-column>
      <el-table-column label="开始时间" prop="startedTime" min-width="165" />
    </el-table>
    <pagination v-show="total > 0" :total="total" :page-sizes="[10, 20, 50]" v-model:page="query.pageNum"
                v-model:limit="query.pageSize" @pagination="loadRecords" />
  </el-dialog>
</template>

<script>
import {invocationSourceLabel, isWorkflowInvocation} from '@/components/agent/agentBindings'
import {ElMessage} from 'element-plus'
import {invokeMcpTool, listMcpInvocations} from '@/api/platform/mcp'

export default {
  name: 'McpToolInvoker',
  emits: ['changed', 'busy', 'records-loaded'],
  data() {
    return {
      open: false, tool: null, connectionId: null, argumentsJson: '{}', confirmed: false,
      invoking: false, result: null, transportFailed: false, recordsOpen: false,
      records: [], total: 0, recordsLoading: false, recordsSequence: 0, recordsError: false,
      query: { connectionId: null, toolId: null, status: '', pageNum: 1, pageSize: 20 },
      statuses: ['STARTED', 'SUCCESS', 'FAILED', 'REJECTED', 'TOOL_ERROR', 'TIMEOUT', 'CANCELLED']
    }
  },
  watch: {
    recordsOpen(value) { if (!value) { this.recordsSequence++; this.recordsLoading = false } }
  },
  beforeUnmount() { this.recordsSequence++ },
  methods: {
    isWorkflowInvocation,
    invocationSourceLabel,
    show(tool, connectionId) {
      if (this.invoking) return
      this.tool = { ...tool }
      this.connectionId = connectionId
      this.argumentsJson = '{}'
      this.confirmed = false
      this.result = null
      this.transportFailed = false
      this.open = true
    },
    async invoke() {
      if (this.invoking || !this.confirmed || !this.tool?.policyReady || this.result || this.transportFailed) return
      let args
      try {
        if (new TextEncoder().encode(this.argumentsJson).length > 64 * 1024) throw new Error()
        args = JSON.parse(this.argumentsJson)
        if (!args || Array.isArray(args) || typeof args !== 'object') throw new Error()
      } catch { ElMessage.warning('参数须为有效 JSON 对象，且不超过 64 KiB'); return }
      this.invoking = true
      this.$emit('busy', true)
      const tool = this.tool
      try {
        const { data } = await invokeMcpTool(tool.id, {
          revision: tool.revision, structureHash: tool.structureHash,
          connectionRevision: tool.connectionRevision, catalogRevision: tool.catalogRevision, arguments: args
        })
        this.result = data
      } catch { this.transportFailed = true }
      finally {
        this.invoking = false
        this.confirmed = false
        this.$emit('busy', false)
        this.$emit('changed')
      }
    },
    showWorkflowRecords(executionId, nodeRunId = null) {
      if (!executionId) return
      this.showRecords(null, null, executionId, nodeRunId)
    },
    closeRecords() {
      this.recordsSequence++
      this.recordsOpen = false
      this.recordsLoading = false
      this.records = []
      this.total = 0
    },
    showRecords(connectionId, toolId = null, executionId = null, nodeRunId = null) {
      this.query = { connectionId, toolId, status: '', pageNum: 1, pageSize: 20 }
      if (executionId != null) this.query.executionId = executionId
      if (executionId != null && nodeRunId != null) this.query.nodeRunId = nodeRunId
      this.records = []
      this.total = 0
      this.recordsError = false
      this.recordsOpen = true
      this.loadRecords()
    },
    searchRecords() { this.query.pageNum = 1; this.loadRecords() },
    async loadRecords() {
      const sequence = ++this.recordsSequence
      const query = {...this.query}
      this.recordsLoading = true
      this.recordsError = false
      try {
        const { data } = await listMcpInvocations(query)
        if (sequence !== this.recordsSequence) return
        if (!Array.isArray(data?.rows) || !Number.isSafeInteger(data.total) || data.total < data.rows.length
            || (query.executionId && data.rows.some(row => row.executionId !== query.executionId
              || !isWorkflowInvocation(row.source) || (query.nodeRunId && row.nodeRunId !== query.nodeRunId)))) {
          throw new Error('审计记录范围或格式不一致')
        }
        this.records = data.rows
        this.total = data.total
        this.$emit('records-loaded', {...query, records: data.rows, total: data.total})
      } catch { if (sequence === this.recordsSequence) { this.recordsError = true; this.records = []; this.total = 0 } }
      finally { if (sequence === this.recordsSequence) this.recordsLoading = false }
    },
    pretty(value) { return JSON.stringify(value, null, 2) },
    statusType(value) {
      return { SUCCESS: 'success', FAILED: 'danger', REJECTED: 'danger', TOOL_ERROR: 'danger',
        STARTED: 'warning', TIMEOUT: 'warning', CANCELLED: 'warning' }[value] || 'info'
    },
    statusLabel(value) {
      return { STARTED: '未完成／结果未知', SUCCESS: '成功', FAILED: '失败', REJECTED: '已拒绝',
        TOOL_ERROR: '远端业务错误', TIMEOUT: '超时／远端未知', CANCELLED: '取消／远端未知' }[value] || value
    }
  }
}
</script>

<style scoped lang="scss">
.invoke-notice { margin-bottom: 16px; }
.invoke-notice.el-alert--info :deep(.el-alert__title) { color: var(--el-text-color-regular); }
.invoke-json { max-height: 350px; overflow: auto; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; white-space: pre-wrap; overflow-wrap: anywhere; }
.invoke-meta { overflow-wrap: anywhere; }
.record-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.record-tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.record-error { font-size: 12px; }
.record-detail { margin: 0; padding: 16px 24px; background: var(--el-fill-color-light); }
.record-detail > div { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 8px; margin-bottom: 8px; }
.record-detail > div:last-child { margin-bottom: 0; }
.record-detail dt { color: var(--el-text-color-secondary); }
.record-detail dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 600px) { .record-detail > div { grid-template-columns: 1fr; gap: 4px; } }
</style>
