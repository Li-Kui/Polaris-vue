<template>
  <section class="transform-editor">
    <header class="transform-heading">
      <div>
        <strong>整理数据</strong>
        <small>选择一份上游数据，再定义希望输出的字段</small>
      </div>
      <el-tag v-if="rules.length" type="success" effect="plain">{{ rules.length }} 个输出字段</el-tag>
    </header>

    <div class="transform-mode" role="radiogroup" aria-label="整理方式">
      <button
        type="button"
        :class="{active: mode === 'OBJECT_MAP'}"
        :disabled="disabled"
        @click="changeMode('OBJECT_MAP')"
      >
        <strong>整理一个对象</strong>
        <small>从对象中挑选、重命名和转换字段</small>
      </button>
      <button
        type="button"
        :class="{active: mode === 'ARRAY_MAP'}"
        :disabled="disabled"
        @click="changeMode('ARRAY_MAP')"
      >
        <strong>逐项整理数组</strong>
        <small>对数组中的每一项应用同一套规则</small>
      </button>
    </div>

    <section class="transform-source-card">
      <div class="transform-section-title">
        <span><strong>1. 选择数据来源</strong><small>{{ sourceHelp }}</small></span>
        <el-tag v-if="selectedSource" size="small" effect="plain">{{ selectedSource.typeLabel }}</el-tag>
      </div>
      <el-select
        :model-value="sourceExpression"
        filterable
        clearable
        :disabled="disabled"
        :placeholder="mode === 'ARRAY_MAP' ? '选择一个上游数组' : '选择一个上游对象'"
        style="width: 100%"
        @change="sourceChanged"
      >
        <el-option-group
          v-for="group in compatibleSourceGroups"
          :key="group.id"
          :label="group.label"
        >
          <el-option
            v-for="option in group.options"
            :key="option.expression"
            :label="option.label"
            :value="option.expression"
          >
            <div class="source-option">
              <span>{{ option.label }}</span>
              <small>{{ option.path || '完整数据' }} · {{ option.typeLabel }}</small>
            </div>
          </el-option>
        </el-option-group>
      </el-select>
      <p v-if="sourceExpression && !selectedSource" class="transform-warning">
        当前来源已失效或暂时无法从上游结构中验证，请重新选择。
      </p>
    </section>

    <section class="transform-rules-card">
      <div class="transform-section-title transform-rules-heading">
        <span>
          <strong>2. 定义输出字段</strong>
          <small>{{ mode === 'ARRAY_MAP' ? '下面的规则会应用到数组中的每一项，并继续识别内部数组' : '对象和数组可以任意嵌套，系统会自动保留层级' }}</small>
        </span>
        <div class="transform-actions">
          <el-dropdown
            split-button
            size="small"
            :disabled="disabled || !availableFields.length || rules.length >= 200"
            trigger="click"
            @click="addAllTopLevelFields"
            @command="autoAddFields"
          >
            自动添加字段
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="TOP_LEVEL">添加一级字段（保留完整结构）</el-dropdown-item>
                <el-dropdown-item command="LEAVES">展开全部末级字段（可分别处理）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown :disabled="disabled || !selectedSource || rules.length >= 200" trigger="click" @command="addRule">
            <el-button type="primary" size="small" :disabled="disabled || !selectedSource || rules.length >= 200">
              添加字段<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="COPY">来自上游字段</el-dropdown-item>
                <el-dropdown-item command="CONSTANT">固定值</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div v-if="!selectedSource" class="transform-empty">
        <el-icon><Connection /></el-icon>
        <strong>先选择数据来源</strong>
        <small>系统会根据上游结构列出可选字段。</small>
      </div>
      <div v-else-if="!rules.length" class="transform-empty">
        <el-icon><Operation /></el-icon>
        <strong>尚未添加输出字段</strong>
        <small>可一键添加已有字段，也可以逐个添加并设置转换方式。</small>
      </div>

      <article v-for="(rule, index) in rules" :key="index" class="transform-rule">
        <div class="rule-main-grid">
          <label>
            <span>输出字段</span>
            <el-input
              :model-value="rule.targetPath"
              :disabled="disabled"
              placeholder="例如 user.name 或 users[].name"
              @input="updateRule(index, {targetPath: $event})"
            />
          </label>
          <label v-if="rule.operation !== 'CONSTANT'">
            <span>来源字段</span>
            <el-select
              :model-value="rule.sourcePath || ''"
              filterable
              :disabled="disabled"
              placeholder="选择字段"
              style="width: 100%"
              @change="sourceFieldChanged(index, $event)"
            >
              <el-option label="整个对象 / 当前项" value="" />
              <el-option
                v-for="field in availableFields"
                :key="field.path"
                :label="`${field.path} · ${field.typeLabel}`"
                :value="field.path"
              />
            </el-select>
          </label>
          <label>
            <span>处理方式</span>
            <el-select
              :model-value="rule.operation"
              :disabled="disabled"
              style="width: 100%"
              @change="operationChanged(index, $event)"
            >
              <el-option
                v-for="option in operationOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>
          <el-button
            class="rule-delete"
            text
            type="danger"
            :disabled="disabled"
            title="删除字段"
            aria-label="删除字段"
            @click="removeRule(index)"
          ><el-icon><Delete /></el-icon></el-button>
        </div>

        <div v-if="rule.operation === 'CONSTANT'" class="rule-constant-grid">
          <label>
            <span>固定值类型</span>
            <el-select
              :model-value="rule.resultType || 'string'"
              :disabled="disabled"
              style="width: 100%"
              @change="constantTypeChanged(index, $event)"
            >
              <el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" />
            </el-select>
          </label>
          <label>
            <span>固定值</span>
            <el-switch
              v-if="rule.resultType === 'boolean'"
              :model-value="!!rule.value"
              :disabled="disabled"
              @change="updateRule(index, {value: $event})"
            />
            <el-input
              v-else
              :model-value="displayValue(rule.value, rule.resultType)"
              :disabled="disabled"
              :placeholder="constantPlaceholder(rule.resultType)"
              @change="constantValueChanged(index, $event)"
            />
          </label>
        </div>

        <div class="rule-summary">
          <span :class="['mapping-type', `mapping-type--${ruleResultType(rule)}`]">
            {{ typeLabel(ruleResultType(rule)) }}
          </span>
          <span>{{ ruleSummary(rule) }}</span>
          <button type="button" :disabled="disabled" @click="toggleAdvanced(index)">
            {{ advancedRules.includes(index) ? '收起选项' : '默认值与异常处理' }}
            <el-icon><component :is="advancedRules.includes(index) ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
          </button>
        </div>

        <div v-if="advancedRules.includes(index)" class="rule-advanced">
          <label>
            <span>何时使用默认值</span>
            <el-select
              :model-value="rule.defaultWhen || 'NEVER'"
              :disabled="disabled"
              style="width: 100%"
              @change="updateRule(index, {defaultWhen: $event})"
            >
              <el-option label="不使用" value="NEVER" />
              <el-option label="字段不存在时" value="MISSING" />
              <el-option label="值为 null 时" value="NULL" />
              <el-option label="文本为空时" value="BLANK" />
              <el-option label="null 或空文本时" value="NULL_OR_BLANK" />
            </el-select>
          </label>
          <label v-if="(rule.defaultWhen && rule.defaultWhen !== 'NEVER') || rule.onError === 'DEFAULT'">
            <span>默认值</span>
            <el-input
              :model-value="displayValue(rule.defaultValue, ruleResultType(rule))"
              :disabled="disabled"
              placeholder="输入默认值"
              @change="defaultValueChanged(index, $event)"
            />
          </label>
          <label>
            <span>转换失败时</span>
            <el-select
              :model-value="rule.onError || 'FAIL'"
              :disabled="disabled"
              style="width: 100%"
              @change="updateRule(index, {onError: $event})"
            >
              <el-option label="停止并提示具体字段" value="FAIL" />
              <el-option label="输出 null" value="NULL" />
              <el-option label="使用默认值" value="DEFAULT" />
              <el-option label="保留原值" value="KEEP" />
            </el-select>
          </label>
          <label class="rule-required">
            <span>字段要求</span>
            <el-checkbox
              :model-value="!!rule.required"
              :disabled="disabled"
              @change="updateRule(index, {required: $event})"
            >来源必须存在</el-checkbox>
          </label>
          <label v-if="rule.operation === 'ARRAY_JOIN'">
            <span>连接符</span>
            <el-input
              :model-value="rule.separator || ','"
              :disabled="disabled"
              @input="updateRule(index, {separator: $event})"
            />
          </label>
        </div>
        <p v-if="ruleIssue(rule, index)" class="transform-warning">{{ ruleIssue(rule, index) }}</p>
      </article>
    </section>

    <section class="transform-output-card">
      <div class="transform-section-title">
        <span><strong>输出结构预览</strong><small>会自动成为下游节点可选字段，无需再手写 Schema</small></span>
      </div>
      <pre>{{ outputPreview }}</pre>
    </section>

    <details class="transform-advanced">
      <summary>高级选项</summary>
      <div class="transform-advanced-content">
        <label>
          <span>保留没有配置的原字段</span>
          <el-switch
            :model-value="!!config.preserveUnmapped"
            :disabled="disabled"
            @change="updateConfig({preserveUnmapped: $event})"
          />
          <small>开启后，整理结果会同时保留来源对象中的其他字段。</small>
        </label>
        <label>
          <span>任一数组最多处理</span>
          <el-input-number
            :model-value="config.maxItems || 1000"
            :min="1"
            :max="10000"
            :disabled="disabled"
            controls-position="right"
            @change="updateConfig({maxItems: $event})"
          />
          <small>顶层或任意嵌套数组超过数量时停止，避免意外处理超大数据。</small>
        </label>
      </div>
    </details>
  </section>
