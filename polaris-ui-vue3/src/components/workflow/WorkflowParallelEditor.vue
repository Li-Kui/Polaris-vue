<template>
  <section class="parallel-editor">
    <div class="parallel-heading">
      <div><strong>并行任务组</strong><small>每条任务线同时开始，全部成功后只继续一次</small></div>
      <el-tag type="success" effect="plain">全部成功</el-tag>
    </div>

    <section class="parallel-section">
      <div class="section-heading">
        <div><b>1. 配置任务线</b><small>为每条任务线选择入口和代表完成的末节点</small></div>
        <el-button :disabled="disabled || branches.length >= 20" @click="addBranch">添加任务线</el-button>
      </div>
      <div class="branch-list">
        <article v-for="(branch, index) in branches" :key="branch.key" class="branch-card">
          <div class="branch-title">
            <span class="branch-index">{{ index + 1 }}</span>
            <el-input :model-value="branch.name" :disabled="disabled" maxlength="128" @input="renameBranch(index, $event)" />
            <el-button text type="danger" :disabled="disabled || branches.length <= 2" @click="removeBranch(index)">删除</el-button>
          </div>
          <div class="route-grid">
            <label>
              <span>从这里开始</span>
              <el-select :model-value="branchTargets[branch.key] || ''" filterable clearable :disabled="disabled" placeholder="选择尚未接入流程的节点" @change="$emit('update:branch-target', {key: branch.key, target: $event || ''})">
                <el-option v-for="node in entryOptionsForBranch(branch.key)" :key="node.id" :label="nodeOptionLabel(node)" :value="node.id" />
              </el-select>
            </label>
            <label>
              <span>这个节点完成代表任务线结束</span>
              <el-select :model-value="branch.resultNodeId" filterable clearable :disabled="disabled || !branchTargets[branch.key]" :placeholder="branchTargets[branch.key] ? '选择末节点' : '请先选择入口'" @change="resultChanged(index, $event || '')">
                <el-option v-for="node in resultOptions[branch.key] || []" :key="node.id" :label="nodeOptionLabel(node)" :value="node.id" />
              </el-select>
              <small v-if="branchTargets[branch.key] && !(resultOptions[branch.key] || []).length" class="field-warning">当前路径没有统一末节点，请先补全或汇合连线。</small>
            </label>
          </div>
        </article>
      </div>
    </section>

    <section class="parallel-section">
      <div class="section-heading"><div><b>2. 设置完成后去向</b><small>系统会等待上面的任务线全部成功</small></div></div>
      <label class="continuation-field">
        <span>全部完成后进入</span>
        <el-select :model-value="continuationTarget" filterable clearable :disabled="disabled" placeholder="选择后续节点" @change="$emit('update:continuation-target', $event || '')">
          <el-option v-for="node in availableContinuationOptions" :key="node.id" :label="nodeOptionLabel(node)" :value="node.id" />
        </el-select>
        <small>下游可从并行任务组输出的 results 中按任务线标识读取结果。</small>
      </label>
    </section>

    <el-alert v-if="issues.length" title="请完成以下配置" type="warning" :closable="false" show-icon>
      <ul class="issue-list"><li v-for="issue in issues" :key="issue">{{ issue }}</li></ul>
    </el-alert>
  </section>
</template>

<script>
import {
  parallelAvailableContinuationOptions,
  parallelEntryOptionsForBranch,
  workflowNodeOptionLabel
} from './workflowParallel'

