<template>
  <ol class="runtime-record-list" aria-label="数据记录">
    <li v-for="(row, index) in rows" :key="index" class="record-row">
      <dl>
        <div v-for="field in fields" :key="field" class="record-field">
          <dt>{{ workflowDisplayLabel(field) }}</dt>
          <dd><RuntimeObjectValue :value="row[field]" /></dd>
        </div>
      </dl>
    </li>
  </ol>
</template>

<script setup>
import {workflowDisplayLabel} from '@/utils/workflowDisplayLabels'
import {computed} from 'vue'
import RuntimeObjectValue from './RuntimeObjectValue.vue'

const props = defineProps({ rows: { type: Array, default: () => [] } })
const fields = computed(() => [...new Set(props.rows.flatMap(row => Object.keys(row || {})))])
</script>

<style scoped>
.runtime-record-list { list-style: none; margin: 0; padding: 0; }
.record-row { padding: 16px 0; border-bottom: 1px solid var(--runtime-border); }
.record-row:first-child { padding-top: 0; }
.record-row:last-child { border-bottom: 0; }
dl { display: grid; gap: 9px; margin: 0; }
.record-field { display: grid; grid-template-columns: minmax(64px, 30%) minmax(0, 1fr); gap: 12px; font-size: 13px; line-height: 1.6; }
dt { color: var(--runtime-secondary); overflow-wrap: anywhere; }
dd { min-width: 0; margin: 0; color: var(--runtime-text); text-align: right; overflow-wrap: anywhere; }
.record-field:first-child dd { font-size: 14px; font-weight: 600; }
dd :deep(.object-field) { padding: 8px; text-align: left; }
</style>
