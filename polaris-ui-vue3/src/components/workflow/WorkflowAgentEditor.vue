<template>
  <div class="collaboration-editor">
    <el-alert v-if="legacy" title="此节点使用旧 Agent 契约，请移除后添加协作智能体 2.0 并重新发布" type="warning" :closable="false" show-icon />
    <template v-else>
      <header><div><strong>{{ selectedAgent?.name || '尚未选择智能体' }}</strong><small>问题、知识依据与允许外发的查询分别配置</small></div>
        <el-button type="primary" plain :disabled="disabled" @click="$emit('select-agent')">{{ selectedAgent ? '更换智能体' : '选择智能体' }}</el-button></header>
      <el-alert v-if="selectedAgent && !confirmed" title="智能体版本已变化，请明确采用当前版本后保存" type="warning" :closable="false" show-icon />
      <el-button v-if="selectedAgent?.available && !confirmed" :disabled="disabled" @click="$emit('reconfirm-agent')">采用当前智能体版本</el-button>
      <section><strong>可选 MCP 查询</strong><p>最多选择 2 项独立查询，串行执行；内部工具类不参与本节点运行。</p>
        <el-empty v-if="!tools.length" :image-size="48" description="未绑定 MCP 工具，请选择知识库作为证据来源" />
        <ul v-else><li v-for="tool in tools" :key="tool.alias"><code>{{ tool.alias }}</code> · {{ tool.purpose }} <small>工具 #{{ tool.toolId }} · 修订 {{ tool.revision }}</small></li></ul>
      </section>
      <el-form label-position="top">
        <el-form-item label="知识依据（可选，绑定后必须检索成功）">
          <el-select :model-value="selectedKnowledgeId || ''" clearable filterable :disabled="disabled" placeholder="选择一个知识库" aria-label="协作知识库" @change="$emit('select-knowledge', $event || '')">
            <el-option v-for="resource in knowledgeResources" :key="resource.resourceId" :value="resource.resourceId" :label="resource.name" :disabled="!knowledgeSnapshot(resource)" />
          </el-select>
          <small>只检索当前明确绑定的知识库；没有有效资料时停止，不使用全局知识替代。</small>
        </el-form-item>
        <el-button v-if="selectedKnowledgeId && !knowledgeConfirmed && knowledgeSnapshot(selectedKnowledge)" :disabled="disabled" @click="$emit('select-knowledge', selectedKnowledgeId)">采用当前知识索引版本</el-button>
        <el-form-item label="每次运行最多查询次数"><el-input-number :model-value="normalized.maxCalls" :min="tools.length ? 1 : 0" :max="2" :disabled="disabled || !tools.length" @update:model-value="update('maxCalls', $event)" /></el-form-item>
        <el-form-item label="总时限（秒）"><el-input-number :model-value="normalized.maxWaitSeconds" :min="1" :max="180" :disabled="disabled" @update:model-value="update('maxWaitSeconds', $event)" /><small>规划、检索、查询和回答共用总预算，同时受工作流节点时限约束。</small></el-form-item>
      </el-form>
      <section><strong>本次输入</strong><p>question 是要回答的问题。externalQuery 是允许发送给外部规划模型和 MCP 工具的独立文本；不从问题或知识正文自动复制。</p>
        <el-button :disabled="disabled" @click="$emit('configure-input')">配置问题与外发查询</el-button></section>
      <el-alert :title="configuration.issues[0] || '配置已就绪，请保存、发布后通过正式工作流验证'" :type="configuration.issues.length ? 'warning' : 'success'" :closable="false" show-icon />
      <p>一次规划、至多两次查询、一次回答；无自动循环、重试或结果重放。该节点通过正式工作流运行，不能隔离试调用。</p>
    </template>
  </div>
</template>
<script>
import {
  agentConfigurationState,
  agentKnowledgeSnapshot,
  agentSnapshotMatches,
  normalizeAgentConfig
} from './workflowAgent'

export default {
  name: 'WorkflowAgentEditor',
  props: {config: {type: Object, default: () => ({})}, selectedAgent: {type: Object, default: null},
    inputMapping: {type: Object, default: () => ({})}, selectedKnowledge: {type: Object, default: null},
    selectedKnowledgeId: {type: String, default: ''}, knowledgeResources: {type: Array, default: () => []}, disabled: Boolean, legacy: Boolean},
  emits: ['update:config', 'select-agent', 'reconfirm-agent', 'configure-input', 'select-knowledge'],
  computed: {
    normalized() {return normalizeAgentConfig(this.config)},
    tools() {return this.selectedAgent?.attributes?.toolBindings || []},
    confirmed() {return agentSnapshotMatches(this.config, this.selectedAgent)},
    knowledgeConfirmed() {return !this.selectedKnowledgeId || agentKnowledgeSnapshot(this.selectedKnowledge)?.definitionHash === this.config.knowledgeHash},
    configuration() {return agentConfigurationState({selectedAgent: this.selectedAgent, selectedKnowledge: this.selectedKnowledge, config: this.config, inputMapping: this.inputMapping, legacy: this.legacy})}
  },
  methods: {knowledgeSnapshot: agentKnowledgeSnapshot, update(key, value) {this.$emit('update:config', {...this.normalized, [key]: value})}}
}
</script>
<style scoped>
.collaboration-editor {display:flex;flex-direction:column;gap:14px;min-width:0}
.collaboration-editor header {display:flex;align-items:center;justify-content:space-between;gap:12px}
.collaboration-editor header small,.collaboration-editor .el-form-item small {display:block;margin-top:6px}
.collaboration-editor section {padding:12px;border:1px solid var(--el-border-color);border-radius:10px}
.collaboration-editor p,.collaboration-editor small {color:var(--el-text-color-secondary);font-size:12px;line-height:1.7}
.collaboration-editor li {margin:8px 0;overflow-wrap:anywhere}
.collaboration-editor .el-select {width:100%}
</style>
