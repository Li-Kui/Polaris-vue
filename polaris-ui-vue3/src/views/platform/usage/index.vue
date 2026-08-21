<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <!-- 顶部用量仪表盘卡片 -->
      <div class="usage-hero-card">
        <div class="hero-left">
          <div class="usage-badge">
            <span class="pulse-point"></span>
            <span>实时用量计量中心</span>
          </div>
          <h2 class="hero-title">Token 资源消耗与配额</h2>
          <p class="hero-desc">实时监控当前租户调用 AI 大模型消耗的 Token 资源与剩余额度情况。</p>
          <div class="quota-numbers-row">
            <div class="q-item">
              <span class="q-label">已消耗 Token</span>
              <span class="q-val used">{{ formatNumber(usageData.usedTokens || 0) }}</span>
            </div>
            <div class="q-split">/</div>
            <div class="q-item">
              <span class="q-label">配额上限</span>
              <span class="q-val total">{{ usageData.quotaTokens === -1 ? '无限' : formatNumber(usageData.quotaTokens || 0) }}</span>
            </div>
            <div class="q-item ml-auto">
              <span class="q-label">剩余可用</span>
              <span class="q-val remain">{{ usageData.quotaTokens === -1 ? '不限量' : formatNumber(remainTokens) }}</span>
            </div>
          </div>
        </div>

        <div class="hero-right">
          <div class="progress-circle-box">
            <el-progress
              type="dashboard"
              :percentage="usagePercent"
              :width="140"
              :stroke-width="12"
              :color="customColors"
            >
              <template #default="{ percentage }">
                <div class="percent-inner">
                  <span class="num">{{ percentage }}%</span>
                  <span class="txt">已使用</span>
                </div>
              </template>
            </el-progress>
          </div>
        </div>
      </div>

      <!-- 配额保障与充值说明卡片 -->
      <div class="quota-policy-grid">
        <div class="policy-card">
          <div class="card-icon-box purple">
            <el-icon><CreditCard /></el-icon>
          </div>
          <h3 class="card-title">配额充值与调整</h3>
          <p class="card-desc">当租户可用 Token 余额不足时，调用开放 API 将返回 429 错误。请联系系统超管在管理后台进行额度扩容或充值。</p>
          <div class="card-footer-tip">
            <span class="tip-label">当前计费模式:</span>
            <span class="tip-val">按实际 Token 消耗计量 (Token Based)</span>
          </div>
        </div>

        <div class="policy-card">
          <div class="card-icon-box blue">
            <el-icon><Document /></el-icon>
          </div>
          <h3 class="card-title">Token 消耗规则</h3>
          <p class="card-desc">对话补全请求将根据输入 Prompt Token 与大模型生成 Reply Token 之和实时进行累加计费，流式 SSE 对话在连接断开或完成时一次性结算。</p>
          <div class="card-footer-tip">
            <span class="tip-label">支持协议:</span>
            <span class="tip-val">OpenAI Chat Completions v1</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {CreditCard, Document} from '@element-plus/icons-vue'
import {getUsage} from '@/api/platform/usage'
import usePlatformUserStore from '@/store/modules/platformUser'

const platformUserStore = usePlatformUserStore()
const usageData = ref({
  usedTokens: 0,
  quotaTokens: -1
})

const customColors = [
  { color: '#10b981', percentage: 40 },
  { color: '#6366f1', percentage: 75 },
  { color: '#f59e0b', percentage: 90 },
  { color: '#ef4444', percentage: 100 }
]

const usagePercent = computed(() => {
  if (!usageData.value.quotaTokens || usageData.value.quotaTokens <= 0) return 0
  const pct = (usageData.value.usedTokens / usageData.value.quotaTokens) * 100
  return Math.min(100, Math.round(pct * 10) / 10)
})

const remainTokens = computed(() => {
  if (usageData.value.quotaTokens === -1) return -1
  const remain = (usageData.value.quotaTokens || 0) - (usageData.value.usedTokens || 0)
  return Math.max(0, remain)
})

function formatNumber(num) {
  if (num === null || num === undefined) return '0'
  return Number(num).toLocaleString('en-US')
}

