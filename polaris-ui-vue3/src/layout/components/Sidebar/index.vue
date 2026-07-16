<template>
  <!-- 左侧 Sidebar (包含拖拽自适应宽度 + 发丝流光边框 + 二级/三级折叠星轨树) -->
  <aside
    :class="['admin-sidebar', { 'is-collapsed': isCollapse }]"
  >
    <div class="sidebar-brand" v-if="showLogo">
      <span class="brand-icon">✦</span>
      <span v-show="!isCollapse" class="brand-text">{{ title }}</span>
    </div>

    <nav class="sidebar-menu">
      <div v-for="menu in formattedMenus" :key="menu.id" class="menu-node-wrapper">
        <!-- 一级菜单项 -->
        <div
          :class="['menu-item', 'level-1', { 'is-active': isItemActive(menu), 'has-children': menu.children && menu.children.length > 0 }]"
          @click="handleMenuClick(menu, 1)"
        >
          <span class="menu-icon">
            <svg-icon v-if="isSvgIcon(menu.icon)" :icon-class="menu.icon" :class="['icon-' + menu.icon]" />
            <span v-else>{{ menu.icon }}</span>
          </span>
          <span v-show="!isCollapse" class="menu-label">{{ menu.label }}</span>
          
          <span
            v-if="menu.children && menu.children.length > 0 && !isCollapse"
            :class="['menu-arrow', { 'is-rotated': expandedMenus[menu.id] }]"
          >
            ▼
          </span>
          
          <div v-if="isItemActive(menu)" class="menu-active-pill-flow"></div>
        </div>

        <!-- 二级子菜单 -->
        <div
          v-if="menu.children && menu.children.length > 0 && !isCollapse"
          :class="['submenu-grid-wrapper', { 'is-expanded': expandedMenus[menu.id] }]"
        >
          <div class="submenu-inner">
            <div v-for="(sub, subIdx) in menu.children" :key="sub.id" class="menu-node-wrapper" :style="{ '--index': subIdx }">
              <!-- 二级菜单项 -->
              <div
                :class="['menu-item', 'level-2', { 'is-active': isItemActive(sub), 'has-children': sub.children && sub.children.length > 0 }]"
                @click="handleMenuClick(sub, 2, menu.id)"
              >
                <span class="menu-icon">
                  <svg-icon v-if="isSvgIcon(sub.icon)" :icon-class="sub.icon" :class="['icon-' + sub.icon]" />
                  <span v-else-if="sub.icon">{{ sub.icon }}</span>
                  <span v-else>{{ sub.children ? '📁' : '✦' }}</span>
                </span>
                <span class="menu-label">{{ sub.label }}</span>
                
                <span
                  v-if="sub.children && sub.children.length > 0"
                  :class="['menu-arrow', { 'is-rotated': expandedMenus[sub.id] }]"
                >
                  ▼
                </span>
                
                <div v-if="isItemActive(sub)" class="menu-active-pill-flow"></div>
              </div>

              <!-- 三级子菜单 -->
              <div
                v-if="sub.children && sub.children.length > 0"
                :class="['submenu-grid-wrapper', { 'is-expanded': expandedMenus[sub.id] }]"
              >
                <div class="submenu-inner">
                  <div
                    v-for="(leaf, leafIdx) in sub.children"
                    :key="leaf.id"
                    :class="['menu-item', 'level-3', { 'is-active': isItemActive(leaf) }]"
                    @click="handleMenuClick(leaf, 3, sub.id)"
                    :style="{ '--index': leafIdx + 2 }"
                  >
                    <span class="menu-icon-line">↳</span>
                    <span class="menu-label">{{ leaf.label }}</span>
                    
                    <div v-if="isItemActive(leaf)" class="menu-active-pill-flow"></div>
                  </div>
                </div>
              </div>

            </div>
          </div>
        </div>
      </div>
    </nav>

    <div class="sidebar-footer">
      <button class="collapse-btn" @click="toggleSidebarCollapse">
        <span class="arrow-sym">{{ isCollapse ? '→' : '←' }}</span>
      </button>
    </div>

    <!-- 右侧边缘流光发丝线 (量子拖拽阻尼轨 + 双主题自动发光流光线) -->
    <div
      class="sidebar-shimmer-border draggable-resizer"
      @mousedown="initResize"
    >
      <div class="resizer-knob-spark"></div>
    </div>
  </aside>
</template>

<script setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {isExternal} from '@/utils/validate'
import {getNormalPath} from '@/utils/ruoyi'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const emit = defineEmits(['update-width'])

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()

