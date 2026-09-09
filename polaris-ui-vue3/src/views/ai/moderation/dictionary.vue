<template>
  <div class="app-container ai-moderation-dictionary-manager no-sidebar-manage-wrap">
    <div class="content-inner">
      <!-- 顶部玻璃卡片：版本管理与搜索过滤 -->
      <div class="polaris-filter-card">
        <!-- 上层：版本控制与发布回滚工具栏 -->
        <div class="version-toolbar-row">
          <div class="version-select-group">
            <span class="toolbar-label">📖 词库版本:</span>
            <el-select
              v-model="selectedVersionId"
              class="version-select"
              placeholder="请选择版本"
              @change="handleVersionChange"
            >
              <el-option
                v-for="ver in versionList"
                :key="ver.id"
                :label="`${ver.versionNo} (${getStatusLabel(ver.status)})`"
                :value="ver.id"
              />
            </el-select>

            <span :class="['version-status-pill', getStatusTagClass(currentVersion?.status)]">
              {{ getStatusLabel(currentVersion?.status) }}
            </span>

            <span v-if="currentVersion?.ruleCount !== undefined" class="rule-count-hint">
              共 <strong>{{ currentVersion.ruleCount }}</strong> 条规则
            </span>
          </div>

          <div class="version-actions-group">
            <el-button
              v-if="!hasDraft"
              type="primary"
              class="action-btn-primary"
              icon="Plus"
              @click="handleCreateDraft"
            >
              新建草稿
            </el-button>

            <template v-if="isDraft">
              <el-button type="success" class="action-btn-success" icon="Upload" @click="handlePublish">
                发布此版本
              </el-button>
              <el-button type="primary" class="action-btn-primary" icon="Plus" @click="openAddRuleDialog">
                添加规则
              </el-button>
              <el-button class="action-btn-secondary" icon="DocumentAdd" @click="openImportDialog">
                批量导入
              </el-button>
            </template>

            <el-button
              v-if="isArchived"
              type="warning"
              class="action-btn-warning"
              icon="RefreshLeft"
              @click="handleRollback"
            >
              回滚至此版本
            </el-button>

            <el-button class="action-btn-secondary" icon="Switch" @click="openDiffDialog">
              版本对比
            </el-button>

            <el-button class="action-btn-secondary" icon="Download" @click="handleExportRules">
              导出词库
            </el-button>

            <el-button class="action-btn-secondary candidate-btn" icon="HelpFilled" @click="openCandidateDrawer">
              候选敏感词 <span class="candidate-badge" v-if="candidateCount > 0">{{ candidateCount }}</span>
            </el-button>
          </div>
        </div>

        <!-- 下层：搜索过滤器表单 -->
        <el-form :inline="true" :model="queryParams" class="polaris-filter-form dict-filter-form">
          <el-form-item label="关键词">
            <el-input
              v-model="queryParams.keyword"
              placeholder="搜索词条内容..."
              clearable
              style="width: 180px;"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="规则类型">
            <el-select
              v-model="queryParams.ruleType"
              placeholder="全部类型"
              clearable
              style="width: 170px;"
            >
              <el-option label="🚫 违规风险词 (RISK_WORD)" value="RISK_WORD" />
              <el-option label="⚠️ 风险语境 (RISK_CONTEXT)" value="RISK_CONTEXT" />
              <el-option label="✅ 安全放行语境 (SAFE_CONTEXT)" value="SAFE_CONTEXT" />
              <el-option label="⚪ 白名单词 (ALLOW_TERM)" value="ALLOW_TERM" />
            </el-select>
          </el-form-item>
          <el-form-item label="违规分类">
            <el-select
              v-model="queryParams.category"
              placeholder="全部分类"
              clearable
              filterable
              allow-create
              default-first-option
              style="width: 170px;"
            >
              <el-option
                v-for="opt in CATEGORY_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" class="polaris-query-btn" @click="handleSearch">查询</el-button>
            <el-button icon="Refresh" class="polaris-reset-btn" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 数据表格玻璃卡片 -->
      <div class="polaris-table-card">
        <el-table
          :data="ruleList"
          v-loading="rulesLoading"
          class="polaris-el-table"
          stripe
        >
          <el-table-column prop="id" label="序号" width="75" align="center" />
          <el-table-column prop="ruleType" label="规则类型" width="160">
            <template #default="{ row }">
              <span :class="['rule-type-badge', getRuleTypeClass(row.ruleType)]">
                {{ getRuleTypeLabel(row.ruleType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="词条规则内容" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="rule-content-text">{{ row.content }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="违规分类" width="165">
            <template #default="{ row }">
              <span
                class="category-badge"
                :style="{
                  color: getCategoryMeta(row.category).color,
                  backgroundColor: getCategoryMeta(row.category).bgColor
                }"
              >
                <span class="cat-badge-emoji">{{ getCategoryEmoji(row.category) }}</span>
                <span class="cat-badge-text">{{ getCategoryLabel(row.category) }}</span>
                <span class="cat-badge-code" v-if="row.category && getCategoryMeta(row.category).label !== row.category">{{ row.category }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="weight" label="风险权重分" width="110" align="center">
            <template #default="{ row }">
              <span :class="['weight-score', row.weight > 0 ? 'score-risk' : (row.weight < 0 ? 'score-safe' : 'score-neutral')]">
                {{ row.weight > 0 ? '+' + row.weight : row.weight }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="source" label="规则来源" width="120" />
          <el-table-column prop="createTime" label="录入时间" width="160" />

          <el-table-column label="操作" width="140" align="center" v-if="isDraft">
            <template #default="{ row }">
              <el-button type="primary" link icon="Edit" @click="openEditRuleDialog(row)">编辑</el-button>
              <el-button type="danger" link icon="Delete" @click="handleDeleteRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="fetchRules"
        />
      </div>
    </div>

    <!-- 添加/编辑规则弹窗 -->
    <el-dialog
      v-model="ruleDialogVisible"
      :title="isEditRule ? '编辑词库规则' : '添加词库规则'"
      width="520px"
      class="polaris-glass-dialog"
      append-to-body
      destroy-on-close
    >
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleFormRules" label-width="96px" class="dict-dialog-form">
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="ruleForm.ruleType" class="w-full" @change="handleFormRuleTypeChange">
            <el-option label="🚫 违规风险词 (RISK_WORD)" value="RISK_WORD" />
            <el-option label="⚠️ 风险语境 (RISK_CONTEXT)" value="RISK_CONTEXT" />
            <el-option label="✅ 安全放行语境 (SAFE_CONTEXT)" value="SAFE_CONTEXT" />
            <el-option label="⚪ 白名单词 (ALLOW_TERM)" value="ALLOW_TERM" />
          </el-select>
        </el-form-item>
        <el-form-item label="词条内容" prop="content">
          <el-input v-model="ruleForm.content" placeholder="请输入词条或变体特征表达..." />
        </el-form-item>
        <el-form-item label="违规分类" prop="category">
          <el-select
            v-model="ruleForm.category"
            filterable
            allow-create
            default-first-option
            placeholder="请选择常用分类或直接输入分类编码"
            class="w-full"
          >
            <el-option
              v-for="opt in CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="风险权重分" prop="weight">
          <el-input-number v-model="ruleForm.weight" :min="-100" :max="100" class="w-full" />
          <div class="form-tip">违规词须为正数 (>0)，安全放行语境须为负数 (<0)，白名单词为 0</div>
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="2" placeholder="可选备注..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="action-btn-secondary" @click="ruleDialogVisible = false">取消</el-button>
          <el-button type="primary" class="action-btn-primary" :loading="savingRule" @click="submitRuleForm">
            保存规则
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      title="批量导入词库规则"
      width="680px"
      class="polaris-glass-dialog"
      append-to-body
    >
      <div class="import-tips-box">
        <span class="tip-icon">💡</span>
        <span>支持上传 CSV 或 TXT 规则文件，格式每行：<code>规则类型,词条内容,分类,权重分</code></span>
      </div>
      <div class="import-upload-row">
        <el-upload
          ref="uploadRef"
          action="#"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          accept=".csv,.txt"
        >
          <template #trigger>
            <el-button type="primary" class="action-btn-primary" icon="Upload">选择规则文件</el-button>
          </template>
        </el-upload>
        <el-button
          v-if="importFile"
          class="action-btn-success"
          type="success"
          :loading="previewing"
          @click="handlePreviewImport"
        >
          解析预览
        </el-button>
      </div>

      <div v-if="previewResult" class="import-preview-box">
        <div class="preview-stats">
          <span>解析结果：有效 <strong class="text-green-600">{{ previewResult.validCount }}</strong> 条，格式错误 <strong class="text-red-500">{{ previewResult.invalidCount }}</strong> 条</span>
        </div>
        <el-table :data="previewResult.validRules.slice(0, 5)" size="small" class="polaris-el-table" max-height="220">
          <el-table-column prop="ruleType" label="类型" width="140" />
          <el-table-column prop="content" label="词条" />
          <el-table-column prop="category" label="分类" width="120">
            <template #default="{ row }">
              <span>{{ getCategoryEmoji(row.category) }} {{ getCategoryLabel(row.category) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="weight" label="权重" width="80" align="center" />
        </el-table>
        <div v-if="previewResult.validRules.length > 5" class="preview-more-hint">仅展示前 5 条预览...</div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="action-btn-secondary" @click="importDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            class="action-btn-primary"
            :disabled="!previewResult || previewResult.validCount === 0"
            :loading="applyingImport"
            @click="submitApplyImport"
          >
            应用导入到当前草稿
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 候选词处理抽屉 -->
    <el-drawer
      v-model="candidateDrawerVisible"
      title="候选敏感词管理"
      size="720px"
      class="polaris-glass-dialog"
      append-to-body
    >
      <div class="candidate-header-row">
        <span class="candidate-desc">🔍 机器日常审核中多次出现的疑似可疑特征表达（已脱敏脱密）</span>
        <div class="candidate-actions">
          <el-button
            type="primary"
            class="action-btn-primary"
            size="small"
            :disabled="!selectedCandidates.length || !isDraft"
            @click="openBatchAcceptDialog"
          >
            采纳到草稿 ({{ selectedCandidates.length }})
          </el-button>
          <el-button
            type="danger"
            class="action-btn-danger"
            size="small"
            :disabled="!selectedCandidates.length"
            @click="handleBatchReject"
          >
            批量拒绝
          </el-button>
        </div>
      </div>

      <el-table
        :data="candidateList"
        v-loading="candidateLoading"
        class="polaris-el-table"
        @selection-change="handleCandidateSelection"
      >
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column prop="candidateTerm" label="候选特征表达" min-width="160" show-overflow-tooltip />
        <el-table-column prop="category" label="建议分类" width="150">
          <template #default="{ row }">
            <span
              class="category-badge small"
              :style="{
                color: getCategoryMeta(row.category).color,
                backgroundColor: getCategoryMeta(row.category).bgColor
              }"
            >
              <span class="cat-badge-emoji">{{ getCategoryEmoji(row.category) }}</span>
              <span class="cat-badge-text">{{ getCategoryLabel(row.category) }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="observationCount" label="出现频次" width="85" align="center" />
        <el-table-column prop="sourceSignal" label="发现信号" width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="审核状态" width="95" align="center">
          <template #default="{ row }">
            <span :class="['candidate-status-tag', row.status === 'PENDING' ? 'status-pending' : 'status-observing']">
              {{ row.status === 'PENDING' ? '待审核' : '观察中' }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 版本对比弹窗 -->
    <el-dialog
      v-model="diffDialogVisible"
      title="词库版本差异对比"
      width="780px"
      class="polaris-glass-dialog"
      append-to-body
    >
      <div class="diff-header-bar">
        <div class="diff-target-info">
          <span>当前版本：<strong>{{ currentVersion?.versionNo }} ({{ getStatusLabel(currentVersion?.status) }})</strong></span>
        </div>
        <div class="diff-base-select">
          <span>对比基准：</span>
          <el-select v-model="diffBaseVersionId" placeholder="对比版本 (默认当前已发布)" style="width: 220px;" @change="fetchDiff">
            <el-option
              v-for="ver in versionList.filter(v => v.id !== selectedVersionId)"
              :key="ver.id"
              :label="`${ver.versionNo} (${getStatusLabel(ver.status)})`"
              :value="ver.id"
            />
          </el-select>
        </div>
      </div>

      <div v-loading="diffLoading" class="diff-content-wrapper">
        <div v-if="diffData" class="diff-summary-row">
          <div class="diff-stat-card added">
            <div class="stat-count">+{{ diffData.addedCount }}</div>
            <div class="stat-label">新增词条</div>
          </div>
          <div class="diff-stat-card removed">
            <div class="stat-count">-{{ diffData.removedCount }}</div>
            <div class="stat-label">移除词条</div>
          </div>
          <div class="diff-stat-card modified">
            <div class="stat-count">~{{ diffData.modifiedCount }}</div>
            <div class="stat-label">修改规则</div>
          </div>
        </div>

        <el-tabs v-if="diffData" v-model="activeDiffTab" class="diff-tabs">
          <el-tab-pane :label="`新增 (${diffData.addedCount})`" name="added">
            <el-table :data="diffData.addedRules" size="small" class="polaris-el-table" max-height="300">
              <el-table-column prop="ruleType" label="类型" width="130">
                <template #default="{ row }">
                  <span :class="['rule-type-badge', getRuleTypeClass(row.ruleType)]">{{ getRuleTypeLabel(row.ruleType) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="content" label="新增词条" />
              <el-table-column prop="category" label="分类" width="130">
                <template #default="{ row }">
                  <span>{{ getCategoryEmoji(row.category) }} {{ getCategoryLabel(row.category) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="weight" label="权重" width="80" align="center" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`移除 (${diffData.removedCount})`" name="removed">
            <el-table :data="diffData.removedRules" size="small" class="polaris-el-table" max-height="300">
              <el-table-column prop="ruleType" label="类型" width="130">
                <template #default="{ row }">
                  <span :class="['rule-type-badge', getRuleTypeClass(row.ruleType)]">{{ getRuleTypeLabel(row.ruleType) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="content" label="移除词条" />
              <el-table-column prop="category" label="分类" width="130">
                <template #default="{ row }">
                  <span>{{ getCategoryEmoji(row.category) }} {{ getCategoryLabel(row.category) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="weight" label="权重" width="80" align="center" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`变更 (${diffData.modifiedCount})`" name="modified">
            <el-table :data="diffData.modifiedRules" size="small" class="polaris-el-table" max-height="300">
              <el-table-column prop="content" label="词条" />
              <el-table-column label="分类变化" width="200">
                <template #default="{ row }">
                  <span>{{ row.categoryBefore }} → <strong>{{ row.categoryAfter }}</strong></span>
                </template>
              </el-table-column>
              <el-table-column label="权重变化" width="150" align="center">
                <template #default="{ row }">
                  <span>{{ row.weightBefore }} → <strong>{{ row.weightAfter }}</strong></span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="action-btn-secondary" @click="diffDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  addDictionaryRule,
  applyRuleImport,
  batchAcceptCandidates,
  batchRejectCandidates,
  createDictionaryDraft,
  deleteDictionaryRule,
  diffDictionaryVersion,
  exportDictionaryRulesUrl,
  listCandidates,
  listDictionaryRules,
  listDictionaryVersions,
  previewRuleImport,
  publishDictionaryVersion,
  rollbackDictionaryVersion,
  updateDictionaryRule
} from '@/api/ai/moderation'
import {CATEGORY_OPTIONS, getCategoryEmoji, getCategoryLabel, getCategoryMeta} from '@/utils/moderationCategory'

const versionList = ref([])
const selectedVersionId = ref(null)
const currentVersion = computed(() => versionList.value.find(v => v.id === selectedVersionId.value))
const isDraft = computed(() => currentVersion.value?.status === 'DRAFT')
const isArchived = computed(() => currentVersion.value?.status === 'ARCHIVED')
const hasDraft = computed(() => versionList.value.some(v => v.status === 'DRAFT'))

const ruleList = ref([])
const rulesLoading = ref(false)
const total = ref(0)
const queryParams = ref({
  keyword: '',
  ruleType: '',
  category: '',
  pageNum: 1,
  pageSize: 10
})

const diffDialogVisible = ref(false)
const diffLoading = ref(false)
const diffBaseVersionId = ref(null)
const diffData = ref(null)
const activeDiffTab = ref('added')

const ruleDialogVisible = ref(false)
const isEditRule = ref(false)
const savingRule = ref(false)
const ruleForm = ref({ id: null, ruleType: 'RISK_WORD', content: '', category: 'GENERAL', weight: 40, remark: '' })
const ruleFormRules = {
  content: [{ required: true, message: '请输入词条内容', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }]
}

const importDialogVisible = ref(false)
const importFile = ref(null)
const previewing = ref(false)
const previewResult = ref(null)
const applyingImport = ref(false)

const candidateDrawerVisible = ref(false)
const candidateLoading = ref(false)
const candidateList = ref([])
const selectedCandidates = ref([])
const candidateCount = ref(0)

const getStatusLabel = status => {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'DRAFT') return '草稿'
  if (status === 'ARCHIVED') return '已归档'
  return status || '未知'
}

const getStatusTagClass = status => {
  if (status === 'PUBLISHED') return 'pill-published'
  if (status === 'DRAFT') return 'pill-draft'
  if (status === 'ARCHIVED') return 'pill-archived'
  return ''
}

const getRuleTypeLabel = type => {
  const map = {
    RISK_WORD: '违规风险词',
    RISK_CONTEXT: '风险语境',
    SAFE_CONTEXT: '安全放行语境',
    ALLOW_TERM: '白名单词'
  }
  return map[type] || type
}

const getRuleTypeClass = type => {
  if (type === 'RISK_WORD') return 'type-risk'
  if (type === 'RISK_CONTEXT') return 'type-context'
  if (type === 'SAFE_CONTEXT') return 'type-safe'
  if (type === 'ALLOW_TERM') return 'type-allow'
  return ''
}

const fetchVersions = async () => {
  try {
    const res = await listDictionaryVersions()
    if (res.code === 200) {
      versionList.value = res.data || []
      if (!selectedVersionId.value && versionList.value.length) {
        const pub = versionList.value.find(v => v.status === 'PUBLISHED')
        selectedVersionId.value = pub ? pub.id : versionList.value[0].id
      }
      if (selectedVersionId.value) {
        fetchRules()
      }
    }
  } catch (err) {
    ElMessage.error('获取词库版本失败')
  }
}

const handleVersionChange = () => {
  queryParams.value.pageNum = 1
  fetchRules()
}

const handleSearch = () => {
  queryParams.value.pageNum = 1
  fetchRules()
}

const fetchRules = async () => {
  if (!selectedVersionId.value) return
  rulesLoading.value = true
  try {
    const res = await listDictionaryRules(selectedVersionId.value, queryParams.value)
    if (res.code === 200) {
      if (Array.isArray(res.rows)) {
        ruleList.value = res.rows
        total.value = res.total || res.rows.length
      } else if (Array.isArray(res.data)) {
        ruleList.value = res.data
        total.value = res.data.length
      } else {
        ruleList.value = []
        total.value = 0
      }
    }
  } catch (err) {
    ElMessage.error('获取规则列表失败')
  } finally {
    rulesLoading.value = false
  }
}

const resetQuery = () => {
  queryParams.value = {
    keyword: '',
    ruleType: '',
    category: '',
    pageNum: 1,
    pageSize: 10
  }
  fetchRules()
}

const openDiffDialog = () => {
  diffBaseVersionId.value = null
  diffDialogVisible.value = true
  activeDiffTab.value = 'added'
  fetchDiff()
}

const fetchDiff = async () => {
  if (!selectedVersionId.value) return
  diffLoading.value = true
  try {
    const res = await diffDictionaryVersion(selectedVersionId.value, diffBaseVersionId.value)
    if (res.code === 200) {
      diffData.value = res.data
    }
  } catch (err) {
    ElMessage.error('获取版本差异失败')
  } finally {
    diffLoading.value = false
  }
}

const handleExportRules = () => {
  if (!selectedVersionId.value) return
  const token = localStorage.getItem('token') || ''
  const url = `${import.meta.env.VITE_APP_BASE_API || ''}${exportDictionaryRulesUrl(selectedVersionId.value)}`
  const a = document.createElement('a')
  a.href = url
  a.download = `rules-${currentVersion.value?.versionNo || 'dict'}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

const handleCreateDraft = async () => {
  try {
    await ElMessageBox.confirm('将基于当前版本创建新的编辑草稿，是否继续？', '新建草稿', { type: 'info' })
    const res = await createDictionaryDraft(selectedVersionId.value)
    if (res.code === 200) {
      ElMessage.success('草稿创建成功')
      selectedVersionId.value = res.data
      await fetchVersions()
    }
  } catch {}
}

const handlePublish = async () => {
  try {
    await ElMessageBox.confirm('发布后新词库快照将即时同步至全部服务实例并生效，是否确认发布？', '发布确认', { type: 'warning' })
    const res = await publishDictionaryVersion(selectedVersionId.value)
    if (res.code === 200) {
      ElMessage.success('词库发布成功并已热加载生效')
      await fetchVersions()
    }
  } catch {}
}

const handleRollback = async () => {
  try {
    await ElMessageBox.confirm('回滚将使系统即刻重新加载此历史版本，是否确认？', '版本回滚', { type: 'warning' })
    const res = await rollbackDictionaryVersion(selectedVersionId.value)
    if (res.code === 200) {
      ElMessage.success('版本回滚成功')
      await fetchVersions()
    }
  } catch {}
}

const openAddRuleDialog = () => {
  isEditRule.value = false
  ruleForm.value = { id: null, ruleType: 'RISK_WORD', content: '', category: 'GENERAL', weight: 40, remark: '' }
  ruleDialogVisible.value = true
}

const openEditRuleDialog = row => {
  isEditRule.value = true
  ruleForm.value = { ...row }
  ruleDialogVisible.value = true
}

const handleFormRuleTypeChange = val => {
  if (val === 'SAFE_CONTEXT') ruleForm.value.weight = -40
  else if (val === 'ALLOW_TERM') ruleForm.value.weight = 0
  else if (val === 'RISK_WORD' && ruleForm.value.weight <= 0) ruleForm.value.weight = 40
}

const submitRuleForm = async () => {
  if (!ruleForm.value.content?.trim()) {
    ElMessage.warning('请输入词条内容')
    return
  }
  savingRule.value = true
  try {
    if (isEditRule.value) {
      await updateDictionaryRule(selectedVersionId.value, ruleForm.value.id, ruleForm.value)
      ElMessage.success('规则更新成功')
    } else {
      await addDictionaryRule(selectedVersionId.value, ruleForm.value)
      ElMessage.success('规则添加成功')
    }
    ruleDialogVisible.value = false
    fetchRules()
  } catch (err) {
    ElMessage.error(`保存失败: ${err.message || '操作异常'}`)
  } finally {
    savingRule.value = false
  }
}

const handleDeleteRule = async row => {
  try {
    await ElMessageBox.confirm(`确认删除规则 [${row.content}] 吗？`, '删除确认', { type: 'warning' })
    await deleteDictionaryRule(selectedVersionId.value, row.id)
    ElMessage.success('删除成功')
    fetchRules()
  } catch {}
}

const openImportDialog = () => {
  importFile.value = null
  previewResult.value = null
  importDialogVisible.value = true
}

const handleFileChange = uploadFile => {
  importFile.value = uploadFile.raw
  previewResult.value = null
}

const handlePreviewImport = async () => {
  if (!importFile.value) return
  previewing.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    const res = await previewRuleImport(formData)
    if (res.code === 200) {
      previewResult.value = res.data
    }
  } catch (err) {
    ElMessage.error('解析预览失败')
  } finally {
    previewing.value = false
  }
}

const submitApplyImport = async () => {
  if (!previewResult.value?.validRules?.length) return
  applyingImport.value = true
  try {
    const res = await applyRuleImport(selectedVersionId.value, previewResult.value.validRules)
    if (res.code === 200) {
      ElMessage.success(`成功导入 ${res.data} 条规则`)
      importDialogVisible.value = false
      fetchRules()
    }
  } catch (err) {
    ElMessage.error('导入应用失败')
  } finally {
    applyingImport.value = false
  }
}

const openCandidateDrawer = () => {
  candidateDrawerVisible.value = true
  fetchCandidates()
}

const fetchCandidates = async () => {
  candidateLoading.value = true
  try {
    const res = await listCandidates({ status: 'PENDING' })
    if (res.code === 200) {
      candidateList.value = res.data || []
      candidateCount.value = candidateList.value.length
    }
  } catch (err) {
    ElMessage.error('获取候选词失败')
  } finally {
    candidateLoading.value = false
  }
}

const handleCandidateSelection = selection => {
  selectedCandidates.value = selection
}

const openBatchAcceptDialog = async () => {
  if (!selectedCandidates.value.length || !isDraft.value) return
  try {
    await ElMessageBox.confirm(`确认将选中的 ${selectedCandidates.value.length} 个候选词采纳为当前草稿的违规词吗？`, '采纳确认', { type: 'info' })
    await batchAcceptCandidates({
      candidateIds: selectedCandidates.value.map(c => c.id),
      targetDraftVersionId: selectedVersionId.value,
      ruleType: 'RISK_WORD',
      weight: 40
    })
    ElMessage.success('批量采纳成功')
    fetchCandidates()
    fetchRules()
  } catch {}
}

const handleBatchReject = async () => {
  if (!selectedCandidates.value.length) return
  try {
    await ElMessageBox.confirm(`确认拒绝选中的 ${selectedCandidates.value.length} 个候选词吗？`, '拒绝确认', { type: 'warning' })
    await batchRejectCandidates({
      candidateIds: selectedCandidates.value.map(c => c.id)
    })
    ElMessage.success('批量拒绝完成')
    fetchCandidates()
  } catch {}
}

onMounted(() => {
  fetchVersions()
  fetchCandidates()
})
</script>

<style lang="scss" scoped>
@import "@/assets/styles/polaris-ai.scss";

/* 顶部工具栏排版 */
.version-toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding-bottom: 14px;
  margin-bottom: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);

  .dark & {
    border-bottom-color: rgba(255, 255, 255, 0.06);
  }

  .version-select-group {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;

    .toolbar-label {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;

      .dark & {
        color: #f1f5f9;
      }
    }

    .version-select {
      width: 230px;
    }

    .version-status-pill {
      font-size: 11px;
      font-weight: 700;
      padding: 3px 10px;
      border-radius: 20px;
      letter-spacing: 0.5px;

      &.pill-published {
        background: rgba(16, 185, 129, 0.12);
        color: #10b981;
        border: 1px solid rgba(16, 185, 129, 0.25);
      }

      &.pill-draft {
        background: rgba(245, 158, 11, 0.12);
        color: #f59e0b;
        border: 1px solid rgba(245, 158, 11, 0.25);
      }

      &.pill-archived {
        background: rgba(100, 116, 139, 0.12);
        color: #64748b;
        border: 1px solid rgba(100, 116, 139, 0.25);

        .dark & {
          color: #94a3b8;
        }
      }
    }

    .rule-count-hint {
      font-size: 12px;
      color: #64748b;
      margin-left: 4px;

      .dark & {
        color: #94a3b8;
      }
    }
  }

  .version-actions-group {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;

    .candidate-btn {
      position: relative;
    }

    .candidate-badge {
      display: inline-block;
      margin-left: 6px;
      background: #ef4444;
      color: #ffffff;
      font-size: 10px;
      font-weight: 700;
      padding: 1px 6px;
      border-radius: 10px;
    }
  }
}

.dict-filter-form {
  padding-top: 4px;

  :deep(.el-form-item__label) {
    font-size: 13px;
    font-weight: 700;
    color: #334155 !important;

    .dark & {
      color: #e2e8f0 !important;
    }
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.12) !important;
    border-radius: 10px !important;
    box-shadow: none !important;

    .dark & {
      background-color: rgba(0, 0, 0, 0.3) !important;
      border-color: rgba(255, 255, 255, 0.12) !important;
    }
  }
}

/* 表格内字段美化 */
.rule-content-text {
  font-weight: 600;
  color: #0f172a;

  .dark & {
    color: #f1f5f9;
  }
}

.category-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;

  .cat-badge-emoji {
    font-size: 13px;
  }

  .cat-badge-text {
    white-space: nowrap;
  }

  .cat-badge-code {
    font-size: 10px;
    font-family: 'Fira Code', monospace, sans-serif;
    opacity: 0.8;
    background: rgba(0, 0, 0, 0.08);
    padding: 1px 4px;
    border-radius: 4px;

    .dark & {
      background: rgba(255, 255, 255, 0.15);
    }
  }

  &.small {
    padding: 2px 6px;
    font-size: 11px;
  }
}

.category-code-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;

  .dark & {
    color: #818cf8;
  }
}

.rule-type-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;

  &.type-risk {
    background: rgba(239, 68, 68, 0.1);
    color: #ef4444;
    border: 1px solid rgba(239, 68, 68, 0.2);
  }

  &.type-context {
    background: rgba(245, 158, 11, 0.1);
    color: #f59e0b;
    border: 1px solid rgba(245, 158, 11, 0.2);
  }

  &.type-safe {
    background: rgba(16, 185, 129, 0.1);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.2);
  }

  &.type-allow {
    background: rgba(14, 165, 233, 0.1);
    color: #0ea5e9;
    border: 1px solid rgba(14, 165, 233, 0.2);
  }
}

.weight-score {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 700;

  &.score-risk {
    color: #ef4444;
  }

  &.score-safe {
    color: #10b981;
  }

  &.score-neutral {
    color: #94a3b8;
  }
}

/* 弹窗细节 */
.form-tip {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.import-tips-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(79, 70, 229, 0.06);
  border: 1px solid rgba(79, 70, 229, 0.15);
  font-size: 12px;
  color: #4f46e5;
  margin-bottom: 16px;

  .dark & {
    background: rgba(99, 102, 241, 0.12);
    border-color: rgba(99, 102, 241, 0.25);
    color: #818cf8;
  }
}

.import-upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.import-preview-box {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);

  .dark & {
    border-top-color: rgba(255, 255, 255, 0.06);
  }

  .preview-stats {
    font-size: 12px;
    margin-bottom: 8px;
    color: #0f172a;

    .dark & {
      color: #f1f5f9;
    }
  }

  .preview-more-hint {
    font-size: 11px;
    color: #94a3b8;
    margin-top: 6px;
  }
}

/* 候选词抽屉 */
.candidate-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;

  .candidate-desc {
    font-size: 12px;
    color: #64748b;

    .dark & {
      color: #94a3b8;
    }
  }

  .candidate-actions {
    display: flex;
    gap: 8px;
  }
}

.candidate-status-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;

  &.status-pending {
    background: rgba(245, 158, 11, 0.12);
    color: #f59e0b;
  }

  &.status-observing {
    background: rgba(100, 116, 139, 0.12);
    color: #64748b;
  }

  .dark & {
    color: #94a3b8;
  }
}

/* 版本对比弹窗样式 */
.diff-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);

  .dark & {
    border-bottom-color: rgba(255, 255, 255, 0.06);
  }

  .diff-target-info {
    font-size: 13px;
    color: #0f172a;

    .dark & {
      color: #f1f5f9;
    }
  }

  .diff-base-select {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }
}

.diff-summary-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 16px;

  .diff-stat-card {
    padding: 12px 16px;
    border-radius: 10px;
    text-align: center;
    background: rgba(0, 0, 0, 0.02);
    border: 1px solid rgba(0, 0, 0, 0.06);

    .dark & {
      background: rgba(255, 255, 255, 0.03);
      border-color: rgba(255, 255, 255, 0.06);
    }

    .stat-count {
      font-size: 20px;
      font-weight: 800;
      margin-bottom: 4px;
    }

    .stat-label {
      font-size: 12px;
      color: #64748b;
    }

    &.added {
      border-color: rgba(16, 185, 129, 0.3);
      background: rgba(16, 185, 129, 0.06);
      .stat-count { color: #10b981; }
    }

    &.removed {
      border-color: rgba(239, 68, 68, 0.3);
      background: rgba(239, 68, 68, 0.06);
      .stat-count { color: #ef4444; }
    }

    &.modified {
      border-color: rgba(245, 158, 11, 0.3);
      background: rgba(245, 158, 11, 0.06);
      .stat-count { color: #f59e0b; }
    }
  }
}

.diff-tabs {
  margin-top: 8px;
}
</style>
