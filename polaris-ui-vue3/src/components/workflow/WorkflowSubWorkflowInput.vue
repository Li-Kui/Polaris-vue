<template>
  <section class="child-input" :class="{'child-input--nested': depth > 0}">
    <div class="child-input-heading">
      <label :for="controlId">{{ label }} <span v-if="required" class="required">必填</span></label>
      <el-select :id="controlId" :model-value="modelValue?.mode === 'VALUE' && modelValue.value === null ? 'NULL' : modelValue?.mode || 'DEFAULT'" :disabled="disabled" aria-label="输入方式" @change="changeMode">
        <el-option label="不填写 / 使用默认值" value="DEFAULT" />
        <el-option label="选择上游字段" value="SOURCE" />
        <el-option v-if="type === 'object'" label="逐字段填写" value="OBJECT" />
        <el-option v-if="type === 'array'" label="添加数组元素" value="ARRAY" />
        <el-option v-if="!['object', 'array'].includes(type)" label="填写固定值" value="VALUE" />
        <el-option v-if="schema.type === 'null' || schema.type?.includes?.('null')" label="传递空值（null）" value="NULL" />
        <el-option label="填写 JSON（高级）" value="JSON" />
      </el-select>
    </div>
    <small v-if="schema.description">{{ schema.description }}</small>
    <small v-if="modelValue?.mode === 'DEFAULT'">{{ Object.hasOwn(schema, 'default') ? `默认值：${JSON.stringify(schema.default)}` : required ? '此字段需要填写' : '未填写时不传递此字段' }}</small>
    <el-select v-if="modelValue?.mode === 'SOURCE'" :model-value="modelValue.expression" filterable clearable :disabled="disabled" placeholder="搜索上游节点或字段" aria-label="来源字段" @change="emit({...modelValue, expression: $event})">
      <el-option v-for="field in fields" :key="field.expression" :label="`${field.group} / ${field.label} · ${field.typeLabel || field.type}`" :value="field.expression" :disabled="!compatibleType(schema, field.type) || field.expression.includes('[]')" />
    </el-select>
    <template v-if="modelValue?.mode === 'VALUE'">
      <el-select v-if="schema.enum" :model-value="modelValue.value" :disabled="disabled" @change="valueChanged">
        <el-option v-for="(value, i) in schema.enum" :key="i" :label="String(value)" :value="value" />
      </el-select>
      <el-input-number v-else-if="['integer', 'number'].includes(type)" :model-value="modelValue.value" :precision="type === 'integer' ? 0 : undefined" :min="schema.minimum" :max="schema.maximum" :disabled="disabled" @change="valueChanged" />
      <el-switch v-else-if="type === 'boolean'" :model-value="modelValue.value" :disabled="disabled" active-text="是" inactive-text="否" @change="valueChanged" />
      <el-input v-else-if="modelValue.value !== null" :model-value="modelValue.value" :disabled="disabled" :maxlength="schema.maxLength" placeholder="填写内容" @update:model-value="valueChanged" />
      <small v-else>将传递空值 null（不同于不填写）</small>
    </template>
    <template v-if="modelValue?.mode === 'JSON'">
      <el-input type="textarea" :rows="5" :model-value="modelValue.json" :disabled="disabled" placeholder='例如：{"name":"示例","items":[[1,2]]}' @update:model-value="emit({...modelValue, json: $event})" />
      <small>用于未声明结构、联合类型或整份固定数据；格式错误时不能通过发布校验。</small>
    </template>
    <template v-if="modelValue?.mode === 'OBJECT' && depth < 20">
      <WorkflowSubWorkflowInput v-for="(child, key) in schema.properties || {}" :key="key" :label="child.title || key" :schema="child" :model-value="modelValue.fields?.[key]" :fields="fields" :required="(schema.required || []).includes(key)" :depth="depth + 1" :disabled="disabled" @update:model-value="updateField(key, $event)" />
      <small v-if="!Object.keys(schema.properties || {}).length">未声明输入字段，默认传递空对象；也可选择上游完整数据或填写高级 JSON。</small>
    </template>
    <template v-if="modelValue?.mode === 'ARRAY' && depth < 20">
      <div v-for="(item, index) in modelValue.items || []" :key="index" class="child-array-item">
        <WorkflowSubWorkflowInput :label="`第 ${index + 1} 项`" :schema="schema.items || {}" :model-value="item" :fields="fields" required :depth="depth + 1" :disabled="disabled" @update:model-value="updateItem(index, $event)" />
        <el-button type="danger" text :disabled="disabled" @click="removeItem(index)">删除此项</el-button>
      </div>
      <el-button plain type="primary" :disabled="disabled || (modelValue.items || []).length >= Math.min(schema.maxItems || 1000, 1000)" @click="addItem">添加一项</el-button>
    </template>
  </section>
</template>

<script setup>
import {computed, useId} from 'vue'
import {compatibleType, initialInput, schemaType} from './workflowSubWorkflow'

const props = defineProps({schema: {type: Object, default: () => ({})}, modelValue: Object, label: String, fields: {type: Array, default: () => []}, required: Boolean, disabled: Boolean, depth: {type: Number, default: 0}})
const events = defineEmits(['update:modelValue'])
const controlId = useId()
const type = computed(() => schemaType(props.schema) || (props.depth === 0 ? 'object' : ''))
const emit = value => events('update:modelValue', value)
function changeMode(mode) {
  if (mode === 'NULL') emit({mode: 'VALUE', value: null})
  else if (mode === 'JSON') emit({mode, json: JSON.stringify(props.schema.default ?? (type.value === 'array' ? [] : type.value === 'object' ? {} : ''), null, 2)})
  else if (mode === 'OBJECT') emit(initialInput({...props.schema, type: 'object'}))
  else if (mode === 'ARRAY') emit({mode, items: []})
  else if (mode === 'VALUE') emit({mode, value: props.schema.default ?? (type.value === 'boolean' ? false : ['integer', 'number'].includes(type.value) ? 0 : '')})
  else emit({mode})
}
const valueChanged = value => emit({...props.modelValue, value})
const updateField = (key, value) => emit({...props.modelValue, fields: {...props.modelValue.fields, [key]: value}})
function updateItem(index, value) { const items = [...props.modelValue.items]; items[index] = value; emit({...props.modelValue, items}) }
const removeItem = index => emit({...props.modelValue, items: props.modelValue.items.filter((_, i) => i !== index)})
const addItem = () => emit({...props.modelValue, items: [...props.modelValue.items, initialInput(props.schema.items || {})]})
</script>

<style scoped>
.child-input {display:grid;gap:10px;min-width:0}.child-input--nested {border-left:2px solid var(--el-border-color);padding:12px 0 12px 16px}.child-input-heading {display:flex;align-items:center;gap:16px;justify-content:space-between}.child-input-heading label {font-weight:600;overflow-wrap:anywhere}.child-input-heading .el-select {width:190px;flex-shrink:0}.child-input small {color:var(--el-text-color-secondary)}.required {font-size:12px;color:var(--el-color-danger)}.child-array-item {border-bottom:1px solid var(--el-border-color-lighter);padding-bottom:8px}@media(max-width:700px){.child-input-heading{align-items:stretch;flex-direction:column;gap:6px}.child-input-heading .el-select{width:100%}}
</style>
