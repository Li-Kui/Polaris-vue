<template>
  <el-dialog v-model="open" :title="`工具目录 · ${connectionName}`" width="min(1100px, calc(100vw - 32px))" append-to-body
             class="polaris-glass-dialog mcp-catalog-dialog" :close-on-click-modal="false">
    <el-alert title="仅当前版本已审批的只读工具可手工调用。远端只读提示不保证实际行为，须管理员核实后明确审批。"
              type="info" :closable="false" show-icon class="catalog-notice" />
    <el-alert v-if="connection?.discoveryStatus === 'FAILED'" title="最近发现失败，以下保留上次完整目录，不代表远端最新状态。"
              type="warning" :closable="false" class="catalog-notice" />
    <el-alert v-if="connection?.discoveryStatus === 'STALE'" title="连接的端点或认证已变化，旧审批失效。请重新发现并审批。"
              type="warning" :closable="false" class="catalog-notice" />
    <div class="catalog-actions">
      <el-button type="primary" :loading="refreshing" :disabled="connection?.status !== '0' || loading || saving"
                 @click="refreshCatalog">重新发现并刷新目录</el-button>
      <el-select v-model="query.catalogStatus" clearable placeholder="全部目录状态" style="width: 160px"
                 :disabled="loading || refreshing || saving" @change="search">
        <el-option label="远端存在" value="PRESENT" /><el-option label="远端失踪" value="MISSING" />
        <el-option label="待重新发现" value="STALE" />
      </el-select>
      <el-button class="action-btn-secondary" :disabled="loading || refreshing || saving" @click="loadCatalog">刷新本地列表</el-button>
      <el-button class="action-btn-secondary" :disabled="invoking" @click="$refs.invoker.showRecords(connectionId)">调用记录</el-button>
    </div>
    <p class="catalog-tip catalog-sync">上次成功发现：{{ connection?.lastDiscoverySuccessTime || '尚未发现' }} · 刷新本地列表不会请求远端服务</p>
    <el-table v-loading="loading || refreshing" :data="rows" class="polaris-el-table">
      <el-table-column label="工具" min-width="180" show-overflow-tooltip>
        <template #default="scope"><div class="tool-title">{{ scope.row.toolTitle || scope.row.toolName }}</div><small class="tool-name">{{ scope.row.toolName }}</small></template>
      </el-table-column>
      <el-table-column label="结构修订" width="90"><template #default="scope">v{{ scope.row.revision }}</template></el-table-column>
      <el-table-column label="目录" width="105"><template #default="scope"><el-tag size="small" :type="scope.row.catalogStatus === 'PRESENT' ? 'success' : 'warning'">{{ catalogLabel(scope.row.catalogStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="审批" width="100"><template #default="scope"><el-tag size="small" :type="scope.row.reviewStatus === 'APPROVED' ? 'success' : 'info'">{{ reviewLabel(scope.row.reviewStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="当前策略" min-width="180" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.policyReady ? '可手工试调用' : scope.row.unavailableReason }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <div class="catalog-row-actions">
          <el-button link type="primary" :disabled="saving || refreshing || loading" @click="viewTool(scope.row)">详情</el-button>
          <el-button link type="primary" :disabled="saving || refreshing || loading || !canApprove(scope.row)" @click="reviewTool(scope.row, 'APPROVE')">审批只读</el-button>
          <el-button link type="danger" :disabled="saving || refreshing || loading || scope.row.reviewStatus !== 'APPROVED'" @click="reviewTool(scope.row, 'REVOKE')">撤销</el-button>
          <el-button link type="primary" :disabled="saving || refreshing || loading || invoking || !scope.row.policyReady"
                     @click="$refs.invoker.show(scope.row, connectionId)">试调用</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page-sizes="[10, 20, 50]" v-model:page="query.pageNum"
                v-model:limit="query.pageSize" @pagination="loadCatalog" />

    <el-dialog v-model="detailOpen" :title="selected?.toolName || '工具详情'" width="min(800px, calc(100vw - 32px))" append-to-body
               class="polaris-glass-dialog mcp-catalog-dialog">
      <template v-if="selected">
        <p class="tool-description">{{ selected.description || '暂无描述' }}</p>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="修订">v{{ selected.revision }}</el-descriptions-item>
          <el-descriptions-item label="最近发现">{{ selected.lastSeenTime }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ selected.reviewedBy || '无' }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ selected.reviewedTime || '无' }}</el-descriptions-item>
          <el-descriptions-item label="Schema支持">{{ selected.schemaSupported ? '支持当前校验档位' : selected.schemaError }}</el-descriptions-item>
          <el-descriptions-item label="结构哈希"><span class="hash-text">{{ selected.structureHash }}</span></el-descriptions-item>
        </el-descriptions>
        <p>审批备注：{{ selected.reviewNote || '无' }}</p>
        <div class="schema-label">输入 Schema</div><pre class="schema-preview">{{ pretty(selected.inputSchema) }}</pre>
        <template v-if="selected.outputSchema"><div class="schema-label">输出 Schema</div><pre class="schema-preview">{{ pretty(selected.outputSchema) }}</pre></template>
        <el-collapse><el-collapse-item title="查看完整远端定义与注解（不可信声明）"><pre class="schema-preview">{{ pretty(selected.definition) }}</pre></el-collapse-item></el-collapse>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewOpen" :title="reviewAction === 'APPROVE' ? '审批只读工具' : '撤销工具审批'" width="min(680px, calc(100vw - 32px))"
               append-to-body class="polaris-glass-dialog mcp-catalog-dialog" :close-on-click-modal="false">
      <template v-if="selected">
        <p>{{ selected.toolName }} · 当前修订 v{{ selected.revision }}</p>
        <p class="hash-text">结构哈希：{{ selected.structureHash }}</p>
        <el-alert title="审批绑定本次展示的目录和结构修订，目录或连接变化后提交会被拒绝。"
                  type="info" :closable="false" class="catalog-notice" />
        <template v-if="reviewAction === 'APPROVE'">
          <p class="tool-description">{{ selected.description || '暂无描述' }}</p>
          <div class="schema-label">输入 Schema</div><pre class="schema-preview">{{ pretty(selected.inputSchema) }}</pre>
          <el-collapse><el-collapse-item title="检查完整定义与输入/输出约束"><pre class="schema-preview">{{ pretty(selected.definition) }}</pre></el-collapse-item></el-collapse>
          <el-checkbox v-model="confirmedReadOnly">我已核实此工具只执行查询操作，不会修改、删除数据或触发其他业务动作</el-checkbox>
          <p class="catalog-tip">远端 readOnlyHint/destructiveHint 仅是提示，不会自动勾选此项，也不能保证业务行为。</p>
        </template>
        <el-input v-model="reviewNote" type="textarea" maxlength="500" show-word-limit placeholder="填写审批或撤销备注（选填）" />
      </template>
      <template #footer>
        <el-button class="action-btn-secondary" :disabled="saving" @click="reviewOpen = false">取消</el-button>
        <el-button :type="reviewAction === 'APPROVE' ? 'primary' : 'danger'" :loading="saving"
                   :disabled="reviewAction === 'APPROVE' && !confirmedReadOnly" @click="submitReview">确认{{ reviewAction === 'APPROVE' ? '审批' : '撤销' }}</el-button>
      </template>
    </el-dialog>
    <McpToolInvoker ref="invoker" @busy="invoking = $event" @changed="loadCatalog" />
  </el-dialog>
