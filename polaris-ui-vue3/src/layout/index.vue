<template>
  <div :class="[classObj, `theme-${isDark ? 'dark' : 'light'}`]" class="app-wrapper" :style="{ 
    '--current-color': theme, 
    '--current-color-light': theme + '1a', 
    '--current-color-dark-bg': theme + '33',
    '--sidebar-width': sidebarWidthStyle
  }">
    <!-- 现代化弥散渐变 + 神经网络突触背景 (Mesh Gradients & Polaris Network) -->
    <div class="mesh-gradient-bg">
      <div class="glow-orb orb-1"></div>
      <div class="glow-orb orb-2"></div>
      <div class="glow-orb orb-3"></div>
      
      <!-- 神经网络发光线条与突触节点 (CSS/SVG 动画) -->
      <svg class="neural-network-svg" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="line-grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.1" />
            <stop offset="50%" stop-color="#818cf8" stop-opacity="0.3" />
            <stop offset="100%" stop-color="#ec4899" stop-opacity="0.1" />
          </linearGradient>
          <linearGradient id="line-grad-light" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#4f46e5" stop-opacity="0.1" />
            <stop offset="50%" stop-color="#a855f7" stop-opacity="0.2" />
            <stop offset="100%" stop-color="#6366f1" stop-opacity="0.1" />
          </linearGradient>
        </defs>
        <!-- 神经网络突触连线 -->
        <g class="neural-lines">
          <line x1="10%" y1="20%" x2="30%" y2="40%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="30%" y1="40%" x2="50%" y2="15%" stroke="url(#line-grad)" stroke-width="1.5" />
          <line x1="30%" y1="40%" x2="25%" y2="80%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="50%" y1="15%" x2="70%" y2="45%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="70%" y1="45%" x2="90%" y2="30%" stroke="url(#line-grad)" stroke-width="1.5" />
          <line x1="70%" y1="45%" x2="60%" y2="85%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="25%" y1="80%" x2="60%" y2="85%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="60%" y1="85%" x2="90%" y2="70%" stroke="url(#line-grad)" stroke-width="1" />
        </g>
        <!-- 神经网络发光突触节点 -->
        <g class="neural-nodes">
          <circle cx="10%" cy="20%" r="3" class="synapse-node node-slow" />
          <circle cx="30%" cy="40%" r="4" class="synapse-node node-fast" />
          <circle cx="50%" cy="15%" r="3.5" class="synapse-node node-pulse" />
          <circle cx="25%" cy="80%" r="3.5" class="synapse-node node-slow" />
          <circle cx="70%" cy="45%" r="5" class="synapse-node node-fast" />
          <circle cx="90%" cy="30%" r="3" class="synapse-node node-pulse" />
          <circle cx="60%" cy="85%" r="4.5" class="synapse-node node-slow" />
          <circle cx="90%" cy="70%" r="3.5" class="synapse-node node-fast" />
        </g>
      </svg>
      <div class="mesh-grid-overlay"></div>
    </div>

    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!sidebar.hide" class="sidebar-container" @update-width="handleSidebarWidthUpdate" />
    
    <div :class="{ hasTagsView: needTagsView, sidebarHide: sidebar.hide }" class="main-container">
      <div :class="{ 'fixed-header': fixedHeader }">
        <navbar @setLayout="setLayout" />
        <tags-view v-if="needTagsView" />
      </div>
      <app-main />
      <settings ref="settingRef" />
    </div>
  </div>
</template>

<script setup>
import {computed, ref, watch, watchEffect} from 'vue'
import {useWindowSize} from '@vueuse/core'
import Sidebar from './components/Sidebar/index.vue'
import {AppMain, Navbar, Settings, TagsView} from './components'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()
const theme = computed(() => settingsStore.theme)
const isDark = computed(() => settingsStore.isDark)
const sidebar = computed(() => useAppStore().sidebar)
const device = computed(() => useAppStore().device)
const needTagsView = computed(() => settingsStore.tagsView)
const fixedHeader = computed(() => settingsStore.fixedHeader)

// 拖拽宽度自适应状态
const customSidebarWidth = ref(parseInt(localStorage.getItem('polaris.sidebar.width')) || 260)

function handleSidebarWidthUpdate(width) {
  customSidebarWidth.value = width
}

// 动态侧边栏宽度样式计算
const sidebarWidthStyle = computed(() => {
  if (sidebar.value.hide) {
    return '0px'
  }
  if (!sidebar.value.opened) {
    return '76px' // 折叠后固定宽度
  }
  return customSidebarWidth.value + 'px'
})

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile'
}))

const { width } = useWindowSize()
const WIDTH = 992 // refer to Bootstrap's responsive design

watch(() => device.value, () => {
  if (device.value === 'mobile' && sidebar.value.opened) {
    useAppStore().closeSideBar({ withoutAnimation: false })
  }
})

watchEffect(() => {
  if (width.value - 1 < WIDTH) {
    useAppStore().toggleDevice('mobile')
    useAppStore().closeSideBar({ withoutAnimation: true })
  } else {
    useAppStore().toggleDevice('desktop')
  }
})

function handleClickOutside() {
  useAppStore().closeSideBar({ withoutAnimation: false })
}

const settingRef = ref(null)
function setLayout() {
  settingRef.value.openSetting()
}
</script>

<style lang="scss" scoped>
@use "@/assets/styles/mixin.scss" as mix;
@use "@/assets/styles/variables.module.scss" as vars;

