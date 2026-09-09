<template>
  <section class="loop-editor">
    <header class="loop-heading">
      <div>
        <strong>循环处理</strong>
        <small>选择循环内容和结束规则，系统自动维护循环连线</small>
      </div>
      <el-tag :type="issues.length ? 'warning' : 'success'" effect="plain">
        {{ issues.length ? `${issues.length} 项待完成` : '配置完成' }}
      </el-tag>
    </header>

    <section class="loop-section loop-mode-section">
      <div class="section-heading"><b>1. 选择循环方式</b><small>大多数数据处理请选择“逐项处理数据”</small></div>
      <el-radio-group :model-value="mode" class="loop-mode-switch" :disabled="disabled" @change="modeChanged">
        <el-radio-button value="FOR_EACH">
          <strong>逐项处理数据</strong><small>依次处理数组中的每一项</small>
        </el-radio-button>
        <el-radio-button value="REPEAT">
          <strong>重复执行任务</strong><small>按次数或条件重复运行</small>
        </el-radio-button>
      </el-radio-group>

      <label v-if="mode === 'FOR_EACH'" class="field-block">
        <span>要处理的数据列表</span>
        <el-select
          :model-value="sourceExpression"
          filterable
          allow-create
          default-first-option
          clearable
          :disabled="disabled"
          placeholder="选择流程输入或上游节点中的数组字段"
          style="width: 100%"
          @change="sourceChanged"
        >
          <el-option-group v-for="group in arraySourceGroups" :key="group.id" :label="group.label">
            <el-option v-for="option in group.options" :key="option.expression" :label="option.label" :value="option.expression">
              <span>{{ option.label }}</span><small class="option-type">数组</small>
            </el-option>
          </el-option-group>
        </el-select>
        <small v-if="sourceExpression && !sourceValid" class="field-warning">请输入以 $.input、$.nodes、$.context 或 $.loop 开头的数据路径。</small>
        <small v-else-if="!arraySourceGroups.length">尚未发现数组字段，可先试运行上游节点，或直接输入数据路径。</small>
        <small v-else>数组元素可以是文本、数字、对象或嵌套数组。</small>
      </label>

      <template v-else>
        <label class="field-block">
          <span>停止方式</span>
          <el-radio-group :model-value="repeatMode" :disabled="disabled" @change="repeatModeChanged">
            <el-radio value="COUNT">执行指定次数</el-radio>
            <el-radio value="UNTIL">满足条件时停止</el-radio>
          </el-radio-group>
        </label>
        <label v-if="repeatMode === 'COUNT'" class="field-block compact-number">
          <span>执行次数</span>
          <el-input-number :model-value="count" :min="1" :max="1000" :disabled="disabled" @change="updateConfig({count: $event, maxIterations: Math.max(maxIterations, Number($event || 1))})" />
          <small>循环体会完整执行 {{ count }} 次。</small>
        </label>
        <div v-else class="condition-builder">
          <div class="condition-copy"><strong>每轮完成后，当以下条件成立时停止</strong><small>默认至少执行一次</small></div>
          <div class="condition-fields">
            <el-select :model-value="conditionField" filterable allow-create default-first-option :disabled="disabled" placeholder="选择或输入本轮结果字段" @change="conditionChanged({conditionField: $event})">
              <el-option v-for="option in conditionOptions" :key="option.expression" :label="option.label" :value="option.expression" />
            </el-select>
            <el-select :model-value="conditionOperator" :disabled="disabled" @change="conditionChanged({conditionOperator: $event})">
              <el-option label="等于" value="==" /><el-option label="不等于" value="!=" />
              <el-option label="大于" value=">" /><el-option label="大于等于" value=">=" />
              <el-option label="小于" value="<" /><el-option label="小于等于" value="<=" />
            </el-select>
            <el-select :model-value="conditionValueType" :disabled="disabled" @change="conditionChanged({conditionValueType: $event})">
              <el-option label="文本" value="string" /><el-option label="数字" value="number" />
              <el-option label="是/否" value="boolean" /><el-option label="空值" value="null" />
            </el-select>
            <el-select v-if="conditionValueType === 'boolean'" :model-value="String(conditionValue)" :disabled="disabled" @change="conditionChanged({conditionValue: $event === 'true'})">
              <el-option label="是" value="true" /><el-option label="否" value="false" />
            </el-select>
            <el-input v-else-if="conditionValueType !== 'null'" :model-value="String(conditionValue ?? '')" :disabled="disabled" :placeholder="conditionValueType === 'number' ? '输入数字' : '输入比较内容'" @input="conditionChanged({conditionValue: $event})" />
            <div v-else class="null-value">空值</div>
          </div>
          <small v-if="stopCondition" class="condition-preview">判断规则：{{ stopCondition }}</small>
        </div>
      </template>
    </section>

    <section class="loop-section">
      <div class="section-heading"><b>2. 设置每轮执行范围</b><small>选择入口和代表本轮完成的节点；返回路径由系统自动维护</small></div>
      <div class="route-grid">
        <label>
          <span>每次执行从这里开始</span>
          <el-select :model-value="bodyTarget" filterable clearable :disabled="disabled" placeholder="选择循环体入口" @change="$emit('update:body-target', $event || '')">
            <el-option v-for="node in bodyOptions" :key="node.id" :label="node.name" :value="node.id" />
          </el-select>
          <small>每一轮都会先进入这个节点。</small>
        </label>
        <label>
          <span>这个节点完成代表本轮结束</span>
          <el-select
            :model-value="resultNodeId"
            filterable
            clearable
            :disabled="disabled || !bodyTarget"
            :placeholder="bodyTarget ? '选择本轮最后节点' : '请先选择循环体入口'"
            @change="resultNodeChanged"
          >
            <el-option v-for="node in resultOptions" :key="node.id" :label="node.name" :value="node.id" />
            <template #empty>
              <div class="loop-result-empty">
                <strong>暂无可选末节点</strong>
                <small>请把每条循环分支连接并汇入同一个最后节点。</small>
              </div>
            </template>
          </el-select>
          <small v-if="bodyTarget && resultOptions.length">系统会收集该节点的输出，并自动开始下一轮。</small>
          <small v-else-if="bodyTarget" class="field-warning">当前路径没有统一末节点，请先补全或汇合循环内连线。</small>
          <small v-else>选择入口后，系统会自动列出合法末节点。</small>
        </label>
        <label>
          <span>全部完成后进入</span>
          <el-select :model-value="exitTarget" filterable clearable :disabled="disabled" placeholder="选择后续节点" @change="$emit('update:exit-target', $event || '')">
            <el-option v-for="node in exitOptions" :key="node.id" :label="node.name" :value="node.id" />
          </el-select>
          <small>所有数据处理完成后只进入一次。</small>
        </label>
      </div>
    </section>

    <section class="loop-section result-section">
      <div class="section-heading"><b>3. 选择循环结果</b><small>下游节点可以直接读取循环摘要、结果和错误</small></div>
      <el-radio-group :model-value="resultMode" :disabled="disabled" @change="updateConfig({resultMode: $event})">
        <el-radio value="COLLECT">收集每轮结果</el-radio>
        <el-radio value="LAST">只保留最后结果</el-radio>
        <el-radio value="NONE">不保存结果</el-radio>
      </el-radio-group>
    </section>

    <el-alert v-if="issues.length" :title="issues[0]" :description="issues.length > 1 ? `另外还有 ${issues.length - 1} 项未完成` : ''" type="warning" :closable="false" show-icon />

    <el-collapse class="loop-advanced">
      <el-collapse-item name="advanced">
        <template #title><span><strong>高级选项</strong><small>安全上限、异常处理和空数据策略</small></span></template>
        <div class="advanced-grid">
          <label><span>最大循环次数</span><el-input-number :model-value="maxIterations" :min="1" :max="1000" :disabled="disabled" @change="updateConfig({maxIterations: $event})" /><small>无论采用哪种模式，都不会超过此次数。</small></label>
          <label v-if="resultMode === 'COLLECT'"><span>最多保存结果数</span><el-input-number :model-value="maxResults" :min="1" :max="1000" :disabled="disabled" @change="updateConfig({maxResults: $event})" /></label>
          <label><span>某一项失败时</span><el-select :model-value="itemErrorPolicy" :disabled="disabled" @change="updateConfig({itemErrorPolicy: $event})"><el-option label="停止整个工作流" value="FAIL" /><el-option label="跳过并继续" value="SKIP" :disabled="!allowContinueOnError" /><el-option label="记录错误并继续" value="COLLECT" :disabled="!allowContinueOnError" /></el-select><small v-if="!allowContinueOnError" class="field-warning">{{ continueOnErrorReason || '循环体包含写操作，为避免部分数据已写入，只能在失败时停止。' }}</small></label>
          <label v-if="mode === 'FOR_EACH'"><span>数据列表为空时</span><el-select :model-value="emptyPolicy" :disabled="disabled" @change="updateConfig({emptyPolicy: $event})"><el-option label="直接完成循环" value="COMPLETE" /><el-option label="作为错误停止" value="FAIL" /></el-select></label>
          <label v-if="mode === 'REPEAT' && repeatMode === 'UNTIL'" class="check-before"><el-switch :model-value="checkBeforeFirst" :disabled="disabled" @change="updateConfig({checkBeforeFirst: $event})" /><span>第一次执行前先判断条件</span><small>开启后，条件已满足时可能一次也不执行。</small></label>
        </div>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