</template>

<script>
import {ElMessage, ElMessageBox} from 'element-plus'
import {changeMcpToolPolicy, getMcpConnection, listMcpTools, refreshMcpCatalog} from '@/api/platform/mcp'
import McpToolInvoker from '@/components/platform/McpToolInvoker.vue'

export default {
  name: 'McpToolCatalog',
  components: { McpToolInvoker },
  emits: ['changed'],
  data() {
    return {
      open: false, connectionId: null, connectionName: '', connection: null, rows: [], total: 0,
      query: { pageNum: 1, pageSize: 20, catalogStatus: '' }, loading: false, refreshing: false,
      selected: null, detailOpen: false, reviewOpen: false, reviewAction: '', saving: false,
      confirmedReadOnly: false, reviewNote: '', loadSequence: 0, invoking: false
    }
  },
  methods: {
    show(row) {
      if (this.refreshing || this.saving || this.invoking) return
      this.connectionId = row.id
      this.connectionName = row.connectionName
      this.connection = row
      this.rows = []
      this.total = 0
      this.query.pageNum = 1
      this.query.catalogStatus = ''
      this.detailOpen = false
      this.reviewOpen = false
      this.open = true
      this.loadCatalog()
    },
    async loadCatalog() {
      const sequence = ++this.loadSequence
      const id = this.connectionId
      this.loading = true
      try {
        const [connection, tools] = await Promise.all([getMcpConnection(id), listMcpTools(id, { ...this.query })])
        if (sequence !== this.loadSequence || id !== this.connectionId) return
        this.connection = connection.data
        this.rows = tools.data.rows
        this.total = tools.data.total
      } catch { /* 请求组件统一提示，不清空上次完整列表。 */ }
      finally { if (sequence === this.loadSequence) this.loading = false }
    },
    search() { this.query.pageNum = 1; this.loadCatalog() },
    async refreshCatalog() {
      if (this.refreshing || this.saving) return
      try {
        await ElMessageBox.confirm('完整拉取远端工具后更新目录；新增、结构变化或重新出现的工具将禁用并要求重新审批。确认刷新？', '刷新目录', { type: 'warning' })
      } catch { return }
      this.refreshing = true
      const id = this.connectionId
      try {
        const { data } = await refreshMcpCatalog(id)
        ElMessage.success(`发现 ${data.discovered} 个工具：新增 ${data.added}，变更 ${data.changed}，失踪 ${data.missing}，未变 ${data.unchanged}`)
        this.query.pageNum = 1
        this.$emit('changed')
      } catch { /* 刷新失败保留旧目录，以下重新读取安全失败状态。 */ }
      finally {
        if (id === this.connectionId) await this.loadCatalog()
        this.refreshing = false
      }
    },
    canApprove(tool) {
      return this.connection?.status === '0' && tool.approvable && tool.reviewStatus !== 'APPROVED'
    },
    viewTool(tool) { this.selected = tool; this.detailOpen = true },
    reviewTool(tool, action) {
      this.selected = tool
      this.reviewAction = action
      this.confirmedReadOnly = false
      this.reviewNote = ''
      this.reviewOpen = true
    },
    async submitReview() {
      if (this.saving || !this.selected || this.reviewAction === 'APPROVE' && !this.confirmedReadOnly) return
      this.saving = true
      try {
        const tool = this.selected
        await changeMcpToolPolicy(tool.id, {
          revision: tool.revision, structureHash: tool.structureHash,
          connectionRevision: tool.connectionRevision, catalogRevision: tool.catalogRevision,
          action: this.reviewAction, confirmedReadOnly: this.reviewAction === 'APPROVE' && this.confirmedReadOnly,
          reviewNote: this.reviewNote
        })
        ElMessage.success(this.reviewAction === 'APPROVE' ? '已审批当前版本只读工具' : '已撤销审批')
        this.reviewOpen = false
        this.detailOpen = false
      } catch { /* 快照冲突由后端拒绝，不自动重试审批。 */ }
      finally { await this.loadCatalog(); this.saving = false }
    },
    pretty(value) { return JSON.stringify(value, null, 2) },
    catalogLabel(value) { return { PRESENT: '存在', MISSING: '失踪', STALE: '待复查' }[value] || value },
    reviewLabel(value) { return { PENDING: '待审核', APPROVED: '已批准', REVOKED: '已撤销' }[value] || value }
  }
}
</script>

<style scoped lang="scss">
.catalog-notice { margin-bottom: 16px; }
.catalog-notice.el-alert--info :deep(.el-alert__title) { color: var(--el-text-color-regular); }
.catalog-actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 16px; }
.catalog-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.catalog-sync { margin: 0 0 16px; line-height: 1.6; }
.catalog-actions :deep(.el-button + .el-button) { margin-left: 0; }
.catalog-row-actions { display: grid; grid-template-columns: repeat(2, max-content); gap: 4px 12px; align-items: center; }
.tool-title { font-weight: 600; }
.tool-name { color: var(--el-text-color-secondary); font-size: 12px; }
.schema-label { margin: 12px 0 6px; font-weight: 600; }
.schema-preview { max-height: 260px; overflow: auto; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; white-space: pre-wrap; overflow-wrap: anywhere; }
.hash-text, .tool-description { overflow-wrap: anywhere; white-space: pre-wrap; }
</style>
