<template>
  <section class="mapping-editor">
    <section v-if="isHttpNode" class="response-overview">
      <header class="response-overview-heading">
        <div>
          <strong>接口响应</strong>
          <small>当前接口运行后提供给下游节点的数据</small>
        </div>
        <el-tag class="response-source-tag" size="small" effect="plain">{{ currentOutputSource }}</el-tag>
      </header>
      <div class="response-field-list">
        <div
          v-for="field in currentOutputFields"
          :key="field.expression"
          class="response-field"
          :style="{'--field-depth': Math.max(field.depth - 1, 0)}"
        >
          <el-icon><Document /></el-icon>
          <span>
            <strong>{{ field.label }}</strong>
            <small>{{ field.path }}</small>
          </span>
          <em :class="['mapping-type', `mapping-type--${field.type}`]">{{ field.typeLabel }}</em>
        </div>
      </div>
      <div v-if="!hasDetailedResponseFields" class="response-overview-empty">
        <span>
          当前只知道响应包含 status 和 body，尚未获得 body 子字段。
          运行当前接口后会自动生成脱敏字段结构。
        </span>
        <el-button
          class="response-run-button"
          type="primary"
          size="small"
          :disabled="disabled"
          @click="$emit('request-node-test')"
        >运行当前接口获取字段</el-button>
      </div>
    </section>

    <header class="mapping-section-heading">
      <div>
        <strong>{{ isHttpNode ? '请求输入' : '输入映射' }}</strong>
        <small>{{ isHttpNode ? '可选：把流程或上游数据传给当前接口' : '查看上游返回字段；点击字段即可完成首次绑定' }}</small>
      </div>
      <div class="mapping-heading-actions">
        <el-button
          v-if="isHttpNode && !mappingRows.length"
          text
          size="small"
          @click="requestMappingExpanded = !requestMappingExpanded"
        >{{ requestMappingExpanded ? '收起' : '配置请求输入' }}</el-button>
        <el-tooltip content="映射会在节点运行前解析，上游节点 ID 和表达式由系统自动维护。" placement="top">
          <el-icon class="mapping-help"><QuestionFilled /></el-icon>
        </el-tooltip>
      </div>
    </header>

    <div v-if="mappingWorkspaceVisible && schemaDiagnostics.length" class="mapping-schema-diagnostics">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ schemaDiagnostics.join('；') }}</span>
    </div>

    <div v-if="!mappingWorkspaceVisible" class="request-input-empty">
      当前接口没有配置上游输入；如需动态查询参数或请求体，可展开后绑定。
    </div>

    <div v-else class="mapping-workspace">
      <section class="source-browser">
        <div class="workspace-title">可选数据源</div>
        <el-input
          ref="sourceSearch"
          v-model="sourceKeyword"
          size="small"
          clearable
          prefix-icon="Search"
          aria-label="搜索上游节点或字段"
          placeholder="搜索节点或字段"
        />
        <div class="source-tree">
          <div v-if="!filteredSourceGroups.length" class="source-empty">没有匹配的数据源</div>
          <div v-for="group in filteredSourceGroups" :key="group.id" class="source-group">
            <button type="button" class="source-group-heading" @click="toggleSourceGroup(group.id)">
              <el-icon><component :is="expandedSourceKeys.includes(group.id) ? 'ArrowDown' : 'ArrowRight'" /></el-icon>
              <span :class="['source-node-icon', {'source-node-icon--start': group.start}]">
                <el-icon><component :is="group.start ? 'VideoPlay' : nodeIcon(group.type)" /></el-icon>
              </span>
              <span class="source-group-copy">
                <strong>{{ group.label }}</strong>
                <small v-if="group.scopeLabel">{{ group.scopeLabel }}</small>
                <small v-if="group.schemaSource" class="schema-source">{{ group.schemaSource }}</small>
              </span>
            </button>
            <div v-show="expandedSourceKeys.includes(group.id)" class="source-fields">
              <button
                v-for="field in group.fields"
                :key="field.expression"
                type="button"
                :class="['source-field', {active: pendingSource?.expression === field.expression}]"
                :style="{'--field-depth': field.depth}"
                :disabled="disabled || field.selectable === false"
                :title="field.selectable === false ? '数组元素字段仅用于展示结构，请绑定整个数组' : ''"
                :draggable="!disabled && field.selectable !== false"
                @click="selectSource(field)"
                @dragstart="beginSourceDrag(field, $event)"
                @dragend="endSourceDrag"
              >
                <el-icon><Document /></el-icon>
                <span class="source-field-copy">
                  <strong>{{ field.label }}</strong>
                  <small v-if="field.meta || field.path">{{ field.meta || field.path }}</small>
                </span>
                <span :class="['mapping-type', `mapping-type--${field.type}`]">{{ field.typeLabel }}</span>
              </button>
            </div>
          </div>
        </div>
        <div class="source-browser-hint">
          {{ pendingSource ? `已选择：${pendingSource.nodeName} / ${pendingSource.label}` : '点击字段即可绑定；存在多个目标槽位时再选择目标' }}
        </div>
      </section>

      <section class="target-browser">
        <div class="workspace-title target-workspace-title">
          <span>当前节点输入</span>
          <small v-if="mappingRows.length">{{ mappingRows.length }} 项已配置</small>
        </div>
        <div v-if="!mappingRows.length && !addingTarget" class="target-empty">
          <el-icon><Connection /></el-icon>
          <strong>暂未配置输入</strong>
          <small>直接点击左侧上游字段，系统会自动创建输入并完成绑定。</small>
        </div>

        <div
          v-for="row in mappingRows"
          :key="row.key"
          :class="['target-row', {'is-unbound': !row.source}]"
          @dragover.prevent
          @drop="dropSourceOnTarget(row.key, $event)"
        >
          <div class="target-row-heading">
            <div class="target-field-copy">
              <el-input
                :model-value="row.key"
                size="small"
                :disabled="disabled || row.declared"
                aria-label="当前节点字段"
                @change="renameTarget(row.key, $event)"
              />
              <small v-if="row.declared">
                {{ row.title }} · {{ row.typeLabel }}<em v-if="row.required">必填</em>
              </small>
            </div>
            <el-button
              v-if="row.binding || !row.declared"
              class="target-remove-button"
              text
              type="danger"
              :disabled="disabled"
              :title="row.declared ? '清除绑定' : '删除映射'"
              @click="removeTarget(row.key)"
            ><el-icon><Delete /></el-icon></el-button>
          </div>
          <div class="target-binding">
            <span class="binding-value" :class="{empty: !row.source}">
              <el-icon><Connection /></el-icon>
              <span>{{ row.source || '尚未选择数据来源' }}</span>
            </span>
            <el-button
              class="target-bind-button"
              circle
              size="small"
              :disabled="disabled || !pendingSource"
              :title="pendingSource ? `绑定 ${pendingSource.label}` : '请先从左侧选择数据'"
              @click="bindPendingSource(row.key)"
            ><el-icon><Plus /></el-icon></el-button>
          </div>
          <small v-if="row.issue" class="target-row-warning">{{ row.issue }}</small>
        </div>

        <div
          v-if="draggingSource && !disabled"
          class="mapping-dropzone"
          @dragover.prevent
          @drop="dropSourceAsNewTarget"
        >拖到这里并创建新目标字段</div>

        <div v-if="addingTarget" class="target-row target-row--new">
          <el-input
            ref="targetNameInput"
            v-model="newTargetName"
            size="small"
            maxlength="64"
            placeholder="输入当前节点字段名"
            @keyup.enter="confirmNewTarget"
          />
          <div class="new-target-actions">
            <small>{{ pendingSource ? `将绑定：${pendingSource.label}` : '请先从左侧选择数据来源' }}</small>
            <el-button size="small" text @click="cancelNewTarget">取消</el-button>
            <el-button type="primary" size="small" :disabled="!canConfirmNewTarget" @click="confirmNewTarget">确认绑定</el-button>
          </div>
        </div>

        <el-button
          v-if="mappingRows.length || addingTarget"
          class="add-target-button"
          plain
          :disabled="disabled || addingTarget"
          @click="startAddingTarget"
        >
          <el-icon><Plus /></el-icon><span>高级：添加更多输入</span>
        </el-button>
      </section>
    </div>

    <section class="mapping-preview">
      <div class="preview-heading">
        <strong>输入预览</strong>
        <small>展示映射关系，实际值以运行结果为准</small>
      </div>
      <pre>{{ mappingPreview }}</pre>
    </section>

    <el-collapse
      ref="mappingAdvanced"
      v-model="advancedSections"
      class="mapping-advanced"
      @change="advancedSectionsChanged"
    >
      <el-collapse-item name="json">
        <template #title>
          <div class="advanced-title">
            <span><strong>高级设置 · 原始 JSON</strong><small>适合熟悉表达式的用户</small></span>
          </div>
        </template>
        <el-input
          :model-value="jsonValue"
          type="textarea"
          :rows="9"
          :disabled="disabled"
          spellcheck="false"
          placeholder='{"prompt":{"expression":"$.input.question"}}'
          @input="jsonChanged"
        />
        <div v-if="mappingError" class="field-error">{{ mappingError }}</div>
        <small class="advanced-hint">支持 $.input、$.nodes.&lt;节点&gt;.output、$.env、$.execution、$.loop 和 $.approval。</small>
      </el-collapse-item>
      <el-collapse-item name="manual-path">
        <template #title>
          <div class="advanced-title">
            <span><strong>高级设置 · 手动子路径</strong><small>Schema 未声明字段时使用</small></span>
          </div>
        </template>
        <div class="manual-path-editor">
          <el-input
            v-model="manualPathExpression"
            :disabled="disabled"
            placeholder="$.nodes.http_1.output.body.customField"
            spellcheck="false"
          />
          <el-input
            v-model="manualPathTarget"
            :disabled="disabled"
            maxlength="64"
            placeholder="当前节点目标字段名"
          />
          <el-button
            type="primary"
            plain
            :disabled="disabled || !canBindManualPath"
            @click="bindManualPath"
          >添加未验证路径</el-button>
          <small v-if="manualPathError" class="field-error">{{ manualPathError }}</small>
          <small v-else>该路径允许保存，但在正式 Schema 中不存在时会标记为“当前无法验证”。</small>
        </div>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
