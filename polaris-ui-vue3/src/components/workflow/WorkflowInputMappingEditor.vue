<template>
  <section class="mapping-editor">
    <div v-if="recommendedSource" class="mapping-recommendation">
      <div class="recommendation-icon"><el-icon><MagicStick /></el-icon></div>
      <div class="recommendation-copy">
        <div class="recommendation-title">
          <strong>检测到上游节点输出</strong>
          <el-tag size="small" type="success" effect="plain">{{ recommendedSource.typeLabel }}</el-tag>
        </div>
        <p>推荐将「{{ recommendedSource.nodeName }}」的{{ recommendedSource.label }}绑定为当前节点输入。</p>
        <div class="recommendation-actions">
          <el-button type="primary" size="small" :disabled="disabled || recommendationApplied" @click="applyRecommendation">
            {{ recommendationApplied ? '已应用' : '一键应用' }}
          </el-button>
          <el-button size="small" text :disabled="disabled" @click="focusSourceTree">手动选择</el-button>
        </div>
      </div>
    </div>

    <header class="mapping-section-heading">
      <div>
        <strong>输入映射</strong>
        <small>从上游节点选择数据并绑定到当前节点字段</small>
      </div>
      <el-tooltip content="映射会在节点运行前解析，上游节点 ID 和表达式由系统自动维护。" placement="top">
        <el-icon class="mapping-help"><QuestionFilled /></el-icon>
      </el-tooltip>
    </header>

    <div class="mapping-workspace">
      <section class="source-browser">
        <div class="workspace-title">可选数据源</div>
        <el-input
          ref="sourceSearch"
          v-model="sourceKeyword"
          size="small"
          clearable
          prefix-icon="Search"
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
              <strong>{{ group.label }}</strong>
            </button>
            <div v-show="expandedSourceKeys.includes(group.id)" class="source-fields">
              <button
                v-for="field in group.fields"
                :key="field.expression"
                type="button"
                :class="['source-field', {active: pendingSource?.expression === field.expression}]"
                :style="{'--field-depth': field.depth}"
                :disabled="disabled"
                @click="selectSource(field)"
              >
                <el-icon><Document /></el-icon>
                <span class="source-field-copy">
                  <strong>{{ field.label }}</strong>
                  <small v-if="field.path">{{ field.path }}</small>
                </span>
                <span :class="['mapping-type', `mapping-type--${field.type}`]">{{ field.typeLabel }}</span>
              </button>
            </div>
          </div>
        </div>
        <div class="source-browser-hint">
          {{ pendingSource ? `已选择：${pendingSource.nodeName} / ${pendingSource.label}` : '点击字段后，在右侧选择要绑定的目标字段' }}
        </div>
      </section>

      <section class="target-browser">
        <div class="workspace-title">当前节点输入</div>
        <div v-if="!mappingRows.length && !addingTarget" class="target-empty">
          <el-icon><Connection /></el-icon>
          <strong>暂未配置输入</strong>
          <small>先从左侧选择数据，或新增一个目标字段。</small>
        </div>

        <div v-for="row in mappingRows" :key="row.key" class="target-row">
          <div class="target-row-heading">
            <el-input
              :model-value="row.key"
              size="small"
              :disabled="disabled"
              aria-label="当前节点字段"
              @change="renameTarget(row.key, $event)"
            />
            <el-button
              icon="Delete"
              text
              type="danger"
              :disabled="disabled"
              title="删除映射"
              @click="removeTarget(row.key)"
            />
          </div>
          <div class="target-binding">
            <span class="binding-value" :class="{empty: !row.source}">
              <el-icon><Connection /></el-icon>
              <span>{{ row.source || '尚未选择数据来源' }}</span>
            </span>
            <el-button
              circle
              size="small"
              icon="Plus"
              :disabled="disabled || !pendingSource"
              :title="pendingSource ? `绑定 ${pendingSource.label}` : '请先从左侧选择数据'"
              @click="bindPendingSource(row.key)"
            />
          </div>
        </div>

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

        <el-button class="add-target-button" plain icon="Plus" :disabled="disabled || addingTarget" @click="startAddingTarget">
          添加目标字段
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
        <small class="advanced-hint">支持 $.input、$.nodes.&lt;节点&gt;.output、$.env 和 $.execution。</small>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
