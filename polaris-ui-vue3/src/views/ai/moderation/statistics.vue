<template>
  <div class="app-container ai-moderation-statistics no-sidebar-manage-wrap">
    <div class="content-inner">
      <!-- 顶部玻璃卡片：页面标题与运维操作 -->
      <div class="polaris-filter-card">
        <div class="stats-header-row">
          <div class="stats-header-info">
            <div class="header-title-wrap">
              <span class="header-icon">📊</span>
              <h3 class="header-title">AI 敏感内容安全检测统计与运维</h3>
            </div>
            <p class="header-desc">实时监控全平台内容安全检测吞吐量、拦截分布、第三方复核费用与隔离存储状态</p>
          </div>
          <div class="stats-header-actions">
            <el-radio-group v-model="selectedDays" class="polaris-radio-group" @change="fetchSummary">
              <el-radio-button :label="7">近 7 天</el-radio-button>
              <el-radio-button :label="14">近 14 天</el-radio-button>
              <el-radio-button :label="30">近 30 天</el-radio-button>
            </el-radio-group>
            <el-button
              type="danger"
              class="action-btn-danger"
              icon="Delete"
              :loading="cleaning"
              @click="handleTriggerCleanup"
            >
              触发定时清理
            </el-button>
            <el-button icon="Refresh" class="action-btn-secondary" @click="fetchSummary">
              刷新
            </el-button>
          </div>
        </div>
      </div>

      <!-- 6 大核心指标卡片网格 -->
      <div class="metrics-grid" v-loading="loading">
        <!-- 1. 总检测请求 -->
        <div class="synapse-glass-card metric-item-card">
          <div class="card-shimmer-ray"></div>
          <div class="metric-icon-wrap icon-blue">📈</div>
          <div class="metric-content">
            <div class="metric-title">总检测请求</div>
            <div class="metric-value val-blue">{{ summary.totalRequests }}</div>
          </div>
        </div>

        <!-- 2. 安全放行量 -->
        <div class="synapse-glass-card metric-item-card">
          <div class="card-shimmer-ray"></div>
          <div class="metric-icon-wrap icon-green">🛡️</div>
          <div class="metric-content">
            <div class="metric-title">安全放行量</div>
            <div class="metric-value val-green">{{ summary.allowCount }}</div>
          </div>
        </div>

        <!-- 3. 机器阻断拦截 -->
        <div class="synapse-glass-card metric-item-card">
          <div class="card-shimmer-ray"></div>
          <div class="metric-icon-wrap icon-red">🚫</div>
          <div class="metric-content">
            <div class="metric-title">机器阻断拦截</div>
            <div class="metric-value val-red">{{ summary.blockCount }}</div>
          </div>
        </div>

        <!-- 4. 隔离处置文档 -->
        <div class="synapse-glass-card metric-item-card">
          <div class="card-shimmer-ray"></div>
          <div class="metric-icon-wrap icon-orange">📦</div>
          <div class="metric-content">
            <div class="metric-title">隔离处置文档</div>
            <div class="metric-value val-orange">{{ summary.quarantineCount }}</div>
          </div>
        </div>

        <!-- 5. 第三方复核调用 -->
        <div class="synapse-glass-card metric-item-card">
          <div class="card-shimmer-ray"></div>
          <div class="metric-icon-wrap icon-purple">☁️</div>
          <div class="metric-content">
            <div class="metric-title">第三方复核调用</div>
            <div class="metric-value val-purple">{{ summary.providerCalls }}</div>
          </div>
        </div>

        <!-- 6. 预估云端费用 -->
        <div class="synapse-glass-card metric-item-card">
          <div class="card-shimmer-ray"></div>
          <div class="metric-icon-wrap icon-yellow">💰</div>
          <div class="metric-content">
            <div class="metric-title">预估云端费用</div>
            <div class="metric-value val-yellow">¥{{ summary.estimatedProviderCost }}</div>
          </div>
        </div>
      </div>

      <!-- 下方图表与明细卡片 -->
      <div class="stats-detail-row">
        <!-- 违规类别分布卡片 -->
        <div class="polaris-table-card stats-sub-card category-card">
          <div class="card-title-bar">
            <span class="title-icon">🏷️</span>
            <span class="title-text">违规风险分类分布</span>
          </div>

          <div v-if="categoryList.length" class="category-list">
            <div v-for="item in categoryList" :key="item.category" class="category-item-row">
              <div class="cat-name-box">
                <span class="cat-emoji">{{ getCategoryEmoji(item.category) }}</span>
                <span class="cat-name">{{ getCategoryLabel(item.category) }}</span>
                <span class="cat-code" :title="item.category">{{ item.category }}</span>
              </div>
              <div class="cat-bar-wrap">
                <el-progress
                  :percentage="item.percentage"
                  :color="getProgressColor(item.category)"
                  :stroke-width="8"
                  :show-text="false"
                  class="polaris-progress-bar"
                />
                <span class="cat-count">{{ item.count }} 次</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无违规分类数据" :image-size="70" class="custom-empty" />
        </div>

        <!-- 隔离存储与运维状态卡片 -->
        <div class="polaris-table-card stats-sub-card ops-card">
          <div class="card-title-bar">
            <span class="title-icon">⚙️</span>
            <span class="title-text">隔离存储与候选词维护</span>
          </div>

          <div class="ops-grid">
            <div class="ops-status-box box-orange">
              <div class="box-left">
                <div class="box-title">隔离区存量文件</div>
                <div class="box-num num-orange">{{ summary.activeQuarantineDocuments }}</div>
                <div class="box-desc">7 天到期自动物理销毁</div>
              </div>
              <span class="box-icon">🔒</span>
            </div>

            <div class="ops-status-box box-blue">
              <div class="box-left">
                <div class="box-title">待处理候选敏感词</div>
                <div class="box-num num-blue">{{ summary.pendingCandidates }}</div>
                <div class="box-desc">请在词库管理中批处理</div>
              </div>
              <span class="box-icon">📝</span>
            </div>
          </div>

          <div class="ops-note-box">
            <span class="note-icon">💡</span>
            <div class="note-text">
              <strong>自动运维说明：</strong> 系统后台通过 Quartz 定时作业 <code>moderationCleanupTask.run</code> 每 15 分钟自动巡检，物理删除超期隔离文件、过期暂存附件与超期审计记录。
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {getModerationSummary, triggerModerationCleanup} from '@/api/ai/moderation'
import {getCategoryColor, getCategoryEmoji, getCategoryLabel} from '@/utils/moderationCategory'

