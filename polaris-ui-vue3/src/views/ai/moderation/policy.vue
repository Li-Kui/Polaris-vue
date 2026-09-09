<template>
  <div class="app-container ai-moderation-policy-manager no-sidebar-manage-wrap">
    <div class="content-inner">
      <!-- 顶部玻璃卡片：页面标题与核心操作 -->
      <div class="polaris-filter-card">
        <div class="policy-header-row">
          <div class="header-info">
            <div class="header-title-wrap">
              <span class="header-icon">🛡️</span>
              <h3 class="header-title">AI 敏感内容安全检测策略管理</h3>
            </div>
            <p class="header-desc">配置各业务场景下的安全拦截预设、阻断模式、第三方云端复核参数与分段策略</p>
          </div>
          <div class="header-actions">
            <el-button type="primary" class="action-btn-primary" icon="Promotion" @click="openTestDialog">
              测试句评估
            </el-button>
            <el-button icon="Refresh" class="action-btn-secondary" @click="fetchPolicies">
              刷新
            </el-button>
          </div>
        </div>
      </div>

      <!-- 策略卡片多列网格容器 -->
      <div class="synapse-card-grid-wrapper polaris-table-card" v-loading="loading">
        <div class="policy-card-grid">
          <div
            v-for="policy in policies"
            :key="policy.scene"
            class="synapse-glass-card policy-item-card"
            :class="policy.enabled ? 'status-border-active' : 'status-border-inactive'"
          >
            <!-- 卡片流光动态微光 -->
            <div class="card-shimmer-ray"></div>

            <!-- 卡片头部：场景标签与启用开关 -->
            <div class="card-header-row">
              <div class="scene-badge-wrap">
                <span :class="['card-code', getSceneTagType(policy.scene)]">
                  <span class="scene-emoji">{{ getSceneEmoji(policy.scene) }}</span>
                  {{ getSceneLabel(policy.scene) }}
                </span>
                <span class="scene-raw-code">{{ policy.scene }}</span>
              </div>
              <el-switch
                v-model="policy.enabled"
                inline-prompt
                active-text="已启用"
                inactive-text="已停用"
                class="policy-switch"
                @change="handlePolicyChange(policy)"
              />
            </div>

            <!-- 卡片主体表单 -->
            <div class="card-body-form">
              <el-form label-position="left" label-width="84px" size="default" class="policy-form">
                <el-form-item label="策略预设">
                  <el-select
                    v-model="policy.preset"
                    class="w-full policy-select"
                    placeholder="请选择预设"
                    @change="handlePresetChange(policy)"
                  >
                    <el-option label="🌱 宽松 (LENIENT)" value="LENIENT" />
                    <el-option label="⚖️ 平衡 (BALANCED)" value="BALANCED" />
                    <el-option label="🔒 严格 (STRICT)" value="STRICT" />
                  </el-select>
                </el-form-item>

                <el-form-item label="执行模式">
                  <el-radio-group
                    v-model="policy.mode"
                    class="policy-radio-group"
                    @change="handleModeChange(policy)"
                  >
                    <el-radio-button value="OBSERVE">仅观察 (OBSERVE)</el-radio-button>
                    <el-radio-button value="ENFORCE">阻断执行 (ENFORCE)</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <div class="section-divider">
                  <span class="divider-title">第三方云端复核</span>
                </div>

                <el-form-item label="云端复核">
                  <div class="provider-switch-row">
                    <el-switch
                      v-model="policy.providerEnabled"
                      class="policy-switch"
                      @change="handlePolicyChange(policy)"
                    />
                    <span class="text-hint-inactive" v-if="!policy.providerEnabled">未开启 (仅本地词库检测)</span>
                    <span class="text-hint-active" v-else>已开启 (疑似触发云端复核)</span>
                  </div>
                </el-form-item>

                <template v-if="policy.providerEnabled">
                  <el-form-item label="每日上限">
                    <el-input-number
                      v-model="policy.providerDailyLimit"
                      :min="0"
                      :step="100"
                      class="w-full policy-number-input"
                      controls-position="right"
                      placeholder="每日限额 (次)"
                      @change="handlePolicyChange(policy)"
                    />
                  </el-form-item>
                  <el-form-item label="月度预算">
                    <el-input-number
                      v-model="policy.providerMonthlyBudget"
                      :min="0"
                      :precision="2"
                      :step="10"
                      class="w-full policy-number-input"
                      controls-position="right"
                      placeholder="月预算 (元)"
                      @change="handlePolicyChange(policy)"
                    />
                  </el-form-item>
                </template>
              </el-form>
            </div>

            <!-- 卡片底部元信息与高级设置 -->
            <div class="card-footer-row">
              <span class="version-badge">版本: v{{ policy.policyVersion || 1 }}</span>
              <button class="advanced-btn" @click="openAdvancedConfig(policy)">
                <span>高级设置</span>
                <span class="arrow">⚙️</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 高级配置抽屉 -->
    <el-drawer
      v-model="advancedDrawerVisible"
      :title="'高级策略配置 - ' + (currentPolicy?.scene || '')"
      size="480px"
      class="polaris-glass-dialog"
      append-to-body
    >
      <el-form v-if="currentPolicy" :model="currentPolicy" label-width="140px" class="drawer-form">
        <el-form-item label="疑似分阈值">
          <el-slider v-model="currentPolicy.suspectThreshold" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="阻断分阈值">
          <el-slider v-model="currentPolicy.blockThreshold" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="文本分段大小 (字)">
          <el-input-number v-model="currentPolicy.segmentChars" :min="50" :max="2000" :step="50" class="w-full" />
        </el-form-item>
        <el-form-item label="分段重叠字数">
          <el-input-number v-model="currentPolicy.segmentOverlapChars" :min="0" :max="200" :step="10" class="w-full" />
        </el-form-item>
        <el-form-item label="流输出缓冲 (字)">
          <el-input-number v-model="currentPolicy.outputBufferChars" :min="50" :max="1000" :step="50" class="w-full" />
        </el-form-item>
        <el-form-item label="隔离区保留天数">
          <el-input-number v-model="currentPolicy.quarantineDays" :min="1" :max="90" class="w-full" />
        </el-form-item>
        <el-form-item label="第三方超时 (ms)">
          <el-input-number v-model="currentPolicy.providerTimeoutMs" :min="100" :max="10000" :step="100" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button class="action-btn-secondary" @click="advancedDrawerVisible = false">取消</el-button>
          <el-button type="primary" class="action-btn-primary" :loading="saving" @click="saveAdvancedPolicy">
            保存并即刻生效
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 测试句评估弹窗 -->
    <el-dialog
      v-model="testDialogVisible"
      title="安全检测测试句评估"
      width="540px"
      class="polaris-glass-dialog"
      append-to-body
      destroy-on-close
    >
      <el-form :model="testForm" label-width="96px" class="test-dialog-form">
        <el-form-item label="测试场景">
          <el-select v-model="testForm.scene" class="w-full">
            <el-option v-for="s in sceneOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="待测文本">
          <el-input
            v-model="testForm.text"
            type="textarea"
            :rows="4"
            placeholder="请输入待测试的文本内容或对话语句..."
          />
        </el-form-item>
        <el-form-item label="第三方复核">
          <div class="test-provider-switch-row">
            <el-switch
              v-model="testForm.useProvider"
              class="policy-switch"
              active-color="#4f46e5"
              inactive-color="#cbd5e1"
            />
            <span :class="['test-provider-tip', testForm.useProvider ? 'tip-active' : 'tip-inactive']">
              {{ testForm.useProvider ? '已开启云端第三方复核 (若场景策略已配置)' : '未开启 (仅本地词库检测)' }}
            </span>
          </div>
        </el-form-item>
      </el-form>

      <!-- 评估结果展示区 -->
      <div v-if="testResult" class="test-result-box">
        <div class="result-header">
          <span class="result-title">评估结果</span>
          <el-tag :type="getActionTagType(testResult.finalAction)" effect="dark" class="action-tag">
            {{ testResult.finalAction }}
          </el-tag>
        </div>
        <div class="result-grid">
          <div class="grid-item"><span class="lbl">本地判定:</span> <span class="val">{{ testResult.localDecision }}</span></div>
          <div class="grid-item"><span class="lbl">综合风险分:</span> <span class="val highlight">{{ testResult.riskScore }}</span></div>
          <div class="grid-item"><span class="lbl">策略版本:</span> <span class="val">v{{ testResult.policyVersion }}</span></div>
          <div class="grid-item"><span class="lbl">词库版本:</span> <span class="val">v{{ testResult.dictionaryVersion }}</span></div>
          <div class="grid-item"><span class="lbl">云端复核:</span> <span class="val">{{ testResult.providerUsed ? '是' : '否' }}</span></div>
          <div class="grid-item"><span class="lbl">执行耗时:</span> <span class="val">{{ testResult.latencyMs }} ms</span></div>
        </div>
        <div v-if="testResult.matchedCategories?.length" class="result-categories">
          <span class="lbl">命中分类: </span>
          <el-tag
            v-for="cat in testResult.matchedCategories"
            :key="cat"
            size="small"
            :type="getCategoryMeta(cat).tagType || 'danger'"
            class="cat-tag"
          >
            {{ getCategoryEmoji(cat) }} {{ getCategoryLabel(cat, true) }}
          </el-tag>
        </div>
        <div v-if="testResult.fallbackReason" class="result-fallback">
          ⚠️ 降级原因: {{ testResult.fallbackReason }}
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="action-btn-secondary" @click="testDialogVisible = false">关闭</el-button>
          <el-button type="primary" class="action-btn-primary" :loading="testing" @click="runSentenceTest">
            执行评估
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {listModerationPolicies, testModerationSentence, updateModerationPolicy} from '@/api/ai/moderation'
import {getCategoryEmoji, getCategoryLabel, getCategoryMeta} from '@/utils/moderationCategory'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const policies = ref([])

