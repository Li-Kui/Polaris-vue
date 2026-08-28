<template>
  <div class="workflow-workbench">
    <header class="workbench-header">
      <div class="header-left">
        <el-button icon="Back" text circle @click="back" />
        <div class="workflow-title">
          <div class="title-row">
            <strong>{{ definition.metadata.name || '未命名工作流' }}</strong>
            <el-tag size="small" type="info">草稿</el-tag>
            <span class="version-label">r{{ currentDefinition?.draftRevision || 0 }}</span>
            <span v-if="dirty" class="dirty-state">未保存</span>
          </div>
          <code>{{ definition.metadata.code || '首次保存时自动生成编码' }}</code>
        </div>
      </div>
      <div class="header-context">
        <el-select
          v-model="resourceEnvironment"
          class="environment-select"
          size="small"
          title="资源环境"
          @change="refreshResourceContext"
        >
          <el-option label="生产（默认）" value="PROD" />
          <el-option label="测试" value="TEST" />
          <el-option label="开发" value="DEV" />
        </el-select>
        <el-tooltip
          content="同一个逻辑资源可以在开发、测试、生产环境绑定不同实体；没有隔离需求时保持生产即可。"
          placement="bottom"
        >
          <el-icon class="context-help"><QuestionFilled /></el-icon>
        </el-tooltip>
      </div>
      <div class="header-actions">
        <el-button-group v-if="canEdit">
          <el-button icon="RefreshLeft" :disabled="!canUndo" title="撤销" @click="undo" />
          <el-button icon="RefreshRight" :disabled="!canRedo" title="重做" @click="redo" />
        </el-button-group>
        <span :class="['validation-state', {passed: validationPassed}]">
          <el-icon><CircleCheck /></el-icon>{{ validationPassed ? '已通过校验' : '等待校验' }}
        </span>
        <el-button :loading="validating" @click="validate">校验</el-button>
        <el-button v-if="canExecute" icon="VideoPlay" @click="openTestRun">测试运行</el-button>
        <el-button v-if="canEdit" :loading="saving" @click="saveDraft">保存</el-button>
        <el-button v-if="canPublish" type="primary" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </header>

    <div :class="['workbench-body', {
      'workbench-body--guided': isApiNode,
      'workbench-body--mapping': isMappingEditorOpen
    }]">
      <aside class="node-palette">
        <div class="palette-heading"><strong>节点库</strong><small>{{ filteredDescriptors.length }} 个节点</small></div>
        <el-input v-model="descriptorKeyword" placeholder="搜索节点名称或类型" clearable size="small" prefix-icon="Search" />
        <div class="palette-tabs">
          <button
            v-for="item in paletteCategories"
            :key="item.value"
            :class="{active: descriptorCategory === item.value}"
            @click="descriptorCategory = item.value"
          >{{ item.label }}</button>
        </div>
        <div class="descriptor-list">
          <button
            v-for="descriptor in filteredDescriptors"
            :key="`${descriptor.type}:${descriptor.handlerVersion}`"
            class="descriptor-item"
            :disabled="!canEdit"
            @click="addNode(descriptor)"
          >
            <span :class="['descriptor-icon', `descriptor-icon--${descriptor.category || 'general'}`]">
              <el-icon><component :is="nodeIcon(descriptor.type)" /></el-icon>
            </span>
            <span class="descriptor-copy">
              <strong>{{ descriptor.displayName }}</strong>
              <small>{{ descriptorDescription(descriptor) }}</small>
            </span>
            <el-icon class="descriptor-add"><Plus /></el-icon>
          </button>
        </div>
      </aside>

      <main class="canvas-panel">
        <div class="canvas-toolbar">
          <span>{{ definition.nodes.length }} 节点 · {{ definition.edges.length }} 连线</span>
          <el-button-group>
            <el-button size="small" icon="ZoomOut" title="缩小" @click="zoomCanvas(-0.15)" />
            <el-button size="small" icon="Aim" title="适应画布" @click="fitCanvas" />
            <el-button size="small" icon="ZoomIn" title="放大" @click="zoomCanvas(0.15)" />
          </el-button-group>
        </div>
        <VueFlow
          ref="workflowCanvas"
          v-model:nodes="canvasNodes"
          v-model:edges="canvasEdges"
          :nodes-connectable="canEdit"
          :nodes-draggable="canEdit"
          :edges-updatable="canEdit"
          :fit-view-on-init="true"
          :min-zoom="0.2"
          :max-zoom="2"
          :only-render-visible-elements="true"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          class="workflow-canvas"
          @connect="onConnect"
          @node-click="selectNode"
          @edge-click="selectEdge"
          @node-drag-stop="markDirty"
          @pane-click="clearSelection"
        >
          <Background :gap="20" pattern-color="var(--workflow-grid)" />
          <template #node-workflow="nodeProps">
            <WorkflowCanvasNode :data="{...nodeProps.data, selected: nodeProps.selected}" />
          </template>
        </VueFlow>
      </main>

      <aside :class="['inspector-panel', {
        'inspector-panel--guided': isApiNode,
        'inspector-panel--mapping': isMappingEditorOpen
      }]">
        <template v-if="selectedNode">
          <div class="inspector-heading">
            <div><span class="inspector-node-icon"><el-icon><component :is="nodeIcon(selectedNode.type)" /></el-icon></span></div>
            <div><strong>{{ selectedNode.name }}</strong><small>{{ selectedNode.id }}</small></div>
            <el-button icon="Close" text circle @click="clearSelection" />
          </div>
          <nav v-if="isApiNode" class="integration-stepper" aria-label="API 节点配置步骤">
            <button
              type="button"
              :class="{active: selectedInspectorTab === 'resource', completed: selectedInspectorTab !== 'resource'}"
              @click="selectedInspectorTab = 'resource'"
            >
              <span>1</span>
              <strong>连接服务</strong>
            </button>
            <i></i>
            <button
              type="button"
              :class="{active: selectedInspectorTab !== 'resource'}"
              :disabled="!apiConnectorReference || !selectedResourceId(apiConnectorReference)"
              @click="selectedInspectorTab = 'config'"
            >
              <span>2</span>
              <strong>配置请求</strong>
            </button>
          </nav>
          <div v-if="isApiNode && selectedInspectorTab !== 'resource'" class="api-config-tabs">
            <button type="button" :class="{active: selectedInspectorTab === 'config'}" @click="selectedInspectorTab = 'config'">请求参数</button>
            <button type="button" :class="{active: selectedInspectorTab === 'mapping'}" @click="selectedInspectorTab = 'mapping'">输入输出</button>
            <button type="button" :class="{active: selectedInspectorTab === 'policy'}" @click="selectedInspectorTab = 'policy'">运行策略</button>
          </div>
          <el-tabs v-model="selectedInspectorTab" :class="['inspector-tabs', {'inspector-tabs--guided': isApiNode}]">
            <el-tab-pane :label="isApiNode ? '2 配置请求' : isDatabaseNode ? '2 编写查询' : '配置'" name="config">
              <el-form label-position="top" size="small">
                <el-form-item label="节点名称"><el-input v-model="selectedNode.name" :disabled="!canEdit" @input="nodeChanged" /></el-form-item>
                <WorkflowDatabaseQueryStep
                  v-if="isDatabaseNode"
                  :config="selectedNode.config"
                  :selected-resource-id="datasourceReference ? selectedResourceId(datasourceReference) : ''"
                  :disabled="!canEdit"
                  @update:config="schemaConfigChanged"
                />
                <template v-else>
                  <WorkflowSchemaConfig
                    :schema="nodeConfigSchema"
                    :model-value="selectedNode.config"
                    :disabled="!canEdit"
                    @update:model-value="schemaConfigChanged"
                  />
                  <el-collapse>
                    <el-collapse-item title="高级 JSON 配置" name="json">
                      <el-input v-model="selectedNodeConfig" type="textarea" :rows="9" :disabled="!canEdit" @input="configChanged" />
                      <div v-if="configError" class="field-error">{{ configError }}</div>
                    </el-collapse-item>
                  </el-collapse>
                </template>
              </el-form>
            </el-tab-pane>
            <el-tab-pane :label="isApiNode ? '1 连接服务' : isDatabaseNode ? '1 连接数据库' : '资源'" name="resource">
              <el-alert
                v-if="catalogError"
                :title="catalogError"
                type="error"
                :closable="false"
                show-icon
                class="resource-alert"
              />
              <WorkflowApiConnectionStep
                v-if="isApiNode && apiConnectorReference"
                :resources="resourceCatalog.API_CONNECTOR || []"
                :selected-resource-id="selectedResourceId(apiConnectorReference)"
                :selected-resource="selectedResource(apiConnectorReference)"
                :environment="resourceEnvironment"
                :loading="resourceLoading"
                :can-edit="canEdit"
                :appearance="appearance"
                :error="catalogError"
                @select="selectApiConnector"
                @created="createAndSelectApiConnector"
              />
              <WorkflowDatasourceConnectionStep
                v-else-if="isDatabaseNode && datasourceReference"
                :resources="resourceCatalog.DATASOURCE || []"
                :selected-resource-id="selectedResourceId(datasourceReference)"
                :selected-resource="selectedResource(datasourceReference)"
                :environment="resourceEnvironment"
                :loading="resourceLoading"
                :can-edit="canEdit"
                :appearance="appearance"
                :error="catalogError"
                @select="selectDatasource"
                @created="createAndSelectDatasource"
              />
              <template v-else>
                <div v-if="!selectedNode.resourceRefs.length" class="empty-resource">该节点不需要外部资源</div>
                <div v-for="(reference, index) in selectedNode.resourceRefs" :key="`${reference.kind}:${index}`" class="resource-card">
                <div class="resource-card-title">
                  <strong>{{ resourceKindLabel(reference.kind) }}</strong>
                  <span class="resource-card-actions">
                    <el-tag size="small" :type="isRequiredResourceReference(reference) ? 'warning' : 'info'">
                      {{ isRequiredResourceReference(reference) ? '节点必需' : '扩展资源' }}
                    </el-tag>
                    <el-tag v-if="selectedResource(reference)?.shared" size="small" type="info">共享</el-tag>
                    <el-button
                      v-if="canEdit && !isRequiredResourceReference(reference)"
                      class="resource-delete"
                      link
                      type="danger"
                      icon="Delete"
                      title="删除扩展资源"
                      @click="removeResourceReference(index)"
                    />
                  </span>
                </div>
                <el-select
                  :model-value="selectedResourceId(reference)"
                  class="resource-select"
                  filterable
                  fit-input-width
                  :loading="resourceLoading"
                  :disabled="!canEdit"
                  popper-class="workflow-resource-popper"
                  placeholder="选择现有资源"
                  style="width: 100%"
                  @change="bindResource(reference, $event)"
                >
                  <el-option
                    v-for="resource in resourceCatalog[reference.kind] || []"
                    :key="resource.resourceId"
                    :label="resource.name"
                    :value="resource.resourceId"
                    :disabled="!resource.available"
                  >
                    <div class="resource-option">
                      <span class="resource-option-copy"><strong>{{ resource.name }}</strong><small>{{ resourceOptionDescription(resource) }}</small></span>
                      <span class="resource-option-tags">
                        <span :class="['resource-scope-badge', {shared: resource.shared}]">{{ resource.shared ? '系统共享' : '当前租户' }}</span>
                        <span v-if="resource.available" class="resource-status-badge available">可用</span>
                      <el-tooltip v-else :content="resource.unavailableReason" placement="left">
                        <span class="resource-status-badge unavailable">不可用</span>
                      </el-tooltip>
                      </span>
                    </div>
                  </el-option>
                </el-select>
                <el-collapse class="resource-advanced">
                  <el-collapse-item :name="`${reference.kind}:${index}`">
                    <template #title>
                      <span class="resource-advanced-title"><el-icon><Setting /></el-icon>高级设置</span>
                    </template>
                    <div class="resource-advanced-fields">
                      <label>
                        <span>资源类型</span>
                        <el-select
                          v-model="reference.kind"
                          :disabled="!canEdit || isRequiredResourceReference(reference)"
                          @change="resourceKindChanged(reference)"
                        >
                          <el-option v-for="item in resourceKindOptions" :key="item.value" :label="item.label" :value="item.value" />
                        </el-select>
                      </label>
                      <label>
                        <span>内部逻辑标识</span>
                        <el-input v-model="reference.key" :disabled="!canEdit" @change="resourceKeyChanged(reference)" />
                      </label>
                      <div class="resource-behavior">
                        <span>未绑定时</span>
                        <strong v-if="isRequiredResourceReference(reference)">阻止工作流运行</strong>
                        <el-radio-group v-else v-model="reference.required" :disabled="!canEdit" @change="markDirty">
                          <el-radio :value="true">阻止运行</el-radio>
                          <el-radio :value="false">跳过该资源</el-radio>
                        </el-radio-group>
                      </div>
                      <small>逻辑标识由系统自动生成，仅在同一工作流需要区分多个同类资源时修改。</small>
                    </div>
                  </el-collapse-item>
                </el-collapse>
                <small
                  v-if="!resourceLoading && !(resourceCatalog[reference.kind] || []).length"
                  class="field-hint"
                >当前操作范围没有可显示的{{ resourceKindLabel(reference.kind) }}。</small>
                </div>
                <el-button v-if="canEdit" class="resource-add" plain icon="Plus" @click="addResourceReference">添加扩展资源</el-button>
              </template>
            </el-tab-pane>
            <el-tab-pane label="输入输出" name="mapping">
              <WorkflowInputMappingEditor
                :model-value="selectedNode.inputMapping || {}"
                :json-value="selectedNodeInputMapping"
                :definition="definition"
                :selected-node="selectedNode"
                :descriptors="descriptors"
                :disabled="!canEdit"
                :mapping-error="mappingError"
                @update:model-value="visualInputMappingChanged"
                @json-change="rawInputMappingChanged"
              />
            </el-tab-pane>
            <el-tab-pane label="运行策略" name="policy">
              <el-form label-position="top" size="small">
                <el-form-item label="执行超时（秒）"><el-input-number v-model="selectedNode.timeoutSeconds" :min="1" :max="86400" :disabled="!canEdit" style="width: 100%" @change="markDirty" /></el-form-item>
                <el-form-item label="错误策略">
                  <el-select v-model="selectedNode.onError" :disabled="!canEdit || selectedDescriptor?.sideEffect === 'WRITE'" style="width: 100%" @change="markDirty">
                    <el-option label="失败并终止" value="FAIL" /><el-option label="跳过节点" value="SKIP" />
                  </el-select>
                </el-form-item>
                <el-form-item label="失败重试"><el-switch :model-value="!!selectedNode.retryPolicy" :disabled="!canEdit || selectedDescriptor?.sideEffect === 'WRITE'" @change="toggleRetry" /></el-form-item>
                <template v-if="selectedNode.retryPolicy">
                  <el-form-item label="最大尝试次数"><el-input-number v-model="selectedNode.retryPolicy.maxAttempts" :min="1" :max="10" :disabled="!canEdit" style="width: 100%" @change="markDirty" /></el-form-item>
                  <el-form-item label="退避方式"><el-select v-model="selectedNode.retryPolicy.backoff" :disabled="!canEdit" style="width: 100%" @change="markDirty"><el-option label="固定间隔" value="FIXED" /><el-option label="指数退避" value="EXPONENTIAL" /></el-select></el-form-item>
                </template>
                <el-form-item v-if="selectedDescriptor?.sideEffect === 'WRITE'" label="失败补偿节点">
                  <el-select v-model="selectedNode.compensationNodeId" clearable :disabled="!canEdit" style="width: 100%" @change="compensationChanged">
                    <el-option v-for="node in compensationOptions" :key="node.id" :label="`${node.name} (${node.id})`" :value="node.id" />
                  </el-select>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
          <div class="inspector-footer"><el-button v-if="canEdit" type="danger" text icon="Delete" @click="removeSelectedNode">删除节点</el-button></div>
        </template>

        <template v-else-if="selectedEdge">
          <div class="inspector-heading simple"><div><strong>连线设置</strong><small>{{ selectedEdge.source }} → {{ selectedEdge.target }}</small></div></div>
          <el-form label-position="top" size="small" class="edge-form">
            <el-form-item label="连线类型"><el-select v-model="selectedEdge.kind" :disabled="!canEdit" style="width: 100%" @change="edgeChanged"><el-option label="普通" value="NORMAL" /><el-option label="条件" value="CONDITION" /><el-option label="并行" value="PARALLEL" /><el-option label="循环" value="LOOP" /><el-option label="语义分类" value="SEMANTIC" /></el-select></el-form-item>
            <el-form-item v-if="selectedEdge.kind === 'SEMANTIC'" label="分类分支标识"><el-input v-model="selectedEdge.sourcePort" :disabled="!canEdit" @input="edgeChanged" /></el-form-item>
            <template v-if="selectedEdge.kind === 'CONDITION'">
              <el-form-item label="默认分支"><el-switch v-model="selectedEdge.default" :disabled="!canEdit" @change="edgeChanged" /></el-form-item>
              <el-form-item v-if="!selectedEdge.default" label="条件表达式"><el-input v-model="selectedEdge.condition.expression" type="textarea" :rows="5" :disabled="!canEdit" placeholder="$.input.amount >= 10000" @input="edgeChanged" /></el-form-item>
              <el-form-item v-if="!selectedEdge.default" label="优先级"><el-input-number v-model="selectedEdge.condition.priority" :min="0" :max="10000" :disabled="!canEdit" style="width: 100%" @change="edgeChanged" /></el-form-item>
            </template>
            <el-button v-if="canEdit" type="danger" plain style="width: 100%" @click="removeSelectedEdge">删除连线</el-button>
          </el-form>
        </template>

        <template v-else>
          <div class="inspector-heading simple"><div><strong>工作流设置</strong><small>全局运行与预算策略</small></div></div>
          <el-form label-position="top" size="small" class="edge-form">
            <el-form-item label="名称" required :error="workflowNameError">
              <el-input v-model="definition.metadata.name" maxlength="128" :disabled="!canEdit" @input="workflowNameChanged" />
            </el-form-item>
            <el-form-item label="编码" :error="workflowCodeError">
              <el-input
                v-model="definition.metadata.code"
                maxlength="64"
                placeholder="留空将在首次保存时自动生成"
                :disabled="!!currentDefinition?.id || !canEdit"
                @input="workflowCodeChanged"
              />
              <small class="field-hint">字母开头，只能包含字母、数字、点、横线和下划线；创建后不可修改。</small>
            </el-form-item>
            <el-form-item label="描述"><el-input v-model="definition.metadata.description" type="textarea" :rows="4" :disabled="!canEdit" @input="markDirty" /></el-form-item>
            <el-form-item label="最大节点运行数"><el-input-number v-model="definition.policies.maxNodeRuns" :min="1" :max="10000" :disabled="!canEdit" style="width: 100%" @change="markDirty" /></el-form-item>
            <el-form-item label="最大并行数"><el-input-number v-model="definition.policies.maxParallelism" :min="1" :max="100" :disabled="!canEdit" style="width: 100%" @change="markDirty" /></el-form-item>
          </el-form>
        </template>
      </aside>
    </div>

    <section v-if="diagnostics.length" class="diagnostic-panel">
      <div class="diagnostic-header"><strong>校验结果</strong><el-button link @click="diagnostics = []">关闭</el-button></div>
      <div v-for="(item, index) in diagnostics" :key="`${item.code}-${index}`" class="diagnostic-item" @click="focusDiagnostic(item)">
        <el-tag :type="item.severity === 'ERROR' ? 'danger' : 'warning'" size="small">{{ item.code }}</el-tag><span>{{ item.message }}</span><code>{{ item.fieldPath }}</code>
      </div>
    </section>

    <div v-if="debugExecution" class="execution-dock">
      <el-button :icon="executionFinished ? 'RefreshRight' : 'VideoPause'" circle @click="executionFinished ? openTestRun() : stopPolling()" />
      <div class="execution-progress"><strong>{{ executionStatusLabel }}</strong><el-progress :percentage="executionProgress" :show-text="false" /></div>
      <span>{{ completedNodeRuns }}/{{ Math.max(1, definition.nodes.length) }} 节点</span>
      <code>{{ debugExecution.executionId }}</code>
      <el-button text @click="pollExecution">刷新状态</el-button>
    </div>

    <el-dialog v-model="testDialogOpen" title="测试运行" width="620px">
      <el-alert v-if="!currentDefinition?.currentPublishedVersionId" title="请先发布一个版本后再运行测试。" type="warning" :closable="false" />
      <el-form label-width="100px" class="test-run-form">
        <el-form-item label="运行环境"><el-select v-model="resourceEnvironment" style="width: 100%"><el-option label="开发 DEV" value="DEV" /><el-option label="测试 TEST" value="TEST" /><el-option label="生产 PROD" value="PROD" /></el-select></el-form-item>
        <el-form-item label="输入 JSON"><el-input v-model="testInputJson" type="textarea" :rows="10" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="testDialogOpen = false">取消</el-button><el-button type="primary" :loading="testStarting" :disabled="!currentDefinition?.currentPublishedVersionId" @click="startTestRun">开始运行</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import {VueFlow} from '@vue-flow/core'
