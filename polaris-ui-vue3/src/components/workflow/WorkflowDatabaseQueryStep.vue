<template>
  <div class="query-step">
    <el-alert
      title="仅允许单条 SELECT / WITH 查询；使用 :参数名 引用节点输入。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-form-item label="只读 SQL">
      <el-input
        :model-value="draft.sql"
        type="textarea"
        :rows="11"
        :disabled="disabled"
        placeholder="SELECT id, status FROM orders WHERE user_id = :userId"
        class="sql-editor"
        @update:model-value="updateField('sql', $event)"
      />
    </el-form-item>
    <div v-if="parameterNames.length" class="parameter-summary">
      <span>识别到参数</span><el-tag v-for="name in parameterNames" :key="name" size="small">:{{ name }}</el-tag>
    </div>
    <div class="limit-grid">
      <el-form-item label="最大返回行数">
        <el-input-number :model-value="draft.maxRows" :min="1" :max="1000" :disabled="disabled" @update:model-value="updateField('maxRows', $event)" />
      </el-form-item>
      <el-form-item label="查询超时">
        <el-input-number :model-value="draft.queryTimeoutSeconds" :min="1" :max="30" :disabled="disabled" @update:model-value="updateField('queryTimeoutSeconds', $event)" />
        <span class="unit">秒</span>
      </el-form-item>
    </div>
    <small class="field-hint">运行时参数来自该节点的“输入映射”；预览参数只用于当前测试，不会保存到工作流。</small>

    <WorkflowDisclosureCard
      class="preview-collapse"
      name="preview"
      title="预览查询"
      description="使用临时参数验证 SQL 和返回字段"
      badge="测试工具"
      icon="preview"
    >
      <el-form-item label="预览参数 JSON">
        <el-input v-model="previewParameters" type="textarea" :rows="5" placeholder='{"userId": 10001}' />
      </el-form-item>
      <el-button type="primary" plain :loading="previewing" :disabled="!selectedResourceId || !draft.sql" @click="runPreview">
        执行预览
      </el-button>
      <el-alert v-if="!selectedResourceId" title="请先完成第 1 步，选择数据库连接。" type="warning" :closable="false" />
      <el-table v-if="previewRows.length" :data="previewRows" size="small" max-height="280" class="preview-table">
        <el-table-column v-for="column in previewColumns" :key="column" :prop="column" :label="column" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-empty v-else-if="previewed" description="查询成功，未返回数据" :image-size="56" />
    </WorkflowDisclosureCard>
  </div>
</template>

<script>
import {queryDatasource} from '@/api/platform/datasource'
import WorkflowDisclosureCard from './WorkflowDisclosureCard.vue'

export default {
  name: 'WorkflowDatabaseQueryStep',
  components: {WorkflowDisclosureCard},
  props: {
    config: {type: Object, default: () => ({})},
    selectedResourceId: {type: String, default: ''},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config'],
  data() {
    return {previewParameters: '{}', previewing: false, previewRows: [], previewed: false}
  },
  computed: {
    draft() {
      return {
        sql: this.config?.sql || '',
        maxRows: Number(this.config?.maxRows || 100),
        queryTimeoutSeconds: Number(this.config?.queryTimeoutSeconds || 10)
      }
    },
    parameterNames() {
      const names = new Set()
      const pattern = /(^|[^:]):([A-Za-z_][A-Za-z0-9_]*)/g
      let match
      while ((match = pattern.exec(this.draft.sql)) !== null) names.add(match[2])
      return [...names]
    },
    previewColumns() {
      return this.previewRows.length ? Object.keys(this.previewRows[0]) : []
    }
  },
  methods: {
    updateField(field, value) {
      this.$emit('update:config', {...this.draft, [field]: value})
    },
    async runPreview() {
      let parameters
      try {
        parameters = JSON.parse(this.previewParameters || '{}')
        if (!parameters || Array.isArray(parameters) || typeof parameters !== 'object') {
          throw new Error('参数必须是 JSON 对象')
        }
      } catch (error) {
        this.$message.error(error.message || '预览参数 JSON 格式无效')
        return
      }
      this.previewing = true
      try {
        const response = await queryDatasource(this.selectedResourceId, {
          sql: this.draft.sql,
          maxRows: this.draft.maxRows,
          queryTimeoutSeconds: this.draft.queryTimeoutSeconds,
          parameters
        })
        this.previewRows = response.data || []
        this.previewed = true
        this.$message.success(`查询成功，返回 ${this.previewRows.length} 行`)
      } finally {
        this.previewing = false
      }
    }
  }
}
</script>

<style scoped lang="scss">
.query-step {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sql-editor :deep(textarea) {
  font-family: 'JetBrains Mono', Consolas, monospace;
  line-height: 1.65;
}

.parameter-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: -6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.limit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.unit {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
}

.field-hint {
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.preview-collapse {
  margin-top: 4px;
}

.preview-table {
  margin-top: 14px;
}
</style>