const advancedDrawerVisible = ref(false)
const currentPolicy = ref(null)

const testDialogVisible = ref(false)
const testForm = ref({ scene: 'CHAT_INPUT', text: '', useProvider: false })
const testResult = ref(null)

const sceneOptions = [
  { label: '💬 对话用户输入 (CHAT_INPUT)', value: 'CHAT_INPUT' },
  { label: '🤖 AI回复及推理流 (AI_OUTPUT)', value: 'AI_OUTPUT' },
  { label: '📚 知识库与文档 (KNOWLEDGE)', value: 'KNOWLEDGE' },
  { label: '⚡ 工作流输入 (WORKFLOW_INPUT)', value: 'WORKFLOW_INPUT' },
  { label: '⚙️ 工作流节点输出 (WORKFLOW_OUTPUT)', value: 'WORKFLOW_OUTPUT' }
]

const getSceneLabel = scene => {
  const map = {
    CHAT_INPUT: '用户输入',
    AI_OUTPUT: 'AI 输出流',
    KNOWLEDGE: '知识库文档',
    WORKFLOW_INPUT: '工作流输入',
    WORKFLOW_OUTPUT: '工作流输出'
  }
  return map[scene] || scene
}

const getSceneEmoji = scene => {
  const map = {
    CHAT_INPUT: '💬',
    AI_OUTPUT: '🤖',
    KNOWLEDGE: '📚',
    WORKFLOW_INPUT: '⚡',
    WORKFLOW_OUTPUT: '⚙️'
  }
  return map[scene] || '🛡️'
}

