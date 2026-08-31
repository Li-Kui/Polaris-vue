<template>
  <div class="api-connection-step">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />

    <div class="step-heading">
      <div>
        <strong>API 连接</strong>
        <small>选择已有连接或在当前工作流中新建</small>
      </div>
      <el-tag v-if="selectedResource" type="success" size="small">已连接</el-tag>
    </div>

    <el-input v-model="keyword" prefix-icon="Search" clearable placeholder="搜索 API 连接" />
    <div class="connection-list" v-loading="loading">
      <button
        v-for="resource in filteredResources"
        :key="resource.resourceId"
        type="button"
        :class="['connection-row', {selected: resource.resourceId === selectedResourceId}]"
        :disabled="!canEdit || !resource.available"
        @click="$emit('select', resource.resourceId)"
      >
        <span class="connection-row-main">
          <span class="connection-row-icon"><el-icon><Connection /></el-icon></span>
          <span class="connection-row-copy">
            <strong class="connection-name">{{ resource.name || resource.connectorName || resource.attributes?.name || '未命名连接' }}</strong>
            <small class="connection-desc">{{ description(resource) }}</small>
          </span>
        </span>
        <el-icon v-if="resource.resourceId === selectedResourceId" class="connection-status-icon is-selected"><CircleCheck /></el-icon>
        <el-icon v-else class="connection-status-icon"><ArrowRight /></el-icon>
      </button>
      <div v-if="!loading && !filteredResources.length" class="connection-empty">
        <el-icon><Connection /></el-icon>
        <strong>{{ keyword ? '没有匹配的 API 连接' : '暂无可用 API 连接' }}</strong>
        <small>{{ keyword ? '请尝试其他关键词' : '可以直接在下方创建第一个连接' }}</small>
      </div>
    </div>

    <section v-if="canEdit" :class="['inline-create-form', {expanded: creating}]">
      <button type="button" class="create-connection-toggle" @click="toggleCreate">
        <span class="create-connection-title">
          <el-icon><CirclePlus /></el-icon>
          <strong>新建 API 连接</strong>
        </span>
        <el-icon class="create-chevron"><ArrowUp v-if="creating" /><ArrowDown v-else /></el-icon>
      </button>
      <div v-if="creating" class="inline-create-body">
        <ApiConnectorForm ref="connectorFormRef" :form="form" compact />
        <p class="reuse-hint">
          {{ appearance === 'platform'
            ? '连接信息将保存到当前租户，可在其他工作流复用。'
            : '连接信息将保存为管理端共享连接，可在其他工作流复用。' }}
        </p>
        <div class="inline-actions">
          <el-button @click="cancelCreate">取消新建</el-button>
          <el-button type="primary" :loading="saving" @click="saveAndUse">保存连接并继续</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import {ArrowDown, ArrowRight, ArrowUp, CircleCheck, CirclePlus, Connection} from '@element-plus/icons-vue'
import {addConnector} from '@/api/platform/connector'
import {createWorkflowApiConnector} from '@/api/ai/workflow'
import ApiConnectorForm from '@/components/platform/ApiConnectorForm.vue'

