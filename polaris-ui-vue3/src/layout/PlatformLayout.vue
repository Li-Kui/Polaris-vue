<template>
  <div class="platform-layout" :class="{ 'is-dark': isDark }">
    <!-- 左侧中台专属现代侧边栏 -->
    <aside class="platform-sidebar">
      <!-- 顶部品牌区 -->
      <div class="brand-header">
        <div class="logo-wrapper">
          <img src="@/assets/logo/logo.png" alt="logo" class="brand-logo" />
        </div>
        <div class="brand-info">
          <div class="brand-title">北辰 AI 中台</div>
          <div class="brand-subtitle">Enterprise AI Platform</div>
        </div>
      </div>

      <!-- 租户身份状态卡片 -->
      <div class="tenant-identity-card">
        <div class="tenant-top">
          <span class="tenant-dot"></span>
          <span class="tenant-name" :title="tenantInfo.tenantName || '默认租户'">
            {{ tenantInfo.tenantName || '企业租户中心' }}
          </span>
          <span class="tenant-role-tag">{{ currentUser.role === 'admin' ? '管理员' : '成员' }}</span>
        </div>
        <div class="tenant-code-row">
          <span class="label">租户编码:</span>
          <code class="code-val">{{ tenantInfo.tenantCode || 'default' }}</code>
        </div>
      </div>

      <!-- 导航菜单列表 -->
      <div class="menu-container">
        <div class="menu-group-title">中台概览</div>
        <router-link to="/platform/console/dashboard" class="nav-item" :class="{ active: isCurrent('/platform/console/dashboard') }">
          <el-icon class="nav-icon"><Odometer /></el-icon>
          <span class="nav-text">概览看板</span>
        </router-link>

        <div class="menu-group-title">AI 智能中枢</div>
        <router-link to="/platform/console/chat" class="nav-item" :class="{ active: isCurrent('/platform/console/chat') }">
          <el-icon class="nav-icon"><ChatDotRound /></el-icon>
          <span class="nav-text">AI 对话体验</span>
        </router-link>
        <router-link to="/platform/console/knowledge" class="nav-item" :class="{ active: isCurrent('/platform/console/knowledge') }">
          <el-icon class="nav-icon"><FolderOpened /></el-icon>
          <span class="nav-text">企业知识库</span>
        </router-link>
        <router-link to="/platform/console/agent" class="nav-item" :class="{ active: isCurrent('/platform/console/agent') }">
          <el-icon class="nav-icon"><UserFilled /></el-icon>
          <span class="nav-text">智能体编排</span>
        </router-link>
        <router-link to="/platform/console/model" class="nav-item" :class="{ active: isCurrent('/platform/console/model') }">
          <el-icon class="nav-icon"><Cpu /></el-icon>
          <span class="nav-text">大模型配置</span>
        </router-link>

        <div class="menu-group-title">开放与连接</div>
        <router-link to="/platform/console/apikey" class="nav-item" :class="{ active: isCurrent('/platform/console/apikey') }">
          <el-icon class="nav-icon"><Key /></el-icon>
          <span class="nav-text">API 密钥管理</span>
        </router-link>
        <router-link to="/platform/console/datasource" class="nav-item" :class="{ active: isCurrent('/platform/console/datasource') }">
          <el-icon class="nav-icon"><Coin /></el-icon>
          <span class="nav-text">外部数据源</span>
        </router-link>
        <router-link to="/platform/console/connector" class="nav-item" :class="{ active: isCurrent('/platform/console/connector') }">
          <el-icon class="nav-icon"><Connection /></el-icon>
          <span class="nav-text">API 连接器</span>
        </router-link>

        <div class="menu-group-title">管理与用量</div>
        <router-link to="/platform/console/user" class="nav-item" :class="{ active: isCurrent('/platform/console/user') }">
          <el-icon class="nav-icon"><User /></el-icon>
          <span class="nav-text">租户成员管理</span>
        </router-link>
        <router-link to="/platform/console/usage" class="nav-item" :class="{ active: isCurrent('/platform/console/usage') }">
          <el-icon class="nav-icon"><PieChart /></el-icon>
          <span class="nav-text">用量与账单</span>
        </router-link>
      </div>

      <!-- 底部文档直达入口 -->
      <div class="sidebar-footer">
        <a href="/platform/docs/index.html" target="_blank" class="doc-link-card">
          <div class="doc-card-left">
            <el-icon class="doc-icon"><Document /></el-icon>
            <div>
              <div class="doc-title">开发者文档</div>
              <div class="doc-desc">OpenAI API 接入指南</div>
            </div>
          </div>
          <el-icon class="arrow-icon"><TopRight /></el-icon>
        </a>
      </div>
    </aside>

    <!-- 右侧主界面 -->
    <div class="platform-main-wrapper">
      <!-- 现代毛玻璃顶栏 -->
      <header class="platform-topbar">
        <div class="topbar-left">
          <div class="breadcrumb-trail">
            <span class="crumb-root">控制台</span>
            <span class="crumb-sep">/</span>
            <span class="crumb-current">{{ currentRouteTitle }}</span>
          </div>
        </div>

        <div class="topbar-right">
          <!-- Token 配额快捷指示器 -->
          <div class="quota-pill" @click="$router.push('/platform/console/usage')">
            <div class="quota-icon-box">⚡</div>
            <div class="quota-info">
              <span class="quota-label">配额消耗</span>
              <span class="quota-nums">
                <b>{{ formatNumber(tenantInfo.usedTokens || 0) }}</b>
                / {{ tenantInfo.quotaTokens === -1 ? '无限' : formatNumber(tenantInfo.quotaTokens) }}
              </span>
            </div>
          </div>

          <!-- 文档直达按钮 -->
          <el-tooltip content="查看 API 开放接口文档" placement="bottom">
            <a href="/platform/docs/index.html" target="_blank" class="topbar-icon-btn">
              <el-icon><Document /></el-icon>
            </a>
          </el-tooltip>

          <!-- 光暗模式切换 -->
          <el-tooltip :content="isDark ? '切换至明亮模式' : '切换至暗黑模式'" placement="bottom">
            <button class="topbar-icon-btn theme-toggle-btn" @click="toggleTheme">
              <el-icon v-if="isDark"><Sunny /></el-icon>
              <el-icon v-else><Moon /></el-icon>
            </button>
          </el-tooltip>

          <!-- 用户头像与下拉菜单 -->
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-profile-btn">
              <div class="avatar-circle">
                {{ (currentUser.nickname || currentUser.username || 'U').charAt(0).toUpperCase() }}
              </div>
              <div class="user-meta">
                <span class="user-name">{{ currentUser.nickname || currentUser.username || '租户用户' }}</span>
                <span class="user-sub">{{ tenantInfo.tenantCode || 'tenant' }}</span>
              </div>
              <el-icon class="arrow"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="profile-dropdown-menu">
                <el-dropdown-item command="usage">
                  <el-icon><PieChart /></el-icon>用量中心
                </el-dropdown-item>
                <el-dropdown-item command="apikey">
                  <el-icon><Key /></el-icon>我的 API Key
                </el-dropdown-item>
                <el-dropdown-item divided command="logout" class="logout-item">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 核心页面视图区域 -->
      <main class="platform-content-view">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useDark} from '@vueuse/core'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  CaretBottom,
  ChatDotRound,
  Coin,
  Connection,
  Cpu,
  Document,
  FolderOpened,
  Key,
  Moon,
  Odometer,
  PieChart,
  Sunny,
  SwitchButton,
  TopRight,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import usePlatformUserStore from '@/store/modules/platformUser'

