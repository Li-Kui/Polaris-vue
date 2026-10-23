<template>
  <el-drawer :model-value="true" class="customer-service-preview" title="我的接待记录" size="min(1380px, 100vw)" @close="emit('closed')">
    <header class="cs-panel-heading"><div><h2>{{ app.config.name }}</h2><p>查看本人测试会话及本客服的访客会话；记录不会恢复录音或重新执行操作。</p></div><span class="cs-status">接待记录</span></header>
    <div class="cs-record-layout" :class="{ 'has-detail': selected }"><section class="cs-panel-section cs-record-list">
    <div class="toolbar">
      <el-button :loading="loading" @click="load()">刷新记录</el-button>
      <el-button :disabled="loading || !previous.length" @click="load(previous.at(-1), previous.slice(0, -1))">上一页</el-button>
      <el-button :disabled="loading || !nextCursor" @click="load(nextCursor, [...previous, cursor])">更早记录</el-button>
      <span class="muted">每页最多20条</span>
    </div>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
    <div class="cs-mobile-rows"><article v-for="row in rows" :key="row.id"><div class="cs-section-heading"><strong>{{ customerRecordTime(row.createTime) }}</strong><span class="cs-status">{{ customerRecordStatus(row) }}</span></div><p class="muted">{{ row.entry === 'VISITOR' ? '网站访客' : row.entry === 'CHANNEL' ? '分享渠道' : '控制台' }} · {{ row.turnCount }} 轮问答</p><el-button @click="select(row)">查看记录</el-button></article></div>
    <el-table class="cs-record-table" v-loading="loading" :data="rows" row-key="id" empty-text="暂无接待记录">
      <el-table-column label="创建时间" min-width="175"><template #default="{ row }">{{ customerRecordTime(row.createTime) }}</template></el-table-column>
      <el-table-column prop="id" label="会话标识" min-width="180" show-overflow-tooltip />
      <el-table-column label="入口" width="90"><template #default="{ row }">{{ row.entry === 'VISITOR' ? '网站访客' : row.entry === 'CHANNEL' ? '分享渠道' : '控制台' }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ customerRecordStatus(row) }}</template></el-table-column>
      <el-table-column prop="turnCount" label="问答轮数" width="90" />
      <el-table-column label="操作" width="95"><template #default="{ row }"><el-button link type="primary" @click="select(row)">查看记录</el-button></template></el-table-column>
    </el-table>
    </section>
    <section v-if="selected" v-loading="detailLoading" class="record-detail" aria-label="接待记录详情">
      <div class="cs-section-heading"><h3>会话详情</h3><el-button link @click="closeDetail">收起详情</el-button></div>
      <p class="muted">{{ selected.id }}</p>
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
      <template v-if="detail">
        <p class="muted">固定发布：{{ detail.session.releaseId }} · {{ customerRecordStatus(detail.session) }} · 业务操作 {{ detail.operationCount }} 次</p>
        <el-alert v-if="!detail.contentAvailable" title="当前权限、渠道或资源状态不允许读取历史正文，仅展示运行记录。" type="warning" :closable="false" />
        <details class="cs-help"><summary>耗时与记录口径</summary><p class="muted">问答耗时包含服务端输入准备与模型处理；实时识别周期包含录音，短录音识别不含本地录音。合成耗时不代表首音或实际播放时长；这些指标不能直接相加作为用户等待时间。旧记录未保存检索、首音或停播分段指标时不作推算。超时待核定仅表示账本仍在处理中，本页不会更改状态或重试。</p></details>
        <h4>问答记录</h4>
        <p v-if="!detail.turns.length" class="muted">此会话尚无问答。</p>
        <article v-for="turn in detail.turns" :key="turn.id" class="turn">
          <div class="turn-heading"><strong>第 {{ turn.turnNo }} 轮 · {{ customerRecordStatus(turn) }}</strong><span>服务端处理 {{ customerRecordDuration(turn.elapsedMillis) }}</span></div>
          <p class="muted">{{ customerRecordTime(turn.createTime) }} · 请求 {{ turn.requestId }}</p>
          <p v-if="turn.errorCode || turn.contentErrorCode" class="muted">{{ customerTurnHint({ ...turn, errorCode: turn.contentErrorCode || turn.errorCode }) }}</p>
          <template v-if="detail.contentAvailable">
            <p v-if="turn.question" class="question">{{ turn.question }}</p>
            <p v-if="turn.answer" class="answer">{{ turn.answer }}</p>
            <p v-for="source in turn.sources" :key="source.citationId" class="muted">[{{ source.citationId }}] {{ source.documentName }}</p>
          </template>
          <p class="muted">输入 Token：{{ turn.inputTokens ?? '未记录' }} · 输出 Token：{{ turn.outputTokens ?? '未记录' }} · 用量来源：{{ turn.usageSource || '未记录' }}</p>
        </article>
        <h4>语音记录</h4>
        <el-table :data="detail.speech" row-key="requestId" empty-text="此会话尚无语音记录">
          <el-table-column label="阶段" min-width="200"><template #default="{ row }">{{ customerSpeechStage(row.kind, row.partNo) }}</template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="{ row }">{{ customerRecordStatus(row) }}</template></el-table-column>
          <el-table-column label="请求周期" width="120"><template #default="{ row }">{{ customerRecordDuration(row.elapsedMillis) }}</template></el-table-column>
          <el-table-column label="录音时长" width="120"><template #default="{ row }">{{ ['STREAM', 'STT'].includes(row.kind) ? customerRecordDuration(row.audioMillis) : '不适用' }}</template></el-table-column>
          <el-table-column prop="errorCode" label="状态码" min-width="160" />
          <el-table-column label="关联轮次" width="100"><template #default="{ row }">{{ detail.turns.find(turn => turn.id === row.turnId)?.turnNo ?? '未关联' }}</template></el-table-column>
        </el-table>
        <p class="muted">识别请求未持久化绑定问答轮次时不按文字或时间猜测关联。已停止包含主动取消及超时，具体以状态码为准。历史页不读取原始录音、合成音频或工作流原始输入输出。</p>
      </template>
    </section></div>
  </el-drawer>
