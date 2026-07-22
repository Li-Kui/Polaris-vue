<template>
  <div
    :class="['login-card-inner', `card-${scheme}`, { 'is-focused': isInputFocused }]"
    :style="card3DStyle"
    @mousemove="handleMouseMove"
    @mouseleave="handleMouseLeave"
  >
    <!-- Pulse 方案的 HUD 装饰边角 -->
    <template v-if="scheme === 'pulse'">
      <div class="hud-corner c-tl" aria-hidden="true"></div>
      <div class="hud-corner c-tr" aria-hidden="true"></div>
      <div class="hud-corner c-bl" aria-hidden="true"></div>
      <div class="hud-corner c-br" aria-hidden="true"></div>
    </template>

    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
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
        <div class="biometric-status-hud">
          <span class="hud-dot"></span>
          <span class="hud-log-text">{{ isInputFocused ? '[DECIPHER] DECRYPTING DATAFLOW...' : '[BIOMETRIC] SECURE SCAN ACTIVE' }}</span>
        </div>
      </div>

      <el-form-item prop="username" class="form-item">
        <el-input
          v-model="loginForm.username"
          type="text"
          auto-complete="off"
          placeholder="账号 / 安全令牌"
          @focus="isInputFocused = true"
          @blur="isInputFocused = false"
        >
          <template #prefix>
            <span class="input-icon-wrap">
              <svg-icon icon-class="user" class="el-input__icon input-icon" />
            </span>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item prop="password" class="form-item">
        <el-input
          v-model="loginForm.password"
          type="password"
          auto-complete="off"
          placeholder="密码 / 解密密钥"
          show-password
          @keyup.enter="handleLogin"
          @focus="isInputFocused = true"
          @blur="isInputFocused = false"
        >
          <template #prefix>
            <span class="input-icon-wrap">
              <svg-icon icon-class="password" class="el-input__icon input-icon" />
            </span>
          </template>
        </el-input>
      </el-form-item>

      <div class="form-options">
        <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
        <router-link v-if="register" class="register-link" to="/register">立即注册</router-link>
      </div>

      <el-form-item class="form-submit">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          class="submit-btn"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import {getCodeImg} from "@/api/login"
import Cookies from "js-cookie"
import {decrypt, encrypt} from "@/utils/jsencrypt"
import useUserStore from "@/store/modules/user"
import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"

const props = defineProps({
  scheme: { type: String, default: "flux" },
  projectTitle: { type: String, default: "Polaris Vue" }
})

const emit = defineEmits(["login-success", "request-verify"])

const userStore = useUserStore()
const route = useRoute()

const isInputFocused = ref(false)

// 3D 鼠标悬停视差计算
const mouseX = ref(0)
const mouseY = ref(0)
const isHovering = ref(false)

const card3DStyle = computed(() => {
  if (!isHovering.value) {
    return {
      transform: props.scheme === 'orbit' ? 'translateY(-12px)' : 'none',
      transition: 'transform 0.5s ease, box-shadow 0.5s ease'
    }
  }
  const maxRotate = props.scheme === 'orbit' ? 12 : 6
  const rotateX = -mouseY.value * maxRotate
  const rotateY = mouseX.value * maxRotate
  return {
    transform: `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) ${props.scheme === 'orbit' ? 'translateY(-14px)' : ''}`,
    transition: 'transform 0.1s ease, box-shadow 0.3s ease'
  }
})

function handleMouseMove(e) {
  isHovering.value = true
  const rect = e.currentTarget.getBoundingClientRect()
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

const loginRef = ref(null)
const loading = ref(false)
const captchaEnabled = ref(true)
const register = ref(false)
const redirect = ref(undefined)

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: "",
  captchaVerification: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }]
}

watch(route, (r) => { redirect.value = r.query?.redirect }, { immediate: true })

onMounted(() => { getCode(); getCookie() })

function handleLogin() {
  loginRef.value.validate(valid => {
    if (!valid) return
    if (captchaEnabled.value) {
      emit("request-verify")
    } else {
      loading.value = true
      submitLogin()
    }
  })
}

function handleVerifySuccess(params) {
  loginForm.value.captchaVerification = params.captchaVerification
  loading.value = true
  submitLogin()
}

function submitLogin() {
  const form = loginForm.value
  if (form.rememberMe) {
    Cookies.set("username", form.username, { expires: 30 })
    Cookies.set("password", encrypt(form.password), { expires: 30 })
    Cookies.set("rememberMe", form.rememberMe, { expires: 30 })
  } else {
    Cookies.remove("username"); Cookies.remove("password"); Cookies.remove("rememberMe")
  }
  userStore.login(form).then(() => {
    const q = { ...route.query }; delete q.redirect
    loading.value = false
    emit("login-success", { path: redirect.value || "/", query: q })
  }).catch(() => { loading.value = false })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
  })
}

function getCookie() {
  const u = Cookies.get("username"), p = Cookies.get("password"), r = Cookies.get("rememberMe")
  loginForm.value = {
    ...loginForm.value,
    username: u ?? loginForm.value.username,
    password: p ? decrypt(p) : loginForm.value.password,
    rememberMe: r ? Boolean(r) : false
  }
}

defineExpose({ handleLogin, handleVerifySuccess })
</script>

<style lang="scss" scoped>
// ===== 卡片容器 =====
.login-card-inner {
  position: relative;
  padding: 36px 40px;
  width: 100%;
  max-width: 380px;
  box-sizing: border-box;
  transition: all 0.4s cubic-bezier(0.2, 0.8, 0.2, 1);
  transform-style: preserve-3d;
  animation: polaris-login-rise var(--dur-page) var(--ease-out) both;
}

