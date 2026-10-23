<template>
  <div class="mcp-tool-picker">
    <div class="mcp-picker-heading">
      <strong>选择 MCP 工具</strong>
      <small>仅租户管理员可使用当前已审批的只读工具；工具升级不会自动更新节点快照。</small>
    </div>
    <el-select :model-value="selectedResourceId" filterable :loading="loading"
               :disabled="!canEdit || loading" placeholder="选择已审批的 MCP 工具"
               aria-label="选择已审批的 MCP 工具" @change="$emit('select', $event)">
      <el-option v-for="resource in resources" :key="resource.resourceId" :label="resource.name"
                 :value="resource.resourceId" :disabled="!snapshot(resource)">
        <span>{{ resource.name }}</span>
        <small v-if="!resource.available"> · {{ resource.unavailableReason || '不可用' }}</small>
      </el-option>
    </el-select>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-empty v-else-if="!loading && !resources.length" description="暂无 MCP 工具">
      <small>请由租户管理员在 MCP 连接中发现工具，并完成当前版本的只读审批。</small>
    </el-empty>
    <template v-if="selectedResourceId">
      <el-alert :title="confirmed ? '已确认当前工具版本' : '工具不可用或版本已变化，需重新确认后才能发布'"
                :type="confirmed ? 'success' : 'warning'" :closable="false" show-icon />
      <dl>
        <dt>工具</dt><dd>{{ selectedResource?.name || '资源不可见或已删除' }}</dd>
        <dt>节点版本</dt><dd>{{ config.revision || '未确认' }}</dd>
        <dt>当前版本</dt><dd>{{ currentSnapshot?.revision || '不可用' }}</dd>
        <dt>连接 / 目录版本</dt><dd>{{ config.connectionRevision || '—' }} / {{ config.catalogRevision || '—' }}</dd>
      </dl>
      <el-button v-if="!confirmed && currentSnapshot" type="primary" plain
                 :disabled="!canEdit || loading" @click="$emit('select', selectedResourceId)">
        采用当前审批版本
      </el-button>
    </template>
    <el-alert title="不自动重试；超时或取消不代表远端已停止。先在 MCP 连接中手工测试，再用正式工作流执行验证。"
              type="info" :closable="false" show-icon />
  </div>
</template>

<script>
import {mcpSnapshot, mcpSnapshotMatches} from './workflowMcp'

export default {
  name: 'WorkflowMcpToolPicker',
  emits: ['select'],
  props: {
    resources: {type: Array, default: () => []},
    selectedResourceId: {type: String, default: ''},
    selectedResource: {type: Object, default: null},
    config: {type: Object, default: () => ({})},
    canEdit: Boolean,
    loading: Boolean,
    error: {type: String, default: ''}
  },
  computed: {
    currentSnapshot() { return mcpSnapshot(this.selectedResource) },
    confirmed() { return mcpSnapshotMatches(this.config, this.selectedResource) }
  },
  methods: {snapshot: mcpSnapshot}
}
</script>

<style scoped>
.mcp-tool-picker {display: flex; flex-direction: column; gap: 16px; min-width: 0;}
.mcp-picker-heading {display: flex; flex-direction: column; gap: 6px;}
.mcp-picker-heading strong {color: var(--el-text-color-primary); font-size: 14px;}
.mcp-picker-heading small, .mcp-tool-picker dt, .mcp-tool-picker :deep(.el-empty small) {color: var(--el-text-color-secondary); line-height: 1.6;}
.mcp-tool-picker dl {display: grid; grid-template-columns: 120px minmax(0, 1fr); gap: 10px; margin: 0; font-size: 13px;}
.mcp-tool-picker dd {margin: 0; overflow-wrap: anywhere; color: var(--el-text-color-primary);}
.mcp-tool-picker :deep(.el-empty) {padding: 18px 0;}
</style>
