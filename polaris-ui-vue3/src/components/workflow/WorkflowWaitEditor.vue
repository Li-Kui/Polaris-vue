<template>
  <section class="wait-editor">
    <header class="wait-heading">
      <div><strong>暂停流程</strong><small>到达恢复时间后，流程会从这里继续执行</small></div>
      <el-tag :type="state.tone" effect="plain">{{ state.label }}</el-tag>
    </header>

    <section class="wait-card">
      <div class="section-heading"><b>1. 选择等待方式</b><small>等待一段时间适合延迟处理；等到某时刻适合预约执行</small></div>
      <el-radio-group :model-value="mode" class="mode-switch" :disabled="disabled" @change="modeChanged">
        <el-radio-button value="AFTER"><strong>等待一段时间</strong><small>例如 5 分钟后继续</small></el-radio-button>
        <el-radio-button value="AT"><strong>等到指定时间</strong><small>例如明天 09:00 继续</small></el-radio-button>
      </el-radio-group>
    </section>

    <section class="wait-card">
      <div class="section-heading"><b>2. 设置恢复时间</b><small>{{ mode === 'AFTER' ? '时长可以固定，也可以来自上游节点' : '时间可以固定，也可以来自上游节点' }}</small></div>
      <el-radio-group :model-value="sourceType" :disabled="disabled" @change="sourceTypeChanged">
        <el-radio value="FIXED">我来设置</el-radio>
        <el-radio value="INPUT">使用上游字段</el-radio>
      </el-radio-group>

      <div v-if="mode === 'AFTER' && sourceType === 'FIXED'" class="duration-row">
        <el-input-number :model-value="source.value" :min="1" :max="amountMaximum" :disabled="disabled" @change="sourceChanged({value: Number($event || 1)})" />
        <el-select :model-value="source.unit" :disabled="disabled" @change="sourceChanged({unit: $event})">
          <el-option label="秒" value="SECOND" /><el-option label="分钟" value="MINUTE" />
          <el-option label="小时" value="HOUR" /><el-option label="天" value="DAY" />
        </el-select>
      </div>

      <label v-else-if="mode === 'AFTER'" class="field-block">
        <span>等待时长来自</span>
        <el-select :model-value="mappingExpression" filterable allow-create clearable default-first-option :disabled="disabled" placeholder="选择数字或整数字段" @change="mappingChanged">
          <el-option-group v-for="group in durationGroups" :key="group.id" :label="group.label">
            <el-option v-for="option in group.options" :key="option.expression" :label="option.label" :value="option.expression" />
          </el-option-group>
        </el-select>
        <small>字段值必须是非负整数，单位使用下方设置。</small>
        <el-select class="dynamic-unit" :model-value="source.unit" :disabled="disabled" @change="sourceChanged({unit: $event})">
          <el-option label="秒" value="SECOND" /><el-option label="分钟" value="MINUTE" />
          <el-option label="小时" value="HOUR" /><el-option label="天" value="DAY" />
        </el-select>
      </label>

      <template v-else>
        <label v-if="sourceType === 'FIXED'" class="field-block">
          <span>目标日期和时间</span>
          <el-date-picker :model-value="source.localDateTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" :disabled="disabled" placeholder="选择日期和时间" style="width:100%" @change="sourceChanged({localDateTime: $event || ''})" />
        </label>
        <label v-else class="field-block">
          <span>目标时间来自</span>
          <el-select :model-value="mappingExpression" filterable allow-create clearable default-first-option :disabled="disabled" placeholder="选择日期时间字段" @change="mappingChanged">
            <el-option-group v-for="group in timeGroups" :key="group.id" :label="group.label">
              <el-option v-for="option in group.options" :key="option.expression" :label="option.label" :value="option.expression" />
            </el-option-group>
          </el-select>
          <small>支持 ISO 日期时间；不带时区时使用下方业务时区。</small>
        </label>
        <label class="field-block">
          <span>业务时区</span>
          <el-select :model-value="source.timezone" filterable allow-create :disabled="disabled" @change="sourceChanged({timezone: $event})">
            <el-option v-for="zone in timezones" :key="zone.value" :label="zone.label" :value="zone.value" />
          </el-select>
        </label>
        <label class="field-block">
          <span>如果目标时间已经过去</span>
          <el-radio-group :model-value="normalized.pastDuePolicy" :disabled="disabled" @change="updateConfig({pastDuePolicy: $event})">
            <el-radio value="CONTINUE">立即继续</el-radio><el-radio value="FAIL">停止并报错</el-radio>
          </el-radio-group>
        </label>
      </template>
    </section>

    <section class="wait-preview">
      <span>当前设置</span><strong>{{ summary }}</strong>
      <small>单节点测试使用虚拟时间，不会真的等待。</small>
      <el-button type="primary" plain :disabled="disabled || state.code !== 'READY'" @click="$emit('test')">模拟等待</el-button>
    </section>

    <el-alert v-if="state.issues.length" :title="state.issues[0]" type="warning" :closable="false" show-icon />
    <el-collapse class="wait-advanced">
      <el-collapse-item name="safety">
        <template #title><span><strong>安全上限</strong><small>防止错误数据让流程无限等待</small></span></template>
        <label class="field-block"><span>单次最多等待（天）</span><el-input-number :model-value="maximumDays" :min="1" :max="365" :disabled="disabled" @change="maximumChanged" /><small>超过上限时节点会失败，不会静默截断。</small></label>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
