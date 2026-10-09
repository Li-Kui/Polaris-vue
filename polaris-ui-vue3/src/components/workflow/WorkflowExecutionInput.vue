<template>
  <div class="workflow-execution-input" :class="{ 'is-simple': simple }">
    <el-skeleton v-if="loading" :rows="5" animated />
    <template v-else>
      <el-radio-group v-model="mode" class="input-mode-switch" @change="modeChanged">
        <el-radio-button value="form">{{ simple ? '填写参数' : '参数表单' }}</el-radio-button>
        <el-radio-button value="json">{{ simple ? '高级 JSON' : 'JSON 模式' }}</el-radio-button>
      </el-radio-group>

      <section v-show="mode === 'form'" class="input-mode-panel">
        <el-alert
          v-if="hasDeclaredInputs && !simple"
          title="已根据当前发布版本的输入契约生成字段，可直接填写。"
          type="success"
          :closable="false"
          show-icon
        />
        <el-form v-if="hasDeclaredInputs" label-position="top">
          <WorkflowSchemaConfig
            :key="formRevision"
            :schema="formSchema"
            :model-value="inputValue"
            :errors="fieldErrors"
            :show-type="!simple"
            :nested-objects="simple"
            @update:model-value="structuredInputChanged"
            @validation="schemaValidationChanged"
          />
        </el-form>
        <el-empty
          v-else
          description="该工作流没有声明输入字段，可直接运行"
          :image-size="64"
        >
          <el-button type="primary" plain @click="mode = 'json'">切换到 JSON 模式</el-button>
        </el-empty>
      </section>

      <section v-show="mode === 'json'" class="input-mode-panel json-mode-panel">
        <div class="json-toolbar">
          <div>
            <strong>输入 JSON</strong>
            <small>{{ simple ? '粘贴完整参数，也可以切回填写参数' : '适合粘贴完整对象或调试复杂结构' }}</small>
          </div>
          <el-button-group>
            <el-button @click="resetToExample(true)">生成示例</el-button>
            <el-button @click="formatJson">格式化</el-button>
            <el-button @click="clearJson">清空</el-button>
          </el-button-group>
        </div>
        <el-input
          :model-value="jsonDraft"
          class="json-editor"
          type="textarea"
          aria-label="输入 JSON"
          :rows="simple ? 8 : 12"
          spellcheck="false"
          @input="jsonInputChanged"
        />
        <div :class="['json-validation', { 'is-error': jsonError || validationErrors.length }]">
          <template v-if="jsonError">{{ jsonError }}</template>
          <template v-else-if="validationErrors.length">{{ validationErrors[0].message }}</template>
          <template v-else>{{ simple ? '输入格式正确' : '格式正确，已通过输入契约校验' }}</template>
        </div>
        <div v-if="schemaHints.length && !simple" class="schema-hints">
          <strong>输入契约提示</strong>
          <span v-for="hint in schemaHints" :key="hint">{{ hint }}</span>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
import WorkflowSchemaConfig from './WorkflowSchemaConfig.vue'
import {runtimeInputFormSchema} from '@/utils/workflowInputMapping'

