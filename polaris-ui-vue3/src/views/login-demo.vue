<!-- AI 智能登录 3 种全新设计方案演示 Demo 页面 -->
<template>
  <div :class="['login-demo', `scheme-${activeScheme}`, activeTheme]" :style="globalStyle">
    <!-- 动态背景层 (Transition 切换) -->
    <transition name="scheme-fade" mode="out-in">
      <component
        :is="schemeComponents[activeScheme]"
        :key="activeScheme"
        :theme="activeTheme"
        :project-title="projectTitle"
        class="background-layer"
      />
    </transition>

    <!-- 成功验证全息光幕 -->
    <transition name="hologram-fade">
      <div v-if="loginSuccess" class="success-hologram-overlay">
        <div class="hologram-card">
          <div class="holo-glitch-text">ACCESS GRANTED</div>
          <div class="holo-sub">{{ projectTitle.toUpperCase() }} IDENTITY VERIFIED</div>
          <div class="holo-scanner-line"></div>
          <div class="holo-radar-circle"></div>
          <div class="holo-stats">
            <div>INTELLIGENCE SYSTEM: {{ projectTitle.toUpperCase() }}</div>
            <div>COGNITIVE MATRIX: ALIGNED</div>
            <div>WELCOME BACK, OPERATOR</div>
          </div>
        </div>
      </div>
    </transition>

    <!-- 登录主体区域 -->
    <div class="login-content-wrapper" v-if="!loginSuccess">
      <!-- 动态交互登录卡片 -->
      <div
        class="demo-card-outer"
        ref="cardRef"
        :style="card3DStyle"
        @mousemove="handleMouseMove"
        @mouseleave="handleMouseLeave"
      >
        <!-- 方案一的流光边框层 -->
        <div v-if="activeScheme === 'awakening'" class="rainbow-glow-border"></div>

        <div :class="['demo-card-inner', `card-${activeScheme}`, { 'is-focused': isInputFocused }]">
          <!-- 方案二的 HUD 直角边角 -->
          <template v-if="activeScheme === 'singularity'">
            <div class="hud-angle angle-tl"></div>
            <div class="hud-angle angle-tr"></div>
            <div class="hud-angle angle-bl"></div>
            <div class="hud-angle angle-br"></div>
          </template>

          <el-form ref="formRef" :model="loginForm" :rules="loginRules" class="login-form">
            <div class="form-header">
              <!-- 生物扫描线容器 -->
              <div class="biometric-scanner-wrap">
                <svg class="ai-logo-symbol" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <linearGradient id="polaris-grad" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stop-color="#ff007f" />
                      <stop offset="100%" stop-color="#7f00ff" />
                    </linearGradient>
                  </defs>
                  <g class="polaris-star-g">
                    <path d="M 50 0 Q 50 50 100 50 Q 50 50 50 100 Q 50 50 0 50 Q 50 50 50 0 Z" fill="url(#polaris-grad)" />
                  </g>
                </svg>
                <!-- 绿光激光扫描线 -->
                <div class="biometric-laser"></div>
              </div>
              <h1 class="title">{{ projectTitle }}</h1>
              <!-- 动态 AI 识辨日志状态 -->
              <div class="biometric-status-hud">
                <span class="hud-dot"></span>
                <span class="hud-log-text">{{ isInputFocused ? '[DECIPHER] DECRYPTING DATAFLOW...' : '[BIOMETRIC] SECURE SCAN ACTIVE' }}</span>
              </div>
            </div>

            <!-- 账号输入 -->
            <el-form-item prop="username" class="demo-form-item">
              <el-input
                v-model="loginForm.username"
                placeholder="安全令牌 / 账户名称"
                @focus="isInputFocused = true"
                @blur="isInputFocused = false"
              >
                <template #prefix>
                  <span class="prefix-icon">✦</span>
                </template>
              </el-input>
            </el-form-item>

            <!-- 密码输入 -->
            <el-form-item prop="password" class="demo-form-item">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="解密密钥 / 密码"
                show-password
                @focus="isInputFocused = true"
                @blur="isInputFocused = false"
                @keyup.enter="handleDemoLogin"
              >
                <template #prefix>
                  <span class="prefix-icon">🔒</span>
                </template>
              </el-input>
            </el-form-item>

            <div class="form-helper-row">
              <el-checkbox v-model="loginForm.rememberMe">自动同步记忆体</el-checkbox>
              <span class="secondary-action">量子注册</span>
            </div>

            <el-form-item class="form-submit-wrap">
              <el-button
                :loading="submitting"
                class="submit-btn"
                type="primary"
                @click.prevent="handleDemoLogin"
              >
                <span v-if="!submitting">建立神经连接</span>
                <span v-else>正在同步认知网络...</span>
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 方案三的反重力悬浮影子效果 -->
        <div v-if="activeScheme === 'horizon'" class="floating-shadow"></div>
      </div>
    </div>

    <!-- AI 视觉控制中枢 (AI Style Control Hub) -->
    <div class="style-control-hub">
      <div class="hub-header">
        <span class="hub-title">AI CONTROL CENTER</span>
        <span class="hub-version">v4.0-BETA</span>
      </div>

      <!-- 方案切换 -->
      <div class="hub-section">
        <label class="hub-label">视觉设计方案</label>
        <div class="scheme-options">
          <button
            v-for="s in schemes"
            :key="s.value"
            :class="['hub-btn', { active: activeScheme === s.value }]"
            @click="activeScheme = s.value"
          >
            <span class="btn-indicator"></span>
            <div class="btn-content">
              <span class="scheme-name">{{ s.label }}</span>
              <span class="scheme-desc">{{ s.desc }}</span>
            </div>
          </button>
        </div>
      </div>

      <!-- 主题切换 -->
      <div class="hub-section">
        <label class="hub-label">主题风格切换</label>
        <div class="theme-switch-row">
          <button
            :class="['theme-btn', { active: activeTheme === 'light' }]"
            @click="setTheme('light')"
          >
            ☀️ 极光亮色
          </button>
          <button
            :class="['theme-btn', { active: activeTheme === 'dark' }]"
            @click="setTheme('dark')"
          >
            🌙 深空暗色
          </button>
        </div>
      </div>

      <!-- 动效演示操作 -->
      <div class="hub-section">
        <label class="hub-label">认知验证测试</label>
        <el-button class="action-trigger-btn" @click="handleDemoLogin">
          ⚡ 模拟登录验证成功动效
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import NewSchemeAwakening from './login/schemes/NewSchemeAwakening.vue'
import NewSchemeSingularity from './login/schemes/NewSchemeSingularity.vue'
import NewSchemeHorizon from './login/schemes/NewSchemeHorizon.vue'

