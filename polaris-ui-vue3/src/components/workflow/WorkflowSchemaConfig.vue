<template>
  <div v-if="fields.length" class="schema-config">
    <el-form-item
      v-for="field in fields"
      :key="field.name"
      :error="errors[field.name]"
    >
      <template #label>
        <span class="field-label">
          <span>{{ fieldLabel(field) }}</span>
          <el-tag v-if="showType" size="small" effect="plain">{{ fieldType(field.schema) }}</el-tag>
        </span>
      </template>
      <el-select
        v-if="field.schema.enum"
        :model-value="modelValue?.[field.name]"
        :disabled="disabled"
        clearable
        style="width: 100%"
        @change="update(field.name, $event)"
      >
        <el-option
          v-for="option in field.schema.enum"
          :key="String(option)"
          :label="String(option)"
          :value="option"
        />
      </el-select>
      <el-input-number
        v-else-if="field.schema.type === 'integer' || field.schema.type === 'number'"
        :model-value="fieldValue(field)"
        :disabled="disabled"
        :min="field.schema.minimum"
        :max="field.schema.maximum"
        :step="field.schema.type === 'integer' ? 1 : 0.1"
        style="width: 100%"
        @change="update(field.name, $event)"
      />
      <el-select
        v-else-if="field.schema.type === 'array' && field.schema.items?.type === 'string'"
        :model-value="modelValue?.[field.name] || []"
        :disabled="disabled"
        multiple
        filterable
        allow-create
        default-first-option
        style="width: 100%"
        placeholder="输入后回车添加"
        @change="update(field.name, $event)"
      />
      <el-switch
        v-else-if="field.schema.type === 'boolean'"
        :model-value="modelValue?.[field.name]"
        :disabled="disabled"
        @change="update(field.name, $event)"
      />
      <template v-else-if="isJsonField(field)">
        <el-input
          :model-value="jsonDrafts[field.name]"
          :disabled="disabled"
          :placeholder="field.schema.placeholder"
          type="textarea"
          :rows="10"
          @input="updateJson(field.name, $event)"
        />
        <div v-if="jsonErrors[field.name]" class="field-error">
          {{ jsonErrors[field.name] }}
        </div>
      </template>
      <el-input
        v-else-if="field.schema.format === 'textarea'"
        :model-value="modelValue?.[field.name]"
        :disabled="disabled"
        :maxlength="field.schema.maxLength"
        :placeholder="field.schema.placeholder"
        :rows="field.schema.rows || 6"
        type="textarea"
        show-word-limit
        resize="vertical"
        @input="update(field.name, $event)"
      />
      <el-input
        v-else
        :model-value="modelValue?.[field.name]"
        :disabled="disabled"
        :maxlength="field.schema.maxLength"
        :placeholder="field.schema.placeholder"
        clearable
        @input="update(field.name, $event)"
      />
      <small v-if="field.schema.description" class="field-description">
        {{ field.schema.description }}
      </small>
    </el-form-item>
  </div>
</template>

<script>
export default {
  name: 'WorkflowSchemaConfig',
  props: {
    schema: {
      type: Object,
      default: () => ({})
    },
    modelValue: {
      type: Object,
      default: () => ({})
    },
    disabled: {
      type: Boolean,
      default: false
    },
    errors: {
      type: Object,
      default: () => ({})
    },
    showType: {
      type: Boolean,
      default: false
    }
  },
  emits: ['update:modelValue'],
  data() {
    return {
      jsonDrafts: {},
      jsonErrors: {}
    }
  },
  computed: {
    requiredFields() {
      return new Set(Array.isArray(this.schema?.required) ? this.schema.required : [])
    },
    fields() {
      return Object.entries(this.schema?.properties || {})
        .map(([name, schema]) => ({name, schema: schema || {}}))
    }
  },
  watch: {
    modelValue: {
      immediate: true,
      deep: true,
      handler(value) {
        this.syncJsonDrafts(value)
      }
    },
    schema: {
      deep: true,
      handler() {
        this.syncJsonDrafts(this.modelValue)
      }
    }
  },
  methods: {
    fieldLabel(field) {
      const fallbackLabels = {
        maxWaitSeconds: '最长等待时间（秒）',
        structuredOutputSchema: '结构化输出 Schema',
        systemPrompt: '系统提示词',
        additionalSystemPrompt: '补充系统提示词'
      }
      const label = field.schema.title || fallbackLabels[field.name] || field.name
      return this.requiredFields.has(field.name) ? `${label} *` : label
    },
    fieldValue(field) {
      if (Object.prototype.hasOwnProperty.call(this.modelValue || {}, field.name)) {
        return this.modelValue[field.name]
      }
      const fallbackDefaults = {maxWaitSeconds: 300}
      return field.schema.default ?? fallbackDefaults[field.name]
    },
    fieldType(schema) {
      const labels = {
        object: 'Object',
        array: 'Array',
        integer: 'Integer',
        number: 'Number',
        boolean: 'Boolean',
        string: 'String'
      }
      return labels[schema?.type] || schema?.type || 'Any'
    },
    isJsonField(field) {
      return field.schema.type === 'object'
        || (field.schema.type === 'array' && field.schema.items?.type !== 'string')
    },
    update(name, value) {
      const result = {...(this.modelValue || {})}
      if (value === undefined || value === null || value === '') {
        delete result[name]
      } else {
        result[name] = value
      }
      this.$emit('update:modelValue', result)
    },
    updateJson(name, value) {
      this.jsonDrafts[name] = value
      const text = String(value || '').trim()
      if (!text) {
        delete this.jsonErrors[name]
        this.update(name, null)
        return
      }
      try {
        const parsed = JSON.parse(text)
        const field = this.fields.find(item => item.name === name)
        const expectsArray = field?.schema?.type === 'array'
        if (expectsArray ? !Array.isArray(parsed) : (!parsed || Array.isArray(parsed) || typeof parsed !== 'object')) {
          throw new Error(expectsArray ? '请输入 JSON 数组' : '请输入 JSON 对象')
        }
        delete this.jsonErrors[name]
        this.update(name, parsed)
      } catch (error) {
        this.jsonErrors[name] = error.message
      }
    },
    syncJsonDrafts(value) {
      this.fields
        .filter(field => this.isJsonField(field))
        .forEach(field => {
          if (this.jsonErrors[field.name]) return
          const current = value?.[field.name]
          this.jsonDrafts[field.name] = current && typeof current === 'object'
            ? JSON.stringify(current, null, 2) : ''
        })
    }
  }
}
</script>

<style scoped>
.field-description {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.field-label {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.field-error {
  margin-top: 4px;
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