import {
  ArrowDown,
  ArrowRight,
  ChatDotRound,
  Coin,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  Finished,
  Grid,
  MagicStick,
  Operation,
  QuestionFilled,
  Refresh,
  Share,
  Switch,
  Timer,
  User,
  VideoPlay
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
    Document,
    Finished,
    Grid,
    MagicStick,
    Operation,
    QuestionFilled,
    Refresh,
    Share,
    Switch,
    Timer,
    User,
    VideoPlay
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
    disabled: {
      type: Boolean,
      default: false
    },
    mappingError: {
      type: String,
      default: ''
    }
  },
  emits: ['update:modelValue', 'json-change'],
  data() {
    return {
      sourceKeyword: '',
      expandedSourceKeys: ['__start__'],
      pendingSource: null,
      addingTarget: false,
      newTargetName: '',
      advancedSections: [],
      advancedScrollTimer: null
    }
  },
  beforeUnmount() {
    clearTimeout(this.advancedScrollTimer)
  },
  computed: {
    upstreamNodeIds() {
      const parents = new Map()
      ;(this.definition.edges || []).forEach(edge => {
        if (!parents.has(edge.target)) parents.set(edge.target, [])
        parents.get(edge.target).push(edge.source)
      })
      const visited = new Set()
      const stack = [...(parents.get(this.selectedNode.id) || [])]
      while (stack.length) {
        const nodeId = stack.pop()
        if (!nodeId || visited.has(nodeId)) continue
        visited.add(nodeId)
        ;(parents.get(nodeId) || []).forEach(parentId => stack.push(parentId))
      }
      return visited
    },
    directUpstreamNodeIds() {
      return new Set((this.definition.edges || [])
        .filter(edge => edge.target === this.selectedNode.id)
        .map(edge => edge.source))
    },
    sourceGroups() {
      const groups = []
      if (this.upstreamNodeIds.has('__start__') || !this.upstreamNodeIds.size) {
        groups.push(this.createStartGroup())
      }
      ;(this.definition.nodes || [])
        .filter(node => this.upstreamNodeIds.has(node.id))
        .forEach(node => groups.push(this.createNodeGroup(node)))
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
    mappingRows() {
      return Object.entries(this.modelValue || {}).map(([key, binding]) => ({
        key,
        binding,
        source: this.bindingLabel(binding)
      }))
    },
    recommendedSource() {
      const directGroups = this.sourceGroups.filter(group =>
        group.id !== '__start__' && this.directUpstreamNodeIds.has(group.id))
      const group = directGroups[directGroups.length - 1] || this.sourceGroups.find(item => item.id !== '__start__')
      if (!group) return null
      return group.fields.find(field => field.path === 'body') || group.fields[0] || null
    },
    recommendationApplied() {
      if (!this.recommendedSource) return false
      return Object.values(this.modelValue || {}).some(binding =>
        binding?.expression === this.recommendedSource.expression)
    },
    canConfirmNewTarget() {
      const name = this.newTargetName.trim()
      return !!name && !!this.pendingSource && !Object.prototype.hasOwnProperty.call(this.modelValue || {}, name)
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
        this.expandedSourceKeys = ['__start__', ...directIds]
        this.pendingSource = null
        this.cancelNewTarget()
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
        fields: this.schemaFields(schema, '$.input', '流程输入', '开始')
      }
    },
    createNodeGroup(node) {
      const descriptor = this.descriptorFor(node)
      const schema = this.effectiveOutputSchema(node, descriptor?.outputSchema)
      return {
        id: node.id,
        label: node.name,
        type: node.type,
        start: false,
        fields: this.schemaFields(schema, `$.nodes.${node.id}.output`, '完整输出', node.name)
      }
    },
    effectiveOutputSchema(node, schema) {
      if (schema?.properties && Object.keys(schema.properties).length) return schema
      const presets = {
        http_get: {status: {type: 'integer'}, body: {type: 'object'}},
        http_request: {status: {type: 'integer'}, body: {type: 'object'}},
        database_query: {rowCount: {type: 'integer'}, rows: {type: 'array'}},
        llm: {text: {type: 'string'}},
        agent: {text: {type: 'string'}, agentCode: {type: 'string'}, agentName: {type: 'string'}},
        llm_classifier: {branch: {type: 'string'}, confidence: {type: 'number'}, summary: {type: 'string'}},
        knowledge_rag: {documents: {type: 'array'}}
      }
      const properties = presets[node.type]
      return properties ? {type: 'object', properties} : {type: 'object', properties: {}}
    },
    schemaFields(schema, baseExpression, rootLabel, nodeName) {
      const result = [{
        label: rootLabel,
        path: '',
        expression: baseExpression,
        type: this.normalizedType(schema?.type || 'object'),
        typeLabel: this.typeLabel(schema?.type || 'object'),
        depth: 0,
        nodeName
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
            nodeName
          })
          if (depth < 2 && property?.properties) visit(property, expression, path, depth + 1)
        })
      }
      visit(schema, baseExpression, '', 1)
      return result
    },
    normalizedType(type) {
      return ['object', 'array', 'string', 'number', 'integer', 'boolean'].includes(type)
        ? type : 'object'
    },
    typeLabel(type) {
      const labels = {
        object: 'Object', array: 'Array', string: 'String', number: 'Number',
        integer: 'Integer', boolean: 'Boolean'
      }
      return labels[type] || 'Object'
    },
    toggleSourceGroup(groupId) {
      this.expandedSourceKeys = this.expandedSourceKeys.includes(groupId)
        ? this.expandedSourceKeys.filter(id => id !== groupId)
        : [...this.expandedSourceKeys, groupId]
    },
    selectSource(source) {
      if (this.disabled) return
      this.pendingSource = source
      if (!this.mappingRows.length && !this.addingTarget) this.startAddingTarget()
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
      return expression
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
      this.newTargetName = this.suggestedTargetName()
      this.$nextTick(() => this.$refs.targetNameInput?.focus())
    },
    suggestedTargetName() {
      const schemaProperties = this.descriptorFor(this.selectedNode)?.inputSchema?.properties || {}
      const schemaTarget = Object.keys(schemaProperties)
        .find(key => !Object.prototype.hasOwnProperty.call(this.modelValue || {}, key))
      if (schemaTarget) return schemaTarget
      const base = this.selectedNode.type === 'database_query' ? 'parameters' : 'input'
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
      const targetKey = this.suggestedTargetName()
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

.mapping-workspace {
  min-height: 310px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  overflow: hidden;
  background: var(--workflow-surface, var(--el-bg-color));
}

.source-browser,
.target-browser {
  min-width: 0;
  padding: 12px;
}

.source-browser {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
}

.workspace-title {
  margin-bottom: 9px;
}

.source-tree {
  min-height: 190px;
  max-height: 250px;
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
  min-height: 34px;
  padding: 4px 5px 4px calc(25px + var(--field-depth, 0) * 12px);
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  border-radius: 7px;
  text-align: left;
}

.source-field:not(:disabled):hover,
.source-field.active {
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
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
  font-size: 11px;
  font-weight: 600;
}

.source-field-copy small {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 9px;
}

.mapping-type {
  padding: 2px 5px;
  border-radius: 5px;
  color: #5f64c9;
  background: #efefff;
  font-size: 9px;
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
  gap: 10px;
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
  padding-bottom: 10px;
  border-bottom: 1px solid var(--workflow-border, var(--el-border-color-lighter));
}

.target-row-heading {
  gap: 4px;
}

.target-row-heading :deep(.el-input__wrapper) {
  padding-left: 0;
  box-shadow: none;
  background: transparent;
}

.target-row-heading :deep(.el-input__inner) {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-weight: 650;
}

.target-binding {
  margin-top: 5px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
}

.binding-value {
  min-width: 0;
  height: 32px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--workflow-border-strong, var(--el-border-color));
  border-radius: 7px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  font-size: 10px;
}

.binding-value span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.binding-value.empty {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  border-style: dashed;
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

.add-target-button:hover,
.add-target-button:focus {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
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
  .mapping-workspace {
    grid-template-columns: 1fr;
  }

  .source-browser {
    border-right: 0;
    border-bottom: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  }
}
</style>
