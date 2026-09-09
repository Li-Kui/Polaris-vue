<template>
  <div class="trigger-page">
    <header class="trigger-header">
      <div>
        <el-button link icon="Back" @click="$emit('back')">返回工作流</el-button>
        <h2>自动触发器</h2>
        <p>触发器始终锁定已发布版本，修改草稿不会改变现有自动任务。</p>
      </div>
      <div class="header-actions">
        <el-button icon="Refresh" :loading="loading" @click="loadTriggers">刷新</el-button>
        <el-button v-if="canEdit" type="primary" icon="Plus" @click="openCreate">新建触发器</el-button>
      </div>
    </header>

    <el-alert
      title="触发配置不能保存令牌、密码或密钥；认证信息必须通过当前工作流资源绑定解析。"
      type="info"
      show-icon
      :closable="false"
      class="trigger-tip"
    />

    <div class="table-shell">
      <el-table v-loading="loading" :data="triggers" class="polaris-el-table">
        <el-table-column prop="triggerType" label="类型" width="120">
          <template #default="{row}">
            <el-tag :type="typeStyle(row.triggerType)">{{ typeLabel(row.triggerType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerId" label="触发器 ID" min-width="250" show-overflow-tooltip />
        <el-table-column prop="definitionId" label="工作流 ID" width="120" />
        <el-table-column prop="workflowVersionId" label="发布版本" min-width="230" show-overflow-tooltip />
        <el-table-column label="配置" min-width="220" show-overflow-tooltip>
          <template #default="{row}">{{ configSummary(row) }}</template>
        </el-table-column>
        <el-table-column prop="nextFireTime" label="下次触发" width="180" />
        <el-table-column label="最近结果" width="130">
          <template #default="{row}">
            <el-tag v-if="row.lastTriggerStatus" :type="row.lastTriggerStatus === 'DISPATCHED' ? 'success' : 'danger'" size="small">
              {{ row.lastTriggerStatus }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{row}">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="120" align="center" fixed="right">
          <template #default="{row}">
            <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogOpen" class="workflow-dialog" title="新建自动触发器" width="620px">
      <el-form label-width="120px">
        <el-form-item label="工作流">
          <el-select v-model="form.definitionId" filterable style="width: 100%" @change="definitionChanged">
            <el-option
              v-for="item in publishedDefinitions"
              :key="item.id"
              :label="`${item.workflowName} (${item.workflowCode})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="锁定发布版本">
          <el-select v-model="form.workflowVersionId" :loading="versionLoading" style="width: 100%">
            <el-option
              v-for="version in versions"
              :key="version.versionId"
              :label="`版本 ${version.versionNo} · ${version.publishedTime || ''}`"
              :value="version.versionId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="触发类型">
          <el-radio-group v-model="form.triggerType">
            <el-radio-button label="SCHEDULE">定时</el-radio-button>
            <el-radio-button label="WEBHOOK">Webhook</el-radio-button>
            <el-radio-button label="EVENT">事件</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.triggerType === 'SCHEDULE'">
          <el-form-item label="Cron 表达式">
            <el-input v-model="form.cron" placeholder="0 0 * * * *（每小时）" />
          </el-form-item>
          <el-form-item label="时区">
            <el-input v-model="form.timezone" placeholder="Asia/Shanghai" />
          </el-form-item>
        </template>
        <template v-else-if="form.triggerType === 'WEBHOOK'">
          <el-form-item label="请求方式"><el-input model-value="POST" disabled /></el-form-item>
          <el-alert title="创建后使用触发器 ID 作为受控调用标识；鉴权仍由平台 API Key 承担。" type="warning" :closable="false" />
        </template>
        <el-form-item v-else label="事件类型">
          <el-input v-model="form.eventType" placeholder="例如 order.created" />
        </el-form-item>
        <el-form-item label="去重窗口（秒）">
          <el-input-number v-model="form.dedupWindowSeconds" :min="0" :max="604800" style="width: 100%" />
        </el-form-item>
        <el-form-item label="去重键表达式">
          <el-input v-model="form.dedupKeyExpression" placeholder="例如 $.input.orderId；留空表示不启用业务去重" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建并启用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  createWorkflowTrigger,
  listWorkflowDefinitions,
  listWorkflowTriggers,
  listWorkflowVersions,
  updateWorkflowTriggerStatus
} from '@/api/ai/workflow'

export default {
  name: 'WorkflowTriggers',
  props: {
    canEdit: {
      type: Boolean,
      default: false
    }
  },
  emits: ['back'],
  data() {
    return {
      loading: false,
      saving: false,
      versionLoading: false,
      dialogOpen: false,
      triggers: [],
      definitions: [],
      versions: [],
      form: this.emptyForm()
    }
  },
  computed: {
    publishedDefinitions() {
      return this.definitions.filter(item => item.currentPublishedVersionId)
    }
  },
  created() {
    this.loadPage()
  },
  methods: {
    emptyForm() {
      return {
        definitionId: null,
        workflowVersionId: null,
        triggerType: 'SCHEDULE',
        cron: '0 0 * * * *',
        timezone: 'Asia/Shanghai',
        eventType: '',
        dedupWindowSeconds: 0,
        dedupKeyExpression: ''
      }
    },
    async loadPage() {
      this.loading = true
      try {
        const [definitions, triggers] = await Promise.all([
          listWorkflowDefinitions(),
          listWorkflowTriggers({})
        ])
        this.definitions = definitions.data || []
        this.triggers = triggers.data || []
      } finally {
        this.loading = false
      }
    },
    async loadTriggers() {
      this.loading = true
      try {
        const response = await listWorkflowTriggers({})
        this.triggers = response.data || []
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      this.form = this.emptyForm()
      this.versions = []
      this.dialogOpen = true
    },
    async definitionChanged(definitionId) {
      this.form.workflowVersionId = null
      this.versionLoading = true
      try {
        const response = await listWorkflowVersions(definitionId)
        this.versions = response.data || []
        if (this.versions.length) this.form.workflowVersionId = this.versions[0].versionId
      } finally {
        this.versionLoading = false
      }
    },
    triggerConfig() {
      if (this.form.triggerType === 'SCHEDULE') {
        return {cron: this.form.cron, timezone: this.form.timezone}
      }
      if (this.form.triggerType === 'WEBHOOK') return {method: 'POST'}
      return {eventType: this.form.eventType}
    },
    async save() {
      if (!this.form.definitionId || !this.form.workflowVersionId) {
        this.$message.warning('请选择工作流和发布版本')
        return
      }
      this.saving = true
      try {
        await createWorkflowTrigger({
          definitionId: this.form.definitionId,
          workflowVersionId: this.form.workflowVersionId,
          triggerType: this.form.triggerType,
          config: this.triggerConfig(),
          dedupPolicy: {
            windowSeconds: this.form.dedupWindowSeconds,
            keyExpression: this.form.dedupKeyExpression || null
          }
        })
        this.$message.success('触发器已创建并启用')
        this.dialogOpen = false
        await this.loadTriggers()
      } finally {
        this.saving = false
      }
    },
    async toggleStatus(row) {
      const status = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
      await updateWorkflowTriggerStatus(row.triggerId, {
        status,
        expectedLockVersion: row.lockVersion
      })
      this.$message.success(status === 'ACTIVE' ? '触发器已启用' : '触发器已停用')
      await this.loadTriggers()
    },
    configSummary(row) {
      try {
        const config = JSON.parse(row.configJson || '{}')
        return config.cron || config.eventType || config.method || '-'
      } catch (error) {
        return '-'
      }
    },
    typeLabel(type) {
      return {SCHEDULE: '定时', WEBHOOK: 'Webhook', EVENT: '事件'}[type] || type
    },
    typeStyle(type) {
      return {SCHEDULE: 'primary', WEBHOOK: 'success', EVENT: 'warning'}[type] || 'info'
    }
  }
}
</script>

<style scoped>
.trigger-page {
  padding: 24px;
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  border-radius: var(--workflow-radius, 18px);
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: var(--workflow-shadow, none);
}

.trigger-header,
.header-actions {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.trigger-header h2 {
  margin: 10px 0 6px;
  font-size: 24px;
}

.trigger-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.trigger-tip {
  margin: 18px 0;
}

.table-shell {
  overflow: hidden;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 12px;
}

.table-shell :deep(.el-table__inner-wrapper::before) {
  display: none;
}

@media (max-width: 760px) {
  .trigger-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
