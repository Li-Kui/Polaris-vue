<template>
  <div v-loading="loading">
    <el-alert title="旧 Agent 节点不会自动迁移" type="warning" :closable="false" show-icon
      description="此检查只读取当前租户的草稿和当前发布版本。旧节点需移除并添加 2.0 节点、重新绑定资源及发布；已有记录保持可查看。" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table v-if="loaded" :data="items" size="small" empty-text="本次扫描未发现旧 Agent 节点">
      <el-table-column prop="workflowName" label="工作流" min-width="180" />
      <el-table-column prop="workflowCode" label="编码" min-width="200" />
      <el-table-column label="范围" width="100"><template #default="{row}">{{ row.scope === 'DRAFT' ? '草稿' : '当前发布' }}</template></el-table-column>
      <el-table-column label="节点" min-width="160"><template #default="{row}">{{ row.nodeIds.join('、') }}</template></el-table-column>
    </el-table>
    <p v-if="loaded">已扫描 {{ scanned }} 个工作流。{{ truncated ? '扫描达到 200 个定义的上限，结果不完整。' : '历史非当前版本及其他租户不在此范围。' }}</p>
    <el-button :loading="loading" @click="load">重新检查</el-button>
  </div>
</template>
<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {getAgentContractImpact} from '@/api/ai/agent'

const loading=ref(false), loaded=ref(false), error=ref(''), items=ref([]), truncated=ref(false), scanned=ref(0)
let sequence=0
async function load() {
  const current=++sequence; loading.value=true; error.value=''; loaded.value=false
  try {
    const response=await getAgentContractImpact()
    if(current!==sequence)return
    if(response.code!==200 || !Array.isArray(response.data?.items))throw new Error(response.msg || '检查失败，请更新后端')
    items.value=response.data.items; truncated.value=response.data.truncated; scanned.value=response.data.scannedDefinitions; loaded.value=true
  } catch(e) {if(current===sequence)error.value=e.message || '旧契约检查失败'}
  finally {if(current===sequence)loading.value=false}
}
onMounted(load);onBeforeUnmount(()=>{sequence++})
</script>
<style scoped>p{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}.el-alert{margin-bottom:12px}</style>
