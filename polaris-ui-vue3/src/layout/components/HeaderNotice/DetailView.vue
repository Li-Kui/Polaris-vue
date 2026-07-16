<template>
  <el-drawer v-model="visible" title="公告详情" direction="rtl" size="50%" append-to-body :before-close="handleClose" class="notice-detail-drawer polaris-glass-drawer">
    <div v-loading="loading" class="notice-detail-drawer__body">
      <div v-if="!detail" class="notice-empty">
        <el-icon><Document /></el-icon>
        <span>暂无数据</span>
      </div>
      <div v-else class="notice-page">
        <div class="notice-type-wrap">
          <span v-if="detail.noticeType === '1'" class="notice-type-tag type-notify">
            <el-icon><Bell /></el-icon> 通知
          </span>
          <span v-else-if="detail.noticeType === '2'" class="notice-type-tag type-announce">
            <el-icon><Message /></el-icon> 公告
          </span>
          <span v-else class="notice-type-tag type-notify">
            <el-icon><Document /></el-icon> 消息
          </span>
        </div>

        <h1 class="notice-title">{{ detail.noticeTitle }}</h1>

        <div class="notice-meta">
          <span class="meta-item">
            <el-icon><User /></el-icon>
            <span>{{ detail.createBy || '—' }}</span>
          </span>
          <span class="meta-item">
            <el-icon><Clock /></el-icon>
            <span>{{ detail.createTime || '—' }}</span>
          </span>
          <span class="meta-item">
            <span :class="['status-dot', isStatusNormal ? 'status-ok' : 'status-off']"></span>
            <span>{{ isStatusNormal ? '正常' : '已关闭' }}</span>
          </span>
        </div>

        <div class="notice-divider">
          <span class="notice-divider-dot"></span>
          <span class="notice-divider-dot"></span>
          <span class="notice-divider-dot"></span>
        </div>

        <div class="notice-body">
          <div v-if="hasContent" class="notice-content" v-html="detail.noticeContent" />
          <div v-else class="notice-empty notice-empty--inner">
            <el-icon><Document /></el-icon> 暂无内容
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import {getNotice} from '@/api/system/notice'

const visible = ref(false)
const loading = ref(false)
const detail = ref(null)

const isStatusNormal = computed(() => {
  const status = detail.value && detail.value.status
  return status === '0' || status === 0
})

const hasContent = computed(() => {
  const content = detail.value && detail.value.noticeContent
  return content != null && String(content).trim() !== ''
})

function open(payload) {
  let id = null
  let preset = null
  if (payload != null && typeof payload === 'object') {
    id = payload.noticeId
    if (payload.noticeContent != null) {
      preset = payload
    }
  } else {
    id = payload
  }
  visible.value = true
  if (preset) {
    detail.value = preset
    return
  }
  if (id == null || id === '') {
    detail.value = null
    return
  }
  loading.value = true
  detail.value = null
  getNotice(id).then(res => {
    detail.value = res.data
  }).catch(() => {
    detail.value = null
  }).finally(() => {
    loading.value = false
  })
}

function handleClose() {
  visible.value = false
  detail.value = null
  loading.value = false
}

defineExpose({
  open
})
</script>