export default {
  name: 'WorkflowExecutionInput',
  components: {WorkflowSchemaConfig},
  props: {
    simple: {
      type: Boolean,
      default: false
    },
    schema: {
      type: Object,
      default: () => ({type: 'object', properties: {}})
    },
    modelValue: {
      type: Object,
      default: () => ({})
    },
    loading: {
      type: Boolean,
      default: false
    },
    hiddenFields: {
      type: Array,
      default: () => []
    }
  },
  emits: ['update:modelValue', 'validation'],
  data() {
    return {
      mode: 'form',
      inputValue: {},
      jsonDraft: '{}',
      jsonError: '',
      formRevision: 0,
      formValidation: {valid: true, message: '', errors: []},
      hasEditedInput: false,
      validationErrors: [],
      syncingModel: false
    }
  },
  computed: {
    formSchema() {
      return runtimeInputFormSchema(this.normalizedSchema, this.hiddenFields)
    },
    normalizedSchema() {
      const schema = this.schema && typeof this.schema === 'object' ? this.schema : {}
      return {
        ...schema,
        type: schema.type || 'object',
        properties: schema.properties && typeof schema.properties === 'object'
          ? schema.properties : {}
      }
    },
    hasDeclaredInputs() {
      return Object.keys(this.normalizedSchema.properties).length > 0
        || (Array.isArray(this.normalizedSchema.required) && this.normalizedSchema.required.length > 0)
    },
    fieldErrors() {
      if (this.simple && !this.hasEditedInput) return {}
      return this.validationErrors.reduce((result, item) => {
        const match = item.path.match(/^\$\.([^.[\]]+)/)
        if (match && !result[match[1]]) result[match[1]] = item.message
        return result
      }, {})
    },
    schemaHints() {
      const required = new Set(Array.isArray(this.normalizedSchema.required)
        ? this.normalizedSchema.required : [])
      const fields = Object.entries(this.normalizedSchema.properties)
      if (!fields.length) return []
      const requiredFields = fields
        .filter(([name]) => required.has(name))
        .map(([name, schema]) => `${schema.title || name} · ${this.typeLabel(schema)}`)
      const optionalFields = fields
        .filter(([name]) => !required.has(name))
        .map(([name, schema]) => `${schema.title || name} · ${this.typeLabel(schema)}`)
      const hints = []
      if (requiredFields.length) hints.push(`必填：${requiredFields.join('，')}`)
      if (optionalFields.length) hints.push(`可选：${optionalFields.join('，')}`)
      return hints
    }
  },
  watch: {
    modelValue: {
      immediate: true,
      deep: true,
      handler(value) {
        if (this.syncingModel) return
        const normalized = this.isObject(value) ? this.deepClone(value) : {}
        this.inputValue = normalized
        if (!this.jsonError) this.jsonDraft = JSON.stringify(normalized, null, 2)
        this.validate(normalized)
      }
    },
    schema: {
      immediate: true,
      deep: true,
      handler() {
        this.$nextTick(() => {
          if (!Object.keys(this.inputValue || {}).length) this.resetToExample(false)
          else this.validate(this.inputValue)
        })
      }
    },
    loading(value) {
      if (!value) this.validate(this.inputValue)
    }
  },
  methods: {
    reset() {
      this.mode = 'form'
      this.jsonError = ''
      this.hasEditedInput = false
      this.resetToExample(false)
    },
    resetToExample(showFeedback = true) {
      this.resetFormDrafts()
      this.jsonError = ''
      if (showFeedback) this.hasEditedInput = true
      const hasSchemaFields = Object.keys(this.normalizedSchema.properties).length > 0
      const example = this.simple && !showFeedback ? this.initialValueForSchema(this.normalizedSchema) : hasSchemaFields
        ? this.exampleForSchema(this.normalizedSchema, true)
        : showFeedback ? {message: '示例输入'} : {}
      const value = this.isObject(example) ? example : {}
      this.commit(value)
      if (!showFeedback) return
      this.$message.success(hasSchemaFields
        ? '已根据输入契约生成示例'
        : '当前契约未声明字段，已生成通用示例，可按需修改')
    },
    structuredInputChanged(value) {
      this.hasEditedInput = true
      this.commit(this.isObject(value) ? value : {})
    },
    resetFormDrafts() {
      this.formRevision++
      this.formValidation = {valid: true, message: '', errors: []}
    },
    schemaValidationChanged(value) {
      this.formValidation = value
      if (!value.valid) this.hasEditedInput = true
      this.emitValidation(!this.loading && !this.jsonError && !this.validationErrors.length)
    },
    jsonInputChanged(value) {
      this.hasEditedInput = true
      this.jsonDraft = value
      const parsed = this.parseJson(value)
      if (!parsed.ok) {
        this.jsonError = parsed.error
        this.emitValidation(false)
        return
      }
      this.jsonError = ''
      this.commit(parsed.value, false)
    },
    formatJson() {
      const parsed = this.parseJson(this.jsonDraft)
      if (!parsed.ok) {
        this.jsonError = parsed.error
        this.emitValidation(false)
        return
      }
      this.jsonError = ''
      this.commit(parsed.value)
    },
    clearJson() {
      this.resetFormDrafts()
      this.jsonDraft = '{}'
      this.jsonError = ''
      this.commit({}, false)
    },
    modeChanged(value) {
      if (value === 'json' && !this.formValidation.valid) {
        this.mode = 'form'
        this.$message.warning('请先修正参数表单中的 JSON 后再切换输入模式')
        this.emitValidation(false)
        return
      }
      if (value !== 'form') return
      const parsed = this.parseJson(this.jsonDraft)
      if (parsed.ok) {
        this.resetFormDrafts()
        this.jsonError = ''
        this.commit(parsed.value)
        return
      }
      this.mode = 'json'
      this.jsonError = parsed.error
      this.$message.warning('请先修正 JSON 后再切换到参数表单')
      this.emitValidation(false)
    },
    commit(value, syncJson = true) {
      const normalized = this.deepClone(value)
      this.inputValue = normalized
      if (syncJson) this.jsonDraft = JSON.stringify(normalized, null, 2)
      this.validationErrors = this.validateValue(normalized, this.normalizedSchema)
      this.syncingModel = true
      this.$emit('update:modelValue', normalized)
      this.$nextTick(() => {
        this.syncingModel = false
      })
      this.emitValidation(!this.loading && !this.jsonError && !this.validationErrors.length)
    },
    validate(value) {
      this.validationErrors = this.validateValue(value, this.normalizedSchema)
      this.emitValidation(!this.loading && !this.jsonError && !this.validationErrors.length)
    },
    emitValidation(valid) {
      const draftErrors = this.mode === 'form' ? this.formValidation.errors : []
      const errors = [...draftErrors, ...this.validationErrors]
      const message = this.jsonError || errors[0]?.message || ''
      this.$emit('validation', {valid: Boolean(valid && (this.mode !== 'form' || this.formValidation.valid)), message, errors})
    },
    parseJson(value) {
      const text = String(value || '').trim()
      if (!text) return {ok: false, error: '请输入 JSON 对象'}
      try {
        const parsed = JSON.parse(text)
        if (!this.isObject(parsed)) return {ok: false, error: '工作流输入必须是 JSON 对象'}
        return {ok: true, value: parsed}
      } catch (error) {
        return {ok: false, error: this.formatJsonError(error, text)}
      }
    },
    formatJsonError(error, text) {
      const message = String(error?.message || '')
      const lineColumn = message.match(/line\s+(\d+)\s+column\s+(\d+)/i)
      if (lineColumn) return `JSON 格式错误：第 ${lineColumn[1]} 行，第 ${lineColumn[2]} 列`
      const position = message.match(/position\s+(\d+)/i)
      const unexpected = message.match(/Unexpected token\s+'([^']+)'/i)
      const offset = position ? Number(position[1])
        : unexpected ? text.lastIndexOf(unexpected[1]) : -1
      if (offset < 0) return `JSON 格式错误：${message}`
      const before = text.slice(0, offset)
      const line = before.split('\n').length
      const column = offset - before.lastIndexOf('\n')
      return `JSON 格式错误：第 ${line} 行，第 ${column} 列`
    },
    validateValue(value, schema, path = '$') {
      if (!schema || typeof schema !== 'object') return []
      const errors = []
      const type = schema.type
      if (type && !this.matchesType(value, type)) {
        errors.push({path, message: `${path} 应为 ${this.typeLabel(schema)}`})
        return errors
      }
      if (Array.isArray(schema.enum) && !schema.enum.some(item => this.equalValue(item, value))) {
        errors.push({path, message: `${path} 只能选择契约中允许的值`})
      }
      if (this.isObject(value)) {
        const required = Array.isArray(schema.required) ? schema.required : []
        required.forEach(name => {
          if (!Object.prototype.hasOwnProperty.call(value, name)
            || value[name] === undefined || value[name] === null) {
            const title = schema.properties?.[name]?.title || name
            errors.push({path: `${path}.${name}`, message: `${title} 为必填项`})
          }
        })
        Object.entries(schema.properties || {}).forEach(([name, childSchema]) => {
          if (value[name] !== undefined && value[name] !== null) {
            errors.push(...this.validateValue(value[name], childSchema, `${path}.${name}`))
          }
        })
      }
      if (Array.isArray(value)) {
        if (schema.minItems !== undefined && value.length < schema.minItems) {
          errors.push({path, message: `${path} 至少需要 ${schema.minItems} 项`})
        }
        if (schema.maxItems !== undefined && value.length > schema.maxItems) {
          errors.push({path, message: `${path} 最多允许 ${schema.maxItems} 项`})
        }
        if (schema.items) {
          value.forEach((item, index) => {
            errors.push(...this.validateValue(item, schema.items, `${path}[${index}]`))
          })
        }
      }
      if (typeof value === 'string') {
        if (schema.minLength !== undefined && value.length < schema.minLength) {
          errors.push({path, message: `${path} 至少需要 ${schema.minLength} 个字符`})
        }
        if (schema.maxLength !== undefined && value.length > schema.maxLength) {
          errors.push({path, message: `${path} 最多允许 ${schema.maxLength} 个字符`})
        }
        if (schema.pattern) {
          try {
            if (!new RegExp(schema.pattern).test(value)) {
              errors.push({path, message: `${path} 不符合要求的格式`})
            }
          } catch (error) {
            // 契约中的无效正则由后端发布校验负责，这里不阻断运行。
          }
        }
      }
      if (typeof value === 'number') {
        if (schema.minimum !== undefined && value < schema.minimum) {
          errors.push({path, message: `${path} 不能小于 ${schema.minimum}`})
        }
        if (schema.maximum !== undefined && value > schema.maximum) {
          errors.push({path, message: `${path} 不能大于 ${schema.maximum}`})
        }
      }
      return errors
    },
    matchesType(value, type) {
      const types = Array.isArray(type) ? type : [type]
      return types.some(item => {
        if (item === 'object') return this.isObject(value)
        if (item === 'array') return Array.isArray(value)
        if (item === 'integer') return Number.isInteger(value)
        if (item === 'number') return typeof value === 'number' && Number.isFinite(value)
        if (item === 'null') return value === null
        return typeof value === item
      })
    },
    typeLabel(schema) {
      const type = Array.isArray(schema?.type) ? schema.type.join(' / ') : schema?.type
      const labels = {
        object: 'Object',
        array: 'Array',
        integer: 'Integer',
        number: 'Number',
        boolean: 'Boolean',
        string: 'String',
        null: 'Null'
      }
      return labels[type] || type || 'Any'
    },
    initialValueForSchema(schema) {
      if (schema?.default !== undefined) return this.deepClone(schema.default)
      if (schema?.type !== 'object' && !schema?.properties) return undefined
      return Object.fromEntries(Object.entries(schema.properties || {}).flatMap(([name, child]) => {
        const value = this.initialValueForSchema(child)
        return value === undefined || (this.isObject(value) && !Object.keys(value).length && child?.default === undefined) ? [] : [[name, value]]
      }))
    },
    exampleForSchema(schema, includeAll = false) {
      if (schema?.default !== undefined) return this.deepClone(schema.default)
      if (schema?.example !== undefined) return this.deepClone(schema.example)
      if (Array.isArray(schema?.examples) && schema.examples.length) {
        return this.deepClone(schema.examples[0])
      }
      if (Array.isArray(schema?.enum) && schema.enum.length) return this.deepClone(schema.enum[0])
      const type = schema?.type || (schema?.properties ? 'object' : 'string')
      if (type === 'object') {
        const value = {}
        const required = new Set(Array.isArray(schema.required) ? schema.required : [])
        Object.entries(schema.properties || {}).forEach(([name, child]) => {
          const hasExample = child?.default !== undefined || child?.example !== undefined
            || (Array.isArray(child?.examples) && child.examples.length)
            || (Array.isArray(child?.enum) && child.enum.length)
          if (includeAll || required.has(name) || hasExample) {
            value[name] = this.exampleForSchema(child, includeAll || required.has(name))
          }
        })
        return value
      }
      if (type === 'array') {
        if (!includeAll || !schema.items) return []
        const item = this.exampleForSchema(schema.items, true)
        return item === undefined ? [] : [item]
      }
      if (type === 'boolean') return false
      if (type === 'integer' || type === 'number') return schema.minimum ?? 0
      // 文件必须由用户提供，不能用示例文字冒充已经上传的地址。
      if (schema.format === 'uri' || schema.format === 'binary'
        || String(schema.contentMediaType || '').startsWith('image/')) return undefined
      if (includeAll && type === 'string') {
        return schema.placeholder || (schema.title ? `示例${schema.title}` : '示例文本')
      }
      return undefined
    },
    equalValue(left, right) {
      return JSON.stringify(left) === JSON.stringify(right)
    },
    deepClone(value) {
      if (value === undefined) return undefined
      return JSON.parse(JSON.stringify(value))
    },
    isObject(value) {
      return value !== null && typeof value === 'object' && !Array.isArray(value)
    }
  }
}
</script>