const getSceneTagType = scene => {
  const map = {
    CHAT_INPUT: 'tag-chat',
    AI_OUTPUT: 'tag-output',
    KNOWLEDGE: 'tag-knowledge',
    WORKFLOW_INPUT: 'tag-workflow',
    WORKFLOW_OUTPUT: 'tag-workflow-out'
  }
  return map[scene] || 'tag-chat'
}

const getActionTagType = action => {
  if (action === 'ALLOW') return 'success'
  if (action === 'BLOCK') return 'danger'
  if (action === 'QUARANTINE') return 'warning'
  if (action === 'REPLACE') return 'info'
  return 'info'
}

const fetchPolicies = async () => {
  loading.value = true
  try {
    const res = await listModerationPolicies()
    if (res.code === 200) {
      policies.value = res.data || []
    }
  } catch (err) {
    ElMessage.error('获取策略配置失败')
  } finally {
    loading.value = false
  }
}

const handlePresetChange = policy => {
  if (policy.preset === 'LENIENT') {
    policy.suspectThreshold = 50
    policy.blockThreshold = 80
  } else if (policy.preset === 'STRICT') {
    policy.suspectThreshold = 20
    policy.blockThreshold = 50
  } else {
    policy.suspectThreshold = 30
    policy.blockThreshold = 70
  }
  handlePolicyChange(policy)
}

