<template>
  <div class="knowledge-picker">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <div class="knowledge-picker__heading">
      <div>
        <strong>选择检索范围</strong>
        <small>可选择多个知识库，节点会合并并去除重复资料</small>
      </div>
      <el-tag :type="selectedResourceIds.length ? 'success' : 'warning'" size="small" effect="plain">
        {{ selectedResourceIds.length ? `已选择 ${selectedResourceIds.length} 个` : '至少选择 1 个' }}
      </el-tag>
    </div>

    <div class="knowledge-picker__tools">
      <el-input v-model="keyword" prefix-icon="Search" clearable placeholder="搜索知识库名称或说明" />
      <el-checkbox v-model="availableOnly">只看可用</el-checkbox>
    </div>

    <div class="knowledge-picker__list" v-loading="loading">
      <button
        v-for="resource in filteredResources"
        :key="resource.resourceId"
        type="button"
        :class="['knowledge-card', {selected: isSelected(resource)}]"
        :aria-pressed="isSelected(resource)"
        :disabled="!canEdit || !resource.available"
        @click="toggle(resource)"
      >
        <span class="knowledge-card__icon"><el-icon><Collection /></el-icon></span>
        <span class="knowledge-card__copy">
          <span class="knowledge-card__title">
            <strong>{{ resource.name || '未命名知识库' }}</strong>
            <el-tag v-if="resource.available" size="small" type="success" effect="plain">可检索</el-tag>
            <el-tag v-else size="small" type="danger" effect="plain">不可用</el-tag>
          </span>
          <small>{{ resource.description || '从这个知识库中查找与问题相关的资料' }}</small>
          <span class="knowledge-card__meta">
            <em>{{ resource.shared ? '系统共享' : '当前租户' }}</em>
            <em v-if="resource.attributes?.indexVersion">索引版本 {{ resource.attributes.indexVersion }}</em>
            <em v-if="!resource.available" class="unavailable">{{ resource.unavailableReason }}</em>
          </span>
        </span>
        <el-icon v-if="isSelected(resource)" class="knowledge-card__state"><CircleCheck /></el-icon>
      </button>

      <div v-if="!loading && !filteredResources.length" class="knowledge-picker__empty">
        <el-icon><Collection /></el-icon>
        <strong>{{ keyword ? '没有匹配的知识库' : '暂无可用知识库' }}</strong>
        <small>{{ keyword ? '请尝试其他关键词' : '请先创建知识库并等待索引完成' }}</small>
      </div>
    </div>

    <el-alert
      v-if="selectedResourceIds.length > 1"
      title="多个知识库会并行检索，再按相关度统一排序；相同资料不会重复返回。"
      type="info"
      :closable="false"
      show-icon
    />
  </div>
</template>

<script>
import {CircleCheck, Collection} from '@element-plus/icons-vue'
import {filterKnowledgeResources} from './workflowKnowledgeRetrieval'

export default {
  name: 'WorkflowKnowledgeSourcePicker',
  components: {CircleCheck, Collection},
  props: {
    resources: {type: Array, default: () => []},
    selectedResourceIds: {type: Array, default: () => []},
    loading: {type: Boolean, default: false},
    canEdit: {type: Boolean, default: false},
    error: {type: String, default: ''}
  },
  emits: ['change'],
  data() {
    return {keyword: '', availableOnly: false}
  },
  computed: {
    filteredResources() {
      return filterKnowledgeResources(this.resources, this.keyword, this.availableOnly)
    }
  },
  methods: {
    isSelected(resource) {
      return this.selectedResourceIds.includes(resource.resourceId)
    },
    toggle(resource) {
      const values = this.isSelected(resource)
        ? this.selectedResourceIds.filter(id => id !== resource.resourceId)
        : [...this.selectedResourceIds, resource.resourceId]
      this.$emit('change', values)
    }
  }
}
</script>

<style scoped lang="scss">
.knowledge-picker { display: flex; flex-direction: column; gap: 12px; }
.knowledge-picker__heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.knowledge-picker__heading strong, .knowledge-picker__heading small { display: block; }
.knowledge-picker__heading strong { font-size: 14px; color: var(--workflow-text, var(--el-text-color-primary)); }
.knowledge-picker__heading small { margin-top: 3px; font-size: 11px; line-height: 1.5; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); }
.knowledge-picker__tools { display: grid; grid-template-columns: minmax(220px, 1fr) auto; align-items: center; gap: 12px; }
.knowledge-picker__list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; min-height: 92px; }
.knowledge-card { min-width: 0; min-height: 104px; padding: 12px; display: flex; align-items: flex-start; gap: 10px; border: 1px solid var(--workflow-border, var(--el-border-color-lighter)); border-radius: 10px; color: var(--workflow-text, var(--el-text-color-primary)); background: var(--workflow-surface, var(--el-bg-color)); text-align: left; cursor: pointer; }
.knowledge-card:hover:not(:disabled), .knowledge-card:focus-visible { border-color: var(--workflow-primary, var(--el-color-primary)); outline: none; }
.knowledge-card.selected { border-color: var(--workflow-primary, var(--el-color-primary)); background: var(--workflow-primary-soft, var(--el-color-primary-light-9)); box-shadow: 0 0 0 1px var(--workflow-primary, var(--el-color-primary)) inset; }
.knowledge-card:disabled { cursor: not-allowed; opacity: .58; }
.knowledge-card__icon { width: 36px; height: 36px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 9px; color: var(--workflow-primary, var(--el-color-primary)); background: var(--workflow-primary-soft, var(--el-color-primary-light-9)); }
.knowledge-card__copy { min-width: 0; flex: 1; }
.knowledge-card__copy > small { display: -webkit-box; margin-top: 5px; overflow: hidden; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); font-size: 11px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.knowledge-card__title, .knowledge-card__meta { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.knowledge-card__title strong { min-width: 0; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-card__meta { margin-top: 7px; }
.knowledge-card__meta em { color: var(--workflow-text-tertiary, var(--el-text-color-placeholder)); font-size: 10px; font-style: normal; }
.knowledge-card__meta em.unavailable { color: var(--el-color-danger); }
.knowledge-card__state { margin-top: 2px; color: var(--workflow-primary, var(--el-color-primary)); }
.knowledge-picker__empty { grid-column: 1 / -1; min-height: 104px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; border: 1px dashed var(--workflow-border-strong, var(--el-border-color)); border-radius: 10px; color: var(--workflow-text-secondary, var(--el-text-color-secondary)); }
.knowledge-picker__empty strong { color: var(--workflow-text, var(--el-text-color-primary)); font-size: 12px; }
.knowledge-picker__empty small { font-size: 11px; }
@media (max-width: 760px) { .knowledge-picker__tools, .knowledge-picker__list { grid-template-columns: 1fr; } }
</style>
