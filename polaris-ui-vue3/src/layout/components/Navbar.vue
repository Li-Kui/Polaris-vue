<template>
  <div class="navbar" :class="['nav' + settingsStore.navType, `theme-${isDark ? 'dark' : 'light'}`]">
    <!-- 左侧区域：面包屑、搜索和安全加密徽章 -->
    <div class="header-left">
      <!-- 按照 Demo 隐藏顶栏折叠按钮 Hamburger -->
      <breadcrumb v-if="settingsStore.navType == 1" id="breadcrumb-container" class="breadcrumb-container" />
      <top-nav v-if="settingsStore.navType == 2" id="topmenu-container" class="topmenu-container" />
      
      <!-- 搜索指令触发器移到左侧 -->
      <header-search id="header-search" class="header-search-wrapper" />
    </div>

    <!-- 右侧区域 -->
    <div class="right-menu">
      <template v-if="appStore.device !== 'mobile'">
        <!-- 消息通知保留 -->
        <header-notice id="header-notice" class="notice-menu-item" />

        <!-- 🪐 极客极轨主题切换器 -->
        <div class="header-theme-toggle">
          <div class="mini-theme-track" @click="handleThemeToggleClick">
            <div 
              :class="['mini-opt', { active: !isDark }]" 
              @click.stop="setTheme('light', $event)"
            >
              ☀️
            </div>
            <div 
              :class="['mini-opt', { active: isDark }]" 
              @click.stop="setTheme('dark', $event)"
            >
              🌙
            </div>
            <div :class="['mini-thumb', isDark ? 'dark' : 'light']"></div>
          </div>
        </div>
      </template>

      <!-- 用户下拉菜单，升级为圆角 modern dropdown -->
      <el-dropdown @command="handleCommand" class="avatar-container right-menu-item hover-effect" trigger="hover" teleported>
        <div class="user-avatar-wrap">
          <img v-if="userStore.avatar" :src="userStore.avatar" class="user-avatar" />
          <div v-else class="avatar-circle">{{ userStore.nickName ? userStore.nickName.substring(0, 2).toUpperCase() : 'AD' }}</div>
          <span class="username hide-mobile"> {{ userStore.nickName }} </span>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="modern-dropdown-menu">
            <router-link to="/user/profile">
              <el-dropdown-item command="profile">👤 个人中心</el-dropdown-item>
            </router-link>
            <el-dropdown-item command="setLayout" v-if="settingsStore.showSettings">
              ⚙️ 布局设置
            </el-dropdown-item>
            <el-dropdown-item command="lockScreen">
              🔒 锁定屏幕
            </el-dropdown-item>
            <el-dropdown-item divided command="logout" class="color-danger">
              🚪 退出安全令牌
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick} from 'vue'
import {ElMessageBox} from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb'
import TopNav from './TopNav'
import HeaderSearch from '@/components/HeaderSearch'
import useAppStore from '@/store/modules/app'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import useSettingsStore from '@/store/modules/settings'
import HeaderNotice from './HeaderNotice'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const lockStore = useLockStore()
const settingsStore = useSettingsStore()

const isDark = computed(() => settingsStore.isDark)

function toggleSideBar() {
  appStore.toggleSideBar()
}

function handleCommand(command) {
  switch (command) {
    case "setLayout":
      setLayout()
      break
    case "lockScreen":
      lockScreen()
      break
    case "logout":
      logout()
      break
    default:
      break
  }
}

function logout() {
  ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logOut().then(() => {
      location.href = '/index'
    })
  }).catch(() => { })
}

const emits = defineEmits(['setLayout'])
function setLayout() {
  emits('setLayout')
}

function lockScreen() {
  const currentPath = route.fullPath
  lockStore.lockScreen(currentPath)
  router.push('/lock')
}

async function toggleTheme(event) {
  const x = event?.clientX || window.innerWidth / 2
  const y = event?.clientY || window.innerHeight / 2
  const wasDark = settingsStore.isDark

  const isReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
  const isSupported = document.startViewTransition && !isReducedMotion

  if (!isSupported) {
    settingsStore.toggleTheme()
    return
  }

  try {
    const transition = document.startViewTransition(async () => {
      await settingsStore.toggleTheme()
      await nextTick()
    })
    await transition.ready

    const endRadius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
    const clipPath = [`circle(0px at ${x}px ${y}px)`, `circle(${endRadius}px at ${x}px ${y}px)`]
    document.documentElement.animate(
      {
        clipPath: !wasDark ? [...clipPath].reverse() : clipPath
      }, {
        duration: 400,
        easing: "cubic-bezier(0.4, 0, 0.2, 1)",
        fill: "forwards",
        pseudoElement: !wasDark ? "::view-transition-old(root)" : "::view-transition-new(root)"
      }
    )
    await transition.finished
  } catch (error) {
    console.warn("View transition failed, falling back to immediate toggle:", error)
    settingsStore.toggleTheme()
  }
}

function setTheme(themeMode, event) {
  const currentIsDark = isDark.value
  if ((themeMode === 'dark' && !currentIsDark) || (themeMode === 'light' && currentIsDark)) {
    toggleTheme(event)
  }
}

function handleThemeToggleClick(event) {
  toggleTheme(event)
}
</script>