const handleModeChange = async policy => {
  if (policy.mode === 'ENFORCE') {
    try {
      await ElMessageBox.confirm(
        `将场景 [${policy.scene}] 切换为阻断执行 (ENFORCE) 模式后，检测到违规将即时拦截用户操作或打断输出。是否确认切换？`,
        '切换执行模式提示',
        { confirmButtonText: '确认开启', cancelButtonText: '取消', type: 'warning' }
      )
      handlePolicyChange(policy)
    } catch {
      policy.mode = 'OBSERVE'
    }
  } else {
    handlePolicyChange(policy)
  }
}

const handlePolicyChange = async policy => {
  try {
    const res = await updateModerationPolicy(policy.scene, policy)
    if (res.code === 200) {
      ElMessage.success(`策略 [${policy.scene}] 已更新并即刻生效`)
      if (res.data) Object.assign(policy, res.data)
    }
  } catch (err) {
    ElMessage.error(`更新策略失败: ${err.message || '系统错误'}`)
  }
}

const openAdvancedConfig = policy => {
  currentPolicy.value = JSON.parse(JSON.stringify(policy))
  advancedDrawerVisible.value = true
}

const saveAdvancedPolicy = async () => {
  if (!currentPolicy.value) return
  saving.value = true
  try {
    const res = await updateModerationPolicy(currentPolicy.value.scene, currentPolicy.value)
    if (res.code === 200) {
      ElMessage.success('高级设置已保存并生效')
      advancedDrawerVisible.value = false
      fetchPolicies()
    }
  } catch (err) {
    ElMessage.error(`保存失败: ${err.message || '参数不合法'}`)
  } finally {
    saving.value = false
  }
}

const openTestDialog = () => {
  testResult.value = null
  testDialogVisible.value = true
}

const runSentenceTest = async () => {
  if (!testForm.value.text?.trim()) {
    ElMessage.warning('请输入待测文本')
    return
  }
  testing.value = true
  try {
    const res = await testModerationSentence(testForm.value)
    if (res.code === 200) {
      testResult.value = res.data
    }
  } catch (err) {
    ElMessage.error(`测试执行失败: ${err.message || '服务异常'}`)
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  fetchPolicies()
})
</script>

<style lang="scss" scoped>
@import "@/assets/styles/polaris-ai.scss";

/* 头部操作排版 */
.policy-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;

  .header-info {
    .header-title-wrap {
      display: flex;
      align-items: center;
      gap: 10px;

      .header-icon {
        font-size: 22px;
      }

      .header-title {
        font-size: 17px;
        font-weight: 800;
        margin: 0;
        color: #0f172a;

        .dark & {
          color: #f1f5f9;
        }
      }
    }

    .header-desc {
      font-size: 13px;
      margin: 4px 0 0 32px;
      color: #475569;
      font-weight: 500;

      .dark & {
        color: #94a3b8;
      }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

/* 策略卡片多列网格布局 */
.policy-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
  width: 100%;
  padding-bottom: 8px;
}

/* 卡片样式细节 */
.policy-item-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 340px;
  padding: 22px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.03);

  .dark & {
    background: rgba(15, 23, 42, 0.55);
    border-color: rgba(255, 255, 255, 0.08);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35);
  }

  .card-header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 14px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);

    .dark & {
      border-bottom-color: rgba(255, 255, 255, 0.08);
    }

    .scene-badge-wrap {
      display: flex;
      align-items: center;
      gap: 8px;

      .scene-raw-code {
        font-size: 12px;
        font-weight: 700;
        color: #1e293b;
        letter-spacing: 0.5px;

        .dark & {
          color: #e2e8f0;
        }
      }
    }
  }

  .card-body-form {
    flex: 1;

    .policy-form {
      :deep(.el-form-item) {
        margin-bottom: 14px;
      }

      :deep(.el-form-item__label) {
        font-size: 13px;
        font-weight: 700;
        color: #334155 !important;

        .dark & {
          color: #e2e8f0 !important;
        }
      }
    }

    .section-divider {
      position: relative;
      margin: 16px 0 12px;
      text-align: left;

      &::before {
        content: '';
        position: absolute;
        top: 50%;
        left: 0;
        right: 0;
        height: 1px;
        background: rgba(0, 0, 0, 0.08);

        .dark & {
          background: rgba(255, 255, 255, 0.08);
        }
      }

      .divider-title {
        position: relative;
        background: #ffffff;
        padding-right: 10px;
        font-size: 12px;
        font-weight: 700;
        color: #475569;

        .dark & {
          background: #151d2e;
          color: #94a3b8;
        }
      }
    }

    .provider-switch-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
    }

    .text-hint-inactive {
      font-size: 12px;
      font-weight: 600;
      color: #64748b;

      .dark & {
        color: #94a3b8;
      }
    }

    .text-hint-active {
      font-size: 12px;
      font-weight: 700;
      color: #10b981;
    }
  }

  .card-footer-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px dashed rgba(0, 0, 0, 0.08);

    .dark & {
      border-top-color: rgba(255, 255, 255, 0.08);
    }

    .version-badge {
      font-size: 11px;
      font-weight: 700;
      color: #475569;
      background: rgba(0, 0, 0, 0.06);
      padding: 3px 10px;
      border-radius: 6px;

      .dark & {
        background: rgba(255, 255, 255, 0.08);
        color: #cbd5e1;
      }
    }

    .advanced-btn {
      background: transparent;
      border: none;
      font-size: 13px;
      font-weight: 700;
      color: #4f46e5;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 4px;
      transition: all 0.2s ease;

      .arrow {
        font-size: 13px;
      }

      &:hover {
        color: #4338ca;
        transform: translateX(2px);
      }

      .dark & {
        color: #38bdf8;

        &:hover {
          color: #7dd3fc;
        }
      }
    }
  }
}

