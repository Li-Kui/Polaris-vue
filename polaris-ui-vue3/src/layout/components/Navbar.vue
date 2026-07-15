<template>
  <div class="navbar" :class="['nav' + settingsStore.navType, `theme-${isDark ? 'dark' : 'light'}`]">
    <!-- 左侧区域：面包屑和安全加密徽章 -->
    <div class="header-left">
      <hamburger id="hamburger-container" :is-active="appStore.sidebar.opened" class="hamburger-container" @toggleClick="toggleSideBar" v-if="settingsStore.navType != 3" />
      <breadcrumb v-if="settingsStore.navType == 1" id="breadcrumb-container" class="breadcrumb-container" />
      <top-nav v-if="settingsStore.navType == 2" id="topmenu-container" class="topmenu-container" />
      
      <!-- 🪐 极客 HUD 状态区 -->
      <div class="header-hud hide-mobile" v-if="appStore.device !== 'mobile'">
        <span class="hud-security-badge">
          <span class="shield-icon">🛡️</span>
          <span class="shield-text">安全通道已加密</span>
        </span>
      </div>
    </div>

    <!-- 右侧区域 -->
    <div class="right-menu">
      <template v-if="appStore.device !== 'mobile'">
        <header-search id="header-search" class="right-menu-item" />

        <screenfull id="screenfull" class="right-menu-item hover-effect" />

        <!-- 主题切换 -->
        <el-tooltip content="主题模式" effect="dark" placement="bottom">
          <div class="right-menu-item hover-effect theme-switch-wrapper" @click="toggleTheme">
            <svg-icon v-if="isDark" icon-class="sunny" class="theme-icon" />
            <svg-icon v-if="!isDark" icon-class="moon" class="theme-icon" />
          </div>
        </el-tooltip>

        <el-tooltip content="布局大小" effect="dark" placement="bottom">
          <size-select id="size-select" class="right-menu-item hover-effect" />
        </el-tooltip>

        <el-tooltip content="消息通知" effect="dark" placement="bottom">
          <header-notice id="header-notice" class="right-menu-item hover-effect" />
        </el-tooltip>
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
import Hamburger from '@/components/Hamburger'
import Screenfull from '@/components/Screenfull'
import SizeSelect from '@/components/SizeSelect'
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
      await new Promise((resolve) => setTimeout(resolve, 10))
      settingsStore.toggleTheme()
      await nextTick()
    })
    await transition.ready

    const endRadius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
    const clipPath = [`circle(0px at ${x}px ${y}px)`, `circle(${endRadius}px at ${x}px ${y}px)`]
    document.documentElement.animate(
      {
        clipPath: !wasDark ? [...clipPath].reverse() : clipPath
      }, {
        duration: 650,
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
  height: 50px;
  overflow: hidden;
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
    background-color: rgba(255, 255, 255, 0.35);
    border-bottom-color: rgba(0, 0, 0, 0.03);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.02);
  }

  &.theme-dark {
    background-color: rgba(15, 23, 42, 0.25);
    border-bottom-color: rgba(255, 255, 255, 0.03);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 100%;

  .hamburger-container {
    height: 100%;
    cursor: pointer;
    transition: background 0.3s;
    display: flex;
    align-items: center;
    flex-shrink: 0;

    &:hover {
      background: rgba(0, 0, 0, 0.025);
    }
  }
}

.header-hud {
  display: flex;
  align-items: center;
}

.hud-security-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 99px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.02em;
  backdrop-filter: blur(5px);
  
  .theme-light & {
    background-color: rgba(16, 185, 129, 0.05);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.1);
  }
  .theme-dark & {
    background-color: rgba(16, 185, 129, 0.08);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.15);
  }
}

.right-menu {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 8px;

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

    &.theme-switch-wrapper {
      .theme-icon {
        font-size: 15px;
        transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
        
        &:hover {
          transform: scale(1.15) rotate(15deg);
        }
      }
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
    width: 28px;
    height: 28px;
    border-radius: 50%;
    object-fit: cover;
    border: 1px solid rgba(0, 0, 0, 0.05);
    
    .theme-dark & {
      border-color: rgba(255, 255, 255, 0.1);
    }
  }

  .avatar-circle {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
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
    .theme-dark & { color: #cbd5e1; }
  }
}

@media (max-width: 768px) {
  .hide-mobile {
    display: none !important;
  }
}
</style>

