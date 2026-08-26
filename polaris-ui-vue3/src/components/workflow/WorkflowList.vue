<template>
  <div class="workflow-list">
    <div class="list-actions">
      <div class="heading-copy">
        <span class="eyebrow">WORKFLOW ORCHESTRATION</span>
        <h2>工作流编排</h2>
        <p>用可视化节点连接模型、知识库、API 与数据源，发布后按不可变版本稳定运行。</p>
      </div>
      <div class="header-buttons">
        <el-button class="header-button" icon="Connection" @click="$emit('bindings')">资源绑定</el-button>
        <el-button class="header-button" icon="Timer" @click="$emit('triggers')">触发器</el-button>
        <el-button class="header-button" icon="List" @click="$emit('executions')">运行记录</el-button>
        <el-button v-if="canApprove" class="header-button" icon="Finished" @click="$emit('approvals')">审批箱</el-button>
        <el-button v-if="canEdit" class="header-button header-button--primary" type="primary" icon="Plus" @click="$emit('create')">
          新建工作流
        </el-button>
      </div>
    </div>

    <div class="overview-grid">
      <div class="overview-card">
        <span class="overview-icon is-total"><el-icon><DataBoard /></el-icon></span>
        <div><strong>{{ items.length }}</strong><span>工作流总数</span></div>
      </div>
      <div class="overview-card">
        <span class="overview-icon is-active"><el-icon><CircleCheck /></el-icon></span>
        <div><strong>{{ publishedCount }}</strong><span>已发布可运行</span></div>
      </div>
      <div class="overview-card">
        <span class="overview-icon is-draft"><el-icon><EditPen /></el-icon></span>
        <div><strong>{{ draftCount }}</strong><span>仅草稿</span></div>
      </div>
    </div>

    <div class="table-shell">
    <el-table v-loading="loading" :data="items" class="polaris-el-table" row-key="id">
      <el-table-column prop="workflowName" label="名称" min-width="180">
        <template #default="{ row }">
          <div class="workflow-name">
            <strong>{{ row.workflowName }}</strong>
            <code>{{ row.workflowCode }}</code>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="草稿" width="100" align="center">
        <template #default="{ row }">r{{ row.draftRevision }}</template>
      </el-table-column>
      <el-table-column label="发布状态" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="row.currentPublishedVersionId ? 'success' : 'info'">
            {{ row.currentPublishedVersionId ? '已发布' : '仅草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="310" align="center" fixed="right">
        <template #default="{ row }">
          <el-button class="row-action" link type="primary" icon="EditPen" @click="$emit('open', row)">
            {{ canEdit ? '打开设计器' : '查看' }}
          </el-button>
          <el-button v-if="canExecute && row.currentPublishedVersionId" class="row-action" link type="success" icon="VideoPlay" @click="$emit('run', row)">
            运行
          </el-button>
          <el-dropdown trigger="click" @command="command => handleMore(command, row)">
            <el-button class="row-action" link>更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="versions">版本记录</el-dropdown-item>
                <el-dropdown-item v-if="canEdit" command="clone">克隆工作流</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有工作流" :image-size="84">
          <el-button v-if="canEdit" type="primary" icon="Plus" @click="$emit('create')">新建工作流</el-button>
        </el-empty>
      </template>
    </el-table>
    </div>
  </div>
</template>

<script>
export default {
  name: 'WorkflowList',
  props: {
    items: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    canEdit: {
      type: Boolean,
      default: false
    },
    canExecute: {
      type: Boolean,
      default: false
    },
    canApprove: {
      type: Boolean,
      default: false
    }
  },
  emits: ['approvals', 'bindings', 'clone', 'create', 'executions', 'open', 'run', 'triggers', 'versions'],
  computed: {
    publishedCount() {
      return this.items.filter(item => item.currentPublishedVersionId).length
    },
    draftCount() {
      return Math.max(0, this.items.length - this.publishedCount)
    }
  },
  methods: {
    handleMore(command, row) {
      this.$emit(command, row)
    }
  }
}
</script>

<style scoped>
.workflow-list {
  padding: 28px;
  border-radius: var(--workflow-radius, 18px);
  background: var(--workflow-surface, var(--el-bg-color));
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  box-shadow: var(--workflow-shadow, none);
}

.list-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.eyebrow {
  display: block;
  margin-bottom: 7px;
  color: var(--workflow-primary, var(--el-color-primary));
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.14em;
}

.list-actions h2 {
  margin: 0 0 7px;
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 25px;
  letter-spacing: -0.02em;
}

.list-actions p {
  margin: 0;
  color: var(--el-text-color-secondary);
  line-height: 1.65;
}

.header-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.header-buttons :deep(.el-button) {
  height: 36px;
  margin-left: 0;
  padding-inline: 14px;
  border-color: var(--workflow-border-strong, var(--el-border-color));
  border-radius: 10px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
  font-weight: 600;
}

.header-buttons :deep(.el-button:hover) {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.header-buttons :deep(.header-button--primary) {
  border-color: var(--workflow-primary, var(--el-color-primary));
  color: #fff;
  background: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 8px 18px color-mix(in srgb, var(--workflow-primary, #2563eb) 24%, transparent);
}

.header-buttons :deep(.header-button--primary:hover) {
  border-color: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
  color: #fff;
  background: var(--workflow-primary-hover, var(--el-color-primary-dark-2));
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 15px 16px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: calc(var(--workflow-radius, 18px) - 6px);
  background: linear-gradient(145deg,
    var(--workflow-surface-raised, var(--el-bg-color-overlay)),
    var(--workflow-muted, var(--el-fill-color-extra-light)));
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.overview-card:hover {
  border-color: var(--workflow-border-strong, var(--el-border-color));
  box-shadow: 0 10px 24px rgb(15 23 42 / 7%);
  transform: translateY(-1px);
}

.overview-card > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.overview-card strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 23px;
  line-height: 1;
}

.overview-card div span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.overview-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  font-size: 18px;
}

.overview-icon.is-total {
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.overview-icon.is-active {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.overview-icon.is-draft {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.table-shell {
  overflow: hidden;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: calc(var(--workflow-radius, 18px) - 6px);
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
}

.table-shell :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.table-shell :deep(.el-table) {
  --el-table-bg-color: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  --el-table-tr-bg-color: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  --el-table-header-bg-color: var(--workflow-muted, var(--el-fill-color-light));
  --el-table-row-hover-bg-color: var(--workflow-hover, var(--el-fill-color-light));
  color: var(--workflow-text, var(--el-text-color-primary));
}

.table-shell :deep(.el-table th.el-table__cell) {
  height: 46px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 12px;
  font-weight: 650;
}

.table-shell :deep(.el-table td.el-table__cell) {
  height: 58px;
  border-bottom-color: var(--workflow-border, var(--el-border-color-lighter));
}

.table-shell :deep(.row-action) {
  margin: 0 3px;
  padding: 5px 7px;
  border-radius: 7px;
  font-weight: 600;
}

.table-shell :deep(.row-action:hover) {
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.workflow-name {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.workflow-name code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.workflow-name strong {
  color: var(--workflow-text, var(--el-text-color-primary));
}

@media (max-width: 900px) {
  .workflow-list {
    padding: 18px;
  }

  .list-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-buttons {
    justify-content: flex-start;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