.policy-radio-group {
  display: flex;
  width: 100%;
  gap: 8px;

  :deep(.el-radio-button) {
    flex: 1;
    margin: 0 !important;

    .el-radio-button__inner {
      width: 100%;
      border-radius: 8px !important;
      background-color: #f1f5f9 !important;
      color: #334155 !important;
      border: 1px solid rgba(0, 0, 0, 0.08) !important;
      padding: 8px 12px;
      font-size: 12px;
      font-weight: 700;
      box-shadow: none !important;
      transition: all 0.25s ease;

      .dark & {
        background-color: rgba(255, 255, 255, 0.06) !important;
        color: #cbd5e1 !important;
        border-color: rgba(255, 255, 255, 0.08) !important;
      }
    }

    &:first-child .el-radio-button__inner {
      border-left: 1px solid rgba(0, 0, 0, 0.08) !important;
      border-radius: 8px !important;

      .dark & {
        border-left-color: rgba(255, 255, 255, 0.08) !important;
      }
    }

    &:last-child .el-radio-button__inner {
      border-radius: 8px !important;
    }

    &.is-active .el-radio-button__inner {
      background-color: #4f46e5 !important;
      color: #ffffff !important;
      border-color: #4f46e5 !important;
      box-shadow: 0 2px 8px rgba(79, 70, 229, 0.3) !important;

      .dark & {
        background-color: #38bdf8 !important;
        color: #0f172a !important;
        border-color: #38bdf8 !important;
        box-shadow: 0 2px 8px rgba(56, 189, 248, 0.3) !important;
      }
    }
  }
}

/* Switch 开关统一双模式配色 */
.policy-switch {
  :deep(.el-switch__core) {
    background-color: #cbd5e1 !important;
    border-color: #cbd5e1 !important;

    .dark & {
      background-color: #475569 !important;
      border-color: #475569 !important;
    }
  }

  &.is-checked :deep(.el-switch__core) {
    background-color: #4f46e5 !important;
    border-color: #4f46e5 !important;

    .dark & {
      background-color: #38bdf8 !important;
      border-color: #38bdf8 !important;
    }
  }
}

/* 下拉选择器与数字输入框 */
.policy-select {
  :deep(.el-select__wrapper) {
    border-radius: 10px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.12) !important;
    box-shadow: none !important;

    .dark & {
      background-color: rgba(0, 0, 0, 0.3) !important;
      border-color: rgba(255, 255, 255, 0.12) !important;
    }
  }

  :deep(.el-select__placeholder) {
    color: #0f172a !important;
    font-weight: 600;

    .dark & {
      color: #f1f5f9 !important;
    }
  }
}

