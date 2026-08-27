<template>
  <div v-if="fields.length" class="schema-config">
    <el-form-item
      v-for="field in fields"
      :key="field.name"
      :label="fieldLabel(field)"
    >
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
        :model-value="modelValue?.[field.name]"
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
    }
  },
  emits: ['update:modelValue'],
  computed: {
    requiredFields() {
      return new Set(Array.isArray(this.schema?.required) ? this.schema.required : [])
    },
    fields() {
      return Object.entries(this.schema?.properties || {})
        .map(([name, schema]) => ({name, schema: schema || {}}))
    }
  },
  methods: {
    fieldLabel(field) {
      const label = field.schema.title || field.name
      return this.requiredFields.has(field.name) ? `${label} *` : label
    },
    update(name, value) {
      const result = {...(this.modelValue || {})}
      if (value === undefined || value === null || value === '') {
        delete result[name]
      } else {
        result[name] = value
      }
      this.$emit('update:modelValue', result)
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
</style>
