<template>
  <div class="policy-grid">
    <div v-for="field in fields" :key="field.key" class="policy-field">
      <div class="policy-label">{{ field.label }}</div>
      <el-select :model-value="mode(field.key)" @change="changeMode(field.key, $event)">
        <el-option label="继承系统默认" value="INHERIT" />
        <el-option label="自定义" value="VALUE" />
      </el-select>
      <el-input-number
        v-if="mode(field.key) === 'VALUE'"
        :model-value="modelValue[field.key]"
        :min="field.min"
        :max="field.max"
        @update:model-value="update(field.key, $event)"
      />
    </div>
  </div>
</template>

<script setup>
const props = defineProps({ modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue'])
const fields = [
  { key: 'maxConcurrency', label: '最大并发', min: 1, max: 10000 },
  { key: 'connectTimeoutMs', label: '连接超时(ms)', min: 100, max: 60000 },
  { key: 'readTimeoutMs', label: '读取超时(ms)', min: 100, max: 300000 },
  { key: 'retryCount', label: '重试次数', min: 0, max: 10 },
  { key: 'qpsLimit', label: 'QPS', min: 0.0001, max: 10000 },
  { key: 'priority', label: '优先级', min: -10000, max: 10000 }
]
function mode(key) { return props.modelValue[key] === null || props.modelValue[key] === undefined ? 'INHERIT' : 'VALUE' }
function changeMode(key, next) { update(key, next === 'INHERIT' ? null : (key === 'retryCount' ? 0 : 1)) }
function update(key, value) { emit('update:modelValue', { ...props.modelValue, [key]: value }) }
</script>

<style scoped>
.policy-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.policy-field { display: grid; gap: 6px; }
.policy-label { color: var(--mc-text); font-size: 13px; }
.policy-field :deep(.el-input-number), .policy-field :deep(.el-select) { width: 100%; }
@media (max-width: 1000px) { .policy-grid { grid-template-columns: 1fr 1fr; } }
</style>