.app-wrapper {
  @include mix.clearfix;
  position: relative;
  height: 100%;
  width: 100%;
  display: flex;
  overflow: hidden;

  // 极光亮色设计系统配置
  --polaris-bg: #f1f5f9;
  --polaris-card-bg: rgba(255, 255, 255, 0.65);
  --polaris-card-border: rgba(255, 255, 255, 0.5);
  --polaris-inner-border: rgba(0, 0, 0, 0.05);
  --polaris-text-main: #0f172a;
  --polaris-text-sub: #64748b;
  --polaris-brand-color: #4f46e5;
  --polaris-brand-hover: rgba(79, 70, 229, 0.06);

  &.theme-dark {
    // 深空暗色设计系统配置 - 匹配 Demo 高级配色
    --polaris-bg: #080a10;
    --polaris-card-bg: rgba(10, 15, 30, 0.55);
    --polaris-card-border: rgba(255, 255, 255, 0.05);
    --polaris-inner-border: rgba(255, 255, 255, 0.08);
    --polaris-text-main: #f8fafc;
    --polaris-text-sub: #94a3b8;
    --polaris-brand-color: #38bdf8;
    --polaris-brand-hover: rgba(255, 255, 255, 0.05);
    background-color: var(--polaris-bg) !important;
  }

  background-color: var(--polaris-bg) !important;
  color: var(--polaris-text-main) !important;
  transition: background-color 0.5s cubic-bezier(0.25, 0.8, 0.25, 1), color 0.5s ease;
}

// ===== 弥散渐变 + 神经网络连线背景 =====
.mesh-gradient-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 1;
  pointer-events: none;

  // 暗黑模式下隐藏弥散渐变，避免颜色透出
  .theme-dark & {
    display: none;
  }
}

.neural-network-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 3;
}

.neural-lines line {
  .theme-light & {
    stroke: url(#line-grad-light) !important;
  }
}

.synapse-node {
  fill: #818cf8;
  filter: drop-shadow(0 0 6px rgba(129, 140, 248, 0.8));
  
  .theme-light & {
    fill: #4f46e5;
    filter: drop-shadow(0 0 4px rgba(79, 70, 229, 0.4));
  }

  &.node-slow { animation: nodeBreath 4s infinite alternate ease-in-out; }
  &.node-fast { animation: nodeBreath 2s infinite alternate ease-in-out; }
  &.node-pulse { animation: nodeRadarPulse 3s infinite ease-out; }
}

@keyframes nodeBreath {
  0% { transform: scale(0.8); opacity: 0.4; }
  100% { transform: scale(1.2); opacity: 0.9; }
}

@keyframes nodeRadarPulse {
  0% { r: 1.5; opacity: 0.9; }
  50% { r: 6; opacity: 0.3; }
  100% { r: 1.5; opacity: 0.9; }
}

.glow-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.55;
  transition: all 1s ease;
}

.theme-light .glow-orb {
  mix-blend-mode: multiply;
  opacity: 0.15;
}
.theme-dark .glow-orb {
  mix-blend-mode: screen;
}

.orb-1 {
  width: 600px;
  height: 600px;
  top: -10%;
  left: -10%;
  animation: float-orb-1 25s infinite alternate ease-in-out;
  
  .theme-light & { background: radial-gradient(circle, #818cf8 0%, transparent 80%); }
  .theme-dark & { background: radial-gradient(circle, rgba(99, 102, 241, 0.35) 0%, transparent 70%); }
}

.orb-2 {
  width: 650px;
  height: 650px;
  bottom: -20%;
  right: -10%;
  animation: float-orb-2 30s infinite alternate ease-in-out;

  .theme-light & { background: radial-gradient(circle, #38bdf8 0%, transparent 80%); }
  .theme-dark & { background: radial-gradient(circle, rgba(14, 165, 233, 0.25) 0%, transparent 70%); }
}

.orb-3 {
  width: 500px;
  height: 500px;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: float-orb-3 20s infinite alternate ease-in-out;

  .theme-light & { background: radial-gradient(circle, #c084fc 0%, transparent 80%); }
  .theme-dark & { background: radial-gradient(circle, rgba(168, 85, 247, 0.2) 0%, transparent 70%); }
}

@keyframes float-orb-1 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(50px, 80px) scale(1.1); }
}

@keyframes float-orb-2 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-80px, -40px) scale(1.05); }
}

@keyframes float-orb-3 {
  0% { transform: translate(-50%, -50%) scale(1); }
  100% { transform: translate(-30%, -60%) scale(1.15); }
}

.mesh-grid-overlay {
  position: absolute;
  inset: 0;
  background-image: 
    radial-gradient(circle at 1px 1px, rgba(255, 255, 255, 0.02) 1px, transparent 0),
    linear-gradient(to right, rgba(255, 255, 255, 0.008) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.008) 1px, transparent 1px);
  background-size: 50px 50px, 50px 50px, 50px 50px;
  z-index: 4;

  .theme-light & {
    background-image: 
      radial-gradient(circle at 1px 1px, rgba(0, 0, 0, 0.015) 1px, transparent 0),
      linear-gradient(to right, rgba(0, 0, 0, 0.008) 1px, transparent 1px),
      linear-gradient(to bottom, rgba(0, 0, 0, 0.008) 1px, transparent 1px);
  }
}

.sidebar-container {
  width: var(--sidebar-width) !important;
  z-index: 1001;
  position: relative;
  height: 100%;
  flex-shrink: 0;
}

.main-container {
  flex: 1;
  margin-left: 0 !important; // 弃用固定 margin-left，转用 flex
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  position: relative;
  z-index: 10;
}

.fixed-header {
  position: relative; // 弃用 fixed，避免与 flex 布局层叠混乱
  width: 100% !important;
  z-index: 9;
}

.drawer-bg {
  background: #000;
  opacity: 0.3;
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}
</style>