</template>
<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {getCustomerRecord, listCustomerRecords} from '@/api/platform/customerService'
import {
  customerRecordDuration,
  customerRecordStatus,
  customerRecordTime,
  customerSpeechStage
} from '@/utils/customerRecordState'
import {customerTurnHint} from '@/utils/customerServiceState'

const props = defineProps({ app: { type: Object, required: true } })
const emit = defineEmits(['closed'])
const rows = ref([]), loading = ref(false), listError = ref(''), cursor = ref(null), nextCursor = ref(null), previous = ref([])
const selected = ref(null), detail = ref(null), detailLoading = ref(false), detailError = ref('')
let disposed = false, listGeneration = 0, detailGeneration = 0, listController, detailController
async function load(before = null, stack = []) {
  const ticket = ++listGeneration
  listController?.abort(); listController = new AbortController()
  detailGeneration++; detailController?.abort(); selected.value = null; detail.value = null; detailError.value = ''; detailLoading.value = false
  loading.value = true; listError.value = ''; rows.value = []; nextCursor.value = null
  try {
    const result = (await listCustomerRecords(props.app.id, before, listController.signal)).data
    if (disposed || ticket !== listGeneration) return
    rows.value = result.items; nextCursor.value = result.nextCursor; cursor.value = before; previous.value = stack
  } catch {
    if (!disposed && ticket === listGeneration) listError.value = '接待记录未加载，请检查登录状态或稍后刷新。'
  } finally { if (!disposed && ticket === listGeneration) loading.value = false }
}
async function select(row) {
  const ticket = ++detailGeneration
  detailController?.abort(); detailController = new AbortController()
  selected.value = row; detail.value = null; detailError.value = ''; detailLoading.value = true
  try {
    const result = (await getCustomerRecord(props.app.id, row.id, detailController.signal)).data
    if (!disposed && ticket === detailGeneration) detail.value = result
  } catch {
    if (!disposed && ticket === detailGeneration) detailError.value = '详情未加载或已无权访问，请刷新记录后核对。'
  } finally { if (!disposed && ticket === detailGeneration) detailLoading.value = false }
}
function closeDetail() { detailGeneration++; detailController?.abort(); selected.value = null; detail.value = null; detailError.value = ''; detailLoading.value = false }
onMounted(() => load())
onBeforeUnmount(() => { disposed = true; listGeneration++; detailGeneration++; listController?.abort(); detailController?.abort(); detail.value = null })
</script>
