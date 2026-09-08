<template>
  <div :class="['workflow-page', `workflow-page--${appearance}`]">
    <WorkflowList
      v-if="pageMode === 'list'"
      :items="definitions"
      :loading="loading"
      :can-edit="canEdit"
      :can-execute="canExecute"
      :can-approve="canApprove"
      @approvals="pageMode = 'approvals'"
      @bindings="pageMode = 'bindings'"
      @clone="openCloneDialog"
      @create="createDefinition"
      @executions="openExecutions"
      @open="openDefinition"
      @run="openRunDialog"
      @triggers="pageMode = 'triggers'"
      @versions="openVersions"
    />
    <WorkflowWorkbench
      v-else-if="pageMode === 'workbench'"
      :model-value="selectedDefinition"
      :descriptors="descriptors"
      :can-edit="canEdit"
      :can-publish="canPublish"
      :can-execute="canExecute"
      :can-debug="canDebug"
      :appearance="appearance"
      @back="backToList"
      @saved="definitionSaved"
      @published="definitionPublished"
    />
    <WorkflowExecutionMonitor
      v-else-if="pageMode === 'executions'"
      :can-execute="canExecute"
      :definition-id="executionTarget.id"
      :workflow-name="executionTarget.workflowName"
      :workflow-code="executionTarget.workflowCode"
      @back="backToList"
    />
    <WorkflowApprovalInbox
      v-else-if="pageMode === 'approvals'"
      @back="backToList"
    />
    <WorkflowTriggers
      v-else-if="pageMode === 'triggers'"
      :can-edit="canEdit"
      @back="backToList"
    />
    <WorkflowResourceBindings
      v-else
      :can-edit="canEdit"
      @back="backToList"
    />

    <el-dialog
      v-model="runDialogOpen"
      class="workflow-dialog workflow-run-dialog"
      title="运行工作流"
      width="720px"
      destroy-on-close
    >
      <div class="workflow-run-summary">
        <div>
          <span>工作流</span>
          <strong>{{ runTarget?.workflowName }}</strong>
        </div>
        <el-tag type="success" effect="plain">当前发布版本</el-tag>
      </div>
      <el-alert
        v-if="runSchemaError"
        :title="runSchemaError"
        type="warning"
        :closable="false"
        show-icon
      />
      <WorkflowExecutionInput
        ref="runInputEditor"
        v-model="runInput"
        :schema="runInputSchema"
        :loading="runInputLoading"
        @validation="runInputValidation = $event"
      />
      <el-collapse v-model="runAdvancedPanels" class="workflow-run-advanced">
        <el-collapse-item name="advanced">
          <template #title>
            <div class="workflow-run-advanced-title">
              <div>
                <strong>高级选项</strong>
                <span>仅在调用方重试同一次请求时需要配置</span>
              </div>
              <el-tag
                v-if="runForm.idempotencyKey"
                size="small"
                type="success"
                effect="plain"
              >已配置</el-tag>
            </div>
          </template>
          <el-form label-position="top">
            <el-form-item label="幂等键">
              <el-input v-model="runForm.idempotencyKey" placeholder="可选；调用方重试时保持不变" />
              <small class="workflow-run-help">不填写时每次都会创建新执行；仅在调用方重试同一次请求时复用。</small>
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>
      <template #footer>
        <el-button @click="runDialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="starting"
          :disabled="runInputLoading || !runInputValidation.valid"
          @click="startExecution"
        >开始运行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cloneDialogOpen" class="workflow-dialog" title="克隆工作流" width="520px">
      <el-alert
        title="将复制当前草稿内容，新工作流拥有独立编码、草稿和发布版本。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-width="100px" class="dialog-form">
        <el-form-item label="来源">
          <el-input :model-value="cloneTarget?.workflowName" disabled />
        </el-form-item>
        <el-form-item label="新名称">
          <el-input v-model="cloneForm.workflowName" maxlength="128" />
        </el-form-item>
        <el-form-item label="新编码">
          <el-input v-model="cloneForm.workflowCode" maxlength="64" placeholder="字母开头，可使用数字、点、横线和下划线" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cloneDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="cloning" @click="cloneDefinition">创建副本</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="versionDrawerOpen" class="workflow-version-drawer" title="发布版本" size="78%" destroy-on-close>
      <div v-loading="versionLoading" class="version-layout">
        <aside class="version-list">
          <div class="version-list-title">
            <strong>{{ versionTarget?.workflowName }}</strong>
            <code>{{ versionTarget?.workflowCode }}</code>
          </div>
          <button
            v-for="version in versions"
            :key="version.versionId"
            :class="['version-item', {active: selectedVersion?.versionId === version.versionId}]"
            @click="selectVersion(version)"
          >
            <span><strong>版本 {{ version.versionNo }}</strong><el-tag size="small" type="success">{{ version.status }}</el-tag></span>
            <small>{{ version.publishedBy || '-' }} · {{ version.publishedTime || '-' }}</small>
          </button>
          <el-empty v-if="!versionLoading && !versions.length" description="暂无发布版本" :image-size="72" />
        </aside>
        <main class="version-detail">
          <template v-if="selectedVersionDetail">
            <div class="version-detail-header">
              <div>
                <span class="eyebrow">DRAFT COMPARISON</span>
                <h3>草稿与版本 {{ selectedVersionDetail.versionNo }} 对比</h3>
                <p>{{ diffSummary }}</p>
              </div>
              <el-button
                v-if="canEdit"
                type="warning"
                plain
                :loading="rollingBack"
                @click="rollbackVersion"
              >
                恢复为当前草稿
              </el-button>
            </div>
            <div class="diff-grid">
              <section>
                <header><span>当前草稿</span><small>r{{ versionTarget?.draftRevision }}</small></header>
                <pre>{{ prettyDefinition(versionTarget?.draftJson) }}</pre>
              </section>
              <section>
                <header><span>发布版本 {{ selectedVersionDetail.versionNo }}</span><small>{{ selectedVersionDetail.contentHash?.slice(0, 10) }}</small></header>
                <pre>{{ prettyDefinition(selectedVersionDetail.definitionJson) }}</pre>
              </section>
            </div>
          </template>
          <el-empty v-else description="选择一个版本查看对比" />
        </main>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import WorkflowList from '@/components/workflow/WorkflowList.vue'