export default {
  name: 'WorkflowParallelEditor',
  props: {
    config: {type: Object, default: () => ({})},
    entryOptions: {type: Array, default: () => []},
    continuationOptions: {type: Array, default: () => []},
    branchTargets: {type: Object, default: () => ({})},
    resultOptions: {type: Object, default: () => ({})},
    continuationTarget: {type: String, default: ''},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config', 'update:branch-target', 'update:continuation-target'],
  computed: {
    branches() { return Array.isArray(this.config?.branches) ? this.config.branches : [] },
    availableContinuationOptions() {
      return parallelAvailableContinuationOptions(this.continuationOptions, this.branchTargets)
    },
    issues() {
      const result = []
      const names = new Set()
      this.branches.forEach(branch => {
        if (!String(branch.name || '').trim()) result.push('任务线名称不能为空')
        else if (names.has(String(branch.name).trim())) result.push('任务线名称不能重复')
        else names.add(String(branch.name).trim())
        if (!this.branchTargets[branch.key]) result.push(`请选择“${branch.name || branch.key}”的入口节点`)
        if (!branch.resultNodeId) result.push(`请选择“${branch.name || branch.key}”的末节点`)
        else if (!(this.resultOptions[branch.key] || []).some(node => node.id === branch.resultNodeId)) {
          result.push(`“${branch.name || branch.key}”的末节点已不在该任务线路径中`)
        }
      })
      if (!this.continuationTarget) result.push('请选择全部完成后的节点')
      return [...new Set(result)]
    }
  },
  methods: {
    entryOptionsForBranch(branchKey) {
      return parallelEntryOptionsForBranch(
        this.entryOptions, this.branchTargets, branchKey, this.continuationTarget)
    },
    nodeOptionLabel(node) { return workflowNodeOptionLabel(node) },
    updateBranches(branches) {
      this.$emit('update:config', {...this.config, version: 2, completionMode: 'ALL_SUCCEEDED', branches})
    },
    addBranch() {
      const used = new Set(this.branches.map(branch => branch.key))
      let index = this.branches.length + 1
      while (used.has(`branch_${index}`)) index++
      this.updateBranches([...this.branches, {key: `branch_${index}`, name: `任务线 ${index}`, resultNodeId: ''}])
    },
    removeBranch(index) {
      this.updateBranches(this.branches.filter((_, current) => current !== index))
    },
    renameBranch(index, name) {
      this.updateBranches(this.branches.map((branch, current) => current === index ? {...branch, name} : branch))
    },
    resultChanged(index, resultNodeId) {
      this.updateBranches(this.branches.map((branch, current) => current === index ? {...branch, resultNodeId} : branch))
    }
  }
}
</script>

<style scoped>
.parallel-editor{display:grid;gap:14px}.parallel-heading,.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.parallel-heading strong{font-size:17px}.parallel-heading small,.section-heading small,.continuation-field small{display:block;margin-top:4px;color:var(--el-text-color-secondary)}.parallel-section{padding:16px;border:1px solid var(--workflow-border,#dfe3ef);border-radius:14px;background:var(--workflow-surface-raised,#fff)}.section-heading{margin-bottom:14px}.section-heading b{font-size:14px}.branch-list{display:grid;gap:10px}.branch-card{padding:12px;border:1px solid var(--workflow-border,#dfe3ef);border-radius:12px;background:var(--el-fill-color-extra-light)}.branch-title{display:grid;grid-template-columns:28px minmax(0,1fr) auto;align-items:center;gap:8px;margin-bottom:12px}.branch-index{width:24px;height:24px;display:grid;place-items:center;border-radius:999px;color:var(--workflow-primary,var(--el-color-primary));background:var(--workflow-primary-soft,var(--el-color-primary-light-9));font-size:12px;font-weight:700}.route-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.route-grid label,.continuation-field{min-width:0}.route-grid label>span,.continuation-field>span{display:block;margin-bottom:7px;font-weight:600}.route-grid :deep(.el-select),.continuation-field :deep(.el-select){width:100%}.route-grid small{display:block;margin-top:7px}.field-warning{color:var(--el-color-warning)!important}.issue-list{margin:0;padding-left:18px}.issue-list li+li{margin-top:4px}@media(max-width:900px){.route-grid{grid-template-columns:1fr}}
</style>
