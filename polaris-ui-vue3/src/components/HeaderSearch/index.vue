<template>
  <div class="header-search">
    <div class="command-search-trigger hide-mobile" @click.stop="click">
      <span class="search-icon">🔍</span>
      <span class="search-text">搜索指令...</span>
      <kbd class="kbd-badge">⌘K</kbd>
    </div>
    <el-dialog
      v-model="show"
      width="580px"
      @close="close"
      @opened="onDialogOpened"
      :show-close="false"
      append-to-body
      class="modern-glass-dialog command-menu-dialog"
    >
      <el-input
        v-model="search"
        ref="headerSearchSelectRef"
        size="large"
        @input="querySearch"
        prefix-icon="Search"
        placeholder="菜单搜索，支持标题、URL模糊查询"
        clearable
        @keyup.enter="selectActiveResult"
        @keydown.up.prevent="navigateResult('up')"
        @keydown.down.prevent="navigateResult('down')"
      >
      </el-input>

      <div class="result-count" v-if="search && options.length > 0">
        找到 <strong>{{ options.length }}</strong> 个结果
      </div>

      <div class="result-wrap">
        <el-scrollbar>

          <template v-if="options.length > 0">
            <div
              class="search-item"
              tabindex="1"
              v-for="(item, index) in options"
              :key="item.path"
              :class="{ 'is-active': index === activeIndex }"
              :style="activeStyle(index)"
              @mouseenter="activeIndex = index"
              @mouseleave="activeIndex = -1"
            >
              <div class="left">
                <svg-icon class="menu-icon" :icon-class="item.icon" />
              </div>
              <div class="search-info" @click="change(item)">
                <div class="menu-title" v-html="highlightText(item.title.join(' / '))"></div>
                <div class="menu-path" v-html="highlightText(item.path)"></div>
              </div>
              <svg-icon icon-class="enter" class="enter-icon" v-show="index === activeIndex" />
            </div>
          </template>

          <div class="empty-state" v-else-if="search && options.length === 0">
            <el-icon class="empty-icon"><Search /></el-icon>
            <p class="empty-text">未找到 "<strong>{{ search }}</strong>" 相关菜单</p>
            <p class="empty-tip">试试其他关键词或路径</p>
          </div>

        </el-scrollbar>
      </div>

      <div class="search-footer">
        <span class="shortcut-item">
          <kbd>↑</kbd><kbd>↓</kbd> 切换
        </span>
        <span class="shortcut-item">
          <kbd>↵</kbd> 选择
        </span>
        <span class="shortcut-item">
          <kbd>Esc</kbd> 关闭
        </span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import Fuse from 'fuse.js'
import {getNormalPath} from '@/utils/ruoyi'
import {isHttp} from '@/utils/validate'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const search = ref('')
const options = ref([])
const searchPool = ref([])
const activeIndex = ref(-1)
const show = ref(false)
const fuse = ref(undefined)
const headerSearchSelectRef = ref(null)
const router = useRouter()
const theme = computed(() => useSettingsStore().theme)
const routes = computed(() => usePermissionStore().defaultRoutes)

function click() {
  show.value = !show.value
  if (show.value) {
    options.value = searchPool.value
  }
}

function onDialogOpened() {
  nextTick(() => {
    headerSearchSelectRef.value && headerSearchSelectRef.value.focus()
  })
}

function close() {
  headerSearchSelectRef.value && headerSearchSelectRef.value.blur()
  search.value = ''
  options.value = searchPool.value
  show.value = false
  activeIndex.value = -1
}

function change(val) {
  const p = val.path
  const query = val.query
  if (isHttp(p)) {
    // http(s):// 路径新窗口打开
    const pindex = p.indexOf("http")
    window.open(p.substr(pindex, p.length), "_blank")
  } else {
    if (query) {
      router.push({ path: p, query: JSON.parse(query) })
    } else {
      router.push(p)
    }
  }
  search.value = ''
  options.value = searchPool.value
  nextTick(() => {
    show.value = false
  })
}

function initFuse(list) {
  fuse.value = new Fuse(list, {
    shouldSort: true,
    threshold: 0.2,
    distance: 100,
    minMatchCharLength: 1,
    keys: [{
      name: 'title',
      weight: 0.7
    }, {
      name: 'path',
      weight: 0.3
    }]
  })
}