</template>

<script>
import {ArrowDown, ArrowUp, Connection, Delete, Operation} from '@element-plus/icons-vue'

export default {
  name: 'WorkflowTransformEditor',
  components: {ArrowDown, ArrowUp, Connection, Delete, Operation},
  props: {
    config: {type: Object, default: () => ({})},
    inputMapping: {type: Object, default: () => ({})},
    definition: {type: Object, required: true},
    selectedNode: {type: Object, required: true},
    descriptors: {type: Array, default: () => []},
    resolvedNodeSchemas: {type: Object, default: () => ({})},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config', 'update:inputMapping'],
  data() {
    return {
      advancedRules: [],
      operationOptions: [
        {label: '直接使用', value: 'COPY'},
        {label: '固定值', value: 'CONSTANT'},
        {label: '转为文本', value: 'TO_STRING'},
        {label: '转为整数', value: 'TO_INTEGER'},
        {label: '转为数字', value: 'TO_NUMBER'},
        {label: '转为是/否', value: 'TO_BOOLEAN'},
        {label: '去除首尾空格', value: 'TRIM'},
        {label: '转为大写', value: 'UPPERCASE'},
        {label: '转为小写', value: 'LOWERCASE'},
        {label: '数组合并为文本', value: 'ARRAY_JOIN'},
        {label: '统计数量', value: 'ARRAY_LENGTH'}
      ],
      valueTypes: [
        {label: '文本', value: 'string'},
        {label: '整数', value: 'integer'},
        {label: '数字', value: 'number'},
        {label: '是/否', value: 'boolean'},
        {label: '对象（JSON）', value: 'object'},
        {label: '数组（JSON）', value: 'array'},
        {label: '空值', value: 'null'}
      ]
    }
  },
  computed: {
    mode() {
      return this.config.mode || 'OBJECT_MAP'
    },
    rules() {
      return Array.isArray(this.config.rules) ? this.config.rules : []
    },
    sourceExpression() {
      return this.inputMapping?.source?.expression || ''
    },
    upstreamNodeIds() {
      const parents = new Map()
      ;(this.definition.edges || []).forEach(edge => {
        if (!parents.has(edge.target)) parents.set(edge.target, [])
        parents.get(edge.target).push(edge.source)
      })
      const visited = new Set()
      const stack = [...(parents.get(this.selectedNode.id) || [])]
      while (stack.length) {
        const id = stack.pop()
        if (!id || visited.has(id)) continue
        visited.add(id)
        ;(parents.get(id) || []).forEach(parent => stack.push(parent))
      }
      return visited
    },
    sourceGroups() {
      const groups = []
      if (this.upstreamNodeIds.has('__start__') || !this.upstreamNodeIds.size) {
        groups.push({
          id: '__start__',
          label: '开始 · 流程输入',
          options: this.containerOptions(this.definition.inputs, '$.input', '流程输入')
        })
      }
      ;(this.definition.nodes || [])
        .filter(node => this.upstreamNodeIds.has(node.id))
        .forEach(node => {
          const descriptor = this.descriptors.find(item => item.type === node.type
            && item.handlerVersion === node.typeVersion)
          const schema = this.resolvedNodeSchemas[node.id]?.outputSchema
            || descriptor?.outputSchema
          groups.unshift({
            id: node.id,
            label: `${node.name}${this.directUpstreamIds.has(node.id) ? ' · 直接上游' : ' · 更早上游'}`,
            options: this.containerOptions(schema, `$.nodes.${node.id}.output`, '完整输出')
          })
        })
      return groups
    },
    directUpstreamIds() {
      return new Set((this.definition.edges || [])
        .filter(edge => edge.target === this.selectedNode.id)
        .map(edge => edge.source))
    },
    compatibleSourceGroups() {
      const expected = this.mode === 'ARRAY_MAP' ? 'array' : 'object'
      return this.sourceGroups
        .map(group => ({...group, options: group.options.filter(option => option.type === expected)}))
        .filter(group => group.options.length)
    },
    selectedSource() {
      return this.sourceGroups.flatMap(group => group.options)
        .find(option => option.expression === this.sourceExpression) || null
    },
    sourceHelp() {
      return this.mode === 'ARRAY_MAP'
        ? '只显示可达上游中的数组；每项仍可以是对象、数组或基础值'
        : '只显示可达上游中的对象，技术路径由系统自动维护'
    },
    sourceItemSchema() {
      if (!this.selectedSource) return null
      return this.mode === 'ARRAY_MAP'
        ? this.selectedSource.schema?.items || {}
        : this.selectedSource.schema
    },
    availableFields() {
      return this.relativeFields(this.sourceItemSchema)
    },
    outputPreview() {
      const root = {}
      this.rules.forEach(rule => {
        if (!this.validTargetPath(rule.targetPath)) return
        const previewValue = rule.operation === 'CONSTANT'
          ? rule.value
          : (rule.operation === 'COPY' && ['object', 'array'].includes(this.ruleResultType(rule))
              ? this.schemaPreview(rule.resultSchema)
              : `<${this.typeLabel(this.ruleResultType(rule))}>`)
        this.setPreviewPath(root, this.transformPathTokens(rule.targetPath),
          previewValue)
      })
      return JSON.stringify(this.mode === 'ARRAY_MAP' ? [root] : root, null, 2)
    }
  },
  methods: {
    cloneSchema(schema) {
      return schema && typeof schema === 'object'
        ? JSON.parse(JSON.stringify(schema)) : undefined
    },
    normalizedType(type) {
      const normalized = Array.isArray(type) ? type.find(value => value !== 'null') : type
      return ['object', 'array', 'string', 'number', 'integer', 'boolean', 'null']
        .includes(normalized) ? normalized : 'object'
    },
    typeLabel(type) {
      return ({
        object: '对象', array: '数组', string: '文本', number: '数字',
        integer: '整数', boolean: '是/否', null: '空值'
      })[this.normalizedType(type)] || '对象'
    },
    containerOptions(schema, expression, label) {
      if (!schema || typeof schema !== 'object') return []
      const result = []
      const visit = (current, currentExpression, path, currentLabel, depth) => {
        const type = this.normalizedType(current?.type)
        if (['object', 'array'].includes(type)) {
          result.push({
            label: currentLabel,
            path,
            expression: currentExpression,
            type,
            typeLabel: this.typeLabel(type),
            schema: current
          })
        }
        if (depth >= 8) return
        Object.entries(current?.properties || {}).forEach(([key, property]) => {
          visit(property, `${currentExpression}.${key}`, path ? `${path}.${key}` : key,
            property?.title || key, depth + 1)
        })
      }
      visit(schema, expression, '', label, 0)
      return result
    },
    relativeFields(schema) {
      const result = []
      const visit = (current, path, label, depth, includeCurrent = true) => {
        if (!current || depth > 10) return
        const type = this.normalizedType(current.type)
        if (includeCurrent && path) {
          const hasObjectChildren = type === 'object' && Object.keys(current.properties || {}).length > 0
          const hasArrayChildren = type === 'array' && current.items
            && Object.keys(current.items).length > 0
          result.push({
            label,
            path,
            type,
            typeLabel: this.typeLabel(type),
            schema: current,
            leaf: !hasObjectChildren && !hasArrayChildren
          })
        }
        if (type === 'object') {
          Object.entries(current.properties || {}).forEach(([key, property]) => {
            visit(property, path ? `${path}.${key}` : key,
              property.title || key, depth + 1)
          })
        } else if (type === 'array' && current.items && Object.keys(current.items).length) {
          visit(current.items, `${path || ''}[]`, `${label || '数组'}中的每一项`, depth + 1)
        }
      }
      const rootType = this.normalizedType(schema?.type)
      if (rootType === 'array') visit(schema.items || {}, '[]', '当前数组中的每一项', 0)
      else visit(schema, '', '', 0, false)
      return result
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
    setPreviewPath(root, tokens, value) {
      let current = root
      tokens.forEach((token, index) => {
        const next = tokens[index + 1]
        const leaf = !next
        if (!token.each) {
          if (leaf) {
            current[token.field] = value
          } else {
            const expected = next.each ? [] : {}
            if (current[token.field] === undefined) current[token.field] = expected
            current = current[token.field]
          }
        } else {
          if (!Array.isArray(current)) return
          if (leaf) {
            current[0] = value
          } else {
            const expected = next.each ? [] : {}
            if (current[0] === undefined) current[0] = expected
            current = current[0]
          }
        }
      })
    },
    schemaPreview(schema, depth = 0) {
      if (!schema || depth > 8) return '<数据>'
      const type = this.normalizedType(schema.type)
      if (type === 'object') {
        const entries = Object.entries(schema.properties || {})
        if (!entries.length) return '<对象>'
        return Object.fromEntries(entries.map(([key, child]) =>
          [key, this.schemaPreview(child, depth + 1)]))
      }
      if (type === 'array') {
        if (!schema.items || !Object.keys(schema.items).length) return ['<数据>']
        return [this.schemaPreview(schema.items, depth + 1)]
      }
      return `<${this.typeLabel(type)}>`
    },
    changeMode(mode) {
      if (this.disabled || mode === this.mode) return
      const expected = mode === 'ARRAY_MAP' ? 'array' : 'object'
      const keepSource = this.selectedSource?.type === expected
      this.updateConfig({mode, rules: []})
      if (!keepSource) this.$emit('update:inputMapping', {})
      this.advancedRules = []
    },
    sourceChanged(expression) {
      this.$emit('update:inputMapping', expression ? {source: {expression}} : {})
      if (this.rules.length) this.updateConfig({rules: []})
      this.advancedRules = []
    },
    autoAddFields(scope) {
      if (scope === 'LEAVES') this.addAllLeafFields()
      else this.addAllTopLevelFields()
    },
    addAllTopLevelFields() {
      const fields = Object.entries(this.sourceItemSchema?.properties || {})
      if (!fields.length) return
      const existing = new Set(this.rules.map(rule => rule.targetPath))
      const added = fields
        .filter(([key]) => !existing.has(this.targetPathFromSource(key)))
        .map(([key, property]) => ({
          targetPath: this.targetPathFromSource(key),
          sourcePath: key,
          operation: 'COPY',
          resultType: this.normalizedType(property?.type),
          resultSchema: this.cloneSchema(property),
          defaultWhen: 'NEVER',
          onError: 'FAIL',
          required: false
        }))
      this.appendAutomaticRules(added)
    },
    addAllLeafFields() {
      const existing = new Set(this.rules.map(rule => rule.targetPath))
      const added = this.availableFields
        .filter(field => field.leaf)
        .map(field => ({...field, targetPath: this.targetPathFromSource(field.path)}))
        .filter(field => !existing.has(field.targetPath))
        .map(field => ({
          targetPath: field.targetPath,
          sourcePath: field.path,
          operation: 'COPY',
          resultType: field.type,
          resultSchema: this.cloneSchema(field.schema),
          defaultWhen: 'NEVER',
          onError: 'FAIL',
          required: false
        }))
      this.appendAutomaticRules(added)
    },
    appendAutomaticRules(added) {
      const capacity = Math.max(0, 200 - this.rules.length)
      if (added.length > capacity) {
        this.$message.warning(`字段较多，本次先添加 ${capacity} 个；单个转换节点最多配置 200 个字段。`)
      }
      this.updateConfig({rules: [...this.rules, ...added.slice(0, capacity)]})
    },
    addRule(operation) {
      const source = this.availableFields.find(field =>
        !this.rules.some(rule => rule.sourcePath === field.path))
      const targetPath = operation === 'CONSTANT'
        ? this.nextTargetName() : (source ? this.targetPathFromSource(source.path) : this.nextTargetName())
      const rule = {
        targetPath,
        operation,
        resultType: operation === 'CONSTANT' ? 'string' : source?.type || 'object',
        resultSchema: operation === 'CONSTANT' ? {type: 'string'} : this.cloneSchema(source?.schema),
        defaultWhen: 'NEVER',
        onError: 'FAIL',
        required: false
      }
      if (operation === 'CONSTANT') rule.value = ''
      else rule.sourcePath = source?.path || ''
      this.updateConfig({rules: [...this.rules, rule]})
    },
    nextTargetName() {
      let index = 1
      const names = new Set(this.rules.map(rule => rule.targetPath))
      while (names.has(`field${index}`)) index++
      return `field${index}`
    },
    updateRule(index, patch) {
      const rules = this.rules.map((rule, current) =>
        current === index ? {...rule, ...patch} : {...rule})
      this.updateConfig({rules})
    },
    removeRule(index) {
      this.updateConfig({rules: this.rules.filter((rule, current) => current !== index)})
      this.advancedRules = this.advancedRules
        .filter(current => current !== index)
        .map(current => current > index ? current - 1 : current)
    },
    sourceFieldChanged(index, path) {
      const field = this.availableFields.find(item => item.path === path)
      const patch = {sourcePath: path}
      const previous = this.rules[index] || {}
      if (previous.targetPath === this.targetPathFromSource(previous.sourcePath)
        || /^field\d+$/.test(previous.targetPath || '')) {
        patch.targetPath = this.targetPathFromSource(path)
      }
      if (this.rules[index]?.operation === 'COPY') {
        patch.resultType = field?.type || 'object'
        patch.resultSchema = this.cloneSchema(field?.schema)
      }
      this.updateRule(index, patch)
    },
    operationChanged(index, operation) {
      const rule = this.rules[index] || {}
      const resultType = this.operationType(operation, rule)
      const patch = {operation, resultType, resultSchema: {type: resultType}}
      if (operation === 'COPY') {
        const field = this.availableFields.find(item => item.path === rule.sourcePath)
        patch.resultSchema = this.cloneSchema(field?.schema) || {type: resultType}
      }
      if (operation === 'CONSTANT' && !Object.prototype.hasOwnProperty.call(rule, 'value')) {
        patch.value = ''
        patch.resultType = 'string'
        patch.resultSchema = {type: 'string'}
      }
      this.updateRule(index, patch)
    },
    operationType(operation, rule) {
      if (['TO_STRING', 'TRIM', 'UPPERCASE', 'LOWERCASE', 'ARRAY_JOIN'].includes(operation)) return 'string'
      if (['TO_INTEGER', 'ARRAY_LENGTH'].includes(operation)) return 'integer'
      if (operation === 'TO_NUMBER') return 'number'
      if (operation === 'TO_BOOLEAN') return 'boolean'
      if (operation === 'CONSTANT') return rule.resultType || 'string'
      return this.availableFields.find(field => field.path === rule.sourcePath)?.type
        || rule.resultType || 'object'
    },
    ruleResultType(rule) {
      return this.operationType(rule.operation || 'COPY', rule)
    },
    constantTypeChanged(index, resultType) {
      const initial = ({
        string: '', integer: 0, number: 0, boolean: false,
        object: {}, array: [], null: null
      })[resultType]
      this.updateRule(index, {resultType, resultSchema: this.inferValueSchema(initial), value: initial})
    },
    constantValueChanged(index, text) {
      const type = this.rules[index]?.resultType || 'string'
      const parsed = this.parseTypedValue(text, type)
      if (!parsed.valid) {
        this.$message.warning(parsed.message)
        return
      }
      this.updateRule(index, {value: parsed.value, resultSchema: this.inferValueSchema(parsed.value)})
    },
    defaultValueChanged(index, text) {
      const parsed = this.parseTypedValue(text, this.ruleResultType(this.rules[index]))
      if (!parsed.valid) {
        this.$message.warning(parsed.message)
        return
      }
      this.updateRule(index, {defaultValue: parsed.value})
    },
    parseTypedValue(text, type) {
      try {
        if (type === 'string') return {valid: true, value: String(text)}
        if (type === 'integer') {
          const value = Number(text)
          if (!Number.isInteger(value)) throw new Error('请输入整数')
          return {valid: true, value}
        }
        if (type === 'number') {
          const value = Number(text)
          if (!Number.isFinite(value)) throw new Error('请输入数字')
          return {valid: true, value}
        }
        if (type === 'null') return {valid: true, value: null}
        if (['object', 'array'].includes(type)) {
          const value = JSON.parse(text)
          if (type === 'array' ? !Array.isArray(value) : !value || Array.isArray(value) || typeof value !== 'object') {
            throw new Error(type === 'array' ? '请输入 JSON 数组' : '请输入 JSON 对象')
          }
          return {valid: true, value}
        }
        return {valid: true, value: text === true || String(text).toLowerCase() === 'true'}
      } catch (error) {
        return {valid: false, message: error.message}
      }
    },
    inferValueSchema(value, depth = 0) {
      if (value === null) return {type: 'null'}
      if (Array.isArray(value)) {
        return {
          type: 'array',
          items: value.length && depth < 10 ? this.inferValueSchema(value[0], depth + 1) : {}
        }
      }
      if (typeof value === 'object') {
        if (depth >= 10) return {type: 'object'}
        const properties = Object.fromEntries(Object.entries(value)
          .map(([key, child]) => [key, this.inferValueSchema(child, depth + 1)]))
        return {
          type: 'object', properties, required: Object.keys(properties), additionalProperties: false
        }
      }
      if (typeof value === 'number') return {type: Number.isInteger(value) ? 'integer' : 'number'}
      return {type: typeof value === 'boolean' ? 'boolean' : 'string'}
    },
    displayValue(value, type) {
      if (value === undefined) return ''
      if (['object', 'array'].includes(type)) return JSON.stringify(value)
      if (value === null) return 'null'
      return String(value)
    },
    constantPlaceholder(type) {
      if (type === 'object') return '例如 {"name":"张三"}'
      if (type === 'array') return '例如 [1,2,3]'
      if (type === 'null') return '固定为空值'
      return '输入固定值'
    },
    toggleAdvanced(index) {
      this.advancedRules = this.advancedRules.includes(index)
        ? this.advancedRules.filter(value => value !== index)
        : [...this.advancedRules, index]
    },
    ruleSummary(rule) {
      if (rule.operation === 'CONSTANT') return '使用固定值'
      const operation = this.operationOptions.find(option => option.value === rule.operation)?.label || '直接使用'
      return `${rule.sourcePath || '整个对象 / 当前项'} · ${operation}`
    },
    validTargetPath(path) {
      return /^[A-Za-z_][A-Za-z0-9_]*(?:\[\])*(?:\.[A-Za-z_][A-Za-z0-9_]*(?:\[\])*){0,9}$/.test(path || '')
    },
    targetPathFromSource(path) {
      const source = String(path || '')
      if (source.startsWith('[]')) return `values${source}`
      return source.split('.').map(segment => {
        const suffix = (segment.match(/(?:\[\])+$/) || [''])[0]
        let field = (suffix ? segment.slice(0, -suffix.length) : segment)
          .replace(/[^A-Za-z0-9_]/g, '_')
        if (!/^[A-Za-z_]/.test(field)) field = `_${field}`
        return `${field}${suffix}`
      }).join('.')
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
    ruleIssue(rule, index) {
      if (!this.validTargetPath(rule.targetPath)) {
        return '输出字段使用英文、数字和下划线；对象层级显示为“.”，数组每一项显示为“[]”。'
      }
      if (this.rules.some((item, current) => current !== index
        && this.transformPathsConflict(item.targetPath, rule.targetPath))) {
        return '输出字段层级冲突，请保留父字段或具体子字段中的一种。'
      }
      if (rule.operation !== 'CONSTANT' && rule.sourcePath
        && !this.availableFields.some(field => field.path === rule.sourcePath)) {
        return '来源字段已失效，请重新选择。'
      }
      const sourceArrays = (String(rule.sourcePath || '').match(/\[\]/g) || []).length
      const targetArrays = (String(rule.targetPath || '').match(/\[\]/g) || []).length
      if (rule.operation === 'CONSTANT' && targetArrays) return '固定值不能直接配置到数组每一项。'
      if (rule.operation !== 'CONSTANT' && sourceArrays !== targetArrays) {
        return '来源字段和输出字段的数组层级需要保持一致。'
      }
      return ''
    },
    updateConfig(patch) {
      this.$emit('update:config', {
        version: 1,
        mode: this.mode,
        rules: this.rules,
        preserveUnmapped: false,
        maxItems: 1000,
        ...this.config,
        ...patch
      })
    }
  }
}
</script>

<style scoped>
.transform-editor {
  display: grid;
  gap: 14px;
}

.transform-heading,
.transform-section-title,
.transform-rules-heading,
.rule-summary,
.transform-actions,
.source-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.transform-heading > div,
.transform-section-title > span {
  display: grid;
  gap: 4px;
}

.transform-heading strong {
  color: var(--workflow-text-strong);
  font-size: 16px;
}

.transform-heading small,
.transform-section-title small,
.source-option small,
.transform-empty small,
.transform-advanced-content small {
  color: var(--workflow-text-muted);
  font-size: 12px;
}

.transform-mode {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.transform-mode button {
  display: grid;
  gap: 5px;
  padding: 13px 15px;
  color: var(--workflow-text);
  text-align: left;
  background: var(--workflow-surface);
  border: 1px solid var(--workflow-border);
  border-radius: 10px;
  cursor: pointer;
}

.transform-mode button.active {
  color: var(--workflow-primary);
  background: var(--workflow-primary-soft);
  border-color: var(--workflow-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--workflow-primary) 10%, transparent);
}

.transform-mode button small {
  color: var(--workflow-text-muted);
}

.transform-source-card,
.transform-rules-card,
.transform-output-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: var(--workflow-surface);
  border: 1px solid var(--workflow-border);
  border-radius: 12px;
}

.transform-section-title strong {
  color: var(--workflow-text-strong);
  font-size: 14px;
}

.source-option {
  width: 100%;
}

.source-option small {
  overflow: hidden;
  max-width: 52%;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transform-empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 30px 16px;
  color: var(--workflow-text-muted);
  background: var(--workflow-surface-subtle);
  border: 1px dashed var(--workflow-border-strong);
  border-radius: 10px;
}

.transform-empty .el-icon {
  font-size: 24px;
}

.transform-rule {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--workflow-surface-subtle);
  border: 1px solid var(--workflow-border);
  border-radius: 10px;
}

.rule-main-grid {
  display: grid;
  grid-template-columns: minmax(145px, .9fr) minmax(180px, 1.4fr) minmax(135px, .8fr) 32px;
  gap: 10px;
  align-items: end;
}

.rule-main-grid label,
.rule-constant-grid label,
.rule-advanced label,
.transform-advanced-content label {
  display: grid;
  gap: 6px;
  color: var(--workflow-text-muted);
  font-size: 12px;
}

.rule-delete {
  width: 32px;
  height: 32px;
  margin-bottom: 1px;
}

.rule-constant-grid,
.rule-advanced {
  display: grid;
  grid-template-columns: minmax(140px, .55fr) minmax(220px, 1fr);
  gap: 10px;
}

.rule-summary {
  justify-content: flex-start;
  color: var(--workflow-text-muted);
  font-size: 12px;
}

.rule-summary button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  color: var(--workflow-primary);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.mapping-type {
  padding: 2px 8px;
  color: #535bd7;
  font-style: normal;
  background: #eef0ff;
  border-radius: 999px;
}

.mapping-type--string { color: #087f5b; background: #e8f8ef; }
.mapping-type--integer,
.mapping-type--number { color: #ad6200; background: #fff3d8; }
.mapping-type--boolean { color: #a33b61; background: #ffe9f1; }

.transform-warning {
  margin: 0;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
}

.transform-output-card pre {
  min-height: 76px;
  max-height: 220px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: var(--workflow-text);
  background: var(--workflow-surface-subtle);
  border-radius: 8px;
}

.transform-advanced {
  background: var(--workflow-surface);
  border: 1px solid var(--workflow-border);
  border-radius: 10px;
}

.transform-advanced summary {
  padding: 13px 15px;
  color: var(--workflow-text-strong);
  font-weight: 600;
  cursor: pointer;
}

.transform-advanced-content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 0 15px 15px;
}

@media (max-width: 900px) {
  .rule-main-grid,
  .rule-constant-grid,
  .rule-advanced,
  .transform-advanced-content {
    grid-template-columns: 1fr;
  }

  .rule-delete {
    justify-self: end;
  }
}
</style>