<style scoped>
.workflow-execution-input {
  min-height: 300px;
}

.input-mode-switch {
  width: 100%;
  padding: 4px;
  box-sizing: border-box;
  border-radius: 10px;
  background: var(--workflow-muted, var(--el-fill-color-light));
}

.input-mode-switch :deep(.el-radio-button) {
  width: 50%;
}

.input-mode-switch :deep(.el-radio-button__inner) {
  width: 100%;
  border: 0 !important;
  border-radius: 8px;
  color: var(--el-text-color-regular) !important;
  background: var(--el-bg-color) !important;
  box-shadow: none !important;
}

.input-mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: var(--workflow-primary-text, #fff) !important;
  border-color: var(--workflow-primary, var(--el-color-primary)) !important;
  background: var(--workflow-primary, var(--el-color-primary)) !important;
  box-shadow: none !important;
}

.input-mode-switch :deep(.el-radio-button:focus-within .el-radio-button__inner) {
  outline: 2px solid var(--workflow-primary, var(--el-color-primary));
  outline-offset: 2px;
}

.input-mode-panel {
  margin-top: 16px;
}

.input-mode-panel :deep(.schema-config) {
  margin-top: 18px;
}

.input-mode-panel :deep(.el-empty) {
  padding: 30px 0 12px;
}

.json-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.json-toolbar > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.json-toolbar small {
  color: var(--el-text-color-secondary);
}

.json-editor :deep(textarea) {
  font: 13px/1.7 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  tab-size: 2;
}

.json-validation {
  margin-top: 8px;
  color: var(--workflow-success, var(--el-color-success));
  font-size: 12px;
}

.json-validation.is-error {
  color: var(--workflow-danger, var(--el-color-danger));
}

.schema-hints {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 9px;
  color: var(--el-text-color-secondary);
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  font-size: 12px;
}

.schema-hints strong {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

@media (max-width: 640px) {
  .json-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