const schemeComponents = {
  awakening: NewSchemeAwakening,
  singularity: NewSchemeSingularity,
  horizon: NewSchemeHorizon
}

const schemes = [
  { value: 'awakening', label: '意识觉醒', desc: '3D 旋转全息球 · 七彩流光溢彩' },
  { value: 'singularity', label: '引力坍缩', desc: '中心吸引粒子风暴 · 扭曲重力' },
  { value: 'horizon', label: '赛博视界', desc: '透视延伸 3D 地面 · 悬浮超立方体' }
]

// 状态控制
const projectTitle = import.meta.env.VITE_APP_TITLE || 'Polaris Vue'
const activeScheme = ref('awakening')
const activeTheme = ref('dark')
const submitting = ref(false)
const loginSuccess = ref(false)
const isInputFocused = ref(false)

const loginForm = ref({
  username: 'admin',
  password: 'admin123',
  rememberMe: true
})

const loginRules = {
  username: [{ required: true, trigger: 'blur', message: '请输入量子账号' }],
  password: [{ required: true, trigger: 'blur', message: '请输入解密密钥' }]
}

// 全局鼠标视差变量
const globalStyle = computed(() => {
  return {
    '--mouse-x': mouseX.value,
    '--mouse-y': mouseY.value
  }
})

// 方案标题计算
const activeSchemeTitle = computed(() => {
  if (activeScheme.value === 'awakening') return 'Aura Awakening'
  if (activeScheme.value === 'singularity') return 'Singularity Core'
  return 'Horizon Explorer'
})