import {normalizeWaitConfig, waitConfigurationState, waitSummary} from './workflowWait'

export default {
  name: 'WorkflowWaitEditor',
  props: {config: {type: Object, default: () => ({})}, inputMapping: {type: Object, default: () => ({})}, sourceGroups: {type: Array, default: () => []}, disabled: {type: Boolean, default: false}},
  emits: ['update:config', 'update:input-mapping', 'test'],
  data: () => ({timezones: [
    {label: '中国标准时间（Asia/Shanghai）', value: 'Asia/Shanghai'},
    {label: '协调世界时（UTC）', value: 'UTC'},
    {label: '东京（Asia/Tokyo）', value: 'Asia/Tokyo'},
    {label: '纽约（America/New_York）', value: 'America/New_York'},
    {label: '伦敦（Europe/London）', value: 'Europe/London'}
  ]}),
  computed: {
    normalized() { return normalizeWaitConfig(this.config) },
    mode() { return this.normalized.schedule.kind }, source() { return this.normalized.schedule.source }, sourceType() { return this.source.kind },
    state() { return waitConfigurationState({config: this.normalized, inputMapping: this.inputMapping}) },
    summary() { return waitSummary(this.normalized, this.inputMapping) },
    maximumDays() { return Math.max(1, Math.ceil(this.normalized.safety.maxWaitSeconds / 86400)) },
    amountMaximum() { return Math.max(1, Math.floor(this.normalized.safety.maxWaitSeconds / ({SECOND: 1, MINUTE: 60, HOUR: 3600, DAY: 86400}[this.source.unit] || 1))) },
    mappingExpression() { return this.inputMapping?.[this.mode === 'AFTER' ? 'duration' : 'targetAt']?.expression || '' },
    durationGroups() { return this.filteredGroups(option => option.type === 'integer') },
    timeGroups() { return this.filteredGroups(option => option.type === 'string' || option.format === 'date-time') }
  },
  methods: {
    filteredGroups(predicate) { return this.sourceGroups.map(group => ({...group, options: (group.options || []).filter(predicate)})).filter(group => group.options.length) },
    updateConfig(patch) { this.$emit('update:config', normalizeWaitConfig({...this.normalized, ...patch})) },
    updateSchedule(schedulePatch) { this.updateConfig({schedule: {...this.normalized.schedule, ...schedulePatch}}) },
    sourceChanged(patch) { this.updateSchedule({source: {...this.source, ...patch}}) },
    modeChanged(kind) { this.updateSchedule({kind}); this.clearMappings() },
    sourceTypeChanged(kind) { this.sourceChanged({kind}); if (kind === 'FIXED') this.clearMappings() },
    mappingChanged(expression) {
      const field = this.mode === 'AFTER' ? 'duration' : 'targetAt'
      const mapping = {...this.inputMapping}; delete mapping.duration; delete mapping.targetAt
      if (expression) mapping[field] = {expression}
      this.$emit('update:input-mapping', mapping)
    },
    clearMappings() { const mapping = {...this.inputMapping}; delete mapping.duration; delete mapping.targetAt; this.$emit('update:input-mapping', mapping) },
    maximumChanged(days) { this.updateConfig({safety: {maxWaitSeconds: Math.max(1, Number(days || 1)) * 86400}}) }
  }
}
</script>

<style scoped>
.wait-editor{display:grid;gap:14px}.wait-heading,.section-heading,.wait-preview{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.wait-heading>div,.section-heading{min-width:0}.wait-heading strong{font-size:17px}.wait-heading small,.section-heading small,.field-block small,.wait-preview small,.wait-advanced small{display:block;margin-top:4px;color:var(--el-text-color-secondary)}.wait-card{display:grid;gap:15px;padding:16px;border:1px solid var(--workflow-border,#dfe3ef);border-radius:14px;background:var(--workflow-surface-raised,#fff)}.section-heading b{font-size:14px}.mode-switch{display:grid;grid-template-columns:1fr 1fr;width:100%}.mode-switch :deep(.el-radio-button__inner){width:100%;min-height:62px;display:flex;flex-direction:column;align-items:flex-start;justify-content:center;box-shadow:none}.mode-switch strong,.mode-switch small{display:block}.mode-switch small{margin-top:4px;color:inherit;opacity:.72}.duration-row{display:grid;grid-template-columns:minmax(140px,1fr) 150px;gap:10px;max-width:440px}.duration-row :deep(.el-input-number),.duration-row :deep(.el-select),.field-block :deep(.el-select){width:100%}.field-block{display:block}.field-block>span{display:block;margin-bottom:7px;font-weight:600}.dynamic-unit{max-width:220px;margin-top:10px}.wait-preview{align-items:center;padding:16px;border-radius:14px;background:var(--workflow-primary-soft,var(--el-color-primary-light-9))}.wait-preview>span{font-size:12px;color:var(--el-text-color-secondary)}.wait-preview>strong{flex:1}.wait-advanced{border:1px solid var(--workflow-border,#dfe3ef);border-radius:12px;overflow:hidden}.wait-advanced :deep(.el-collapse-item__header){height:auto;min-height:58px;padding:0 16px}.wait-advanced :deep(.el-collapse-item__content){padding:4px 16px 16px}@media(max-width:760px){.mode-switch,.duration-row{grid-template-columns:1fr}.wait-preview{align-items:flex-start;flex-direction:column}}
</style>