const loading = ref(false)
const cleaning = ref(false)
const selectedDays = ref(7)

const summary = ref({
  totalRequests: 0,
  allowCount: 0,
  blockCount: 0,
  quarantineCount: 0,
  replaceCount: 0,
  providerCalls: 0,
  providerCacheHits: 0,
  estimatedProviderCost: '0.00',
  activeQuarantineDocuments: 0,
  pendingCandidates: 0,
  categoryDistribution: {},
  dailyTrends: {}
})

const categoryList = computed(() => {
  const dist = summary.value.categoryDistribution || {}
  const total = Object.values(dist).reduce((a, b) => a + b, 0)
  if (!total) return []
  return Object.entries(dist).map(([category, count]) => ({
    category,
    count,
    percentage: Math.min(100, Math.round((count / total) * 100))
  })).sort((a, b) => b.count - a.count)
})

const getProgressColor = cat => {
  return getCategoryColor(cat)
}

const fetchSummary = async () => {
  loading.value = true
  try {
    const res = await getModerationSummary({ days: selectedDays.value })
    if (res.code === 200) {
      summary.value = res.data || summary.value
    }
  } catch (err) {
    ElMessage.error('获取统计数据失败')
  } finally {
    loading.value = false
  }
}

const handleTriggerCleanup = async () => {
  try {
    await ElMessageBox.confirm('将立即执行到期隔离文件、暂存附件与历史审计清理任务，是否继续？', '清理确认', { type: 'warning' })
    cleaning.value = true
    const res = await triggerModerationCleanup()
    if (res.code === 200) {
      ElMessage.success('清理任务执行成功')
      fetchSummary()
    }
  } catch {} finally {
    cleaning.value = false
  }
}

onMounted(() => {
  fetchSummary()
})
</script>

<style lang="scss" scoped>
@import "@/assets/styles/polaris-ai.scss";

