<template>
  <div class="binding-page">
    <div class="page-header">
      <div class="page-heading">
        <el-button icon="Back" circle @click="$emit('back')" />
        <div>
          <span class="page-title">工作流资源绑定</span>
          <small>按环境管理逻辑资源与实际资源的映射关系</small>
        </div>
      </div>
      <div class="header-actions">
        <el-select v-model="environment" style="width: 120px" @change="loadBindings">
          <el-option label="开发 DEV" value="DEV" />
          <el-option label="测试 TEST" value="TEST" />
          <el-option label="生产 PROD" value="PROD" />
        </el-select>
        <el-button :loading="loading" icon="Refresh" @click="loadBindings">刷新</el-button>
        <el-button v-if="canEdit" type="primary" icon="Plus" @click="openCreate">新增绑定</el-button>
      </div>
    </div>

    <el-alert
      title="工作流只保存逻辑键；每次执行会把逻辑键解析为当前资源并固化快照。修改绑定不会影响已创建的执行。"
      type="info"
      show-icon
      :closable="false"
      class="binding-tip"
    />

    <div class="binding-table-frame">
      <el-table v-loading="loading" :data="bindings" class="polaris-el-table binding-table">
      <el-table-column label="环境" width="110">
        <template #default="{row}">
          <span :class="['environment-badge', `environment-badge--${row.environment?.toLowerCase()}`]">
            {{ row.environment }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="作用范围" width="150">
        <template #default="{row}">
          <span :class="['scope-badge', {'scope-badge--workflow': row.scopeType === 'WORKFLOW'}]">
            <i></i>
            {{ scopeLabel(row.scopeType) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="资源类型" width="180">
        <template #default="{row}">
          <div class="resource-kind">
            <strong>{{ resourceKindLabel(row.resourceKind) }}</strong>
            <code>{{ row.resourceKind }}</code>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="resourceKey" label="逻辑键" min-width="180" />
      <el-table-column label="已关联资源" min-width="240">
        <template #default="{row}">
          <div class="resource-cell">
            <strong>{{ resourceName(row) }}</strong>
            <span>{{ row.resourceId }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="90" align="center">
        <template #default="{row}">v{{ row.bindingVersion }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{row}">
          <span :class="['status-badge', {'status-badge--active': row.status === 'ACTIVE'}]">
            <i></i>{{ row.status === 'ACTIVE' ? '已启用' : '已停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="150" align="center" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="disable(row)">停用</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="当前环境暂无资源绑定" :image-size="72" />
      </template>
      </el-table>
    </div>

    <el-dialog v-model="dialogOpen" :title="form.id ? '修改资源绑定' : '新增资源绑定'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="作用范围">
          <div :class="['scope-summary', {'scope-summary--workflow': form.scopeType === 'WORKFLOW'}]">
            <span class="scope-summary-icon"><el-icon><Connection /></el-icon></span>
            <span>
              <strong>{{ scopeLabel(form.scopeType) }}</strong>
              <small>
                {{ form.scopeType === 'WORKFLOW'
                  ? `仅供工作流 #${form.definitionId} 使用，优先于共享绑定`
                  : '可被当前租户下的工作流作为默认资源使用' }}
              </small>
            </span>
          </div>
        </el-form-item>
        <el-form-item label="环境">
          <el-select v-model="form.environment" style="width: 100%" @change="loadDialogResources">
            <el-option label="开发 DEV" value="DEV" />
            <el-option label="测试 TEST" value="TEST" />
            <el-option label="生产 PROD" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="form.resourceKind" style="width: 100%" @change="resourceKindChanged">
            <el-option label="大模型" value="MODEL" />
            <el-option label="AI 智能体" value="AGENT" />
            <el-option label="知识库" value="KNOWLEDGE_BASE" />
            <el-option label="API 连接器" value="API_CONNECTOR" />
            <el-option label="外部数据源" value="DATASOURCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="逻辑键">
          <el-input v-model="form.resourceKey" placeholder="例如 primary_model" />
        </el-form-item>
        <el-form-item label="关联现有资源">
          <el-select
            v-model="form.resourceId"
            filterable
            :loading="resourceLoading"
            placeholder="按名称搜索可用资源"
            style="width: 100%"
          >
            <el-option
              v-for="resource in dialogResources"
              :key="resource.resourceId"
              :label="resource.name"
              :value="resource.resourceId"
              :disabled="!resource.available"
            >
              <div class="resource-option">
                <span><strong>{{ resource.name }}</strong><small>{{ resourceDescription(resource) }}</small></span>
                <el-tag v-if="resource.shared" size="small" type="info">共享</el-tag>
                <el-tag v-else-if="resource.available" size="small" type="success">可用</el-tag>
                <el-tooltip v-else :content="resource.unavailableReason" placement="left">
                  <el-tag size="small" type="danger">不可用</el-tag>
                </el-tooltip>
              </div>
            </el-option>
          </el-select>
          <small v-if="selectedResource?.unavailableReason" class="resource-warning">
            {{ selectedResource.unavailableReason }}
          </small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存并校验</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {Connection} from '@element-plus/icons-vue'
import {
  disableWorkflowResourceBinding,
  listWorkflowResourceBindings,
  listWorkflowResources,
  saveWorkflowResourceBinding
} from '@/api/ai/workflow'

export default {
  name: 'WorkflowResourceBindings',
  components: {Connection},
  props: {
    canEdit: {
      type: Boolean,
      default: false
    }
  },
  emits: ['back'],
  data() {
    return {
      environment: 'PROD',
      bindings: [],
      loading: false,
      saving: false,
      resourceLoading: false,
      dialogOpen: false,
      resourceCatalog: {},
      dialogResources: [],
      form: this.emptyForm()
    }
  },
  computed: {
    selectedResource() {
      return this.dialogResources.find(item => item.resourceId === this.form.resourceId) || null
    }
  },
  created() {
    this.loadBindings()
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        definitionId: null,
        scopeType: 'OWNER',
        environment: this.environment,
        resourceKind: 'MODEL',
        resourceKey: '',
        resourceId: '',
        expectedLockVersion: null
      }
    },
    async loadBindings() {
      this.loading = true
      try {
        const response = await listWorkflowResourceBindings({
          environment: this.environment
        })
        this.bindings = response.data || []
        await this.loadCatalogs()
      } finally {
        this.loading = false
      }
    },
    async openCreate() {
      this.form = this.emptyForm()
      this.dialogOpen = true
      await this.loadDialogResources()
    },
    async openEdit(row) {
      this.form = {
        id: row.id,
        definitionId: row.definitionId,
        scopeType: row.scopeType || 'OWNER',
        environment: row.environment,
        resourceKind: row.resourceKind,
        resourceKey: row.resourceKey,
        resourceId: row.resourceId,
        expectedLockVersion: row.lockVersion
      }
      this.dialogOpen = true
      await this.loadDialogResources()
    },
    async save() {
      if (!this.form.resourceKey || !this.form.resourceId) {
        this.$message.warning('逻辑键和关联资源不能为空')
        return
      }
      this.saving = true
      try {
        await saveWorkflowResourceBinding(this.form)
        this.$message.success('资源绑定已保存')
        this.dialogOpen = false
        await this.loadBindings()
      } finally {
        this.saving = false
      }
    },
    async disable(row) {
      await this.$confirm(`确认停用资源绑定 ${row.resourceKind}:${row.resourceKey}？`, '停用确认', {type: 'warning'})
      await disableWorkflowResourceBinding(row.id)
      this.$message.success('资源绑定已停用')
      await this.loadBindings()
    },
    async loadCatalogs() {
      const kinds = ['MODEL', 'AGENT', 'KNOWLEDGE_BASE', 'API_CONNECTOR', 'DATASOURCE']
      const responses = await Promise.all(kinds.map(kind => listWorkflowResources({
        kind,
        environment: this.environment
      }).catch(() => ({data: []}))))
      this.resourceCatalog = kinds.reduce((result, kind, index) => {
        result[kind] = responses[index].data || []
        return result
      }, {})
    },
    async loadDialogResources() {
      this.resourceLoading = true
      try {
        const response = await listWorkflowResources({
          kind: this.form.resourceKind,
          environment: this.form.environment
        })
        this.dialogResources = response.data || []
      } finally {
        this.resourceLoading = false
      }
    },
    async resourceKindChanged() {
      this.form.resourceId = ''
      await this.loadDialogResources()
    },
    resourceName(row) {
      return (this.resourceCatalog[row.resourceKind] || [])
        .find(item => item.resourceId === row.resourceId)?.name || '资源不可见或已删除'
    },
    resourceDescription(resource) {
      const details = resource.attributes || {}
      return resource.description || details.modelName || details.type || details.baseUrl || ''
    },
    scopeLabel(scopeType) {
      return scopeType === 'WORKFLOW' ? '当前工作流' : '租户共享'
    },
    resourceKindLabel(resourceKind) {
      const labels = {
        MODEL: '大模型',
        AGENT: 'AI 智能体',
        KNOWLEDGE_BASE: '知识库',
        API_CONNECTOR: 'API 连接器',
        DATASOURCE: '外部数据源'
      }
      return labels[resourceKind] || resourceKind
    }
  }
}
</script>

<style scoped>
.binding-page {
  padding: 26px;
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  border-radius: var(--workflow-radius, 18px);
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: var(--workflow-shadow, none);
}

.page-header,
.header-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.page-header {
  flex-wrap: wrap;
}

.page-heading {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-heading > div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
}

.page-heading small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 12px;
}

.header-actions :deep(.el-button--primary) {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: #fff;
  background: var(--workflow-primary, var(--el-color-primary));
}

.binding-tip {
  margin: 16px 0;
}

.binding-table-frame {
  overflow: hidden;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 14px;
}

.binding-table {
  min-width: 1120px;
}

.binding-table :deep(.el-table__header th.el-table__cell) {
  height: 48px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  font-size: 12px;
  font-weight: 700;
}

.binding-table :deep(.el-table__row td.el-table__cell) {
  height: 72px;
}

.binding-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: var(--workflow-hover, var(--el-fill-color-light));
}

.environment-badge,
.scope-badge,
.status-badge {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 700;
}

.environment-badge {
  min-width: 48px;
  justify-content: center;
  padding: 5px 8px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 7px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  letter-spacing: 0.04em;
}

.environment-badge--prod {
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 30%, transparent);
  color: var(--workflow-primary, #625bf6);
  background: color-mix(in srgb, var(--workflow-primary, #625bf6) 9%, var(--workflow-surface, #fff));
}

.scope-badge,
.status-badge {
  gap: 7px;
  padding: 5px 9px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 999px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-light));
}

.scope-badge i,
.status-badge i {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 14%, transparent);
}

.scope-badge--workflow {
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 32%, transparent);
  color: var(--workflow-primary, #625bf6);
  background: color-mix(in srgb, var(--workflow-primary, #625bf6) 11%, var(--workflow-surface, #fff));
}

.status-badge--active {
  border-color: color-mix(in srgb, #10a36e 30%, transparent);
  color: #087c55;
  background: color-mix(in srgb, #10a36e 10%, var(--workflow-surface, #fff));
}

.resource-kind {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.resource-kind strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 13px;
}

.resource-kind code {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.resource-cell,
.resource-option > span {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.resource-cell span,
.resource-option small,
.resource-warning {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.scope-summary {
  width: 100%;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 11px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 10px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.scope-summary--workflow {
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 30%, transparent);
  background: color-mix(in srgb, var(--workflow-primary, #625bf6) 8%, var(--workflow-surface, #fff));
}

.scope-summary-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
}

.scope-summary > span:last-child {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.scope-summary strong {
  color: var(--workflow-text, var(--el-text-color-primary));
}

.scope-summary small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  line-height: 1.5;
}

.resource-option {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.resource-option > span {
  min-width: 0;
}

.resource-option small {
  max-width: 330px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-warning {
  display: block;
  margin-top: 6px;
  color: var(--el-color-danger);
}

@media (max-width: 760px) {
  .binding-page {
    padding: 16px;
  }

  .page-header,
  .header-actions {
    align-items: stretch;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.el-select),
  .header-actions :deep(.el-button) {
    flex: 1;
  }

  .binding-table-frame {
    overflow-x: auto;
  }
}
</style>