import {Background} from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import {
  createWorkflowDraft,
  getWorkflowExecution,
  listWorkflowNodeRuns,
  listWorkflowResourceBindings,
  listWorkflowResources,
  publishWorkflowDraft,
  saveWorkflowResourceBinding,
  startWorkflowExecution,
  updateWorkflowDraft,
  validateWorkflowDraft
} from '@/api/ai/workflow'
import {
  ChatDotRound,
  CircleCheck,
  Coin,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Finished,
  Grid,
  MagicStick,
  Operation,
  Plus,
  QuestionFilled,
  Refresh,
  Share,
  Switch,
  Timer,
  User
} from '@element-plus/icons-vue'
import WorkflowSchemaConfig from './WorkflowSchemaConfig.vue'
import WorkflowCanvasNode from './WorkflowCanvasNode.vue'
import WorkflowApiConnectionStep from './WorkflowApiConnectionStep.vue'
import WorkflowDatasourceConnectionStep from './WorkflowDatasourceConnectionStep.vue'
import WorkflowDatabaseQueryStep from './WorkflowDatabaseQueryStep.vue'
import WorkflowInputMappingEditor from './WorkflowInputMappingEditor.vue'

export default {
  name: 'WorkflowWorkbench',
  components: {
    VueFlow,
    Background,
    WorkflowSchemaConfig,
    WorkflowCanvasNode,
    WorkflowApiConnectionStep,
    WorkflowDatasourceConnectionStep,
    WorkflowDatabaseQueryStep,
    WorkflowInputMappingEditor,
    ChatDotRound,
    CircleCheck,
    Coin,
    Collection,
    Connection,
    Cpu,
    DataAnalysis,
    Finished,
    Grid,
    MagicStick,
    Operation,
    Plus,
    QuestionFilled,
    Refresh,
    Share,
    Switch,
    Timer,
    User
  },
  props: {
    modelValue: {
      type: Object,
      default: null
    },
    descriptors: {
      type: Array,
      default: () => []
    },
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
    appearance: {
      type: String,
      default: 'admin'
    }
  },
  emits: ['back', 'saved', 'published'],
  data() {
    return {
      currentDefinition: this.modelValue,
      definition: this.createEmptyDefinition(),
      canvasNodes: [],
      canvasEdges: [],
      selectedNode: null,
      selectedEdge: null,
      selectedNodeConfig: '{}',
      selectedNodeInputMapping: '{}',
      configError: '',
      mappingError: '',
      workflowNameError: '',
      workflowCodeError: '',
      descriptorKeyword: '',
      descriptorCategory: 'all',
      selectedInspectorTab: 'config',
      diagnostics: [],
      validationPassed: false,
      dirty: false,
      saving: false,
      validating: false,
      publishing: false,
      history: [],
      historyIndex: -1,
      historyTimer: null,
      historyRestoring: false,
      resourceEnvironment: 'PROD',
      resourceLoading: false,
      resourceCatalog: {},
      catalogError: '',
      resourceBindings: [],
      pendingResourceBindings: [],
      testDialogOpen: false,
      testStarting: false,
      testInputJson: '{}',
      debugExecution: null,
      debugNodeRuns: [],
      executionPollTimer: null,
      paletteCategories: [
        {label: '全部', value: 'all'},
        {label: '数据', value: 'data'},
        {label: 'AI', value: 'ai'},
        {label: '逻辑', value: 'control'},
        {label: '集成', value: 'integration'}
      ],
      resourceKindOptions: [
        {label: '大模型', value: 'MODEL'},
        {label: 'AI 智能体', value: 'AGENT'},
        {label: '知识库', value: 'KNOWLEDGE_BASE'},
        {label: 'API 连接器', value: 'API_CONNECTOR'},
        {label: '外部数据源', value: 'DATASOURCE'}
      ]
    }
  },
  computed: {
    isApiNode() {
      return ['http_get', 'http_request'].includes(this.selectedNode?.type)
    },
    isDatabaseNode() {
      return this.selectedNode?.type === 'database_query'
    },
    isMappingEditorOpen() {
      return !!this.selectedNode && this.selectedInspectorTab === 'mapping'
    },
    apiConnectorReference() {
      return this.selectedNode?.resourceRefs?.find(reference => reference.kind === 'API_CONNECTOR') || null
    },
    datasourceReference() {
      return this.selectedNode?.resourceRefs?.find(reference => reference.kind === 'DATASOURCE') || null
    },
    selectedDescriptor() {
      if (!this.selectedNode) return null
      return this.descriptors.find(item =>
        item.type === this.selectedNode.type
          && item.handlerVersion === this.selectedNode.typeVersion) || null
    },
    nodeConfigSchema() {
      if (!this.isApiNode) return this.selectedDescriptor?.configSchema || {}
      const properties = {
        method: {
          type: 'string',
          title: '请求方法',
          enum: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
          description: 'GET 按只读请求执行，其他方法按写请求安全策略执行。'
        }
      }
      properties.path = {
        type: 'string',
        title: '接口路径',
        placeholder: '/orders/query',
        minLength: 1,
        maxLength: 2048,
        description: '只填写 Base URL 后面的相对路径，不要重复填写域名；建议以 / 开头。'
      }
      return {
        type: 'object',
        properties,
        required: ['method', 'path'],
        additionalProperties: false
      }
    },
    compensationOptions() {
      if (!this.selectedNode) return []
      return this.definition.nodes.filter(node => {
        if (node.id === this.selectedNode.id) return false
        const descriptor = this.descriptors.find(item =>
          item.type === node.type && item.handlerVersion === node.typeVersion)
        return descriptor?.sideEffect === 'WRITE'
      })
    },
    retryableErrorOptions() {
      return [
        'RESOURCE_TEMPORARILY_UNAVAILABLE', 'RATE_LIMITED', 'NODE_TIMEOUT',
        'EXECUTION_CONFLICT', 'TEMPORARY', 'RATE_LIMIT', 'TIMEOUT'
      ]
    },
    filteredDescriptors() {
      const keyword = this.descriptorKeyword.trim().toLowerCase()
      let httpDescriptorAdded = false
      const paletteDescriptors = this.descriptors.reduce((result, item) => {
        if (!['http_get', 'http_request'].includes(item.type)) {
          result.push(item)
          return result
        }
        if (!httpDescriptorAdded) {
          const descriptor = this.descriptors.find(candidate => candidate.type === 'http_get') || item
          result.push({
            ...descriptor,
            displayName: 'HTTP 请求',
            unifiedHttp: true,
            searchKeywords: 'http_get http_request get post put patch delete'
          })
          httpDescriptorAdded = true
        }
        return result
      }, [])
      return paletteDescriptors.filter(item => {
        const categoryMatched = this.descriptorCategory === 'all'
          || String(item.category || 'general').toLowerCase() === this.descriptorCategory
        const keywordMatched = !keyword
          || item.displayName?.toLowerCase().includes(keyword)
          || item.type?.toLowerCase().includes(keyword)
          || item.searchKeywords?.includes(keyword)
        return categoryMatched && keywordMatched
      })
    },
    canUndo() {
      return this.historyIndex > 0
    },
    canRedo() {
      return this.historyIndex >= 0 && this.historyIndex < this.history.length - 1
    },
    completedNodeRuns() {
      return new Set(this.debugNodeRuns
        .filter(item => ['SUCCEEDED', 'FAILED', 'SKIPPED', 'CANCELLED'].includes(item.status))
        .map(item => item.nodeId)).size
    },
    executionProgress() {
      if (!this.definition.nodes.length) return 0
      return Math.min(100, Math.round(this.completedNodeRuns / this.definition.nodes.length * 100))
    },
    executionFinished() {
      return ['SUCCEEDED', 'FAILED', 'CANCELLED', 'REJECTED', 'NEEDS_ATTENTION']
        .includes(this.debugExecution?.status)
    },
    executionStatusLabel() {
      const labels = {
        QUEUED: '等待执行', RUNNING: '正在执行', WAITING_APPROVAL: '等待审批',
        WAITING_EVENT: '等待事件', RECOVERING: '正在恢复', SUCCEEDED: '运行成功',
        FAILED: '运行失败', CANCELLED: '已取消', REJECTED: '已拒绝',
        NEEDS_ATTENTION: '需要人工处理'
      }
      return labels[this.debugExecution?.status] || this.debugExecution?.status || '准备运行'
    }
  },
  created() {
    this.loadDefinition(this.modelValue)
    this.refreshResourceContext()
  },
  beforeUnmount() {
    clearTimeout(this.historyTimer)
    clearTimeout(this.executionPollTimer)
  },
  methods: {
    nodeIcon(type) {
      const icons = {
        llm: 'Cpu', agent: 'ChatDotRound', llm_classifier: 'MagicStick',
        knowledge_rag: 'Collection', http_get: 'Connection', http_request: 'Connection',
        database_query: 'Coin', condition: 'Switch', parallel: 'Share', join: 'Grid',
        loop: 'Refresh', wait: 'Timer', approval: 'User', transform: 'Operation',
        artifact: 'DataAnalysis', sub_workflow: 'Finished'
      }
      return icons[type] || 'Operation'
    },
    descriptorDescription(descriptor) {
      if (descriptor.unifiedHttp) {
        return '调用外部 API，支持 GET、POST、PUT、PATCH、DELETE'
      }
      const descriptions = {
        llm: '调用现有大模型理解与生成',
        agent: '运行已配置的 AI 智能体',
        llm_classifier: '基于语义进行稳定分支',
        knowledge_rag: '检索现有知识库内容',
        http_get: '调用现有 API 连接器',
        http_request: '执行受控外部写请求',
        database_query: '查询现有只读数据源',
        condition: '按确定性条件选择分支',
        parallel: '并行执行多条分支',
        join: '汇聚并行分支结果',
        loop: '在限制范围内循环执行',
        wait: '延迟后恢复执行',
        approval: '发起人工审批任务'
      }
      return descriptions[descriptor.type] || this.nodeCategoryLabel(descriptor.category)
    },
    nodeCategoryLabel(category) {
      const labels = {
        ai: '人工智能',
        control: '流程控制',
        data: '数据处理',
        integration: '系统集成',
        general: '通用'
      }
      return labels[String(category || 'general').toLowerCase()] || category || labels.general
    },
    createEmptyDefinition() {
      return {
        schemaVersion: '2.0',
        metadata: {
          code: '',
          name: '新建工作流',
          description: '',
          tags: []
        },
        inputs: { type: 'object', properties: {} },
        nodes: [],
        edges: [],
        outputs: {},
        policies: {
          timeoutSeconds: 1800,
          maxNodeRuns: 200,
          maxParallelism: 10,
          tokenBudget: 100000,
          costBudget: 100
        }
      }
    },
    loadDefinition(record) {
      this.currentDefinition = record
      if (record?.draftJson) {
        try {
          this.definition = JSON.parse(record.draftJson)
        } catch (error) {
          this.$message.error('草稿 JSON 已损坏，无法进入设计器')
          this.definition = this.createEmptyDefinition()
        }
      } else {
        this.definition = this.createEmptyDefinition()
      }
      this.normalizeHttpNodeConfigs()
      this.buildCanvas()
      this.dirty = false
      this.resetHistory()
    },
    canvasNodeData(node) {
      const descriptor = this.descriptors.find(item =>
        item.type === node.type && item.handlerVersion === node.typeVersion)
      const reference = Array.isArray(node.resourceRefs) ? node.resourceRefs[0] : null
      return {
        label: node.name,
        type: node.type,
        category: descriptor?.category || 'general',
        inputSummary: Object.keys(node.inputMapping || {}).length
          ? `${Object.keys(node.inputMapping).length} 个映射` : '对象',
        outputSummary: node.type === 'knowledge_rag' ? '知识片段' : '结果对象',
        resourceName: reference ? this.selectedResource(reference)?.name : '',
        status: this.latestNodeStatus(node.id)
      }
    },
    buildCanvas() {
      const compensationTargets = new Set((this.definition.nodes || [])
        .map(node => node.compensationNodeId)
        .filter(Boolean))
      const positionedNodes = (this.definition.nodes || []).map((node, index) => ({
        node,
        position: node.ui || { x: 240 + index * 180, y: 220 }
      }))
      const furthestPosition = positionedNodes.reduce((result, item) => ({
        x: Math.max(result.x, item.position.x),
        y: Math.max(result.y, item.position.y)
      }), {x: 520, y: 220})
      const start = {
        id: '__start__',
        type: 'workflow',
        position: { x: 60, y: 220 },
        data: {label: '开始', start: true},
        draggable: false,
        deletable: false
      }
      const end = {
        id: '__end__',
        type: 'workflow',
        position: {
          x: positionedNodes.length > 4 ? furthestPosition.x : furthestPosition.x + 260,
          y: positionedNodes.length > 4 ? furthestPosition.y + 210 : 220
        },
        data: {label: '结束', end: true},
        draggable: false,
        deletable: false
      }
      this.canvasNodes = [start, ...positionedNodes.map(({node, position}) => ({
        id: node.id,
        type: 'workflow',
        position,
        data: this.canvasNodeData(node),
        class: compensationTargets.has(node.id) ? 'compensation-node' : ''
      })), end]
      this.canvasEdges = (this.definition.edges || []).map((edge, index) => this.canvasEdge(edge, index))
    },
    canvasEdge(edge, index) {
      return {
        id: edge.id || `edge-${index}-${edge.source}-${edge.target}`,
        source: edge.source,
        target: edge.target,
        label: edge.kind === 'CONDITION'
          ? (edge.default ? '默认' : edge.condition?.expression || '条件')
          : edge.kind === 'SEMANTIC' ? edge.sourcePort || '分类分支' : '',
        animated: edge.kind === 'CONDITION' || edge.kind === 'LOOP',
        style: {stroke: '#6762e8', strokeWidth: 1.6},
        labelStyle: {fill: '#6f7890', fontSize: 10},
        data: { definitionEdge: edge }
      }
    },
    addNode(descriptor) {
      if (!this.canEdit) return
      const base = descriptor.type.replace(/[^a-z0-9_]/g, '_')
      let index = 1
      let id = `${base}_${index}`
      const ids = new Set(this.definition.nodes.map(item => item.id))
      while (ids.has(id)) id = `${base}_${++index}`
      const resourceReferences = this.defaultResourceReferences(descriptor, id)
      const node = {
        id,
        type: descriptor.type,
        typeVersion: descriptor.handlerVersion,
        name: descriptor.displayName,
        inputMapping: {},
        config: this.defaultNodeConfig(descriptor.type),
        timeoutSeconds: 120,
        onError: 'FAIL',
        resourceRefs: resourceReferences,
        ui: { x: 260 + this.definition.nodes.length * 40, y: 180 + this.definition.nodes.length * 30 }
      }
      this.definition.nodes.push(node)
      this.canvasNodes.push({
        id: node.id,
        type: 'workflow',
        position: {...node.ui},
        data: this.canvasNodeData(node)
      })
      if (this.definition.nodes.length === 1 && this.definition.edges.length === 0) {
        this.addDefinitionEdge('__start__', node.id, 'NORMAL')
        this.addDefinitionEdge(node.id, '__end__', 'NORMAL')
      }
      this.selectedNode = node
      this.selectedEdge = null
      this.selectedNodeConfig = JSON.stringify(node.config, null, 2)
      this.selectedNodeInputMapping = JSON.stringify(node.inputMapping, null, 2)
      this.selectedInspectorTab = resourceReferences.length ? 'resource' : 'config'
      this.markDirty()
    },
    defaultNodeConfig(type) {
      if (type === 'http_get') return {method: 'GET', path: ''}
      if (type === 'http_request') return {method: 'POST', path: ''}
      if (type === 'loop') return {maxIterations: 10}
      if (type === 'join') return {mode: 'ALL'}
      if (type === 'wait') return {delaySeconds: 60}
      if (type === 'database_query') return {sql: '', maxRows: 100, queryTimeoutSeconds: 10}
      if (type === 'sub_workflow') {
        return {workflowCode: '', workflowVersionId: '', pollSeconds: 2}
      }
      if (type === 'approval') {
        return {
          assigneeType: 'USER',
          assigneeIds: [],
          approvalMode: 'ANY',
          requiredApprovals: 1,
          allowSelfApproval: false,
          timeoutSeconds: 900
        }
      }
      return {}
    },
    onConnect(params) {
      if (!this.canEdit || !params.source || !params.target || params.source === params.target) return
      if (this.definition.edges.some(edge => edge.source === params.source && edge.target === params.target)) return
      const sourceNode = this.definition.nodes.find(node => node.id === params.source)
      const kind = sourceNode?.type === 'condition' ? 'CONDITION'
        : sourceNode?.type === 'parallel' ? 'PARALLEL'
          : sourceNode?.type === 'llm_classifier' ? 'SEMANTIC' : 'NORMAL'
      this.addDefinitionEdge(params.source, params.target, kind)
      this.markDirty()
    },
    addDefinitionEdge(source, target, kind) {
      const edge = {
        id: `edge-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        source,
        target,
        kind,
        default: false
      }
      if (kind === 'CONDITION') {
        edge.condition = { expression: '', priority: 100, onError: 'FAIL' }
      }
      if (kind === 'SEMANTIC') edge.sourcePort = ''
      this.definition.edges.push(edge)
      this.canvasEdges.push(this.canvasEdge(edge, this.canvasEdges.length))
    },
    selectNode({node}) {
      if (node.id === '__start__' || node.id === '__end__') {
        this.clearSelection()
        return
      }
      this.selectedNode = this.definition.nodes.find(item => item.id === node.id) || null
      if (this.selectedNode && !Array.isArray(this.selectedNode.resourceRefs)) {
        this.selectedNode.resourceRefs = []
      }
      this.selectedEdge = null
      this.selectedNodeConfig = JSON.stringify(this.selectedNode?.config || {}, null, 2)
      this.selectedNodeInputMapping = JSON.stringify(this.selectedNode?.inputMapping || {}, null, 2)
      this.selectedInspectorTab = this.selectedNode?.resourceRefs?.length ? 'resource' : 'config'
      this.configError = ''
      this.mappingError = ''
    },
    selectEdge({edge}) {
      this.selectedEdge = this.definition.edges.find(item => item.id === edge.id)
        || edge.data?.definitionEdge || null
      if (this.selectedEdge?.kind === 'CONDITION' && !this.selectedEdge.condition && !this.selectedEdge.default) {
        this.selectedEdge.condition = { expression: '', priority: 100, onError: 'FAIL' }
      }
      this.selectedNode = null
    },
    clearSelection() {
      this.selectedNode = null
      this.selectedEdge = null
      this.configError = ''
      this.mappingError = ''
    },
    nodeChanged() {
      const canvasNode = this.canvasNodes.find(item => item.id === this.selectedNode.id)
      if (canvasNode) canvasNode.data.label = this.selectedNode.name
      this.markDirty()
    },
    configChanged() {
      try {
        const value = JSON.parse(this.selectedNodeConfig)
        if (!value || Array.isArray(value) || typeof value !== 'object') {
          throw new Error('配置必须是 JSON 对象')
        }
        this.selectedNode.config = value
        this.syncHttpNodeType(value.method)
        this.configError = ''
        this.markDirty()
      } catch (error) {
        this.configError = error.message
      }
    },
    schemaConfigChanged(value) {
      this.selectedNode.config = value
      this.syncHttpNodeType(value?.method)
      this.selectedNodeConfig = JSON.stringify(value, null, 2)
      this.configError = ''
      this.markDirty()
    },
    normalizeHttpNodeConfigs() {
      const nodes = this.definition.nodes || []
      nodes.forEach(node => {
        if (!['http_get', 'http_request'].includes(node.type)) return
        if (!node.config || Array.isArray(node.config) || typeof node.config !== 'object') {
          node.config = {}
        }
        if (!node.config.method) {
          node.config.method = node.type === 'http_get' ? 'GET' : 'POST'
        }
      })
    },
    syncHttpNodeType(method) {
      if (!this.isApiNode || !method) return
      const normalizedMethod = String(method).toUpperCase()
      const targetType = normalizedMethod === 'GET' ? 'http_get' : 'http_request'
      if (this.selectedNode.type === targetType) return
      const descriptor = this.descriptors.find(item => item.type === targetType)
      if (!descriptor) {
        this.$message.error(`当前环境未提供 ${targetType} 节点处理器`)
        return
      }
      this.selectedNode.type = targetType
      this.selectedNode.typeVersion = descriptor.handlerVersion
      if (targetType === 'http_request') {
        this.selectedNode.onError = 'FAIL'
        delete this.selectedNode.retryPolicy
      }
      const canvasNode = this.canvasNodes.find(item => item.id === this.selectedNode.id)
      if (canvasNode) canvasNode.data = this.canvasNodeData(this.selectedNode)
    },
    inputMappingChanged() {
      try {
        const value = JSON.parse(this.selectedNodeInputMapping)
        if (!value || Array.isArray(value) || typeof value !== 'object') {
          throw new Error('输入映射必须是 JSON 对象')
        }
        this.selectedNode.inputMapping = value
        this.mappingError = ''
        this.markDirty()
      } catch (error) {
        this.mappingError = error.message
      }
    },
    visualInputMappingChanged(value) {
      this.selectedNode.inputMapping = value
      this.selectedNodeInputMapping = JSON.stringify(value, null, 2)
      this.mappingError = ''
      const canvasNode = this.canvasNodes.find(item => item.id === this.selectedNode.id)
      if (canvasNode) canvasNode.data = this.canvasNodeData(this.selectedNode)
      this.markDirty()
    },
    rawInputMappingChanged(value) {
      this.selectedNodeInputMapping = value
      this.inputMappingChanged()
    },
    defaultResourceReferences(descriptor, nodeId) {
      const keys = {
        MODEL: 'primary_model',
        AGENT: 'primary_agent',
        KNOWLEDGE_BASE: 'primary_knowledge',
        API_CONNECTOR: `api.${nodeId}`,
        DATASOURCE: `db.${nodeId}`
      }
      if (Array.isArray(descriptor.requiredResourceKinds)
          && descriptor.requiredResourceKinds.length) {
        return descriptor.requiredResourceKinds.map(kind => ({
          kind,
          key: keys[kind] || `primary_${kind.toLowerCase()}`,
          required: true
        }))
      }
      const defaults = {
        llm: {kind: 'MODEL', key: 'primary_model', required: true},
        agent: {kind: 'AGENT', key: 'primary_agent', required: true},
        knowledge_rag: {kind: 'KNOWLEDGE_BASE', key: 'primary_knowledge', required: true},
        http_get: {kind: 'API_CONNECTOR', key: `api.${nodeId}`, required: true},
        http_request: {kind: 'API_CONNECTOR', key: `api.${nodeId}`, required: true},
        database_query: {kind: 'DATASOURCE', key: `db.${nodeId}`, required: true}
      }
      const value = defaults[descriptor.type]
      return value ? [{...value}] : []
    },
    resourceKindLabel(kind) {
      return this.resourceKindOptions.find(item => item.value === kind)?.label || kind
    },
    isRequiredResourceReference(reference) {
      return !!this.selectedDescriptor?.requiredResourceKinds?.includes(reference.kind)
    },
    resourceKeyForKind(kind) {
      const keys = {
        MODEL: 'primary_model',
        AGENT: 'primary_agent',
        KNOWLEDGE_BASE: 'primary_knowledge',
        API_CONNECTOR: 'primary_api',
        DATASOURCE: 'primary_database'
      }
      return keys[kind] || `primary_${String(kind || 'resource').toLowerCase()}`
    },
    nextResourceKey(kind, currentReference = null) {
      const baseKey = this.resourceKeyForKind(kind)
      const usedKeys = new Set((this.selectedNode?.resourceRefs || [])
        .filter(reference => reference !== currentReference)
        .map(reference => reference.key))
      if (!usedKeys.has(baseKey)) return baseKey
      let sequence = 2
      while (usedKeys.has(`${baseKey}_${sequence}`)) sequence += 1
      return `${baseKey}_${sequence}`
    },
    resourceOptionDescription(resource) {
      const attributes = resource?.attributes || {}
      if (!resource.available && resource.unavailableReason) return resource.unavailableReason
      const detail = resource.description || attributes.modelName || attributes.type
        || attributes.baseUrl || ''
      return [detail, attributes.toolNotice].filter(Boolean).join(' · ')
    },
    selectedResourceId(reference) {
      return this.bindingForReference(reference)?.resourceId || ''
    },
    selectedResource(reference) {
      const resourceId = this.selectedResourceId(reference)
      return (this.resourceCatalog[reference.kind] || [])
        .find(resource => resource.resourceId === resourceId) || null
    },
    bindingForReference(reference) {
      const pending = this.pendingBindingForReference(reference)
      if (pending) return pending
      const matches = this.resourceBindings.filter(binding =>
        binding.status === 'ACTIVE'
          && binding.resourceKind === reference.kind
          && binding.resourceKey === reference.key
          && binding.environment === this.resourceEnvironment)
      return matches.find(binding => binding.scopeType === 'WORKFLOW'
          && binding.definitionId === this.currentDefinition?.id)
        || matches.find(binding => binding.scopeType === 'OWNER')
        || null
    },
    nodeIdForReference(reference) {
      return this.definition.nodes.find(node =>
        Array.isArray(node.resourceRefs) && node.resourceRefs.includes(reference))?.id || ''
    },
    pendingBindingForReference(reference) {
      const nodeId = this.nodeIdForReference(reference)
      return this.pendingResourceBindings.find(binding =>
        binding.nodeId === nodeId
          && binding.resourceKind === reference.kind
          && binding.resourceKey === reference.key
          && binding.environment === this.resourceEnvironment) || null
    },
    async refreshResourceContext() {
      this.resourceLoading = true
      try {
        const kinds = this.resourceKindOptions.map(item => item.value)
        this.catalogError = ''
        const catalogResponses = await Promise.all(kinds.map(kind => listWorkflowResources({
          kind,
          environment: this.resourceEnvironment
        }).catch(() => ({data: [], failed: true}))))
        this.resourceCatalog = kinds.reduce((result, kind, index) => {
          result[kind] = catalogResponses[index].data || []
          return result
        }, {})
        const failedKinds = kinds.filter((kind, index) => catalogResponses[index].failed)
        if (failedKinds.length) {
          this.catalogError = failedKinds.length === kinds.length
            ? '资源目录加载失败，请检查数据库升级和服务日志。'
            : `部分资源目录加载失败：${failedKinds.map(kind => this.resourceKindLabel(kind)).join('、')}`
        }
        const response = await listWorkflowResourceBindings({
          definitionId: this.currentDefinition?.id,
          environment: this.resourceEnvironment
        })
        this.resourceBindings = response.data || []
        this.refreshCanvasNodeData()
      } finally {
        this.resourceLoading = false
      }
    },
    async bindResource(reference, resourceId) {
      if (!reference.key?.trim()) {
        this.$message.warning('请先填写逻辑资源键')
        return
      }
      if (!this.currentDefinition?.id) {
        const nodeId = this.nodeIdForReference(reference)
        const existingIndex = this.pendingResourceBindings.findIndex(binding =>
          binding.nodeId === nodeId
            && binding.resourceKind === reference.kind
            && binding.resourceKey === reference.key
            && binding.environment === this.resourceEnvironment)
        const pending = {
          status: 'ACTIVE',
          scopeType: 'WORKFLOW',
          definitionId: null,
          nodeId,
          environment: this.resourceEnvironment,
          resourceKind: reference.kind,
          resourceKey: reference.key.trim(),
          resourceId
        }
        if (existingIndex >= 0) {
          this.pendingResourceBindings.splice(existingIndex, 1, pending)
        } else {
          this.pendingResourceBindings.push(pending)
        }
        this.markDirty()
        this.refreshCanvasNodeData()
        this.$message.success(`已选择${this.resourceKindLabel(reference.kind)}，保存工作流后完成关联`)
        return true
      }
      const existing = this.resourceBindings.find(binding =>
        binding.resourceKind === reference.kind
          && binding.resourceKey === reference.key
          && binding.environment === this.resourceEnvironment
          && binding.scopeType === 'WORKFLOW'
          && binding.definitionId === this.currentDefinition?.id)
      this.resourceLoading = true
      try {
        await saveWorkflowResourceBinding({
          id: existing?.id || null,
          definitionId: this.currentDefinition?.id,
          scopeType: 'WORKFLOW',
          environment: this.resourceEnvironment,
          resourceKind: reference.kind,
          resourceKey: reference.key.trim(),
          resourceId,
          expectedLockVersion: existing?.lockVersion ?? null
        })
        await this.refreshResourceContext()
        this.$message.success(`已关联${this.resourceKindLabel(reference.kind)}`)
        return true
      } finally {
        this.resourceLoading = false
      }
    },
    async selectApiConnector(resourceId) {
      if (!this.apiConnectorReference) return
      const selected = await this.bindResource(this.apiConnectorReference, resourceId)
      if (selected) this.selectedInspectorTab = 'config'
    },
    async createAndSelectApiConnector(resourceId) {
      await this.refreshResourceContext()
      await this.selectApiConnector(resourceId)
    },
    async selectDatasource(resourceId) {
      if (!this.datasourceReference) return
      const selected = await this.bindResource(this.datasourceReference, resourceId)
      if (selected) this.selectedInspectorTab = 'config'
    },
    async createAndSelectDatasource(resourceId) {
      await this.refreshResourceContext()
      await this.selectDatasource(resourceId)
    },
    resourceKeyChanged(reference) {
      this.updatePendingBindingReference(reference)
      this.markDirty()
      this.refreshCanvasNodeData()
    },
    resourceKindChanged(reference) {
      this.updatePendingBindingReference(reference)
      reference.key = this.nextResourceKey(reference.kind, reference)
      reference.required = this.isRequiredResourceReference(reference)
      this.resourceKeyChanged(reference)
    },
    updatePendingBindingReference(reference) {
      if (!reference) return
      const nodeId = this.nodeIdForReference(reference)
      const candidates = this.pendingResourceBindings.filter(binding =>
        binding.nodeId === nodeId
          && binding.environment === this.resourceEnvironment
          && (binding.resourceKind === reference.kind || binding.resourceKey === reference.key))
      if (candidates.length === 1) {
        candidates[0].resourceKind = reference.kind
        candidates[0].resourceKey = reference.key.trim()
      }
    },
    refreshCanvasNodeData() {
      this.definition.nodes.forEach(node => {
        const canvasNode = this.canvasNodes.find(item => item.id === node.id)
        if (canvasNode) canvasNode.data = this.canvasNodeData(node)
      })
    },
    latestNodeStatus(nodeId) {
      return this.debugNodeRuns
        .filter(item => item.nodeId === nodeId)
        .sort((left, right) => (right.attemptNo || 0) - (left.attemptNo || 0))[0]?.status || ''
    },
    openTestRun() {
      if (!this.currentDefinition?.currentPublishedVersionId) {
        this.$message.warning('请先发布工作流版本')
      }
      this.testDialogOpen = true
    },
    async startTestRun() {
      let input
      try {
        input = JSON.parse(this.testInputJson)
      } catch (error) {
        this.$message.error('测试输入必须是有效 JSON')
        return
      }
      this.testStarting = true
      try {
        const response = await startWorkflowExecution({
          definitionId: this.currentDefinition.id,
          workflowVersionId: this.currentDefinition.currentPublishedVersionId,
          input,
          environment: this.resourceEnvironment,
          idempotencyKey: `debug-${Date.now()}`
        })
        this.debugExecution = response.data
        this.debugNodeRuns = []
        this.testDialogOpen = false
        this.pollExecution()
      } finally {
        this.testStarting = false
      }
    },
    async pollExecution() {
      if (!this.debugExecution?.executionId) return
      clearTimeout(this.executionPollTimer)
      const executionId = this.debugExecution.executionId
      try {
        const [executionResponse, nodeRunResponse] = await Promise.all([
          getWorkflowExecution(executionId),
          listWorkflowNodeRuns(executionId)
        ])
        this.debugExecution = executionResponse.data
        this.debugNodeRuns = nodeRunResponse.data || []
        this.refreshCanvasNodeData()
        if (!this.executionFinished) {
          this.executionPollTimer = setTimeout(() => this.pollExecution(), 1800)
        }
      } catch (error) {
        this.stopPolling()
      }
    },
    stopPolling() {
      clearTimeout(this.executionPollTimer)
      this.executionPollTimer = null
    },
    toggleRetry(enabled) {
      if (enabled) {
        this.selectedNode.retryPolicy = {
          maxAttempts: 3,
          backoff: 'EXPONENTIAL',
          initialDelayMs: 1000,
          maxDelayMs: 30000,
          retryableErrors: []
        }
      } else {
        delete this.selectedNode.retryPolicy
      }
      this.markDirty()
    },
    addResourceReference() {
      const requiredKinds = this.selectedDescriptor?.requiredResourceKinds || []
      const kind = this.resourceKindOptions.find(item => !requiredKinds.includes(item.value))?.value || 'MODEL'
      this.selectedNode.resourceRefs.push({
        kind,
        key: this.nextResourceKey(kind),
        required: false
      })
      this.selectedInspectorTab = 'resource'
      this.markDirty()
    },
    removeResourceReference(index) {
      const reference = this.selectedNode.resourceRefs[index]
      const nodeId = this.selectedNode.id
      this.pendingResourceBindings = this.pendingResourceBindings.filter(binding =>
        !(binding.nodeId === nodeId
          && binding.resourceKind === reference?.kind
          && binding.resourceKey === reference?.key))
      this.selectedNode.resourceRefs.splice(index, 1)
      this.markDirty()
    },
    compensationChanged() {
      const targets = new Set(this.definition.nodes
        .map(node => node.compensationNodeId)
        .filter(Boolean))
      this.canvasNodes.forEach(node => {
        if (node.id !== '__start__' && node.id !== '__end__') {
          node.class = targets.has(node.id) ? 'compensation-node' : ''
        }
      })
      this.markDirty()
    },
    fitCanvas() {
      this.$refs.workflowCanvas?.fitView?.({padding: 0.2, duration: 250})
    },
    zoomCanvas(delta) {
      const flow = this.$refs.workflowCanvas
      if (delta > 0) flow?.zoomIn?.({duration: 160})
      else flow?.zoomOut?.({duration: 160})
    },
    edgeChanged() {
      if (this.selectedEdge.kind !== 'CONDITION') {
        delete this.selectedEdge.condition
        this.selectedEdge.default = false
      } else if (this.selectedEdge.default) {
        delete this.selectedEdge.condition
      } else if (!this.selectedEdge.condition) {
        this.selectedEdge.condition = { expression: '', priority: 100, onError: 'FAIL' }
      }
      const canvasEdge = this.canvasEdges.find(item => item.id === this.selectedEdge.id)
      if (canvasEdge) {
        canvasEdge.label = this.selectedEdge.kind === 'CONDITION'
          ? (this.selectedEdge.default ? '默认' : this.selectedEdge.condition?.expression || '条件')
          : this.selectedEdge.kind === 'SEMANTIC' ? this.selectedEdge.sourcePort || '分类分支' : ''
        canvasEdge.animated = this.selectedEdge.kind === 'CONDITION' || this.selectedEdge.kind === 'LOOP'
      }
      this.markDirty()
    },
    removeSelectedNode() {
      const nodeId = this.selectedNode.id
      this.pendingResourceBindings = this.pendingResourceBindings
        .filter(binding => binding.nodeId !== nodeId)
      this.definition.nodes = this.definition.nodes.filter(item => item.id !== nodeId)
      this.definition.nodes.forEach(node => {
        if (node.compensationNodeId === nodeId) delete node.compensationNodeId
      })
      this.definition.edges = this.definition.edges.filter(edge => edge.source !== nodeId && edge.target !== nodeId)
      this.canvasNodes = this.canvasNodes.filter(item => item.id !== nodeId)
      this.canvasEdges = this.canvasEdges.filter(edge => edge.source !== nodeId && edge.target !== nodeId)
      this.clearSelection()
      this.markDirty()
    },
    removeSelectedEdge() {
      const edgeId = this.selectedEdge.id
      this.definition.edges = this.definition.edges.filter(item => item.id !== edgeId)
      this.canvasEdges = this.canvasEdges.filter(item => item.id !== edgeId)
      this.clearSelection()
      this.markDirty()
    },
    syncPositions() {
      this.definition.nodes.forEach(node => {
        const canvasNode = this.canvasNodes.find(item => item.id === node.id)
        if (canvasNode?.position) node.ui = {...canvasNode.position}
      })
    },
    definitionJson() {
      this.syncPositions()
      return JSON.stringify(this.definition)
    },
    resetHistory() {
      clearTimeout(this.historyTimer)
      this.history = [JSON.stringify(this.definition)]
      this.historyIndex = 0
    },
    scheduleHistory() {
      if (this.historyRestoring || !this.canEdit) return
      clearTimeout(this.historyTimer)
      this.historyTimer = setTimeout(() => this.recordHistory(), 180)
    },
    recordHistory() {
      if (this.historyRestoring || !this.canEdit) return
      this.syncPositions()
      const snapshot = JSON.stringify(this.definition)
      if (snapshot === this.history[this.historyIndex]) return
      this.history = this.history.slice(0, this.historyIndex + 1)
      this.history.push(snapshot)
      if (this.history.length > 50) this.history.shift()
      this.historyIndex = this.history.length - 1
    },
    restoreHistory(index) {
      if (index < 0 || index >= this.history.length) return
      this.historyRestoring = true
      this.historyIndex = index
      this.definition = JSON.parse(this.history[index])
      this.reconcilePendingResourceBindings()
      this.buildCanvas()
      this.clearSelection()
      this.dirty = true
      this.$nextTick(() => {
        this.historyRestoring = false
      })
    },
    reconcilePendingResourceBindings() {
      this.pendingResourceBindings = this.pendingResourceBindings.flatMap(binding => {
        const node = this.definition.nodes.find(item => item.id === binding.nodeId)
        const references = node?.resourceRefs || []
        const reference = references.find(item =>
          item.kind === binding.resourceKind && item.key === binding.resourceKey)
          || references.find(item => item.kind === binding.resourceKind)
        return reference ? [{
          ...binding,
          resourceKind: reference.kind,
          resourceKey: reference.key
        }] : []
      })
    },
    undo() {
      if (!this.canUndo) return
      this.recordHistory()
      this.restoreHistory(this.historyIndex - 1)
    },
    redo() {
      if (!this.canRedo) return
      this.restoreHistory(this.historyIndex + 1)
    },
    async saveDraft() {
      if (!this.canEdit || this.configError || this.mappingError) return false
      if (!this.validateWorkflowMetadata()) return false
      this.saving = true
      try {
        let response
        if (this.currentDefinition?.id) {
          response = await updateWorkflowDraft(
            this.currentDefinition.id,
            this.definitionJson(),
            this.currentDefinition.draftRevision
          )
        } else {
          response = await createWorkflowDraft(this.definitionJson())
        }
        this.currentDefinition = response.data
        await this.flushPendingResourceBindings()
        this.dirty = false
        this.$message.success('草稿已保存')
        this.$emit('saved', response.data)
        return true
      } catch (error) {
        return false
      } finally {
        this.saving = false
      }
    },
    validateWorkflowMetadata() {
      const name = String(this.definition.metadata.name || '').trim()
      if (!name || name.length > 128) {
        this.workflowNameError = '工作流名称不能为空且不能超过 128 个字符'
        this.clearSelection()
        this.$message.warning(this.workflowNameError)
        return false
      }
      this.workflowNameError = ''
      let code = String(this.definition.metadata.code || '').trim()
      if (!code && !this.currentDefinition?.id) {
        code = this.generateWorkflowCode(name)
        this.definition.metadata.code = code
      }
      if (!/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(code)) {
        this.workflowCodeError = '编码必须以字母开头，且只能包含字母、数字、点、横线和下划线'
        this.clearSelection()
        this.$message.warning(this.workflowCodeError)
        return false
      }
      this.definition.metadata.name = name
      this.definition.metadata.code = code
      this.workflowCodeError = ''
      return true
    },
    generateWorkflowCode(name) {
      const normalized = name.normalize('NFKD')
        .replace(/[^A-Za-z0-9]+/g, '_')
        .replace(/^_+|_+$/g, '')
        .toLowerCase()
      const base = /^[a-z]/i.test(normalized) ? normalized : 'workflow'
      const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
      return `${base.slice(0, 48)}_${suffix}`.slice(0, 64)
    },
    workflowNameChanged() {
      this.workflowNameError = ''
      this.markDirty()
    },
    workflowCodeChanged() {
      this.workflowCodeError = ''
      this.markDirty()
    },
    async flushPendingResourceBindings() {
      if (!this.currentDefinition?.id || !this.pendingResourceBindings.length) return
      const bindingResponse = await listWorkflowResourceBindings({
        definitionId: this.currentDefinition.id
      })
      const persistedBindings = bindingResponse.data || []
      for (const pending of [...this.pendingResourceBindings]) {
        const node = this.definition.nodes.find(item => item.id === pending.nodeId)
        const references = node?.resourceRefs || []
        const reference = references.find(item =>
          item.kind === pending.resourceKind && item.key === pending.resourceKey)
          || references.find(item => item.kind === pending.resourceKind)
        if (!reference) {
          this.pendingResourceBindings = this.pendingResourceBindings
            .filter(item => item !== pending)
          continue
        }
        const existing = persistedBindings.find(binding =>
          binding.scopeType === 'WORKFLOW'
            && binding.definitionId === this.currentDefinition.id
            && binding.environment === pending.environment
            && binding.resourceKind === reference.kind
            && binding.resourceKey === reference.key)
        const saveResponse = await saveWorkflowResourceBinding({
          id: existing?.id || null,
          definitionId: this.currentDefinition.id,
          scopeType: 'WORKFLOW',
          environment: pending.environment,
          resourceKind: reference.kind,
          resourceKey: reference.key.trim(),
          resourceId: pending.resourceId,
          expectedLockVersion: existing?.lockVersion ?? null
        })
        if (saveResponse.data) persistedBindings.push(saveResponse.data)
        this.pendingResourceBindings = this.pendingResourceBindings
          .filter(item => item !== pending)
      }
      await this.refreshResourceContext()
    },
    async validate() {
      if (!this.currentDefinition?.id || this.dirty) {
        const saved = await this.saveDraft()
        if (!saved) return false
      }
      this.validating = true
      try {
        const response = await validateWorkflowDraft(this.currentDefinition.id)
        this.diagnostics = response.data?.diagnostics || []
        if (response.data?.valid) {
          this.validationPassed = true
          this.$message.success('校验通过，可以发布')
          return true
        }
        this.$message.warning(`发现 ${this.diagnostics.length} 个校验问题`)
        this.validationPassed = false
        return false
      } finally {
        this.validating = false
      }
    },
    async publish() {
      if (!this.canPublish) return
      const valid = await this.validate()
      if (!valid) return
      this.publishing = true
      try {
        const response = await publishWorkflowDraft(
          this.currentDefinition.id,
          this.currentDefinition.draftRevision
        )
        this.diagnostics = response.data?.diagnostics || []
        if (!response.data?.published) {
          this.$message.warning('发布校验未通过')
          return
        }
        this.currentDefinition.currentPublishedVersionId = response.data.version.versionId
        this.$message.success(`已发布版本 v${response.data.version.versionNo}`)
        this.$emit('published', response.data)
      } finally {
        this.publishing = false
      }
    },
    focusDiagnostic(item) {
      if (!item.nodeId) return
      const node = this.definition.nodes.find(value => value.id === item.nodeId)
      if (node) {
        this.selectedNode = node
        this.selectedEdge = null
        this.selectedNodeConfig = JSON.stringify(node.config || {}, null, 2)
        this.selectedNodeInputMapping = JSON.stringify(node.inputMapping || {}, null, 2)
      }
    },
    markDirty() {
      if (this.canEdit) {
        this.dirty = true
        this.validationPassed = false
        this.scheduleHistory()
      }
    },
    async back() {
      if (this.dirty && this.canEdit) {
        try {
          await this.$confirm('当前草稿尚未保存，确认离开设计器？', '未保存变更', {type: 'warning'})
        } catch (error) {
          return
        }
      }
      this.$emit('back')
    }
  }
}
</script>

<style scoped>
.workflow-workbench {
  height: calc(100vh - 120px);
  min-height: 650px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  border-radius: var(--workflow-radius, 18px);
  overflow: hidden;
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: var(--workflow-shadow, none);
}

.workbench-header {
  min-height: 70px;
  padding: 11px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--workflow-border, var(--el-border-color-light));
  background: var(--workflow-surface, var(--el-bg-color));
}

.header-left,
.title-row,
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-left code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.title-row strong {
  font-size: 16px;
  letter-spacing: -0.01em;
}

.dirty-state {
  color: var(--el-color-warning);
  font-size: 12px;
}

.workbench-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 220px minmax(420px, 1fr) 300px;
}

.node-palette,
.inspector-panel {
  padding: 16px;
  overflow: auto;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.node-palette {
  border-right: 1px solid var(--el-border-color-light);
}

.inspector-panel {
  border-left: 1px solid var(--el-border-color-light);
}

.panel-title {
  margin-bottom: 13px;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.02em;
}

.descriptor-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.descriptor-item {
  position: relative;
  padding: 12px 12px 12px 15px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 11px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.descriptor-item::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  content: '';
  background: var(--workflow-primary, var(--el-color-primary));
}

.descriptor-item:not(:disabled):hover {
  border-color: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 8px 18px rgb(15 23 42 / 8%);
  transform: translateY(-1px);
}

.descriptor-item:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.descriptor-item small {
  color: var(--el-text-color-secondary);
}

.descriptor-name {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.descriptor-name em {
  padding: 2px 6px;
  border-radius: 999px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
  text-transform: uppercase;
}

.canvas-panel,
.workflow-canvas {
  min-width: 0;
  min-height: 0;
  height: 100%;
}

.canvas-panel {
  position: relative;
  background:
    radial-gradient(circle at 50% 0, var(--workflow-primary-soft, transparent), transparent 42%),
    var(--el-bg-color);
}

.canvas-toolbar {
  position: absolute;
  z-index: 5;
  top: 12px;
  left: 50%;
  padding: 6px 8px 6px 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  border-radius: 12px;
  color: var(--el-text-color-secondary);
  background: color-mix(in srgb, var(--workflow-surface, var(--el-bg-color)) 92%, transparent);
  box-shadow: 0 10px 28px rgb(15 23 42 / 10%);
  font-size: 11px;
  backdrop-filter: blur(12px);
  transform: translateX(-50%);
}

.workflow-canvas :deep(.vue-flow__node) {
  min-width: 132px;
  padding: 11px 14px;
  border: 1px solid var(--workflow-border, var(--el-border-color));
  border-radius: 11px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  box-shadow: 0 8px 22px rgb(15 23 42 / 8%);
}

.workflow-canvas :deep(.vue-flow__node.selected) {
  border-color: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 0 0 3px var(--workflow-primary-soft, var(--el-color-primary-light-9)),
    0 10px 24px rgb(15 23 42 / 10%);
}

.workflow-canvas :deep(.vue-flow__node.compensation-node) {
  border-style: dashed;
  border-color: var(--el-color-warning);
  background: linear-gradient(135deg, var(--el-color-warning-light-9), var(--el-bg-color));
}

.workflow-canvas :deep(.vue-flow__node.compensation-node::after) {
  position: absolute;
  right: 8px;
  bottom: 5px;
  content: '补偿';
  color: var(--el-color-warning-dark-2);
  font-size: 10px;
}

.workflow-canvas :deep(.vue-flow__node-input),
.workflow-canvas :deep(.vue-flow__node-output) {
  color: #fff;
  border-color: transparent;
  background: linear-gradient(135deg, var(--workflow-primary, #2563eb), #14b8a6);
}

.field-error {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
}

.field-hint {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
}

.retry-delay-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  width: 100%;
}

.retry-delay-row :deep(.el-input-number) {
  width: 100%;
}

.resource-reference-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-reference-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr auto auto;
  align-items: center;
  gap: 6px;
}

.diagnostic-panel {
  max-height: 180px;
  padding: 10px 14px;
  overflow: auto;
  border-top: 1px solid var(--el-border-color-light);
}

.diagnostic-header,
.diagnostic-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.diagnostic-header {
  justify-content: space-between;
}

.diagnostic-item {
  padding: 7px 0;
  cursor: pointer;
}

.diagnostic-item code {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

@media (max-width: 1200px) {
  .workbench-body {
    grid-template-columns: 180px minmax(360px, 1fr) 260px;
  }
}
</style>

<style scoped>
.workflow-workbench {
  position: relative;
  height: calc(100vh - 112px);
  min-height: 680px;
  border-radius: 14px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-canvas, var(--el-bg-color-page));
  box-shadow: var(--workflow-shadow, 0 14px 40px rgb(32 45 80 / 7%));
}

.workbench-header {
  min-height: 66px;
  padding: 0 16px;
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto minmax(460px, 1fr);
  gap: 14px;
  background: var(--workflow-surface, var(--el-bg-color));
}

.workflow-title {
  min-width: 0;
}

.workflow-title code {
  display: block;
  max-width: 330px;
  margin-top: 3px;
  overflow: hidden;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-row strong {
  max-width: 230px;
  overflow: hidden;
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-label {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 12px;
}

.header-context {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-context :deep(.el-select) {
  width: 112px;
}

.header-context :deep(.environment-select) {
  width: 124px;
}

.context-help {
  flex: 0 0 auto;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  cursor: help;
}

.header-actions {
  justify-content: flex-end;
}

.header-actions :deep(.el-button) {
  height: 34px;
  margin-left: 0;
  border-color: var(--workflow-border-strong, var(--el-border-color));
  border-radius: 9px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  font-weight: 600;
}

.header-actions :deep(.el-button:hover) {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.header-actions :deep(.el-button-group .el-button) {
  border-radius: 0;
}

.header-actions :deep(.el-button-group .el-button:first-child) {
  border-radius: 9px 0 0 9px;
}

.header-actions :deep(.el-button-group .el-button:last-child) {
  border-radius: 0 9px 9px 0;
}

.header-actions :deep(.el-button--primary) {
  min-width: 82px;
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: #fff;
  background: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 8px 18px color-mix(in srgb, var(--workflow-primary, #625bf6) 24%, transparent);
}

.header-actions :deep(.el-button--primary:hover) {
  border-color: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
  color: #fff;
  background: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
}

.validation-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 12px;
}

.validation-state.passed {
  color: #0aa674;
}

.workbench-body {
  grid-template-columns: 268px minmax(520px, 1fr) 360px;
  background: var(--workflow-canvas, var(--el-bg-color-page));
}

.workbench-body--guided {
  grid-template-columns: 248px minmax(420px, 1fr) minmax(540px, 680px);
}

.workbench-body--mapping {
  grid-template-columns: 248px minmax(380px, 1fr) minmax(680px, 760px);
}

.node-palette,
.inspector-panel {
  padding: 0;
  background: var(--workflow-surface, var(--el-bg-color));
}

.node-palette {
  padding: 16px 14px;
  border-right-color: var(--workflow-border, var(--el-border-color-lighter));
}

.palette-heading {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.palette-heading strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 14px;
}

.palette-heading small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
}

.palette-tabs {
  margin: 12px 0;
  padding: 3px;
  display: flex;
  gap: 3px;
  overflow-x: auto;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 9px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.palette-tabs button {
  min-height: 28px;
  padding: 5px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: transparent;
  font-size: 11px;
  cursor: pointer;
}

.palette-tabs button.active {
  color: var(--workflow-primary, var(--el-color-primary));
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 22%, transparent);
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  box-shadow: 0 2px 7px rgb(15 23 42 / 8%);
  font-weight: 700;
}

.descriptor-list {
  gap: 3px;
  margin-top: 0;
}

.descriptor-item {
  min-height: 52px;
  padding: 7px 6px;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  border-color: transparent;
  border-radius: 9px;
  box-shadow: none;
}

.descriptor-item::before {
  display: none;
}

.descriptor-item:not(:disabled):hover {
  border-color: var(--workflow-border-strong, var(--el-border-color-light));
  background: var(--workflow-hover, var(--el-fill-color-light));
  box-shadow: none;
  transform: none;
}

.descriptor-icon,
.inspector-node-icon {
  width: 31px;
  height: 31px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.descriptor-icon--data {
  color: #0a9f70;
  background: var(--el-color-success-light-9);
}

.descriptor-icon--control {
  color: #df8915;
  background: var(--el-color-warning-light-9);
}

.descriptor-icon--integration {
  color: #1477d4;
  background: var(--el-color-primary-light-9);
}

.descriptor-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.descriptor-copy strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 12px;
  font-weight: 620;
}

.descriptor-copy small {
  max-width: 170px;
  overflow: hidden;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.descriptor-add {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
}

.canvas-panel {
  background: var(--workflow-canvas, var(--el-bg-color-page));
}

.canvas-toolbar {
  top: 10px;
  border-color: var(--workflow-border-strong, var(--el-border-color-light));
  border-radius: 9px;
  background: color-mix(in srgb, var(--workflow-surface-raised, var(--el-bg-color-overlay)) 94%, transparent);
  box-shadow: var(--workflow-shadow, 0 8px 22px rgb(32 45 80 / 8%));
}

.workflow-canvas :deep(.vue-flow__node-workflow) {
  width: auto;
  min-width: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.workflow-canvas :deep(.vue-flow__node-workflow.selected) {
  border: 0;
  box-shadow: none;
}

.workflow-canvas :deep(.vue-flow__edge-path) {
  stroke: var(--workflow-primary, var(--el-color-primary));
}

.workflow-canvas :deep(.vue-flow__edge-textbg) {
  fill: var(--workflow-surface, var(--el-bg-color));
}

.inspector-panel {
  border-left-color: var(--workflow-border, var(--el-border-color-lighter));
}

.inspector-panel--guided {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.inspector-panel--mapping {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: -12px 0 34px rgb(42 34 110 / 6%);
}

.inspector-panel--mapping .inspector-tabs {
  min-height: 0;
  flex: 1;
  height: auto;
}

.inspector-panel--mapping .inspector-tabs :deep(.el-tabs__content) {
  min-height: 0;
  padding-bottom: 36px;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.inspector-panel--mapping .inspector-tabs :deep(.el-tab-pane) {
  min-height: 100%;
}

.inspector-heading {
  min-height: 72px;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--workflow-border, var(--el-border-color-lighter));
}

.inspector-heading.simple {
  display: flex;
}

.inspector-heading > div {
  min-width: 0;
}

.inspector-heading strong,
.inspector-heading small {
  display: block;
}

.inspector-heading strong {
  overflow: hidden;
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inspector-heading small {
  margin-top: 4px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.inspector-tabs {
  height: calc(100% - 116px);
}

.inspector-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 14px;
}

.inspector-tabs :deep(.el-tabs__item) {
  height: 44px;
  padding: 0 11px;
  font-size: 12px;
}

.inspector-tabs :deep(.el-tabs__active-bar) {
  background: var(--workflow-primary, var(--el-color-primary));
}

.inspector-tabs :deep(.el-tabs__item) {
  color: var(--workflow-text, var(--el-text-color-primary)) !important;
}

.inspector-tabs :deep(.el-tabs__item:hover) {
  color: var(--workflow-primary, var(--el-color-primary)) !important;
}

.inspector-tabs :deep(.el-tabs__item.is-active) {
  color: var(--workflow-primary, #625bf6) !important;
  font-weight: 700;
  text-shadow: none;
}

.inspector-tabs :deep(.el-tabs__item.is-disabled) {
  color: var(--el-text-color-disabled) !important;
}

.inspector-tabs :deep(.el-tabs__content) {
  height: calc(100% - 44px);
  padding: 15px 16px 24px;
  overflow: auto;
}

.integration-stepper {
  min-height: 82px;
  padding: 18px 28px 14px;
  display: grid;
  grid-template-columns: auto minmax(48px, 1fr) auto;
  align-items: center;
  gap: 14px;
  background: var(--workflow-surface, var(--el-bg-color));
}

.integration-stepper button {
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  border: 0;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: transparent;
  cursor: pointer;
}

.integration-stepper button:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.integration-stepper button > span {
  width: 29px;
  height: 29px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 50%;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-surface, var(--el-bg-color));
  font-size: 12px;
}

.integration-stepper button > strong {
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
}

.integration-stepper button.active,
.integration-stepper button.completed {
  color: var(--workflow-text, var(--el-text-color-primary));
}

.integration-stepper button.active > span,
.integration-stepper button.completed > span {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: #fff;
  background: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 5px 12px color-mix(in srgb, var(--workflow-primary, #625bf6) 20%, transparent);
}

.integration-stepper > i {
  height: 1px;
  background: var(--workflow-border-strong, var(--el-border-color));
}

.api-config-tabs {
  margin: 0 28px 10px;
  padding: 4px;
  display: flex;
  gap: 4px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.api-config-tabs button {
  min-height: 32px;
  padding: 0 13px;
  flex: 1;
  border: 1px solid transparent;
  border-radius: 7px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}

.api-config-tabs button.active {
  color: var(--workflow-primary, var(--el-color-primary));
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 22%, transparent);
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  box-shadow: 0 2px 8px rgb(15 23 42 / 8%);
  font-weight: 700;
}

.api-config-tabs button.active::after {
  display: none;
}

.inspector-tabs--guided {
  min-height: 0;
  flex: 1;
  height: auto;
}

.inspector-tabs--guided :deep(.el-tabs__header) {
  display: none;
}

.inspector-tabs--guided :deep(.el-tabs__content) {
  height: 100%;
  padding: 10px 28px 24px;
}

.inspector-tabs--guided :deep(.el-tab-pane) {
  min-height: 100%;
}

.inspector-panel--guided .inspector-footer,
.inspector-panel--mapping .inspector-footer {
  position: static;
  width: 100%;
  flex: 0 0 auto;
}

.edge-form {
  padding: 18px 16px;
}

.inspector-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 360px;
  padding: 8px 16px;
  border-top: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  background: var(--workflow-surface, var(--el-bg-color));
}

.resource-card {
  margin-bottom: 13px;
  padding: 12px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.resource-alert {
  margin-bottom: 12px;
}

.resource-card-title,
.resource-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.resource-card-title {
  margin-bottom: 9px;
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 12px;
}

.resource-card-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 5px;
}

.resource-delete {
  flex: 0 0 auto;
}

.resource-advanced {
  margin-top: 7px;
  border: 0;
}

.resource-advanced :deep(.el-collapse-item__header) {
  height: 32px;
  border: 0;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: transparent;
  font-size: 11px;
}

.resource-advanced :deep(.el-collapse-item__wrap) {
  border: 0;
  background: transparent;
}

.resource-advanced :deep(.el-collapse-item__content) {
  padding: 4px 0 2px;
}

.resource-advanced-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.resource-advanced-fields {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 9px;
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
}

.resource-advanced-fields > label {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.resource-advanced-fields > label > span,
.resource-behavior > span {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
}

.resource-behavior {
  min-height: 32px;
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.resource-behavior strong {
  color: var(--el-color-warning-dark-2);
  font-size: 11px;
}

.resource-behavior :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.resource-behavior :deep(.el-radio) {
  margin-right: 0;
}

.resource-advanced-fields > small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
  line-height: 1.55;
}

.resource-add {
  width: 100%;
  height: 36px;
  border-style: dashed;
  border-color: var(--workflow-border-strong, var(--el-border-color));
  border-radius: 9px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: transparent;
}

.resource-add:hover {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.resource-option-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.resource-option-tags {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 4px;
}

.resource-scope-badge,
.resource-status-badge {
  height: 22px;
  padding: 0 7px;
  display: inline-flex;
  align-items: center;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 650;
  line-height: 1;
  white-space: nowrap;
}

.resource-scope-badge {
  border-color: color-mix(in srgb, #625bf6 26%, transparent);
  color: #5148dd;
  background: color-mix(in srgb, #625bf6 10%, var(--el-bg-color-overlay));
}

.resource-scope-badge.shared {
  border-color: var(--el-border-color-light);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.resource-status-badge.available {
  border-color: color-mix(in srgb, #16a86f 28%, transparent);
  color: #087c51;
  background: color-mix(in srgb, #16a86f 11%, var(--el-bg-color-overlay));
}

.resource-status-badge.unavailable {
  border-color: color-mix(in srgb, #ef5261 28%, transparent);
  color: #d63848;
  background: color-mix(in srgb, #ef5261 10%, var(--el-bg-color-overlay));
}

:global(html.dark) .resource-scope-badge {
  border-color: rgb(129 140 248 / 34%);
  color: #b8b4ff;
  background: rgb(129 140 248 / 14%);
}

:global(html.dark) .resource-scope-badge.shared {
  border-color: var(--el-border-color-light);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

:global(html.dark) .resource-status-badge.available {
  border-color: rgb(52 211 153 / 30%);
  color: #6ee7b7;
  background: rgb(16 185 129 / 12%);
}

:global(html.dark) .resource-status-badge.unavailable {
  border-color: rgb(251 113 133 / 30%);
  color: #fda4af;
  background: rgb(244 63 94 / 12%);
}

.resource-option small {
  max-width: 230px;
  overflow: hidden;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-option {
  width: 100%;
  min-width: 0;
}

.resource-select :deep(.el-select__wrapper) {
  min-height: 38px;
  border-radius: 9px;
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  box-shadow: 0 0 0 1px var(--workflow-border-strong, var(--el-border-color)) inset;
}

.resource-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--workflow-primary, var(--el-color-primary)) inset,
    0 0 0 3px var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.resource-select :deep(.el-select__selected-item),
.resource-select :deep(.el-select__placeholder.is-transparent) {
  color: var(--workflow-text, var(--el-text-color-primary)) !important;
  font-weight: 650;
  opacity: 1 !important;
}

:global(.workflow-resource-popper.el-popper) {
  overflow: hidden;
  border-color: var(--el-border-color-light);
  border-radius: 11px;
  background: var(--el-bg-color-overlay);
  box-shadow: 0 16px 40px rgb(15 23 42 / 16%);
}

:global(.workflow-resource-popper .el-select-dropdown__wrap) {
  max-height: 320px;
}

:global(.workflow-resource-popper .el-select-dropdown__item) {
  height: auto !important;
  min-height: 58px !important;
  margin: 4px 6px !important;
  padding: 9px 10px !important;
  display: flex;
  align-items: center;
  line-height: 1.35 !important;
}

:global(.workflow-resource-popper .el-select-dropdown__item.is-hovering) {
  color: var(--el-text-color-primary) !important;
  background: color-mix(in srgb, #625bf6 7%, var(--el-bg-color-overlay)) !important;
}

:global(.workflow-resource-popper .el-select-dropdown__item.is-selected) {
  color: var(--el-text-color-primary) !important;
  background: color-mix(in srgb, #625bf6 12%, var(--el-bg-color-overlay)) !important;
  box-shadow: 3px 0 0 #625bf6 inset;
}

:global(.workflow-resource-popper .resource-option) {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

:global(.workflow-resource-popper .resource-option-copy) {
  min-width: 0;
  flex: 1;
}

:global(.workflow-resource-popper .resource-option-tags) {
  flex: 0 0 auto;
}

:global(.workflow-resource-popper .resource-option strong) {
  color: var(--el-text-color-primary) !important;
  font-weight: 700 !important;
}

:global(.workflow-resource-popper .resource-option small) {
  color: var(--el-text-color-secondary) !important;
}

:global(html:not(.dark) body .workflow-resource-popper.el-popper .el-select-dropdown__item.is-selected .resource-option strong) {
  color: var(--workflow-primary, #625bf6) !important;
}

:global(html:not(.dark) body .workflow-resource-popper.el-popper .el-select-dropdown__item.is-selected .resource-option small) {
  color: var(--el-text-color-primary) !important;
  opacity: 0.8;
}

:global(html.dark .workflow-resource-popper.el-popper) {
  box-shadow: 0 20px 48px rgb(0 0 0 / 46%);
}

:global(html.dark .workflow-resource-popper .el-select-dropdown__item.is-hovering) {
  color: var(--el-text-color-primary) !important;
  background: rgb(129 140 248 / 12%) !important;
}

:global(html.dark .workflow-resource-popper .el-select-dropdown__item.is-selected) {
  color: var(--el-text-color-primary) !important;
  background: rgb(129 140 248 / 18%) !important;
  box-shadow: 3px 0 0 #818cf8 inset;
}

:global(html.dark .workflow-resource-popper .resource-scope-badge) {
  border-color: rgb(165 180 252 / 42%) !important;
  color: #d2d0ff !important;
  background: rgb(129 140 248 / 22%) !important;
}

:global(html.dark .workflow-resource-popper .resource-scope-badge.shared) {
  border-color: rgb(148 163 184 / 30%) !important;
  color: #cbd5e1 !important;
  background: rgb(148 163 184 / 12%) !important;
}

:global(html.dark .workflow-resource-popper .resource-status-badge.available) {
  border-color: rgb(110 231 183 / 38%) !important;
  color: #a7f3d0 !important;
  background: rgb(16 185 129 / 18%) !important;
}

:global(html.dark .workflow-resource-popper .resource-status-badge.unavailable) {
  border-color: rgb(253 164 175 / 38%) !important;
  color: #fecdd3 !important;
  background: rgb(244 63 94 / 18%) !important;
}

.empty-resource {
  margin-bottom: 14px;
  padding: 28px 14px;
  border: 1px dashed var(--workflow-border-strong, var(--el-border-color));
  border-radius: 10px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  text-align: center;
  font-size: 12px;
}

.execution-dock {
  position: absolute;
  z-index: 20;
  left: 50%;
  bottom: 18px;
  width: min(620px, calc(100% - 700px));
  min-width: 460px;
  padding: 9px 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 12px;
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  box-shadow: var(--workflow-shadow-float, 0 14px 36px rgb(32 45 80 / 14%));
  transform: translateX(-50%);
}

.execution-dock span,
.execution-dock code {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.execution-progress {
  min-width: 160px;
  flex: 1;
}

.execution-progress strong {
  display: block;
  margin-bottom: 5px;
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 11px;
}

.test-run-form {
  margin-top: 16px;
}

@media (max-width: 1360px) {
  .workbench-header {
    grid-template-columns: minmax(220px, 1fr) auto;
    padding-block: 8px;
  }

  .header-context {
    grid-column: 1 / -1;
    justify-content: center;
  }

  .workbench-body {
    grid-template-columns: 220px minmax(440px, 1fr) 320px;
  }

  .workbench-body--guided {
    grid-template-columns: 210px minmax(340px, 1fr) minmax(480px, 580px);
  }

  .workbench-body--mapping {
    grid-template-columns: 210px minmax(320px, 1fr) minmax(600px, 660px);
  }

  .inspector-footer {
    width: 320px;
  }
}

@media (max-width: 1080px) {
  .node-palette {
    display: none;
  }

  .workbench-body {
    grid-template-columns: minmax(420px, 1fr) 320px;
  }

  .workbench-body--guided {
    grid-template-columns: minmax(360px, 1fr) minmax(460px, 520px);
  }

  .workbench-body--mapping {
    grid-template-columns: minmax(320px, 1fr) minmax(560px, 620px);
  }
}

@media (max-width: 920px) {
  .workbench-header {
    grid-template-columns: minmax(0, 1fr);
  }

  .header-context {
    grid-column: auto;
    justify-content: flex-start;
  }

  .header-actions {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .header-actions :deep(.el-button),
  .validation-state {
    flex: 0 0 auto;
  }

  .workbench-body--mapping {
    grid-template-columns: minmax(280px, 38%) minmax(520px, 62%);
    overflow-x: auto;
  }
}
</style>
