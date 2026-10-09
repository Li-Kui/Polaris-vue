<template>
  <div class="schema-grid">
    <el-form-item
      v-for="field in fields"
      :key="field.key"
      :label="fieldLabel(field)"
      :required="requiredFields.has(field.key)"
      class="schema-field"
    >
      <el-switch
        v-if="field.component === 'switch'"
        :model-value="value(field.key)"
        :disabled="disabled"
        @update:model-value="update(field.key, $event)"
      />
      <el-select
        v-else-if="field.component === 'select'"
        :model-value="value(field.key)"
        :disabled="disabled"
        clearable
        @update:model-value="update(field.key, $event)"
      >
        <el-option
          v-for="option in enumValues(field.schema)"
          :key="String(option)"
          :label="optionLabel(field, option)"
          :value="option"
        />
      </el-select>
      <el-slider
        v-else-if="field.component === 'slider'"
        :model-value="numberValue(field.key, field.schema)"
        :disabled="disabled"
        :min="numberOr(field.schema.minimum, 0)"
        :max="numberOr(field.schema.maximum, 100)"
        :step="numberOr(field.ui.step, 1)"
        show-input
        @update:model-value="update(field.key, $event)"
      />
      <el-input-number
        v-else-if="field.component === 'number'"
        :model-value="numberValue(field.key, field.schema)"
        :disabled="disabled"
        :min="field.schema.minimum"
        :max="field.schema.maximum"
        :step="numberOr(field.ui.step || field.schema.multipleOf, 1)"
        @update:model-value="update(field.key, $event)"
      />
      <el-select
        v-else-if="field.component === 'tags'"
        :model-value="arrayValue(field.key)"
        :disabled="disabled"
        multiple
        allow-create
        filterable
        default-first-option
        @update:model-value="update(field.key, $event)"
      />
      <el-select
        v-else-if="field.component === 'custom' && field.ui.customComponent === 'image-size-selector'"
        :model-value="value(field.key)"
        :disabled="disabled"
        allow-create
        filterable
        @update:model-value="update(field.key, $event)"
      >
        <el-option v-for="size in imageSizes" :key="size" :label="size" :value="size" />
      </el-select>
      <el-select
        v-else-if="field.component === 'custom' && field.ui.customComponent === 'voice-selector' && optionValues(field).length"
        :model-value="value(field.key)"
        :disabled="disabled"
        filterable
        @update:model-value="update(field.key, $event)"
      >
        <el-option
          v-for="voice in optionValues(field)"
          :key="String(voice.value)"
          :label="voice.label"
          :value="voice.value"
        />
      </el-select>
      <el-input
        v-else-if="field.component === 'input' || isKnownCustom(field)"
        :model-value="value(field.key)"
        :disabled="disabled"
        :placeholder="field.ui.placeholder || field.schema.description"
        @update:model-value="update(field.key, $event)"
      />
      <el-alert
        v-else
        type="error"
        :closable="false"
        :title="`不支持的配置组件：${field.component}`"
      />
      <div v-if="field.schema.description" class="field-help">{{ field.schema.description }}</div>
    </el-form-item>
  </div>
</template>

<script setup>
import {computed} from 'vue'

const props = defineProps({
  schema: { type: Object, required: true },
  uiSchema: { type: Object, default: () => ({}) },
  modelValue: { type: Object, default: () => ({}) },
  options: { type: Object, default: () => ({}) },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const supported = new Set(['input', 'number', 'select', 'slider', 'switch', 'tags', 'custom'])
const imageSizes = ['512x512', '1024x1024', '1024x1536', '1536x1024', '2048x2048']
const requiredFields = computed(() => new Set(props.schema?.required || []))
const fields = computed(() => Object.entries(props.schema?.properties || {})
  .map(([key, schema]) => {
    const ui = props.uiSchema?.[key] || {}
    let component = ui.component || defaultComponent(schema)
    if (!supported.has(component)) component = `unknown:${component}`
    return { key, schema, ui, component }
  })
  .sort((a, b) => (a.ui.order || 999) - (b.ui.order || 999)))

function defaultComponent(schema) {
  if ((schema?.enumValues || schema?.enum || []).length) return 'select'
  if (schema?.type === 'boolean') return 'switch'
  if (schema?.type === 'integer' || schema?.type === 'number') return 'number'
  if (schema?.type === 'array') return 'tags'
  return 'input'
}

function value(key) {
  return props.modelValue?.[key]
}

function numberValue(key, schema) {
  return value(key) ?? schema?.defaultValue ?? schema?.default ?? schema?.minimum ?? 0
}

function arrayValue(key) {
  return Array.isArray(value(key)) ? value(key) : []
}

function enumValues(schema) {
  return schema?.enumValues || schema?.enum || []
}

function optionValues(field) {
  const dynamic = props.options?.[field.key]
  if (Array.isArray(dynamic) && dynamic.length) return dynamic
  return enumValues(field.schema).map(value => ({ value, label: String(value) }))
}

function optionLabel(field, option) {
  const labels = {
    standard: '标准', hd: '高清', low: '低', medium: '中', high: '高', max: '最高',
    ON_REQUEST: '按需开启', ALWAYS: '始终开启', MODEL_DEFAULT: '使用模型默认', REQUEST: '请求指定'
  }
  return labels[String(option)] || String(option)
}

function fieldLabel(field) {
  if (field.schema.title) return field.schema.title
  return ({
    maxTokens: '最大输出长度', temperature: '随机性', size: '尺寸', quality: '质量',
    strength: '编辑强度', maskFeather: '蒙版羽化', similarity: '相似度',
    voice: '音色', format: '格式', sampleRate: '采样率', speed: '语速', pitch: '音调',
    language: '语言', timestamps: '返回时间戳', speakerDiarization: '区分说话人', punctuation: '自动标点',
    dimension: '向量维度', dimensionMode: '维度模式', maxInputTokens: '最大输入长度', batchSize: '批处理数量',
    topN: '返回结果数', returnDocuments: '返回原文', effort: '思考强度', activationPolicy: '启用方式',
    duration: '时长（秒）', fps: '帧率', resolution: '分辨率', aspectRatio: '画面比例'
  })[field.key] || field.key
}

function numberOr(value, fallback) {
  return value === null || value === undefined ? fallback : Number(value)
}

function isKnownCustom(field) {
  return field.component === 'custom' && field.ui.customComponent === 'voice-selector'
}

function update(key, next) {
  emit('update:modelValue', { ...props.modelValue, [key]: next })
}

defineExpose({
  hasUnsupportedComponent: () => fields.value.some(field => field.component.startsWith('unknown:'))
})
</script>

<style scoped>
.schema-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px 20px; }
.schema-field { min-width: 0; }
.schema-field :deep(.el-form-item__content) { min-width: 0; }
.schema-field :deep(.el-slider) { min-width: 0; width: 100%; gap: 16px; }
.schema-field :deep(.el-slider__runway.show-input) { flex: 1; min-width: 0; margin-right: 0; }
.schema-field :deep(.el-slider__input) { flex: 0 0 120px; width: 120px; }
.schema-field :deep(.el-select), .schema-field :deep(.el-input-number) { width: 100%; }
.schema-field :deep(.el-form-item__label) { color: var(--mc-text) !important; font-weight: 600; }
.field-help { margin-top: 5px; color: var(--mc-muted); font-size: 12px; line-height: 1.4; }
@media (max-width: 900px) { .schema-grid { grid-template-columns: 1fr; } }
</style>
