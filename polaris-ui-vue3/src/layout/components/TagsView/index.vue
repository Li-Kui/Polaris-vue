<template>
  <div id="tags-view-container" class="tags-view-container" :class="{ 'tags-view-container--chrome': tagsViewStyle === 'chrome' }">
    <!-- 左切换箭头 -->
    <span class="tags-nav-btn tags-nav-btn--left" :class="{ disabled: !canScrollLeft }" @click="scrollLeft">
      <el-icon><arrow-left /></el-icon>
    </span>

    <!-- 标签滚动区 -->
    <scroll-pane ref="scrollPaneRef" class="tags-view-wrapper" @scroll="handleScroll" @update-arrows="updateArrowState">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :data-path="tag.path"
        :class="{ 'active': isActive(tag), 'has-icon': tagsIcon }"
        :to="{ path: tag.path, query: tag.query, fullPath: tag.fullPath }"
        class="tags-view-item"
        :style="tagActiveStyle(tag)"
        @click.middle="!isAffix(tag) ? closeSelectedTag(tag) : ''"
        @contextmenu.prevent="openMenu(tag, $event)"
      >
        <span v-if="isActive(tag)" class="tag-dot"></span>
        <svg-icon v-if="tagsIcon && tag.meta && tag.meta.icon && tag.meta.icon !== '#'" :icon-class="tag.meta.icon" style="margin-right: 3px;" />
        <span class="tag-title">{{ tag.title }}</span>
        <span v-if="!isAffix(tag)" @click.prevent.stop="closeSelectedTag(tag)" class="tags-close-btn">
          <close class="el-icon-close" />
        </span>
      </router-link>
    </scroll-pane>

    <!-- 右切换箭头 -->
    <span class="tags-nav-btn tags-nav-btn--right" :class="{ disabled: !canScrollRight }" @click="scrollRight">
      <el-icon><arrow-right /></el-icon>
    </span>

    <!-- 下拉操作菜单 -->
    <el-dropdown class="tags-action-dropdown" trigger="click" placement="bottom-end" @command="handleDropdownCommand">
      <span class="tags-action-btn">
        <el-icon><arrow-down /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu class="tags-dropdown-menu">
          <el-dropdown-item v-if="!isAffix(selectedDropdownTag)" command="close"><close style="width: 1em; height: 1em;" />关闭当前</el-dropdown-item>
          <el-dropdown-item command="closeOthers"><circle-close style="width: 1em; height: 1em;" />关闭其他</el-dropdown-item>
          <el-dropdown-item command="closeLeft" :disabled="isFirstView()"><back style="width: 1em; height: 1em;" />关闭左侧</el-dropdown-item>
          <el-dropdown-item command="closeRight" :disabled="isLastView()"><right style="width: 1em; height: 1em;" />关闭右侧</el-dropdown-item>
          <el-dropdown-item command="closeAll"><circle-close style="width: 1em; height: 1em;" />全部关闭</el-dropdown-item>
          <el-dropdown-item command="fullscreen" divided>
            <template v-if="!isFullscreen"><full-screen style="width: 1em; height: 1em;" />全屏显示</template>
            <template v-else><close style="width: 1em; height: 1em;" />退出全屏</template>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- 刷新按钮 -->
    <span class="tags-action-btn tags-refresh-btn" title="刷新页面" @click="refreshSelectedTag(selectedDropdownTag)">
      <el-icon><refresh-right/></el-icon> 刷新
    </span>

    <!-- 右键上下文菜单 -->
    <ul v-show="visible" :style="{ left: left + 'px', top: top + 'px' }" class="contextmenu">
      <li @click="refreshSelectedTag(selectedTag)"><refresh-right style="width: 1em; height: 1em;" />刷新页面</li>
      <li v-if="!isAffix(selectedTag)" @click="closeSelectedTag(selectedTag)"><close style="width: 1em; height: 1em;" />关闭当前</li>
      <li @click="closeOthersTags"><circle-close style="width: 1em; height: 1em;" />关闭其他</li>
      <li v-if="!isFirstView()" @click="closeLeftTags"><back style="width: 1em; height: 1em;" />关闭左侧</li>
      <li v-if="!isLastView()" @click="closeRightTags"><right style="width: 1em; height: 1em;" />关闭右侧</li>
      <li @click="closeAllTags(selectedTag)"><circle-close style="width: 1em; height: 1em;" />全部关闭</li>
    </ul>
  </div>