const showLogo = computed(() => settingsStore.sidebarLogo)
const isCollapse = computed(() => !appStore.sidebar.opened)
const title = import.meta.env.VITE_APP_TITLE || '北辰 AI 管理系统'

const sidebarWidth = ref(parseInt(localStorage.getItem('polaris.sidebar.width')) || 260)
const isResizing = ref(false)

const expandedMenus = ref({})

// 处理路径拼接
function resolvePath(basePath, routePath) {
  if (isExternal(routePath)) {
    return routePath
  }
  if (isExternal(basePath)) {
    return basePath
  }
  let fullPath = ''
  if (basePath === '/') {
    fullPath = '/' + routePath
  } else {
    fullPath = basePath + '/' + routePath
  }
  return getNormalPath(fullPath)
}

// 校验是否是 element Svg 图标
function isSvgIcon(iconName) {
  if (!iconName) return false
  return /^[a-zA-Z0-9_-]+$/.test(iconName)
}

// 格式化解析若依动态路由，方便渲染一二三级菜单
const formattedMenus = computed(() => {
  const routes = permissionStore.sidebarRouters
  const menuData = []

  routes.forEach(routeItem => {
    if (routeItem.hidden) return

    const showingChildren = routeItem.children ? routeItem.children.filter(c => !c.hidden) : []

    if (showingChildren.length === 1 && !routeItem.alwaysShow) {
      // 提拔为一级菜单展示自身唯一的子节点
      const onlyChild = showingChildren[0]
      const resolvedPath = resolvePath(routeItem.path, onlyChild.path)
      menuData.push({
        id: resolvedPath,
        path: resolvedPath,
        label: onlyChild.meta?.title || routeItem.meta?.title || '',
        icon: onlyChild.meta?.icon || routeItem.meta?.icon || '✦',
        query: onlyChild.query ? JSON.stringify(onlyChild.query) : undefined,
        link: onlyChild.meta?.link
      })
    } else if (showingChildren.length === 0) {
      // 没有子级，展示自身为一级菜单
      const resolvedPath = resolvePath(routeItem.path, '')
      menuData.push({
        id: resolvedPath,
        path: resolvedPath,
        label: routeItem.meta?.title || '',
        icon: routeItem.meta?.icon || '✦',
        query: routeItem.query ? JSON.stringify(routeItem.query) : undefined,
        link: routeItem.meta?.link
      })
    } else {
      // 有多个子菜单，或者强制展示自身为父级
      const children = []
      showingChildren.forEach(child => {
        const subShowingChildren = child.children ? child.children.filter(cc => !cc.hidden) : []
        const childResolvedPath = resolvePath(routeItem.path, child.path)

        if (subShowingChildren.length > 0) {
          // 含有三级菜单
          const subChildren = []
          subShowingChildren.forEach(leaf => {
            const leafResolvedPath = resolvePath(childResolvedPath, leaf.path)
            subChildren.push({
              id: leafResolvedPath,
              path: leafResolvedPath,
              label: leaf.meta?.title || '',
              icon: leaf.meta?.icon || '✦',
              query: leaf.query ? JSON.stringify(leaf.query) : undefined,
              link: leaf.meta?.link
            })
          })
          children.push({
            id: childResolvedPath,
            path: childResolvedPath,
            label: child.meta?.title || '',
            icon: child.meta?.icon || '✦',
            children: subChildren
          })
        } else {
          // 常规二级菜单
          children.push({
            id: childResolvedPath,
            path: childResolvedPath,
            label: child.meta?.title || '',
            icon: child.meta?.icon || '✦',
            query: child.query ? JSON.stringify(child.query) : undefined,
            link: child.meta?.link
          })
        }
      })

      menuData.push({
        id: routeItem.path || resolvePath(routeItem.path, ''),
        label: routeItem.meta?.title || '',
        icon: routeItem.meta?.icon || '✦',
        children: children
      })
    }
  })

  return menuData
})

// 计算当前高亮匹配路由项
const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta.activeMenu) {
    return meta.activeMenu
  }
  return path
})

// 判断菜单是否处于激活高亮状态
function isItemActive(menu) {
  if (activeMenu.value === menu.path) {
    return true
  }
  return false
}

// 点击菜单逻辑（兼容一二三级，以及外链）
function handleMenuClick(menu, level, parentId) {
  if (menu.children && menu.children.length > 0) {
    expandedMenus.value[menu.id] = !expandedMenus.value[menu.id]
  } else {
    if (menu.link && isExternal(menu.link)) {
      window.open(menu.link, '_blank')
    } else if (isExternal(menu.path)) {
      window.open(menu.path, '_blank')
    } else {
      let queryObj = undefined
      if (menu.query) {
        try {
          queryObj = JSON.parse(menu.query)
        } catch (e) {
          queryObj = undefined
        }
      }
      router.push({ path: menu.path, query: queryObj })
    }
  }
}