function generateRoutes(routes, basePath = '', prefixTitle = []) {
  let res = []
  for (const r of routes) {
    if (r.hidden) { continue }
    const p = r.path.length > 0 && r.path[0] === '/' ? r.path : '/' + r.path
    const data = {
      path: !isHttp(r.path) ? getNormalPath(basePath + p) : r.path,
      title: [...prefixTitle],
      icon: ''
    }
    if (r.meta && r.meta.title) {
      data.title = [...data.title, r.meta.title]
      data.icon = r.meta.icon
      if (r.redirect !== "noRedirect") {
        res.push(data)
      }
    }
    if (r.query) {
      data.query = r.query
    }
    if (r.children) {
      const tempRoutes = generateRoutes(r.children, data.path, data.title)
      if (tempRoutes.length >= 1) {
        res = [...res, ...tempRoutes]
      }
    }
  }
  return res
}

function querySearch(query) {
  activeIndex.value = -1
  if (query !== '') {
    const q = query.toLowerCase()
    const pathMatches = searchPool.value.filter(item =>
      item.path.toLowerCase().includes(q)
    )
    const fuseMatches = fuse.value.search(query).map(item => item.item)
    const merged = [...pathMatches]
    fuseMatches.forEach(item => {
      if (!merged.find(m => m.path === item.path)) {
        merged.push(item)
      }
    })
    options.value = merged
  } else {
    options.value = searchPool.value
  }
}

function activeStyle(index) {
  return {}
}

function navigateResult(direction) {
  if (direction === "up") {
    activeIndex.value = activeIndex.value <= 0 ? options.value.length - 1 : activeIndex.value - 1
  } else if (direction === "down") {
    activeIndex.value = activeIndex.value >= options.value.length - 1 ? 0 : activeIndex.value + 1
  }
}

function selectActiveResult() {
  if (options.value.length > 0 && activeIndex.value >= 0) {
    change(options.value[activeIndex.value])
  }
}

function highlightText(text) {
  if (!text) return ''
  if (!search.value) return text
  const keyword = escapeRegExp(search.value)
  const reg = new RegExp(`(${keyword})`, 'gi')
  return text.replace(reg, '<span class="highlight">$1</span>')
}

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// 键盘监听
function handleKeyDown(e) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    click()
  }
}

onMounted(() => {
  searchPool.value = generateRoutes(routes.value)
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})

watch(searchPool, (list) => {
  initFuse(list)
})
</script>

<style lang='scss' scoped>
:deep(.el-dialog__header) {
  padding: 6px !important;
}

.header-search {
  display: flex;
  align-items: center;
}

