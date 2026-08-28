<template>
  <div class="connection-step">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="step-heading">
      <div><strong>选择数据库连接</strong><small>{{ environment }} · 默认仅当前工作流生效</small></div>
      <el-tag v-if="selectedResource" type="success" size="small">已连接</el-tag>
    </div>
    <el-input v-model="keyword" prefix-icon="Search" clearable placeholder="搜索数据库连接" />
    <div class="connection-list" v-loading="loading">
      <button
        v-for="resource in filteredResources"
        :key="resource.resourceId"
        type="button"
        :class="['connection-row', {selected: resource.resourceId === selectedResourceId}]"
        :disabled="!canEdit || !resource.available"
        @click="$emit('select', resource.resourceId)"
      >
        <span class="connection-row-copy">
          <strong class="connection-name">{{ resource.name || resource.dsName || '未命名数据源' }}</strong>
          <small class="connection-desc">{{ description(resource) }}</small>
        </span>
        <el-icon v-if="resource.resourceId === selectedResourceId" class="connection-status-icon is-selected"><CircleCheck /></el-icon>
        <el-icon v-else class="connection-status-icon"><ArrowRight /></el-icon>
      </button>
      <el-empty v-if="!loading && !filteredResources.length" description="暂无可用数据库连接" :image-size="64" />
    </div>

    <button
      v-if="appearance === 'platform' && canEdit"
      type="button"
      class="create-toggle"
      @click="toggleCreate"
    >
      <el-icon><Plus /></el-icon><span>{{ creating ? '收起新建连接' : '新建数据库连接' }}</span>
    </button>
    <section v-if="creating" class="inline-create-form">
      <DatasourceForm ref="datasourceFormRef" :form="form" />
      <el-alert title="连接将独立保存并生成第一个已验证版本，可被其他工作流复用。" type="info" :closable="false" show-icon />
      <div class="inline-actions">
        <el-button @click="cancelCreate">取消新建</el-button>
        <el-button type="primary" :loading="saving" @click="saveAndUse">验证、保存并继续</el-button>
      </div>
    </section>
  </div>
</template>

<script>
import {ArrowRight, CircleCheck, Plus} from '@element-plus/icons-vue'
import {addDatasource} from '@/api/platform/datasource'
import DatasourceForm from '@/components/platform/DatasourceForm.vue'

export default {
  name: 'WorkflowDatasourceConnectionStep',
  components: {DatasourceForm, ArrowRight, CircleCheck, Plus},
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
    return {keyword: '', creating: false, saving: false, form: this.emptyForm()}
  },
  computed: {
    filteredResources() {
      const keyword = this.keyword.trim().toLowerCase()
      if (!keyword) return this.resources
      return this.resources.filter(resource => {
        const attributes = resource.attributes || {}
        return [resource.name, attributes.type, attributes.host, attributes.databaseName]
          .filter(Boolean)
          .some(value => String(value).toLowerCase().includes(keyword))
      })
    }
  },
  methods: {
    emptyForm() {
      return {
        id: undefined,
        dsName: '',
        dsType: 'MYSQL',
        host: '',
        port: 3306,
        databaseName: '',
        username: '',
        credentialAction: 'REPLACE',
        credential: {password: ''},
        passwordConfigured: false,
        sslEnabled: false,
        connectTimeoutSeconds: 5,
        queryTimeoutSeconds: 10,
        status: '0',
        remark: ''
      }
    },
    description(resource) {
      const attributes = resource.attributes || {}
      const target = [attributes.host && `${attributes.host}:${attributes.port || ''}`, attributes.databaseName]
        .filter(Boolean).join('/')
      return [attributes.type, target, attributes.configVersion && `v${attributes.configVersion}`]
        .filter(Boolean).join(' · ')
    },
    toggleCreate() {
      this.creating = !this.creating
      if (this.creating) this.form = this.emptyForm()
    },
    cancelCreate() {
      this.creating = false
      this.form = this.emptyForm()
    },
    async saveAndUse() {
      try {
        await this.$refs.datasourceFormRef.validate()
      } catch (error) {
        return
      }
      this.saving = true
      try {
        const response = await addDatasource(this.$refs.datasourceFormRef.buildPayload())
        const datasource = response.data
        if (!datasource?.id) throw new Error('连接创建成功，但未返回数据源 ID')
        this.creating = false
        this.form = this.emptyForm()
        this.$emit('created', String(datasource.id))
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped lang="scss">
.connection-step {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.step-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.step-heading strong,
.step-heading small {
  display: block;
}

.step-heading small {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.connection-list {
  display: flex;
  flex-direction: column;
  min-height: 80px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  overflow: hidden;
}

.connection-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 0;
  border-bottom: 1px solid var(--workflow-border, var(--el-border-color-extra-light));
  background: var(--workflow-surface, var(--el-bg-color));
  color: var(--workflow-text, var(--el-text-color-primary));
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: var(--workflow-hover, var(--el-fill-color-light));
  }

  &.selected {
    background: var(--workflow-primary-soft, var(--el-color-primary-light-9));

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

.connection-status-icon {
  flex: 0 0 auto;
  font-size: 16px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));

  &.is-selected {
    color: var(--workflow-primary, var(--el-color-primary));
  }
}

.create-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  border: 1px dashed var(--el-color-primary-light-5);
  border-radius: 9px;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
}

.inline-create-form {
  padding: 16px;
  border: 1px dashed var(--el-color-primary-light-5);
  border-radius: 12px;
  background: var(--el-color-primary-light-9);
}

.inline-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
</style>