</template>

<script setup>
import ScrollPane from './ScrollPane'
import {getNormalPath} from '@/utils/ruoyi'
import useTagsViewStore from '@/store/modules/tagsView'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const visible = ref(false)
const top = ref(0)
const left = ref(0)
const selectedTag = ref({})
const affixTags = ref([])
const scrollPaneRef = ref(null)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
const isFullscreen = ref(false)
const hiddenElements = ref([])

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()
const settingsStore = useSettingsStore()

const visitedViews = computed(() => useTagsViewStore().visitedViews)
const routes = computed(() => usePermissionStore().routes)
const theme = computed(() => useSettingsStore().theme)
const tagsIcon = computed(() => useSettingsStore().tagsIcon)
const tagsViewPersist = computed(() => useSettingsStore().tagsViewPersist)
const tagsViewStyle = computed(() => useSettingsStore().tagsViewStyle)

// 下拉菜单针对当前激活的 tag
const selectedDropdownTag = computed(() => visitedViews.value.find(v => isActive(v)) || {})

watch(route, () => {
  addTags()
  moveToCurrentTag()
})

watch(visible, (value) => {
  if (value) {
    document.body.addEventListener('click', closeMenu)
  } else {
    document.body.removeEventListener('click', closeMenu)
  }
})

watch(visitedViews, () => {
  nextTick(() => updateArrowState())
})

onMounted(() => {
  initTags()
  addTags()
  window.addEventListener('resize', updateArrowState)
  window.addEventListener('keydown', handleKeyDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateArrowState)
  window.removeEventListener('keydown', handleKeyDown)
})

function handleKeyDown(event) {
  // 当按下Esc键且处于全屏状态时，退出全屏
  if (event.key === 'Escape' && isFullscreen.value) {
    toggleFullscreen()
  }
}

function isActive(r) {
  return r.path === route.path
}

function tagActiveStyle(tag) {
  if (!isActive(tag) || tagsViewStyle.value !== 'card') return {}
  return {
    'background-color': theme.value,
    'border-color': theme.value
  }
}

function isAffix(tag) {
  return tag && tag.meta && tag.meta.affix
}

function isFirstView() {
  try {
    const tag = selectedTag.value && selectedTag.value.fullPath ? selectedTag.value : selectedDropdownTag.value
    return tag.fullPath === '/index' || tag.fullPath === visitedViews.value[1].fullPath
  } catch (err) {
    return false
  }
}

function isLastView() {
  try {
    const tag = selectedTag.value && selectedTag.value.fullPath ? selectedTag.value : selectedDropdownTag.value
    return tag.fullPath === visitedViews.value[visitedViews.value.length - 1].fullPath
  } catch (err) {
    return false
  }
}

function filterAffixTags(routes, basePath = '') {
  let tags = []
  routes.forEach(route => {
    if (route.meta && route.meta.affix) {
      const tagPath = getNormalPath(basePath + '/' + route.path)
      tags.push({
        fullPath: tagPath,
        path: tagPath,
        name: route.name,
        meta: { ...route.meta }
      })
    }
    if (route.children) {
      const tempTags = filterAffixTags(route.children, route.path)
      if (tempTags.length >= 1) {
        tags = [...tags, ...tempTags]
      }
    }
  })
  return tags
}