// 3D 鼠标悬停视差物理感应
const cardRef = ref(null)
const mouseX = ref(0)
const mouseY = ref(0)
const isHovering = ref(false)

const card3DStyle = computed(() => {
  // 方案三拥有悬浮与 3D 视差，方案一与二也能支持轻微的视差，这里对方案三提供最强的偏转角度
  if (!isHovering.value) {
    return {
      transform: activeScheme.value === 'horizon' ? 'translateY(-12px)' : 'none',
      transition: 'transform 0.5s ease, box-shadow 0.5s ease'
    }
  }
  const maxRotate = activeScheme.value === 'horizon' ? 12 : 6
  const rotateX = -mouseY.value * maxRotate
  const rotateY = mouseX.value * maxRotate
  return {
    transform: `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) ${activeScheme.value === 'horizon' ? 'translateY(-14px)' : ''}`,
    transition: 'transform 0.1s ease, box-shadow 0.3s ease'
  }
})

function handleMouseMove(e) {
  if (!cardRef.value) return
  isHovering.value = true
  const rect = cardRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left - rect.width / 2
  const y = e.clientY - rect.top - rect.height / 2
  mouseX.value = x / (rect.width / 2)
  mouseY.value = y / (rect.height / 2)
}

function handleMouseLeave() {
  isHovering.value = false
  mouseX.value = 0
  mouseY.value = 0
}

// 模拟登录成功动效
function handleDemoLogin() {
  submitting.value = true
  setTimeout(() => {
    submitting.value = false
    loginSuccess.value = true
    
    // 3秒后还原重置，方便持续预览
    setTimeout(() => {
      loginSuccess.value = false
    }, 4500)
  }, 1200)
}

// 主题切换逻辑
function setTheme(theme) {
  activeTheme.value = theme
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

// 初始化时应用主题
onMounted(() => {
  setTheme(activeTheme.value)
})
</script>

<style lang="scss">
// 全局的过度动效和基本变量 (仅在 demo 路由生效)
.login-demo {
  --trans-slow: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  --trans-fast: all 0.2s cubic-bezier(0.2, 0.7, 0.1, 1);

  display: flex;
  justify-content: center;
  align-items: center;
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  user-select: none;
}

// 页面淡入淡出切换
.scheme-fade-enter-active,
.scheme-fade-leave-active {
  transition: opacity 0.6s ease;
}
.scheme-fade-enter-from,
.scheme-fade-leave-to {
  opacity: 0;
}
</style>

<style lang="scss" scoped>
.background-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
}

// ===== 登录卡片布局 =====
.login-content-wrapper {
  position: relative;
  z-index: 10;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  max-width: 1200px;
  padding: 0 40px;
}

// 星轨 & 赛博视界方案中，卡片居右布局
.scheme-singularity .login-content-wrapper,
.scheme-horizon .login-content-wrapper {
  justify-content: flex-end;
  padding-right: 15%;
  @media (max-width: 1024px) {
    justify-content: center;
    padding-right: 40px;
  }
}

.demo-card-outer {
  position: relative;
  width: 100%;
  max-width: 400px;
  transform-style: preserve-3d;
}