export default {
  name: 'WorkflowApiConnectionStep',
  components: {
    ApiConnectorForm,
    ArrowDown,
    ArrowRight,
    ArrowUp,
    CircleCheck,
    CirclePlus,
    Connection
  },
  props: {
    resources: {type: Array, default: () => []},
    selectedResourceId: {type: String, default: ''},
    selectedResource: {type: Object, default: null},
    environment: {type: String, default: 'PROD'},
    loading: {type: Boolean, default: false},
    canEdit: {type: Boolean, default: false},
    appearance: {type: String, default: 'admin'},
    error: {type: String, default: ''}
  },
  emits: ['select', 'created'],
  data() {
    return {
      keyword: '',
      creating: false,
      emptyStateHandled: false,
      saving: false,
      form: this.emptyForm()
    }
  },
  computed: {
    filteredResources() {
      const keyword = this.keyword.trim().toLowerCase()
      if (!keyword) return this.resources
      return this.resources.filter(resource => {
        const attributes = resource.attributes || {}
        return [resource.name, resource.description, attributes.baseUrl]
          .filter(Boolean)
          .some(value => String(value).toLowerCase().includes(keyword))
      })
    }
  },
  watch: {
    loading(value) {
      if (!value) this.openCreateForEmptyState()
    },
    resources() {
      this.openCreateForEmptyState()
    }
  },
  mounted() {
    this.openCreateForEmptyState()
  },
  methods: {
    emptyForm() {
      return {
        id: undefined,
        connectorName: '',
        baseUrl: '',
        authType: 'NONE',
        originalAuthType: 'NONE',
        credentialAction: 'CLEAR',
        credentialConfigured: false,
        credential: {headerName: 'X-API-Key', secret: ''},
        headerRows: [],
        responseSchemaText: '',
        timeoutSeconds: 30,
        status: '0',
        remark: ''
      }
    },
    description(resource) {
      if (!resource) return ''
      const attributes = resource.attributes || {}
      const baseUrl = attributes.baseUrl || resource.baseUrl || ''
      const authType = attributes.authType || resource.authType
      const isConfigured = attributes.credentialConfigured ?? resource.credentialConfigured ?? false
      const authLabel = authType && authType !== 'NONE'
        ? `${authType} · ${isConfigured ? '凭证已配置' : '凭证缺失'}`
        : (authType === 'NONE' ? '无需认证' : '')
      const desc = resource.description || resource.remark || ''
      return [baseUrl, authLabel, desc].filter(Boolean).join(' · ')
    },
    toggleCreate() {
      this.creating = !this.creating
      this.emptyStateHandled = true
      if (this.creating) {
        this.form = this.emptyForm()
        this.$nextTick(() => this.$refs.connectorFormRef?.$el?.querySelector('input')?.focus())
      }
    },
    cancelCreate() {
      this.creating = false
      this.emptyStateHandled = true
      this.form = this.emptyForm()
    },
    openCreateForEmptyState() {
      if (this.loading
          || this.emptyStateHandled
          || this.resources.length) return
      this.creating = true
      this.emptyStateHandled = true
    },
    async saveAndUse() {
      try {
        await this.$refs.connectorFormRef.validate()
      } catch (error) {
        return
      }
      this.saving = true
      try {
        const createConnector = this.appearance === 'platform'
          ? addConnector
          : createWorkflowApiConnector
        const response = await createConnector(this.$refs.connectorFormRef.buildPayload())
        const connector = response.data
        if (!connector?.id) throw new Error('连接器创建成功，但未返回连接器 ID')
        this.creating = false
        this.emptyStateHandled = true
        this.form = this.emptyForm()
        this.$emit('created', String(connector.id))
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped lang="scss">
.api-connection-step {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 100%;
}

.step-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 28px;

  > div {
    display: inline-flex;
    align-items: baseline;
    gap: 9px;
    min-width: 0;
  }

  strong,
  small {
    display: block;
  }

  small {
    margin-top: 0;
    color: var(--el-text-color-secondary);
    font-size: 11px;
  }
}

.connection-list {
  display: flex;
  flex-direction: column;
  min-height: 72px;
  gap: 6px;
}

.connection-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 58px;
  padding: 10px 14px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 9px;
  background: var(--workflow-surface, var(--el-bg-color));
  color: var(--workflow-text, var(--el-text-color-primary));
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--workflow-primary, var(--el-color-primary));
    background: var(--workflow-hover, var(--el-fill-color-light));
  }

  &.selected {
    border-color: var(--workflow-primary, var(--el-color-primary));
    background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
    box-shadow: 0 0 0 1px var(--workflow-primary, var(--el-color-primary)) inset;

    .connection-name {
      color: var(--workflow-primary, var(--el-color-primary));
      font-weight: 700;
    }

    .connection-desc {
      color: var(--workflow-text, #334155);
      opacity: 0.88;
    }
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }
}

.connection-row-main {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.connection-row-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.connection-name {
  display: block;
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--workflow-text, var(--el-text-color-primary));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connection-desc {
  display: block;
  max-width: 360px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connection-row-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 16px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.connection-status-icon {
  flex: 0 0 auto;
  font-size: 16px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));

  &.is-selected {
    color: var(--workflow-primary, var(--el-color-primary));
  }
}

.connection-empty {
  min-height: 72px;
  padding: 10px 14px;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 32px minmax(0, auto);
  grid-template-rows: auto auto;
  align-content: center;
  justify-content: start;
  column-gap: 11px;
  row-gap: 2px;
  border: 1px dashed var(--workflow-border-strong, var(--el-border-color));
  border-radius: 10px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));

  > .el-icon {
    grid-row: 1 / 3;
    align-self: center;
    margin-bottom: 0;
    color: var(--workflow-primary, var(--el-color-primary));
    font-size: 22px;
  }

  strong {
    grid-column: 2;
    color: var(--workflow-text, var(--el-text-color-primary));
    font-size: 13px;
  }

  small {
    grid-column: 2;
    font-size: 11px;
  }
}

.inline-create-form {
  border: 1px dashed var(--workflow-primary, var(--el-color-primary));
  border-radius: 12px;
  background: var(--workflow-surface, var(--el-bg-color));
  overflow: visible;
}

.inline-create-form.expanded {
  box-shadow: 0 8px 24px color-mix(in srgb, var(--workflow-primary, #625bf6) 8%, transparent);
}

.inline-create-form :deep(.el-input__wrapper.is-focus),
.inline-create-form :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--workflow-primary, var(--el-color-primary)) inset;
}

.create-connection-toggle {
  width: 100%;
  min-height: 42px;
  padding: 0 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 0;
  color: var(--workflow-primary, var(--el-color-primary));
  background: transparent;
  cursor: pointer;
}

.create-connection-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  .el-icon {
    font-size: 20px;
  }

  strong {
    font-size: 14px;
  }
}

.create-chevron {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
}

.inline-create-body {
  padding: 8px 14px 0;
}

.inline-actions {
  position: sticky;
  bottom: -24px;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin: 10px -14px 0;
  padding: 10px 14px;
  border-top: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  background: var(--workflow-surface, var(--el-bg-color));

  :deep(.el-button) {
    min-width: 112px;
    height: 38px;
    border-radius: 8px;
  }

  :deep(.el-button--primary) {
    --el-button-bg-color: var(--workflow-primary, var(--el-color-primary));
    --el-button-border-color: var(--workflow-primary, var(--el-color-primary));
    --el-button-hover-bg-color: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
    --el-button-hover-border-color: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
  }
}

.reuse-hint {
  margin: 8px 0 0;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
}
</style>