function initTags() {
  if (tagsViewPersist.value) {
    useTagsViewStore().loadPersistedViews()
  }
  const res = filterAffixTags(routes.value)
  affixTags.value = res
  for (const tag of res) {
    if (tag.name) {
      useTagsViewStore().addAffixView(tag)
    }
  }
}

function addTags() {
  const { name } = route
  if (name) {
    useTagsViewStore().addView(route)
  }
}

function moveToCurrentTag() {
  nextTick(() => {
    for (const r of visitedViews.value) {
      if (r.path === route.path) {
        scrollPaneRef.value.moveToTarget(r)
        if (r.fullPath !== route.fullPath) {
          useTagsViewStore().updateVisitedView(route)
        }
      }
    }
  })
}

function scrollLeft() {
  if (!canScrollLeft.value) return
  scrollPaneRef.value.scrollToStart()
}

function scrollRight() {
  if (!canScrollRight.value) return
  scrollPaneRef.value.scrollToEnd()
}

function updateArrowState() {
  nextTick(() => {
    if (scrollPaneRef.value) {
      const state = scrollPaneRef.value.getScrollState()
      canScrollLeft.value = state.canLeft
      canScrollRight.value = state.canRight
    }
  })
}

function toggleFullscreen() {
  const mainContainer = document.querySelector('.main-container')
  const navbar = document.querySelector('.navbar')
  const sidebar = document.querySelector('.sidebar-container')
  if (!mainContainer) return

  if (!isFullscreen.value) {
    mainContainer.classList.add('fullscreen-mode')
    document.body.style.overflow = 'hidden'
    const elementsToHide = [{ el: navbar, originalDisplay: navbar?.style.display || '' }, { el: sidebar, originalDisplay: sidebar?.style.display || '' }]
    elementsToHide.forEach(item => {
      if (item.el && item.el.style.display !== 'none') {
        item.originalDisplay = item.el.style.display
        item.el.style.display = 'none'
        hiddenElements.value.push(item)
      }
    })
    isFullscreen.value = true
  } else {
    mainContainer.classList.remove('fullscreen-mode')
    document.body.style.overflow = ''
    hiddenElements.value.forEach(item => {
      if (item.el) {
        item.el.style.display = item.originalDisplay
      }
    })
    hiddenElements.value = []
    document.querySelector('.tags-action-btn').blur()
    isFullscreen.value = false
  }
}

function handleDropdownCommand(command) {
  const tag = selectedDropdownTag.value
  selectedTag.value = tag
  switch (command) {
    case 'refresh':     refreshSelectedTag(tag); break
    case 'fullscreen':  toggleFullscreen(); break
    case 'close':       closeSelectedTag(tag); break
    case 'closeOthers': closeOthersTags(); break
    case 'closeLeft':   closeLeftTags(); break
    case 'closeRight':  closeRightTags(); break
    case 'closeAll':    closeAllTags(tag); break
  }
}

function refreshSelectedTag(view) {
  proxy.$tab.refreshPage(view)
  if (route.meta.link) {
    useTagsViewStore().delIframeView(route)
  }
}

function closeSelectedTag(view) {
  proxy.$tab.closePage(view).then(({ visitedViews }) => {
    if (isActive(view)) {
      toLastView(visitedViews, view)
    }
  })
}

function closeRightTags() {
  proxy.$tab.closeRightPage(selectedTag.value).then(visitedViews => {
    if (!visitedViews.find(i => i.fullPath === route.fullPath)) {
      toLastView(visitedViews)
    }
  })
}

function closeLeftTags() {
  proxy.$tab.closeLeftPage(selectedTag.value).then(visitedViews => {
    if (!visitedViews.find(i => i.fullPath === route.fullPath)) {
      toLastView(visitedViews)
    }
  })
}

function closeOthersTags() {
  router.push(selectedTag.value).catch(() => { })
  proxy.$tab.closeOtherPage(selectedTag.value).then(() => {
    moveToCurrentTag()
  })
}

