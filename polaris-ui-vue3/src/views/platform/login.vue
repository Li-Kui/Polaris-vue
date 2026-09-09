<template>
  <div :class="['platform-login-container', `theme-${activeTheme}`]">
    <div class="ambient-layer" aria-hidden="true"></div>

    <div class="theme-switcher" role="group" aria-label="登录页主题">
      <button
        type="button"
        :class="['theme-option', { active: activeTheme === 'light' }]"
        :aria-pressed="activeTheme === 'light'"
        @click="setTheme('light')"
      >
        极光
      </button>
      <button
        type="button"
        :class="['theme-option', { active: activeTheme === 'dark' }]"
        :aria-pressed="activeTheme === 'dark'"
        @click="setTheme('dark')"
      >
        深空
      </button>
    </div>

    <main class="platform-login-shell">
      <section class="platform-intro" aria-labelledby="platform-brand-title">
        <div class="brand-lockup">
          <img src="@/assets/logo/logo.png" alt="" class="brand-logo" />
          <span>POLARIS OS</span>
        </div>

        <div class="intro-copy">
          <div class="intro-eyebrow">POLARIS OPEN PLATFORM</div>
          <h1 id="platform-brand-title">北辰 AI 开放中台</h1>
          <h2>租户级 AI 能力运营与交付</h2>
          <p>统一使用智能体、知识库、工作流、模型与开放 API，让团队安全、高效地构建并管理企业智能应用。</p>
          <div class="capability-list" aria-label="平台能力">
            <span>智能体</span>
            <span>知识库</span>
            <span>工作流</span>
            <span>开放 API</span>
          </div>
        </div>

        <div class="system-status">
          <span class="status-dot" aria-hidden="true"></span>
          中台服务正常
        </div>
      </section>

      <section class="platform-form-panel" aria-labelledby="platform-login-title">
        <div class="login-glass-card">
          <div class="login-header">
            <div class="console-badge">POLARIS PLATFORM CONSOLE</div>
            <h2 id="platform-login-title" class="title">中台登录</h2>
            <p class="subtitle">面向租户管理员与业务成员</p>
          </div>

          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form" size="large">
            <el-form-item prop="tenantCode">
              <label class="field-label" for="platform-tenant-code">租户编码</label>
              <el-input
                id="platform-tenant-code"
                v-model="loginForm.tenantCode"
                placeholder="请输入租户编码"
                prefix-icon="OfficeBuilding"
                clearable
              />
            </el-form-item>
            <el-form-item prop="username">
              <label class="field-label" for="platform-username">用户账号</label>
              <el-input
                id="platform-username"
                v-model="loginForm.username"
                placeholder="请输入用户账号"
                prefix-icon="User"
                clearable
              />
            </el-form-item>
            <el-form-item prop="password">
              <label class="field-label" for="platform-password">登录密码</label>
              <el-input
                id="platform-password"
                v-model="loginForm.password"
                type="password"
                placeholder="请输入登录密码"
                prefix-icon="Lock"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-button :loading="loading" type="primary" class="submit-btn" @click="handleLogin">
              进入中台控制台
            </el-button>
          </el-form>

          <div class="login-footer">
            <span>系统管理员？<router-link to="/login">返回管理端</router-link></span>
            <span class="divider" aria-hidden="true">/</span>
            <a href="/platform/docs/index.html" target="_blank" rel="noopener noreferrer">开发者文档</a>
          </div>
        </div>
      </section>
    </main>

    <div class="copyright">Copyright © 2018-2026 Polaris All Rights Reserved.</div>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import usePlatformUserStore from '@/store/modules/platformUser'

const router = useRouter()
const route = useRoute()
const platformUserStore = usePlatformUserStore()

const loginFormRef = ref(null)
const loading = ref(false)
const activeTheme = ref(localStorage.getItem('polaris.theme.variable') || 'dark')