.command-search-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
  user-select: none;
  
  html:not(.dark) & {
    background-color: rgba(0, 0, 0, 0.02);
    border-color: rgba(0, 0, 0, 0.05);
    .search-text { color: #64748b; }
    .kbd-badge { background-color: rgba(0, 0, 0, 0.05); color: #64748b; }
    &:hover { background-color: rgba(0, 0, 0, 0.04); }
  }
  
  .dark & {
    background-color: rgba(255, 255, 255, 0.02);
    border-color: rgba(255, 255, 255, 0.04);
    .search-text { color: #94a3b8; }
    .kbd-badge { background-color: rgba(255, 255, 255, 0.05); color: #94a3b8; }
    &:hover { background-color: rgba(255, 255, 255, 0.04); }
  }
  
  .search-icon {
    font-size: 11px;
    line-height: 1;
  }
  
  .search-text {
    font-size: 11.5px;
    font-weight: 600;
  }
  
  .kbd-badge {
    font-size: 9px;
    padding: 1px 4px;
    border-radius: 4px;
    font-family: monospace;
    font-weight: bold;
  }
}

@media (max-width: 768px) {
  .hide-mobile {
    display: none !important;
  }
}
</style>

<style lang="scss">
/* 全局覆盖 body 下挂载的命令行式搜索弹窗 */
.command-menu-dialog {
  .el-dialog {
    background: rgba(15, 23, 42, 0.65) !important;
    backdrop-filter: blur(40px) !important;
    border: 1px solid rgba(255, 255, 255, 0.06) !important;
    border-radius: 20px !important;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.45) !important;
    overflow: hidden;
    padding: 20px !important;
    
    html:not(.dark) & {
      background: rgba(255, 255, 255, 0.75) !important;
      border-color: rgba(79, 70, 229, 0.15) !important;
      box-shadow: 0 15px 40px rgba(0, 0, 0, 0.08) !important;
    }
  }

  .el-dialog__header {
    display: none !important;
  }

  .el-dialog__body {
    padding: 0 !important;
  }

  .el-input__wrapper {
    border-radius: 12px !important;
    height: 44px;
    background: rgba(0, 0, 0, 0.2) !important;
    border: 1px solid rgba(255, 255, 255, 0.05) !important;
    box-shadow: none !important;
    transition: all 0.3s;
    
    html:not(.dark) & {
      background: #ffffff !important;
      border-color: rgba(0, 0, 0, 0.08) !important;
    }
    
    &:hover, &.is-focus {
      html:not(.dark) & { border-color: rgba(79, 70, 229, 0.4) !important; }
      .dark & { border-color: rgba(56, 189, 248, 0.4) !important; }
    }
  }

  .el-input__inner {
    font-size: 13px;
    html:not(.dark) & { color: #0f172a !important; }
    .dark & { color: #f8fafc !important; }
  }

  .highlight {
    font-weight: 700;
    
    html:not(.dark) & {
      color: #4f46e5 !important;
    }
    .dark & {
      color: #38bdf8 !important;
    }
  }

  .result-count {
    padding: 12px 6px 4px;
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.05em;
    
    html:not(.dark) & { color: #64748b; }
    .dark & { color: #94a3b8; }
    
    strong {
      html:not(.dark) & { color: #4f46e5; }
      .dark & { color: #38bdf8; }
    }
  }

  .result-wrap {
    height: 320px;
    margin: 10px 0;
    
    :deep(.el-scrollbar__wrap) {
      overflow-x: hidden !important;
    }
    
    :deep(.el-scrollbar__view) {
      padding: 0 4px;
    }

    :deep(.el-scrollbar__bar.is-horizontal) {
      display: none !important;
    }
    
    .search-item {
      display: flex;
      align-items: center;
      padding: 10px 24px 10px 16px;
      margin: 0 auto 6px;
      border-radius: 12px;
      cursor: pointer;
      border: 1px solid transparent;
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      width: calc(100% - 16px);
      box-sizing: border-box;
      
      html:not(.dark) & {
        background: rgba(0, 0, 0, 0.01);
        border-color: rgba(0, 0, 0, 0.02);
      }
      
      .dark & {
        background: rgba(255, 255, 255, 0.01);
        border-color: rgba(255, 255, 255, 0.02);
      }

      .left {
        width: 32px;
        height: 32px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-right: 12px;
        flex-shrink: 0;
        transition: background 0.2s;
        
        html:not(.dark) & {
          background: rgba(79, 70, 229, 0.08);
          color: #4f46e5;
        }
        .dark & {
          background: rgba(56, 189, 248, 0.1);
          color: #38bdf8;
        }
        
        .menu-icon {
          width: 14px;
          height: 14px;
        }
      }

      .search-info {
        display: flex;
        flex-direction: column;
        flex: 1;
        overflow: hidden;
        margin-top: 0px !important;
        padding-left: 0px !important;
        
        .menu-title {
          font-size: 12.5px;
          font-weight: 600;
          line-height: 1.4;
          
          html:not(.dark) & { color: #334155; }
          .dark & { color: #cbd5e1; }
        }
        
        .menu-path {
          font-size: 10px;
          margin-top: 2px;
          
          html:not(.dark) & { color: #94a3b8; }
          .dark & { color: #64748b; }
        }
      }

      /* 选中/激活态样式 */
      &.is-active {
        transform: scale(1.01) translateX(3px);
        
        html:not(.dark) & {
          background: rgba(79, 70, 229, 0.06) !important;
          border-color: rgba(79, 70, 229, 0.25) !important;
          
          .search-info .menu-title {
            color: #4f46e5 !important;
          }
        }
        
        .dark & {
          background: rgba(56, 189, 248, 0.08) !important;
          border-color: rgba(56, 189, 248, 0.3) !important;
          
          .search-info .menu-title {
            color: #38bdf8 !important;
          }
        }
      }
      
      .enter-icon {
        width: 14px;
        height: 14px;
        opacity: 0.8;
        margin-left: auto;
        
        html:not(.dark) & { color: #4f46e5; }
        .dark & { color: #38bdf8; }
      }
    }
  }

  .search-footer {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 12px 6px 0;
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    font-size: 11px;
    font-weight: 600;
    
    html:not(.dark) & {
      border-top-color: rgba(0, 0, 0, 0.05);
      color: #64748b;
    }
    
    .dark & {
      color: #94a3b8;
    }

    .shortcut-item {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    kbd {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 18px;
      height: 18px;
      padding: 0 4px;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.05);
      font-size: 10px;
      font-family: inherit;
      line-height: 1;
      
      html:not(.dark) & {
        background: #ffffff;
        border-color: rgba(0, 0, 0, 0.1);
        box-shadow: 0 1px 0 rgba(0, 0, 0, 0.05);
        color: #64748b;
      }
      
      .dark & {
        color: #94a3b8;
      }
    }
  }
}
</style>
