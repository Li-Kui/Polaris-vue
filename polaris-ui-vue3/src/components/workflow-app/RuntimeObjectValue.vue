<template>
  <dl v-if="value && typeof value === 'object' && !Array.isArray(value) && depth < 8" class="object-value">
    <div v-for="(item, key) in value" :key="key" class="object-field">
      <dt>{{ key }}</dt>
      <dd><RuntimeObjectValue :value="item" :depth="depth + 1" /></dd>
    </div>
  </dl>
  <ol v-else-if="Array.isArray(value) && depth < 8" class="array-value">
    <li v-for="(item, index) in value" :key="index"><RuntimeObjectValue :value="item" :depth="depth + 1" /></li>
  </ol>
  <span v-else class="scalar-value">{{ formatValue(value) }}</span>
</template>

<script setup>
defineProps({ value: { default: null }, depth: { type: Number, default: 0 } })
const formatValue = value => value == null ? '—' : typeof value === 'boolean' ? (value ? '是' : '否')
  : typeof value === 'object' ? JSON.stringify(value) : String(value)
</script>

<style scoped>
.object-value { margin: 0; display: grid; gap: 12px; }
.object-field { min-width: 0; padding: 16px; border: 1px solid var(--runtime-border, #dbe2ea); border-radius: 12px; background: var(--runtime-muted, #f1f5f9); }
dt { color: var(--runtime-secondary, #64748b); font-size: 12px; margin-bottom: 8px; overflow-wrap: anywhere; }
dd { margin: 0; color: var(--runtime-text, #1e293b); font-size: 15px; line-height: 1.6; }
.scalar-value { white-space: pre-wrap; overflow-wrap: anywhere; }
.array-value { margin: 0; padding-left: 20px; }
.array-value li + li { margin-top: 10px; }
</style>
