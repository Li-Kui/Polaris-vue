<template>
  <section class="knowledge-test-result">
    <header>
      <div><strong>{{ view.hasResults ? `找到 ${view.count} 条相关资料` : '没有找到相关资料' }}</strong><small>{{ summary }}</small></div>
      <el-tag :type="view.hasResults ? 'success' : 'info'" effect="plain">{{ view.hasResults ? '有结果' : '空结果' }}</el-tag>
    </header>
    <small v-if="view.hasResults" class="knowledge-test-result__score-hint">
      向量匹配分表示文字语义接近程度，不代表资料中的对象和结论一定适用于当前问题，请结合内容判断。
    </small>
    <el-alert v-if="view.degraded" title="本次检索发生降级，请检查检索详情后再用于正式流程。" type="warning" :closable="false" show-icon />
    <div v-if="view.results.length" class="knowledge-test-result__list">
      <article v-for="item in view.results" :key="item.id">
        <div class="knowledge-test-result__rank">{{ item.rank }}</div>
        <div class="knowledge-test-result__copy">
          <div><strong>{{ item.documentName }}</strong><el-tag size="small" :type="relevanceType(item.relevanceLevel)" effect="plain">{{ relevanceLabel(item.relevanceLevel) }}</el-tag></div>
          <p>{{ item.content }}</p>
          <small>{{ item.sourceName }} · 向量匹配分 {{ scoreLabel(item.score) }}</small>
        </div>
      </article>
    </div>
    <div v-else class="knowledge-test-result__empty">可尝试换一种问法、选择更多知识库，或将检索偏好改为“更全面”。</div>
  </section>
</template>

<script>
import {knowledgeTestResultView} from './workflowKnowledgeRetrieval'

export default {
  name: 'WorkflowKnowledgeTestResult',
  props: {output: {type: Object, default: () => ({})}},
  computed: {
    view() { return knowledgeTestResultView(this.output) },
    summary() {
      const tokenUsage = this.view.budgetTokens
        ? `约 ${this.view.tokenCount}/${this.view.budgetTokens} Token`
        : `约 ${this.view.tokenCount} Token`
      const values = [`检索 ${this.view.sourceCount} 个知识库`, `${this.view.durationMs} ms`, tokenUsage]
      values.push(...this.view.limitationLabels)
      return values.join(' · ')
    }
  },
  methods: {
    relevanceLabel(value) { return {HIGH: '匹配较高', MEDIUM: '匹配一般', LOW: '匹配较低'}[value] || '匹配较低' },
    relevanceType(value) { return value === 'HIGH' ? 'success' : value === 'MEDIUM' ? 'primary' : 'info' },
    scoreLabel(value) { return `${Math.round(Math.max(0, Math.min(1, value)) * 100)}%` }
  }
}
</script>

<style scoped lang="scss">
.knowledge-test-result { display: flex; flex-direction: column; gap: 10px; }
.knowledge-test-result > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.knowledge-test-result > header strong, .knowledge-test-result > header small { display: block; }
.knowledge-test-result > header small { margin-top: 3px; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 11px; }
.knowledge-test-result__score-hint { color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 10px; line-height: 1.5; }
.knowledge-test-result__list { display: flex; flex-direction: column; gap: 8px; max-height: 380px; overflow: auto; }
.knowledge-test-result__list article { display: flex; align-items: flex-start; gap: 10px; padding: 12px; border: 1px solid var(--workflow-border, var(--el-border-color-lighter)); border-radius: 10px; background: var(--workflow-surface, var(--el-bg-color)); }
.knowledge-test-result__rank { width: 24px; height: 24px; flex: 0 0 auto; display: flex; align-items: center; justify-content: center; border-radius: 50%; color: var(--workflow-primary, var(--el-color-primary)); background: var(--workflow-primary-soft, var(--el-color-primary-light-9)); font-size: 11px; font-weight: 700; }
.knowledge-test-result__copy { min-width: 0; flex: 1; }
.knowledge-test-result__copy > div { display: flex; align-items: center; gap: 7px; }
.knowledge-test-result__copy p { margin: 7px 0; color: var(--workflow-text, var(--el-text-color-primary)); font-size: 12px; line-height: 1.6; white-space: pre-wrap; }
.knowledge-test-result__copy small { color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 10px; }
.knowledge-test-result__empty { padding: 22px; border: 1px dashed var(--workflow-border-strong, var(--el-border-color)); border-radius: 10px; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 12px; text-align: center; }
</style>