const loginForm = reactive({
  tenantCode: '',
  username: '',
  password: ''
})

const loginRules = {
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  applyGlobalTheme(activeTheme.value)
})

function applyGlobalTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
  localStorage.setItem('polaris.theme.variable', theme)
}

function setTheme(theme) {
  if (activeTheme.value === theme) return
  activeTheme.value = theme
  applyGlobalTheme(theme)
}

function handleLogin() {
  loginFormRef.value.validate(valid => {
    if (valid) {
      loading.value = true
      platformUserStore.login(loginForm).then(() => {
        ElMessage.success('登录成功')
        const redirect = route.query.redirect || '/platform/console/dashboard'
        router.push(redirect)
      }).catch(() => {
        loading.value = false
      })
    }
  })
}
</script>

<style lang="scss" scoped>
.platform-login-container {
  --accent: #4f46e5;
  --accent-strong: #4338ca;
  --accent-contrast: #ffffff;
  --accent-soft: rgba(79, 70, 229, 0.12);
  --page-bg: #f4f7fb;
  --intro-bg: linear-gradient(145deg, rgba(237, 242, 255, 0.98), rgba(238, 249, 252, 0.92));
  --panel-bg: rgba(250, 252, 255, 0.96);
  --card-bg: rgba(255, 255, 255, 0.72);
  --input-bg: #ffffff;
  --ink: #0f172a;
  --ink-muted: #64748b;
  --line: rgba(15, 23, 42, 0.09);
  --soft-line: rgba(79, 70, 229, 0.18);
  --card-shadow: 0 24px 64px rgba(15, 23, 42, 0.08);

  min-height: 100vh;
  width: 100%;
  background: var(--page-bg);
  color: var(--ink);
  position: relative;
  overflow: hidden;
  transition: background-color 0.35s ease, color 0.35s ease;

  &.theme-dark {
    --accent: #38bdf8;
    --accent-strong: #7dd3fc;
    --accent-contrast: #0f172a;
    --accent-soft: rgba(56, 189, 248, 0.12);
    --page-bg: #070b14;
    --intro-bg: linear-gradient(145deg, rgba(11, 20, 38, 0.98), rgba(13, 27, 44, 0.94));
    --panel-bg: rgba(8, 12, 22, 0.96);
    --card-bg: rgba(15, 23, 42, 0.58);
    --input-bg: rgba(2, 6, 23, 0.72);
    --ink: #f8fafc;
    --ink-muted: #94a3b8;
    --line: rgba(255, 255, 255, 0.09);
    --soft-line: rgba(56, 189, 248, 0.2);
    --card-shadow: 0 30px 80px rgba(0, 0, 0, 0.42);
  }
}

.ambient-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 12% 12%, var(--accent-soft), transparent 32%),
    radial-gradient(circle at 86% 76%, rgba(14, 165, 233, 0.08), transparent 34%);
}

.platform-login-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(420px, 1.05fr) minmax(520px, 1fr);
  min-height: 100vh;
}

.platform-intro,
.platform-form-panel {
  padding: 56px clamp(48px, 6vw, 96px);
}

.platform-intro {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: var(--intro-bg);
  border-right: 1px solid var(--line);
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.brand-logo {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.intro-copy {
  max-width: 620px;

  .intro-eyebrow {
    margin-bottom: 18px;
    color: var(--accent);
    font-family: monospace;
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.14em;
  }

  h1 {
    margin: 0 0 10px;
    font-size: clamp(36px, 4vw, 58px);
    line-height: 1.08;
    letter-spacing: -0.03em;
  }

  h2 {
    margin: 0 0 24px;
    color: var(--accent);
    font-size: clamp(22px, 2.2vw, 34px);
    font-weight: 750;
    line-height: 1.2;
  }

  p {
    max-width: 560px;
    margin: 0;
    color: var(--ink-muted);
    font-size: 15px;
    line-height: 1.9;
  }
}

.capability-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;

  span {
    padding: 7px 12px;
    border: 1px solid var(--soft-line);
    border-radius: 999px;
    background: var(--accent-soft);
    color: var(--accent);
    font-size: 12px;
    font-weight: 700;
  }
}

.system-status {
  display: flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  color: #10b981;
  font-size: 12px;
  font-weight: 700;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 10px rgba(16, 185, 129, 0.6);
}

.platform-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--panel-bg);
}