<style lang="scss">
/* 详情抽屉全局样式（针对 Teleport 挂载） */
.notice-detail-drawer {
  /* 基础面板设置 */
  .notice-page {
    max-width: 800px;
    margin: 0 auto;
    padding: 16px 20px 40px;
    animation: notice-fade-up 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
  }

  .notice-type-wrap {
    margin-bottom: 16px;
  }

  /* 极光风格类型标签 */
  .notice-type-tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 14px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 700;
    line-height: 1;
    border: 1px solid transparent;
    transition: all 0.3s ease;

    .el-icon {
      font-size: 13px;
    }

    &.type-notify {
      background: linear-gradient(135deg, rgba(245, 158, 11, 0.06), rgba(245, 158, 11, 0.12)) !important;
      color: #d97706 !important;
      border-color: rgba(245, 158, 11, 0.2) !important;
    }

    &.type-announce {
      background: linear-gradient(135deg, rgba(79, 70, 229, 0.06), rgba(6, 182, 212, 0.1)) !important;
      color: #4f46e5 !important;
      border-color: rgba(79, 70, 229, 0.18) !important;
    }
  }

  /* 标题样式 */
  .notice-title {
    font-size: 26px;
    font-weight: 800;
    color: #1e293b;
    line-height: 1.4;
    margin: 0 0 20px;
    letter-spacing: -0.5px;
  }

  /* 元信息卡片化 */
  .notice-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 20px;
    padding: 14px 18px;
    background: rgba(255, 255, 255, 0.45);
    border: 1px solid rgba(226, 232, 240, 0.5);
    border-radius: 12px;
    margin-bottom: 28px;
    backdrop-filter: blur(4px);
  }

  .meta-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #64748b;
    font-weight: 500;

    .el-icon {
      font-size: 14px;
      color: #94a3b8;
    }
  }

  /* 状态小圆点与呼吸动效 */
  .status-dot {
    position: relative;
    display: inline-flex;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    margin-right: 4px;
    
    &::after {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      animation: polaris-dot-pulse 2s infinite ease-in-out;
    }

    &.status-ok {
      background-color: #10b981;
      &::after {
        background-color: #10b981;
      }
    }

    &.status-off {
      background-color: #ef4444;
      &::after {
        background-color: #ef4444;
      }
    }
  }

  /* 现代风格渐变分隔线 */
  .notice-divider {
    height: 1px;
    background: linear-gradient(to right, rgba(226, 232, 240, 0), rgba(226, 232, 240, 0.8), rgba(226, 232, 240, 0));
    margin: 28px 0;
    border: none;
  }

  /* 画报风格内容板：毛玻璃卡片 */
  .notice-body {
    background: rgba(255, 255, 255, 0.45) !important;
    border: 1px solid rgba(255, 255, 255, 0.5) !important;
    border-radius: 16px;
    padding: 32px 40px;
    backdrop-filter: blur(12px);
    box-shadow: 0 8px 32px rgba(31, 38, 135, 0.04) !important;
    min-height: 180px;
  }

  .notice-content {
    font-size: 15px;
    line-height: 1.9;
    color: #334155;
    word-break: break-word;

    p {
      margin: 0 0 1.2em;
    }

    h1, h2, h3 {
      font-weight: 800;
      color: #0f172a;
      margin: 1.6em 0 0.8em;
    }

    h1 { font-size: 20px; }
    h2 { font-size: 18px; }
    h3 { font-size: 16px; }

    a {
      color: #4f46e5;
      text-decoration: none;
      border-bottom: 1px dashed rgba(79, 70, 229, 0.4);
      transition: all 0.2s ease;
      
      &:hover {
        color: #312e81;
        border-bottom-style: solid;
      }
    }

    img {
      max-width: 100%;
      border-radius: 8px;
      margin: 12px 0;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
    }

    ul, ol {
      padding-left: 24px;
      margin: 0 0 1.2em;
    }
    li {
      margin-bottom: 6px;
    }

    blockquote {
      border-left: 4px solid #cbd5e1;
      margin: 1.2em 0;
      padding: 10px 20px;
      color: #475569;
      background: rgba(241, 245, 249, 0.5);
      border-radius: 0 8px 8px 0;
    }

    table {
      border-collapse: collapse;
      width: 100%;
      margin: 1.2em 0;
      font-size: 13.5px;
    }
    
    table th, table td {
      border: 1px solid #e2e8f0;
      padding: 8px 14px;
    }
    
    table th {
      background: rgba(241, 245, 249, 0.6);
      font-weight: 700;
      color: #1e293b;
    }
  }

  .notice-empty {
    text-align: center;
    padding: 60px 0;
    color: #94a3b8;
    font-size: 14px;

    .el-icon {
      font-size: 32px;
      display: inline-flex;
      margin-bottom: 12px;
      color: #cbd5e1;
    }
  }
  .notice-empty--inner {
    padding: 48px 0;
  }

  .notice-detail-drawer__body {
    height: 100%;
    overflow-y: auto;
    padding: 16px 20px 32px;
    
    /* 美化滚动条 */
    &::-webkit-scrollbar {
      width: 6px;
    }
    &::-webkit-scrollbar-thumb {
      background: rgba(0, 0, 0, 0.08);
      border-radius: 4px;
    }
    &::-webkit-scrollbar-track {
      background: transparent;
    }
  }
}

/* 暗色模式样式适配 */
.dark {
  .notice-detail-drawer {
    .notice-title {
      color: #f1f5f9 !important;
    }

    .notice-type-tag {
      &.type-notify {
        background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(245, 158, 11, 0.2)) !important;
        color: #fbbf24 !important;
        border-color: rgba(245, 158, 11, 0.3) !important;
      }

      &.type-announce {
        background: linear-gradient(135deg, rgba(129, 140, 248, 0.15), rgba(56, 189, 248, 0.15)) !important;
        color: #38bdf8 !important;
        border-color: rgba(56, 189, 248, 0.35) !important;
      }
    }

    .notice-meta {
      background: rgba(255, 255, 255, 0.03) !important;
      border-color: rgba(255, 255, 255, 0.05) !important;
    }

    .meta-item {
      color: #cbd5e1 !important;
      .el-icon {
        color: #64748b !important;
      }
    }

    .notice-divider {
      background: linear-gradient(to right, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0));
    }

    .notice-body {
      background: rgba(255, 255, 255, 0.03) !important;
      border-color: rgba(255, 255, 255, 0.05) !important;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2) !important;
    }

    .notice-content {
      color: #cbd5e1 !important;

      h1, h2, h3 {
        color: #f1f5f9 !important;
      }

      a {
        color: #818cf8 !important;
        border-bottom-color: rgba(129, 140, 248, 0.4) !important;
        &:hover {
          color: #c7d2fe !important;
        }
      }

      blockquote {
        border-left-color: #475569 !important;
        color: #94a3b8 !important;
        background: rgba(30, 41, 59, 0.4) !important;
      }

      table th, table td {
        border-color: #334155 !important;
      }
      
      table th {
        background: rgba(30, 41, 59, 0.5) !important;
        color: #f1f5f9 !important;
      }
    }

    .notice-detail-drawer__body::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.08);
    }
  }
}

@keyframes notice-fade-up {
  from { opacity: 0; transform: translateY(20px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes polaris-dot-pulse {
  0% {
    transform: scale(1);
    opacity: 0.8;
  }
  100% {
    transform: scale(3);
    opacity: 0;
  }
}
</style>