// Flux 方案：毛玻璃卡片（意识觉醒）
.card-flux {
  border-radius: 22px;
  z-index: 2;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.2);
  
  .light & {
    background: rgba(255, 255, 255, 0.48);
    border: 1px solid rgba(255, 255, 255, 0.25);
    backdrop-filter: blur(25px);
    --btn-bg: linear-gradient(135deg, #6366f1, #a855f7);
    --btn-border-pure: #6366f1;
    --input-border: rgba(99, 102, 241, 0.15);
  }

  .dark & {
    background: rgba(10, 8, 20, 0.35);
    border: 1px solid rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(25px);
    --btn-bg: linear-gradient(135deg, #818cf8, #c084fc);
    --btn-border-pure: #818cf8;
    --input-border: rgba(129, 140, 248, 0.15);
  }
}

// Pulse 方案：HUD 边角（引力坍缩）
.card-pulse {
  border-radius: 12px;
  box-shadow: 0 30px 70px rgba(0, 0, 0, 0.35);
  
  .light & {
    background: rgba(255, 255, 255, 0.45);
    border: 1px solid rgba(220, 95, 30, 0.3);
    --btn-bg: #ea580c;
    --btn-border-pure: #ea580c;
    --input-border: rgba(220, 95, 30, 0.2);
  }

  .dark & {
    background: rgba(10, 5, 3, 0.35);
    border: 1px solid rgba(249, 115, 22, 0.25);
    --btn-bg: #f97316;
    --btn-border-pure: #f97316;
    --input-border: rgba(249, 115, 22, 0.2);
    box-shadow: 0 0 40px rgba(249, 115, 22, 0.1);
  }
}

// Orbit 方案（赛博视界）
.card-orbit {
  border-radius: 16px;
  animation: levitate 4s ease-in-out infinite alternate;
  
  .light & {
    background: rgba(255, 255, 255, 0.45);
    border: 1px solid rgba(2, 132, 199, 0.2);
    --btn-bg: #0284c7;
    --btn-border-pure: #0284c7;
    --input-border: rgba(2, 132, 199, 0.2);
  }

  .dark & {
    background: rgba(3, 6, 15, 0.35);
    border: 1px solid rgba(236, 72, 153, 0.2);
    backdrop-filter: blur(20px);
    --btn-bg: #ec4899;
    --btn-border-pure: #ec4899;
    --input-border: rgba(236, 72, 153, 0.15);
    box-shadow: 0 0 30px rgba(236, 72, 153, 0.05);
  }
}

@keyframes levitate {
  0% { transform: translateY(0px); }
  100% { transform: translateY(-12px); }
}

// ===== 表单 =====
.login-form { width: 100%; }

.form-header {
  text-align: center;
  margin-bottom: 28px;
  
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

  // 生物扫描激光
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
    font-family: var(--font-mono, monospace);
    font-size: 9px;
    font-weight: bold;
    color: var(--ink-3);
    letter-spacing: 0.08em;
  }

  .title {
    font-family: var(--font-display);
    font-size: var(--fs-display, 24px);
    font-weight: 700;
    color: var(--ink);
    margin: 8px 0 0;
    letter-spacing: -0.01em;
  }
}

@keyframes rotate-logo {
  to { transform: rotate(360deg); }
}

@keyframes polaris-star-breath {
  0% { transform: scale(0.9); filter: drop-shadow(0 0 4px rgba(127, 0, 255, 0.35)); }
  100% { transform: scale(1.11); filter: drop-shadow(0 0 14px rgba(255, 0, 127, 0.65)); }
}

// ===== 输入框 =====
.form-item {
  margin-bottom: 24px;
  position: relative;

  :deep(.el-form-item__content) { line-height: normal; }
  :deep(.el-input) { height: 44px !important; }
}

.form-item :deep(.el-input__wrapper) {
  background-color: transparent !important;
  border: none !important;
  border-bottom: 1.5px solid var(--input-border) !important;
  box-shadow: none !important;
  border-radius: 0px !important;
  height: 44px !important;
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

.form-item :deep(.el-input__inner) {
  color: var(--ink);
  font-size: 13px;
  font-family: var(--font-mono, monospace);
}

.input-icon-wrap {
  display: inline-flex;
  align-items: center;
  height: 100%;
  padding-right: 4px;
  color: var(--ink-3);
  .input-icon { font-size: 14px; }
}

// ===== 选项行 =====
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;

  :deep(.el-checkbox__label) { color: var(--ink-2); font-size: 13px; }
  :deep(.el-checkbox__inner) { border-color: var(--line); background: var(--card); }
  :deep(.is-checked .el-checkbox__inner) { background: var(--accent); border-color: var(--accent); }
}

.register-link {
  color: var(--accent);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  &:hover { color: var(--accent-2); }
}

// ===== 提交按钮 =====
.form-submit {
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
    color: var(--ink) !important;
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

// ===== HUD 边角（Pulse 方案） =====
.hud-corner {
  position: absolute;
  width: 16px;
  height: 16px;
  border: 2px solid var(--btn-bg);
  pointer-events: none;
  z-index: 5;
  transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
  
  &.c-tl { top: -2px; left: -2px; border-right: none; border-bottom: none; }
  &.c-tr { top: -2px; right: -2px; border-left: none; border-bottom: none; }
  &.c-bl { bottom: -2px; left: -2px; border-right: none; border-top: none; }
  &.c-br { bottom: -2px; right: -2px; border-left: none; border-top: none; }
}

.is-focused .hud-corner {
  width: 12px;
  height: 12px;
  border-color: #f43f5e;
  &.c-tl { top: 4px; left: 4px; }
  &.c-tr { top: 4px; right: 4px; }
  &.c-bl { bottom: 4px; left: 4px; }
  &.c-br { bottom: 4px; right: 4px; }
}
</style>
