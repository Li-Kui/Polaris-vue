<template>
  <div class="knowledge-editor">
    <section class="knowledge-editor__summary">
      <span class="knowledge-editor__icon"><el-icon><Collection /></el-icon></span>
      <span>
        <strong>检索 {{ resourceCount }} 个知识库</strong>
        <small>选择偏好即可，分数阈值和候选数量由系统统一维护</small>
      </span>
      <el-button plain :disabled="disabled" @click="$emit('select-sources')">更改范围</el-button>
    </section>

    <section class="knowledge-editor__card">
      <header><div><strong>检索偏好</strong><small>按业务目标选择，不需要理解向量或相似度参数</small></div></header>
      <div class="mode-cards">
        <button v-for="item in modes" :key="item.value" type="button"
          :class="{selected: normalizedConfig.retrievalMode === item.value}"
          :disabled="disabled" @click="update({retrievalMode: item.value})">
          <strong>{{ item.label }}</strong><small>{{ item.description }}</small>
          <em v-if="item.recommended">推荐</em>
        </button>
      </div>
    </section>

    <section class="knowledge-editor__card knowledge-editor__quantity">
      <header><div><strong>返回资料数量</strong><small>最终交给下游节点的资料条数</small></div></header>
      <el-slider :model-value="normalizedConfig.resultLimit" :min="1" :max="20" :step="1"
        show-input :disabled="disabled" @update:model-value="value => update({resultLimit: value})" />
      <small>通常 3–8 条足够回答问题；数量越多，占用的上下文也越多。</small>
    </section>

    <section class="knowledge-editor__input">
      <span><el-icon><Connection /></el-icon></span>
      <div><strong>要检索的内容</strong><small>{{ hasQuery ? '已选择查询内容，可进入第 3 步检查并试查' : '尚未选择流程输入或上游字段' }}</small></div>
      <el-button :type="hasQuery ? 'default' : 'primary'" plain :disabled="disabled" @click="$emit('configure-input')">
        {{ hasQuery ? '检查输入' : '设置查询内容' }}
      </el-button>
    </section>

    <WorkflowDisclosureCard name="knowledge-advanced" title="高级设置" description="大多数场景保持默认即可">
      <el-form-item label="没有找到资料时">
        <el-radio-group :model-value="normalizedConfig.emptyPolicy" :disabled="disabled"
          @update:model-value="value => update({emptyPolicy: value})">
          <el-radio value="CONTINUE">继续流程并返回空结果</el-radio>
          <el-radio value="FAIL">让节点失败</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="部分知识库临时故障时">
        <el-radio-group :model-value="normalizedConfig.degradationPolicy" :disabled="disabled"
          @update:model-value="value => update({degradationPolicy: value})">
          <el-radio value="DENY">停止并提示错误</el-radio>
          <el-radio value="ALLOW">使用其余知识库继续</el-radio>
        </el-radio-group>
        <small class="advanced-hint">继续时结果会明确标记为“不完整”，不会静默忽略故障。</small>
      </el-form-item>
      <el-form-item label="单个文档最多返回">
        <el-input-number :model-value="normalizedConfig.maxChunksPerDocument" :min="1" :max="10"
          :disabled="disabled" @update:model-value="value => update({maxChunksPerDocument: value})" />
        <small class="advanced-hint">避免某一个文档占满全部结果。</small>
      </el-form-item>
      <el-form-item label="上下文长度">
        <el-radio-group :model-value="normalizedConfig.contextBudgetMode" :disabled="disabled"
          @update:model-value="value => update({contextBudgetMode: value})">
          <el-radio value="AUTO">系统自动控制</el-radio>
          <el-radio value="MANUAL">手动设置</el-radio>
        </el-radio-group>
        <el-input-number v-if="normalizedConfig.contextBudgetMode === 'MANUAL'"
          :model-value="normalizedConfig.maxContextTokens" :min="256" :max="32000" :step="256"
          :disabled="disabled" @update:model-value="value => update({maxContextTokens: value})" />
        <small class="advanced-hint">自动模式使用系统安全上限 4000 Token；手动模式适合已明确下游容量的流程。</small>
      </el-form-item>
    </WorkflowDisclosureCard>

    <section :class="['knowledge-editor__readiness', `is-${configuration.tone}`]">
      <el-icon><CircleCheck v-if="!configuration.issues.length" /><WarningFilled v-else /></el-icon>
      <div><strong>{{ configuration.label }}</strong><small>{{ configuration.issues[0] || '配置完整，可以检查输入并试查。' }}</small></div>
    </section>
  </div>