const route = useRoute()
const router = useRouter()
const platformUserStore = usePlatformUserStore()

const isDark = useDark({
  storageKey: 'polaris-theme-appearance',
  valueDark: 'dark',
  valueLight: 'light'
})

function toggleTheme() {
  isDark.value = !isDark.value
  if (isDark.value) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

const currentUser = computed(() => platformUserStore.user || {})
const tenantInfo = computed(() => platformUserStore.tenant || {})

// 挂载时自动拉取最新的租户与用户信息
onMounted(() => {
  if (platformUserStore.token) {
    platformUserStore.getInfo().catch(err => {
      console.warn('获取中台租户信息异常:', err)
    })
  }
})

const currentRouteTitle = computed(() => {
  return route.meta?.title || '中台页面'
})

function isCurrent(path) {
  return route.path === path
}

function formatNumber(num) {
  if (num === null || num === undefined) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return String(num)
}

function handleCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出北辰中台控制台吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定退出',
      cancelButtonText: '取消'
    }).then(() => {
      platformUserStore.logOut().then(() => {
        ElMessage.success('已安全退出')
        router.push('/platform/login')
      })
    })
  } else if (cmd === 'usage') {
    router.push('/platform/console/usage')
  } else if (cmd === 'apikey') {
    router.push('/platform/console/apikey')
  }
}
</script>