// 自动展开激活路由对应的所有父级菜单
function autoExpandParent(menuList, targetPath, parentIds = []) {
  for (const item of menuList) {
    if (item.path === targetPath) {
      parentIds.forEach(id => {
        expandedMenus.value[id] = true
      })
      return true
    }
    if (item.children && item.children.length > 0) {
      const found = autoExpandParent(item.children, targetPath, [...parentIds, item.id])
      if (found) {
        expandedMenus.value[item.id] = true
        return true
      }
    }
  }
  return false
}

watch(activeMenu, (newVal) => {
  nextTick(() => {
    autoExpandParent(formattedMenus.value, newVal)
  })
}, { immediate: true })

function toggleSidebarCollapse() {
  appStore.toggleSideBar()
  if (isCollapse.value) {
    expandedMenus.value = {}
  } else {
    // 展开时，自动高亮展开对应的父级
    autoExpandParent(formattedMenus.value, activeMenu.value)
  }
}

// 侧栏拖拽宽度自适应
function initResize(e) {
  if (isCollapse.value) return
  isResizing.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', handleResize)
  window.addEventListener('mouseup', stopResize)
}

function handleResize(e) {
  if (!isResizing.value) return
  let newWidth = e.clientX
  if (newWidth < 180) newWidth = 180
  if (newWidth > 400) newWidth = 400
  sidebarWidth.value = newWidth
  emit('update-width', newWidth)
}

function stopResize() {
  isResizing.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('mousemove', handleResize)
  window.removeEventListener('mouseup', stopResize)
  localStorage.setItem('polaris.sidebar.width', sidebarWidth.value)
}

// 初始化时向父级通报宽度
nextTick(() => {
  if (!isCollapse.value) {
    emit('update-width', sidebarWidth.value)
  }
})
</script>

<style lang="scss" scoped>
.admin-sidebar {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(40px);
  position: relative;
  box-shadow: 0px 0px 8px 0px rgba(0, 0, 0, 0.1);
  
  .theme-light & {
    background: rgba(255, 255, 255, 0.65);
    border-right: 1px solid rgba(0, 0, 0, 0.05);
  }
  .theme-dark & {
    background: rgba(15, 23, 42, 0.4);
    border-right: 1px solid rgba(255, 255, 255, 0.03);
  }

  &.is-collapsed {
    transition: width 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  }
}