// ===== 方案一卡片：流光边框 + 陶瓷高透毛玻璃 =====
.rainbow-glow-border {
  position: absolute;
  inset: -2px;
  border-radius: 24px;
  background: linear-gradient(90deg, #ff007f, #7f00ff, #00f0ff, #ff007f);
  background-size: 400% 400%;
  z-index: 1;
  animation: rainbow-flow 8s linear infinite;
  filter: blur(2px);
  opacity: 0.8;
}

@keyframes rainbow-flow {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.card-awakening {
  border-radius: 22px;
  z-index: 2;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.2);
  
  .light & {
    background: rgba(255, 255, 255, 0.48);
    border: 1px solid rgba(255, 255, 255, 0.25);
    backdrop-filter: blur(25px);
    --btn-bg: linear-gradient(135deg, #6366f1, #a855f7);
    --btn-hover: linear-gradient(135deg, #4f46e5, #9333ea);
    --btn-border-pure: #6366f1;
    --text-main: #0f172a;
    --text-sub: #475569;
    --input-bg: transparent;
    --input-border: rgba(99, 102, 241, 0.15);
  }

  .dark & {
    background: rgba(10, 8, 20, 0.35);
    border: 1px solid rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(25px);
    --btn-bg: linear-gradient(135deg, #818cf8, #c084fc);
    --btn-hover: linear-gradient(135deg, #6366f1, #a855f7);
    --btn-border-pure: #818cf8;
    --text-main: #f8fafc;
    --text-sub: #94a3b8;
    --input-bg: transparent;
    --input-border: rgba(129, 140, 248, 0.15);
  }
}

// ===== 方案二卡片：直角数码边角 + 奇点边框 =====
.hud-angle {
  position: absolute;
  width: 16px;
  height: 16px;
  border: 2px solid var(--accent, #f97316);
  z-index: 5;
  transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
  
  &.angle-tl { top: -2px; left: -2px; border-right: none; border-bottom: none; }
  &.angle-tr { top: -2px; right: -2px; border-left: none; border-bottom: none; }
  &.angle-bl { bottom: -2px; left: -2px; border-right: none; border-top: none; }
  &.angle-br { bottom: -2px; right: -2px; border-left: none; border-top: none; }
}

// 输入框聚焦时，直角缩进并闪烁
.is-focused .hud-angle {
  width: 12px;
  height: 12px;
  border-color: #f43f5e;
  &.angle-tl { top: 4px; left: 4px; }
  &.angle-tr { top: 4px; right: 4px; }
  &.angle-bl { bottom: 4px; left: 4px; }
  &.angle-br { bottom: 4px; right: 4px; }
}

.card-singularity {
  border-radius: 12px;
  box-shadow: 0 30px 70px rgba(0, 0, 0, 0.35);
  
  .light & {
    background: rgba(255, 255, 255, 0.45);
    border: 1px solid rgba(220, 95, 30, 0.3);
    --btn-bg: #ea580c;
    --btn-hover: #c2410c;
    --btn-border-pure: #ea580c;
    --text-main: #1c1917;
    --text-sub: #78716c;
    --input-bg: transparent;
    --input-border: rgba(220, 95, 30, 0.2);
  }

  .dark & {
    background: rgba(10, 5, 3, 0.35);
    border: 1px solid rgba(249, 115, 22, 0.25);
    --btn-bg: #f97316;
    --btn-hover: #ea580c;
    --btn-border-pure: #f97316;
    --text-main: #f5f5f4;
    --text-sub: #a8a29e;
    --input-bg: transparent;
    --input-border: rgba(249, 115, 22, 0.2);
    box-shadow: 0 0 40px rgba(249, 115, 22, 0.1);
  }
}

// ===== 方案三卡片：悬浮与阴影 + 极简未来派 =====
.card-horizon {
  border-radius: 16px;
  animation: levitate 4s ease-in-out infinite alternate;
  
  .light & {
    background: rgba(255, 255, 255, 0.45);
    border: 1px solid rgba(2, 132, 199, 0.2);
    box-shadow: 0 10px 40px rgba(2, 132, 199, 0.05);
    --btn-bg: #0284c7;
    --btn-hover: #0369a1;
    --btn-border-pure: #0284c7;
    --text-main: #0f172a;
    --text-sub: #64748b;
    --input-bg: transparent;
    --input-border: rgba(2, 132, 199, 0.2);
  }

  .dark & {
    background: rgba(3, 6, 15, 0.35);
    border: 1px solid rgba(236, 72, 153, 0.2);
    backdrop-filter: blur(20px);
    box-shadow: 0 0 30px rgba(236, 72, 153, 0.05);
    --btn-bg: #ec4899;
    --btn-hover: #db2777;
    --btn-border-pure: #ec4899;
    --text-main: #f1f5f9;
    --text-sub: #94a3b8;
    --input-bg: transparent;
    --input-border: rgba(236, 72, 153, 0.15);
  }
}

@keyframes levitate {
  0% { transform: translateY(0px); }
  100% { transform: translateY(-12px); }
}

// 悬浮影子的缩放与虚化
.floating-shadow {
  position: absolute;
  bottom: -35px;
  left: 10%;
  width: 80%;
  height: 12px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.15);
  filter: blur(8px);
  z-index: 0;
  animation: shadow-scale 4s ease-in-out infinite alternate;
  
  .dark & {
    background: rgba(0, 0, 0, 0.55);
  }
}

@keyframes shadow-scale {
  0% { transform: scale(1); opacity: 0.8; filter: blur(8px); }
  100% { transform: scale(0.8); opacity: 0.3; filter: blur(12px); }
}

// ===== 通用卡片内部样式 =====
.demo-card-inner {
  position: relative;
  padding: 40px;
  box-sizing: border-box;
  width: 100%;
}

.form-header {
  text-align: center;
  margin-bottom: 30px;
  
  .ai-logo-symbol {
    width: 44px;
    height: 44px;
    display: block;
    margin: 0 auto;
    filter: drop-shadow(0 0 8px rgba(127, 0, 255, 0.45));
    animation: polaris-star-breath 4s ease-in-out infinite alternate;
  }
  
  .polaris-star-g {
    transform-origin: 50px 50px;
    animation: rotate-logo 20s linear infinite;
  }
  
  // 生物激光器
  .biometric-scanner-wrap {
    position: relative;
    width: 90px;
    height: 90px;
    margin: 0 auto 12px;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
    border-radius: 50%;
  }

  .biometric-laser {
    position: absolute;
    left: 0;
    right: 0;
    height: 1.5px;
    background: linear-gradient(90deg, transparent, #22d3ee, #06b6d4, transparent);
    box-shadow: 0 0 10px #22d3ee, 0 0 20px #06b6d4;
    animation: biometric-laser-scan 3.5s ease-in-out infinite;
    z-index: 5;
    pointer-events: none;
    
    .light & {
      background: linear-gradient(90deg, transparent, #6366f1, #8b5cf6, transparent);
      box-shadow: 0 0 8px #6366f1;
    }
  }

  @keyframes biometric-laser-scan {
    0%, 100% { top: 5%; opacity: 0.1; }
    30%, 70% { opacity: 1; }
    50% { top: 95%; opacity: 0.1; }
  }

  .biometric-status-hud {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-top: 14px;
    padding: 5px 12px;
    border-radius: 99px;
    background: rgba(0, 0, 0, 0.35);
    border: 1px solid rgba(255, 255, 255, 0.05);
    box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.2);
    
    .light & {
      background: rgba(0, 0, 0, 0.03);
      border-color: rgba(0, 0, 0, 0.05);
    }
  }

  .hud-dot {
    width: 6px;
    height: 6px;
    background-color: #22d3ee;
    border-radius: 50%;
    box-shadow: 0 0 8px #22d3ee;
    animation: pulse-dot-flash 1.5s infinite alternate;
    
    .light & {
      background-color: #6366f1;
      box-shadow: 0 0 6px #6366f1;
    }
  }

  @keyframes pulse-dot-flash {
    0% { opacity: 0.3; transform: scale(0.8); }
    100% { opacity: 1; transform: scale(1.2); }
  }

  .hud-log-text {
    font-family: var(--font-mono);
    font-size: 9px;
    font-weight: bold;
    color: var(--text-sub);
    letter-spacing: 0.08em;
  }
  
  .title {
    font-size: 24px;
    font-weight: 700;
    margin: 8px 0 0;
    color: var(--text-main);
    letter-spacing: -0.02em;
  }
  
  .subtitle {
    font-size: 11px;
    margin: 6px 0 0;
    color: var(--text-sub);
    letter-spacing: 0.15em;
    font-family: monospace;
  }
}

@keyframes rotate-logo {
  to { transform: rotate(360deg); }
}

@keyframes polaris-star-breath {
  0% { transform: scale(0.9); filter: drop-shadow(0 0 4px rgba(127, 0, 255, 0.35)); }
  100% { transform: scale(1.11); filter: drop-shadow(0 0 14px rgba(255, 0, 127, 0.65)); }
}

.demo-form-item {
  margin-bottom: 24px;
  position: relative;
  
  :deep(.el-input__wrapper) {
    background-color: transparent !important;
    border: none !important;
    border-bottom: 1.5px solid var(--input-border, rgba(255, 255, 255, 0.15)) !important;
    box-shadow: none !important;
    border-radius: 0px !important;
    height: 44px;
    padding: 0 4px !important;
    transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
    
    &:hover {
      border-bottom-color: var(--btn-bg) !important;
    }
    
    &.is-focus {
      border-bottom-color: var(--btn-bg) !important;
      box-shadow: 0 2px 8px rgba(129, 140, 248, 0.25) !important;
    }
  }
  
  :deep(.el-input__inner) {
    color: var(--text-main);
    font-size: 13px;
    font-family: var(--font-mono, monospace);
  }
  
  .prefix-icon {
    font-size: 13px;
    color: var(--btn-bg);
    margin-right: 6px;
    opacity: 0.8;
  }
}

.form-helper-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
  font-size: 12px;
  color: var(--text-sub);
  
  :deep(.el-checkbox__label) {
    color: var(--text-sub) !important;
    font-size: 12px;
  }
  
  .secondary-action {
    cursor: pointer;
    font-weight: 500;
    transition: color 0.2s;
    &:hover {
      color: var(--text-main);
    }
  }
}

.form-submit-wrap {
  margin-bottom: 0;
  
  .submit-btn {
    width: 100%;
    height: 48px;
    border-radius: 4px !important;
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0.1em;
    background: transparent !important;
    border: 1px solid var(--btn-border-pure, rgba(255, 255, 255, 0.2)) !important;
    color: var(--text-main) !important;
    position: relative;
    overflow: hidden;
    transition: all 0.4s cubic-bezier(0.2, 0.8, 0.2, 1);
    box-shadow: 0 0 10px rgba(0, 0, 0, 0.05);
    
    &::after {
      content: '';
      position: absolute;
      inset: -50%;
      background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
      transform: skewX(-20deg) translateX(-100%);
      transition: transform 0.6s ease;
    }
    
    &:hover {
      background: var(--btn-bg) !important;
      color: #ffffff !important;
      border-color: transparent !important;
      box-shadow: 0 0 20px var(--btn-border-pure, rgba(255, 255, 255, 0.35)) !important;
      
      &::after {
        transform: skewX(-20deg) translateX(200%);
      }
    }
    
    &:active {
      transform: scale(0.98);
    }
  }
}

// ===== AI 控制中枢样式 (浮动悬浮台) =====
.style-control-hub {
  position: fixed;
  left: 24px;
  bottom: 24px;
  width: 320px;
  padding: 24px;
  border-radius: 20px;
  z-index: 100;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  transition: var(--trans-slow);

  .light & {
    background: rgba(255, 255, 255, 0.85);
    border: 1px solid rgba(0, 0, 0, 0.06);
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.08);
  }

  .dark & {
    background: rgba(10, 10, 18, 0.85);
  }
  
  @media (max-width: 768px) {
    left: 12px;
    right: 12px;
    bottom: 12px;
    width: auto;
  }
}

.hub-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 10px;
  
  .light & {
    border-bottom-color: rgba(0, 0, 0, 0.06);
  }

  .hub-title {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.1em;
    color: var(--text-main);
  }
  
  .hub-version {
    font-family: monospace;
    font-size: 9px;
    color: var(--text-sub);
    border: 1px solid var(--text-sub);
    padding: 1px 4px;
    border-radius: 3px;
  }
}

.hub-section {
  margin-bottom: 16px;
  
  &:last-child {
    margin-bottom: 0;
  }
}

.hub-label {
  display: block;
  font-size: 10px;
  font-weight: 600;
  color: var(--text-sub);
  margin-bottom: 8px;
  letter-spacing: 0.05em;
}

.scheme-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hub-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: transparent;
  cursor: pointer;
  width: 100%;
  text-align: left;
  transition: var(--trans-fast);
  color: var(--text-main);

  .light & {
    border-color: rgba(0, 0, 0, 0.06);
  }

  &:hover {
    background: rgba(255, 255, 255, 0.05);
    .light & {
      background: rgba(0, 0, 0, 0.02);
    }
  }

  &.active {
    border-color: #818cf8;
    background: rgba(129, 140, 248, 0.08);
    .btn-indicator {
      background-color: #818cf8;
      box-shadow: 0 0 8px #818cf8;
    }
  }
}

.btn-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
  transition: var(--trans-fast);
}

.btn-content {
  display: flex;
  flex-direction: column;
  
  .scheme-name {
    font-size: 12px;
    font-weight: 600;
  }
  
  .scheme-desc {
    font-size: 9px;
    color: var(--text-sub);
    margin-top: 2px;
  }
}

.theme-switch-row {
  display: flex;
  gap: 8px;
}

.theme-btn {
  flex: 1;
  padding: 8px 0;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: transparent;
  font-size: 11px;
  cursor: pointer;
  transition: var(--trans-fast);
  color: var(--text-main);

  .light & {
    border-color: rgba(0, 0, 0, 0.06);
  }

  &:hover {
    background: rgba(255, 255, 255, 0.05);
    .light & {
      background: rgba(0, 0, 0, 0.02);
    }
  }

  &.active {
    border-color: #818cf8;
    background: rgba(129, 140, 248, 0.08);
  }
}

.action-trigger-btn {
  width: 100%;
  height: 38px;
  border-radius: 8px;
  border: 1px dashed #818cf8 !important;
  background: transparent !important;
  color: #818cf8 !important;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--trans-fast);
  
  &:hover {
    background: rgba(129, 140, 248, 0.1) !important;
    transform: translateY(-1px);
  }
}

// ===== 登录验证成功全息光幕 overlay =====
.success-hologram-overlay {
  position: absolute;
  inset: 0;
  background-color: rgba(3, 2, 8, 0.9);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
  
  .light & {
    background-color: rgba(240, 244, 250, 0.95);
  }
}

.hologram-card {
  position: relative;
  padding: 40px;
  border: 1px solid #10b981;
  background: rgba(16, 185, 129, 0.06);
  border-radius: 20px;
  text-align: center;
  width: 100%;
  max-width: 460px;
  box-shadow: 0 0 50px rgba(16, 185, 129, 0.2);
  overflow: hidden;
  animation: holo-show 0.6s cubic-bezier(0.19, 1, 0.22, 1) both;

  .light & {
    border-color: #059669;
    background: rgba(5, 150, 105, 0.05);
    box-shadow: 0 0 40px rgba(5, 150, 105, 0.1);
  }
}

@keyframes holo-show {
  0% { transform: scale(0.8) translateY(30px); opacity: 0; }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}

.holo-glitch-text {
  font-size: 32px;
  font-weight: 900;
  color: #10b981;
  letter-spacing: 0.15em;
  text-shadow: 0 0 10px rgba(16, 185, 129, 0.6);
  
  .light & {
    color: #059669;
  }
}

.holo-sub {
  font-size: 11px;
  font-family: monospace;
  color: var(--text-sub, #64748b);
  margin-top: 10px;
  letter-spacing: 0.2em;
}

// 模拟雷达扫描的横线
.holo-scanner-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, #10b981, transparent);
  animation: holo-scan 2.5s ease-in-out infinite;
  
  .light & {
    background: linear-gradient(90deg, transparent, #059669, transparent);
  }
}

@keyframes holo-scan {
  0% { top: 0; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

.holo-radar-circle {
  width: 120px;
  height: 120px;
  border: 1px dashed rgba(16, 185, 129, 0.3);
  border-radius: 50%;
  margin: 30px auto 20px;
  position: relative;
  
  &::after {
    content: '';
    position: absolute;
    inset: 10px;
    border: 1px solid rgba(16, 185, 129, 0.6);
    border-radius: 50%;
    animation: radar-ping 2s linear infinite;
  }
}

@keyframes radar-ping {
  0% { transform: scale(0.6); opacity: 0.8; }
  100% { transform: scale(1.3); opacity: 0; }
}

.holo-stats {
  font-family: monospace;
  font-size: 11px;
  color: var(--text-sub, #64748b);
  text-align: left;
  border-top: 1px solid rgba(16, 185, 129, 0.2);
  padding-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

// 成功遮罩动画
.hologram-fade-enter-active,
.hologram-fade-leave-active {
  transition: opacity 0.4s ease;
}
.hologram-fade-enter-from,
.hologram-fade-leave-to {
  opacity: 0;
}
</style>