export default {
  name: 'WorkflowLoopEditor',
  props: {
    config: {type: Object, default: () => ({})}, inputMapping: {type: Object, default: () => ({})},
    sourceGroups: {type: Array, default: () => []}, conditionOptions: {type: Array, default: () => []},
    bodyOptions: {type: Array, default: () => []}, resultOptions: {type: Array, default: () => []}, exitOptions: {type: Array, default: () => []},
    bodyTarget: {type: String, default: ''}, exitTarget: {type: String, default: ''}, disabled: {type: Boolean, default: false},
    allowContinueOnError: {type: Boolean, default: true}, continueOnErrorReason: {type: String, default: ''}
  },
  emits: ['update:config', 'update:input-mapping', 'update:body-target', 'update:exit-target'],
  computed: {
    mode() { return this.config?.mode || 'FOR_EACH' }, repeatMode() { return this.config?.repeatMode || 'COUNT' },
    count() { return Number(this.config?.count || 3) }, maxIterations() { return Number(this.config?.maxIterations || 100) },
    resultMode() { return this.config?.resultMode || (this.mode === 'FOR_EACH' ? 'COLLECT' : 'LAST') },
    resultNodeId() { return this.config?.resultNodeId || '' }, maxResults() { return Number(this.config?.maxResults || 1000) },
    itemErrorPolicy() { return this.config?.itemErrorPolicy || 'FAIL' }, emptyPolicy() { return this.config?.emptyPolicy || 'COMPLETE' },
    checkBeforeFirst() { return !!this.config?.checkBeforeFirst }, sourceExpression() { return this.inputMapping?.items?.expression || '' },
    arraySourceGroups() { return this.sourceGroups.map(group => ({...group, options: (group.options || []).filter(item => item.type === 'array')})).filter(group => group.options.length) },
    sourceValid() {
      return this.arraySourceGroups.some(group => group.options.some(item => item.expression === this.sourceExpression))
        || /^\$\.(input|nodes|context|loop)(?:[.\[].*)?$/.test(this.sourceExpression)
    },
    conditionField() { return this.config?.conditionField || '' }, conditionOperator() { return this.config?.conditionOperator || '==' },
    conditionValueType() { return this.config?.conditionValueType || 'string' }, conditionValue() { return this.config?.conditionValue ?? '' },
    stopCondition() { return this.config?.stopCondition || '' },
    issues() {
      const result = []
      if (this.mode === 'FOR_EACH' && (!this.sourceExpression || !this.sourceValid)) result.push('请选择有效的数组数据来源')
      if (this.mode === 'REPEAT' && this.repeatMode === 'UNTIL' && (!this.conditionField || !this.stopCondition)) result.push('请设置完整的停止条件')
      if (!this.bodyTarget) result.push('请选择循环体入口节点')
      if (!this.resultNodeId) result.push('请选择代表本轮完成的节点')
      if (!this.exitTarget) result.push('请选择循环完成后的节点')
      if (this.bodyTarget && this.bodyTarget === this.exitTarget) result.push('循环体入口和完成后节点不能相同')
      return result
    }
  },
  methods: {
    updateConfig(patch) { this.$emit('update:config', {...this.config, version: 2, ...patch}) },
    modeChanged(mode) { this.updateConfig({mode, resultMode: mode === 'FOR_EACH' ? 'COLLECT' : 'LAST', maxIterations: mode === 'FOR_EACH' ? 100 : Math.max(this.count, 10)}) },
    repeatModeChanged(repeatMode) { this.updateConfig({repeatMode}) },
    sourceChanged(expression) { const mapping = {...this.inputMapping}; if (expression) mapping.items = {expression}; else delete mapping.items; this.$emit('update:input-mapping', mapping) },
    resultNodeChanged(resultNodeId) { this.updateConfig({resultNodeId: resultNodeId || ''}) },
    conditionChanged(patch) {
      const next = {...this.config, ...patch}
      const field = next.conditionField || ''
      const operator = next.conditionOperator || '=='
      const type = next.conditionValueType || 'string'
      let literal = 'null'
      if (type === 'string') literal = JSON.stringify(String(next.conditionValue ?? ''))
      if (type === 'number') literal = String(next.conditionValue ?? '')
      if (type === 'boolean') literal = String(next.conditionValue === true || next.conditionValue === 'true')
      const validNumber = type !== 'number' || /^-?\d+(\.\d+)?$/.test(literal)
      next.stopCondition = field && validNumber ? `${field} ${operator} ${literal}` : ''
      this.updateConfig(next)
    }
  }
}
</script>

<style scoped>
.loop-editor{display:grid;gap:14px}.loop-heading,.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.loop-heading>div,.section-heading{min-width:0}.loop-heading strong{font-size:17px}.loop-heading small,.section-heading small,.field-block>small,.advanced-grid small{display:block;margin-top:4px;color:var(--el-text-color-secondary)}.loop-section{padding:16px;border:1px solid var(--workflow-border,#dfe3ef);border-radius:14px;background:var(--workflow-surface-raised,#fff)}.section-heading{margin-bottom:14px}.section-heading b{font-size:14px}.loop-mode-switch{display:grid;grid-template-columns:1fr 1fr;width:100%;margin-bottom:16px}.loop-mode-switch :deep(.el-radio-button__inner){width:100%;min-height:62px;display:flex;flex-direction:column;align-items:flex-start;justify-content:center;box-shadow:none}.loop-mode-switch strong,.loop-mode-switch small{display:block}.loop-mode-switch small{margin-top:4px;color:inherit;opacity:.72}.field-block{display:block}.field-block>span,.route-grid label>span,.advanced-grid label>span{display:block;margin-bottom:7px;font-weight:600}.compact-number{max-width:360px}.condition-builder{padding:14px;border-radius:12px;background:var(--workflow-primary-soft,var(--el-color-primary-light-9))}.condition-copy{display:flex;justify-content:space-between;margin-bottom:10px}.condition-copy small{color:var(--el-text-color-secondary)}.condition-fields{display:grid;grid-template-columns:minmax(220px,2fr) 120px 100px minmax(150px,1fr);gap:8px}.condition-preview{display:block;margin-top:10px;color:var(--workflow-primary,var(--el-color-primary));word-break:break-all}.null-value{height:32px;display:flex;align-items:center;padding:0 11px;border:1px dashed var(--el-border-color);border-radius:4px;color:var(--el-text-color-secondary)}.route-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.route-grid label{min-width:0}.route-grid label>small{display:block;min-height:32px;margin-top:7px;color:var(--el-text-color-secondary);line-height:1.45}.route-grid :deep(.el-select){width:100%}.loop-result-empty{display:grid;gap:4px;padding:12px 16px;text-align:left}.loop-result-empty strong{color:var(--el-text-color-primary);font-size:13px}.loop-result-empty small{color:var(--el-text-color-secondary);line-height:1.45}.result-section :deep(.el-radio-group){display:flex;gap:18px}.loop-editor :deep(.el-radio){--el-radio-text-color:var(--workflow-text,var(--el-text-color-primary));--el-radio-checked-text-color:var(--workflow-primary,var(--el-color-primary));color:var(--workflow-text,var(--el-text-color-primary))!important}.loop-editor :deep(.el-radio .el-radio__label){color:var(--workflow-text,var(--el-text-color-primary))!important}.loop-editor :deep(.el-radio .el-radio__inner){border-color:var(--workflow-control-border,var(--el-border-color))!important;background:var(--workflow-surface-raised,var(--el-bg-color-overlay))!important}.loop-editor :deep(.el-radio.is-checked .el-radio__label){color:var(--workflow-primary,var(--el-color-primary))!important}.loop-editor :deep(.el-radio.is-checked .el-radio__inner){border-color:var(--workflow-primary,var(--el-color-primary))!important;background:var(--workflow-primary,var(--el-color-primary))!important}.loop-editor :deep(.el-radio.is-checked .el-radio__inner::after){background:#fff!important}.loop-editor :deep(.el-radio.is-disabled .el-radio__label){color:var(--workflow-control-disabled-text,var(--el-text-color-placeholder))!important}.loop-editor :deep(.el-radio.is-disabled.is-checked .el-radio__label){color:color-mix(in srgb,var(--workflow-primary,var(--el-color-primary)) 72%,var(--workflow-text-secondary,var(--el-text-color-secondary)))!important}.loop-editor :deep(.el-radio.is-disabled.is-checked .el-radio__inner){border-color:color-mix(in srgb,var(--workflow-primary,var(--el-color-primary)) 72%,var(--workflow-control-border,var(--el-border-color)))!important;background:color-mix(in srgb,var(--workflow-primary,var(--el-color-primary)) 72%,var(--workflow-surface-raised,var(--el-bg-color-overlay)))!important}.loop-editor :deep(.el-radio.is-checked .el-radio__inner::after){background:#fff!important}.loop-advanced{border:1px solid var(--workflow-border,#dfe3ef);border-radius:12px;overflow:hidden}.loop-advanced :deep(.el-collapse-item__header){height:auto;min-height:58px;padding:0 16px}.loop-advanced :deep(.el-collapse-item__header span small){display:block;margin-top:2px;color:var(--el-text-color-secondary);font-weight:400}.loop-advanced :deep(.el-collapse-item__content){padding:4px 16px 16px}.advanced-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.advanced-grid label{min-width:0}.advanced-grid :deep(.el-input-number),.advanced-grid :deep(.el-select){width:100%}.check-before{display:grid!important;grid-template-columns:auto 1fr;align-items:center;column-gap:9px}.check-before small{grid-column:2}.option-type{float:right;color:var(--el-text-color-secondary)}.field-warning{color:var(--el-color-warning)!important}@media(max-width:900px){.condition-fields,.route-grid,.advanced-grid{grid-template-columns:1fr}.loop-mode-switch{grid-template-columns:1fr}}
</style>