function closeAllTags(view) {
  proxy.$tab.closeAllPage().then(({ visitedViews }) => {
    if (affixTags.value.some(tag => tag.path === route.path)) {
      return
    }
    toLastView(visitedViews, view)
  })
}

function toLastView(visitedViews, view) {
  const latestView = visitedViews.slice(-1)[0]
  if (latestView) {
    router.push(latestView.fullPath)
  } else {
    if (view && view.name === 'Dashboard') {
      router.replace({ path: '/redirect' + view.fullPath })
    } else {
      router.push('/')
    }
  }
}

function openMenu(tag, e) {
  left.value = e.clientX
  top.value = e.clientY
  visible.value = true
  selectedTag.value = tag
}

function closeMenu() {
  visible.value = false
}

function handleScroll() {
  closeMenu()
  updateArrowState()
}
</script>

<style lang="scss" scoped>
$tags-bar-height: 38px;

.tags-view-container {
  height: $tags-bar-height;
  width: 100%;
  display: flex;
  align-items: center;
  overflow: hidden;
  padding: 0 24px;
  box-sizing: border-box;
  backdrop-filter: blur(20px);
  border-bottom: 1px solid transparent;
  transition: all 0.3s;

  .theme-light & {
    background-color: rgba(255, 255, 255, 0.2);
    border-bottom-color: rgba(0, 0, 0, 0.03);
  }
  .theme-dark & {
    background-color: rgba(15, 23, 42, 0.1);
    border-bottom-color: rgba(255, 255, 255, 0.02);
  }

  $btn-width: 24px;
  $btn-color: #64748b;
  $divider: 1px solid transparent;

  .tags-nav-btn {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: $btn-width;
    height: 26px;
    cursor: pointer;
    color: $btn-color;
    font-size: 11px;
    user-select: none;
    border-radius: 6px;
    transition: all 0.2s;

    &:hover:not(.disabled) {
      .theme-light & { background: rgba(0, 0, 0, 0.03); color: #334155; }
      .theme-dark & { background: rgba(255, 255, 255, 0.04); color: #f8fafc; }
    }

    &.disabled {
      opacity: 0.3;
      cursor: not-allowed;
    }
  }

  .tags-view-wrapper {
    flex: 1;
    min-width: 0;
    height: 100%;
    display: flex;
    align-items: center;

    :deep(.el-scrollbar__wrap) {
      display: flex;
      align-items: center;
    }

    .tags-view-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      position: relative;
      cursor: pointer;
      height: 25px;
      padding: 0 12px;
      border-radius: 99px;
      border: 1px solid transparent;
      text-decoration: none;
      vertical-align: middle;
      transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
      margin-left: 6px;

      .tag-title {
        font-size: 11px;
        font-weight: 600;
        transition: color 0.2s;
        
        .theme-light & { color: #64748b; }
        .theme-dark & { color: #94a3b8; }
      }

      .tag-dot {
        width: 4px;
        height: 4px;
        border-radius: 50%;
        animation: activeTagDotPulse 1.6s infinite alternate ease-in-out;
        
        .theme-light & {
          background-color: #4f46e5;
          box-shadow: 0 0 4px rgba(79, 70, 229, 0.4);
        }
        .theme-dark & {
          background-color: #38bdf8;
          box-shadow: 0 0 6px rgba(56, 189, 248, 0.8);
        }
      }

      &:first-of-type { margin-left: 6px; }
      &:last-of-type  { margin-right: 15px; }

      .theme-light & {
        background-color: transparent;
        border-color: transparent;
        &:hover {
          background-color: rgba(0, 0, 0, 0.02);
          border-color: rgba(0, 0, 0, 0.04);
        }
      }
      
      .theme-dark & {
        background-color: transparent;
        border-color: transparent;
        &:hover {
          background-color: rgba(255, 255, 255, 0.02);
          border-color: rgba(255, 255, 255, 0.04);
        }
      }

      &.active {
        .theme-light & {
          background-color: #ffffff;
          border-color: rgba(0, 0, 0, 0.06);
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
          .tag-title { color: #4f46e5; }
        }
        .theme-dark & {
          background-color: rgba(255, 255, 255, 0.05);
          border-color: rgba(255, 255, 255, 0.06);
          .tag-title { color: #38bdf8; }
        }
      }
    }
  }

  .tags-action-dropdown {
    flex-shrink: 0;
    display: flex;
    align-items: center;
  }

  .tags-action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: $btn-width;
    height: 26px;
    cursor: pointer;
    color: $btn-color;
    font-size: 11px;
    user-select: none;
    border-radius: 6px;
    transition: all 0.2s;

    &:hover {
      .theme-light & { background: rgba(0, 0, 0, 0.03); color: #334155; }
      .theme-dark & { background: rgba(255, 255, 255, 0.04); color: #f8fafc; }
    }
  }

  .tags-refresh-btn {
    width: auto;
    padding: 0 8px;
    font-size: 11px;
    font-weight: 700;
    gap: 4px;
    height: 26px;
    margin-left: 6px;
    border-radius: 6px;
    display: inline-flex;
    align-items: center;
    color: $btn-color;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      .theme-light & { background: rgba(0, 0, 0, 0.03); color: #334155; }
      .theme-dark & { background: rgba(255, 255, 255, 0.04); color: #f8fafc; }
    }
  }

  .contextmenu {
    margin: 0;
    z-index: 3000;
    position: fixed;
    list-style-type: none;
    padding: 6px 0;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 600;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15) !important;
    backdrop-filter: blur(30px) !important;
    border: 1px solid transparent;

    .theme-light & {
      background: rgba(255, 255, 255, 0.95);
      border-color: rgba(0, 0, 0, 0.05);
      color: #475569;
    }

    .theme-dark & {
      background: rgba(20, 25, 40, 0.95);
      border-color: rgba(255, 255, 255, 0.08);
      color: #cbd5e1;
    }

    li {
      margin: 0 4px;
      padding: 8px 16px;
      cursor: pointer;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s;

      &:hover {
        .theme-light & {
          background-color: rgba(79, 70, 229, 0.06);
          color: #4f46e5;
        }
        .theme-dark & {
          background-color: rgba(255, 255, 255, 0.06);
          color: #38bdf8;
        }
      }
    }
  }
}

@keyframes activeTagDotPulse {
  0% { transform: scale(0.8); opacity: 0.6; }
  100% { transform: scale(1.4); opacity: 1; }
}
</style>

<style lang="scss">
$tags-bar-height: 38px;
.tags-view-wrapper {
  .tags-view-item {
    .tags-close-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 14px;
      height: 14px;
      margin-left: 2px;
      border-radius: 50%;
      transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
      cursor: pointer;
      
      .el-icon-close {
        width: 1em;
        height: 1em;
        vertical-align: 0;
        line-height: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 8px;
      }
      
      &:hover {
        background-color: rgba(0, 0, 0, 0.1);
        .theme-dark & {
          background-color: rgba(255, 255, 255, 0.15);
        }
        
        .el-icon-close {
          color: inherit;
        }
      }
    }
  }
}

/* 页签全屏模式样式 */
.main-container.fullscreen-mode {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  margin-left: 0 !important;
  transition: none !important;
}

.main-container.fullscreen-mode .fixed-header {
  display: block !important;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  width: 100% !important;
  z-index: 1000;
  transition: none !important;
}

.main-container.fullscreen-mode .fixed-header .navbar {
  display: none !important;
}

.main-container.fullscreen-mode .app-main {
  position: fixed;
  top: $tags-bar-height;
  left: 0;
  right: 0;
  bottom: 0;
  margin: 0 !important;
  padding: 0 !important;
  height: calc(100vh - #{$tags-bar-height}) !important;
  min-height: calc(100vh - #{$tags-bar-height}) !important;
  overflow: auto;
}
</style>