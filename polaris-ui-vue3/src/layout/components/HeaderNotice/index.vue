<template>
  <div>
    <el-popover ref="noticePopover" placement="bottom-end" :width="320" trigger="manual" v-model:visible="noticeVisible" popper-class="notice-popover">
      <!-- 弹出内容 -->
      <div class="notice-header">
        <span class="notice-title">通知公告</span>
        <span class="notice-mark-all" @click="markAllRead">全部已读</span>
      </div>
      <div v-if="noticeLoading" class="notice-loading">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
      <div v-else-if="noticeList.length === 0" class="notice-empty">
        <el-icon style="font-size:24px;display:block;margin-bottom:6px;"><Postcard /></el-icon>
        暂无公告
      </div>
      <div v-else>
        <div v-for="item in noticeList" :key="item.noticeId" class="notice-item" :class="{ 'is-read': item.isRead }" @click="previewNotice(item)">
          <el-tag size="small" :type="item.noticeType === '1' ? 'warning' : 'success'" class="notice-tag">
            {{ item.noticeType === '1' ? '通知' : '公告' }}
          </el-tag>
          <span class="notice-item-title">{{ item.noticeTitle }}</span>
          <span class="notice-item-date">{{ item.createTime }}</span>
        </div>
      </div>

      <!-- 触发器 -->
      <template #reference>
        <div class="right-menu-item hover-effect notice-trigger" @mouseenter="onNoticeEnter" @mouseleave="onNoticeLeave">
          <svg-icon icon-class="bell" />
          <span v-if="unreadCount > 0" class="notice-badge">{{ unreadCount }}</span>
        </div>
      </template>
    </el-popover>

    <!-- 预览弹窗 -->
    <notice-detail-view ref="noticeViewRef" />
  </div>
</template>

<script setup>
import NoticeDetailView from './DetailView'
import {listNoticeTop, markNoticeRead, markNoticeReadAll} from '@/api/system/notice'

const noticePopover = ref(null)
const noticeList = ref([])
const unreadCount = ref(0)
const noticeLoading = ref(false)
const noticeVisible = ref(false)
const noticeLeaveTimer = ref(null)
const { proxy } = getCurrentInstance()

// 加载顶部公告列表
function loadNoticeTop() {
  noticeLoading.value = true
  listNoticeTop().then(res => {
    noticeList.value = res.data || []
    unreadCount.value = res.unreadCount !== undefined ? res.unreadCount : noticeList.value.filter(n => !n.isRead).length
  }).finally(() => {
    noticeLoading.value = false
  })
}

onMounted(() => loadNoticeTop())

// 鼠标移入铃铛区域
function onNoticeEnter() {
  clearTimeout(noticeLeaveTimer.value)
  noticeVisible.value = true
  nextTick(() => {
    const popper = noticePopover.value?.popperRef?.contentRef
    if (popper && !popper._noticeBound) {
      popper._noticeBound = true
      popper.addEventListener('mouseenter', () => clearTimeout(noticeLeaveTimer.value))
      popper.addEventListener('mouseleave', () => {
        noticeLeaveTimer.value = setTimeout(() => { noticeVisible.value = false }, 100)
      })
    }
  })
}

// 鼠标离开铃铛区域
function onNoticeLeave() {
  noticeLeaveTimer.value = setTimeout(() => { noticeVisible.value = false }, 150)
}

// 预览公告详情
function previewNotice(item) {
  if (!item.isRead) {
    markNoticeRead(item.noticeId).catch(() => {})
    const idx = noticeList.value.indexOf(item)
    if (idx !== -1) noticeList.value[idx] = { ...item, isRead: true }
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  proxy.$refs["noticeViewRef"].open(item.noticeId)
}

// 全部已读
function markAllRead() {
  const ids = noticeList.value.map(n => n.noticeId).join(',')
  if (!ids) return
  markNoticeReadAll(ids).catch(() => {})
  noticeList.value = noticeList.value.map(n => ({ ...n, isRead: true }))
  unreadCount.value = 0
}
</script>

<style lang="scss" scoped>
.notice-trigger {
  position: relative;
  transform: translateX(-6px);
  .svg-icon { width: 1.2em; height: 1.2em; vertical-align: -0.2em; }
  .notice-badge {
    position: absolute;
    top: 7px;
    right: -3px;
    background: #f56c6c;
    color: #fff;
    border-radius: 10px;
    font-size: 10px;
    height: 16px;
    line-height: 16px;
    padding: 0 4px;
    min-width: 16px;
    text-align: center;
    white-space: nowrap;
    pointer-events: none;
  }
}
</style>

<style lang="scss">
/* 全局覆盖消息通知 Popover 样式，实现高级毛玻璃与亮暗色兼容 */
.el-popover.notice-popover {
  background: rgba(15, 23, 42, 0.65) !important;
  backdrop-filter: blur(40px) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  border-radius: 16px !important;
  box-shadow: 0 15px 45px rgba(0, 0, 0, 0.35) !important;
  padding: 0 !important;
  overflow: hidden;
  
  html:not(.dark) & {
    background: rgba(255, 255, 255, 0.75) !important;
    border-color: rgba(79, 70, 229, 0.15) !important;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08) !important;
  }

  /* 头部区域 */
  .notice-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: transparent !important;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    
    html:not(.dark) & {
      border-bottom-color: rgba(0, 0, 0, 0.05);
    }
    
    .notice-title {
      font-size: 13px;
      font-weight: 700;
      
      html:not(.dark) & { color: #1e293b; }
      .dark & { color: #f8fafc; }
    }
    
    .notice-mark-all {
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      transition: opacity 0.2s;
      
      html:not(.dark) & {
        color: #4f46e5;
      }
      .dark & {
        color: #38bdf8;
      }
      
      &:hover {
        opacity: 0.8;
      }
    }
  }

  /* 列表项 */
  .notice-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.04);
    cursor: pointer;
    background: transparent;
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    box-sizing: border-box;
    
    html:not(.dark) & {
      border-bottom-color: rgba(0, 0, 0, 0.03);
    }
    
    &:last-child {
      border-bottom: none;
    }
    
    &:hover {
      transform: translateX(2px);
      
      html:not(.dark) & {
        background: rgba(79, 70, 229, 0.04) !important;
      }
      .dark & {
        background: rgba(56, 189, 248, 0.05) !important;
      }
    }
    
    .notice-item-title {
      flex: 1;
      font-size: 12px;
      font-weight: 600;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      
      html:not(.dark) & { color: #334155; }
      .dark & { color: #cbd5e1; }
    }
    
    .notice-item-date {
      flex-shrink: 0;
      font-size: 10.5px;
      
      html:not(.dark) & { color: #64748b; }
      .dark & { color: #94a3b8; }
    }

    /* 已读状态微调 */
    &.is-read {
      opacity: 0.5;
      
      .notice-item-title, .notice-item-date {
        color: #999 !important;
      }
    }
  }

  /* 空状态与加载中 */
  .notice-loading,
  .notice-empty {
    padding: 30px 24px;
    text-align: center;
    font-size: 12px;
    font-weight: 600;
    
    html:not(.dark) & { color: #64748b; }
    .dark & { color: #94a3b8; }
    
    .el-icon {
      font-size: 26px;
      display: block;
      margin: 0 auto 8px;
      opacity: 0.7;
    }
  }
}
</style>