.theme-switcher {
  position: fixed;
  top: 28px;
  right: 32px;
  z-index: 10;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  width: 146px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--card-bg);
  box-shadow: 0 8px 26px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(18px);
}

.theme-option {
  min-height: 32px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--ink-muted);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.2s, color 0.2s, box-shadow 0.2s;

  &.active {
    background: var(--accent);
    color: var(--accent-contrast);
    box-shadow: 0 4px 12px var(--accent-soft);
  }

  &:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }
}

.login-glass-card {
  width: min(100%, 460px);
  padding: 46px 42px;
  background: var(--card-bg);
  backdrop-filter: blur(32px);
  border: 1px solid var(--line);
  border-radius: 24px;
  box-shadow: var(--card-shadow);
  position: relative;
}

.login-header {
  margin-bottom: 30px;

  .console-badge {
    display: inline-flex;
    margin-bottom: 12px;
    padding: 4px 8px;
    border-radius: 5px;
    background: var(--accent-soft);
    color: var(--accent);
    font-family: monospace;
    font-size: 9px;
    font-weight: 800;
    letter-spacing: 0.05em;
  }

  .title {
    margin: 0 0 7px;
    color: var(--ink);
    font-size: 24px;
    font-weight: 850;
  }

  .subtitle {
    margin: 0;
    color: var(--ink-muted);
    font-size: 13px;
  }
}

.login-form :deep(.el-form-item) {
  display: block;
  margin-bottom: 22px;
}

.field-label {
  display: block;
  margin-bottom: 9px;
  color: var(--ink-muted);
  font-size: 12px;
  font-weight: 700;
}

:deep(.el-input__wrapper) {
  height: 48px;
  padding: 0 15px;
  background: var(--input-bg) !important;
  border: 1px solid var(--line) !important;
  border-radius: 12px !important;
  box-shadow: none !important;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover,
  &.is-focus {
    border-color: var(--accent) !important;
    box-shadow: 0 0 0 3px var(--accent-soft) !important;
  }
}

:deep(.el-input__inner) {
  color: var(--ink) !important;
  font-size: 14px;

  &::placeholder { color: var(--ink-muted); }
}

:deep(.el-input__prefix-inner) { color: var(--accent); }

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  border: none;
  color: var(--accent-contrast);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-top: 4px;
  box-shadow: 0 10px 22px -8px var(--accent);
  transition: all 0.3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 28px -8px var(--accent);
  }

  &:focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
}

.login-footer {
  margin-top: 28px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--ink-muted);

  a {
    color: var(--accent);
    text-decoration: none;
    transition: color 0.2s;

    &:hover {
      color: var(--accent-strong);
      text-decoration: underline;
    }
  }

  .divider { opacity: 0.45; }
}

.copyright {
  position: fixed;
  z-index: 2;
  right: 28px;
  bottom: 20px;
  color: var(--ink-muted);
  font-size: 11px;
  opacity: 0.7;
}

@media (max-width: 980px) {
  .platform-login-shell { grid-template-columns: 1fr; }
  .platform-intro { display: none; }
  .platform-form-panel { min-height: 100vh; padding: 84px 24px 64px; }
}

@media (max-width: 520px) {
  .theme-switcher { top: 18px; right: 18px; }
  .login-glass-card { padding: 36px 24px; border-radius: 20px; }
  .login-footer { flex-wrap: wrap; row-gap: 8px; }
  .copyright { right: 18px; bottom: 14px; left: 18px; text-align: center; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