<style lang="scss">
/* 全局 popper 样式覆盖，解决 Teleport 状态下的下拉框毛玻璃视觉 */
.modern-dropdown-menu {
  background: transparent !important;
  padding: 6px !important;
  border-radius: 14px !important;
  border: none !important;

  .el-dropdown-menu__item {
    font-size: 12px !important;
    font-weight: 600 !important;
    padding: 8px 16px !important;
    border-radius: 8px !important;
    transition: all 0.2s !important;
    
    html:not(.dark) & {
      color: #475569 !important;
      
      &:hover, &:focus, &.is-focus {
        background-color: rgba(79, 70, 229, 0.06) !important;
        color: #4f46e5 !important;
      }
    }
    
    .dark &, &.dark {
      color: #cbd5e1 !important;
      
      &:hover, &:focus, &.is-focus {
        background-color: rgba(255, 255, 255, 0.06) !important;
        color: #38bdf8 !important;
      }
    }

    &.color-danger {
      color: #ef4444 !important;
      
      &:hover, &:focus, &.is-focus {
        html:not(.dark) & {
          background-color: rgba(239, 68, 68, 0.06) !important;
          color: #ef4444 !important;
        }
        .dark & {
          background-color: rgba(239, 68, 68, 0.15) !important;
          color: #fca5a5 !important;
        }
      }
    }
  }

  .el-dropdown-menu__item--divided {
    margin: 6px 0 !important;
    
    html:not(.dark) & {
      border-top-color: rgba(0, 0, 0, 0.05) !important;
    }
    .dark & {
      border-top-color: rgba(255, 255, 255, 0.08) !important;
    }
  }
}
</style>

<style lang="scss" scoped>
.navbar.nav3 {
  .hamburger-container {
    display: none !important;
  }
}

.navbar {
  height: 64px;
  overflow: visible;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-sizing: border-box;
  backdrop-filter: blur(20px);
  border-bottom: 1px solid transparent;
  transition: all 0.3s;

  &.theme-light {
    background-color: rgba(255, 255, 255, 0.85);
    border-bottom-color: rgba(0, 0, 0, 0.03);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.02);
  }

  &.theme-dark {
    background-color: rgba(15, 23, 42, 0.85);
    border-bottom-color: rgba(255, 255, 255, 0.03);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 100%;

  .header-search-wrapper {
    display: flex;
    align-items: center;
  }
}



.right-menu {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 20px;

  &:focus {
    outline: none;
  }

  .right-menu-item {
    display: inline-flex;
    align-items: center;
    padding: 0 8px;
    height: 100%;
    font-size: 16px;
    color: #5a5e66;
    vertical-align: text-bottom;
    transition: background 0.3s;

    &.hover-effect {
      cursor: pointer;

      &:hover {
        background: rgba(0, 0, 0, 0.025);
        
        .theme-dark & {
          background: rgba(255, 255, 255, 0.025);
        }
      }
    }
  }

  .notice-menu-item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    height: 32px;
    width: 32px;
    border-radius: 8px;
    transition: background 0.3s;
    
    &:hover {
      background: rgba(0, 0, 0, 0.035);
      .theme-dark & {
        background: rgba(255, 255, 255, 0.035);
      }
    }
    
    :deep(.notice-trigger) {
      height: 100% !important;
      display: flex !important;
      align-items: center !important;
      justify-content: center !important;
      padding: 0 !important;
      transform: none !important;
      
      .svg-icon {
        width: 1.25em !important;
        height: 1.25em !important;
        color: #64748b;
        
        .theme-dark & {
          color: #d0d0d0;
        }
      }
    }
  }

  .header-theme-toggle {
    display: flex;
    align-items: center;
  }

  .mini-theme-track {
    position: relative;
    display: flex;
    padding: 2px;
    width: 72px;
    height: 26px;
    border-radius: 99px;
    border: 1px solid;
    cursor: pointer;
    box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.05);
    transition: all 0.3s;
    box-sizing: border-box;
    
    .theme-light & {
      background-color: rgba(0, 0, 0, 0.03);
      border-color: rgba(0, 0, 0, 0.06);
    }
    
    .theme-dark & {
      background-color: rgba(0, 0, 0, 0.03);
      border-color: rgba(255, 255, 255, 0.05);
    }
  }

  .mini-opt {
    flex: 1;
    font-size: 11px;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.3s;
    user-select: none;
    
    &.active {
      .theme-light & { color: #ffffff !important; }
      .theme-dark & { color: #ffffff !important; }
    }
    
    &:not(.active) {
      opacity: 0.55;
      &:hover { opacity: 0.85; }
    }
  }

  .mini-thumb {
    position: absolute;
    top: 1px;
    left: 1px;
    bottom: 1px;
    width: 32px;
    border-radius: 99px;
    z-index: 1;
    transition: transform 0.35s cubic-bezier(0.25, 0.8, 0.25, 1.15);
    
    &.light {
      transform: translateX(0);
      background: linear-gradient(135deg, #4f46e5, #6366f1);
      box-shadow: 0 1px 5px rgba(79, 70, 229, 0.3);
    }
    
    &.dark {
      transform: translateX(36px);
      background: linear-gradient(135deg, #38bdf8, #818cf8);
      box-shadow: 0 1px 6px rgba(56, 189, 248, 0.4);
    }
  }

  .avatar-container {
    margin-right: 0px;
    padding-right: 0px;
  }
}

.user-avatar-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  height: 100%;

  .user-avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    object-fit: cover;
    border: 1px solid rgba(0, 0, 0, 0.05);
    
    .theme-dark & {
      border-color: rgba(255, 255, 255, 0.1);
    }
  }

  .avatar-circle {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: bold;
    
    .theme-light & {
      background-color: #4f46e5;
      color: #ffffff;
    }
    .theme-dark & {
      background: linear-gradient(135deg, #38bdf8, #818cf8);
      color: #0f172a;
    }
  }

  .username {
    font-size: 12px;
    font-weight: 700;
    
    .theme-light & { color: #334155; }
    .theme-dark & { color: #d0d0d0; }
  }
}

@media (max-width: 768px) {
  .hide-mobile {
    display: none !important;
  }
}
</style>