import {workflowUpstreamNodeIds} from './workflowClassifier'
import {
  ArrowDown,
  ArrowRight,
  ChatDotRound,
  Coin,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Delete,
  Document,
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
  User,
  VideoPlay,
  WarningFilled
} from '@element-plus/icons-vue'

export default {
  name: 'WorkflowInputMappingEditor',
  components: {
    ArrowDown,
    ArrowRight,
    ChatDotRound,
    Coin,
    Collection,
    Connection,
    Cpu,
    DataAnalysis,
    Delete,
    Document,
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
    User,
    VideoPlay,
    WarningFilled
  },
  props: {
    modelValue: {
      type: Object,
      default: () => ({})
    },
    jsonValue: {
      type: String,
      default: '{}'
    },
    definition: {
      type: Object,
      required: true
    },
    selectedNode: {
      type: Object,
      required: true
    },
    descriptors: {
      type: Array,
      default: () => []
    },
    resolvedNodeSchemas: {
      type: Object,
      default: () => ({})
    },
    disabled: {
      type: Boolean,
      default: false
    },
    mappingError: {
      type: String,
      default: ''
    }
  },
  emits: ['update:modelValue', 'json-change', 'request-node-test'],
  data() {
    return {
      sourceKeyword: '',
      expandedSourceKeys: ['__start__'],
      pendingSource: null,
      addingTarget: false,
      newTargetName: '',
      advancedSections: [],
      advancedScrollTimer: null,
      draggingSource: null,
      manualPathExpression: '',
      manualPathTarget: '',
      requestMappingExpanded: false
    }
  },
  beforeUnmount() {
    clearTimeout(this.advancedScrollTimer)
  },
  computed: {
    upstreamNodeIds() {
      return workflowUpstreamNodeIds(this.definition, this.selectedNode.id)
    },
    directUpstreamNodeIds() {
      return new Set((this.definition.edges || [])
        .filter(edge => edge.target === this.selectedNode.id
          && edge.targetPort !== 'loop-return')
        .map(edge => edge.source))
    },
    isHttpNode() {
      return ['http_get', 'http_request'].includes(this.selectedNode?.type)
    },
    currentOutputSchema() {
      return this.resolvedNodeSchemas[this.selectedNode.id]?.outputSchema
        || this.descriptorFor(this.selectedNode)?.outputSchema
        || {type: 'object', properties: {}}
    },
    currentOutputSource() {
      return this.resolvedNodeSchemas[this.selectedNode.id]?.source || '节点契约'
    },
    currentOutputFields() {
      return this.schemaFields(
        this.currentOutputSchema,
        `$.nodes.${this.selectedNode.id}.output`,
        '完整响应', this.selectedNode.name, this.currentOutputSource)
        .filter(field => field.path)
    },
    hasDetailedResponseFields() {
      return this.currentOutputFields.some(field =>
        field.path.startsWith('body.') || field.path.startsWith('body[]'))
    },
    mappingWorkspaceVisible() {
      return !this.isHttpNode || this.requestMappingExpanded || this.mappingRows.length > 0
    },
    sourceGroups() {
      const directGroups = []
      const indirectGroups = []
      ;(this.definition.nodes || [])
        .filter(node => this.upstreamNodeIds.has(node.id))
        .forEach(node => {
          const direct = this.directUpstreamNodeIds.has(node.id)
          const group = this.createNodeGroup(node, direct ? '直接上游' : '更早上游')
          ;(direct ? directGroups : indirectGroups).push(group)
        })
      const groups = [...directGroups, ...indirectGroups]
      if (this.upstreamNodeIds.has('__start__') || !this.upstreamNodeIds.size) {
        groups.push(this.createStartGroup())
      }
      groups.push(this.createSystemGroup())
      return groups
    },
    filteredSourceGroups() {
      const keyword = this.sourceKeyword.trim().toLowerCase()
      if (!keyword) return this.sourceGroups
      return this.sourceGroups.reduce((result, group) => {
        const groupMatched = `${group.label} ${group.id}`.toLowerCase().includes(keyword)
        const fields = groupMatched ? group.fields : group.fields.filter(field =>
          `${field.label} ${field.path} ${field.typeLabel}`.toLowerCase().includes(keyword))
        if (fields.length) result.push({...group, fields})
        return result
      }, [])
    },
    schemaDiagnostics() {
      return [...this.upstreamNodeIds]
        .flatMap(nodeId => this.resolvedNodeSchemas[nodeId]?.diagnostics || [])
        .filter((message, index, messages) => message && messages.indexOf(message) === index)
    },
    mappingRows() {
      const mapping = this.modelValue || {}
      const declaredKeys = new Set(this.declaredTargets.map(field => field.key))
      const declaredRows = this.declaredTargets
        .filter(field => field.required
          || Object.prototype.hasOwnProperty.call(mapping, field.key))
        .map(field => {
          const binding = mapping[field.key]
          return {
            ...field,
            binding,
            source: this.bindingLabel(binding),
            issue: this.mappingIssue(binding, field.type, field.required)
          }
        })
      const customRows = Object.entries(mapping)
        .filter(([key]) => !declaredKeys.has(key))
        .map(([key, binding]) => ({
          key,
          binding,
          source: this.bindingLabel(binding),
          issue: this.mappingIssue(binding),
          declared: false,
          required: false
        }))
      return [...declaredRows, ...customRows]
    },
    declaredTargets() {
      const schema = this.resolvedNodeSchemas[this.selectedNode.id]?.inputSchema
        || this.descriptorFor(this.selectedNode)?.inputSchema
      const required = new Set(Array.isArray(schema?.required) ? schema.required : [])
      return Object.entries(schema?.properties || {}).map(([key, property]) => ({
        key,
        title: property?.title || key,
        type: this.normalizedType(property?.type),
        typeLabel: this.typeLabel(property?.type),
        declared: true,
        required: required.has(key)
      }))
    },
    recommendedSource() {
      const target = this.recommendedTarget
      const candidates = this.sourceGroups
        .filter(group => group.nodeSource)
        .flatMap(group => group.fields
          .filter(field => field.selectable !== false)
          .map(field => ({
            field,
            score: this.recommendationScore(group, field, target)
          })))
        .filter(candidate => candidate.score >= 0)
        .sort((left, right) => right.score - left.score)
      return candidates[0]?.field || null
    },
    recommendedTarget() {
      return this.declaredTargets
        .find(field => !Object.prototype.hasOwnProperty.call(this.modelValue || {}, field.key)) || null
    },
    recommendationApplied() {
      if (!this.recommendedSource) return false
      return Object.values(this.modelValue || {}).some(binding =>
        binding?.expression === this.recommendedSource.expression)
    },
    canConfirmNewTarget() {
      const name = this.newTargetName.trim()
      return !!name && !!this.pendingSource
        && !Object.prototype.hasOwnProperty.call(this.modelValue || {}, name)
    },
    manualPathError() {
      const expression = this.manualPathExpression.trim()
      const target = this.manualPathTarget.trim()
      if (!expression && !target) return ''
      if (!/^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(target)) {
        return '目标字段名格式无效'
      }
      if (!/^\$\.(input(?:\.|$)|nodes\.[A-Za-z][A-Za-z0-9_.-]*\.output(?:\.|$)|env(?:\.|$)|execution(?:\.|$)|loop(?:\.|$)|approval(?:\.|$))/.test(expression)) {
        return '路径必须来自流程输入、可达上游节点或系统上下文'
      }
      const nodeMatch = expression.match(/^\$\.nodes\.([A-Za-z][A-Za-z0-9_.-]*)\.output/)
      if (nodeMatch && !this.upstreamNodeIds.has(nodeMatch[1])) {
        return '路径引用的节点不是当前节点的可达上游'
      }
      return ''
    },
    canBindManualPath() {
      return !!this.manualPathExpression.trim() && !!this.manualPathTarget.trim()
        && !this.manualPathError
        && !Object.prototype.hasOwnProperty.call(
          this.modelValue || {}, this.manualPathTarget.trim())
    },
    mappingPreview() {
      const preview = {}
      Object.entries(this.modelValue || {}).forEach(([key, binding]) => {
        if (binding && Object.prototype.hasOwnProperty.call(binding, 'value')) {
          preview[key] = binding.value
          return
        }
        preview[key] = this.bindingLabel(binding) || '未配置'
      })
      return JSON.stringify(preview, null, 2)
    }
  },
  watch: {
    selectedNode: {
      immediate: true,
      handler() {
        const directIds = [...this.directUpstreamNodeIds].filter(id => id !== '__start__')
        const startIsDirect = this.directUpstreamNodeIds.has('__start__')
        this.expandedSourceKeys = [...(startIsDirect ? ['__start__'] : []), ...directIds]
        this.pendingSource = null
        this.requestMappingExpanded = false
        this.cancelNewTarget()
        this.$nextTick(() => this.cleanupLegacyAutoMappings())
      }
    }
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
    descriptorFor(node) {
      return this.descriptors.find(item => item.type === node.type
        && item.handlerVersion === node.typeVersion) || null
    },
    createStartGroup() {
      const schema = this.definition.inputs || {type: 'object', properties: {}}
      return {
        id: '__start__',
        label: '开始',
        type: 'start',
        start: true,
        scopeLabel: '流程输入',
        nodeSource: false,
        fields: this.schemaFields(schema, '$.input', '流程输入', '开始')
      }
    },
    createNodeGroup(node, scopeLabel) {
      const descriptor = this.descriptorFor(node)
      const resolved = this.resolvedNodeSchemas[node.id]
      const schema = resolved?.outputSchema
        || descriptor?.outputSchema
        || {type: 'object', properties: {}}
      const schemaSource = resolved?.source || '节点契约'
      return {
        id: node.id,
        label: node.name,
        type: node.type,
        start: false,
        scopeLabel,
        schemaSource,
        nodeSource: true,
        fields: this.schemaFields(
          schema, `$.nodes.${node.id}.output`, '完整输出', node.name, schemaSource)
      }
    },
    createSystemGroup() {
      const fields = [
        this.sourceField('环境名称', 'name', '$.env.name', 'string', '系统上下文'),
        this.sourceField('是否生产环境', 'production', '$.env.production', 'boolean', '系统上下文'),
        this.sourceField('执行 ID', 'id', '$.execution.id', 'string', '系统上下文'),
        this.sourceField('工作流编码', 'workflowCode', '$.execution.workflowCode', 'string', '系统上下文'),
        this.sourceField('发布版本 ID', 'workflowVersionId', '$.execution.workflowVersionId', 'string', '系统上下文'),
        this.sourceField('发布版本号', 'versionNo', '$.execution.versionNo', 'integer', '系统上下文'),
        this.sourceField('根执行 ID', 'rootId', '$.execution.rootId', 'string', '系统上下文'),
        this.sourceField('父执行 ID', 'parentId', '$.execution.parentId', 'string', '系统上下文'),
        this.sourceField('执行深度', 'depth', '$.execution.depth', 'integer', '系统上下文'),
        this.sourceField('调用主体类型', 'principalType', '$.execution.principalType', 'string', '系统上下文')
      ]
      const loops = (this.definition.nodes || [])
        .filter(node => node.type === 'loop' && this.upstreamNodeIds.has(node.id))
      if (loops.length) {
        fields.push(this.sourceField(
          '当前分支路径', 'branchPath', '$.loop.branchPath', 'string', '系统上下文'))
        fields.push(
          this.sourceField('当前循环数据', 'current.item', '$.loop.current.item', 'any', '循环上下文'),
          this.sourceField('当前序号（从 1 开始）', 'current.number', '$.loop.current.number', 'integer', '循环上下文'),
          this.sourceField('数组下标（从 0 开始）', 'current.index', '$.loop.current.index', 'integer', '循环上下文'),
          this.sourceField('数据总数', 'current.total', '$.loop.current.total', 'integer', '循环上下文'),
          this.sourceField('是否第一项', 'current.first', '$.loop.current.first', 'boolean', '循环上下文'),
          this.sourceField('是否最后一项', 'current.last', '$.loop.current.last', 'boolean', '循环上下文'),
          this.sourceField('上一次执行结果', 'current.lastOutput', '$.loop.current.lastOutput', 'any', '循环上下文'),
          this.sourceField('已收集结果', 'current.results', '$.loop.current.results', 'array', '循环上下文')
        )
        loops.forEach(node => fields.push(this.sourceField(
          `${node.name}迭代次数`, node.id, `$.loop.${node.id}`, 'integer', '系统上下文')))
      }
      const hasApproval = (this.definition.nodes || [])
        .some(node => node.type === 'approval' && this.upstreamNodeIds.has(node.id))
      if (hasApproval) {
        fields.push(
          this.sourceField('审批状态', 'status', '$.approval.status', 'string', '系统上下文'),
          this.sourceField('审批实例 ID', 'approvalInstanceId', '$.approval.approvalInstanceId', 'string', '系统上下文'),
          this.sourceField('审批完成时间', 'finishedAt', '$.approval.finishedAt', 'integer', '系统上下文')
        )
      }
      return {
        id: '__system__',
        label: '高级 · 系统上下文',
        type: 'system',
        start: false,
        scopeLabel: '运行时',
        nodeSource: false,
        fields
      }
    },
    sourceField(label, path, expression, type, nodeName) {
      return {
        label,
        path,
        expression,
        type: this.normalizedType(type),
        typeLabel: this.typeLabel(type),
        depth: 1,
        nodeName
      }
    },
    schemaFields(schema, baseExpression, rootLabel, nodeName, schemaSource = '流程契约') {
      const result = [{
        label: rootLabel,
        path: '',
        expression: baseExpression,
        type: this.normalizedType(schema?.type || 'object'),
        typeLabel: this.typeLabel(schema?.type || 'object'),
        depth: 0,
        nodeName,
        schemaSource,
        meta: schemaSource
      }]
      const visit = (currentSchema, currentExpression, parentPath, depth) => {
        Object.entries(currentSchema?.properties || {}).forEach(([key, property]) => {
          const path = parentPath ? `${parentPath}.${key}` : key
          const expression = `${currentExpression}.${key}`
          result.push({
            label: property?.title || key,
            path,
            expression,
            type: this.normalizedType(property?.type),
            typeLabel: this.typeLabel(property?.type),
            depth,
            nodeName,
            schemaSource,
            meta: [path, schemaSource].filter(Boolean).join(' · ')
          })
          if (depth < 4 && property?.properties) visit(property, expression, path, depth + 1)
          if (depth < 4 && property?.items?.properties) {
            Object.entries(property.items.properties).forEach(([itemKey, itemProperty]) => {
              const itemPath = `${path}[].${itemKey}`
              result.push({
                label: itemProperty?.title || itemKey,
                path: itemPath,
                expression: `${expression}.__item__.${itemKey}`,
                type: this.normalizedType(itemProperty?.type),
                typeLabel: this.typeLabel(itemProperty?.type),
                depth: depth + 1,
                nodeName,
                schemaSource,
                selectable: false,
                meta: `${itemPath} · ${schemaSource} · 数组列`
              })
            })
          }
        })
      }
      visit(schema, baseExpression, '', 1)
      return result
    },
    normalizedType(type) {
      const schemaType = Array.isArray(type)
        ? type.find(item => item !== 'null')
        : type
      return ['any', 'object', 'array', 'string', 'number', 'integer', 'boolean'].includes(schemaType)
        ? schemaType : 'object'
    },
    typeLabel(type) {
      const labels = {
        any: '任意类型', object: 'Object', array: 'Array', string: 'String', number: 'Number',
        integer: 'Integer', boolean: 'Boolean'
      }
      return labels[this.normalizedType(type)] || 'Object'
    },
    toggleSourceGroup(groupId) {
      this.expandedSourceKeys = this.expandedSourceKeys.includes(groupId)
        ? this.expandedSourceKeys.filter(id => id !== groupId)
        : [...this.expandedSourceKeys, groupId]
    },
    selectSource(source) {
      if (this.disabled || source?.selectable === false) return
      this.pendingSource = source
      const unboundRequiredTargets = this.declaredTargets.filter(field => field.required
        && !Object.prototype.hasOwnProperty.call(this.modelValue || {}, field.key)
        && this.typesCompatible(source.type, field.type))
      if (unboundRequiredTargets.length === 1) {
        this.bindPendingSource(unboundRequiredTargets[0].key)
        return
      }
      if (!Object.keys(this.modelValue || {}).length) {
        const target = this.preferredAutoTarget(source)
        const targetKey = target?.key || 'input'
        this.emitMapping({[targetKey]: {expression: source.expression}})
        return
      }
    },
    preferredAutoTarget(source) {
      const compatible = this.declaredTargets.filter(field =>
        this.typesCompatible(source.type, field.type))
      const preferredKeys = this.selectedNode.type === 'knowledge_rag'
        ? ['query', 'prompt'] : ['prompt', 'query']
      return preferredKeys
        .map(key => compatible.find(field => field.key === key))
        .find(Boolean) || compatible[0] || null
    },
    cleanupLegacyAutoMappings() {
      if (this.disabled || !['llm', 'agent', 'llm_classifier', 'knowledge_rag']
        .includes(this.selectedNode?.type)) return
      const mapping = this.modelValue || {}
      if (!Object.prototype.hasOwnProperty.call(mapping, 'input')) return
      const legacyKeys = Object.keys(mapping).filter(key => /^input(?:[2-9]|[1-9][0-9]+)$/.test(key))
      if (!legacyKeys.length) return
      const cleaned = {...mapping}
      legacyKeys.forEach(key => delete cleaned[key])
      this.emitMapping(cleaned)
    },
    beginSourceDrag(source, event) {
      if (this.disabled || source?.selectable === false) {
        event?.preventDefault()
        return
      }
      this.draggingSource = source
      this.pendingSource = source
      if (event?.dataTransfer) {
        event.dataTransfer.effectAllowed = 'copy'
        event.dataTransfer.setData('text/plain', source.expression)
      }
    },
    endSourceDrag() {
      this.draggingSource = null
    },
    dropSourceOnTarget(targetKey, event) {
      event?.preventDefault()
      const source = this.draggingSource || this.pendingSource
      if (!source || this.disabled) return
      this.emitMapping({
        ...(this.modelValue || {}),
        [targetKey]: {expression: source.expression}
      })
      this.draggingSource = null
    },
    dropSourceAsNewTarget(event) {
      event?.preventDefault()
      const source = this.draggingSource || this.pendingSource
      if (!source || this.disabled) return
      const targetKey = this.preferredAutoTarget(source)?.key || this.suggestedTargetName(false)
      this.emitMapping({
        ...(this.modelValue || {}),
        [targetKey]: {expression: source.expression}
      })
      this.draggingSource = null
    },
    bindManualPath() {
      if (!this.canBindManualPath) return
      const target = this.manualPathTarget.trim()
      this.emitMapping({
        ...(this.modelValue || {}),
        [target]: {expression: this.manualPathExpression.trim()}
      })
      this.manualPathExpression = ''
      this.manualPathTarget = ''
    },
    bindingLabel(binding) {
      if (!binding || typeof binding !== 'object') return ''
      if (Object.prototype.hasOwnProperty.call(binding, 'value')) return '固定值'
      const expression = binding.expression
      if (!expression) return ''
      const source = this.sourceGroups.flatMap(group => group.fields)
        .find(field => field.expression === expression)
      if (source) return `${source.nodeName} / ${source.label}${source.path ? ` · ${source.path}` : ''}`
      if (expression.startsWith('$.env')) return `环境变量 · ${expression.slice(6) || '全部'}`
      if (expression.startsWith('$.execution')) return `执行信息 · ${expression.slice(12) || '全部'}`
      if (expression.startsWith('$.loop')) return `循环上下文 · ${expression.slice(7) || '全部'}`
      if (expression.startsWith('$.approval')) return `审批上下文 · ${expression.slice(11) || '全部'}`
      return expression
    },
    mappingIssue(binding, expectedType, required = false) {
      if (!binding) return required ? '必填字段尚未绑定' : ''
      if (Object.prototype.hasOwnProperty.call(binding, 'value')) {
        const actualType = this.valueType(binding.value)
        return expectedType && !this.typesCompatible(actualType, expectedType)
          ? `固定值类型 ${this.typeLabel(actualType)} 与目标 ${this.typeLabel(expectedType)} 不兼容`
          : ''
      }
      if (!binding.expression) return '映射尚未配置数据来源'
      const source = this.sourceGroups.flatMap(group => group.fields)
        .find(field => field.expression === binding.expression)
      if (!source) return '来源字段已失效或当前无法验证'
      if (expectedType && !this.typesCompatible(source.type, expectedType)) {
        return `来源 ${source.typeLabel} 与目标 ${this.typeLabel(expectedType)} 不兼容`
      }
      return ''
    },
    valueType(value) {
      if (Array.isArray(value)) return 'array'
      if (value !== null && typeof value === 'object') return 'object'
      if (typeof value === 'number') return Number.isInteger(value) ? 'integer' : 'number'
      return this.normalizedType(typeof value)
    },
    typesCompatible(sourceType, targetType) {
      if (!sourceType || !targetType || sourceType === 'any' || targetType === 'any'
        || sourceType === targetType) return true
      return ['number', 'integer'].includes(sourceType)
        && ['number', 'integer'].includes(targetType)
    },
    recommendationScore(group, field, target) {
      if (target && !this.typesCompatible(field.type, target.type)) return -1
      let score = this.directUpstreamNodeIds.has(group.id) ? 100 : 40
      if (target) {
        score += 40
        const targetName = target.key.toLowerCase().replace(/[^a-z0-9]/g, '')
        const sourceName = `${field.label}${field.path}`.toLowerCase().replace(/[^a-z0-9]/g, '')
        if (targetName && sourceName === targetName) score += 80
        else if (targetName && sourceName.includes(targetName)) score += 40
        const sourceKey = field.path.split('.').pop()?.toLowerCase() || ''
        if (['prompt', 'query', 'input'].includes(targetName)
          && ['text', 'content', 'message', 'query'].includes(sourceKey)) score += 60
      }
      if (field.path === 'body') score += 20
      if (!field.path) score -= 10
      return score
    },
    emitMapping(value) {
      this.$emit('update:modelValue', value)
    },
    bindPendingSource(targetKey) {
      if (!this.pendingSource || !targetKey) return
      this.emitMapping({
        ...(this.modelValue || {}),
        [targetKey]: {expression: this.pendingSource.expression}
      })
    },
    renameTarget(previousKey, nextValue) {
      if (this.declaredTargets.some(field => field.key === previousKey)) return
      const nextKey = String(nextValue || '').trim()
      if (!nextKey || nextKey === previousKey) return
      if (Object.prototype.hasOwnProperty.call(this.modelValue || {}, nextKey)) {
        this.$message.warning('当前节点字段名不能重复')
        return
      }
      const result = {}
      Object.entries(this.modelValue || {}).forEach(([key, binding]) => {
        result[key === previousKey ? nextKey : key] = binding
      })
      this.emitMapping(result)
    },
    removeTarget(targetKey) {
      const result = {...(this.modelValue || {})}
      delete result[targetKey]
      this.emitMapping(result)
    },
    startAddingTarget() {
      this.addingTarget = true
      this.newTargetName = this.suggestedTargetName(false)
      this.$nextTick(() => this.$refs.targetNameInput?.focus())
    },
    suggestedTargetName(preferDeclared = true) {
      if (preferDeclared) {
        const schemaTarget = this.declaredTargets
          .find(field => !Object.prototype.hasOwnProperty.call(this.modelValue || {}, field.key))?.key
        if (schemaTarget) return schemaTarget
      }
      const sourceKey = (this.pendingSource || this.recommendedSource)?.path?.split('.').pop()
      const base = sourceKey && /^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(sourceKey)
        ? sourceKey : 'input'
      if (!Object.prototype.hasOwnProperty.call(this.modelValue || {}, base)) return base
      let index = 2
      while (Object.prototype.hasOwnProperty.call(this.modelValue || {}, `${base}${index}`)) index += 1
      return `${base}${index}`
    },
    confirmNewTarget() {
      if (!this.canConfirmNewTarget) return
      const targetKey = this.newTargetName.trim()
      this.emitMapping({
        ...(this.modelValue || {}),
        [targetKey]: {expression: this.pendingSource.expression}
      })
      this.cancelNewTarget()
    },
    cancelNewTarget() {
      this.addingTarget = false
      this.newTargetName = ''
    },
    applyRecommendation() {
      if (!this.recommendedSource || this.disabled || this.recommendationApplied) return
      const targetKey = this.recommendedTarget?.key || this.suggestedTargetName()
      this.emitMapping({
        ...(this.modelValue || {}),
        [targetKey]: {expression: this.recommendedSource.expression}
      })
      this.pendingSource = this.recommendedSource
    },
    focusSourceTree() {
      if (this.recommendedSource) {
        const group = this.sourceGroups.find(item =>
          item.fields.some(field => field.expression === this.recommendedSource.expression))
        if (group && !this.expandedSourceKeys.includes(group.id)) {
          this.expandedSourceKeys = [...this.expandedSourceKeys, group.id]
        }
      }
      this.$nextTick(() => this.$refs.sourceSearch?.focus())
    },
    advancedSectionsChanged(activeNames) {
      if (!activeNames.includes('json')) return
      clearTimeout(this.advancedScrollTimer)
      this.advancedScrollTimer = setTimeout(() => {
        this.$refs.mappingAdvanced?.$el?.scrollIntoView({
          behavior: 'smooth',
          block: 'nearest'
        })
      }, 320)
    },
    jsonChanged(value) {
      this.$emit('json-change', value)
    }
  }
}
</script>

