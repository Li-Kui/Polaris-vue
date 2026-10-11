<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <el-alert v-if="!isAdmin" title="仅租户管理员可管理 MCP 连接" type="info" :closable="false" />
      <div v-else class="polaris-table-card mcp-card">
        <div class="mcp-heading">
          <div>
            <h2>MCP 连接</h2>
            <p>连接外部工具服务，发现工具并在审核后受控调用。</p>
          </div>
          <div class="actions-left mcp-heading-actions">
            <el-button class="action-btn-secondary" icon="Clock" @click="$refs.invocations.showRecords(null)">调用记录</el-button>
            <el-button type="primary" icon="Plus" class="action-btn-primary" @click="handleAdd">新建 MCP 连接</el-button>
          </div>
        </div>
        <el-alert title="支持域名或公网 IP 的 Streamable HTTP 服务；工具须经本地审核，远端只读提示不代表授权。"
                  type="info" :closable="false" show-icon class="mcp-notice" />
        <el-form :inline="true" :model="query" class="mcp-filters" @submit.prevent="search">
          <el-form-item label="连接名称">
            <el-input v-model="query.connectionName" clearable maxlength="100" placeholder="搜索连接名称" @keyup.enter="search" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
              <el-option label="启用" value="0" /><el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item class="mcp-filter-actions">
            <el-button class="action-btn-secondary" icon="Search" :loading="loading" @click="search">查询</el-button>
            <el-button class="action-btn-secondary" :disabled="loading" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="mcp-list-heading">
          <span>连接列表 <span v-if="!loadError" class="mcp-count">{{ total }}</span></span>
          <span class="field-tip">新增连接后，先测试连通性，再进入工具目录审核</span>
        </div>
        <el-alert v-if="loadError" title="连接列表加载失败，当前展示可能不是最新数据，请重新查询。"
                  type="error" :closable="false" show-icon class="mcp-notice" />
        <div v-loading="loading" class="mcp-table-region">
          <div v-if="!rows.length" class="mcp-empty">
            <h3>{{ loadError ? '暂时无法加载连接' : (hasFilters ? '没有匹配的连接' : '还没有 MCP 连接') }}</h3>
            <p>{{ loadError ? '请检查网络或服务状态后重试。' : (hasFilters ? '调整连接名称或状态筛选，再试一次。' : '添加域名或公网 IP 端点，完成测试与工具审核后即可调用。') }}</p>
            <div v-if="!loadError && !hasFilters" class="mcp-empty-steps" aria-label="接入步骤">
              <span>1. 新建连接</span><span>2. 测试与发现</span><span>3. 审核后调用</span>
            </div>
            <el-button v-if="loadError" class="action-btn-secondary" icon="Refresh" @click="getList">重新加载</el-button>
            <el-button v-else-if="hasFilters" class="action-btn-secondary" @click="resetQuery">清空筛选</el-button>
            <el-button v-else type="primary" icon="Plus" class="action-btn-primary" @click="handleAdd">添加第一个连接</el-button>
          </div>
          <el-table v-else :data="rows" class="polaris-el-table" aria-label="MCP 连接列表">
            <el-table-column prop="connectionName" label="连接名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="endpointUrl" label="服务端点" min-width="240" show-overflow-tooltip />
            <el-table-column label="认证" width="130">
              <template #default="scope">
                {{ authLabel(scope.row.authType) }}
                <el-tag v-if="scope.row.credentialConfigured" size="small" type="info">已配置</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="总超时" width="90">
              <template #default="scope">{{ scope.row.timeoutMs / 1000 }}s</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="scope"><el-tag :type="scope.row.status === '0' ? 'success' : 'info'">{{ scope.row.status === '0' ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="目录发现" width="115">
              <template #default="scope"><el-tag size="small" :type="scope.row.discoveryStatus === 'SUCCESS' ? 'success' : 'info'">{{ discoveryLabel(scope.row.discoveryStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="scope">
                <el-button link type="primary" :disabled="probing" @click="$refs.toolCatalog.show(scope.row)">工具目录</el-button>
                <el-button link type="primary" :disabled="scope.row.status !== '0' || probing"
                           @click="handleProbe(scope.row, false)">测试</el-button>
                <el-dropdown :disabled="probing" class="mcp-row-more" @command="command => handleRowCommand(command, scope.row)">
                  <el-button link type="primary" :disabled="probing">更多<el-icon><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">修改连接</el-dropdown-item>
                      <el-dropdown-item command="preview" :disabled="scope.row.status !== '0'">发现预览</el-dropdown-item>
                      <el-dropdown-item command="records">调用记录</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除连接</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum"
                    v-model:limit="query.pageSize" @pagination="getList" />
      </div>
    </div>

    <el-dialog v-model="open" :title="form.id ? '修改 MCP 连接' : '新建 MCP 连接'" width="min(680px, calc(100vw - 32px))"
               append-to-body class="polaris-glass-dialog mcp-connection-dialog" :close-on-click-modal="false" @closed="clearSecret">
      <el-form ref="connectionForm" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="连接名称" prop="connectionName"><el-input v-model="form.connectionName" maxlength="100" /></el-form-item>
        <el-form-item label="服务端点" prop="endpointUrl">
          <el-input v-model="form.endpointUrl" maxlength="2048" placeholder="https://example.com/mcp" />
          <div class="field-tip">支持域名或公网 IP，需包含 http(s)://、端口（如有）与完整 MCP 路径；IPv6 地址用方括号包裹。</div>
          <div class="field-tip">不支持内网地址、查询参数、OAuth、stdio 或旧式 SSE 端点。</div>
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="form.authType" @change="authChanged">
            <el-option label="无认证" value="NONE" /><el-option label="Bearer Token" value="BEARER" /><el-option label="API Key" value="API_KEY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.authType === 'API_KEY'" label="凭证头名" prop="credentialHeaderName">
          <el-input v-model="form.credentialHeaderName" maxlength="100" placeholder="X-API-Key" />
        </el-form-item>
        <template v-if="form.authType !== 'NONE'">
          <el-form-item label="凭证操作">
            <el-radio-group v-model="form.credentialAction" @change="clearSecret">
              <el-radio value="KEEP" :disabled="!canKeepCredential">保留已存凭证</el-radio>
              <el-radio value="REPLACE">替换凭证</el-radio>
            </el-radio-group>
            <div class="field-tip">密钥不回显；修改端点或认证方式后必须重新填写。携带凭证须使用 HTTPS，证书需与域名或 IP 匹配。</div>
          </el-form-item>
          <el-form-item v-if="form.credentialAction === 'REPLACE'" label="新凭证" prop="secret">
            <el-input v-model="form.secret" type="password" show-password autocomplete="new-password" maxlength="4096" />
          </el-form-item>
        </template>
        <el-form-item label="总超时">
          <el-input-number v-model="form.timeoutMs" :min="1000" :max="60000" :step="1000" :precision="0" />
          <span class="field-tip">毫秒，单次操作的总时限（1–60 秒）</span>
        </el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio value="0">启用</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button class="action-btn-secondary" @click="open = false">取消</el-button><el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="previewOpen" :title="previewTitle" width="min(880px, calc(100vw - 32px))" append-to-body class="polaris-glass-dialog mcp-connection-dialog">
      <div v-loading="probing" class="mcp-preview">
        <template v-if="preview">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="服务">{{ preview.serverName }} / {{ preview.serverVersion }}</el-descriptions-item>
            <el-descriptions-item label="协议">{{ preview.protocolVersion }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ preview.durationMs }} ms</el-descriptions-item>
            <el-descriptions-item label="工具能力">{{ preview.toolsSupported ? '支持' : '未声明' }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-if="discovering" title="以下内容由远端提供，仅作发现预览，不自动入库、不代表授权。" type="warning" :closable="false" class="mcp-notice" />
          <el-empty v-if="discovering && !preview.tools.length" description="远端未返回工具" />
          <el-collapse v-if="discovering">
            <el-collapse-item v-for="tool in preview.tools" :key="tool.name" :name="tool.name" :title="tool.title ? `${tool.title} (${tool.name})` : tool.name">
              <p class="tool-description">{{ tool.description || '暂无描述' }}</p>
              <p>远端只读提示：{{ hint(tool.readOnlyHint) }}；破坏性提示：{{ hint(tool.destructiveHint) }}（不可用作权限判断）</p>
              <div class="schema-label">输入 Schema</div><pre class="schema-preview">{{ pretty(tool.inputSchema) }}</pre>
              <template v-if="tool.outputSchema"><div class="schema-label">输出 Schema</div><pre class="schema-preview">{{ pretty(tool.outputSchema) }}</pre></template>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-empty v-else :description="probing ? '正在初始化 MCP 会话，请稍候' : '探测失败，请检查连接配置后重试'" />
      </div>
    </el-dialog>
    <McpToolCatalog ref="toolCatalog" @changed="getList" />
    <McpToolInvoker ref="invocations" />
  </div>
</template>

<script>
import {ElMessage, ElMessageBox} from 'element-plus'
import usePlatformUserStore from '@/store/modules/platformUser'
import {
  deleteMcpConnection,
  getMcpConnection,
  listMcpConnection,
  probeMcpConnection,
  saveMcpConnection
} from '@/api/platform/mcp'
import McpToolCatalog from '@/components/platform/McpToolCatalog.vue'
import McpToolInvoker from '@/components/platform/McpToolInvoker.vue'

function emptyForm() {
  return { connectionName: '', endpointUrl: '', authType: 'NONE', credentialHeaderName: '',
    credentialAction: 'CLEAR', secret: '', timeoutMs: 15000, status: '0', remark: '' }
}

export default {
  name: 'PlatformMcp',
  components: { McpToolCatalog, McpToolInvoker },
  data() {
    return {
      userStore: usePlatformUserStore(), loading: false, loadError: false, loadSequence: 0, rows: [], total: 0,
      query: { pageNum: 1, pageSize: 10, connectionName: '', status: '' },
      open: false, saving: false, form: emptyForm(), original: null,
      previewOpen: false, previewTitle: '', preview: null, probing: false, discovering: false,
      rules: {
        connectionName: [{ required: true, whitespace: true, message: '请输入连接名称', trigger: 'blur' }],
        endpointUrl: [{ required: true, whitespace: true, message: '请输入完整端点', trigger: 'blur' }],
        credentialHeaderName: [{ required: true, message: '请输入 API Key 请求头名', trigger: 'blur' }],
        secret: [{ required: true, whitespace: true, message: '请输入新凭证', trigger: 'blur' }]
      }
    }
  },
  computed: {
    isAdmin() { return this.userStore.user?.role === 'admin' },
    hasFilters() { return !!(this.query.connectionName.trim() || this.query.status) },
    canKeepCredential() {
      return !!this.original?.credentialConfigured && this.form.authType === this.original.authType
        && this.form.endpointUrl.trim() === this.original.endpointUrl
        && (this.form.authType !== 'API_KEY' || this.form.credentialHeaderName.trim() === this.original.credentialHeaderName)
    }
  },
  watch: {
    isAdmin: { immediate: true, handler(value) { if (value) this.getList() } },
    canKeepCredential(value) {
      if (!value && this.form.credentialAction === 'KEEP') this.form.credentialAction = 'REPLACE'
    }
  },
  methods: {
    async getList() {
      const sequence = ++this.loadSequence
      this.loading = true
      this.loadError = false
      try {
        const response = await listMcpConnection({ ...this.query })
        if (sequence !== this.loadSequence) return
        this.rows = response.data.rows
        this.total = response.data.total
      } catch { if (sequence === this.loadSequence) this.loadError = true }
      finally { if (sequence === this.loadSequence) this.loading = false }
    },
    search() { this.query.pageNum = 1; this.getList() },
    resetQuery() { this.query.connectionName = ''; this.query.status = ''; this.search() },
    handleRowCommand(command, row) {
      if (this.probing) return
      if (command === 'edit') this.handleEdit(row)
      else if (command === 'preview' && row.status === '0') this.handleProbe(row, true)
      else if (command === 'records') this.$refs.invocations.showRecords(row.id)
      else if (command === 'delete') this.handleDelete(row)
    },
    handleAdd() {
      this.original = null
      this.form = emptyForm()
      this.open = true
      this.$nextTick(() => this.$refs.connectionForm?.clearValidate())
    },
    async handleEdit(row) {
      try {
        const { data } = await getMcpConnection(row.id)
        this.original = data
        this.form = { ...emptyForm(), ...data, secret: '', credentialHeaderName: data.credentialHeaderName || '',
          credentialAction: data.authType === 'NONE' ? 'CLEAR' : (data.credentialConfigured ? 'KEEP' : 'REPLACE') }
        this.open = true
        this.$nextTick(() => this.$refs.connectionForm?.clearValidate())
      } catch { /* 请求组件统一提示。 */ }
    },
    clearSecret() { this.form.secret = '' },
    authChanged() {
      this.clearSecret()
      if (this.form.authType !== 'API_KEY') this.form.credentialHeaderName = ''
      else if (!this.form.credentialHeaderName) this.form.credentialHeaderName = 'X-API-Key'
      this.form.credentialAction = this.form.authType === 'NONE' ? 'CLEAR' : (this.canKeepCredential ? 'KEEP' : 'REPLACE')
      this.$refs.connectionForm?.clearValidate()
    },
    async handleSave() {
      if (this.saving) return
      try { await this.$refs.connectionForm.validate() } catch { return }
      this.saving = true
      try {
        await saveMcpConnection({
          id: this.form.id, revision: this.form.revision, connectionName: this.form.connectionName,
          endpointUrl: this.form.endpointUrl, authType: this.form.authType,
          credentialHeaderName: this.form.authType === 'API_KEY' ? this.form.credentialHeaderName : null,
          credentialAction: this.form.credentialAction,
          secret: this.form.credentialAction === 'REPLACE' ? this.form.secret : undefined,
          timeoutMs: this.form.timeoutMs, status: this.form.status, remark: this.form.remark
        })
        this.clearSecret()
        this.open = false
        ElMessage.success('MCP 连接已保存')
        await this.getList()
      } catch { /* 保存失败时保留表单，便于修正。 */ }
      finally { this.saving = false }
    },
    async handleDelete(row) {
      try {
        await ElMessageBox.confirm(`确认删除 MCP 连接“${row.connectionName}”？`, '删除连接', { type: 'warning' })
        await deleteMcpConnection(row.id, row.revision)
        ElMessage.success('已删除')
        if (this.rows.length === 1 && this.query.pageNum > 1) this.query.pageNum--
        await this.getList()
      } catch { /* 取消及请求错误由调用方/请求组件处理。 */ }
    },
    async handleProbe(row, discover) {
      if (this.probing) return
      this.probing = true
      this.discovering = discover
      this.preview = null
      this.previewTitle = `${discover ? '工具发现预览' : '连接测试'} · ${row.connectionName}`
      this.previewOpen = true
      try {
        const { data } = await probeMcpConnection(row.id, discover)
        this.preview = data
        ElMessage.success(discover ? `已发现 ${data.tools.length} 个工具（仅预览）` : 'MCP 协议初始化成功')
      } catch { /* 不展示远端错误原文。 */ }
      finally { this.probing = false }
    },
    pretty(value) { return JSON.stringify(value, null, 2) },
    authLabel(value) { return { NONE: '无认证', BEARER: 'Bearer', API_KEY: 'API Key' }[value] || value },
    discoveryLabel(value) { return { NEVER: '未发现', SUCCESS: '已刷新', FAILED: '发现失败', STALE: '待复查' }[value] || '未发现' },
    hint(value) { return value == null ? '未声明' : (value ? '是' : '否') }
  }
}
</script>

<style scoped lang="scss">
.mcp-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
.mcp-heading h2 { margin: 0 0 8px; font-size: 20px; color: var(--el-text-color-primary); }
.mcp-heading p { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.6; }
.mcp-heading-actions :deep(.el-button + .el-button) { margin-left: 0; }
.mcp-filters { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 12px 20px; }
.mcp-filters :deep(.el-form-item) { margin: 0 0 16px; }
.mcp-filters :deep(.el-input) { width: 200px; }
.mcp-list-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin: 4px 0 12px; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.mcp-count { margin-left: 6px; color: var(--el-text-color-secondary); font-weight: 400; }
.mcp-list-heading .field-tip { margin-left: 0; font-weight: 400; }
.mcp-table-region { min-height: 220px; }
.mcp-empty { padding: 36px 16px 40px; text-align: center; line-height: 1.6; color: var(--el-text-color-primary); border-top: 1px solid var(--el-border-color-lighter); }
.mcp-empty h3 { margin: 0 0 10px; font-size: 16px; font-weight: 600; }
.mcp-empty p { margin: 0 0 20px; font-size: 13px; color: var(--el-text-color-secondary); }
.mcp-empty-steps { display: flex; justify-content: center; flex-wrap: wrap; gap: 12px 24px; margin-bottom: 24px; font-size: 12px; color: var(--el-text-color-regular); }
.mcp-row-more { margin-left: 12px; vertical-align: middle; }
.mcp-row-more .el-icon { margin-left: 4px; }
:global(.mcp-connection-dialog .el-select) { width: 100%; }
@media (max-width: 600px) {
  .mcp-heading-actions { width: 100%; }
  .mcp-filters :deep(.el-form-item) { margin-bottom: 4px; }
  .mcp-filters { margin-bottom: 16px; }
}
.mcp-notice { margin-bottom: 20px; }
.mcp-notice :deep(.el-alert__title) { color: var(--el-text-color-regular); }
.mcp-preview { min-height: 180px; }
.field-tip { font-size: 12px; color: var(--el-text-color-regular); margin-left: 6px; line-height: 22px; }
.tool-description { white-space: pre-wrap; overflow-wrap: anywhere; }
.schema-label { margin: 12px 0 6px; font-weight: 600; }
.schema-preview { max-height: 300px; overflow: auto; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