/* 头部排版 */
.stats-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;

  .stats-header-info {
    .header-title-wrap {
      display: flex;
      align-items: center;
      gap: 10px;

      .header-icon {
        font-size: 22px;
      }

      .header-title {
        font-size: 17px;
        font-weight: 700;
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
      color: #64748b;

      .dark & {
        color: #94a3b8;
      }
    }
  }

  .stats-header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;

    .polaris-radio-group {
      display: inline-flex;
      align-items: center;
      gap: 8px;

      :deep(.el-radio-button) {
        margin: 0 !important;

        .el-radio-button__inner {
          border-radius: 8px !important;
          background-color: #f1f5f9 !important;
          color: #334155 !important;
          border: 1px solid rgba(0, 0, 0, 0.08) !important;
          padding: 8px 14px;
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
  }
}

/* 6 大指标卡片网格 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;

  .metric-item-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 18px 20px;
    min-height: auto;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.65);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border: 1px solid rgba(255, 255, 255, 0.6);
    box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);
    position: relative;
    overflow: hidden;
    transition: all 0.35s cubic-bezier(0.25, 0.8, 0.25, 1);

    .dark & {
      background: rgba(15, 23, 42, 0.45);
      border-color: rgba(255, 255, 255, 0.06);
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
    }

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 12px 30px -4px rgba(0, 0, 0, 0.06);

      .card-shimmer-ray {
        transform: skewX(-20deg) translateX(300px);
      }

      .dark & {
        box-shadow: 0 16px 36px rgba(0, 0, 0, 0.4);
      }
    }

    .metric-icon-wrap {
      width: 44px;
      height: 44px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
      transition: transform 0.3s ease;

      &.icon-blue { background: rgba(59, 130, 246, 0.12); }
      &.icon-green { background: rgba(16, 185, 129, 0.12); }
      &.icon-red { background: rgba(239, 68, 68, 0.12); }
      &.icon-orange { background: rgba(245, 158, 11, 0.12); }
      &.icon-purple { background: rgba(168, 85, 247, 0.12); }
      &.icon-yellow { background: rgba(234, 179, 8, 0.12); }
    }

    &:hover .metric-icon-wrap {
      transform: scale(1.08);
    }

    .metric-content {
      display: flex;
      flex-direction: column;
      gap: 2px;
      overflow: hidden;

      .metric-title {
        font-size: 12px;
        font-weight: 600;
        color: #64748b;
        white-space: nowrap;

        .dark & {
          color: #94a3b8;
        }
      }

      .metric-value {
        font-size: 22px;
        font-weight: 800;
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        letter-spacing: -0.5px;

        &.val-blue { color: #3b82f6; }
        &.val-green { color: #10b981; }
        &.val-red { color: #ef4444; }
        &.val-orange { color: #f59e0b; }
        &.val-purple { color: #a855f7; }
        &.val-yellow { color: #eab308; }
      }
    }
  }
}

/* 下方明细卡片 */
.stats-detail-row {
  display: grid;
  grid-template-columns: 4fr 6fr;
  gap: 16px;

  @media (max-width: 992px) {
    grid-template-columns: 1fr;
  }

  .stats-sub-card {
    padding: 20px 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;

    .card-title-bar {
      display: flex;
      align-items: center;
      gap: 8px;
      padding-bottom: 12px;
      border-bottom: 1px solid rgba(0, 0, 0, 0.05);

      .dark & {
        border-bottom-color: rgba(255, 255, 255, 0.06);
      }

      .title-icon {
        font-size: 16px;
      }

      .title-text {
        font-size: 14px;
        font-weight: 700;
        color: #0f172a;

        .dark & {
          color: #f1f5f9;
        }
      }
    }
  }
}

/* 违规类别列表 */
.category-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;

  .category-item-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;

    .cat-name-box {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 145px;
      flex-shrink: 0;

      .cat-emoji {
        font-size: 14px;
        line-height: 1;
      }

      .cat-name {
        font-size: 13px;
        font-weight: 600;
        color: #1e293b;

        .dark & {
          color: #f1f5f9;
        }
      }

      .cat-code {
        font-size: 10px;
        font-family: 'Fira Code', monospace, sans-serif;
        color: #64748b;
        background: rgba(148, 163, 184, 0.15);
        padding: 1px 6px;
        border-radius: 4px;
        font-weight: 500;
        max-width: 90px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;

        .dark & {
          color: #94a3b8;
          background: rgba(148, 163, 184, 0.25);
        }
      }
    }

    .cat-bar-wrap {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 10px;

      .polaris-progress-bar {
        flex: 1;
      }

      .cat-count {
        font-size: 11px;
        font-weight: 700;
        color: #64748b;
        min-width: 48px;
        text-align: right;

        .dark & {
          color: #94a3b8;
        }
      }
    }
  }
}

/* 运维状态双卡片 */
.ops-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  .ops-status-box {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-radius: 12px;
    border: 1px solid transparent;

    &.box-orange {
      background: rgba(245, 158, 11, 0.06);
      border-color: rgba(245, 158, 11, 0.15);

      .dark & {
        background: rgba(245, 158, 11, 0.1);
        border-color: rgba(245, 158, 11, 0.25);
      }
    }

    &.box-blue {
      background: rgba(59, 130, 246, 0.06);
      border-color: rgba(59, 130, 246, 0.15);

      .dark & {
        background: rgba(59, 130, 246, 0.1);
        border-color: rgba(59, 130, 246, 0.25);
      }
    }

    .box-left {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .box-title {
        font-size: 12px;
        font-weight: 700;
        color: #64748b;

        .dark & {
          color: #94a3b8;
        }
      }

      .box-num {
        font-size: 22px;
        font-weight: 800;
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;

        &.num-orange { color: #f59e0b; }
        &.num-blue { color: #3b82f6; }
      }

      .box-desc {
        font-size: 11px;
        color: #94a3b8;

        .dark & {
          color: #64748b;
        }
      }
    }

    .box-icon {
      font-size: 28px;
      opacity: 0.85;
    }
  }
}

.ops-note-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.02);
  border: 1px dashed rgba(0, 0, 0, 0.08);

  .dark & {
    background: rgba(255, 255, 255, 0.03);
    border-color: rgba(255, 255, 255, 0.08);
  }

  .note-icon {
    font-size: 14px;
    margin-top: 1px;
  }

  .note-text {
    font-size: 12px;
    line-height: 1.6;
    color: #64748b;

    .dark & {
      color: #94a3b8;
    }

    code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      background: rgba(0, 0, 0, 0.05);
      padding: 1px 4px;
      border-radius: 4px;
      color: #4f46e5;

      .dark & {
        background: rgba(255, 255, 255, 0.08);
        color: #818cf8;
      }
    }
  }
}
</style>
