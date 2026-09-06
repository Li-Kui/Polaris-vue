<template>
  <div class="agent-picker">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />

    <div class="agent-step-heading">
      <div>
        <strong>选择智能体</strong>
        <small>选择最符合当前任务的智能体，模型和基础能力由智能体统一管理</small>
      </div>
      <el-tag v-if="selectedResource" type="success" size="small" effect="plain">已选择</el-tag>
    </div>

    <div class="agent-picker-tools">
      <el-input
        v-model="keyword"
        prefix-icon="Search"
        clearable
        aria-label="搜索智能体名称或用途"
        placeholder="搜索智能体名称或用途"
      />
      <el-radio-group v-model="filter" size="small" aria-label="筛选智能体">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="AVAILABLE">可用</el-radio-button>
        <el-radio-button value="WITH_TOOLS">包含工具</el-radio-button>
      </el-radio-group>
    </div>

    <div class="agent-list" v-loading="loading">
      <button
        v-for="resource in filteredResources"
        :key="resource.resourceId"
        type="button"
        :class="['agent-card', {selected: resource.resourceId === selectedResourceId}]"
        :aria-pressed="resource.resourceId === selectedResourceId"
        :disabled="!canEdit || !resource.available"
        @click="$emit('select', resource.resourceId)"
      >
        <span class="agent-avatar"><el-icon><ChatDotRound /></el-icon></span>
        <span class="agent-copy">
          <span class="agent-title-row">
            <strong>{{ resource.name || '未命名智能体' }}</strong>
            <el-tag
              v-for="tag in toolAccess(resource).tags"
              :key="tag.label"
              size="small"
              :type="tag.type"
              effect="plain"
            >{{ tag.label }}</el-tag>
          </span>
          <small>{{ description(resource) }}</small>
          <span class="agent-meta">
            <em v-if="resource.attributes?.modelName">模型由智能体管理</em>
            <em>{{ resource.shared ? '系统共享' : '当前租户' }}</em>
            <em :class="{unavailable: !resource.available}">{{ resource.available ? '可用' : resource.unavailableReason }}</em>
          </span>
        </span>
        <el-icon v-if="resource.resourceId === selectedResourceId" class="agent-state selected"><CircleCheck /></el-icon>
        <el-icon v-else class="agent-state"><ArrowRight /></el-icon>
      </button>

      <div v-if="!loading && !filteredResources.length" class="agent-empty">
        <el-icon><ChatDotRound /></el-icon>
        <strong>{{ keyword ? '没有匹配的智能体' : '暂无可用智能体' }}</strong>
        <small>{{ keyword ? '请尝试其他关键词或筛选条件' : '请先在智能体管理中创建并启用智能体' }}</small>
      </div>
    </div>

    <el-alert
      v-if="selectedToolAccess.hasTools"
      :title="selectedToolAccess.notice"
      :type="selectedToolAccess.alertType"
      :closable="false"
      show-icon
    />
  </div>
</template>

<script>
import {ArrowRight, ChatDotRound, CircleCheck} from '@element-plus/icons-vue'
import {agentToolAccessView, filterAgentResources} from './workflowAgent'

export default {
  name: 'WorkflowAgentPicker',
  components: {ArrowRight, ChatDotRound, CircleCheck},
  props: {
    resources: {type: Array, default: () => []},
    selectedResourceId: {type: String, default: ''},
    selectedResource: {type: Object, default: null},
    loading: {type: Boolean, default: false},
    canEdit: {type: Boolean, default: false},
    error: {type: String, default: ''}
  },
  emits: ['select'],
  data() {
    return {keyword: '', filter: 'ALL'}
  },
  computed: {
    filteredResources() {
      return filterAgentResources(this.resources, this.keyword, this.filter)
    },
    selectedToolAccess() {
      return agentToolAccessView(this.selectedResource)
    }
  },
  methods: {
    toolAccess(resource) {
      return agentToolAccessView(resource)
    },
    description(resource) {
      return resource?.description || resource?.attributes?.agentCode || '使用该智能体的既有角色和能力完成任务'
    }
  }
}
</script>

<style scoped lang="scss">
.agent-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
}

.agent-step-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  > div {
    min-width: 0;
  }

  strong,
  small {
    display: block;
  }

  strong {
    color: var(--workflow-text, var(--el-text-color-primary));
    font-size: 14px;
  }

  small {
    margin-top: 3px;
    color: var(--workflow-text-secondary, var(--el-text-color-secondary));
    font-size: 11px;
    line-height: 1.5;
  }
}

.agent-picker-tools {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  gap: 10px;
}

.agent-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  min-height: 88px;
}

.agent-card {
  min-width: 0;
  min-height: 92px;
  padding: 12px;
  display: flex;
  align-items: flex-start;
  gap: 11px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface, var(--el-bg-color));
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;

  &:hover:not(:disabled),
  &:focus-visible {
    border-color: var(--workflow-primary, var(--el-color-primary));
    outline: none;
  }

  &.selected {
    border-color: var(--workflow-primary, var(--el-color-primary));
    background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
    box-shadow: 0 0 0 1px var(--workflow-primary, var(--el-color-primary)) inset;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.58;
  }
}

.agent-avatar {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.agent-copy {
  min-width: 0;
  flex: 1;

  > small {
    display: -webkit-box;
    margin-top: 5px;
    overflow: hidden;
    color: var(--workflow-text-secondary, var(--el-text-color-secondary));
    font-size: 11px;
    line-height: 1.45;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
}

.agent-title-row,
.agent-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.agent-title-row strong {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-meta {
  margin-top: 7px;

  em {
    color: var(--workflow-text-tertiary, var(--el-text-color-placeholder));
    font-size: 10px;
    font-style: normal;

    &.unavailable {
      color: var(--el-color-danger);
    }
  }
}

.agent-state {
  margin-top: 2px;
  flex: 0 0 auto;
  color: var(--workflow-text-tertiary, var(--el-text-color-placeholder));

  &.selected {
    color: var(--workflow-primary, var(--el-color-primary));
  }
}

.agent-empty {
  grid-column: 1 / -1;
  min-height: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px dashed var(--workflow-border-strong, var(--el-border-color));
  border-radius: 10px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));

  strong {
    color: var(--workflow-text, var(--el-text-color-primary));
    font-size: 12px;
  }

  small {
    font-size: 11px;
  }
}

@media (max-width: 760px) {
  .agent-picker-tools,
  .agent-list {
    grid-template-columns: 1fr;
  }
}
</style>