import WorkflowWorkbench from '@/components/workflow/WorkflowWorkbench.vue'
import WorkflowExecutionMonitor from '@/components/workflow/WorkflowExecutionMonitor.vue'
import WorkflowResourceBindings from '@/components/workflow/WorkflowResourceBindings.vue'
import WorkflowApprovalInbox from '@/components/workflow/WorkflowApprovalInbox.vue'
import WorkflowTriggers from '@/components/workflow/WorkflowTriggers.vue'
import WorkflowExecutionInput from '@/components/workflow/WorkflowExecutionInput.vue'
import {
  cloneWorkflowDefinition,
  getWorkflowDefinition,
  getWorkflowVersion,
  listWorkflowDefinitions,
  listWorkflowNodeDescriptors,
  listWorkflowVersions,
  rollbackWorkflowDraft,
  startWorkflowExecution
} from '@/api/ai/workflow'

export default {
  name: 'WorkflowPage',
  components: {
    WorkflowList,
    WorkflowWorkbench,
    WorkflowExecutionMonitor,
    WorkflowResourceBindings,
    WorkflowApprovalInbox,
    WorkflowTriggers,
    WorkflowExecutionInput
  },
  props: {
    canEdit: {
      type: Boolean,
      default: false
    },
    canPublish: {
      type: Boolean,
      default: false
    },
    canExecute: {
      type: Boolean,
      default: false
    },
    canDebug: {
      type: Boolean,
      default: false
    },
    canApprove: {
      type: Boolean,
      default: false
    },
    appearance: {
      type: String,
      default: 'admin',
      validator: value => ['admin', 'platform'].includes(value)
    }
  },
  data() {
    return {
      pageMode: 'list',
      loading: false,
      definitions: [],
      descriptors: [],
      selectedDefinition: null,
      executionTarget: null,
      runDialogOpen: false,
      starting: false,
      runTarget: null,
      runInput: {},
      runInputSchema: {type: 'object', properties: {}},
      runInputLoading: false,
      runInputValidation: {valid: false, message: '', errors: []},
      runSchemaError: '',
      runAdvancedPanels: [],
      cloneDialogOpen: false,
      cloning: false,
      cloneTarget: null,
      cloneForm: {
        workflowCode: '',
        workflowName: ''
      },
      versionDrawerOpen: false,
      versionLoading: false,
      rollingBack: false,
      versionTarget: null,
      versions: [],
      selectedVersion: null,
      selectedVersionDetail: null,
      runForm: {
        environment: 'PROD',
        idempotencyKey: ''
      }
    }
  },
  created() {
    this.loadPageData()
  },
  computed: {
    diffSummary() {
      if (!this.versionTarget?.draftJson || !this.selectedVersionDetail?.definitionJson) return ''
      const draft = this.prettyDefinition(this.versionTarget.draftJson)
      const published = this.prettyDefinition(this.selectedVersionDetail.definitionJson)
      if (draft === published) return '当前草稿与该发布版本完全一致。'
      const draftLines = draft.split('\n')
      const publishedLines = published.split('\n')
      const changed = Math.max(draftLines.length, publishedLines.length)
        - draftLines.filter((line, index) => line === publishedLines[index]).length
      return `检测到约 ${changed} 行结构差异；恢复操作只更新草稿，不会改变正在运行的实例。`
    }
  },
  methods: {
    async loadPageData() {
      this.loading = true
      try {
        const [definitionResponse, descriptorResponse] = await Promise.all([
          listWorkflowDefinitions(),
          listWorkflowNodeDescriptors()
        ])
        this.definitions = definitionResponse.data || []
        this.descriptors = descriptorResponse.data || []
      } finally {
        this.loading = false
      }
    },
    createDefinition() {
      if (!this.canEdit) return
      this.selectedDefinition = null
      this.pageMode = 'workbench'
    },
    async openDefinition(item) {
      this.loading = true
      try {
        const response = await getWorkflowDefinition(item.id)
        this.selectedDefinition = response.data
        this.pageMode = 'workbench'
      } finally {
        this.loading = false
      }
    },
    async backToList() {
      this.pageMode = 'list'
      this.selectedDefinition = null
      this.executionTarget = null
      await this.loadPageData()
    },
    openExecutions(definition) {
      if (!definition?.id) return
      this.executionTarget = definition
      this.pageMode = 'executions'
    },
    definitionSaved(definition) {
      this.selectedDefinition = definition
    },
    definitionPublished(result) {
      if (this.selectedDefinition && result?.version) {
        this.selectedDefinition.currentPublishedVersionId = result.version.versionId
      }
    },
    openCloneDialog(definition) {
      if (!this.canEdit) return
      this.cloneTarget = definition
      this.cloneForm = {
        workflowName: `${definition.workflowName} 副本`,
        workflowCode: `${definition.workflowCode}_copy`
      }
      this.cloneDialogOpen = true
    },
    async cloneDefinition() {
      if (!this.cloneForm.workflowName.trim() || !this.cloneForm.workflowCode.trim()) {
        this.$message.warning('新工作流名称和编码不能为空')
        return
      }
      this.cloning = true
      try {
        const response = await cloneWorkflowDefinition(this.cloneTarget.id, {
          workflowName: this.cloneForm.workflowName.trim(),
          workflowCode: this.cloneForm.workflowCode.trim()
        })
        this.$message.success('工作流副本已创建')
        this.cloneDialogOpen = false
        await this.loadPageData()
        await this.openDefinition(response.data)
      } finally {
        this.cloning = false
      }
    },
    async openVersions(definition) {
      this.versionTarget = definition
      this.versions = []
      this.selectedVersion = null
      this.selectedVersionDetail = null
      this.versionDrawerOpen = true
      this.versionLoading = true
      try {
        const [definitionResponse, versionsResponse] = await Promise.all([
          getWorkflowDefinition(definition.id),
          listWorkflowVersions(definition.id)
        ])
        this.versionTarget = definitionResponse.data
        this.versions = versionsResponse.data || []
        if (this.versions.length) await this.selectVersion(this.versions[0])
      } finally {
        this.versionLoading = false
      }
    },
    async selectVersion(version) {
      this.selectedVersion = version
      const response = await getWorkflowVersion(this.versionTarget.id, version.versionId)
      if (this.selectedVersion?.versionId === version.versionId) {
        this.selectedVersionDetail = response.data
      }
    },
    async rollbackVersion() {
      await this.$confirm(
        `确认使用发布版本 ${this.selectedVersionDetail.versionNo} 覆盖当前草稿？`,
        '恢复草稿',
        {type: 'warning'}
      )
      this.rollingBack = true
      try {
        const response = await rollbackWorkflowDraft(
          this.versionTarget.id,
          this.selectedVersionDetail.versionId,
          this.versionTarget.draftRevision
        )
        this.versionTarget = response.data
        this.$message.success('已恢复为新草稿，发布版本和运行实例未受影响')
        await this.loadPageData()
      } finally {
        this.rollingBack = false
      }
    },
    async openRunDialog(definition) {
      if (!this.canExecute) return
      this.runTarget = definition
      this.runForm = {
        environment: 'PROD',
        idempotencyKey: ''
      }
      this.runInput = {}
      this.runInputSchema = {type: 'object', properties: {}}
      this.runInputValidation = {valid: false, message: '', errors: []}
      this.runSchemaError = ''
      this.runAdvancedPanels = []
      this.runInputLoading = true
      this.runDialogOpen = true
      try {
        const response = await getWorkflowVersion(
          definition.id,
          definition.currentPublishedVersionId
        )
        this.runInputSchema = this.publishedInputSchema(response.data?.definitionJson)
      } catch (error) {
        this.runSchemaError = '未能读取发布版本的输入契约，仍可切换到 JSON 模式填写。'
      } finally {
        this.runInputLoading = false
        this.$nextTick(() => this.$refs.runInputEditor?.reset())
      }
    },
    async startExecution() {
      if (!this.runInputValidation.valid) {
        this.$message.error(this.runInputValidation.message || '请先修正工作流输入')
        return
      }
      this.starting = true
      try {
        const response = await startWorkflowExecution({
          definitionId: this.runTarget.id,
          workflowVersionId: this.runTarget.currentPublishedVersionId,
          input: this.runInput,
          environment: this.runForm.environment,
          idempotencyKey: this.runForm.idempotencyKey || null
        })
        this.$message.success(`执行已创建：${response.data.executionId}`)
        this.runDialogOpen = false
        this.executionTarget = this.runTarget
        this.pageMode = 'executions'
      } finally {
        this.starting = false
      }
    },
    publishedInputSchema(definitionJson) {
      try {
        const definition = typeof definitionJson === 'string'
          ? JSON.parse(definitionJson) : definitionJson
        const schema = definition?.inputs
        if (!schema || typeof schema !== 'object' || Array.isArray(schema)) {
          return {type: 'object', properties: {}}
        }
        return {
          ...schema,
          type: schema.type || 'object',
          properties: schema.properties && typeof schema.properties === 'object'
            ? schema.properties : {}
        }
      } catch (error) {
        return {type: 'object', properties: {}}
      }
    },
    prettyDefinition(value) {
      if (!value) return '-'
      try {
        return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
      } catch (error) {
        return value
      }
    }
  }
}
</script>