<style lang="scss" scoped>
.platform-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: #f8fafc;
  color: #0f172a;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  transition: background-color 0.3s, color 0.3s;

  &.is-dark {
    background-color: #090d16;
    color: #f1f5f9;
  }
}

/* ================= 侧边栏 ================= */
.platform-sidebar {
  width: 260px;
  min-width: 260px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.02);
  z-index: 20;
  transition: all 0.3s;

  .is-dark & {
    background: #0f172a;
    border-right-color: #1e293b;
    box-shadow: 2px 0 20px rgba(0, 0, 0, 0.4);
  }
}

.brand-header {
  height: 70px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f1f5f9;

  .is-dark & {
    border-bottom-color: #1e293b;
  }

  .logo-wrapper {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 10px rgba(79, 70, 229, 0.3);

    .brand-logo {
      width: 24px;
      height: 24px;
    }
  }

  .brand-title {
    font-size: 16px;
    font-weight: 700;
    letter-spacing: -0.3px;
    color: #0f172a;

    .is-dark & {
      color: #f8fafc;
    }
  }

  .brand-subtitle {
    font-size: 11px;
    color: #64748b;
    font-weight: 500;
  }
}

.tenant-identity-card {
  margin: 14px 16px 8px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;

  .is-dark & {
    background: rgba(30, 41, 59, 0.6);
    border-color: rgba(255, 255, 255, 0.08);
  }

  .tenant-top {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 4px;

    .tenant-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: #10b981;
      box-shadow: 0 0 6px #10b981;
    }

    .tenant-name {
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      .is-dark & {
        color: #e2e8f0;
      }
    }

    .tenant-role-tag {
      font-size: 10px;
      font-weight: 700;
      padding: 1px 6px;
      border-radius: 4px;
      background: rgba(99, 102, 241, 0.1);
      color: #6366f1;

      .is-dark & {
        background: rgba(129, 140, 248, 0.2);
        color: #a5b4fc;
      }
    }
  }

  .tenant-code-row {
    font-size: 11px;
    color: #64748b;
    display: flex;
    align-items: center;
    gap: 6px;

    .code-val {
      font-family: 'JetBrains Mono', Consolas, monospace;
      font-weight: 600;
      color: #4f46e5;

      .is-dark & {
        color: #818cf8;
      }
    }
  }
}

.menu-container {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }
}

.menu-group-title {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  padding: 12px 12px 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  text-decoration: none;
  margin-bottom: 3px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  .nav-icon {
    font-size: 16px;
    color: #64748b;
    transition: transform 0.2s, color 0.2s;
  }

  &:hover {
    background-color: #f1f5f9;
    color: #0f172a;

    .nav-icon {
      color: #4f46e5;
      transform: scale(1.1);
    }
  }

  &.active {
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.12) 0%, rgba(79, 70, 229, 0.06) 100%);
    color: #4f46e5;
    font-weight: 700;

    .nav-icon {
      color: #4f46e5;
    }
  }

  .is-dark & {
    color: #94a3b8;

    &:hover {
      background-color: rgba(255, 255, 255, 0.05);
      color: #f8fafc;

      .nav-icon {
        color: #818cf8;
      }
    }

    &.active {
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(79, 70, 229, 0.1) 100%);
      color: #818cf8;

      .nav-icon {
        color: #818cf8;
      }
    }
  }
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid #f1f5f9;

  .is-dark & {
    border-top-color: #1e293b;
  }
}