.policy-number-input {
  :deep(.el-input__wrapper) {
    border-radius: 10px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.12) !important;
    box-shadow: none !important;

    .dark & {
      background-color: rgba(0, 0, 0, 0.3) !important;
      border-color: rgba(255, 255, 255, 0.12) !important;
    }
  }

  :deep(.el-input__inner) {
    color: #0f172a !important;
    font-weight: 700;

    .dark & {
      color: #f1f5f9 !important;
    }
  }
}

/* 场景标签颜色 */
.tag-chat {
  background: rgba(79, 70, 229, 0.12) !important;
  color: #4f46e5 !important;
  border-color: rgba(79, 70, 229, 0.25) !important;
  font-weight: 700 !important;
  .dark & {
    background: rgba(99, 102, 241, 0.2) !important;
    color: #818cf8 !important;
  }
}

.tag-output {
  background: rgba(16, 185, 129, 0.12) !important;
  color: #10b981 !important;
  border-color: rgba(16, 185, 129, 0.25) !important;
  font-weight: 700 !important;
  .dark & {
    background: rgba(16, 185, 129, 0.2) !important;
    color: #34d399 !important;
  }
}

.tag-knowledge {
  background: rgba(245, 158, 11, 0.12) !important;
  color: #d97706 !important;
  border-color: rgba(245, 158, 11, 0.25) !important;
  font-weight: 700 !important;
  .dark & {
    background: rgba(245, 158, 11, 0.2) !important;
    color: #fbbf24 !important;
  }
}

.tag-workflow {
  background: rgba(14, 165, 233, 0.12) !important;
  color: #0284c7 !important;
  border-color: rgba(14, 165, 233, 0.25) !important;
  font-weight: 700 !important;
  .dark & {
    background: rgba(14, 165, 233, 0.2) !important;
    color: #38bdf8 !important;
  }
}

.tag-workflow-out {
  background: rgba(168, 85, 247, 0.12) !important;
  color: #9333ea !important;
  border-color: rgba(168, 85, 247, 0.25) !important;
  font-weight: 700 !important;
  .dark & {
    background: rgba(168, 85, 247, 0.2) !important;
    color: #c084fc !important;
  }
}

/* 测试评估结果框 */
.test-result-box {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.03);
  border: 1px solid rgba(0, 0, 0, 0.08);

  .dark & {
    background: rgba(255, 255, 255, 0.04);
    border-color: rgba(255, 255, 255, 0.08);
  }

  .result-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 10px;
    margin-bottom: 10px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);

    .dark & {
      border-bottom-color: rgba(255, 255, 255, 0.08);
    }

    .result-title {
      font-size: 13px;
      font-weight: 700;
      color: #0f172a;

      .dark & {
        color: #f1f5f9;
      }
    }
  }

  .result-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px 16px;
    font-size: 12px;

    .grid-item {
      .lbl {
        color: #475569;
        font-weight: 600;
        margin-right: 6px;

        .dark & {
          color: #94a3b8;
        }
      }

      .val {
        font-weight: 700;
        color: #0f172a;

        .dark & {
          color: #f1f5f9;
        }

        &.highlight {
          color: #ef4444;
        }
      }
    }
  }

  .result-categories {
    margin-top: 10px;
    font-size: 12px;

    .lbl {
      color: #475569;
      font-weight: 600;
      margin-right: 6px;

      .dark & {
        color: #94a3b8;
      }
    }

    .cat-tag {
      margin-right: 6px;
      margin-bottom: 4px;
    }
  }

  .result-fallback {
    margin-top: 8px;
    font-size: 11px;
    font-weight: 700;
    color: #f59e0b;
  }
}

/* 测试评估弹窗表单内部开关美化 */
.test-dialog-form {
  .test-provider-switch-row {
    display: flex;
    align-items: center;
    gap: 12px;

    .test-provider-tip {
      font-size: 13px;
      font-weight: 600;
      white-space: nowrap;
      transition: all 0.25s ease;

      &.tip-active {
        color: #4f46e5;
        font-weight: 700;
      }

      &.tip-inactive {
        color: #64748b;
      }
    }
  }
}

.dark .test-dialog-form {
  .test-provider-switch-row {
    .test-provider-tip {
      &.tip-active {
        color: #38bdf8;
      }

      &.tip-inactive {
        color: #94a3b8;
      }
    }
  }
}
</style>
