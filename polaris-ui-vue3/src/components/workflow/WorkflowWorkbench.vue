<template>
  <div class="workflow-workbench">
    <header class="workbench-header">
      <div class="header-left">
        <el-button text circle title="返回" aria-label="返回" @click="back"><el-icon><Back /></el-icon></el-button>
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
      <div class="header-actions">
        <el-button-group v-if="canEdit">
          <el-button :disabled="!canUndo" title="撤销" aria-label="撤销" @click="undo"><el-icon><RefreshLeft /></el-icon></el-button>
          <el-button :disabled="!canRedo" title="重做" aria-label="重做" @click="redo"><el-icon><RefreshRight /></el-icon></el-button>
        </el-button-group>
        <span :class="['validation-state', {passed: validationPassed}]">
          <el-icon><CircleCheck /></el-icon>{{ validationPassed ? '已通过校验' : '等待校验' }}
        </span>
        <el-button :loading="validating" :disabled="publishing || saving" @click="validate">校验</el-button>
        <el-button v-if="canDebug || canExecute" @click="openTestRun">
          <el-icon><VideoPlay /></el-icon>
          <span>{{ canDebug && workflowDraftTestAvailability.available
            ? '试运行当前草稿'
            : '测试已发布版本' }}</span>
        </el-button>
        <el-button v-if="canEdit" :loading="saving" :disabled="publishing || validating" @click="saveDraft">保存</el-button>
        <el-button
          v-if="canPublish"
          type="primary"
          :loading="publishing"
          :disabled="publishing || validating || saving"
          @click="publish"
        >发布</el-button>
      </div>
    </header>

    <div :class="['workbench-body', {'workbench-body--dock-open': !inspectorCollapsed}]">
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

      <section class="workspace-stage">
      <main class="canvas-panel">
        <div class="canvas-toolbar">
          <span>{{ definition.nodes.length }} 节点 · {{ definition.edges.length }} 连线</span>
          <el-button-group>
            <el-button size="small" title="缩小" aria-label="缩小画布" @click="zoomCanvas(-0.15)">
              <el-icon><ZoomOut /></el-icon>
            </el-button>
            <el-button size="small" title="适应画布" aria-label="适应画布" @click="fitCanvas">
              <el-icon><Aim /></el-icon>
            </el-button>
            <el-button size="small" title="放大" aria-label="放大画布" @click="zoomCanvas(0.15)">
              <el-icon><ZoomIn /></el-icon>
            </el-button>
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
          @node-context-menu="suppressNodeContextMenu"
          @edge-click="selectEdge"
          @node-drag-start="nodeDragStarted"
          @node-drag-stop="nodeDragStopped"
          @pane-click="clearSelection"
        >
          <Background :gap="20" pattern-color="var(--workflow-grid)" />
          <template #node-workflow="nodeProps">
            <WorkflowCanvasNode
              :data="{...nodeProps.data, selected: nodeProps.selected}"
              @test="nodeProps.id === '__start__' ? openTestRun() : openCanvasNodeTest(nodeProps.id)"
            />
          </template>
        </VueFlow>
      </main>

      <aside
        :class="['inspector-panel', {
        'inspector-panel--guided': isGuidedIntegrationNode,
        'inspector-panel--mapping': isMappingEditorOpen,
        'is-collapsed': inspectorCollapsed
      }]"
        :style="inspectorDockStyle"
      >
        <button
          v-if="!inspectorCollapsed"
          type="button"
          class="inspector-resize-handle"
          title="拖动调整面板高度"
          aria-label="拖动调整面板高度"
          @pointerdown="startInspectorResize"
        ><span></span></button>
        <button
          v-if="inspectorCollapsed"
          type="button"
          class="inspector-collapsed-bar"
          @click="expandInspector"
        >
          <span><el-icon><Setting /></el-icon>{{ inspectorSummary }}</span>
          <small>点击展开</small>
        </button>
        <template v-else-if="selectedNode">
          <div class="inspector-heading">
            <div><span class="inspector-node-icon"><el-icon><component :is="nodeIcon(selectedNode.type)" /></el-icon></span></div>
            <div><strong>{{ selectedNode.name }}</strong><small>{{ selectedNode.id }}</small></div>
            <el-button text circle title="收起面板" aria-label="收起面板" @click="collapseInspector"><el-icon><Close /></el-icon></el-button>
          </div>
          <nav
            v-if="isGuidedIntegrationNode"
            class="integration-stepper"
            :aria-label="isApiNode ? 'API 节点配置步骤' : '数据库查询节点配置步骤'"
          >
            <button
              type="button"
              :class="{active: selectedInspectorTab === 'resource', completed: selectedInspectorTab !== 'resource'}"
              @click="selectedInspectorTab = 'resource'"
            >
              <span>1</span>
              <strong>{{ isApiNode ? '连接服务' : '连接数据库' }}</strong>
            </button>
            <i></i>
            <button
              type="button"
              :class="{active: selectedInspectorTab !== 'resource'}"
              :disabled="isApiNode
                ? !apiConnectorReference || !selectedResourceId(apiConnectorReference)
                : !datasourceReference || !selectedResourceId(datasourceReference)"
              @click="selectedInspectorTab = 'config'"
            >
              <span>2</span>
              <strong>{{ isApiNode ? '配置请求' : '编写查询' }}</strong>
            </button>
          </nav>
          <div v-if="isGuidedIntegrationNode && selectedInspectorTab !== 'resource'" class="api-config-tabs">
            <button type="button" :class="{active: selectedInspectorTab === 'config'}" @click="selectedInspectorTab = 'config'">
              {{ isApiNode ? '请求参数' : '查询设置' }}
            </button>
            <button type="button" :class="{active: selectedInspectorTab === 'mapping'}" @click="selectedInspectorTab = 'mapping'">输入输出</button>
            <button type="button" :class="{active: selectedInspectorTab === 'policy'}" @click="selectedInspectorTab = 'policy'">运行策略</button>
          </div>
          <el-tabs v-model="selectedInspectorTab" :class="['inspector-tabs', {'inspector-tabs--guided': isGuidedIntegrationNode}]">
            <el-tab-pane :label="isApiNode ? '2 配置请求' : isDatabaseNode ? '2 编写查询' : '配置'" name="config">
              <el-form label-position="top" size="small">
                <el-form-item label="节点名称"><el-input v-model="selectedNode.name" :disabled="!canEdit" @input="nodeChanged" /></el-form-item>
                <WorkflowSemanticClassifierEditor
                  v-if="isClassifierNode"
                  :config="selectedNode.config"
                  :input-mapping="selectedNode.inputMapping || {}"
                  :source-groups="classifierSourceGroups"
                  :target-options="classifierTargetOptions"
                  :branch-targets="classifierBranchTargets"
                  :disabled="!canEdit"
                  @update:config="classifierConfigChanged"
                  @update:input-mapping="visualInputMappingChanged"
                  @update:branch-target="classifierBranchTargetChanged"
                  @test="openNodeTest"
                />
                <WorkflowTransformEditor
                  v-else-if="isTransformNode"
                  :config="selectedNode.config"
                  :input-mapping="selectedNode.inputMapping || {}"
                  :definition="definition"
                  :selected-node="selectedNode"
                  :descriptors="descriptors"
                  :resolved-node-schemas="resolvedNodeSchemas"
                  :downstream-impacts="transformDownstreamImpacts"
                  :disabled="!canEdit"
                  @update:config="schemaConfigChanged"
                  @update:input-mapping="visualInputMappingChanged"
                />
                <WorkflowDatabaseQueryStep
                  v-else-if="isDatabaseNode"
                  :config="selectedNode.config"
                  :selected-resource-id="datasourceReference ? selectedResourceId(datasourceReference) : ''"
                  :disabled="!canEdit"
                  @update:config="schemaConfigChanged"
                />
                <template v-else>
                  <section v-if="isLlmNode" class="node-prompt-editor">
                    <div class="node-prompt-heading">
                      <div>
                        <strong>节点提示词</strong>
                        <small>告诉大模型当前节点要完成什么任务</small>
                      </div>
                      <el-tag v-if="nodePromptValue" size="small" type="success" effect="plain">已填写</el-tag>
                    </div>
                    <el-input
                      :model-value="nodePromptValue"
                      type="textarea"
                      :rows="7"
                      maxlength="12000"
                      show-word-limit
                      resize="vertical"
                      :disabled="!canEdit"
                      placeholder="例如：将输入的 JSON 转换成清晰、自然的文本，只输出转换结果。\n或：总结输入内容，提取关键信息。"
                      @input="nodePromptChanged"
                    />
                    <small class="node-prompt-hint">
                      上游内容请在“输入输出”中选择；运行时会自动附在提示词后，无需手工拼接 JSON。
                    </small>
                  </section>
                  <WorkflowSchemaConfig
                    :schema="nodeConfigFormSchema"
                    :model-value="selectedNode.config"
                    :disabled="!canEdit"
                    @update:model-value="schemaConfigChanged"
                  />
                  <WorkflowDisclosureCard
                    v-if="isLlmNode"
                    class="inspector-advanced structured-output-disclosure"
                    name="structured-output"
                    title="结构化输出格式（可选）"
                    description="默认输出普通文本；仅在下游需要按字段读取 JSON 时配置"
                    :badge="structuredOutputConfigured ? '已配置' : ''"
                    badge-type="success"
                  >
                    <WorkflowSchemaConfig
                      :schema="llmStructuredOutputSchema"
                      :model-value="selectedNode.config"
                      :disabled="!canEdit"
                      @update:model-value="schemaConfigChanged"
                    />
                    <small class="inspector-advanced-hint">
                      例如定义 summary、score 等字段；不需要固定 JSON 字段时请保持为空。
                    </small>
                  </WorkflowDisclosureCard>
                  <WorkflowDisclosureCard
                    class="inspector-advanced"
                    name="json"
                    title="高级 JSON 配置"
                    description="仅在需要编辑表单未展示的字段时使用"
                    :badge="configError ? '格式有误' : ''"
                    badge-type="danger"
                  >
                    <el-input
                      v-model="selectedNodeConfig"
                      class="inspector-json-editor"
                      type="textarea"
                      :rows="9"
                      :disabled="!canEdit"
                      spellcheck="false"
                      @input="configChanged"
                    />
                    <div v-if="configError" class="field-error">{{ configError }}</div>
                    <small v-else class="inspector-advanced-hint">修改后会同步到当前节点配置；常规字段优先使用上方表单。</small>
                  </WorkflowDisclosureCard>
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
                <el-button v-if="canEdit" class="resource-add" plain @click="addResourceReference"><el-icon><Plus /></el-icon><span>添加扩展资源</span></el-button>
              </template>
            </el-tab-pane>
            <el-tab-pane label="输入输出" name="mapping">
              <section v-if="isTransformNode" class="transform-mapping-summary">
                <div>
                  <strong>转换来源与输出字段已统一到“配置”页</strong>
                  <small>避免在两个位置重复配置。这里仅展示系统自动生成的正式输出结构。</small>
                </div>
                <el-button type="primary" plain size="small" @click="selectedInspectorTab = 'config'">
                  返回整理数据
                </el-button>
              </section>
              <WorkflowInputMappingEditor
                v-else
                :model-value="selectedNode.inputMapping || {}"
                :json-value="selectedNodeInputMapping"
                :definition="definition"
                :selected-node="selectedNode"
                :descriptors="descriptors"
                :resolved-node-schemas="resolvedNodeSchemas"
                :disabled="!canEdit"
                :mapping-error="mappingError"
                @update:model-value="visualInputMappingChanged"
                @json-change="rawInputMappingChanged"
                @request-node-test="fetchNodeFields"
              />
              <section class="formal-schema-card">
                <div class="formal-schema-heading">
                  <div>
                    <strong>正式输出结构</strong>
                    <small>发布后进入执行计划，并用于节点输出校验</small>
                  </div>
                  <el-tag :type="isTransformNode || selectedNode.outputSchemaOverride ? 'success' : 'info'" size="small">
                    {{ isTransformNode ? '转换规则自动生成' : (selectedNode.outputSchemaOverride ? '用户正式覆盖' : '沿用节点契约') }}
                  </el-tag>
                </div>
                <template v-if="isTransformNode">
                  <pre>{{ formatNodeTestJson(resolvedNodeSchemas[selectedNode.id]?.outputSchema || {}) }}</pre>
                  <div class="formal-schema-actions">
                    <small>修改转换规则后自动更新，无需手写或再次确认。</small>
                  </div>
                </template>
                <template v-else-if="selectedNode.outputSchemaOverride">
                  <pre>{{ formatNodeTestJson(selectedNode.outputSchemaOverride) }}</pre>
                  <div class="formal-schema-actions">
                    <small>
                      确认于 {{ selectedNode.ui?.outputSchemaOverrideMetadata?.confirmedAt || '当前草稿' }}；
                      发布版本可用于回滚。
                    </small>
                    <el-button v-if="canEdit" type="danger" plain size="small" @click="clearFormalOutputSchema">
                      恢复节点契约
                    </el-button>
                  </div>
                </template>
                <template v-else-if="selectedNode.ui?.inferredOutputSchema?.schema">
                  <p>已有试运行样本结构；确认后才会成为正式约束。</p>
                  <el-button
                    v-if="canEdit"
                    type="primary"
                    plain
                    size="small"
                    :disabled="selectedNode.ui.inferredOutputSchema.stale"
                    @click="promoteSelectedInferredSchema"
                  >设为正式输出结构</el-button>
                </template>
                <p v-else>当前没有用户覆盖，运行时沿用发布时固化的节点或资源契约。</p>
              </section>
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
          <div class="inspector-footer">
            <el-tooltip
              v-if="canDebug"
              :content="nodeTestAvailability.reason"
              :disabled="nodeTestAvailability.available"
              placement="top"
            >
              <span>
                <el-button
                  type="primary"
                  plain
                  class="node-test-trigger"
                  :disabled="!nodeTestAvailability.available"
                  @click="openNodeTest"
                ><el-icon><VideoPlay /></el-icon><span>{{ nodeTestRunning ? '查看试运行' : '试运行当前节点' }}</span></el-button>
              </span>
            </el-tooltip>
            <el-button v-if="canEdit" class="node-delete-button" type="danger" text @click="removeSelectedNode">
              <el-icon><Delete /></el-icon><span>删除节点</span>
            </el-button>
          </div>
        </template>

        <template v-else-if="selectedEdge">
          <div class="inspector-heading simple">
            <div><strong>连线设置</strong><small>{{ selectedEdge.source }} → {{ selectedEdge.target }}</small></div>
            <el-button text circle title="收起面板" aria-label="收起面板" @click="collapseInspector"><el-icon><Close /></el-icon></el-button>
          </div>
          <el-form label-position="top" size="small" class="edge-form">
            <el-form-item label="连线类型"><el-select v-model="selectedEdge.kind" :disabled="!canEdit" style="width: 100%" @change="edgeChanged"><el-option label="普通" value="NORMAL" /><el-option label="条件" value="CONDITION" /><el-option label="并行" value="PARALLEL" /><el-option label="循环" value="LOOP" /><el-option label="语义分类" value="SEMANTIC" /></el-select></el-form-item>
            <el-form-item v-if="selectedEdge.kind === 'SEMANTIC'" label="分类分支">
              <el-input :model-value="semanticBranchLabel(selectedEdge)" disabled />
              <small class="edge-field-help">分类与下一节点请在语义分类节点中调整，内部标识由系统自动维护。</small>
            </el-form-item>
            <template v-if="selectedEdge.kind === 'CONDITION'">
              <el-form-item label="默认分支">
                <el-switch v-model="selectedEdge.default" :disabled="!canEdit" @change="edgeChanged" />
                <small class="edge-field-help">其他条件都不满足时走此分支，不需要填写表达式。</small>
              </el-form-item>
              <template v-if="!selectedEdge.default">
                <section class="condition-guide">
                  <div class="condition-guide-heading">
                    <span>
                      <strong>满足条件时走此分支</strong>
                      <small>选择上游字段，再补充判断方式和值；表达式结果必须是 true 或 false。</small>
                    </span>
                    <el-tooltip content="字符串需要加引号；数字、true、false 和 null 不加引号。" placement="top">
                      <el-icon><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                  <el-select
                    v-model="conditionFieldSelection"
                    class="condition-field-select"
                    filterable
                    clearable
                    :disabled="!canEdit || !conditionFieldGroups.length"
                    :placeholder="conditionFieldGroups.length ? '选择上游字段并插入' : '暂无可用字段，请先试运行上游节点'"
                    @change="insertConditionField"
                  >
                    <el-option-group
                      v-for="group in conditionFieldGroups"
                      :key="group.id"
                      :label="group.label"
                    >
                      <el-option
                        v-for="field in group.fields"
                        :key="field.expression"
                        :label="`${field.label} · ${field.typeLabel} · ${field.expression}`"
                        :value="field.expression"
                      >
                        <span class="condition-option-label">{{ field.label }}</span>
                        <small>{{ field.typeLabel }} · {{ field.expression }}</small>
                      </el-option>
                    </el-option-group>
                  </el-select>
                  <div class="condition-operator-bar" aria-label="快捷插入运算符">
                    <button
                      v-for="operator in conditionOperators"
                      :key="operator.value"
                      type="button"
                      :disabled="!canEdit"
                      :title="operator.title"
                      @click="insertConditionToken(operator.value)"
                    >{{ operator.label }}</button>
                  </div>
                </section>
                <el-form-item label="条件表达式" class="condition-expression-item">
                  <el-input
                    ref="conditionExpressionInput"
                    v-model="selectedEdge.condition.expression"
                    type="textarea"
                    :rows="5"
                    maxlength="2000"
                    show-word-limit
                    :disabled="!canEdit"
                    placeholder="例如：选择字段后填写  >= 10000"
                    @input="edgeChanged"
                  />
                  <div class="condition-syntax-help">
                    <span>示例</span>
                    <code>字段 == 'SUCCESS'</code>
                    <code>字段 >= 10000 && 字段 != null</code>
                  </div>
                </el-form-item>
                <el-form-item label="优先级">
                  <el-input-number v-model="selectedEdge.condition.priority" :min="0" :max="10000" :disabled="!canEdit" style="width: 100%" @change="edgeChanged" />
                  <small class="edge-field-help">数字越小越先判断；同一个条件节点的分支优先级不能重复。</small>
                </el-form-item>
              </template>
            </template>
            <el-button v-if="canEdit" type="danger" plain style="width: 100%" @click="removeSelectedEdge">删除连线</el-button>
          </el-form>
        </template>

        <template v-else>
          <div class="inspector-heading simple">
            <div><strong>工作流设置</strong><small>全局运行与预算策略</small></div>
            <el-button text circle title="收起面板" aria-label="收起面板" @click="collapseInspector"><el-icon><Close /></el-icon></el-button>
          </div>
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
      </section>

      <nav class="context-rail" aria-label="节点属性入口">
        <button
          v-for="item in inspectorTabOptions"
          :key="item.value"
          type="button"
          :class="{active: selectedNode && !inspectorCollapsed && selectedInspectorTab === item.value}"
          :disabled="!selectedNode"
          :title="selectedNode ? item.label : '请先选择节点'"
          @click="openInspectorTab(item.value)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
        <span class="context-rail-spacer"></span>
        <button
          type="button"
          :class="{active: !selectedNode && !selectedEdge && !inspectorCollapsed}"
          title="工作流设置"
          @click="openWorkflowInspector"
        >
          <el-icon><Grid /></el-icon>
          <span>工作流</span>
        </button>
      </nav>
    </div>

    <section v-if="diagnostics.length" class="diagnostic-panel">
      <div class="diagnostic-header">
        <div>
          <strong>{{ diagnosticHasErrors ? '校验未通过' : '校验通过，但有建议' }}</strong>
          <span>
            {{ diagnosticHasErrors
              ? `发现 ${diagnostics.length} 个问题，请按提示修改后重新校验`
              : `发现 ${diagnostics.length} 个建议，可按需优化` }}
          </span>
        </div>
        <el-button link @click="diagnostics = []">关闭</el-button>
      </div>
      <div class="diagnostic-list">
        <article
          v-for="(item, index) in displayDiagnostics"
          :key="`${item.code}-${index}`"
          :class="['diagnostic-item', `is-${item.severity.toLowerCase()}`]"
        >
          <div class="diagnostic-status" aria-hidden="true">
            <el-icon><CircleClose v-if="item.severity === 'ERROR'" /><QuestionFilled v-else /></el-icon>
          </div>
          <div class="diagnostic-content">
            <div class="diagnostic-title-row">
              <strong>{{ item.title }}</strong>
              <el-tag :type="item.severity === 'ERROR' ? 'danger' : 'warning'" size="small">
                {{ item.severity === 'ERROR' ? '必须修改' : '建议检查' }}
              </el-tag>
            </div>
            <div class="diagnostic-location">问题位置：{{ item.location }}</div>
            <p>{{ item.message }}</p>
            <div class="diagnostic-suggestion"><strong>修改方法：</strong>{{ item.suggestion }}</div>
            <code :title="item.fieldPath">错误代码：{{ item.code }}</code>
          </div>
          <el-button v-if="item.nodeId" type="primary" link @click="focusDiagnostic(item)">定位节点</el-button>
        </article>
      </div>
    </section>

    <div v-if="debugExecution" class="execution-dock">
      <div class="execution-state-icon" :class="`is-${executionStateTone}`">
        <el-icon v-if="!executionFinished" class="is-loading"><Loading /></el-icon>
        <el-icon v-else-if="debugExecution.status === 'SUCCEEDED'"><CircleCheck /></el-icon>
        <el-icon v-else><CircleClose /></el-icon>
      </div>
      <div class="execution-progress"><strong>{{ executionStatusLabel }}</strong><el-progress :percentage="executionProgress" :show-text="false" /></div>
      <span>{{ completedNodeRuns }}/{{ Math.max(1, definition.nodes.length) }} 节点</span>
      <code class="execution-id" :title="debugExecution.executionId">{{ debugExecution.executionId }}</code>
      <div class="execution-actions">
        <el-button class="execution-action" text @click="pollExecution">
          <el-icon><Refresh /></el-icon><span>刷新</span>
        </el-button>
        <el-button
          v-if="!executionFinished"
          class="execution-action execution-action--cancel"
          text
          :loading="executionCancelling"
          @click="cancelExecution"
        >取消运行</el-button>
        <el-button
          class="execution-close"
          text
          aria-label="关闭运行状态"
          title="关闭运行状态"
          @click="closeExecutionDock"
        ><el-icon><Close /></el-icon></el-button>
      </div>
    </div>

    <el-dialog
      v-model="testDialogOpen"
      class="workflow-dialog"
      title="测试运行"
      width="720px"
      destroy-on-close
    >
      <el-alert v-if="!currentDefinition?.currentPublishedVersionId" title="请先发布一个版本后再运行测试。" type="warning" :closable="false" />
      <el-alert
        v-if="testInputSchemaError"
        :title="testInputSchemaError"
        type="warning"
        :closable="false"
        show-icon
      />
      <WorkflowExecutionInput
        ref="testInputEditor"
        v-model="testInput"
        :schema="testInputSchema"
        :loading="testInputSchemaLoading"
        @validation="testInputValidation = $event"
      />
      <template #footer>
        <el-button @click="testDialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="testStarting"
          :disabled="!currentDefinition?.currentPublishedVersionId || testInputSchemaLoading || !testInputValidation.valid"
          @click="startTestRun"
        >开始运行</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="nodeTestDialogOpen"
      class="workflow-dialog"
      :title="workflowDraftTest
        ? `试运行工作流 · ${definition.metadata.name || '未命名工作流'}`
        : `试运行节点 · ${nodeTestNodeName}`"
      width="720px"
      destroy-on-close
    >
      <el-alert
        :title="workflowDraftTest
          ? '直接运行当前草稿，不创建发布版本或正式运行记录；仅支持无写操作的线性安全流程。'
          : nodeTestMode === 'UPSTREAM_CHAIN'
          ? '按普通连线依次执行安全的线性上游链；条件、并行、循环、多入口和写节点会被阻止。输入表示流程输入。'
          : '仅执行当前节点，不执行上游；输入表示当前节点最终输入。写操作节点不会执行。'"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-width="100px" class="node-test-form">
        <el-form-item v-if="!workflowDraftTest" label="执行范围">
          <el-radio-group v-model="nodeTestMode" class="node-test-mode">
            <el-radio-button value="NODE">仅当前节点</el-radio-button>
            <el-tooltip
              :content="nodeTestChainAvailability.reason"
              :disabled="nodeTestChainAvailability.available"
              placement="top"
            >
              <span>
                <el-radio-button
                  value="UPSTREAM_CHAIN"
                  :disabled="!nodeTestChainAvailability.available"
                >线性上游链</el-radio-button>
              </span>
            </el-tooltip>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="nodeTestTimeoutSeconds" :min="1" :max="120" />
          <span class="node-test-unit">秒</span>
        </el-form-item>
        <el-form-item :label="nodeTestIsClassifier && nodeTestMode === 'NODE' ? '待分类内容' : workflowDraftTest || nodeTestMode === 'UPSTREAM_CHAIN' ? '流程输入' : '节点输入'">
          <el-input
            v-if="nodeTestIsClassifier && nodeTestMode === 'NODE'"
            v-model="classifierTestContent"
            type="textarea"
            :rows="7"
            maxlength="12000"
            show-word-limit
            placeholder="输入一句用户问题、一段文本，或粘贴需要分类的 JSON 内容"
          />
          <el-input v-else v-model="nodeTestInputJson" type="textarea" :rows="10" spellcheck="false" />
        </el-form-item>
      </el-form>
      <section v-if="nodeTestResult" class="node-test-result">
        <header>
          <strong>运行结果</strong>
          <el-tag :type="nodeTestStatusType(nodeTestResult.status)">
            {{ nodeTestStatusLabel(nodeTestResult.status) }}
          </el-tag>
          <span>{{ nodeTestResult.durationMs }} ms</span>
          <code>{{ nodeTestResult.testRunId }}</code>
        </header>
        <el-alert
          v-if="nodeTestResult.errorMessage"
          :title="nodeTestResult.errorMessage"
          type="error"
          :closable="false"
          show-icon
        />
        <div class="node-test-meta">
          <span>范围 <strong>{{ workflowDraftTest
            ? '当前草稿'
            : nodeTestResult.mode === 'UPSTREAM_CHAIN' ? '线性上游链' : '仅当前节点' }}</strong></span>
          <span>副作用 <strong>{{ nodeTestResult.sideEffect }}</strong></span>
          <span>Schema <strong>{{ nodeTestSchemaLabel(nodeTestResult.schemaSource) }}</strong></span>
          <span v-if="nodeTestResult.schemaSourceVersion">版本 <code>{{ nodeTestResult.schemaSourceVersion }}</code></span>
          <span v-if="nodeTestResult.errorCode">
            错误类型 <strong>{{ nodeTestErrorCodeLabel(nodeTestResult.errorCode) }}</strong>
            <code>{{ nodeTestResult.errorCode }}</code>
          </span>
        </div>
        <div v-if="nodeTestResult.schemaDiagnostics?.length" class="node-test-diagnostics">
          <span v-for="item in nodeTestResult.schemaDiagnostics" :key="item">{{ item }}</span>
        </div>
        <div v-if="nodeTestResult.status === 'SUCCEEDED'" class="node-test-schema-action">
          <div>
            <strong>样本字段结构</strong>
            <small>仅补充编辑器字段提示，不会改变正式 Schema 或运行校验。</small>
          </div>
          <el-button
            v-if="canEdit"
            type="primary"
            plain
            :loading="nodeTestGeneratingSchema"
            :disabled="nodeTestSchemaApplied"
            @click="generateNodeTestSchema"
          >{{ nodeTestSchemaApplied ? '已加入草稿' : '生成字段结构' }}</el-button>
          <el-button
            v-if="canEdit && nodeTestInferredSchema"
            type="success"
            plain
            :disabled="nodeTestSchemaPromoted"
            @click="promoteNodeTestSchema"
          >{{ nodeTestSchemaPromoted ? '已设为正式结构' : '设为正式结构' }}</el-button>
        </div>
        <label class="node-test-output">
          <span>脱敏输出</span>
          <pre>{{ formatNodeTestJson(nodeTestResult.output) }}</pre>
        </label>
      </section>
      <template #footer>
        <el-button @click="nodeTestDialogOpen = false">关闭</el-button>
        <el-button
          v-if="nodeTestRunning"
          type="danger"
          plain
          @click="cancelNodeTest"
        >取消运行</el-button>
        <el-button v-else type="primary" @click="runNodeTest">
          {{ workflowDraftTest ? '运行当前草稿' : '运行当前节点' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {VueFlow} from '@vue-flow/core'
import {Background} from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import {
  cancelWorkflowExecution,
  cancelWorkflowNodeTest,
  createWorkflowDraft,
  getWorkflowExecution,
  getWorkflowNodeTest,
  getWorkflowVersion,
  inferWorkflowNodeTestSchema,
  listWorkflowNodeRuns,
  listWorkflowResourceBindings,
  listWorkflowResources,
  publishWorkflowDraft,
  resolveWorkflowNodeSchemas,
  saveWorkflowResourceBinding,
  startWorkflowExecution,
  testWorkflowNode,
  updateWorkflowDraft,
  validateWorkflowDraft
} from '@/api/ai/workflow'
import {
  Aim,
  Back,
  ChatDotRound,
  CircleCheck,
  CircleClose,
  Close,
  Coin,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Delete,
  Finished,
  Grid,
  Loading,
  MagicStick,
  Operation,
  Plus,
  QuestionFilled,
  Refresh,
  RefreshLeft,
  RefreshRight,
  Setting,
  Share,
  Switch,
  Timer,
  User,
  VideoPlay,
  ZoomIn,
  ZoomOut
} from '@element-plus/icons-vue'
import WorkflowSchemaConfig from './WorkflowSchemaConfig.vue'
import WorkflowCanvasNode from './WorkflowCanvasNode.vue'
import WorkflowApiConnectionStep from './WorkflowApiConnectionStep.vue'
import WorkflowDatasourceConnectionStep from './WorkflowDatasourceConnectionStep.vue'
import WorkflowDatabaseQueryStep from './WorkflowDatabaseQueryStep.vue'
import WorkflowDisclosureCard from './WorkflowDisclosureCard.vue'
import WorkflowInputMappingEditor from './WorkflowInputMappingEditor.vue'
import WorkflowExecutionInput from './WorkflowExecutionInput.vue'
import WorkflowSemanticClassifierEditor from './WorkflowSemanticClassifierEditor.vue'
import WorkflowTransformEditor from './WorkflowTransformEditor.vue'
import {
  classifierSchemaOptions as buildClassifierSchemaOptions,
  classifierTargetOptions as buildClassifierTargetOptions,
  isClassifierTargetAllowed
} from './workflowClassifier'

export default {
  name: 'WorkflowWorkbench',
  components: {
    Aim,
    Back,
    VueFlow,
    Background,
    WorkflowSchemaConfig,
    WorkflowCanvasNode,
    WorkflowApiConnectionStep,
    WorkflowDatasourceConnectionStep,
    WorkflowDatabaseQueryStep,
    WorkflowDisclosureCard,
    WorkflowInputMappingEditor,
    WorkflowExecutionInput,
    WorkflowSemanticClassifierEditor,
    WorkflowTransformEditor,
    ChatDotRound,
    CircleCheck,
    CircleClose,
    Close,
    Coin,
    Collection,
    Connection,
    Cpu,
    DataAnalysis,
    Delete,
    Finished,
    Grid,
    Loading,
    MagicStick,
    Operation,
    Plus,
    QuestionFilled,
    Refresh,
    RefreshLeft,
    RefreshRight,
    Setting,
    Share,
    Switch,
    Timer,
    User,
    VideoPlay,
    ZoomIn,
    ZoomOut
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
    canDebug: {
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
      conditionFieldSelection: '',
      conditionOperators: [
        {label: '等于 ==', value: ' == ', title: '等于'},
        {label: '不等于 !=', value: ' != ', title: '不等于'},
        {label: '大于 >', value: ' > ', title: '大于'},
        {label: '大于等于 >=', value: ' >= ', title: '大于等于'},
        {label: '小于 <', value: ' < ', title: '小于'},
        {label: '小于等于 <=', value: ' <= ', title: '小于等于'},
        {label: '并且 &&', value: ' && ', title: '并且'},
        {label: '或者 ||', value: ' || ', title: '或者'},
        {label: '非 !', value: '!', title: '取反'},
        {label: '( )', value: '()', title: '括号'}
      ],
      selectedInspectorTab: 'config',
      inspectorCollapsed: true,
      inspectorHeight: 430,
      inspectorResizeState: null,
      inspectorTabOptions: [
        {label: '配置', value: 'config', icon: 'Setting'},
        {label: '资源', value: 'resource', icon: 'Connection'},
        {label: '输入输出', value: 'mapping', icon: 'Operation'},
        {label: '运行策略', value: 'policy', icon: 'Timer'}
      ],
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
      backendResolvedNodeSchemas: {},
      catalogError: '',
      resourceBindings: [],
      pendingResourceBindings: [],
      testDialogOpen: false,
      testStarting: false,
      testInput: {},
      testInputSchema: {type: 'object', properties: {}},
      testInputSchemaLoading: false,
      testInputSchemaError: '',
      testInputValidation: {valid: false, message: '', errors: []},
      nodeTestDialogOpen: false,
      nodeTestRunning: false,
      nodeTestNodeId: '',
      nodeTestNodeName: '',
      nodeTestMode: 'NODE',
      workflowDraftTest: false,
      nodeTestTimeoutSeconds: 60,
      nodeTestInputJson: '{}',
      classifierTestContent: '',
      nodeTestResult: null,
      nodeTestPollTimer: null,
      nodeTestGeneratingSchema: false,
      nodeTestSchemaApplied: false,
      nodeTestSchemaPromoted: false,
      nodeTestInferredSchema: null,
      nodeTestAutoPersistSchema: false,
      nodeDragOrigin: null,
      debugExecution: null,
      debugNodeRuns: [],
      executionPollTimer: null,
      executionCancelling: false,
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
    diagnosticHasErrors() {
      return this.diagnostics.some(item => item.severity === 'ERROR')
    },
    displayDiagnostics() {
      return this.diagnostics.map(item => this.presentDiagnostic(item))
    },
    isApiNode() {
      return ['http_get', 'http_request'].includes(this.selectedNode?.type)
    },
    isDatabaseNode() {
      return this.selectedNode?.type === 'database_query'
    },
    isGuidedIntegrationNode() {
      return this.isApiNode || this.isDatabaseNode
    },
    isLlmNode() {
      return this.selectedNode?.type === 'llm'
    },
    isClassifierNode() {
      return this.selectedNode?.type === 'llm_classifier'
    },
    isTransformNode() {
      return this.selectedNode?.type === 'transform'
    },
    classifierSourceGroups() {
      return this.isClassifierNode && this.selectedNode
        ? this.buildClassifierSourceGroups(this.selectedNode.id) : []
    },
    classifierTargetOptions() {
      if (!this.isClassifierNode || !this.selectedNode) return []
      return buildClassifierTargetOptions(this.definition, this.selectedNode.id)
    },
    classifierBranchTargets() {
      if (!this.isClassifierNode || !this.selectedNode) return {}
      return (this.definition.edges || [])
        .filter(edge => edge.source === this.selectedNode.id && edge.kind === 'SEMANTIC')
        .reduce((result, edge) => ({...result, [edge.sourcePort]: edge.target}), {})
    },
    nodeTestIsClassifier() {
      return (this.definition.nodes || []).find(node => node.id === this.nodeTestNodeId)?.type === 'llm_classifier'
    },
    transformDownstreamImpacts() {
      if (!this.isTransformNode || !this.selectedNode) return []
      return this.outputSchemaImpacts(
        this.selectedNode.id,
        this.transformEditorOutputSchema(this.selectedNode)
      )
    },
    nodePromptValue() {
      return this.isLlmNode ? String(this.selectedNode?.config?.prompt || '') : ''
    },
    structuredOutputConfigured() {
      const schema = this.selectedNode?.config?.structuredOutputSchema
      return !!schema && typeof schema === 'object' && !Array.isArray(schema)
    },
    isMappingEditorOpen() {
      return !!this.selectedNode && this.selectedInspectorTab === 'mapping'
    },
    inspectorDockStyle() {
      return {height: this.inspectorCollapsed ? '44px' : `${this.inspectorHeight}px`}
    },
    inspectorSummary() {
      if (this.selectedNode) return `${this.selectedNode.name} · ${this.inspectorTabLabel(this.selectedInspectorTab)}`
      if (this.selectedEdge) return '连线设置'
      return '工作流设置'
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
    nodeTestAvailability() {
      if (this.nodeTestRunning) return {available: true, reason: ''}
      if (!this.selectedNode) return {available: false, reason: '请先选择节点'}
      if ((!this.currentDefinition?.id || this.dirty) && !this.canEdit) {
        return {available: false, reason: '当前修改尚未保存，暂无权限同步草稿'}
      }
      if (!this.selectedDescriptor) return {available: false, reason: '当前节点处理器不可用'}
      if (this.selectedDescriptor.sideEffect === 'WRITE') {
        return {available: false, reason: '写操作节点暂不允许单节点真实试运行'}
      }
      if (['approval', 'wait', 'sub_workflow'].includes(this.selectedNode.type)) {
        return {available: false, reason: '持久化控制节点暂不支持隔离试运行'}
      }
      const capabilities = this.selectedDescriptor.capabilities || []
      if (this.selectedDescriptor.sideEffect === 'NONE' && !capabilities.includes('MOCKABLE')) {
        return {available: false, reason: '该节点未声明可安全模拟能力'}
      }
      if (!capabilities.includes('CANCELLABLE')) {
        return {available: false, reason: '该节点未声明可取消能力'}
      }
      return {available: true, reason: ''}
    },
    nodeTestChainAvailability() {
      return this.testChainAvailability(this.selectedNode)
    },
    workflowDraftTestAvailability() {
      if ((!this.currentDefinition?.id || this.dirty) && !this.canEdit) {
        return {available: false, reason: '当前修改尚未保存，暂无权限同步草稿'}
      }
      const endEdges = (this.definition.edges || [])
        .filter(edge => edge.target === '__end__')
      if (endEdges.length !== 1 || endEdges[0].kind !== 'NORMAL') {
        return {available: false, reason: '当前仅支持只有一个普通结束出口的线性流程直接试运行'}
      }
      const target = (this.definition.nodes || [])
        .find(node => node.id === endEdges[0].source)
      if (!target) return {available: false, reason: '结束节点前没有可运行节点'}
      const chain = this.testChainAvailability(target)
      if (!chain.available) return chain
      if (chain.nodeIds.length !== (this.definition.nodes || []).length) {
        return {available: false, reason: '当前流程包含分支或不在线性主路径上的节点'}
      }
      return {available: true, reason: '', target}
    },
    resolvedNodeSchemas() {
      const result = (this.definition.nodes || []).reduce((schemas, node) => {
        if (node.type === 'transform') {
          const descriptor = this.descriptors.find(item => item.type === node.type
            && item.handlerVersion === node.typeVersion)
          schemas[node.id] = {
            inputSchema: descriptor?.inputSchema || {type: 'object', additionalProperties: true},
            outputSchema: this.transformEditorOutputSchema(node),
            source: '转换规则',
            sourceCode: 'TRANSFORM_RULES',
            diagnostics: this.transformEditorDiagnostics(node)
          }
          return schemas
        }
        if (!['http_get', 'http_request'].includes(node.type)) return schemas
        const reference = (node.resourceRefs || [])
          .find(item => item.kind === 'API_CONNECTOR')
        const resource = reference ? this.selectedResource(reference) : null
        const responseSchema = resource?.attributes?.responseSchema
        if (!responseSchema || Array.isArray(responseSchema)
          || typeof responseSchema !== 'object') return schemas
        const hasPendingBinding = this.pendingResourceBindings.some(binding =>
          binding.nodeId === node.id
            && binding.resourceKind === 'API_CONNECTOR'
            && binding.environment === this.resourceEnvironment)
        if (schemas[node.id] && !hasPendingBinding) return schemas
        const descriptor = this.descriptors.find(item => item.type === node.type
          && item.handlerVersion === node.typeVersion)
        const outputSchema = JSON.parse(JSON.stringify(descriptor?.outputSchema || {
          type: 'object',
          properties: {}
        }))
        outputSchema.type = 'object'
        outputSchema.properties = {
          ...(outputSchema.properties || {}),
          body: responseSchema
        }
        schemas[node.id] = {
          outputSchema,
          source: '连接器 Schema',
          sourceCode: resource.attributes.responseSchemaSource || 'API_CONNECTOR',
          sourceVersion: resource.attributes.responseSchemaVersion || resource.resourceId
        }
        return schemas
      }, {...this.backendResolvedNodeSchemas})
      const nodes = this.definition.nodes || []
      nodes.forEach(node => {
        if (!node.outputSchemaOverride) return
        const descriptor = this.descriptors.find(item => item.type === node.type
          && item.handlerVersion === node.typeVersion)
        const current = result[node.id] || {
          inputSchema: descriptor?.inputSchema || {type: 'object', properties: {}},
          outputSchema: descriptor?.outputSchema || {type: 'object', properties: {}},
          diagnostics: []
        }
        result[node.id] = {
          ...current,
          outputSchema: node.outputSchemaOverride,
          source: '用户正式覆盖',
          sourceCode: 'USER_OVERRIDE',
          diagnostics: current.diagnostics || []
        }
      })
      const latestRuntimeOutputs = (this.debugNodeRuns || [])
        .filter(run => run.status === 'SUCCEEDED' && run.outputJson)
        .sort((left, right) => (right.attemptNo || 0) - (left.attemptNo || 0))
        .reduce((outputs, run) => {
          if (!outputs.has(run.nodeId)) outputs.set(run.nodeId, run.outputJson)
          return outputs
        }, new Map())
      nodes.forEach(node => {
        const outputJson = latestRuntimeOutputs.get(node.id)
        if (!outputJson) return
        try {
          const output = typeof outputJson === 'string' ? JSON.parse(outputJson) : outputJson
          const sampleSchema = this.inferEditorSchema(output)
          const descriptor = this.descriptors.find(item => item.type === node.type
            && item.handlerVersion === node.typeVersion)
          const current = result[node.id] || {
            outputSchema: descriptor?.outputSchema || {type: 'object', properties: {}},
            source: '节点契约',
            sourceCode: 'NODE_CONTRACT',
            diagnostics: []
          }
          result[node.id] = {
            ...current,
            outputSchema: this.mergeEditorSchema(current.outputSchema, sampleSchema),
            source: `${current.source || '节点契约'} + 本次运行`,
            runtimeSample: true,
            diagnostics: current.diagnostics || []
          }
        } catch (error) {
          // 运行输出不可解析时继续使用正式或动态 Schema。
        }
      })
      nodes.forEach(node => {
        const inferred = node.ui?.inferredOutputSchema
        if (!inferred?.schema) return
        const descriptor = this.descriptors.find(item => item.type === node.type
          && item.handlerVersion === node.typeVersion)
        const current = result[node.id] || {
          outputSchema: descriptor?.outputSchema || {type: 'object', properties: {}},
          source: '节点契约',
          sourceCode: 'NODE_CONTRACT',
          diagnostics: []
        }
        if (!this.isUsableEditorSchema(inferred.schema)) {
          result[node.id] = {
            ...current,
            diagnostics: [
              ...(current.diagnostics || []),
              '旧版试运行字段结构无效，请重新运行上游节点获取字段'
            ]
          }
          return
        }
        const sourceVersionChanged = inferred.schemaSourceVersion
          && current.sourceVersion
          && inferred.schemaSourceVersion !== current.sourceVersion
        if (inferred.stale || sourceVersionChanged) {
          result[node.id] = {
            ...current,
            diagnostics: [
              ...(current.diagnostics || []),
              sourceVersionChanged
                ? '资源契约已变化，试运行样本已过期，请重新运行节点'
                : '试运行样本已过期，请重新运行节点'
            ]
          }
          return
        }
        const sampleCount = inferred.sampleCount || 1
        const sampleLabel = sampleCount > 1
          ? `试运行样本（${sampleCount} 次）` : '试运行样本'
        result[node.id] = {
          ...current,
          outputSchema: this.mergeEditorSchema(current.outputSchema, inferred.schema),
          source: `${current.source || '节点契约'} + ${sampleLabel}`,
          sourceCode: current.sourceCode || 'NODE_TEST',
          sampleSource: 'NODE_TEST',
          sampleCount,
          diagnostics: [...(current.diagnostics || []), ...(inferred.diagnostics || [])]
        }
      })
      return result
    },
    conditionFieldGroups() {
      if (this.selectedEdge?.kind !== 'CONDITION' || this.selectedEdge.default) return []
      return this.buildConditionFieldGroups(this.selectedEdge.source)
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
    nodeConfigFormSchema() {
      if (!this.isLlmNode) return this.nodeConfigSchema
      const schema = this.nodeConfigSchema || {}
      const properties = {...(schema.properties || {})}
      delete properties.prompt
      delete properties.systemPrompt
      delete properties.structuredOutputSchema
      return {
        ...schema,
        properties,
        required: Array.isArray(schema.required)
          ? schema.required.filter(name => !['prompt', 'systemPrompt'].includes(name))
          : schema.required
      }
    },
    llmStructuredOutputSchema() {
      const declared = this.nodeConfigSchema?.properties?.structuredOutputSchema || {}
      return {
        type: 'object',
        properties: {
          structuredOutputSchema: {
            ...declared,
            type: 'object',
            title: 'JSON 字段定义',
            format: 'json-schema',
            description: '模型返回结果必须符合此结构，下游节点才能稳定读取对应字段',
            placeholder: '{\n  "type": "object",\n  "properties": {\n    "summary": { "type": "string" }\n  },\n  "required": ["summary"]\n}',
            additionalProperties: true
          }
        },
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
    executionStateTone() {
      const status = this.debugExecution?.status
      if (status === 'SUCCEEDED') return 'success'
      if (['FAILED', 'REJECTED', 'NEEDS_ATTENTION'].includes(status)) return 'danger'
      if (status === 'CANCELLED') return 'neutral'
      if (['WAITING_APPROVAL', 'WAITING_EVENT'].includes(status)) return 'warning'
      return 'running'
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
    clearTimeout(this.nodeTestPollTimer)
    window.removeEventListener('pointermove', this.resizeInspector)
    window.removeEventListener('pointerup', this.stopInspectorResize)
  },
  methods: {
    presentDiagnostic(item) {
      const node = item.nodeId
        ? this.definition.nodes.find(value => value.id === item.nodeId)
        : null
      const presentations = {
        CONDITION_BRANCH_INVALID: {
          title: '条件分支设置不完整',
          suggestion: '从该条件节点至少连接 2 条条件分支，并将其中 1 条设为“默认分支”。只能有 1 条默认分支。'
        },
        CONDITION_PRIORITY_DUPLICATE: {
          title: '条件分支优先级重复',
          suggestion: '打开该节点的各条非默认连线，为每条分支设置不同的优先级，例如 10、20、30。'
        },
        EXPRESSION_INVALID: {
          title: '条件表达式无法识别',
          suggestion: '定位节点后检查条件连线，补全或修正表达式，再重新校验。'
        },
        NODE_UNREACHABLE: {
          title: '节点不会被执行',
          suggestion: '补充一条从开始节点或其他可执行节点到该节点的连线。'
        },
        NODE_CANNOT_REACH_END: {
          title: '节点无法走到结束',
          suggestion: '从该节点补充后续连线，确保最终能够连接到结束节点。'
        },
        PUBLISH_CONTENT_UNCHANGED: {
          title: '当前内容已经发布',
          suggestion: '无需重复发布；如需生成新版本，请先修改并保存草稿。'
        }
      }
      const presentation = presentations[item.code] || {}
      return {
        ...item,
        severity: item.severity || 'ERROR',
        title: presentation.title || (item.severity === 'WARNING' ? '请检查此项配置' : '此项配置需要修改'),
        location: node ? `节点“${node.name || node.id}”（${node.id}）` : '工作流整体设置',
        suggestion: presentation.suggestion
          || (item.nodeId ? '点击“定位节点”检查对应配置，修改后重新校验。' : '检查工作流设置，修改后重新校验。')
      }
    },
    inspectorTabLabel(value) {
      return this.inspectorTabOptions.find(item => item.value === value)?.label || '配置'
    },
    openInspectorTab(value) {
      if (!this.selectedNode) return
      this.selectedInspectorTab = value
      this.inspectorCollapsed = false
      this.refreshCanvasLayout()
    },
    openWorkflowInspector() {
      this.selectedNode = null
      this.selectedEdge = null
      this.inspectorCollapsed = false
      this.refreshCanvasLayout()
    },
    collapseInspector() {
      this.inspectorCollapsed = true
      this.refreshCanvasLayout()
    },
    expandInspector() {
      this.inspectorCollapsed = false
      this.refreshCanvasLayout()
    },
    refreshCanvasLayout() {
      this.$nextTick(() => {
        window.dispatchEvent(new Event('resize'))
      })
    },
    startInspectorResize(event) {
      if (this.inspectorCollapsed) return
      event.preventDefault()
      this.inspectorResizeState = {
        startY: event.clientY,
        startHeight: this.inspectorHeight
      }
      window.addEventListener('pointermove', this.resizeInspector)
      window.addEventListener('pointerup', this.stopInspectorResize, {once: true})
    },
    resizeInspector(event) {
      if (!this.inspectorResizeState) return
      const stageHeight = this.$el?.querySelector('.workspace-stage')?.clientHeight
        || window.innerHeight * 0.7
      const maxHeight = Math.max(360, Math.min(720, stageHeight - 140))
      const nextHeight = this.inspectorResizeState.startHeight
        + this.inspectorResizeState.startY - event.clientY
      this.inspectorHeight = Math.round(Math.min(maxHeight, Math.max(300, nextHeight)))
    },
    stopInspectorResize() {
      this.inspectorResizeState = null
      window.removeEventListener('pointermove', this.resizeInspector)
      window.removeEventListener('pointerup', this.stopInspectorResize)
      this.refreshCanvasLayout()
    },
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
        ui: {},
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
      this.normalizeClassifierNodeConfigs()
      this.normalizeInferredOutputSchemas()
      this.buildCanvas()
      this.dirty = false
      this.resetHistory()
    },
    canvasNodeData(node) {
      const descriptor = this.descriptors.find(item =>
        item.type === node.type && item.handlerVersion === node.typeVersion)
      const reference = Array.isArray(node.resourceRefs) ? node.resourceRefs[0] : null
      const classifierBranches = node.type === 'llm_classifier'
        ? (Array.isArray(node.config?.branches) ? node.config.branches : []).map((branch, index) => {
            const edge = (this.definition.edges || []).find(item => item.source === node.id
              && item.kind === 'SEMANTIC' && item.sourcePort === branch.slug)
            const target = edge?.target === '__end__' ? {name: '结束流程'}
              : (this.definition.nodes || []).find(item => item.id === edge?.target)
            return {
              slug: branch.slug,
              label: branch.label || branch.slug || `分类 ${index + 1}`,
              targetName: target?.name || '未连接',
              connected: !!edge
            }
          }) : []
      return {
        label: node.name,
        type: node.type,
        category: descriptor?.category || 'general',
        inputSummary: Object.keys(node.inputMapping || {}).length
          ? `${Object.keys(node.inputMapping).length} 个映射` : '对象',
        outputSummary: node.type === 'knowledge_rag' ? '知识片段' : '结果对象',
        resourceName: reference ? this.selectedResource(reference)?.name : '',
        status: this.latestNodeStatus(node.id),
        testable: this.canDebug && this.nodeSupportsTest(node),
        classifierBranches
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
      const canvasUi = this.definition.ui || {}
      const start = {
        id: '__start__',
        type: 'workflow',
        position: canvasUi.startPosition || { x: 60, y: 220 },
        data: {
          label: '开始',
          start: true,
          movable: true,
          testable: this.canDebug && this.workflowDraftTestAvailability.available,
          testLabel: '试运行工作流',
          testTitle: '直接试运行当前草稿'
        },
        draggable: this.canEdit,
        deletable: false
      }
      const end = {
        id: '__end__',
        type: 'workflow',
        position: canvasUi.endPosition || {
          x: positionedNodes.length > 4 ? furthestPosition.x : furthestPosition.x + 260,
          y: positionedNodes.length > 4 ? furthestPosition.y + 210 : 220
        },
        data: {label: '结束', end: true, movable: true},
        draggable: this.canEdit,
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
        sourceHandle: edge.kind === 'SEMANTIC' ? edge.sourcePort : undefined,
        target: edge.target,
        label: edge.kind === 'CONDITION'
          ? (edge.default ? '默认' : edge.condition?.expression || '条件')
          : edge.kind === 'SEMANTIC' ? this.semanticBranchLabel(edge) : '',
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
        if (node.type !== 'llm_classifier') this.addDefinitionEdge(node.id, '__end__', 'NORMAL')
      }
      this.selectedNode = node
      this.selectedEdge = null
      this.selectedNodeConfig = JSON.stringify(node.config, null, 2)
      this.selectedNodeInputMapping = JSON.stringify(node.inputMapping, null, 2)
      this.selectedInspectorTab = resourceReferences.length ? 'resource' : 'config'
      this.inspectorCollapsed = false
      this.markDirty()
      this.refreshCanvasLayout()
    },
    defaultNodeConfig(type) {
      if (type === 'http_get') return {method: 'GET', path: ''}
      if (type === 'http_request') return {method: 'POST', path: ''}
      if (type === 'transform') {
        return {
          version: 1,
          mode: 'OBJECT_MAP',
          rules: [],
          preserveUnmapped: false,
          maxItems: 1000
        }
      }
      if (type === 'llm_classifier') {
        return {
          version: 2,
          branches: [
            {slug: 'branch_1', label: '分类 1', description: '', examples: []},
            {slug: 'other', label: '其他', description: '不符合其他分类时使用', examples: []}
          ],
          minConfidence: 0.6,
          fallbackSlug: 'other',
          invalidResponseStrategy: 'FALLBACK',
          instruction: '',
          maxWaitSeconds: 300
        }
      }
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
      const sourceNode = this.definition.nodes.find(node => node.id === params.source)
      const kind = sourceNode?.type === 'condition' ? 'CONDITION'
        : sourceNode?.type === 'parallel' ? 'PARALLEL'
          : sourceNode?.type === 'llm_classifier' ? 'SEMANTIC' : 'NORMAL'
      const sourcePort = kind === 'SEMANTIC' ? String(params.sourceHandle || '') : ''
      if (kind === 'SEMANTIC') {
        const valid = (sourceNode?.config?.branches || []).some(branch => branch.slug === sourcePort)
        if (!valid) return
        if (!isClassifierTargetAllowed(this.definition, sourceNode.id, params.target)) {
          this.$message.warning('分类分支只能连接下游节点，不能连接自身、开始节点或任意上游节点')
          return
        }
        this.classifierBranchTargetChanged({slug: sourcePort, target: params.target}, sourceNode)
        return
      }
      if (this.definition.edges.some(edge => edge.source === params.source && edge.target === params.target)) return
      this.addDefinitionEdge(params.source, params.target, kind)
      this.markDirty()
    },
    addDefinitionEdge(source, target, kind, sourcePort = '') {
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
      if (kind === 'SEMANTIC') edge.sourcePort = sourcePort
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
      this.inspectorCollapsed = false
      this.refreshCanvasLayout()
    },
    suppressNodeContextMenu({event}) {
      event?.preventDefault()
      event?.stopPropagation()
    },
    nodeDragStarted({node}) {
      this.nodeDragOrigin = node?.position ? {...node.position} : null
    },
    nodeDragStopped({node}) {
      const origin = this.nodeDragOrigin
      this.nodeDragOrigin = null
      if (!node?.position || !origin
          || (node.position.x === origin.x && node.position.y === origin.y)) return
      if (node.id === '__start__' || node.id === '__end__') {
        const positionKey = node.id === '__start__' ? 'startPosition' : 'endPosition'
        this.definition.ui = {
          ...(this.definition.ui || {}),
          [positionKey]: {...node.position}
        }
        this.markDirty()
        return
      }
      const definitionNode = this.definition.nodes.find(item => item.id === node.id)
      if (definitionNode) {
        definitionNode.ui = {...(definitionNode.ui || {}), ...node.position}
      }
      this.markDirty()
    },
    selectEdge({edge}) {
      this.selectedEdge = this.definition.edges.find(item => item.id === edge.id)
        || edge.data?.definitionEdge || null
      this.conditionFieldSelection = ''
      if (this.selectedEdge?.kind === 'CONDITION' && !this.selectedEdge.condition && !this.selectedEdge.default) {
        this.selectedEdge.condition = { expression: '', priority: 100, onError: 'FAIL' }
      }
      this.selectedNode = null
      this.inspectorCollapsed = false
      this.refreshCanvasLayout()
    },
    clearSelection() {
      this.selectedNode = null
      this.selectedEdge = null
      this.configError = ''
      this.mappingError = ''
      this.inspectorCollapsed = true
      this.refreshCanvasLayout()
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
        this.invalidateSelectedInferredSchema()
        this.syncHttpNodeType(value.method)
        this.configError = ''
        this.markDirty()
      } catch (error) {
        this.configError = error.message
      }
    },
    schemaConfigChanged(value) {
      this.selectedNode.config = value
      this.invalidateSelectedInferredSchema()
      this.syncHttpNodeType(value?.method)
      this.selectedNodeConfig = JSON.stringify(value, null, 2)
      this.configError = ''
      this.markDirty()
    },
    classifierConfigChanged(value) {
      if (!this.selectedNode || this.selectedNode.type !== 'llm_classifier') return
      const nodeId = this.selectedNode.id
      const slugs = new Set((value?.branches || []).map(branch => branch.slug).filter(Boolean))
      this.definition.edges = (this.definition.edges || []).filter(edge =>
        edge.source !== nodeId || edge.kind !== 'SEMANTIC' || slugs.has(edge.sourcePort))
      this.schemaConfigChanged(value)
      this.buildCanvas()
    },
    classifierBranchTargetChanged({slug, target}, nodeOverride = null) {
      const node = nodeOverride || this.selectedNode
      if (!node || node.type !== 'llm_classifier' || !slug) return
      if (target && !isClassifierTargetAllowed(this.definition, node.id, target)) {
        this.$message.warning('分类分支只能选择下游节点，当前目标会形成循环')
        return
      }
      const existing = (this.definition.edges || []).find(edge => edge.source === node.id
        && edge.kind === 'SEMANTIC' && edge.sourcePort === slug)
      if (!target) {
        if (existing) this.definition.edges = this.definition.edges.filter(edge => edge !== existing)
      } else if (existing) {
        existing.target = target
      } else {
        this.addDefinitionEdge(node.id, target, 'SEMANTIC', slug)
      }
      this.buildCanvas()
      this.markDirty()
    },
    semanticBranchLabel(edge) {
      const source = (this.definition.nodes || []).find(node => node.id === edge?.source)
      const branch = (source?.config?.branches || []).find(item => item.slug === edge?.sourcePort)
      return branch?.label || edge?.sourcePort || '分类分支'
    },
    nodePromptChanged(value) {
      const config = {...(this.selectedNode?.config || {})}
      const prompt = String(value || '')
      if (prompt) config.prompt = prompt
      else delete config.prompt
      this.schemaConfigChanged(config)
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
    normalizeClassifierNodeConfigs() {
      ;(this.definition.nodes || []).forEach(node => {
        if (node.type !== 'llm_classifier') return
        const current = node.config && typeof node.config === 'object' && !Array.isArray(node.config)
          ? node.config : {}
        const branches = Array.isArray(current.branches) ? current.branches : []
        const normalized = branches.map((branch, index) => ({
          ...branch,
          slug: branch.slug || `branch_${index + 1}`,
          label: branch.label || branch.slug || `分类 ${index + 1}`,
          description: branch.description || '',
          examples: Array.isArray(branch.examples) ? branch.examples : []
        }))
        node.config = {
          ...current,
          version: current.version || 2,
          branches: normalized,
          minConfidence: Number.isFinite(Number(current.minConfidence))
            ? Number(current.minConfidence) : 0.6,
          fallbackSlug: current.fallbackSlug || normalized[normalized.length - 1]?.slug || '',
          invalidResponseStrategy: current.invalidResponseStrategy || 'FALLBACK',
          instruction: current.instruction || current.systemPrompt || '',
          maxWaitSeconds: Number(current.maxWaitSeconds || 300)
        }
        delete node.config.systemPrompt
      })
    },
    normalizeInferredOutputSchemas() {
      ;(this.definition.nodes || []).forEach(node => {
        const inferred = node.ui?.inferredOutputSchema
        if (!inferred?.schema || this.isUsableEditorSchema(inferred.schema)) return
        delete node.ui.inferredOutputSchema
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
    invalidateSelectedInferredSchema() {
      if (!this.selectedNode?.id) return
      this.invalidateNodeAndDownstreamInferredSchemas(this.selectedNode.id)
    },
    invalidateNodeAndDownstreamInferredSchemas(nodeId) {
      const children = new Map()
      ;(this.definition.edges || []).forEach(edge => {
        if (!children.has(edge.source)) children.set(edge.source, [])
        children.get(edge.source).push(edge.target)
      })
      const visited = new Set()
      const stack = [nodeId]
      const staleAt = new Date().toISOString()
      while (stack.length) {
        const current = stack.pop()
        if (!current || visited.has(current)) continue
        visited.add(current)
        const node = (this.definition.nodes || []).find(item => item.id === current)
        const inferred = node?.ui?.inferredOutputSchema
        if (inferred && !inferred.stale) {
          inferred.stale = true
          inferred.staleAt = staleAt
        }
        ;(children.get(current) || []).forEach(child => stack.push(child))
      }
    },
    mergeEditorSchema(formalSchema, sampleSchema) {
      if (!this.isUsableEditorSchema(formalSchema)) {
        return JSON.parse(JSON.stringify(sampleSchema || {}))
      }
      if (!this.isUsableEditorSchema(sampleSchema)) {
        return JSON.parse(JSON.stringify(formalSchema))
      }
      const formal = JSON.parse(JSON.stringify(formalSchema))
      const formalTypes = Array.isArray(formal.type) ? formal.type : [formal.type]
      const sampleTypes = Array.isArray(sampleSchema.type)
        ? sampleSchema.type : [sampleSchema.type]
      if (formalTypes.includes('object') && sampleTypes.includes('object')) {
        const formalPropertyNames = new Set(Object.keys(formal.properties || {}))
        formal.properties = {...(formal.properties || {})}
        const formalRequired = Array.isArray(formal.required) ? formal.required : []
        const inferredRequired = Array.isArray(sampleSchema.required)
          ? sampleSchema.required.filter(name => !formalPropertyNames.has(name)) : []
        const required = [...new Set([...formalRequired, ...inferredRequired])]
        if (required.length) formal.required = required
        Object.entries(sampleSchema.properties || {}).forEach(([name, child]) => {
          formal.properties[name] = formal.properties[name]
            ? this.mergeEditorSchema(formal.properties[name], child)
            : JSON.parse(JSON.stringify(child))
        })
      }
      if (formalTypes.includes('array') && sampleTypes.includes('array')
        && sampleSchema.items) {
        formal.items = formal.items
          ? this.mergeEditorSchema(formal.items, sampleSchema.items)
          : JSON.parse(JSON.stringify(sampleSchema.items))
      }
      return formal
    },
    isUsableEditorSchema(schema) {
      if (!schema || Array.isArray(schema) || typeof schema !== 'object') return false
      return ['type', 'properties', 'items', 'oneOf', 'anyOf', 'allOf', '$ref',
        'enum', 'const', 'additionalProperties']
        .some(key => Object.prototype.hasOwnProperty.call(schema, key))
    },
    inferEditorSchema(value, depth = 0) {
      if (value === null) return {type: 'null'}
      if (Array.isArray(value)) {
        const sample = value.find(item => item !== null && item !== undefined)
        return {
          type: 'array',
          items: depth >= 6 || sample === undefined
            ? {} : this.inferEditorSchema(sample, depth + 1)
        }
      }
      if (typeof value === 'object') {
        if (depth >= 6) return {type: 'object'}
        const properties = Object.entries(value).reduce((result, [key, child]) => {
          result[key] = this.inferEditorSchema(child, depth + 1)
          return result
        }, {})
        return {
          type: 'object',
          properties,
          required: Object.keys(properties)
        }
      }
      if (typeof value === 'number') {
        return {type: Number.isInteger(value) ? 'integer' : 'number'}
      }
      return {type: typeof value === 'boolean' ? 'boolean' : 'string'}
    },
    transformEditorOutputSchema(node) {
      const config = node?.config || {}
      if (config.mode === 'VALUE') {
        const rule = Array.isArray(config.rules) ? config.rules[0] : null
        return rule ? this.transformRuleResultSchema(rule) : {}
      }
      const item = {
        type: 'object',
        properties: {},
        additionalProperties: !!config.preserveUnmapped
      }
      ;(Array.isArray(config.rules) ? config.rules : []).forEach(rule => {
        if (!this.validTransformTargetPath(rule.targetPath)) return
        const defaultWhen = rule.defaultWhen || 'NEVER'
        const guaranteed = !!rule.required || rule.operation === 'CONSTANT'
          || defaultWhen === 'MISSING' || !rule.sourcePath
        this.addTransformEditorSchemaPath(item, rule.targetPath,
          this.transformRuleResultSchema(rule), guaranteed)
      })
      return config.mode === 'ARRAY_MAP' ? {type: 'array', items: item} : item
    },
    transformPathTokens(path) {
      const tokens = []
      String(path || '').split('.').filter(Boolean).forEach(segment => {
        const bracket = segment.indexOf('[')
        const field = bracket < 0 ? segment : segment.slice(0, bracket)
        if (field) tokens.push({field, each: false})
        let suffix = bracket < 0 ? '' : segment.slice(bracket)
        while (suffix.startsWith('[]')) {
          tokens.push({field: '', each: true})
          suffix = suffix.slice(2)
        }
      })
      return tokens
    },
    validTransformTargetPath(path) {
      return /^(?:\$|[\p{L}_][\p{L}\p{N}_-]*(?:\[\])*(?:\.[\p{L}_][\p{L}\p{N}_-]*(?:\[\])*){0,9})$/u.test(path || '')
    },
    addTransformEditorSchemaPath(root, path, resultSchema, guaranteed) {
      const tokens = this.transformPathTokens(path)
      const type = resultSchema?.type || 'object'
      let current = root
      tokens.forEach((token, index) => {
        const next = tokens[index + 1]
        const leaf = !next
        if (token.each) {
          if (!current.type) current.type = 'array'
          if (current.type !== 'array') return
          current.items = current.items || {}
          if (leaf) Object.assign(current.items, JSON.parse(JSON.stringify(resultSchema || {type})))
          else if (!current.items.type) {
            current.items.type = next.each ? 'array' : 'object'
            if (!next.each) {
              current.items.properties = {}
              current.items.additionalProperties = false
            }
          }
          current = current.items
          return
        }
        current.type = current.type || 'object'
        current.properties = current.properties || {}
        if (guaranteed) current.required = [...new Set([...(current.required || []), token.field])]
        const field = current.properties[token.field] || {}
        current.properties[token.field] = field
        if (leaf) Object.assign(field, JSON.parse(JSON.stringify(resultSchema || {type})))
        else if (!field.type) {
          field.type = next.each ? 'array' : 'object'
          if (!next.each) {
            field.properties = {}
            field.additionalProperties = false
          }
        }
        current = field
      })
    },
    transformRuleResultType(rule) {
      const operation = rule?.operation || 'COPY'
      if (['TO_STRING', 'TRIM', 'UPPERCASE', 'LOWERCASE', 'ARRAY_JOIN', 'CONCAT',
        'DATE_FORMAT', 'TEMPLATE'].includes(operation)) return 'string'
      if (['TO_INTEGER', 'ARRAY_LENGTH'].includes(operation)) return 'integer'
      if (operation === 'TO_NUMBER') return 'number'
      if (operation === 'ARRAY_AGGREGATE') return rule?.aggregate === 'COUNT' ? 'integer' : 'number'
      if (operation === 'TO_BOOLEAN') return 'boolean'
      if (['ARRAY_FILTER', 'ARRAY_FLATTEN', 'ARRAY_SORT', 'ARRAY_DISTINCT',
        'ARRAY_GROUP'].includes(operation)) return 'array'
      return ['object', 'array', 'string', 'integer', 'number', 'boolean', 'null']
        .includes(rule?.resultType) ? rule.resultType : 'object'
    },
    transformRuleResultSchema(rule) {
      let schema
      if (rule?.resultSchema?.type) schema = JSON.parse(JSON.stringify(rule.resultSchema))
      else if (rule?.operation === 'CONSTANT') schema = this.inferEditorSchema(rule.value)
      else schema = {type: this.transformRuleResultType(rule)}
      const operation = rule?.operation || 'COPY'
      if (operation === 'ARRAY_AGGREGATE' && rule?.aggregate !== 'COUNT') {
        this.appendTransformSchemaType(schema, 'null')
      }
      if (operation === 'ARRAY_GROUP' && !['NONE', 'COUNT'].includes(rule?.groupAggregate || 'NONE')) {
        const valueSchema = schema?.items?.properties?.value
        if (valueSchema) this.appendTransformSchemaType(valueSchema, 'null')
      }
      const nullable = rule?.onError === 'NULL'
        || (rule?.onError === 'DEFAULT' && rule?.defaultValue == null)
        || ((rule?.defaultWhen || 'NEVER') !== 'NEVER' && rule?.defaultValue == null)
        || (rule?.targetPath === '$' && !rule?.required && rule?.defaultWhen !== 'MISSING')
      if (nullable) this.appendTransformSchemaType(schema, 'null')
      if (rule?.onError === 'KEEP') {
        this.appendTransformSchemaType(schema, rule?.sourceType || 'object')
        if (Array.isArray(schema.type)) {
          delete schema.properties
          delete schema.required
          delete schema.items
          delete schema.additionalProperties
        }
      }
      return schema
    },
    appendTransformSchemaType(schema, type) {
      const values = Array.isArray(schema?.type) ? [...schema.type] : [schema?.type || type]
      if (!values.includes(type)) values.push(type)
      schema.type = values.length === 1 ? values[0] : values
    },
    transformEditorDiagnostics(node) {
      const rules = Array.isArray(node?.config?.rules) ? node.config.rules : []
      const targets = []
      const diagnostics = []
      rules.forEach(rule => {
        const path = String(rule?.targetPath || '')
        const sourceKey = rule?.sourceKey || 'source'
        if (!this.validTransformTargetPath(path)
          || (node?.config?.mode === 'VALUE' ? path !== '$' : path === '$')) {
          diagnostics.push(`输出字段路径无效：${path || '未填写'}`)
        } else if (targets.some(existing => this.transformPathsConflict(existing, path))) {
          diagnostics.push(`输出字段路径冲突：${path}`)
        }
        const sourceArrays = (String(rule?.sourcePath || '').match(/\[\]/g) || []).length
        const targetArrays = (path.match(/\[\]/g) || []).length
        if (node?.config?.mode !== 'VALUE' && rule?.operation === 'CONSTANT' && targetArrays) {
          diagnostics.push(`固定值不能写入数组通配路径：${path}`)
        } else if (node?.config?.mode !== 'VALUE' && rule?.operation !== 'CONSTANT' && sourceArrays !== targetArrays) {
          diagnostics.push(`来源与输出数组层级不一致：${rule?.sourcePath || '当前数据'} → ${path}`)
        }
        if (rule?.operation !== 'CONSTANT' && !node?.inputMapping?.[sourceKey]) {
          diagnostics.push(`输出字段 ${path || '未填写'} 的数据来源已失效`)
        }
        if (rule?.operation === 'DATE_FORMAT' && !rule?.outputFormat) {
          diagnostics.push(`输出字段 ${path || '未填写'} 缺少日期输出格式`)
        }
        if (rule?.operation === 'TEMPLATE' && !rule?.template) {
          diagnostics.push(`输出字段 ${path || '未填写'} 缺少文本模板`)
        }
        if (rule?.operation === 'EXPRESSION' && !rule?.expression) {
          diagnostics.push(`输出字段 ${path || '未填写'} 缺少受限表达式`)
        }
        targets.push(path)
      })
      if (node?.config?.mode === 'VALUE' && rules.length !== 1) {
        diagnostics.push('直接转换整份数据时必须且只能配置一条规则')
      }
      if (node?.config?.mode === 'ARRAY_MAP' && node?.config?.arrayAlignment === 'KEYED'
        && !node?.config?.alignmentPath) {
        diagnostics.push('按字段关联多个数组时必须选择关联字段')
      }
      return diagnostics
    },
    transformPathsConflict(left, right) {
      const leftTokens = this.transformPathTokens(left)
      const rightTokens = this.transformPathTokens(right)
      const maximum = Math.min(leftTokens.length, rightTokens.length)
      for (let index = 0; index < maximum; index++) {
        const leftToken = leftTokens[index]
        const rightToken = rightTokens[index]
        if (leftToken.each === rightToken.each && leftToken.field === rightToken.field) continue
        return leftToken.each !== rightToken.each
      }
      return true
    },
    inputMappingChanged() {
      try {
        const value = JSON.parse(this.selectedNodeInputMapping)
        if (!value || Array.isArray(value) || typeof value !== 'object') {
          throw new Error('输入映射必须是 JSON 对象')
        }
        this.selectedNode.inputMapping = value
        this.invalidateSelectedInferredSchema()
        this.mappingError = ''
        this.markDirty()
      } catch (error) {
        this.mappingError = error.message
      }
    },
    visualInputMappingChanged(value) {
      this.selectedNode.inputMapping = value
      this.invalidateSelectedInferredSchema()
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
        await this.refreshResolvedNodeSchemas()
        this.refreshCanvasNodeData()
      } finally {
        this.resourceLoading = false
      }
    },
    async refreshResolvedNodeSchemas() {
      if (!this.currentDefinition?.id) {
        this.backendResolvedNodeSchemas = {}
        return
      }
      try {
        const response = await resolveWorkflowNodeSchemas(
          this.currentDefinition.id, this.resourceEnvironment)
        this.backendResolvedNodeSchemas = (response.data || []).reduce((result, item) => {
          result[item.nodeId] = {
            inputSchema: item.inputSchema,
            outputSchema: item.outputSchema,
            source: this.schemaSourceLabel(item.source),
            sourceCode: item.source,
            sourceVersion: item.sourceVersion,
            diagnostics: item.diagnostics || [],
            fieldSources: item.fieldSources || {}
          }
          return result
        }, {})
      } catch (error) {
        this.backendResolvedNodeSchemas = {}
      }
    },
    schemaSourceLabel(source) {
      const labels = {
        NODE_CONTRACT: '节点契约',
        API_CONNECTOR: '连接器 Schema',
        OPENAPI: 'OpenAPI',
        DATABASE_METADATA: '数据库元数据',
        LLM_STRUCTURED_OUTPUT: '结构化输出',
        TRANSFORM_RULES: '转换规则',
        NODE_TEST: '试运行样本',
        USER_OVERRIDE: '用户正式覆盖'
      }
      return labels[source] || source || '节点契约'
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
        this.invalidateSelectedInferredSchema()
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
        this.invalidateSelectedInferredSchema()
        this.markDirty()
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
      this.invalidateSelectedInferredSchema()
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
      const startNode = this.canvasNodes.find(item => item.id === '__start__')
      if (startNode) {
        startNode.data = {
          ...startNode.data,
          testable: this.canDebug && this.workflowDraftTestAvailability.available,
          testLabel: '试运行工作流',
          testTitle: '直接试运行当前草稿'
        }
      }
    },
    latestNodeStatus(nodeId) {
      return this.debugNodeRuns
        .filter(item => item.nodeId === nodeId)
        .sort((left, right) => (right.attemptNo || 0) - (left.attemptNo || 0))[0]?.status || ''
    },
    nodeSupportsTest(node) {
      if (!node || ((!this.currentDefinition?.id || this.dirty) && !this.canEdit)) return false
      const descriptor = this.descriptors.find(item => item.type === node.type
        && item.handlerVersion === node.typeVersion)
      if (!descriptor || descriptor.sideEffect === 'WRITE') return false
      if (['approval', 'wait', 'sub_workflow'].includes(node.type)) return false
      const capabilities = descriptor.capabilities || []
      if (descriptor.sideEffect === 'NONE' && !capabilities.includes('MOCKABLE')) return false
      return capabilities.includes('CANCELLABLE')
    },
    testChainAvailability(target) {
      if (!target) return {available: false, reason: '请先选择节点', nodeIds: []}
      const nodes = new Map((this.definition.nodes || []).map(node => [node.id, node]))
      const visited = new Set()
      let current = target.id
      while (current) {
        if (visited.has(current)) {
          return {available: false, reason: '上游路径包含循环', nodeIds: [...visited]}
        }
        visited.add(current)
        const node = nodes.get(current)
        if (!node) {
          return {available: false, reason: '上游路径节点已失效', nodeIds: [...visited]}
        }
        if (['approval', 'wait', 'sub_workflow', 'condition', 'parallel', 'join',
          'loop', 'llm_classifier'].includes(node.type)) {
          return {
            available: false,
            reason: `路径包含暂不支持直接试运行的控制节点「${node.name}」`,
            nodeIds: [...visited]
          }
        }
        const descriptor = this.descriptors.find(item => item.type === node.type
          && item.handlerVersion === node.typeVersion)
        if (!descriptor || descriptor.sideEffect === 'WRITE'
          || !(descriptor.capabilities || []).includes('CANCELLABLE')) {
          return {
            available: false,
            reason: `路径节点「${node.name}」不满足安全执行要求`,
            nodeIds: [...visited]
          }
        }
        const incoming = (this.definition.edges || [])
          .filter(edge => edge.target === current)
        if (incoming.length !== 1 || incoming[0].kind !== 'NORMAL') {
          return {
            available: false,
            reason: '仅支持单入口普通连线组成的线性路径',
            nodeIds: [...visited]
          }
        }
        current = incoming[0].source === '__start__' ? null : incoming[0].source
      }
      return {available: true, reason: '', nodeIds: [...visited]}
    },
    openCanvasNodeTest(nodeId) {
      const node = this.definition.nodes.find(item => item.id === nodeId)
      if (!node) return
      this.selectNode({node: {id: nodeId}})
      this.openNodeTest()
    },
    openNodeTest() {
      if (this.nodeTestRunning) {
        this.nodeTestDialogOpen = true
        return
      }
      if (!this.nodeTestAvailability.available) {
        this.$message.warning(this.nodeTestAvailability.reason)
        return
      }
      this.nodeTestNodeId = this.selectedNode.id
      this.nodeTestNodeName = this.selectedNode.name
      this.nodeTestTimeoutSeconds = Math.min(this.selectedNode.timeoutSeconds || 60, 120)
      this.nodeTestMode = 'NODE'
      this.workflowDraftTest = false
      this.nodeTestResult = null
      this.nodeTestSchemaApplied = false
      this.nodeTestSchemaPromoted = !!this.selectedNode.outputSchemaOverride
      this.nodeTestInferredSchema = this.selectedNode.ui?.inferredOutputSchema?.schema || null
      this.nodeTestDialogOpen = true
    },
    async fetchNodeFields() {
      this.openNodeTest()
      if (!this.nodeTestDialogOpen || this.nodeTestRunning) return
      this.nodeTestInputJson = '{}'
      this.nodeTestAutoPersistSchema = true
      await this.runNodeTest()
    },
    async runNodeTest() {
      let input
      if (this.nodeTestIsClassifier && this.nodeTestMode === 'NODE') {
        if (!this.classifierTestContent.trim()) {
          this.$message.warning('请输入要分类的内容')
          return
        }
        input = {input: this.classifierTestContent}
      } else {
        try {
          input = JSON.parse(this.nodeTestInputJson)
        } catch (error) {
          this.$message.error(this.workflowDraftTest
            ? '流程输入必须是有效 JSON'
            : '节点输入必须是有效 JSON')
          return
        }
      }
      if (!this.currentDefinition?.id || this.dirty) {
        const saved = await this.saveDraft({silent: true})
        if (!saved) {
          this.nodeTestAutoPersistSchema = false
          return
        }
        this.$message.info(this.workflowDraftTest
          ? '已同步当前草稿，正在试运行工作流'
          : '已同步当前草稿，正在试运行节点')
      }
      this.nodeTestRunning = true
      this.nodeTestResult = null
      this.nodeTestSchemaApplied = false
      this.nodeTestSchemaPromoted = false
      this.nodeTestInferredSchema = null
      try {
        const response = await testWorkflowNode(
          this.currentDefinition.id,
          this.nodeTestNodeId,
          {
            input,
            environment: this.resourceEnvironment,
            timeoutSeconds: this.nodeTestTimeoutSeconds,
            mode: this.nodeTestMode
          }
        )
        this.nodeTestResult = response.data
        this.pollNodeTest()
      } catch (error) {
        this.nodeTestRunning = false
        this.nodeTestAutoPersistSchema = false
      }
    },
    async pollNodeTest() {
      const testRunId = this.nodeTestResult?.testRunId
      if (!testRunId) return
      clearTimeout(this.nodeTestPollTimer)
      try {
        const response = await getWorkflowNodeTest(testRunId)
        this.nodeTestResult = response.data
        if (['QUEUED', 'RUNNING'].includes(response.data?.status)) {
          this.nodeTestPollTimer = setTimeout(() => this.pollNodeTest(), 800)
          return
        }
        this.nodeTestRunning = false
        if (response.data?.status === 'SUCCEEDED') {
          const schemaUpdated = await this.generateNodeTestSchema({silent: true})
          if (schemaUpdated && this.nodeTestAutoPersistSchema) {
            await this.saveDraft({silent: true})
          }
          this.nodeTestAutoPersistSchema = false
          if (this.workflowDraftTest) {
            this.$message.success(schemaUpdated
              ? '当前草稿试运行成功，末节点响应字段已自动更新'
              : '当前草稿试运行成功')
          } else {
            this.$message.success(schemaUpdated
              ? '当前节点试运行成功，响应字段已自动更新'
              : '当前节点试运行成功')
          }
        } else {
          this.nodeTestAutoPersistSchema = false
        }
      } catch (error) {
        this.nodeTestRunning = false
        this.nodeTestAutoPersistSchema = false
      }
    },
    async cancelNodeTest() {
      const testRunId = this.nodeTestResult?.testRunId
      if (!testRunId) return
      clearTimeout(this.nodeTestPollTimer)
      try {
        const response = await cancelWorkflowNodeTest(testRunId)
        this.nodeTestResult = response.data
        if (response.data?.status === 'CANCELLED') {
          this.$message.success(this.workflowDraftTest ? '已取消工作流试运行' : '已取消当前节点试运行')
        } else {
          this.$message.info(this.workflowDraftTest ? '工作流试运行已经结束' : '节点试运行已经结束')
        }
        this.nodeTestRunning = false
        this.nodeTestAutoPersistSchema = false
      } catch (error) {
        this.pollNodeTest()
      }
    },
    async generateNodeTestSchema(options = {}) {
      const silent = options?.silent === true
      const testRunId = this.nodeTestResult?.testRunId
      if (!testRunId || this.nodeTestResult?.status !== 'SUCCEEDED') return false
      if (this.dirty) {
        if (!silent) this.$message.warning('当前草稿已有修改，请保存后重新试运行再生成字段结构')
        return false
      }
      this.nodeTestGeneratingSchema = true
      try {
        const response = await inferWorkflowNodeTestSchema(testRunId)
        const inferred = response.data
        if (!this.isUsableEditorSchema(inferred?.schema)) {
          if (!silent) this.$message.error('接口返回的字段结构无效，请重启后端后重新运行')
          return false
        }
        if (inferred.draftRevision !== this.currentDefinition?.draftRevision) {
          this.$message.warning('试运行对应的草稿修订已变化，请重新试运行')
          return false
        }
        const node = this.definition.nodes.find(item => item.id === inferred.nodeId)
        if (!node) {
          this.$message.error('试运行对应节点已不存在')
          return false
        }
        node.ui = {
          ...(node.ui || {}),
          inferredOutputSchema: {
            schema: inferred.schema,
            source: 'NODE_TEST',
            inferredAt: inferred.inferredAt,
            sampleCount: inferred.sampleCount,
            nodeConfigHash: inferred.nodeConfigHash,
            schemaSourceVersion: inferred.schemaSourceVersion,
            testRunId: inferred.testRunId,
            diagnostics: inferred.diagnostics || [],
            stale: false
          }
        }
        this.nodeTestInferredSchema = inferred.schema
        this.nodeTestSchemaApplied = true
        this.markDirty()
        if (this.selectedNode?.id === node.id) this.selectedInspectorTab = 'mapping'
        if (!silent) {
          this.$message.success(inferred.sampleCount > 1
            ? `已聚合 ${inferred.sampleCount} 次兼容试运行，字段结构已加入草稿`
            : '样本字段结构已加入草稿，请保存后生效')
        }
        return true
      } finally {
        this.nodeTestGeneratingSchema = false
      }
    },
    async promoteNodeTestSchema() {
      const node = this.definition.nodes.find(item => item.id === this.nodeTestNodeId)
      const schema = this.nodeTestInferredSchema || node?.ui?.inferredOutputSchema?.schema
      if (!node || !schema) return
      const promoted = await this.promoteOutputSchema(
        node, schema, this.nodeTestResult?.testRunId)
      if (promoted) this.nodeTestSchemaPromoted = true
    },
    async promoteSelectedInferredSchema() {
      const inferred = this.selectedNode?.ui?.inferredOutputSchema
      if (!this.selectedNode || !inferred?.schema || inferred.stale) return
      await this.promoteOutputSchema(this.selectedNode, inferred.schema, null)
    },
    async promoteOutputSchema(node, schema, testRunId) {
      const impacts = this.outputSchemaImpacts(node.id, schema)
      const detail = impacts.length
        ? `检测到 ${impacts.length} 个下游映射路径不在新结构中，保存前需要重新检查。`
        : '发布后节点输出不符合该结构时，运行会以 OUTPUT_SCHEMA_MISMATCH 失败。'
      try {
        await this.$confirm(detail, '确认设为正式输出结构', {
          confirmButtonText: '确认设置',
          cancelButtonText: '取消',
          type: impacts.length ? 'warning' : 'info'
        })
      } catch (error) {
        return false
      }
      node.outputSchemaOverride = JSON.parse(JSON.stringify(schema))
      node.ui = {
        ...(node.ui || {}),
        outputSchemaOverrideMetadata: {
          source: 'NODE_TEST',
          confirmedAt: new Date().toISOString(),
          testRunId: testRunId || undefined,
          sampleCount: node.ui?.inferredOutputSchema?.sampleCount || 1
        }
      }
      this.markDirty()
      this.refreshCanvasNodeData()
      if (impacts.length) {
        this.$message.warning(`正式结构已加入草稿，${impacts.length} 个下游映射需要检查`)
      } else {
        this.$message.success('正式输出结构已加入草稿，保存并发布后参与运行校验')
      }
      return true
    },
    async clearFormalOutputSchema() {
      if (!this.selectedNode?.outputSchemaOverride) return
      try {
        await this.$confirm(
          '恢复后将重新使用节点或资源契约；已发布版本不受影响。',
          '恢复节点契约',
          {confirmButtonText: '确认恢复', cancelButtonText: '取消', type: 'warning'})
      } catch (error) {
        return
      }
      delete this.selectedNode.outputSchemaOverride
      if (this.selectedNode.ui) delete this.selectedNode.ui.outputSchemaOverrideMetadata
      this.markDirty()
      this.refreshCanvasNodeData()
    },
    outputSchemaImpacts(nodeId, schema) {
      const prefix = `$.nodes.${nodeId}.output`
      const nodeImpacts = (this.definition.nodes || []).flatMap(node =>
        Object.entries(node.inputMapping || {}).flatMap(([target, binding]) => {
          const expression = binding?.expression
          if (!expression?.startsWith(prefix)) return []
          const path = expression.slice(prefix.length).replace(/^\./, '')
          if (!path || this.schemaContainsPath(schema, path.split('.'))) return []
          return [{nodeId: node.id, target, expression}]
        }))
      const outputImpacts = Object.entries(this.definition.outputs || {})
        .flatMap(([target, binding]) => {
          const expression = binding?.expression
          if (!expression?.startsWith(prefix)) return []
          const path = expression.slice(prefix.length).replace(/^\./, '')
          if (!path || this.schemaContainsPath(schema, path.split('.'))) return []
          return [{nodeId: '__end__', target, expression}]
        })
      const edgeImpacts = (this.definition.edges || []).flatMap(edge => {
        const expression = edge?.condition?.expression
        return this.expressionSchemaImpacts(expression, prefix, schema)
          .map(reference => ({
            nodeId: edge.target,
            target: `连线条件 ${edge.source} → ${edge.target}`,
            expression: reference
          }))
      })
      const configImpacts = (this.definition.nodes || []).flatMap(node => {
        const strings = []
        const visit = (value, path = 'config') => {
          if (typeof value === 'string') {
            if (value.includes(prefix)) strings.push({value, path})
            return
          }
          if (Array.isArray(value)) {
            value.forEach((item, index) => visit(item, `${path}[${index}]`))
            return
          }
          if (value && typeof value === 'object') {
            Object.entries(value).forEach(([key, child]) => visit(child, `${path}.${key}`))
          }
        }
        visit(node.config || {})
        return strings.flatMap(item => this.expressionSchemaImpacts(item.value, prefix, schema)
          .map(reference => ({nodeId: node.id, target: item.path, expression: reference})))
      })
      return [...nodeImpacts, ...outputImpacts, ...edgeImpacts, ...configImpacts]
    },
    expressionSchemaImpacts(expression, prefix, schema) {
      if (typeof expression !== 'string' || !expression.includes(prefix)) return []
      const impacts = []
      let cursor = 0
      while (cursor < expression.length) {
        const start = expression.indexOf(prefix, cursor)
        if (start < 0) break
        const suffix = expression.slice(start + prefix.length)
        const match = suffix.match(/^(?:\.[\p{L}\p{N}_-]+(?:\[[0-9]+\])?)*/u)
        const pathText = match?.[0] || ''
        const path = pathText.replace(/^\./, '')
        if (path && !this.schemaContainsPath(schema, path.split('.'))) {
          impacts.push(`${prefix}${pathText}`)
        }
        cursor = start + prefix.length + Math.max(pathText.length, 1)
      }
      return impacts
    },
    schemaContainsPath(schema, segments) {
      let current = schema
      for (const rawSegment of segments) {
        const segment = rawSegment.replace(/\[[0-9]+]$/, '')
        const types = Array.isArray(current?.type) ? current.type : [current?.type]
        if (types.includes('array')) current = current?.items
        if (current?.properties?.[segment]) {
          current = current.properties[segment]
          continue
        }
        if (current?.additionalProperties === true
          || (current?.additionalProperties && typeof current.additionalProperties === 'object')) {
          current = current.additionalProperties === true ? {} : current.additionalProperties
          continue
        }
        return false
      }
      return true
    },
    nodeTestStatusLabel(status) {
      const labels = {
        QUEUED: '等待运行',
        RUNNING: '正在运行',
        SUCCEEDED: '运行成功',
        FAILED: '运行失败',
        CANCELLED: '已取消'
      }
      return labels[status] || status || '-'
    },
    nodeTestStatusType(status) {
      return {
        QUEUED: 'info',
        RUNNING: 'warning',
        SUCCEEDED: 'success',
        FAILED: 'danger',
        CANCELLED: 'info'
      }[status] || 'info'
    },
    nodeTestErrorCodeLabel(code) {
      const labels = {
        INTERNAL_ERROR: '节点执行异常',
        NODE_TIMEOUT: '节点运行超时',
        OUTPUT_SCHEMA_MISMATCH: '输出结构不匹配',
        DEFINITION_INVALID: '节点配置不完整',
        PERMISSION_DENIED: '没有执行权限',
        RESOURCE_NOT_FOUND: '资源不存在',
        RESOURCE_DISABLED: '资源已停用',
        RESOURCE_TEMPORARILY_UNAVAILABLE: '资源暂时不可用',
        NODE_CANCELLED: '运行已取消'
      }
      return labels[code] || '节点运行失败'
    },
    nodeTestSchemaLabel(source) {
      const labels = {
        NODE_CONTRACT: '节点契约',
        API_CONNECTOR: 'API 连接器',
        DATABASE_METADATA: '数据库元数据',
        LLM_STRUCTURED_OUTPUT: '大模型结构化输出',
        PUBLISHED_SNAPSHOT: '发布快照',
        USER_OVERRIDE: '用户正式覆盖'
      }
      return labels[source] || source || '节点契约'
    },
    formatNodeTestJson(value) {
      if (value === undefined || value === null) return '无输出'
      return JSON.stringify(value, null, 2)
    },
    async openTestRun() {
      if (this.canDebug && this.workflowDraftTestAvailability.available) {
        this.openDraftWorkflowTest()
        return
      }
      if (!this.currentDefinition?.currentPublishedVersionId) {
        const reason = this.canDebug ? this.workflowDraftTestAvailability.reason : ''
        this.$message.warning(reason
          ? `当前草稿暂不能直接试运行：${reason}；请发布后测试完整流程`
          : '请先发布工作流版本')
        return
      }
      this.testInput = {}
      this.testInputSchema = {type: 'object', properties: {}}
      this.testInputSchemaError = ''
      this.testInputValidation = {valid: false, message: '', errors: []}
      this.testInputSchemaLoading = true
      this.testDialogOpen = true
      try {
        const response = await getWorkflowVersion(
          this.currentDefinition.id,
          this.currentDefinition.currentPublishedVersionId
        )
        this.testInputSchema = this.publishedInputSchema(response.data?.definitionJson)
      } catch (error) {
        this.testInputSchemaError = '未能读取发布版本的输入契约，仍可切换到 JSON 模式填写。'
      } finally {
        this.testInputSchemaLoading = false
        this.$nextTick(() => this.$refs.testInputEditor?.reset())
      }
    },
    openDraftWorkflowTest() {
      if (this.nodeTestRunning) {
        this.nodeTestDialogOpen = true
        return
      }
      const availability = this.workflowDraftTestAvailability
      if (!availability.available) {
        this.$message.warning(`当前草稿暂不能直接试运行：${availability.reason}`)
        return
      }
      const target = availability.target
      this.nodeTestNodeId = target.id
      this.nodeTestNodeName = target.name
      this.nodeTestTimeoutSeconds = Math.min(
        this.definition.policies?.timeoutSeconds || 120, 120)
      this.nodeTestMode = 'UPSTREAM_CHAIN'
      this.workflowDraftTest = true
      this.nodeTestInputJson = '{}'
      this.nodeTestResult = null
      this.nodeTestSchemaApplied = false
      this.nodeTestSchemaPromoted = !!target.outputSchemaOverride
      this.nodeTestInferredSchema = target.ui?.inferredOutputSchema?.schema || null
      this.nodeTestDialogOpen = true
    },
    async startTestRun() {
      if (!this.testInputValidation.valid) {
        this.$message.error(this.testInputValidation.message || '请先修正测试输入')
        return
      }
      this.testStarting = true
      try {
        const response = await startWorkflowExecution({
          definitionId: this.currentDefinition.id,
          workflowVersionId: this.currentDefinition.currentPublishedVersionId,
          input: this.testInput,
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
    async cancelExecution() {
      if (!this.debugExecution?.executionId || this.executionFinished || this.executionCancelling) return
      this.executionCancelling = true
      try {
        const response = await cancelWorkflowExecution(this.debugExecution.executionId)
        this.debugExecution = response.data
        this.stopPolling()
        await this.pollExecution()
        this.$message.success('运行已取消')
      } finally {
        this.executionCancelling = false
      }
    },
    closeExecutionDock() {
      this.stopPolling()
      this.debugExecution = null
      this.debugNodeRuns = []
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
      this.invalidateSelectedInferredSchema()
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
      this.invalidateSelectedInferredSchema()
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
    buildClassifierSourceGroups(nodeId) {
      const upstreamIds = new Set()
      const pending = [nodeId]
      while (pending.length) {
        const current = pending.shift()
        ;(this.definition.edges || []).filter(edge => edge.target === current)
          .forEach(edge => {
            if (edge.source === '__start__' || edge.source === nodeId || upstreamIds.has(edge.source)) return
            upstreamIds.add(edge.source)
            pending.push(edge.source)
          })
      }
      const groups = []
      const inputOptions = this.classifierSchemaOptions(
        this.definition.inputs || {type: 'object'}, '$.input', '完整流程输入')
      if (inputOptions.length) groups.push({id: '__input__', label: '流程输入', options: inputOptions})
      ;(this.definition.nodes || []).filter(node => upstreamIds.has(node.id)).forEach(node => {
        const descriptor = this.descriptors.find(item => item.type === node.type
          && item.handlerVersion === node.typeVersion)
        const schema = this.resolvedNodeSchemas[node.id]?.outputSchema
          || descriptor?.outputSchema || {type: 'object'}
        const options = this.classifierSchemaOptions(
          schema, `$.nodes.${node.id}.output`, '完整输出')
        if (options.length) groups.push({id: node.id, label: node.name, options})
      })
      return groups
    },
    classifierSchemaOptions(schema, baseExpression, rootLabel) {
      return buildClassifierSchemaOptions(schema, baseExpression, rootLabel)
    },
    buildConditionFieldGroups(sourceNodeId) {
      const upstreamIds = new Set()
      const pending = [sourceNodeId]
      while (pending.length) {
        const current = pending.shift()
        const incomingEdges = (this.definition.edges || [])
          .filter(edge => edge.target === current)
        incomingEdges.forEach(edge => {
          if (edge.source === '__start__' || edge.source === sourceNodeId
            || upstreamIds.has(edge.source)) return
          upstreamIds.add(edge.source)
          pending.push(edge.source)
        })
      }
      const groups = []
      const inputFields = this.conditionSchemaFields(
        this.definition.inputs, '$.input', '流程输入')
      if (inputFields.length) {
        groups.push({id: '__input__', label: '流程输入', fields: inputFields})
      }
      const upstreamNodes = (this.definition.nodes || [])
        .filter(node => upstreamIds.has(node.id))
      upstreamNodes.forEach(node => {
        const descriptor = this.descriptors.find(item => item.type === node.type
          && item.handlerVersion === node.typeVersion)
        const schema = this.resolvedNodeSchemas[node.id]?.outputSchema
          || descriptor?.outputSchema
        const fields = this.conditionSchemaFields(
          schema, `$.nodes.${node.id}.output`, node.name)
        if (fields.length) groups.push({id: node.id, label: node.name, fields})
      })
      return groups
    },
    conditionSchemaFields(schema, baseExpression, sourceLabel) {
      const fields = []
      const visit = (currentSchema, currentExpression, parentPath, depth) => {
        if (!currentSchema || depth > 5) return
        Object.entries(currentSchema.properties || {}).forEach(([key, property]) => {
          const expression = `${currentExpression}.${key}`
          const path = parentPath ? `${parentPath}.${key}` : key
          const rawType = Array.isArray(property?.type)
            ? property.type.find(type => type !== 'null')
            : property?.type
          if (['string', 'number', 'integer', 'boolean'].includes(rawType)) {
            fields.push({
              label: property?.title || path,
              expression,
              typeLabel: this.conditionTypeLabel(rawType),
              sourceLabel
            })
          }
          if (property?.properties) visit(property, expression, path, depth + 1)
        })
      }
      visit(schema, baseExpression, '', 1)
      return fields.slice(0, 100)
    },
    conditionTypeLabel(type) {
      return {
        string: '文本', number: '数字', integer: '整数', boolean: '布尔'
      }[type] || type
    },
    insertConditionField(expression) {
      if (!expression) return
      this.insertConditionToken(expression)
      this.$nextTick(() => {
        this.conditionFieldSelection = ''
      })
    },
    insertConditionToken(token) {
      if (!this.canEdit || !this.selectedEdge?.condition) return
      const input = this.$refs.conditionExpressionInput
      const textarea = input?.textarea
      const value = this.selectedEdge.condition.expression || ''
      const start = textarea?.selectionStart ?? value.length
      const end = textarea?.selectionEnd ?? start
      let insertion = token
      if (token.startsWith('$.') && start > 0 && !/[\s(!]/.test(value.charAt(start - 1))) {
        insertion = ` ${token}`
      }
      this.selectedEdge.condition.expression = `${value.slice(0, start)}${insertion}${value.slice(end)}`
      this.edgeChanged()
      this.$nextTick(() => {
        const target = this.$refs.conditionExpressionInput?.textarea
        if (!target) return
        const cursor = start + insertion.length - (token === '()' ? 1 : 0)
        target.focus()
        target.setSelectionRange(cursor, cursor)
      })
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
        if (canvasNode?.position) node.ui = {...(node.ui || {}), ...canvasNode.position}
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
    async saveDraft(options = {}) {
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
        await this.refreshResolvedNodeSchemas()
        this.dirty = false
        if (!options?.silent) this.$message.success('草稿已保存')
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
      if (!this.canPublish || this.publishing || this.validating || this.saving) return
      this.publishing = true
      try {
        const valid = await this.validate()
        if (!valid) return
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
        const contentUnchanged = this.diagnostics
          .some(item => item.code === 'PUBLISH_CONTENT_UNCHANGED')
        if (contentUnchanged) {
          this.$message.info(`当前内容已是版本 v${response.data.version.versionNo}，无需重复发布`)
        } else {
          this.$message.success(`已发布版本 v${response.data.version.versionNo}`)
        }
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
        this.inspectorCollapsed = false
        this.refreshCanvasLayout()
      }
    },
    markDirty() {
      if (this.canEdit) {
        this.dirty = true
        this.validationPassed = false
        const startNode = this.canvasNodes.find(item => item.id === '__start__')
        if (startNode) {
          startNode.data = {
            ...startNode.data,
            testable: this.canDebug && this.workflowDraftTestAvailability.available
          }
        }
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

.canvas-toolbar :deep(.el-button) {
  min-width: 34px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
}

.canvas-toolbar :deep(.el-button:hover),
.canvas-toolbar :deep(.el-button:focus-visible) {
  z-index: 1;
  border-color: color-mix(in srgb, var(--workflow-primary, var(--el-color-primary)) 42%, transparent);
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.canvas-toolbar :deep(.el-button:active) {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: #fff;
  background: var(--workflow-primary, var(--el-color-primary));
}

.canvas-toolbar :deep(.el-button .el-icon),
.canvas-toolbar :deep(.el-button .el-icon svg) {
  width: 16px;
  height: 16px;
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
  max-height: 320px;
  padding: 14px 16px 16px;
  overflow: auto;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-extra-light);
}

.diagnostic-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.diagnostic-header > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.diagnostic-header strong {
  font-size: 15px;
}

.diagnostic-header span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.diagnostic-list {
  display: grid;
  gap: 10px;
}

.diagnostic-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid var(--el-color-danger);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.diagnostic-item.is-warning {
  border-left-color: var(--el-color-warning);
}

.diagnostic-status {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  color: var(--el-color-danger);
  font-size: 18px;
}

.diagnostic-item.is-warning .diagnostic-status {
  color: var(--el-color-warning);
}

.diagnostic-content {
  min-width: 0;
}

.diagnostic-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.diagnostic-title-row > strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.diagnostic-location {
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.diagnostic-content p {
  margin: 7px 0 0;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.diagnostic-suggestion {
  margin-top: 7px;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.diagnostic-content code {
  display: block;
  margin-top: 7px;
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
/* Canvas-first layout: stable navigation, flexible graph, contextual bottom dock. */
.workbench-body,
.workbench-body--guided,
.workbench-body--mapping {
  grid-template-columns: 256px minmax(0, 1fr) 56px !important;
  overflow: hidden;
}

.node-palette {
  width: 256px;
  box-sizing: border-box;
}

.workspace-stage {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  overflow: hidden;
  background: var(--workflow-canvas, var(--el-bg-color-page));
}

.workspace-stage > .canvas-panel {
  min-height: 0;
  height: auto;
}

.context-rail {
  min-width: 0;
  padding: 10px 6px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 5px;
  border-left: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  background: var(--workflow-surface, var(--el-bg-color));
}

.context-rail button {
  min-height: 54px;
  padding: 5px 2px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.context-rail button .el-icon {
  font-size: 17px;
}

.context-rail button span {
  font-size: 10px;
  line-height: 1.15;
  white-space: nowrap;
}

.context-rail button:not(:disabled):hover,
.context-rail button:not(:disabled):focus-visible,
.context-rail button.active {
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 24%, transparent);
  color: var(--workflow-primary, #625bf6);
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
  outline: none;
}

.context-rail button.active {
  font-weight: 700;
}

.context-rail button:disabled {
  color: var(--workflow-text-tertiary, var(--el-text-color-placeholder));
  cursor: not-allowed;
  opacity: 0.5;
}

.context-rail-spacer {
  flex: 1;
}

.inspector-panel,
.inspector-panel--guided,
.inspector-panel--mapping {
  position: relative;
  min-width: 0;
  min-height: 44px;
  width: 100%;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-top: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-left: 0;
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: 0 -10px 28px rgb(42 34 110 / 5%);
}

.inspector-panel.is-collapsed {
  box-shadow: none;
}

.inspector-resize-handle {
  position: absolute;
  z-index: 9;
  top: -7px;
  left: 50%;
  width: 56px;
  height: 15px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: ns-resize;
  transform: translateX(-50%);
}

.inspector-resize-handle span {
  width: 30px;
  height: 4px;
  border-radius: 99px;
  background: var(--workflow-border-strong, var(--el-border-color));
}

.inspector-resize-handle:hover span,
.inspector-resize-handle:focus-visible span {
  background: var(--workflow-primary, #625bf6);
}

.inspector-collapsed-bar {
  width: 100%;
  height: 44px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 0;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface, var(--el-bg-color));
  cursor: pointer;
}

.inspector-collapsed-bar > span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 650;
}

.inspector-collapsed-bar .el-icon {
  color: var(--workflow-primary, #625bf6);
}

.inspector-collapsed-bar small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.inspector-collapsed-bar:hover,
.inspector-collapsed-bar:focus-visible {
  color: var(--workflow-primary, #625bf6);
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
  outline: none;
}

.inspector-heading {
  min-height: 62px;
  padding: 10px 18px;
  flex: 0 0 auto;
}

.inspector-heading.simple {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.inspector-tabs,
.inspector-tabs--guided,
.inspector-panel--mapping .inspector-tabs {
  min-height: 0;
  height: auto;
  flex: 1;
}

.inspector-tabs :deep(.el-tabs__header) {
  padding: 0 18px;
}

.inspector-tabs :deep(.el-tabs__content),
.inspector-tabs--guided :deep(.el-tabs__content),
.inspector-panel--mapping .inspector-tabs :deep(.el-tabs__content) {
  min-height: 0;
  height: calc(100% - 44px);
  padding: 12px 18px 18px;
  overflow: auto;
}

.integration-stepper {
  min-height: 62px;
  padding: 10px 28px;
  flex: 0 0 auto;
}

.api-config-tabs {
  margin: 0 28px 8px;
  flex: 0 0 auto;
}

.inspector-footer,
.inspector-panel--guided .inspector-footer,
.inspector-panel--mapping .inspector-footer {
  position: static;
  width: 100%;
  min-height: 52px;
  padding: 8px 18px;
  flex: 0 0 auto;
}

.inspector-panel > .edge-form {
  min-height: 0;
  max-width: 920px;
  width: 100%;
  margin: 0 auto;
  padding: 14px 18px 22px;
  overflow: auto;
}

@media (max-width: 1180px) {
  .workbench-body,
  .workbench-body--guided,
  .workbench-body--mapping {
    grid-template-columns: 232px minmax(0, 1fr) 54px !important;
  }

  .node-palette {
    width: 232px;
  }
}

@media (max-width: 960px) {
  .workbench-body,
  .workbench-body--guided,
  .workbench-body--mapping {
    grid-template-columns: minmax(0, 1fr) 52px !important;
  }

  .node-palette {
    display: none;
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
  grid-template-columns: minmax(260px, 1fr) auto;
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

.inspector-advanced {
  margin-top: 10px;
}

.node-prompt-editor {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--workflow-primary, #625bf6) 22%, var(--workflow-border, var(--el-border-color)));
  border-radius: 12px;
  background: color-mix(in srgb, var(--workflow-primary-soft, var(--el-color-primary-light-9)) 58%, var(--workflow-surface, var(--el-bg-color)));
}

.node-prompt-heading {
  margin-bottom: 10px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.node-prompt-heading strong,
.node-prompt-heading small,
.node-prompt-hint {
  display: block;
}

.node-prompt-heading strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 13px;
}

.node-prompt-heading small,
.node-prompt-hint {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
  line-height: 1.55;
}

.node-prompt-heading small {
  margin-top: 3px;
}

.node-prompt-hint {
  margin-top: 8px;
}

.node-prompt-editor :deep(.el-textarea__inner) {
  min-height: 126px !important;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
}

.inspector-advanced-hint {
  display: block;
  margin-top: 7px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
  line-height: 1.5;
}

.inspector-json-editor :deep(.el-textarea__inner) {
  min-height: 168px !important;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  font: 11px/1.65 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
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

.edge-field-help {
  width: 100%;
  margin-top: 6px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
  line-height: 1.5;
}

.condition-guide {
  margin: 2px 0 18px;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--workflow-primary, #625bf6) 24%, var(--workflow-border, #dfe3ee));
  border-radius: 10px;
  background: color-mix(in srgb, var(--workflow-primary-soft, #f0efff) 56%, var(--workflow-surface, #fff));
}

.condition-guide-heading {
  margin-bottom: 10px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.condition-guide-heading strong,
.condition-guide-heading small {
  display: block;
}

.condition-guide-heading strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 12px;
}

.condition-guide-heading small {
  margin-top: 4px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
  line-height: 1.5;
}

.condition-guide-heading .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  cursor: help;
}

.condition-field-select {
  width: 100%;
}

.condition-option-label {
  margin-right: 8px;
  font-weight: 650;
}

.condition-option-label + small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.condition-operator-bar {
  margin-top: 9px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.condition-operator-bar button {
  min-height: 26px;
  padding: 3px 8px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 6px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-surface, var(--el-bg-color));
  font-size: 10px;
  cursor: pointer;
}

.condition-operator-bar button:not(:disabled):hover,
.condition-operator-bar button:not(:disabled):focus-visible {
  border-color: var(--workflow-primary, #625bf6);
  color: var(--workflow-primary, #625bf6);
  outline: none;
}

.condition-operator-bar button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.condition-expression-item :deep(.el-form-item__content) {
  display: block;
}

.condition-syntax-help {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.condition-syntax-help code {
  max-width: 100%;
  padding: 3px 6px;
  overflow: hidden;
  border-radius: 4px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-muted, var(--el-fill-color-light));
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inspector-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 360px;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-top: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  background: var(--workflow-surface, var(--el-bg-color));
}

.node-test-trigger.el-button {
  --el-button-text-color: var(--workflow-primary, #625bf6);
  --el-button-bg-color: var(--workflow-primary-soft, #f0efff);
  --el-button-border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 34%, var(--workflow-border, #dfe3ee));
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: var(--workflow-primary, #625bf6);
  --el-button-hover-border-color: var(--workflow-primary, #625bf6);
  color: var(--workflow-primary, #625bf6) !important;
  background: var(--workflow-primary-soft, #f0efff) !important;
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 34%, var(--workflow-border, #dfe3ee)) !important;
  font-weight: 650;
}

.node-test-trigger.el-button:not(.is-disabled):hover {
  color: #fff !important;
  background: var(--workflow-primary, #625bf6) !important;
  border-color: var(--workflow-primary, #625bf6) !important;
}

.node-test-trigger.el-button.is-disabled {
  color: color-mix(in srgb, var(--workflow-primary, #625bf6) 62%, var(--workflow-text-secondary, #9097a6)) !important;
  background: color-mix(in srgb, var(--workflow-primary-soft, #f0efff) 72%, var(--workflow-surface, #fff)) !important;
  border-color: var(--workflow-border, #dfe3ee) !important;
  opacity: 1;
}

.node-test-trigger.el-button span,
.node-test-trigger.el-button .el-icon,
.node-delete-button.el-button span,
.node-delete-button.el-button .el-icon {
  color: inherit !important;
}

.node-delete-button.el-button {
  --el-button-text-color: #dc4c5c;
  --el-button-hover-text-color: #c93648;
  --el-button-hover-bg-color: rgb(239 82 97 / 10%);
  color: #dc4c5c !important;
  background: transparent !important;
  font-weight: 650;
}

.node-delete-button.el-button:hover {
  color: #c93648 !important;
  background: rgb(239 82 97 / 10%) !important;
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
  color: #fff !important;
  background: #5148dd !important;
  box-shadow: 3px 0 0 #8b85ff inset, 0 6px 16px rgb(81 72 221 / 22%) !important;
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

:global(body .workflow-resource-popper.el-popper .el-select-dropdown__item.is-selected .resource-option strong) {
  color: #fff !important;
}

:global(body .workflow-resource-popper.el-popper .el-select-dropdown__item.is-selected .resource-option small) {
  color: rgb(255 255 255 / 82%) !important;
  opacity: 1;
}

:global(html.dark .workflow-resource-popper.el-popper) {
  box-shadow: 0 20px 48px rgb(0 0 0 / 46%);
}

:global(html.dark .workflow-resource-popper .el-select-dropdown__item.is-hovering) {
  color: var(--el-text-color-primary) !important;
  background: rgb(129 140 248 / 12%) !important;
}

:global(html.dark .workflow-resource-popper .el-select-dropdown__item.is-selected) {
  color: #fff !important;
  background: #5148dd !important;
  box-shadow: 3px 0 0 #a5b4fc inset, 0 8px 18px rgb(0 0 0 / 28%) !important;
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
  width: min(720px, calc(100% - 700px));
  min-width: 520px;
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

.execution-state-icon {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 50%;
  font-size: 19px;
}

.execution-state-icon.is-running {
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 32%, transparent);
  color: var(--workflow-primary, #625bf6);
  background: var(--workflow-primary-soft, #eeecff);
}

.execution-state-icon.is-success {
  border-color: rgb(16 185 129 / 30%);
  color: #059669;
  background: rgb(16 185 129 / 12%);
}

.execution-state-icon.is-warning {
  border-color: rgb(245 158 11 / 32%);
  color: #d97706;
  background: rgb(245 158 11 / 12%);
}

.execution-state-icon.is-danger {
  border-color: rgb(239 68 68 / 30%);
  color: #dc2626;
  background: rgb(239 68 68 / 11%);
}

.execution-state-icon.is-neutral {
  border-color: var(--workflow-border-strong, var(--el-border-color));
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-light));
}

.execution-dock span,
.execution-dock code {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.execution-id {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.execution-progress :deep(.el-progress-bar__outer) {
  background: color-mix(in srgb, var(--workflow-primary, #625bf6) 12%, var(--workflow-muted, #eef0f6));
}

.execution-progress :deep(.el-progress-bar__inner) {
  background: var(--workflow-primary, #625bf6);
}

.execution-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-left: 4px;
  border-left: 1px solid var(--workflow-border, var(--el-border-color-lighter));
}

.execution-action.el-button,
.execution-close.el-button {
  margin-left: 0;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-weight: 600;
}

.execution-action.el-button {
  padding: 6px 8px;
}

.execution-action.el-button span {
  color: inherit;
  font-size: 12px;
}

.execution-action--cancel.el-button {
  color: #dc2626;
}

.execution-action.el-button:hover,
.execution-close.el-button:hover {
  color: var(--workflow-primary, #625bf6);
  background: var(--workflow-hover, var(--el-fill-color-light));
}

.execution-action--cancel.el-button:hover {
  color: #b91c1c;
  background: rgb(239 68 68 / 10%);
}

.execution-close.el-button {
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 8px;
  background: var(--workflow-surface, var(--el-bg-color));
  font-size: 16px;
}

.test-run-form {
  margin-top: 16px;
}

.node-test-form {
  margin-top: 18px;
}

.node-test-mode :deep(.el-radio-button__inner) {
  border-color: var(--workflow-border-strong, var(--el-border-color)) !important;
  color: var(--workflow-text, var(--el-text-color-primary)) !important;
  background: var(--workflow-surface, var(--el-bg-color)) !important;
  box-shadow: none !important;
}

.node-test-mode :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: var(--workflow-primary, #625bf6) !important;
  color: #fff !important;
  background: var(--workflow-primary, #625bf6) !important;
  box-shadow: -1px 0 0 0 var(--workflow-primary, #625bf6) !important;
}

.node-test-mode :deep(.el-radio-button.is-disabled .el-radio-button__inner) {
  color: var(--workflow-text-tertiary, var(--el-text-color-placeholder)) !important;
  background: var(--workflow-muted, var(--el-fill-color-light)) !important;
}

.node-test-unit {
  margin-left: 8px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
}

.node-test-result {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.node-test-result header,
.node-test-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.node-test-result header {
  margin-bottom: 12px;
}

.node-test-result header code {
  margin-left: auto;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.node-test-meta {
  margin: 12px 0;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 12px;
}

.node-test-diagnostics {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--el-color-warning);
  font-size: 12px;
}

.formal-schema-card {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 9px;
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
}

.formal-schema-heading,
.formal-schema-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.formal-schema-heading div,
.formal-schema-heading small {
  display: block;
}

.formal-schema-heading small,
.formal-schema-card p,
.formal-schema-actions small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
}

.formal-schema-card pre {
  max-height: 220px;
  margin: 10px 0;
  padding: 10px;
  overflow: auto;
  border-radius: 7px;
  background: var(--workflow-surface, var(--el-bg-color));
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.node-test-schema-action {
  margin-bottom: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px dashed var(--workflow-border-strong, var(--el-border-color));
  border-radius: 8px;
  background: var(--workflow-surface, var(--el-bg-color));
}

.node-test-schema-action strong,
.node-test-schema-action small {
  display: block;
}

.node-test-schema-action small {
  margin-top: 3px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
}

.node-test-output > span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 650;
}

.node-test-output pre {
  max-height: 280px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 8px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface, var(--el-bg-color));
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1360px) {
  .workbench-header {
    grid-template-columns: minmax(220px, 1fr) auto;
    padding-block: 8px;
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

<style scoped>
/* Final dock overrides stay last because this component retains legacy style layers. */
.workspace-stage > .inspector-panel,
.workspace-stage > .inspector-panel--guided,
.workspace-stage > .inspector-panel--mapping {
  position: relative;
  min-width: 0;
  min-height: 44px;
  width: 100%;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-top: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-left: 0;
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: 0 -10px 28px rgb(42 34 110 / 5%);
}

.workspace-stage > .inspector-panel.is-collapsed {
  box-shadow: none;
}

.workspace-stage .inspector-heading {
  min-height: 56px;
  padding: 8px 18px;
  box-sizing: border-box;
  flex: 0 0 auto;
}

.workspace-stage .inspector-heading.simple {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.workspace-stage .inspector-tabs,
.workspace-stage .inspector-tabs--guided,
.workspace-stage .inspector-panel--mapping .inspector-tabs {
  min-height: 0;
  height: auto;
  flex: 1;
}

.workspace-stage .inspector-tabs :deep(.el-tabs__content),
.workspace-stage .inspector-tabs--guided :deep(.el-tabs__content),
.workspace-stage .inspector-panel--mapping .inspector-tabs :deep(.el-tabs__content) {
  min-height: 0;
  height: calc(100% - 44px);
  padding: 9px 18px 16px;
  overflow: auto;
}

.workspace-stage .integration-stepper {
  min-height: 50px;
  padding: 6px 20px;
  box-sizing: border-box;
  gap: 10px;
  flex: 0 0 auto;
}

.workspace-stage .integration-stepper button {
  gap: 7px;
}

.workspace-stage .integration-stepper button > span {
  width: 25px;
  height: 25px;
  font-size: 11px;
}

.workspace-stage .integration-stepper button > strong {
  font-size: 13px;
}

.workspace-stage .api-config-tabs {
  margin: 0 20px 7px;
  flex: 0 0 auto;
}

.workspace-stage .inspector-footer,
.workspace-stage .inspector-panel--guided .inspector-footer,
.workspace-stage .inspector-panel--mapping .inspector-footer {
  position: static;
  width: 100%;
  min-height: 52px;
  padding: 8px 18px;
  flex: 0 0 auto;
}

.workspace-stage > .inspector-panel > .edge-form {
  min-height: 0;
  max-width: 920px;
  width: 100%;
  margin: 0 auto;
  padding: 14px 18px 22px;
  overflow: auto;
}

.transform-mapping-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 14px 16px;
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
}

.transform-mapping-summary > div {
  display: grid;
  gap: 4px;
}

.transform-mapping-summary small {
  color: var(--workflow-text-muted, var(--el-text-color-secondary));
}
</style>