<style scoped>
.workflow-page {
  min-height: 100%;
  --workflow-radius: 18px;
  --workflow-primary: #2563eb;
  --workflow-primary-hover: #1d4ed8;
  --workflow-primary-soft: color-mix(in srgb, #2563eb 10%, var(--el-bg-color));
  --workflow-surface: var(--el-bg-color);
  --workflow-surface-raised: var(--el-bg-color-overlay);
  --workflow-muted: var(--el-fill-color-extra-light);
  --workflow-hover: var(--el-fill-color-light);
  --workflow-border: var(--el-border-color-lighter);
  --workflow-border-strong: var(--el-border-color-light);
  --workflow-canvas: color-mix(in srgb, var(--el-fill-color-extra-light) 72%, var(--el-bg-color));
  --workflow-grid: var(--el-border-color-lighter);
  --workflow-text: var(--el-text-color-primary);
  --workflow-text-secondary: var(--el-text-color-secondary);
  --workflow-shadow: 0 12px 34px rgb(15 23 42 / 6%);
  --workflow-shadow-float: 0 16px 40px rgb(15 23 42 / 12%);
  color: var(--workflow-text);
}

.workflow-page--platform {
  --workflow-radius: 22px;
  --workflow-primary: #6d5dfc;
  --workflow-primary-hover: #574be8;
  --workflow-primary-soft: color-mix(in srgb, #6d5dfc 12%, var(--el-bg-color));
  --workflow-surface: color-mix(in srgb, var(--el-bg-color) 96%, #6d5dfc);
  --workflow-surface-raised: color-mix(in srgb, var(--el-bg-color-overlay) 97%, #6d5dfc);
  --workflow-muted: color-mix(in srgb, var(--el-fill-color-extra-light) 90%, #6d5dfc);
  --workflow-hover: color-mix(in srgb, var(--el-fill-color-light) 86%, #6d5dfc);
  --workflow-border: color-mix(in srgb, var(--el-border-color-lighter) 76%, #8b7fff);
  --workflow-border-strong: color-mix(in srgb, var(--el-border-color-light) 78%, #8b7fff);
  --workflow-canvas: color-mix(in srgb, var(--el-fill-color-extra-light) 88%, #6d5dfc);
  --workflow-grid: color-mix(in srgb, var(--el-border-color-lighter) 82%, #8b7fff);
  --workflow-shadow: 0 18px 50px rgb(76 65 180 / 12%);
  --workflow-shadow-float: 0 20px 48px rgb(40 32 110 / 20%);
}

:global(html.dark) .workflow-page {
  --workflow-primary-soft: color-mix(in srgb, var(--workflow-primary) 18%, var(--el-bg-color));
  --workflow-surface: color-mix(in srgb, var(--el-bg-color) 97%, var(--workflow-primary));
  --workflow-surface-raised: color-mix(in srgb, var(--el-bg-color-overlay) 96%, var(--workflow-primary));
  --workflow-muted: color-mix(in srgb, var(--el-fill-color-extra-light) 94%, var(--workflow-primary));
  --workflow-hover: color-mix(in srgb, var(--el-fill-color-light) 91%, var(--workflow-primary));
  --workflow-border: color-mix(in srgb, var(--el-border-color-lighter) 88%, var(--workflow-primary));
  --workflow-border-strong: color-mix(in srgb, var(--el-border-color-light) 86%, var(--workflow-primary));
  --workflow-canvas: color-mix(in srgb, var(--el-bg-color-page) 95%, var(--workflow-primary));
  --workflow-grid: color-mix(in srgb, var(--el-border-color-darker) 82%, var(--workflow-primary));
  --workflow-shadow: 0 16px 38px rgb(0 0 0 / 28%);
  --workflow-shadow-float: 0 22px 52px rgb(0 0 0 / 42%);
}

.tenant-hint {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
}

.dialog-form {
  margin-top: 18px;
}

.workflow-run-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.workflow-run-summary > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.workflow-run-summary span,
.workflow-run-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.workflow-run-advanced {
  margin-top: 14px;
  overflow: hidden;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 11px;
  background: var(--workflow-surface, var(--el-bg-color));
}

.workflow-run-advanced :deep(.el-collapse-item__header) {
  min-height: 58px;
  height: auto;
  padding: 10px 14px;
  border-bottom: 0;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  font-weight: 650;
}

.workflow-run-advanced :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
  background: transparent;
}

.workflow-run-advanced :deep(.el-collapse-item__content) {
  padding: 14px 14px 8px;
}

.workflow-run-advanced-title {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-right: 10px;
}

.workflow-run-advanced-title > div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.workflow-run-advanced-title strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 14px;
  line-height: 20px;
}

.workflow-run-advanced-title span {
  overflow: hidden;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-run-help {
  display: block;
  margin-top: 5px;
}

.version-layout {
  min-height: calc(100vh - 140px);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 18px;
}

.version-list,
.version-detail {
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-bg-color);
}

.version-list {
  padding: 12px;
}

.version-list-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 8px 14px;
}

.version-list-title code,
.version-item small {
  color: var(--el-text-color-secondary);
}

.version-item {
  width: 100%;
  padding: 12px;
  margin-bottom: 8px;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-extra-light);
  cursor: pointer;
  transition: 0.2s ease;
}

.version-item:hover,
.version-item.active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.version-item > span {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 7px;
}

.version-detail {
  padding: 18px;
}

.version-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.version-detail-header h3 {
  margin: 4px 0 6px;
  font-size: 20px;
}

.version-detail-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.eyebrow {
  color: var(--el-color-primary);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.14em;
}

.diff-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.diff-grid section {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.diff-grid header {
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
}

.diff-grid pre {
  height: calc(100vh - 280px);
  min-height: 360px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  font: 12px/1.65 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

@media (max-width: 960px) {
  .version-layout,
  .diff-grid {
    grid-template-columns: 1fr;
  }

  .version-layout {
    min-height: auto;
  }

  .diff-grid pre {
    height: 420px;
  }
}
</style>

<style>
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer) {
  --workflow-control-surface: color-mix(in srgb, var(--workflow-primary, var(--el-color-primary)) 5%, var(--workflow-surface-raised, var(--el-bg-color-overlay)));
  --workflow-control-hover: color-mix(in srgb, var(--workflow-primary, var(--el-color-primary)) 12%, var(--workflow-surface-raised, var(--el-bg-color-overlay)));
  --workflow-control-border: color-mix(in srgb, var(--workflow-text-secondary, var(--el-text-color-secondary)) 48%, var(--workflow-surface, var(--el-bg-color)));
  --workflow-control-icon: color-mix(in srgb, var(--workflow-text, var(--el-text-color-primary)) 76%, var(--workflow-surface, var(--el-bg-color)));
  --workflow-control-disabled-bg: color-mix(in srgb, var(--workflow-text-secondary, var(--el-text-color-secondary)) 12%, var(--workflow-surface, var(--el-bg-color)));
  --workflow-control-disabled-text: color-mix(in srgb, var(--workflow-text-secondary, var(--el-text-color-secondary)) 74%, var(--workflow-surface, var(--el-bg-color)));
  --workflow-danger: #dc2626;
  --workflow-warning: #a44908;
  --workflow-success: #047857;
  --workflow-info: #475569;
}

html.dark :is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer) {
  --workflow-danger: #f87171;
  --workflow-warning: #fbbf24;
  --workflow-success: #34d399;
  --workflow-info: #cbd5e1;
}

/* 工作流中的默认按钮始终保留清晰边界，避免和浅色面板融为一体。 */
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger):not(.el-button--info):not(.is-link):not(.is-text) {
  --el-button-text-color: var(--workflow-control-icon);
  --el-button-bg-color: var(--workflow-control-surface);
  --el-button-border-color: var(--workflow-control-border);
  --el-button-hover-text-color: var(--workflow-primary, var(--el-color-primary));
  --el-button-hover-bg-color: var(--workflow-control-hover);
  --el-button-hover-border-color: var(--workflow-primary, var(--el-color-primary));
  color: var(--workflow-control-icon) !important;
  background: var(--workflow-control-surface) !important;
  border-color: var(--workflow-control-border) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger):not(.el-button--info):not(.is-disabled):not(.is-link):not(.is-text):hover {
  color: var(--workflow-primary, var(--el-color-primary)) !important;
  background: var(--workflow-control-hover) !important;
  border-color: var(--workflow-primary, var(--el-color-primary)) !important;
}

/* 主操作和状态操作固定使用实色，内部图标、文字统一继承前景色。 */
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--primary:not(.is-plain):not(.is-link):not(.is-text) {
  color: #fff !important;
  background: var(--workflow-primary, var(--el-color-primary)) !important;
  border-color: var(--workflow-primary, var(--el-color-primary)) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--success:not(.is-plain):not(.is-link):not(.is-text) {
  color: #fff !important;
  background: #047857 !important;
  border-color: #047857 !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--warning:not(.is-plain):not(.is-link):not(.is-text) {
  color: #fff !important;
  background: #a44908 !important;
  border-color: #a44908 !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--danger:not(.is-plain):not(.is-link):not(.is-text) {
  color: #fff !important;
  background: #c81e35 !important;
  border-color: #c81e35 !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--primary:not(.is-disabled):not(.is-plain):not(.is-link):not(.is-text):hover {
  color: #fff !important;
  background: var(--workflow-primary-hover, #4f46e5) !important;
  border-color: var(--workflow-primary-hover, #4f46e5) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--success:not(.is-disabled):not(.is-plain):not(.is-link):not(.is-text):hover {
  color: #fff !important;
  background: #036449 !important;
  border-color: #036449 !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--warning:not(.is-disabled):not(.is-plain):not(.is-link):not(.is-text):hover {
  color: #fff !important;
  background: #873b07 !important;
  border-color: #873b07 !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--danger:not(.is-disabled):not(.is-plain):not(.is-link):not(.is-text):hover {
  color: #fff !important;
  background: #a9162b !important;
  border-color: #a9162b !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button.is-plain {
  background: var(--workflow-control-surface) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--primary.is-plain {
  color: var(--workflow-primary, var(--el-color-primary)) !important;
  border-color: color-mix(in srgb, var(--workflow-primary, var(--el-color-primary)) 52%, var(--workflow-control-border)) !important;
  background: color-mix(in srgb, var(--workflow-primary, var(--el-color-primary)) 10%, var(--workflow-surface, var(--el-bg-color))) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--danger.is-plain {
  color: var(--workflow-danger) !important;
  border-color: color-mix(in srgb, var(--workflow-danger) 48%, var(--workflow-control-border)) !important;
  background: color-mix(in srgb, var(--workflow-danger) 9%, var(--workflow-surface, var(--el-bg-color))) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--warning.is-plain {
  color: var(--workflow-warning) !important;
  border-color: color-mix(in srgb, var(--workflow-warning) 48%, var(--workflow-control-border)) !important;
  background: color-mix(in srgb, var(--workflow-warning) 9%, var(--workflow-surface, var(--el-bg-color))) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--success.is-plain {
  color: var(--workflow-success) !important;
  border-color: color-mix(in srgb, var(--workflow-success) 48%, var(--workflow-control-border)) !important;
  background: color-mix(in srgb, var(--workflow-success) 9%, var(--workflow-surface, var(--el-bg-color))) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--info.is-plain {
  color: var(--workflow-info) !important;
  border-color: color-mix(in srgb, var(--workflow-info) 48%, var(--workflow-control-border)) !important;
  background: color-mix(in srgb, var(--workflow-info) 9%, var(--workflow-surface, var(--el-bg-color))) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button:is(.is-link, .is-text) {
  color: var(--workflow-control-icon) !important;
  background: transparent !important;
  border-color: transparent !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--primary:is(.is-link, .is-text) {
  color: var(--workflow-primary, var(--el-color-primary)) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--success:is(.is-link, .is-text) {
  color: var(--workflow-success) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--warning:is(.is-link, .is-text) {
  color: var(--workflow-warning) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button--danger:is(.is-link, .is-text) {
  color: var(--workflow-danger) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button.is-disabled:not(.is-link):not(.is-text) {
  color: var(--workflow-control-disabled-text) !important;
  background: var(--workflow-control-disabled-bg) !important;
  border-color: var(--workflow-control-border) !important;
  opacity: 1;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button.is-disabled:is(.is-link, .is-text) {
  color: var(--workflow-control-disabled-text) !important;
  opacity: 1;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button span,
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-button .el-icon {
  color: inherit !important;
}

/* 开关关闭态也必须可见；禁用态降低强调度但不消失。 */
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer) .el-switch {
  --el-switch-on-color: var(--workflow-primary, var(--el-color-primary));
  --el-switch-off-color: var(--workflow-control-icon);
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-switch .el-switch__core {
  border-color: var(--workflow-control-icon) !important;
  background: var(--workflow-control-icon) !important;
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--workflow-control-icon) 28%, transparent);
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-switch.is-checked .el-switch__core {
  border-color: var(--workflow-primary, var(--el-color-primary)) !important;
  background: var(--workflow-primary, var(--el-color-primary)) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-switch.is-disabled .el-switch__core {
  border-color: var(--workflow-control-border) !important;
  background: var(--workflow-control-disabled-text) !important;
  opacity: 0.78;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-switch .el-switch__action {
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay)) !important;
  box-shadow: 0 1px 3px rgb(15 23 42 / 24%);
}

/* 数字输入框的增减按钮使用独立底色和深色图标。 */
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__decrease,
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__increase {
  color: var(--workflow-control-icon) !important;
  background: var(--workflow-control-surface) !important;
  border-color: var(--workflow-control-border) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__decrease:not(.is-disabled):hover,
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__increase:not(.is-disabled):hover {
  color: var(--workflow-primary, var(--el-color-primary)) !important;
  background: var(--workflow-control-hover) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__decrease.is-disabled,
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__increase.is-disabled {
  color: var(--workflow-control-disabled-text) !important;
  background: var(--workflow-control-disabled-bg) !important;
  opacity: 1;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__decrease .el-icon,
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-input-number__increase .el-icon {
  color: inherit !important;
}

/* 分段选择未选中与选中状态都保持明确。 */
:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-radio-button__inner {
  color: var(--workflow-control-icon) !important;
  background: var(--workflow-control-surface) !important;
  border-color: var(--workflow-control-border) !important;
  box-shadow: none !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  .el-radio-button__original-radio:checked + .el-radio-button__inner {
  color: #fff !important;
  background: var(--workflow-primary, var(--el-color-primary)) !important;
  border-color: var(--workflow-primary, var(--el-color-primary)) !important;
  box-shadow: -1px 0 0 0 var(--workflow-primary, var(--el-color-primary)) !important;
}

:is(.workflow-page, .workflow-dialog, .workflow-version-drawer, .workflow-execution-drawer)
  :is(.el-button, .el-switch, .el-input-number__decrease, .el-input-number__increase, .el-radio-button__inner):focus-visible {
  outline: 2px solid color-mix(in srgb, var(--workflow-primary, var(--el-color-primary)) 58%, transparent);
  outline-offset: 2px;
}
</style>