// 右侧边缘流光发丝轨
.sidebar-shimmer-border {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 2px; // 极细发丝轨道
  z-index: 100;
  cursor: col-resize;
  overflow: hidden; // 将粒子流光限制在发丝内滑行
  
  .theme-light & {
    background-color: rgba(0, 0, 0, 0.03); // 亮色极淡底轨线
  }
  .theme-dark & {
    background-color: rgba(255, 255, 255, 0.03); // 暗色极淡底轨线
  }
  
  &::before {
    content: '';
    position: absolute;
    top: -40%;
    left: 0;
    width: 100%;
    height: 40%; // 流光束占高 40%
    
    // 暗黑模式渐变流光线
    .theme-dark & {
      background: linear-gradient(to bottom, transparent, #38bdf8, #818cf8, transparent);
      filter: drop-shadow(0 0 3px #38bdf8);
    }
    // 亮色模式渐变流光线
    .theme-light & {
      background: linear-gradient(to bottom, transparent, #4f46e5, #a855f7, transparent);
      filter: drop-shadow(0 0 2px #4f46e5);
    }
    
    animation: borderShimmerFlow 3.5s linear infinite; // 3.5秒一次不断向下流动
  }
}

@keyframes borderShimmerFlow {
  0% { transform: translateY(-120%); }
  100% { transform: translateY(350%); }
}

.sidebar-brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid transparent;
  z-index: 2;
  flex-shrink: 0;
  
  .theme-light & {
    border-bottom-color: rgba(0, 0, 0, 0.03);
  }
  .theme-dark & {
    border-bottom-color: rgba(255, 255, 255, 0.03);
  }

  .brand-icon {
    font-size: 20px;
    font-weight: 800;
    animation: pulse-icon 3s infinite alternate ease-in-out;
    
    .theme-light & { color: #4f46e5; }
    .theme-dark & { color: #38bdf8; }
  }

  .brand-text {
    font-weight: 800;
    font-size: 16px;
    letter-spacing: 0.05em;
    white-space: nowrap;
    
    .theme-light & { color: #0f172a; }
    .theme-dark & { color: #ffffff; }
  }
}

.sidebar-menu {
  flex: 1;
  padding: 16px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  z-index: 2;
  overflow-y: auto;
  
  &::-webkit-scrollbar {
    width: 0px;
  }
}

.menu-node-wrapper {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

// 伸展折叠子菜单网格过渡高度
.submenu-grid-wrapper {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.35s cubic-bezier(0.25, 0.8, 0.25, 1),
              visibility 0.35s cubic-bezier(0.25, 0.8, 0.25, 1);
  visibility: hidden;
  overflow: hidden;
  
  &.is-expanded {
    grid-template-rows: 1fr;
    visibility: visible;
    overflow: visible;
    
    .submenu-inner {
      overflow: visible;
      padding: 2px 0;
    }
    
    .menu-node-wrapper {
      animation: submenuNodeLight 0.35s cubic-bezier(0.25, 0.8, 0.25, 1) both;
      
      &:nth-child(1) { animation-delay: 0.04s; }
      &:nth-child(2) { animation-delay: 0.08s; }
      &:nth-child(3) { animation-delay: 0.12s; }
      &:nth-child(4) { animation-delay: 0.16s; }
    }
  }
}

@keyframes submenuNodeLight {
  0% { opacity: 0; transform: translateY(5px); }
  100% { opacity: 1; transform: translateY(0); }
}

.submenu-inner {
  overflow: hidden;
  min-height: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  position: relative;
  
  // 树引导线
  &::before {
    content: '';
    position: absolute;
    left: 28px;
    top: 0;
    bottom: 0;
    width: 1px;
    pointer-events: none;
    
    .theme-light & { background-color: rgba(0, 0, 0, 0.05); }
    .theme-dark & { background-color: rgba(255, 255, 255, 0.08); }
  }
}

// 菜单弹性 Hover & 量子冲击波
.menu-item {
  height: 42px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  
  .menu-icon {
    font-size: 16px;
    transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    flex-shrink: 0;
    
    .svg-icon {
      width: 1em;
      height: 1em;
      margin-right: 0px !important; // 重置若依默认 margin
      transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); // 增加 transition 确保 SVG 动画顺滑
    }
  }
  
  .menu-label {
    font-size: 13.5px;
    font-weight: 700;
    transition: color 0.2s;
    white-space: nowrap;
    
    .theme-light & { color: #475569; }
    .theme-dark & { color: #d0d0d0; }
  }

  .menu-arrow {
    margin-left: auto;
    font-size: 9px;
    transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    opacity: 0.6;
    flex-shrink: 0;
    
    &.is-rotated {
      transform: rotate(180deg);
    }
  }

  // 菜单分级缩进
  &.level-1 { padding: 0 14px; }
  &.level-2 {
    padding: 0 14px 0 34px;
    height: 38px;
    .menu-label { font-weight: 600; font-size: 12.5px; }
  }
  &.level-3 {
    padding: 0 14px 0 48px; // 使用 48px 缩进，折线在普通流中排布，自动实现图标与文字一同缩进
    height: 34px;
    .menu-label { font-weight: 500; font-size: 12px; }
  }

  // 悬停图标浮动与缩放
  &:hover {
    transform: scale(1.015) translateX(3px);
    
    .menu-icon, .svg-icon {
      transform: scale(1.15) rotate(5deg);
    }
    
    .theme-light & {
      background-color: rgba(0, 0, 0, 0.02);
      .menu-label { color: #4f46e5; }
    }
    .theme-dark & {
      background-color: rgba(255, 255, 255, 0.025);
      .menu-label { color: #38bdf8; }
    }
  }

  // 点击量子冲击波
  &:active {
    transform: scale(0.97) translateX(1px) !important;
    
    &::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: 10px;
      border: 2px solid;
      opacity: 0.8;
      animation: menuClickPulse 0.4s cubic-bezier(0.25, 0.8, 0.25, 1) forwards;
      
      .theme-light & { border-color: #4f46e5; }
      .theme-dark & { border-color: #38bdf8; }
    }
  }

  // 激活态
  &.is-active {
    .theme-light & {
      background-color: rgba(79, 70, 229, 0.05);
      .menu-label { color: #4f46e5; }
    }
    .theme-dark & {
      background-color: rgba(56, 189, 248, 0.05);
      .menu-label { color: #38bdf8; }
    }
  }
}

.menu-active-pill-flow {
  position: absolute;
  inset: 0;
  border-radius: 10px;
  pointer-events: none;
  z-index: -1;
  border: 1px solid;
  overflow: hidden;
  box-sizing: border-box;
  animation: activePillExpand 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) both;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: -150%;
    width: 60%;
    height: 100%;
    transform: skewX(-25deg);
    animation: activePillSweep 2s infinite ease-in-out;
  }
  
  .theme-light & {
    border-color: rgba(79, 70, 229, 0.25);
    background: linear-gradient(135deg, rgba(79, 70, 229, 0.05), rgba(168, 85, 247, 0.03));
    
    &::before {
      background: linear-gradient(90deg, transparent, rgba(79, 70, 229, 0.12), transparent);
    }
  }
  
  .theme-dark & {
    border-color: rgba(56, 189, 248, 0.25);
    background: linear-gradient(135deg, rgba(56, 189, 248, 0.06), rgba(129, 140, 248, 0.03));
    
    &::before {
      background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent);
    }
  }
}

@keyframes menuClickPulse {
  0% { transform: scale(0.98); opacity: 0.8; }
  100% { transform: scale(1.1); opacity: 0; }
}

@keyframes activePillExpand {
  0% { transform: scaleX(0.8) scaleY(0.7); opacity: 0; }
  100% { transform: scaleX(1) scaleY(1); opacity: 1; }
}

@keyframes activePillSweep {
  0% { left: -150%; }
  50% { left: 150%; }
  100% { left: 150%; }
}

.sidebar-footer {
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  border-top: 1px solid transparent;
  flex-shrink: 0;
  
  .theme-light & {
    border-top-color: rgba(0, 0, 0, 0.03);
  }
  .theme-dark & {
    border-top-color: rgba(255, 255, 255, 0.03);
  }
}

.collapse-btn {
  background: transparent;
  border: none;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  transition: all 0.3s;
  
  .arrow-sym {
    transition: transform 0.3s;
  }
  
  &:hover {
    .arrow-sym {
      transform: scale(1.15);
    }
    .theme-light & {
      background-color: rgba(0, 0, 0, 0.03);
      color: #4f46e5;
    }
    .theme-dark & {
      background-color: rgba(255, 255, 255, 0.03);
      color: #38bdf8;
    }
  }
}

// 三级菜单折线样式
.menu-icon-line {
  font-family: monospace;
  font-weight: bold;
  font-size: 15px;
  margin-right: 8px; // 与文字保持优雅的 8px 间距
  opacity: 0.35;
  transition: opacity 0.28s;
  display: inline-block;
  vertical-align: middle;
  
  .theme-light & {
    color: #475569;
  }
  .theme-dark & {
    color: #d0d0d0;
  }
}

.menu-item:hover, .menu-item.is-active {
  .menu-icon-line {
    opacity: 0.8;
    .theme-light & { color: #4f46e5; }
    .theme-dark & { color: #38bdf8; }
  }
}

// 彩色图标核心配置样式
.svg-icon {
  // 一级与主要二级图标彩色化
  &.icon-dashboard { color: #ff5252 !important; } // 首页：珊瑚红
  &.icon-system { color: #3b82f6 !important; }    // 系统管理：科技蓝
  &.icon-monitor { color: #0ea5e9 !important; }   // 系统监控：天空蓝
  &.icon-tool { color: #a855f7 !important; }      // 系统工具：幻彩紫
  
  // 二级子级菜单图标彩色化
  &.icon-user { color: #10b981 !important; }      // 用户管理：翡翠绿
  &.icon-peoples { color: #6366f1 !important; }   // 角色管理：靛青紫
  &.icon-tree-table { color: #06b6d4 !important; }// 菜单管理：湖水蓝
  &.icon-tree { color: #22c55e !important; }      // 部门管理：森林绿
  &.icon-post { color: #f97316 !important; }      // 岗位管理：活力橙
  &.icon-dict { color: #8b5cf6 !important; }      // 字典管理：优雅紫
  &.icon-edit { color: #ec4899 !important; }      // 参数设置：霓虹粉
  &.icon-message { color: #eab308 !important; }   // 通知公告：琥珀黄
  &.icon-log { color: #f43f5e !important; }       // 日志管理：玫瑰红
  
  // 三级及其他常用图标彩色化
  &.icon-form { color: #10b981 !important; }      // 操作日志：绿
  &.icon-login { color: #3b82f6 !important; }     // 登录日志：蓝
}

@keyframes pulse-icon {
  0% { transform: scale(0.9); opacity: 0.7; }
  100% { transform: scale(1.1); opacity: 1; }
}
</style>