<style scoped>
.mapping-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
  color: var(--workflow-text, var(--el-text-color-primary));
}

.response-overview {
  padding: 13px;
  border: 1px solid color-mix(in srgb, var(--workflow-primary, #625bf6) 22%, var(--workflow-border, #dfe3ee));
  border-radius: 10px;
  background: color-mix(in srgb, var(--workflow-primary-soft, #f0efff) 32%, var(--workflow-surface, #fff));
}

.response-overview-heading,
.response-overview-empty,
.response-field {
  display: flex;
  align-items: center;
}

.response-overview-heading,
.response-overview-empty {
  justify-content: space-between;
  gap: 12px;
}

.response-overview-heading > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.response-overview-heading strong {
  font-size: 12px;
}

.response-overview-heading small,
.response-field small,
.response-overview-empty span {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.response-overview :deep(.response-source-tag.el-tag) {
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 35%, var(--workflow-border, #dfe3ee));
  color: var(--workflow-primary, #625bf6) !important;
  background: var(--workflow-surface, #fff) !important;
}

.response-overview :deep(.response-run-button.el-button) {
  --el-button-text-color: #fff;
  --el-button-bg-color: var(--workflow-primary, #625bf6);
  --el-button-border-color: var(--workflow-primary, #625bf6);
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: var(--workflow-primary-hover, #5149e8);
  --el-button-hover-border-color: var(--workflow-primary-hover, #5149e8);
  color: #fff !important;
}

.response-field-list {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.response-field {
  min-width: 0;
  min-height: 38px;
  padding: 6px 8px 6px calc(8px + var(--field-depth, 0) * 7px);
  gap: 7px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 7px;
  background: var(--workflow-surface, var(--el-bg-color));
}

.response-field > span {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
}

.response-field strong,
.response-field small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.response-field strong {
  font-size: 11px;
}

.response-field em {
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 9px;
  font-style: normal;
}

.response-overview-empty {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--workflow-border, var(--el-border-color));
}

.response-overview-empty span {
  line-height: 1.5;
}

.mapping-recommendation {
  padding: 14px;
  display: flex;
  gap: 11px;
  border: 1px solid color-mix(in srgb, var(--workflow-primary, #625bf6) 24%, var(--workflow-border, #dfe3ee));
  border-radius: 10px;
  background: color-mix(in srgb, var(--workflow-primary-soft, #f0efff) 52%, var(--workflow-surface, #fff));
}

.recommendation-icon {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-surface, var(--el-bg-color));
}

.recommendation-copy {
  min-width: 0;
  flex: 1;
}

.recommendation-title,
.recommendation-actions,
.mapping-section-heading,
.preview-heading,
.target-row-heading,
.new-target-actions {
  display: flex;
  align-items: center;
}

.recommendation-title,
.mapping-section-heading,
.preview-heading,
.target-row-heading,
.new-target-actions {
  justify-content: space-between;
}

.recommendation-title strong,
.mapping-section-heading strong,
.preview-heading strong,
.workspace-title {
  font-size: 12px;
  font-weight: 700;
}

.target-workspace-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.target-workspace-title small {
  padding: 3px 7px;
  border-radius: 999px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-light));
  font-size: 9px;
  font-weight: 500;
}

.recommendation-copy p {
  margin: 5px 0 9px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
  line-height: 1.55;
}

.recommendation-actions {
  gap: 4px;
  flex-wrap: wrap;
}

.recommendation-actions :deep(.el-button) {
  min-width: 78px;
  margin-left: 0;
  font-weight: 650;
  white-space: nowrap;
}

.recommendation-actions :deep(.el-button--primary) {
  --el-button-text-color: #fff;
  --el-button-bg-color: var(--workflow-primary, var(--el-color-primary));
  --el-button-border-color: var(--workflow-primary, var(--el-color-primary));
  --el-button-disabled-text-color: #fff;
  --el-button-disabled-bg-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 62%, var(--workflow-surface, #fff));
  --el-button-disabled-border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 52%, var(--workflow-border, #dfe3ee));
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
  --el-button-hover-border-color: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
  color: #fff !important;
}

.recommendation-actions :deep(.el-button--primary.is-disabled),
.recommendation-actions :deep(.el-button--primary.is-disabled:hover) {
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 46%, var(--workflow-border, #dfe3ee)) !important;
  color: #fff !important;
  background: color-mix(in srgb, var(--workflow-primary, #625bf6) 62%, var(--workflow-surface, #fff)) !important;
  opacity: 1;
}

.recommendation-actions :deep(.el-button.is-text) {
  color: var(--workflow-text, var(--el-text-color-primary)) !important;
  background: transparent !important;
}

.recommendation-actions :deep(.el-button.is-text:hover) {
  color: var(--workflow-primary, var(--el-color-primary)) !important;
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9)) !important;
}

.mapping-section-heading > div,
.advanced-title span {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.mapping-section-heading > .mapping-heading-actions {
  align-items: center;
  flex-direction: row;
  gap: 6px;
}

.mapping-section-heading small,
.preview-heading small,
.advanced-title small,
.advanced-hint,
.new-target-actions small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.mapping-help {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  cursor: help;
}

.mapping-schema-diagnostics {
  padding: 8px 10px;
  display: flex;
  align-items: flex-start;
  gap: 7px;
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 8px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  font-size: 10px;
  line-height: 1.5;
}

.mapping-schema-diagnostics .el-icon {
  margin-top: 2px;
  flex: 0 0 auto;
}

.request-input-empty {
  padding: 12px;
  border: 1px dashed var(--workflow-border, var(--el-border-color));
  border-radius: 8px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  font-size: 11px;
  line-height: 1.5;
}

.mapping-workspace {
  min-height: 310px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 12px;
  overflow: hidden;
  background: var(--workflow-surface, var(--el-bg-color));
}

.source-browser,
.target-browser {
  min-width: 0;
  padding: 12px;
}

.source-browser {
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  background: color-mix(in srgb, var(--workflow-primary-soft, #f0efff) 34%, var(--workflow-surface, #fff));
}

.workspace-title {
  margin-bottom: 12px;
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 13px;
}

.source-browser :deep(.el-input__wrapper) {
  min-height: 36px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 8px;
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: none;
}

.source-browser :deep(.el-input__wrapper.is-focus) {
  border-color: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 0 0 3px var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.source-tree {
  min-height: 0;
  max-height: none;
  flex: 1 1 0;
  margin: 8px -4px 0;
  overflow: auto;
}

.source-empty,
.target-empty {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
}

.source-empty {
  padding: 28px 8px;
  text-align: center;
}

.source-group-heading,
.source-field {
  width: 100%;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

.source-group-heading {
  min-height: 34px;
  padding: 4px;
  display: grid;
  grid-template-columns: 15px 25px minmax(0, 1fr);
  align-items: center;
  gap: 5px;
  text-align: left;
}

.source-group-heading strong {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-group-copy {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 5px;
}

.source-group-copy strong {
  min-width: 0;
  flex: 1;
}

.source-group-copy small {
  flex: 0 0 auto;
  padding: 1px 4px;
  border-radius: 4px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-surface, var(--el-bg-color));
  font-size: 8px;
  white-space: nowrap;
}

.source-group-copy .schema-source {
  color: #16805f;
  background: #e8f8f1;
}

.source-node-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.source-node-icon--start {
  color: #0a9f70;
  background: var(--el-color-success-light-9);
}

.source-field {
  min-height: 40px;
  padding: 5px 7px 5px calc(25px + var(--field-depth, 0) * 12px);
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: 8px;
  text-align: left;
}

.source-field:not(:disabled):hover,
.source-field.active {
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 22%, transparent);
}

.source-field.active {
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--workflow-primary, #625bf6) 28%, transparent) inset;
}

.source-field:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.source-field-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.source-field-copy strong,
.source-field-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-field-copy strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 12px;
  font-weight: 650;
}

.source-field-copy small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
}

.mapping-type {
  padding: 3px 7px;
  border-radius: 999px;
  color: #5f64c9;
  background: #efefff;
  font-size: 10px;
  line-height: 1.2;
}

.mapping-type--string {
  color: #16805f;
  background: #e8f8f1;
}

.mapping-type--number,
.mapping-type--integer {
  color: #b56a12;
  background: #fff4df;
}

.mapping-type--boolean {
  color: #b03d65;
  background: #fff0f5;
}

.source-browser-hint {
  min-height: 34px;
  margin: auto -12px -12px;
  padding: 9px 12px;
  border-top: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  background: var(--workflow-surface, var(--el-bg-color));
  font-size: 9px;
  line-height: 1.45;
}

.target-browser {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--workflow-surface, var(--el-bg-color));
}

.target-empty {
  min-height: 145px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  text-align: center;
}

.target-empty .el-icon {
  font-size: 22px;
}

.target-empty strong {
  color: var(--workflow-text, var(--el-text-color-primary));
}

.target-empty small {
  max-width: 180px;
  line-height: 1.5;
}

.target-row {
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--workflow-primary, #625bf6) 20%, var(--workflow-border, #dfe3ee));
  border-radius: 10px;
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  box-shadow: 0 3px 10px rgb(24 32 72 / 4%);
}

.target-row.is-unbound {
  border-color: var(--workflow-border, var(--el-border-color-lighter));
  background: color-mix(in srgb, var(--workflow-muted, #f5f6fa) 64%, var(--workflow-surface, #fff));
  box-shadow: none;
}

.target-row-heading {
  gap: 4px;
}

.target-field-copy {
  min-width: 0;
  flex: 1;
}

.target-field-copy > small {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
  line-height: 1.45;
}

.target-field-copy em {
  padding: 1px 4px;
  border-radius: 4px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  font-size: 8px;
  font-style: normal;
}

.target-row-heading :deep(.el-input__wrapper) {
  padding-left: 0;
  box-shadow: none;
  background: transparent;
}

.target-row-heading :deep(.el-input__inner) {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 14px;
  font-weight: 650;
}

.target-row-heading :deep(.el-input.is-disabled),
.target-row-heading :deep(.el-input.is-disabled .el-input__wrapper) {
  cursor: default;
  opacity: 1;
}

.target-row-heading :deep(.el-input.is-disabled .el-input__inner) {
  color: var(--workflow-text, var(--el-text-color-primary));
  -webkit-text-fill-color: var(--workflow-text, var(--el-text-color-primary));
  cursor: default;
  opacity: 1;
}

.target-remove-button.el-button {
  --el-button-text-color: #dc5a68;
  --el-button-hover-text-color: #c93648;
  --el-button-hover-bg-color: rgb(239 82 97 / 10%);
  width: 30px;
  height: 30px;
  padding: 0;
  color: #dc5a68 !important;
  border-radius: 7px;
}

.target-binding {
  margin-top: 8px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
}

.binding-value {
  min-width: 0;
  height: 38px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 8px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  font-size: 11px;
}

.binding-value span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.binding-value.empty {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  border-style: dashed;
  background: var(--workflow-surface, var(--el-bg-color));
}

.target-bind-button.el-button {
  --el-button-text-color: var(--workflow-primary, #625bf6);
  --el-button-bg-color: var(--workflow-primary-soft, #f0efff);
  --el-button-border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 30%, var(--workflow-border, #dfe3ee));
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: var(--workflow-primary, #625bf6);
  --el-button-hover-border-color: var(--workflow-primary, #625bf6);
  width: 38px;
  height: 38px;
  color: var(--workflow-primary, #625bf6) !important;
  background: var(--workflow-primary-soft, #f0efff) !important;
  border-color: color-mix(in srgb, var(--workflow-primary, #625bf6) 30%, var(--workflow-border, #dfe3ee)) !important;
}

.target-bind-button.el-button:not(.is-disabled):hover {
  color: #fff !important;
  background: var(--workflow-primary, #625bf6) !important;
  border-color: var(--workflow-primary, #625bf6) !important;
}

.target-bind-button.el-button.is-disabled {
  color: var(--workflow-text-secondary, #9097a6) !important;
  background: var(--workflow-muted, #f4f5f8) !important;
  border-color: var(--workflow-border, #dfe3ee) !important;
  opacity: 0.72;
}

.target-row-warning {
  margin-top: 5px;
  display: block;
  color: var(--el-color-warning-dark-2);
  font-size: 9px;
  line-height: 1.4;
}

.mapping-dropzone {
  padding: 12px;
  border: 1px dashed var(--workflow-primary, var(--el-color-primary));
  border-radius: 8px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
  font-size: 11px;
  text-align: center;
}

.target-row--new {
  padding: 10px;
  border: 1px dashed var(--workflow-primary, var(--el-color-primary));
  border-radius: 8px;
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.new-target-actions {
  margin-top: 7px;
  gap: 4px;
  flex-wrap: wrap;
}

.new-target-actions small {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.add-target-button {
  width: 100%;
  min-height: 36px;
  margin-top: auto;
  border-style: dashed;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  font-weight: 650;
  white-space: nowrap;
}

.add-target-button.el-button,
.add-target-button.el-button span,
.add-target-button.el-button .el-icon {
  color: var(--workflow-text, var(--el-text-color-primary)) !important;
}

.add-target-button:hover,
.add-target-button:focus {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.add-target-button.el-button:hover span,
.add-target-button.el-button:hover .el-icon,
.add-target-button.el-button:focus span,
.add-target-button.el-button:focus .el-icon {
  color: var(--workflow-primary, var(--el-color-primary)) !important;
}

.new-target-actions :deep(.el-button--primary) {
  --el-button-text-color: #fff;
  --el-button-bg-color: var(--workflow-primary, var(--el-color-primary));
  --el-button-border-color: var(--workflow-primary, var(--el-color-primary));
  color: #fff !important;
}

.mapping-preview {
  border-top: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  padding-top: 12px;
}

.preview-heading {
  margin-bottom: 7px;
}

.mapping-preview pre {
  min-height: 72px;
  max-height: 132px;
  margin: 0;
  padding: 10px 12px;
  overflow: auto;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 8px;
  color: #5c67a7;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  font: 10px/1.6 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.mapping-advanced {
  flex: 0 0 auto;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 9px;
  overflow: hidden;
  background: var(--workflow-surface, var(--el-bg-color));
}

.mapping-advanced :deep(.el-collapse-item__header) {
  min-height: 66px;
  height: auto;
  padding: 10px 12px;
  align-items: center;
  border: 0;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  line-height: 1.35;
}

.mapping-advanced :deep(.el-collapse-item__wrap) {
  border: 0;
  background: var(--workflow-surface, var(--el-bg-color));
}

.mapping-advanced :deep(.el-collapse-item__content) {
  padding: 14px;
}

.mapping-advanced :deep(.el-collapse-item__arrow) {
  flex: 0 0 auto;
  margin-left: 12px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
}

.mapping-advanced :deep(.el-textarea__inner) {
  min-height: 180px !important;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  font: 11px/1.65 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.advanced-title {
  min-width: 0;
  width: 100%;
  padding: 2px 0;
  text-align: left;
}

.advanced-title strong {
  display: block;
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 12px;
  line-height: 1.45;
}

.advanced-title small {
  display: block;
  line-height: 1.45;
}

.advanced-hint {
  display: block;
  margin-top: 7px;
  line-height: 1.5;
}

.manual-path-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(150px, 0.45fr) auto;
  align-items: start;
  gap: 8px;
}

.manual-path-editor > small {
  grid-column: 1 / -1;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
}

.field-error {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 11px;
}

:global(html.dark .mapping-type) {
  color: #c7c9ff;
  background: rgb(99 102 241 / 18%);
}

:global(html.dark .mapping-recommendation) {
  border-color: rgb(129 140 248 / 32%);
  background: color-mix(in srgb, #625bf6 13%, var(--el-bg-color));
}

:global(html.dark .recommendation-icon) {
  background: var(--el-fill-color-dark);
}

:global(html.dark .mapping-type--string) {
  color: #86efc7;
  background: rgb(16 185 129 / 16%);
}

:global(html.dark .mapping-type--number),
:global(html.dark .mapping-type--integer) {
  color: #fbd38d;
  background: rgb(245 158 11 / 16%);
}

:global(html.dark .mapping-type--boolean) {
  color: #f9a8c2;
  background: rgb(236 72 153 / 16%);
}

@media (max-width: 1280px) {
  .response-field-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .response-overview-empty {
    align-items: flex-start;
    flex-direction: column;
  }

  .mapping-workspace {
    grid-template-columns: 1fr;
  }

  .source-browser {
    border-right: 0;
    border-bottom: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  }

  .source-tree {
    min-height: 240px;
    max-height: 360px;
    flex: 0 1 auto;
  }

  .manual-path-editor {
    grid-template-columns: 1fr;
  }

  .manual-path-editor > small {
    grid-column: auto;
  }
}
</style>