function loadUsage() {
  getUsage().then(res => {
    if (res.data) {
      usageData.value = res.data
    }
  }).catch(() => {
    if (platformUserStore.tenant) {
      usageData.value = {
        usedTokens: platformUserStore.tenant.usedTokens || 0,
        quotaTokens: platformUserStore.tenant.quotaTokens || -1
      }
    }
  })
}

onMounted(() => {
  loadUsage()
})
</script>

<style lang="scss" scoped>
.app-container.no-sidebar-manage-wrap {
  padding: 0 !important;
}

.content-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.usage-hero-card {
  padding: 32px 36px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.02);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;

  .hero-left {
    flex: 1;

    .usage-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 4px 12px;
      border-radius: 20px;
      background: rgba(99, 102, 241, 0.08);
      color: #6366f1;
      font-size: 12px;
      font-weight: 700;
      margin-bottom: 12px;

      .pulse-point {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #6366f1;
        box-shadow: 0 0 6px #6366f1;
      }
    }

    .hero-title {
      font-size: 24px;
      font-weight: 800;
      color: #0f172a;
      margin: 0 0 8px;
      letter-spacing: -0.3px;
    }

    .hero-desc {
      font-size: 13px;
      color: #64748b;
      margin: 0 0 24px;
      max-width: 600px;
    }

    .quota-numbers-row {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px 20px;
      border-radius: 14px;
      background: #f8fafc;
      border: 1px solid #f1f5f9;

      .q-item {
        display: flex;
        flex-direction: column;

        .q-label {
          font-size: 11px;
          font-weight: 600;
          color: #94a3b8;
          text-transform: uppercase;
          margin-bottom: 2px;
        }

        .q-val {
          font-family: 'JetBrains Mono', Consolas, monospace;
          font-size: 20px;
          font-weight: 800;

          &.used { color: #4f46e5; }
          &.total { color: #334155; }
          &.remain { color: #10b981; }
        }

        &.ml-auto {
          margin-left: auto;
        }
      }

      .q-split {
        font-size: 22px;
        color: #cbd5e1;
        font-weight: 300;
      }
    }
  }

  .hero-right {
    padding-left: 20px;

    .percent-inner {
      display: flex;
      flex-direction: column;
      align-items: center;

      .num {
        font-size: 22px;
        font-weight: 800;
        font-family: 'JetBrains Mono', Consolas, monospace;
        color: #0f172a;
      }
      .txt {
        font-size: 11px;
        color: #94a3b8;
      }
    }
  }

  .dark & {
    background: #0f172a;
    border-color: #1e293b;

    .hero-title { color: #f8fafc; }
    .hero-desc { color: #94a3b8; }

    .quota-numbers-row {
      background: rgba(30, 41, 59, 0.5);
      border-color: rgba(255, 255, 255, 0.06);

      .q-val {
        &.used { color: #818cf8; }
        &.total { color: #e2e8f0; }
        &.remain { color: #34d399; }
      }
    }

    .percent-inner .num { color: #f8fafc; }
  }
}

.quota-policy-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.policy-card {
  padding: 24px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;

  .card-icon-box {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    margin-bottom: 14px;

    &.purple {
      background: rgba(99, 102, 241, 0.1);
      color: #6366f1;
    }
    &.blue {
      background: rgba(2, 132, 199, 0.1);
      color: #0284c7;
    }
  }

  .card-title {
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 8px;
  }

  .card-desc {
    font-size: 13px;
    color: #64748b;
    line-height: 1.6;
    margin: 0 0 20px;
    flex: 1;
  }

  .card-footer-tip {
    padding-top: 14px;
    border-top: 1px solid #f1f5f9;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;

    .tip-label {
      color: #94a3b8;
    }
    .tip-val {
      font-weight: 600;
      color: #475569;
    }
  }

  .dark & {
    background: #0f172a;
    border-color: #1e293b;

    .card-title { color: #f8fafc; }
    .card-desc { color: #94a3b8; }
    .card-footer-tip {
      border-top-color: #1e293b;
      .tip-val { color: #cbd5e1; }
    }
  }
}
</style>