</template>

<script>
import {CircleCheck, Collection, Connection, WarningFilled} from '@element-plus/icons-vue'
import WorkflowDisclosureCard from './WorkflowDisclosureCard.vue'
import {knowledgeRetrievalConfigurationState, normalizeKnowledgeRetrievalConfig} from './workflowKnowledgeRetrieval'

export default {
  name: 'WorkflowKnowledgeRetrievalEditor',
  components: {CircleCheck, Collection, Connection, WarningFilled, WorkflowDisclosureCard},
  props: {
    config: {type: Object, default: () => ({})},
    resources: {type: Array, default: () => []},
    inputMapping: {type: Object, default: () => ({})},
    queryIssue: {type: String, default: ''},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config', 'select-sources', 'configure-input'],
  data() {
    return {modes: [
      {value: 'PRECISE', label: '更精准', description: '宁缺毋滥，适合制度和标准问答'},
      {value: 'BALANCED', label: '均衡', description: '兼顾相关度和覆盖面', recommended: true},
      {value: 'BROAD', label: '更全面', description: '尽量找全，适合探索和调研'}
    ]}
  },
  computed: {
    normalizedConfig() { return normalizeKnowledgeRetrievalConfig(this.config) },
    resourceCount() { return this.resources.length },
    hasQuery() { return !!this.inputMapping?.query },
    configuration() {
      return knowledgeRetrievalConfigurationState({
        resources: this.resources,
        config: this.config,
        inputMapping: this.inputMapping,
        queryIssue: this.queryIssue
      })
    }
  },
  methods: {
    update(value) { this.$emit('update:config', {...this.normalizedConfig, ...value}) }
  }
}
</script>

<style scoped lang="scss">
.knowledge-editor { display: flex; flex-direction: column; gap: 12px; }
.knowledge-editor__summary, .knowledge-editor__input, .knowledge-editor__readiness { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--workflow-border, var(--el-border-color-lighter)); border-radius: 10px; }
.knowledge-editor__summary > span:nth-child(2), .knowledge-editor__input > div, .knowledge-editor__readiness > div { min-width: 0; flex: 1; }
.knowledge-editor__summary strong, .knowledge-editor__summary small, .knowledge-editor__input strong, .knowledge-editor__input small, .knowledge-editor__readiness strong, .knowledge-editor__readiness small { display: block; }
.knowledge-editor__summary small, .knowledge-editor__input small, .knowledge-editor__readiness small, .knowledge-editor__quantity > small { margin-top: 3px; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 11px; line-height: 1.45; }
.knowledge-editor__icon, .knowledge-editor__input > span { width: 34px; height: 34px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 9px; color: var(--workflow-primary, var(--el-color-primary)); background: var(--workflow-primary-soft, var(--el-color-primary-light-9)); }
.knowledge-editor__card { padding: 14px; border: 1px solid var(--workflow-border, var(--el-border-color-lighter)); border-radius: 10px; background: var(--workflow-surface, var(--el-bg-color)); }
.knowledge-editor__card header { margin-bottom: 10px; }
.knowledge-editor__card header strong, .knowledge-editor__card header small { display: block; }
.knowledge-editor__card header small { margin-top: 3px; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 11px; }
.mode-cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.mode-cards button { position: relative; min-height: 86px; padding: 11px; border: 1px solid var(--workflow-border, var(--el-border-color-lighter)); border-radius: 9px; color: var(--workflow-text, var(--el-text-color-primary)); background: var(--workflow-surface-raised, var(--el-bg-color-overlay)); text-align: left; cursor: pointer; }
.mode-cards button.selected { border-color: var(--workflow-primary, var(--el-color-primary)); background: var(--workflow-primary-soft, var(--el-color-primary-light-9)); box-shadow: 0 0 0 1px var(--workflow-primary, var(--el-color-primary)) inset; }
.mode-cards strong, .mode-cards small { display: block; }
.mode-cards small { margin-top: 5px; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 10px; line-height: 1.4; }
.mode-cards em { position: absolute; top: 7px; right: 7px; color: var(--workflow-primary, var(--el-color-primary)); font-size: 9px; font-style: normal; }
.knowledge-editor__readiness.is-success { border-color: var(--el-color-success-light-5); background: var(--el-color-success-light-9); color: var(--el-color-success); }
.knowledge-editor__readiness.is-warning { border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.knowledge-editor__readiness.is-danger { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.advanced-hint { margin-left: 8px; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 11px; }
@media (max-width: 760px) { .mode-cards { grid-template-columns: 1fr; } }
</style>