.doc-link-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.06) 0%, rgba(79, 70, 229, 0.02) 100%);
  border: 1px dashed rgba(99, 102, 241, 0.3);
  text-decoration: none;
  transition: all 0.2s;

  &:hover {
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.12) 0%, rgba(79, 70, 229, 0.05) 100%);
    border-color: #6366f1;
    transform: translateY(-1px);
  }

  .doc-card-left {
    display: flex;
    align-items: center;
    gap: 10px;

    .doc-icon {
      font-size: 18px;
      color: #4f46e5;
    }

    .doc-title {
      font-size: 12px;
      font-weight: 700;
      color: #1e293b;
    }

    .doc-desc {
      font-size: 10px;
      color: #64748b;
    }
  }

  .arrow-icon {
    font-size: 14px;
    color: #4f46e5;
  }

  .is-dark & {
    background: rgba(30, 41, 59, 0.4);
    border-color: rgba(129, 140, 248, 0.3);

    .doc-title {
      color: #e2e8f0;
    }
    .doc-icon,
    .arrow-icon {
      color: #818cf8;
    }
  }
}

/* ================= 主工作区 ================= */
.platform-main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f8fafc;

  .is-dark & {
    background-color: #090d16;
  }
}

.platform-topbar {
  height: 64px;
  min-height: 64px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 10;

  .is-dark & {
    background: rgba(15, 23, 42, 0.8);
    border-bottom-color: #1e293b;
  }
}

.breadcrumb-trail {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;

  .crumb-root {
    color: #64748b;
    font-weight: 500;
  }

  .crumb-sep {
    color: #cbd5e1;
  }

  .crumb-current {
    font-weight: 700;
    color: #0f172a;

    .is-dark & {
      color: #f8fafc;
    }
  }
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.quota-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px;
  border-radius: 30px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #6366f1;
    transform: scale(1.02);
  }

  .quota-icon-box {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: #fef3c7;
    color: #d97706;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
  }

  .quota-info {
    display: flex;
    flex-direction: column;

    .quota-label {
      font-size: 10px;
      color: #64748b;
      font-weight: 600;
      text-transform: uppercase;
    }

    .quota-nums {
      font-size: 12px;
      font-family: 'JetBrains Mono', Consolas, monospace;
      color: #475569;

      b {
        color: #4f46e5;
        font-weight: 700;
      }
    }
  }

  .is-dark & {
    background: #1e293b;
    border-color: rgba(255, 255, 255, 0.08);

    .quota-icon-box {
      background: rgba(245, 158, 11, 0.2);
      color: #fbbf24;
    }
    .quota-nums {
      color: #94a3b8;
      b { color: #818cf8; }
    }
  }
}

.topbar-icon-btn {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  color: #475569;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  text-decoration: none;
  font-size: 16px;
  transition: all 0.2s;

  &:hover {
    background-color: #f1f5f9;
    color: #4f46e5;
    border-color: rgba(79, 70, 229, 0.3);
  }

  .is-dark & {
    background: #1e293b;
    border-color: rgba(255, 255, 255, 0.08);
    color: #cbd5e1;

    &:hover {
      background-color: rgba(255, 255, 255, 0.08);
      color: #818cf8;
    }
  }
}

.user-profile-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 12px 5px 5px;
  border-radius: 9999px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    border-color: rgba(99, 102, 241, 0.4);
    box-shadow: 0 2px 8px rgba(99, 102, 241, 0.08);
    transform: translateY(-1px);
  }

  .avatar-circle {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
    color: #ffffff;
    font-size: 13px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 2px 4px rgba(79, 70, 229, 0.2);
    flex-shrink: 0;
  }

  .user-meta {
    display: flex;
    flex-direction: column;
    text-align: left;
    gap: 3px; /* 舒适的上下行间距 */

    .user-name {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
      line-height: 1.25;
      letter-spacing: 0.1px;
    }

    .user-sub {
      font-size: 11px;
      font-family: 'JetBrains Mono', Consolas, monospace;
      color: #64748b;
      line-height: 1.2;
      letter-spacing: 0.2px;
    }
  }

  .arrow {
    font-size: 12px;
    color: #94a3b8;
    margin-left: 2px;
    transition: transform 0.2s;
  }

  .is-dark & {
    background: #1e293b;
    border-color: rgba(255, 255, 255, 0.08);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);

    .user-name { color: #f8fafc; }
    .user-sub { color: #94a3b8; }
    .arrow { color: #64748b; }

    &:hover {
      border-color: rgba(129, 140, 248, 0.4);
      background: #243048;
    }
  }
}

.platform-content-view {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  position: relative;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.12);
    border-radius: 6px;
  }
}
</style>
