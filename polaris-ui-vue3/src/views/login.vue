<template>
  <div :class="['login-container', 'login', `theme-${activeTheme}`, { 'dark-mode': activeTheme === 'dark' }]">
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
          <line x1="10%" y1="20%" x2="30%" y2="40%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1" />
          <line x1="30%" y1="40%" x2="50%" y2="15%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1.5" />
          <line x1="30%" y1="40%" x2="25%" y2="80%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1" />
          <line x1="50%" y1="15%" x2="70%" y2="45%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1" />
          <line x1="70%" y1="45%" x2="90%" y2="30%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1.5" />
          <line x1="70%" y1="45%" x2="60%" y2="85%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1" />
          <line x1="25%" y1="80%" x2="60%" y2="85%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1" />
          <line x1="60%" y1="85%" x2="90%" y2="70%" :stroke="activeTheme === 'dark' ? 'url(#line-grad)' : 'url(#line-grad-light)'" stroke-width="1" />
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

    <!-- 视图过渡容器 -->
    <transition name="view-fade" mode="out-in">
      <!-- 视图一：登录表单面板 -->
      <div v-if="currentView === 'login'" key="login" class="login-view-wrapper">
        <div class="login-card-inner">
          <!-- 左侧：全屏浩瀚星仪与AI星轨视觉面板 (Cosmic Viewer) -->
          <div class="login-visual-panel">
            <!-- 玻璃罩下的液态极光流动 blob -->
            <div class="aurora-blob-container">
              <div class="aurora-blob blob-1"></div>
              <div class="aurora-blob blob-2"></div>
            </div>
            <div class="glass-layer"></div>

            <!-- 数据流瀑布 -->
            <div class="synapse-data-cascade">
              <div class="cascade-line line-1"></div>
              <div class="cascade-line line-2"></div>
              <div class="cascade-line line-3"></div>
            </div>

            <!-- 顶部品牌栏 -->
            <div class="visual-header">
              <div class="product-brand">
                <span class="brand-spark">✦</span>
                <span class="brand-name">Polaris OS</span>
              </div>
            </div>
            
            <!-- 巨型：AI 北辰星仪脉冲核心 (带失重悬浮) -->
            <div class="ai-cognition-core-wrapper float-animation">
              <div class="ai-core-ring outer-ring"></div>
              <div class="ai-core-ring middle-ring"></div>
              <div class="ai-core-ring inner-pulse"></div>
              <!-- 闪烁发光的北辰星芒 -->
              <div class="ai-core-center-node">✦</div>
            </div>

            <!-- 流光文字 (自适应漂移) -->
            <div class="visual-content float-animation-delayed">
              <h2 class="visual-title-shimmer">北辰 · Polaris<br/>智能对话与数据分析</h2>
              <p class="visual-desc">基于 LangChain4j 构建的全新一代人工智能对话、知识库检索与数据分析可视化管理平台。打破大模型与业务数据壁垒，赋能企业智能化生产力。</p>
            </div>
            
            <div class="visual-footer">
              <div class="status-indicator">
                <span class="status-dot"></span>
                <span class="status-text">系统状态：稳固运行</span>
              </div>
            </div>
          </div>

          <!-- 右侧：无边框极纯登录仓 (Obsidian Minimal Panel) -->
          <div class="login-form-panel">
            <div class="holographic-form-shield">
              <!-- 冰晶全息定位四角 -->
              <div class="shield-corner corner-tl"></div>
              <div class="shield-corner corner-tr"></div>
              <div class="shield-corner corner-bl"></div>
              <div class="shield-corner corner-br"></div>

              <div class="form-container-inner">
                <div class="form-header">
                  <!-- 安全验证芯片 HUD 贴片 -->
                  <div class="secure-hud-badge">
                    <span class="badge-dot"></span>
                    <span class="badge-txt">POLARIS PLATFORM LOGIN</span>
                  </div>
                  <h3 class="form-title">用户登录</h3>
                  <p class="form-subtitle">北辰 (Polaris) · 智能大模型对话与数据分析管理平台</p>
                </div>

                <el-form ref="formRef" :model="loginForm" :rules="loginRules" class="modern-form">
                  <!-- 账号输入 -->
                  <el-form-item prop="username" class="modern-form-item">
                    <div class="input-label">用户账号</div>
                    <div class="input-glow-wrapper">
                      <el-input
                        v-model="loginForm.username"
                        placeholder="请输入账号 / admin"
                        @focus="focusInput('username')"
                        @blur="blurInput"
                      >
                        <template #prefix>
                          <span class="prefix-icon">👤</span>
                        </template>
                      </el-input>
                      <div :class="['input-active-line', { 'is-active': activeField === 'username' }]"></div>
                    </div>
                  </el-form-item>

                  <!-- 密码输入 -->
                  <el-form-item prop="password" class="modern-form-item">
                    <div class="input-label">登录密码</div>
                    <div class="input-glow-wrapper">
                      <el-input
                        v-model="loginForm.password"
                        type="password"
                        placeholder="请输入密码 / admin123"
                        show-password
                        @focus="focusInput('password')"
                        @blur="blurInput"
                        @keyup.enter="handleLogin"
                      >
                        <template #prefix>
                          <span class="prefix-icon">🔒</span>
                        </template>
                      </el-input>
                      <div :class="['input-active-line', { 'is-active': activeField === 'password' }]"></div>
                    </div>
                  </el-form-item>

                  <div class="form-options">
                    <el-checkbox v-model="loginForm.rememberMe" class="remember-checkbox">记住我</el-checkbox>
                    <span class="forgot-link">忘记密码?</span>
                  </div>

                  <!-- 登录按钮 -->
                  <el-form-item class="form-btn-item">
                    <div class="submit-btn-glow-container">
                      <el-button
                        :loading="loading"
                        type="primary"
                        class="login-submit-btn"
                        @click.prevent="handleLogin"
                      >
                        <span>登 录</span>
                      </el-button>
                      <div class="submit-btn-glow-back"></div>
                    </div>
                  </el-form-item>
                </el-form>


              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 视图二：北辰全息授权重构中 (Loading 画面) -->
      <div v-else-if="currentView === 'authorizing'" key="authorizing" class="authorizing-wrapper">
        <div class="auth-loader-card">
          <!-- 十字星芒脉冲环 -->
          <div class="polaris-pulse-ring">
            <div class="pulse-circle circle-1"></div>
            <div class="pulse-circle circle-2"></div>
            <div class="pulse-circle circle-3"></div>
            <span class="polaris-star-spark">✦</span>
          </div>

          <div class="auth-title">北辰星轨重构同步中...</div>
          <div class="auth-sub">POLARIS NODE ALIGNMENT & DECRYPTION</div>
          
          <!-- 解密滚动日志控制台 -->
          <div class="auth-terminal-log">
            <div v-for="(log, idx) in visibleLogs" :key="idx" class="log-line">
              <span class="log-tag">[{{ log.tag }}]</span>
              <span class="log-text">{{ log.text }}</span>
            </div>
            <div class="terminal-cursor">_</div>
          </div>

          <div class="progress-bar-container">
            <div class="progress-bar-fill" :style="{ width: authProgress + '%' }"></div>
          </div>
          <div class="progress-info-row">
            <span class="progress-percentage">{{ authProgress }}% SYNCHRONIZED</span>
            <span class="progress-hz">Polaris-IV Matrix</span>
          </div>
        </div>
      </div>
    </transition>

    <!-- 🧭 右上角：星轨罗盘控制极仪 -->
    <div 
      v-if="currentView === 'login'"
      class="stellar-control-dock pos-top-right" 
      @click="handleDockClick"
    >
      <div class="dock-hologram-wrapper">
        <div class="dock-ring-decor"></div>
        <div class="dock-center-core">
          <span class="dock-core-icon">🧭</span>
        </div>

        <div class="dock-sliding-panel">
          <span class="dock-title">星轨极仪</span>
          <div class="theme-slider-track">
            <div 
              :class="['slider-opt', { active: activeTheme === 'light' }]" 
              @click.stop="setThemeWithWave('light', $event)"
            >
              ☀️ 极光
            </div>
            <div 
              :class="['slider-opt', { active: activeTheme === 'dark' }]" 
              @click.stop="setThemeWithWave('dark', $event)"
            >
              🌙 深空
            </div>
            <div :class="['slider-active-thumb', activeTheme]"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 🌀 引力波全屏重组涟漪 -->
    <transition name="stellar-wave">
      <div 
        v-if="showWave" 
        class="stellar-wave-ripple" 
        :style="{ left: waveX + 'px', top: waveY + 'px', backgroundColor: waveBg }"
      ></div>
    </transition>

    <!-- 🪐 量子信道滑动对齐验证端 (Quantum Slider Verification Dialog) -->
    <el-dialog
      v-model="showSliderVerify"
      width="400px"
      destroy-on-close
      :show-close="false"
      class="modern-glass-dialog slider-verify-dialog"
    >
      <div class="slider-verify-shell">
        <div class="slider-verify-header">
          <span class="sv-header-icon">🛡️</span>
          <span class="sv-header-text">量子信道安全对齐</span>
        </div>
        
        <!-- 核心物理对齐舱 -->
        <div class="aligner-chamber">
          <div class="chamber-grid"></div>
          
          <!-- 固定的目标槽 -->
          <div 
            class="quantum-groove" 
            :style="{ left: targetX + 'px' }"
            :class="{ 'aligned': isAligned }"
          >
            <div class="groove-pulse-ring"></div>
          </div>
          
          <!-- 可拖拽的量子密钥块 -->
          <div 
            class="quantum-key" 
            :style="{ transform: `translateX(${sliderLeft}px)` }"
            :class="{ 'success': verifyStatus === 'success', 'error': verifyStatus === 'error' }"
          >
            <div class="key-core">✦</div>
            <div class="key-orbit"></div>
          </div>
          
          <div class="chamber-info-text">
            <span v-if="verifyStatus === 'pending'">请将 ✦ 密钥滑移至虚线靶心</span>
            <span v-else-if="verifyStatus === 'success'" class="text-success">✔ 量子信道已对齐，授权准入</span>
            <span v-else-if="verifyStatus === 'error'" class="text-danger">✘ 信道偏角超标，密钥回退</span>
          </div>
        </div>
        
        <!-- 滑动轨道 -->
        <div class="slider-track-wrap">
          <div class="slider-track-bg"></div>
          <div 
            class="slider-track-fill" 
            :style="{ width: sliderLeft + 36 + 'px' }"
            :class="verifyStatus"
          ></div>
          <div 
            class="slider-handle"
            :style="{ transform: `translateX(${sliderLeft}px)` }"
            :class="{ 'dragging': isDragging, 'success': verifyStatus === 'success', 'error': verifyStatus === 'error' }"
            @mousedown="startDrag"
          >
            <span class="handle-arrow">≫</span>
          </div>
        </div>
        
        <div class="slider-verify-footer">
          <button class="sv-cancel-btn" @click="closeVerify">中断校验</button>
        </div>
      </div>
    </el-dialog>

    <!-- 图形滑块验证码 (后端业务验证，已覆盖为高透毛玻璃北辰特装版) -->
    <Verify
      ref="verifyRef"
      :captcha-type="'blockPuzzle'"
      :mode="'pop'"
      @success="handleVerifySuccess"
    />
  </div>
</template>

<script setup>
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import Cookies from "js-cookie"
import {decrypt, encrypt} from "@/utils/jsencrypt"
import useUserStore from "@/store/modules/user"
import {getCodeImg} from "@/api/login"
import Verify from "@/components/verifition/Verify.vue"

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const redirect = ref(undefined)

const activeTheme = ref(localStorage.getItem('polaris.theme.variable') || 'dark')
const currentView = ref('login')
const authProgress = ref(0)
const visibleLogs = ref([])

const loading = ref(false)
const formRef = ref(null)
const activeField = ref('')

const projectTitle = import.meta.env.VITE_APP_TITLE || 'Polaris Vue'

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
  rememberMe: false,
  code: '',
  uuid: '',
  captchaVerification: ''
})

const loginRules = {
  username: [{ required: true, trigger: 'blur', message: '请输入用户账号' }],
  password: [{ required: true, trigger: 'blur', message: '请输入登录密码' }]
}

const consoleLogs = [
  { tag: 'POLARIS', text: 'INITIATING COMPASS ORIENTATION...' },
  { tag: 'NODE', text: 'CONNECTING TO BEICHEN QUANTUM MATRIX [GRID-01]' },
  { tag: 'DECRYPT', text: 'SYNCHRONIZING URSA MAJOR DECRYPTION CODES' },
  { tag: 'SENTINEL', text: 'POLARIS SENTINEL ACTIVE. SHIELD ENGAGED' },
  { tag: 'COGNITIVE', text: 'ALIGNING NEURAL COGNITION PATHWAYS' },
  { tag: 'SUCCESS', text: 'STELLAR COGNITIVE MATRIX SHIELD ALIGNED.' }
]

// ===== 引力波控制 =====
const showWave = ref(false)
const waveX = ref(0)
const waveY = ref(0)
const waveBg = ref('')

// ===== 量子验证码状态与逻辑 =====
const showSliderVerify = ref(false)
const sliderLeft = ref(0)
const targetX = ref(180)
const isDragging = ref(false)
const verifyStatus = ref('pending') // pending, success, error
const isAligned = computed(() => verifyStatus.value === 'success')

let startX = 0
const trackMaxWidth = ref(308)

// 后端图形验证码状态 (对接真实业务)
const verifyRef = ref(null)
const captchaEnabled = ref(true)

watch(route, (r) => { redirect.value = r.query?.redirect }, { immediate: true })

onMounted(() => {
  applyGlobalTheme(activeTheme.value)
  getCookie()
  getCode()
})

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
  })
}

function getCookie() {
  const u = Cookies.get("username")
  const p = Cookies.get("password")
  const r = Cookies.get("rememberMe")
  loginForm.username = u ?? loginForm.username
  loginForm.password = p ? decrypt(p) : loginForm.password
  loginForm.rememberMe = r ? Boolean(r) : false
}

function saveCookie() {
  if (loginForm.rememberMe) {
    Cookies.set("username", loginForm.username, { expires: 30 })
    Cookies.set("password", encrypt(loginForm.password), { expires: 30 })
    Cookies.set("rememberMe", loginForm.rememberMe, { expires: 30 })
  } else {
    Cookies.remove("username")
    Cookies.remove("password")
    Cookies.remove("rememberMe")
  }
}

function applyGlobalTheme(theme) {
  const root = document.documentElement
  if (theme === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
  localStorage.setItem('polaris.theme.variable', theme)
}

function setThemeWithWave(theme, event) {
  if (activeTheme.value === theme) return

  if (event) {
    waveX.value = event.clientX
    waveY.value = event.clientY
  } else {
    waveX.value = window.innerWidth - 60
    waveY.value = 60
  }

  waveBg.value = theme === 'dark' 
    ? 'rgba(10, 15, 25, 0.85)' 
    : 'rgba(255, 255, 255, 0.85)'

  showWave.value = true
  
  setTimeout(() => {
    activeTheme.value = theme
    applyGlobalTheme(theme)
  }, 250)

  setTimeout(() => {
    showWave.value = false
  }, 650)
}

function handleDockClick() {
  const nextTheme = activeTheme.value === 'dark' ? 'light' : 'dark'
  const mockEvent = {
    clientX: window.innerWidth - 30,
    clientY: 60
  }
  setThemeWithWave(nextTheme, mockEvent)
}

function focusInput(field) {
  activeField.value = field
}

function blurInput() {
  activeField.value = ''
}

function handleLogin() {
  if (!formRef.value) return
  formRef.value.validate((valid) => {
    if (valid) {
      // 直接拉起后端拼图验证码
      if (captchaEnabled.value) {
        verifyRef.value?.show()
      } else {
        startSimulationLoading()
      }
    }
  })
}

function quickFill() {
  loginForm.username = 'admin'
  loginForm.password = 'admin123'
  handleLogin()
}

// ===== 量子对齐滑块拖拽 =====
function openVerify() {
  verifyStatus.value = 'pending'
  sliderLeft.value = 0
  targetX.value = Math.floor(Math.random() * 160) + 110
  showSliderVerify.value = true
}

function closeVerify() {
  showSliderVerify.value = false
  isDragging.value = false
  sliderLeft.value = 0
  verifyStatus.value = 'pending'
}

function startDrag(e) {
  if (verifyStatus.value === 'success') return
  isDragging.value = true
  startX = e.clientX
  
  const handleEl = e.currentTarget
  const trackEl = handleEl?.parentElement
  if (trackEl) {
    const trackWidth = trackEl.clientWidth
    trackMaxWidth.value = trackWidth - 44 - 2
  }
  
  window.addEventListener('mousemove', handleDrag)
  window.addEventListener('mouseup', stopDrag)
}

function handleDrag(e) {
  if (!isDragging.value) return
  let deltaX = e.clientX - startX
  if (deltaX < 0) deltaX = 0
  if (deltaX > trackMaxWidth.value) deltaX = trackMaxWidth.value
  sliderLeft.value = deltaX
}

function stopDrag() {
  if (!isDragging.value) return
  isDragging.value = false
  window.removeEventListener('mousemove', handleDrag)
  window.removeEventListener('mouseup', stopDrag)
  
  const diff = Math.abs(sliderLeft.value - targetX.value)
  if (diff <= 5) {
    verifyStatus.value = 'success'
    setTimeout(() => {
      showSliderVerify.value = false
      // 量子对齐成功后，直接启动全屏仿真加载并执行登录
      startSimulationLoading()
    }, 600)
  } else {
    verifyStatus.value = 'error'
    setTimeout(() => {
      let count = 0
      const interval = setInterval(() => {
        sliderLeft.value = sliderLeft.value * 0.5
        count++
        if (count > 5 || sliderLeft.value < 1) {
          sliderLeft.value = 0
          verifyStatus.value = 'pending'
          clearInterval(interval)
        }
      }, 30)
    }, 400)
  }
}

// 后端原装验证码组件校验成功回调
function handleVerifySuccess(params) {
  loginForm.captchaVerification = params.captchaVerification
  // 获取到了后端签名凭证后，启动全屏仿真加载，并执行登录！
  startSimulationLoading()
}

// ===== 授权同步仿真 Loading 载入 =====
function startSimulationLoading() {
  currentView.value = 'authorizing'
  authProgress.value = 0
  visibleLogs.value = []
  
  let logIndex = 0
  const logInterval = setInterval(() => {
    if (logIndex < consoleLogs.length) {
      visibleLogs.value.push(consoleLogs[logIndex])
      logIndex++
    } else {
      clearInterval(logInterval)
    }
  }, 200)

  const progressInterval = setInterval(() => {
    if (authProgress.value < 100) {
      authProgress.value += Math.floor(Math.random() * 14) + 8
      if (authProgress.value > 100) authProgress.value = 100
    } else {
      clearInterval(progressInterval)
      clearInterval(logInterval)
      
      executeRealLogin()
    }
  }, 80)
}

function executeRealLogin() {
  loading.value = true
  saveCookie()
  
  userStore.login(loginForm).then(() => {
    loading.value = false
    const q = { ...route.query }
    delete q.redirect
    ElMessage.success('量子信道已同步，安全连接就绪')
    router.push({ path: redirect.value || "/", query: q })
  }).catch((err) => {
    loading.value = false
    currentView.value = 'login'
  })
}
</script>

<style lang="scss">
/* ==========================================================================
   北辰 / Polaris 全局设计系统样式配置文件 (Design Tokens Configuration)
   ========================================================================== */
:root {
  /* ✦ 极光亮色设计系统配置 (Glacier Light Theme) */
  --polaris-bg: #f1f5f9;
  --polaris-card-bg: rgba(255, 255, 255, 0.65);
  --polaris-card-border: rgba(255, 255, 255, 0.5);
  --polaris-inner-border: rgba(0, 0, 0, 0.05);
  --polaris-text-main: #0f172a;
  --polaris-text-sub: #64748b;
  --polaris-brand-color: #4f46e5;
  --polaris-brand-hover: rgba(79, 70, 229, 0.06);
  --polaris-danger-color: #ef4444;
  --polaris-danger-hover: rgba(239, 68, 68, 0.06);
}

.dark {
  /* ✦ 深空暗色设计系统配置 (Obsidian Dark Theme) */
  --polaris-bg: #080a10;
  --polaris-card-bg: rgba(10, 15, 30, 0.55);
  --polaris-card-border: rgba(255, 255, 255, 0.05);
  --polaris-inner-border: rgba(255, 255, 255, 0.08);
  --polaris-text-main: #f8fafc;
  --polaris-text-sub: #94a3b8;
  --polaris-brand-color: #38bdf8;
  --polaris-brand-hover: rgba(255, 255, 255, 0.05);
  --polaris-danger-color: #fca5a5;
  --polaris-danger-hover: rgba(239, 68, 68, 0.15);
}

/* 保证一键切换时的流动性 */
body, .login-container {
  background-color: var(--polaris-bg) !important;
  color: var(--polaris-text-main) !important;
  transition: background-color 0.5s cubic-bezier(0.25, 0.8, 0.25, 1), color 0.5s ease;
}

/* 全局 Dialog 玻璃拟态重写 */
.slider-verify-dialog {
  :deep(.el-dialog), .el-dialog {
    background: rgba(13, 18, 30, 0.45) !important;
    backdrop-filter: blur(40px) !important;
    border: 1px solid rgba(56, 189, 248, 0.2) !important;
    border-radius: 24px !important;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.5) !important;
    overflow: hidden;
    
    .theme-light &, html:not(.dark) & {
      background: rgba(255, 255, 255, 0.65) !important;
      border-color: rgba(79, 70, 229, 0.15) !important;
      box-shadow: 0 15px 40px rgba(0, 0, 0, 0.08) !important;
    }
  }
  
  :deep(.el-dialog__header), .el-dialog__header {
    display: none !important;
  }
  
  :deep(.el-dialog__body), .el-dialog__body {
    padding: 0 !important;
  }
}

/* 🌌 强力接管后端图形验证码组件，升级为高透毛玻璃北辰特装版 */
.login-container {
  // ==================== 🌑 深空暗色主题 (Obsidian Dark Theme) ====================
  &.theme-dark {
    .mask {
      position: fixed;
      top: 0;
      left: 0;
      z-index: 2001;
      width: 100vw;
      height: 100vh;
      background: rgba(8, 10, 20, 0.55) !important;
      backdrop-filter: blur(25px) !important;
      -webkit-backdrop-filter: blur(25px) !important;
    }

    .verifybox {
      background: rgba(13, 18, 30, 0.65) !important;
      backdrop-filter: blur(40px) !important;
      -webkit-backdrop-filter: blur(40px) !important;
      border: 1px solid rgba(56, 189, 248, 0.25) !important;
      border-radius: 24px !important;
      box-shadow: 0 25px 60px rgba(0, 0, 0, 0.65), 0 0 25px rgba(56, 189, 248, 0.1) !important;
      color: #f8fafc !important;
    }

    .verifybox-top {
      background: rgba(255, 255, 255, 0.02) !important;
      border-bottom: 1px dashed rgba(255, 255, 255, 0.08) !important;
      color: #f8fafc !important;
      font-size: 13.5px !important;
      letter-spacing: 0.05em;
      font-weight: 800 !important;
    }

    .verifybox-close {
      .iconfont {
        color: rgba(255, 255, 255, 0.45) !important;
      }
      &:hover {
        .iconfont {
          color: #fca5a5 !important;
        }
      }
    }

    .verifybox-bottom {
      background: transparent !important;
    }

    .verify-bar-area {
      background: rgba(0, 0, 0, 0.35) !important;
      border: 1px solid rgba(255, 255, 255, 0.04) !important;
      border-radius: 99px !important;
      height: 40px !important;
      line-height: 40px !important;
    }

    .verify-msg {
      color: rgba(255, 255, 255, 0.4) !important;
      font-size: 11.5px !important;
      font-weight: 700 !important;
    }

    .verify-left-bar {
      border-radius: 99px 0 0 99px !important;
      border: 1px solid rgba(56, 189, 248, 0.3) !important;
      background: linear-gradient(90deg, rgba(56, 189, 248, 0.06), rgba(56, 189, 248, 0.18)) !important;
      height: 40px !important;
    }

    .verify-move-block {
      background: linear-gradient(135deg, #38bdf8, #818cf8) !important;
      border: 1px solid rgba(255, 255, 255, 0.25) !important;
      box-shadow: 0 4px 12px rgba(56, 189, 248, 0.35) !important;
      border-radius: 50% !important;
      width: 40px !important;
      height: 40px !important;
      top: 0px !important;
      transition: background-color 0.2s, border-color 0.2s, box-shadow 0.2s;
      
      .iconfont, .icon-right {
        color: #0f172a !important;
        font-size: 14px !important;
      }
      
      &:hover {
        background: linear-gradient(135deg, #7dd3fc, #a5b4fc) !important;
        box-shadow: 0 6px 18px rgba(56, 189, 248, 0.45) !important;
      }
    }

    .verify-img-panel {
      border: 1px solid rgba(255, 255, 255, 0.08) !important;
      border-radius: 14px !important;
      overflow: hidden !important;
    }

    .verify-img-panel .verify-refresh {
      background: rgba(255, 255, 255, 0.06) !important;
      border: 1px solid rgba(255, 255, 255, 0.1) !important;
      
      .icon-refresh {
        color: rgba(255, 255, 255, 0.7) !important;
      }
      
      &:hover {
        background: rgba(255, 255, 255, 0.15) !important;
        .icon-refresh {
          color: #38bdf8 !important;
        }
      }
    }

    .verify-tips {
      font-size: 12px !important;
      font-weight: bold !important;
      backdrop-filter: blur(10px) !important;
      line-height: 30px !important;
      &.suc-bg {
        background-color: rgba(16, 185, 129, 0.85) !important;
      }
      &.err-bg {
        background-color: rgba(239, 68, 68, 0.85) !important;
      }
    }
  }

  // ==================== ☀️ 极光亮色主题 (Glacier Light Theme) ====================
  &.theme-light {
    .mask {
      position: fixed;
      top: 0;
      left: 0;
      z-index: 2001;
      width: 100vw;
      height: 100vh;
      background: rgba(15, 23, 42, 0.18) !important;
      backdrop-filter: blur(15px) !important;
      -webkit-backdrop-filter: blur(15px) !important;
    }

    .verifybox {
      background: rgba(255, 255, 255, 0.88) !important;
      backdrop-filter: blur(40px) !important;
      -webkit-backdrop-filter: blur(40px) !important;
      border: 1px solid rgba(79, 70, 229, 0.18) !important;
      border-radius: 24px !important;
      box-shadow: 0 20px 50px rgba(15, 23, 42, 0.08), 0 0 15px rgba(79, 70, 229, 0.04) !important;
      color: #0f172a !important;
    }

    .verifybox-top {
      background: rgba(0, 0, 0, 0.02) !important;
      border-bottom: 1px dashed rgba(0, 0, 0, 0.08) !important;
      color: #0f172a !important;
      font-size: 13.5px !important;
      letter-spacing: 0.05em;
      font-weight: 800 !important;
    }

    .verifybox-close {
      .iconfont {
        color: rgba(0, 0, 0, 0.4) !important;
      }
      &:hover {
        .iconfont {
          color: #ef4444 !important;
        }
      }
    }

    .verifybox-bottom {
      background: transparent !important;
    }

    .verify-bar-area {
      background: rgba(0, 0, 0, 0.03) !important;
      border: 1px solid rgba(0, 0, 0, 0.05) !important;
      border-radius: 99px !important;
      height: 40px !important;
      line-height: 40px !important;
    }

    .verify-msg {
      color: rgba(0, 0, 0, 0.45) !important;
      font-size: 11.5px !important;
      font-weight: 700 !important;
    }

    .verify-left-bar {
      border-radius: 99px 0 0 99px !important;
      border: 1px solid rgba(79, 70, 229, 0.25) !important;
      background: linear-gradient(90deg, rgba(79, 70, 229, 0.05), rgba(79, 70, 229, 0.15)) !important;
      height: 40px !important;
    }

    .verify-move-block {
      background: linear-gradient(135deg, #4f46e5, #6366f1) !important;
      border: 1px solid rgba(255, 255, 255, 0.3) !important;
      box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25) !important;
      border-radius: 50% !important;
      width: 40px !important;
      height: 40px !important;
      top: 0px !important;
      transition: background-color 0.2s, border-color 0.2s, box-shadow 0.2s;
      
      .iconfont, .icon-right {
        color: #ffffff !important;
        font-size: 14px !important;
      }
      
      &:hover {
        background: linear-gradient(135deg, #4338ca, #4f46e5) !important;
        box-shadow: 0 6px 18px rgba(79, 70, 229, 0.35) !important;
      }
    }

    .verify-img-panel {
      border: 1px solid rgba(0, 0, 0, 0.06) !important;
      border-radius: 14px !important;
      overflow: hidden !important;
    }

    .verify-img-panel .verify-refresh {
      background: rgba(0, 0, 0, 0.03) !important;
      border: 1px solid rgba(0, 0, 0, 0.06) !important;
      
      .icon-refresh {
        color: rgba(0, 0, 0, 0.6) !important;
      }
      
      &:hover {
        background: rgba(0, 0, 0, 0.08) !important;
        .icon-refresh {
          color: #4f46e5 !important;
        }
      }
    }

    .verify-tips {
      font-size: 12px !important;
      font-weight: bold !important;
      backdrop-filter: blur(10px) !important;
      line-height: 30px !important;
      &.suc-bg {
        background-color: rgba(16, 185, 129, 0.85) !important;
      }
      &.err-bg {
        background-color: rgba(239, 68, 68, 0.85) !important;
      }
    }
  }
}
</style>

<style lang="scss" scoped>
// ===== 极简自适应总包裹座 =====
.login-container {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1;
}

// ===== 弥散渐变 + 突触网络背景 =====
.mesh-gradient-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 1;
  pointer-events: none;
  width: 100%;
  height: 100%;
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

// ===== 视图过渡动画 =====
.view-fade-enter-active,
.view-fade-leave-active {
  transition: opacity 0.4s cubic-bezier(0.25, 0.8, 0.25, 1), transform 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.view-fade-enter-from {
  opacity: 0;
  transform: scale(0.98) translateY(8px);
}
.view-fade-leave-to {
  opacity: 0;
  transform: scale(1.02) translateY(-8px);
}

// ===== 登录全屏双分栏布局 (抗挤压自适应) =====
.login-view-wrapper {
  position: relative;
  z-index: 10;
  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
  flex-shrink: 0;
}

.login-card-inner {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
  position: relative;
}

// 左侧星轨视区
.login-visual-panel {
  flex: 1.35;
  padding: 60px 80px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
  
  .theme-light & {
    border-right: 1px solid rgba(0, 0, 0, 0.04);
  }
  .theme-dark & {
    border-right: 1px solid rgba(255, 255, 255, 0.03);
  }
}

.aurora-blob-container {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.aurora-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  mix-blend-mode: screen;
  transition: all 1s ease;
  
  .theme-light & {
    mix-blend-mode: multiply;
    opacity: 0.25;
  }
  .theme-dark & {
    opacity: 0.35;
  }
}

.blob-1 {
  width: 450px;
  height: 450px;
  top: -15%;
  left: -10%;
  background: radial-gradient(circle, #6366f1 0%, transparent 80%);
  animation: morph 12s infinite alternate ease-in-out, float-visual-1 20s infinite alternate ease-in-out;
}

.blob-2 {
  width: 500px;
  height: 500px;
  bottom: -20%;
  right: -10%;
  background: radial-gradient(circle, #38bdf8 0%, transparent 80%);
  animation: morph 14s infinite alternate ease-in-out, float-visual-2 24s infinite alternate ease-in-out;
}

.glass-layer {
  position: absolute;
  inset: 0;
  backdrop-filter: blur(25px);
  z-index: 2;
  
  .theme-light & { background: rgba(255, 255, 255, 0.1); }
  .theme-dark & { background: rgba(15, 23, 42, 0.15); }
}

.synapse-data-cascade {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

.cascade-line {
  position: absolute;
  width: 1px;
  height: 100px;
  opacity: 0.3;
  animation: cascadeFall 4s linear infinite;
  
  .theme-light & {
    background: linear-gradient(to bottom, transparent, #4f46e5, transparent);
  }
  .theme-dark & {
    background: linear-gradient(to bottom, transparent, #38bdf8, transparent);
  }
  
  &.line-1 { left: 15%; top: -100px; animation-duration: 3s; animation-delay: 0.2s; }
  &.line-2 { left: 50%; top: -100px; animation-duration: 4.5s; animation-delay: 1.5s; }
  &.line-3 { left: 85%; top: -100px; animation-duration: 3.5s; animation-delay: 0.8s; }
}

// 品牌头
.visual-header, .visual-content, .visual-footer {
  position: relative;
  z-index: 4;
}

.visual-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.product-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .brand-spark {
    font-size: 20px;
    
    .theme-light & { color: #4f46e5; }
    .theme-dark & { color: #38bdf8; }
  }
  
  .brand-name {
    font-weight: 900;
    font-size: 16px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    
    .theme-light & { color: #0f172a; }
    .theme-dark & { color: #f8fafc; }
  }
}

.product-version {
  font-family: monospace;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 99px;
  font-weight: bold;
  
  .theme-light & {
    background-color: rgba(0, 0, 0, 0.04);
    color: #64748b;
  }
  .theme-dark & {
    background-color: rgba(255, 255, 255, 0.05);
    color: #94a3b8;
  }
}

// 突触脉冲仪
.ai-cognition-core-wrapper {
  position: relative;
  width: 220px;
  height: 220px;
  margin: 30px auto;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 4;
}

.ai-core-ring {
  position: absolute;
  border-radius: 50%;
  border: 1.5px solid transparent;
  
  &.outer-ring {
    width: 200px;
    height: 200px;
    border-style: dashed;
    animation: rotateRingClockwise 18s linear infinite;
    
    .theme-light & { border-color: rgba(79, 70, 229, 0.35); }
    .theme-dark & { border-color: rgba(56, 189, 248, 0.35); }
  }

  &.middle-ring {
    width: 150px;
    height: 150px;
    animation: rotateRingCounterClockwise 12s linear infinite;
    
    .theme-light & {
      border-color: rgba(168, 85, 247, 0.4);
      border-top-color: #a855f7;
    }
    .theme-dark & {
      border-color: rgba(129, 140, 248, 0.4);
      border-top-color: #818cf8;
    }
  }
  
  &.inner-pulse {
    width: 90px;
    height: 90px;
    animation: pulseRingBreath 2s infinite ease-in-out;
    
    .theme-light & {
      background-color: rgba(79, 70, 229, 0.05);
      border-color: rgba(79, 70, 229, 0.3);
    }
    .theme-dark & {
      background-color: rgba(56, 189, 248, 0.05);
      border-color: rgba(56, 189, 248, 0.3);
    }
  }
}

.ai-core-center-node {
  font-size: 46px;
  font-weight: 800;
  z-index: 5;
  animation: starFloat 3s infinite alternate ease-in-out;
  
  .theme-light & {
    color: #4f46e5;
    filter: drop-shadow(0 0 8px rgba(79, 70, 229, 0.6));
  }
  .theme-dark & {
    color: #38bdf8;
    filter: drop-shadow(0 0 16px rgba(56, 189, 248, 0.85));
  }
}

// 底部流光字
.visual-content {
  z-index: 5;
  margin-top: 10px;
}

.visual-title-shimmer {
  font-size: 44px;
  font-weight: 900;
  line-height: 1.25;
  letter-spacing: 0.02em;
  margin-bottom: 20px;
  background: linear-gradient(to right, #4f46e5 20%, #06b6d4 40%, #a855f7 60%, #4f46e5 80%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: textShimmer 4s linear infinite;

  .theme-dark & {
    background: linear-gradient(to right, #f8fafc 20%, #38bdf8 40%, #c084fc 60%, #f8fafc 80%);
    background-size: 200% auto;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.visual-desc {
  font-size: 13.5px;
  line-height: 1.75;
  font-weight: 500;
  max-width: 480px;
  
  .theme-light & { color: #475569; }
  .theme-dark & { color: #cbd5e1; }
}

.visual-footer {
  z-index: 5;
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 99px;
  border: 1px solid transparent;
  
  .theme-light & {
    background-color: rgba(16, 185, 129, 0.05);
    border-color: rgba(16, 185, 129, 0.15);
  }
  .theme-dark & {
    background-color: rgba(16, 185, 129, 0.08);
    border-color: rgba(16, 185, 129, 0.1);
  }
}

.status-dot {
  width: 6px;
  height: 6px;
  background-color: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.6);
  animation: pulse-green 2s infinite alternate;
}

.status-text {
  font-size: 10.5px;
  font-weight: bold;
  letter-spacing: 0.04em;
  color: #10b981;
}

// 右半分栏
.login-form-panel {
  flex: 1;
  padding: 60px 8%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  z-index: 3;
  position: relative;
  
  .theme-light & {
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(241, 245, 249, 0.95)) !important;
    border-left: 1px solid rgba(0, 0, 0, 0.04) !important;
  }
  .theme-dark & {
    background: linear-gradient(135deg, rgba(8, 10, 16, 0.96), rgba(15, 23, 42, 0.95)) !important;
    border-left: 1px solid rgba(255, 255, 255, 0.04) !important;
    box-shadow: -20px 0 50px rgba(0, 0, 0, 0.35);
  }
}

.holographic-form-shield {
  position: relative;
  width: 100%;
  max-width: 440px;
  margin: 0 auto;
  padding: 48px 40px;
  border-radius: 24px;
  backdrop-filter: blur(35px);
  transition: all 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
  
  .theme-light & {
    background: rgba(255, 255, 255, 0.55);
    border: 1px solid rgba(255, 255, 255, 0.55);
    box-shadow: 
      0 20px 50px -10px rgba(0, 0, 0, 0.03),
      inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }
  
  .theme-dark & {
    background: rgba(10, 15, 30, 0.42);
    border: 1px solid rgba(255, 255, 255, 0.05);
    box-shadow: 
      0 30px 70px -10px rgba(0, 0, 0, 0.35),
      inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }
  
  &:hover {
    transform: translateY(-4px);
    .theme-light & {
      box-shadow: 
        0 25px 60px -10px rgba(0, 0, 0, 0.05),
        0 0 15px rgba(79, 70, 229, 0.05);
    }
    .theme-dark & {
      box-shadow: 
        0 35px 80px -10px rgba(0, 0, 0, 0.45),
        0 0 20px rgba(56, 189, 248, 0.08);
    }
  }
}

.shield-corner {
  position: absolute;
  width: 14px;
  height: 14px;
  border: 1.5px solid transparent;
  pointer-events: none;
  
  .theme-light & { border-color: rgba(79, 70, 229, 0.25); }
  .theme-dark & { border-color: rgba(56, 189, 248, 0.25); }

  &.corner-tl { top: 12px; left: 12px; border-right: none; border-bottom: none; }
  &.corner-tr { top: 12px; right: 12px; border-left: none; border-bottom: none; }
  &.corner-bl { bottom: 12px; left: 12px; border-right: none; border-top: none; }
  &.corner-br { bottom: 12px; right: 12px; border-left: none; border-top: none; }
}

.form-header {
  margin-bottom: 32px;
}

.secure-hud-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border-radius: 4px;
  margin-bottom: 12px;
  font-family: monospace;
  font-size: 9px;
  font-weight: 800;
  
  .badge-dot {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    animation: pulse-green 1.5s infinite alternate;
    
    .theme-light & { background-color: #4f46e5; }
    .theme-dark & { background-color: #38bdf8; }
  }
  
  .theme-light & {
    background-color: rgba(79, 70, 229, 0.04);
    border-color: rgba(79, 70, 229, 0.15);
    color: #4f46e5;
  }
  .theme-dark & {
    background-color: rgba(56, 189, 248, 0.04);
    border-color: rgba(56, 189, 248, 0.15);
    color: #38bdf8;
  }
}

.form-title {
  font-size: 22px;
  font-weight: 850;
  margin-bottom: 6px;
  letter-spacing: 0.02em;
  
  .theme-light & { color: #0f172a; }
  .theme-dark & { color: #f8fafc; }
}

.form-subtitle {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  
  .theme-light & { color: #64748b; }
  .theme-dark & { color: #94a3b8; }
}

// 输入框 Label 样式
.modern-form-item {
  margin-bottom: 24px;
  
  .input-label {
    font-size: 11.5px;
    font-weight: 700;
    margin-bottom: 10px;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    
    .theme-light & { color: #64748b; }
    .theme-dark & { color: #94a3b8; }
  }
}

.input-glow-wrapper {
  position: relative;
  width: 100%;
  
  :deep(.el-input__wrapper) {
    background-color: transparent !important;
    border: 1px solid transparent !important;
    border-radius: 12px !important;
    height: 48px;
    padding: 0 16px !important;
    transition: all 0.3s;
    overflow: hidden;
    box-shadow: none !important;
    
    .theme-light & {
      border-color: rgba(0, 0, 0, 0.06) !important;
      background-color: #ffffff !important;
    }
    
    .theme-dark & {
      border-color: rgba(255, 255, 255, 0.05) !important;
      background-color: rgba(0, 0, 0, 0.3) !important;
    }

    &:hover {
      .theme-light & { border-color: rgba(79, 70, 229, 0.25) !important; }
      .theme-dark & { border-color: rgba(56, 189, 248, 0.25) !important; }
    }

    &.is-focus {
      box-shadow: none !important;
      .theme-light & {
        border-color: rgba(79, 70, 229, 0.5) !important;
      }
      .theme-dark & {
        border-color: rgba(56, 189, 248, 0.5) !important;
      }
    }
  }

  :deep(.el-input__inner) {
    font-size: 13.5px;
    
    .theme-light & { color: #0f172a; }
    .theme-dark & { color: #f8fafc; }
  }
  
  .prefix-icon {
    font-size: 14px;
    margin-right: 10px;
    opacity: 0.7;
  }
}

.input-active-line {
  position: absolute;
  left: 50%;
  bottom: 0;
  height: 2px;
  width: 0%;
  border-radius: 2px;
  transform: translateX(-50%);
  transition: width 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
  pointer-events: none;
  
  .theme-light & {
    background: linear-gradient(90deg, #6366f1, #a855f7);
  }
  .theme-dark & {
    background: linear-gradient(90deg, #38bdf8, #818cf8);
  }
  
  &.is-active {
    width: calc(100% - 24px);
  }
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 34px;
  font-size: 12.5px;
  
  :deep(.el-checkbox__label) {
    font-size: 12.5px;
    .theme-light & { color: #475569 !important; }
    .theme-dark & { color: #94a3b8 !important; }
  }
  
  .forgot-link {
    cursor: pointer;
    transition: color 0.2s;
    
    .theme-light & {
      color: #4f46e5;
      &:hover { color: #4338ca; }
    }
    .theme-dark & {
      color: #38bdf8;
      &:hover { color: #7dd3fc; }
    }
  }
}

:deep(.remember-checkbox) {
  .el-checkbox__label {
    font-size: 11px !important;
    font-weight: 700 !important;
    
    .theme-light & { color: #64748b !important; }
    .theme-dark & { color: #94a3b8 !important; }
  }
  
  .el-checkbox__inner {
    border-radius: 4px !important;
    
    .theme-light & { border-color: rgba(0, 0, 0, 0.15) !important; }
    .theme-dark & { border-color: rgba(255, 255, 255, 0.15) !important; }
  }
  
  &.is-checked {
    .el-checkbox__inner {
      .theme-light & { background-color: #4f46e5 !important; border-color: #4f46e5 !important; }
      .theme-dark & { background-color: #38bdf8 !important; border-color: #38bdf8 !important; }
    }
  }
}

.submit-btn-glow-container {
  position: relative;
  width: 100%;
}

.login-submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px !important;
  font-size: 13.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  position: relative;
  z-index: 2;
  border: none !important;
  
  .theme-light & {
    background: linear-gradient(135deg, #4f46e5, #6366f1) !important;
    color: #ffffff !important;
    box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  }

  .theme-dark & {
    background: linear-gradient(135deg, #38bdf8, #818cf8) !important;
    color: #0f172a !important;
  }
  
  &:hover {
    transform: translateY(-1px);
    
    .theme-light & {
      box-shadow: 0 8px 20px rgba(79, 70, 229, 0.35) !important;
    }
    .theme-dark & {
      box-shadow: 0 8px 24px rgba(56, 189, 248, 0.45) !important;
    }
  }
}

.submit-btn-glow-back {
  position: absolute;
  inset: 4px;
  filter: blur(14px);
  opacity: 0;
  transition: opacity 0.3s;
  z-index: 1;
  border-radius: 12px;
  pointer-events: none;
  
  .theme-light & {
    background: linear-gradient(135deg, #4f46e5, #a855f7);
  }
  .theme-dark & {
    background: linear-gradient(135deg, #38bdf8, #818cf8);
  }
  
  .submit-btn-glow-container:hover & {
    opacity: 0.6;
  }
}

.quick-control-row {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: 24px;
  border-top: 1px dashed;
  padding-top: 16px;
  
  .theme-light & {
    border-top-color: rgba(0, 0, 0, 0.05);
  }
  .theme-dark & {
    border-top-color: rgba(255, 255, 255, 0.05);
  }
}

.control-text-btn {
  background: transparent;
  border: none;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 6px;
  transition: all 0.2s;
  
  .theme-light & {
    color: #4f46e5;
    &:hover { background: rgba(79, 70, 229, 0.04); }
  }
  .theme-dark & {
    color: #38bdf8;
    &:hover { background: rgba(56, 189, 248, 0.04); }
  }
}

// ===== 视图二：授权重构仿真卡片 =====
.authorizing-wrapper {
  position: relative;
  z-index: 10;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.auth-loader-card {
  padding: 48px 40px;
  border-radius: 28px;
  text-align: center;
  width: 440px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 30px 80px -15px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(40px);
  transition: all 0.3s;
  
  .theme-light & {
    background: rgba(255, 255, 255, 0.65);
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow: 0 12px 40px -10px rgba(0, 0, 0, 0.06);
  }
  
  .theme-dark & {
    background: rgba(10, 15, 30, 0.55);
    border: 1px solid rgba(255, 255, 255, 0.06);
    box-shadow: 0 30px 80px -15px rgba(0, 0, 0, 0.5);
  }
}

.polaris-pulse-ring {
  position: relative;
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
}

.pulse-circle {
  position: absolute;
  border-radius: 50%;
  border: 1px solid;
  opacity: 0;
  
  .theme-light & { border-color: rgba(79, 70, 229, 0.6); }
  .theme-dark & { border-color: rgba(56, 189, 248, 0.6); }
  
  &.circle-1 {
    width: 60px;
    height: 60px;
    animation: brainPulse 2s infinite ease-out;
  }
  &.circle-2 {
    width: 80px;
    height: 80px;
    animation: brainPulse 2s infinite ease-out 0.66s;
  }
  &.circle-3 {
    width: 100px;
    height: 100px;
    animation: brainPulse 2s infinite ease-out 1.33s;
  }
}

.polaris-star-spark {
  font-size: 36px;
  z-index: 2;
  font-weight: 800;
  animation: starCoreBreath 2s infinite alternate ease-in-out;
  
  .theme-light & {
    color: #4f46e5;
    filter: drop-shadow(0 0 6px rgba(79, 70, 229, 0.6));
  }
  .theme-dark & {
    color: #38bdf8;
    filter: drop-shadow(0 0 12px rgba(56, 189, 248, 0.85));
  }
}

@keyframes brainPulse {
  0% { transform: scale(0.5); opacity: 0.8; }
  100% { transform: scale(1.4); opacity: 0; }
}

@keyframes starCoreBreath {
  0% { transform: scale(0.9) rotate(0deg); }
  100% { transform: scale(1.2) rotate(45deg); }
}

.auth-title {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.05em;
  margin-bottom: 6px;
  
  .theme-light & { color: #0f172a; }
  .theme-dark & { color: #f8fafc; }
}

.auth-sub {
  font-size: 9px;
  font-family: monospace;
  font-weight: bold;
  letter-spacing: 0.12em;
  color: var(--polaris-text-sub);
  margin-bottom: 24px;
}

// 模拟控制台日志
.auth-terminal-log {
  width: 100%;
  height: 130px;
  border-radius: 14px;
  padding: 16px;
  text-align: left;
  font-family: monospace;
  font-size: 11px;
  overflow: hidden;
  margin-bottom: 28px;
  border: 1px solid;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.1);
  
  .theme-light & {
    background-color: rgba(0, 0, 0, 0.03);
    border-color: rgba(0, 0, 0, 0.06);
    color: #334155;
  }
  .theme-dark & {
    background-color: rgba(0, 0, 0, 0.4);
    border-color: rgba(255, 255, 255, 0.04);
    color: #38bdf8;
  }
  
  .log-line {
    display: flex;
    gap: 8px;
    animation: logLineIn 0.3s cubic-bezier(0.25, 0.8, 0.25, 1) both;
  }
  
  .log-tag {
    font-weight: bold;
    opacity: 0.7;
  }
  
  .log-text {
    font-weight: 500;
    word-break: break-all;
  }
  
  .terminal-cursor {
    animation: cursorBlink 1s infinite steps(2, start);
    color: var(--polaris-text-sub);
    font-weight: bold;
    margin-top: -2px;
  }
}

@keyframes terminalFadeIn {
  from { opacity: 0; transform: translateY(3px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes cursorBlink {
  0%, 40% { opacity: 0; }
  60%, 100% { opacity: 1; }
}

.progress-bar-container {
  width: 100%;
  height: 4px;
  border-radius: 99px;
  overflow: hidden;
  margin-bottom: 12px;
  
  .theme-light & { background-color: rgba(0, 0, 0, 0.05); }
  .theme-dark & { background-color: rgba(255, 255, 255, 0.05); }
}

.progress-bar-fill {
  height: 100%;
  border-radius: 99px;
  transition: width 0.15s ease-out;
  
  .theme-light & {
    background: linear-gradient(90deg, #6366f1, #a855f7);
  }
  .theme-dark & {
    background: linear-gradient(90deg, #38bdf8, #818cf8, #ec4899);
  }
}

.progress-info-row {
  display: flex;
  justify-content: space-between;
  width: 100%;
  font-family: monospace;
  font-size: 10px;
  font-weight: bold;
  color: var(--polaris-text-sub);
  letter-spacing: 0.05em;
}

// ===== 🧭 右侧滑入式星轨罗盘控制极仪 =====
.stellar-control-dock {
  position: fixed;
  right: -241px;
  width: 295px;
  height: 54px;
  z-index: 9999;
  transition: all 0.6s cubic-bezier(0.25, 0.8, 0.25, 1);
  cursor: pointer;
  
  &.pos-top-right {
    top: 40px;
    bottom: auto;
  }
  
  &:hover {
    right: 20px;
    
    .dock-ring-decor {
      animation: rotateRingClockwise 8s linear infinite;
    }
  }
}

.dock-hologram-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px 0 54px;
  border-radius: 99px;
  backdrop-filter: blur(35px);
  border: 1px solid;
  transition: all 0.3s;
  
  .theme-light & {
    background: rgba(255, 255, 255, 0.85) !important;
    border-color: rgba(79, 70, 229, 0.3) !important;
    box-shadow: 
      0 10px 30px rgba(79, 70, 229, 0.06),
      0 0 15px rgba(79, 70, 229, 0.04) !important;
  }
  
  .theme-dark & {
    background: rgba(10, 15, 30, 0.85) !important;
    border-color: rgba(56, 189, 248, 0.25) !important;
    box-shadow: 
      0 12px 36px rgba(0, 0, 0, 0.45),
      0 0 20px rgba(56, 189, 248, 0.08) !important;
  }
}

.dock-ring-decor {
  position: absolute;
  left: 2px;
  top: 2px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 1.5px dashed;
  pointer-events: none;
  transition: all 0.3s;
  
  .theme-light & { 
    border-color: rgba(79, 70, 229, 0.45) !important; 
    filter: drop-shadow(0 0 2px rgba(79, 70, 229, 0.25));
  }
  
  .theme-dark & { 
    border-color: rgba(56, 189, 248, 0.55) !important; 
    filter: drop-shadow(0 0 4px rgba(56, 189, 248, 0.4));
  }
}

.dock-center-core {
  position: absolute;
  left: 8px;
  top: 8px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  z-index: 2;
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  
  .theme-light & { 
    background: #ffffff !important; 
    border: 1px solid rgba(79, 70, 229, 0.45) !important;
    color: #4f46e5 !important; 
    box-shadow: 0 2px 10px rgba(79, 70, 229, 0.2) !important;
  }
  
  .theme-dark & { 
    background: rgba(15, 23, 42, 0.95) !important; 
    border: 1px solid rgba(56, 189, 248, 0.6) !important;
    color: #38bdf8 !important; 
    text-shadow: 0 0 8px rgba(56, 189, 248, 0.8);
    box-shadow: 0 0 14px rgba(56, 189, 248, 0.45) !important;
  }
  
  .stellar-control-dock:hover & {
    transform: rotate(180deg) scale(1.05);
  }
}

.dock-sliding-panel {
  display: flex;
  align-items: center;
  gap: 16px;
  white-space: nowrap;
}

.dock-title {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  
  .theme-light & { color: #4f46e5; }
  .theme-dark & { color: #38bdf8; }
}

.theme-slider-track {
  position: relative;
  display: flex;
  padding: 3px;
  width: 130px;
  height: 32px;
  border-radius: 99px;
  border: 1px solid;
  
  .theme-light & {
    background-color: rgba(0, 0, 0, 0.03);
    border-color: rgba(0, 0, 0, 0.05);
  }
  
  .theme-dark & {
    background-color: rgba(0, 0, 0, 0.35);
    border-color: rgba(255, 255, 255, 0.05);
  }
}

.slider-opt {
  flex: 1;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  cursor: pointer;
  transition: color 0.3s;
  
  .theme-light & {
    color: #64748b;
    &.active { color: #ffffff !important; }
  }
  
  .theme-dark & {
    color: #94a3b8;
    &.active { color: #0f172a !important; }
  }
}

.slider-active-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  bottom: 2px;
  width: calc(50% - 2px);
  border-radius: 99px;
  z-index: 1;
  transition: transform 0.4s cubic-bezier(0.25, 0.8, 0.25, 1.25);
  
  &.light {
    transform: translateX(0);
    background: linear-gradient(135deg, #4f46e5, #6366f1);
    box-shadow: 0 2px 8px rgba(79, 70, 229, 0.35);
  }
  
  &.dark {
    transform: translateX(100%);
    background: linear-gradient(135deg, #38bdf8, #818cf8);
    box-shadow: 0 2px 8px rgba(56, 189, 248, 0.35);
  }
}

// ===== 🌀 全局引力波爆破涟漪 =====
.stellar-wave-ripple {
  position: fixed;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  z-index: 9998;
  pointer-events: none;
  transform: translate(-50%, -50%) scale(1);
  backdrop-filter: blur(5px);
  transform-origin: center center;
}

.stellar-wave-enter-active {
  transition: transform 0.65s cubic-bezier(0.1, 0.8, 0.1, 1), opacity 0.65s ease;
}
.stellar-wave-leave-active {
  transition: opacity 0.4s ease;
}
.stellar-wave-enter-from {
  transform: translate(-50%, -50%) scale(1);
  opacity: 0.95;
}
.stellar-wave-enter-to {
  transform: translate(-50%, -50%) scale(220);
  opacity: 0.15;
}
.stellar-wave-leave-from {
  opacity: 0.15;
}
.stellar-wave-leave-to {
  opacity: 0;
}

// ===== 量子信道滑动对齐验证端 =====
.slider-verify-shell {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: 
      linear-gradient(rgba(255,255,255,0.005) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255,255,255,0.005) 1px, transparent 1px);
    background-size: 16px 16px;
    pointer-events: none;
    z-index: 1;
  }
}

.slider-verify-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.05);
  
  html:not(.dark) & {
    border-bottom-color: rgba(0, 0, 0, 0.05);
  }
  
  .sv-header-icon {
    font-size: 14px;
    
    html:not(.dark) & { color: #4f46e5; }
    .dark & { color: #38bdf8; }
  }
  
  .sv-header-text {
    font-size: 13.5px;
    font-weight: 800;
    letter-spacing: 0.05em;
    
    html:not(.dark) & { color: #0f172a; }
    .dark & { color: #f8fafc; }
  }
}

// 核心对齐舱
.aligner-chamber {
  height: 140px;
  border-radius: 16px;
  position: relative;
  overflow: hidden;
  border: 1px solid;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding-bottom: 12px;
  
  html:not(.dark) & {
    background: rgba(0, 0, 0, 0.02);
    border-color: rgba(0, 0, 0, 0.05);
  }
  .dark & {
    background: rgba(0, 0, 0, 0.25);
    border-color: rgba(255, 255, 255, 0.04);
  }
}

.chamber-grid {
  position: absolute;
  inset: 0;
  background-size: 10px 10px;
  
  html:not(.dark) & {
    background-image: radial-gradient(rgba(0, 0, 0, 0.02) 1px, transparent 1px);
  }
  .dark & {
    background-image: radial-gradient(rgba(255, 255, 255, 0.015) 1px, transparent 1px);
  }
}

.quantum-groove {
  position: absolute;
  top: 30px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 2px dashed;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  
  html:not(.dark) & {
    border-color: rgba(0, 0, 0, 0.15);
    background: rgba(0, 0, 0, 0.03);
  }
  .dark & {
    border-color: rgba(255, 255, 255, 0.15);
    background: rgba(255, 255, 255, 0.03);
  }
  
  .groove-pulse-ring {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    border: 1px solid transparent;
    animation: groovePulse 2s infinite ease-in-out;
  }
  
  &.aligned {
    border-style: solid;
    transform: scale(1.05);
    
    html:not(.dark) & {
      border-color: #10b981;
      background: rgba(16, 185, 129, 0.1);
      box-shadow: 0 0 15px rgba(16, 185, 129, 0.3);
    }
    
    .dark & {
      border-color: #10b981;
      background: rgba(16, 185, 129, 0.15);
      box-shadow: 0 0 20px rgba(16, 185, 129, 0.4);
    }
    
    &::after {
      content: '';
      position: absolute;
      inset: -8px;
      border: 1px solid rgba(16, 185, 129, 0.3);
      border-radius: 50%;
      animation: grooveBurst 1s infinite alternate;
    }
  }
}

@keyframes groovePulse {
  0% { transform: scale(0.95); opacity: 0.3; border-color: currentColor; }
  50% { transform: scale(1.05); opacity: 0.7; border-color: currentColor; }
  100% { transform: scale(0.95); opacity: 0.3; border-color: currentColor; }
}

@keyframes grooveBurst {
  0% { transform: scale(0.9); opacity: 0.4; }
  100% { transform: scale(1.15); opacity: 0.8; }
}

.quantum-key {
  position: absolute;
  top: 30px;
  left: 0;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
  transition: border-color 0.2s, background-color 0.2s;
  
  html:not(.dark) & {
    background: linear-gradient(135deg, #4f46e5, #6366f1);
    box-shadow: 0 4px 10px rgba(79, 70, 229, 0.25);
    border: 2px solid rgba(255, 255, 255, 0.9);
    color: #ffffff;
  }
  
  .dark & {
    background: linear-gradient(135deg, #38bdf8, #818cf8);
    box-shadow: 0 4px 12px rgba(56, 189, 248, 0.3);
    border: 2px solid rgba(255, 255, 255, 0.85);
    color: #0f172a;
  }
  
  .key-core {
    font-size: 14px;
    font-weight: bold;
    z-index: 2;
  }
  
  .key-orbit {
    position: absolute;
    inset: -3px;
    border: 1px solid rgba(255,255,255,0.4);
    border-radius: 50%;
    animation: keyOrbitSpin 3s linear infinite;
  }
  
  &.success {
    background: #10b981 !important;
    border-color: #ffffff !important;
    box-shadow: 0 0 20px rgba(16, 185, 129, 0.6) !important;
    color: #ffffff !important;
  }
  
  &.error {
    background: #ef4444 !important;
    border-color: #ffffff !important;
    box-shadow: 0 0 15px rgba(239, 68, 68, 0.5) !important;
    color: #ffffff !important;
    animation: errorShake 0.4s ease-in-out;
  }
}

@keyframes keyOrbitSpin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.chamber-info-text {
  text-align: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  z-index: 2;
  color: var(--polaris-text-sub);
  
  .text-success {
    color: #10b981 !important;
    animation: bounce-text 0.4s ease-out;
  }
  .text-danger {
    color: #ef4444 !important;
  }
}

// 滑动轨道
.slider-track-wrap {
  height: 38px;
  border-radius: 99px;
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  border: 1px solid;
  
  html:not(.dark) & {
    background: rgba(0, 0, 0, 0.02);
    border-color: rgba(0, 0, 0, 0.05);
  }
  .dark & {
    background: rgba(0, 0, 0, 0.15);
    border-color: rgba(255, 255, 255, 0.03);
  }
}

.slider-track-bg {
  position: absolute;
  inset: 0;
  
  html:not(.dark) & {
    background: rgba(0, 0, 0, 0.01);
  }
}

.slider-track-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 0;
  border-radius: 99px 0 0 99px;
  transition: background-color 0.2s;
  
  html:not(.dark) & {
    background: linear-gradient(90deg, rgba(79, 70, 229, 0.05), rgba(79, 70, 229, 0.15));
  }
  .dark & {
    background: linear-gradient(90deg, rgba(56, 189, 248, 0.05), rgba(56, 189, 248, 0.18));
  }
  
  &.success {
    background: rgba(16, 185, 129, 0.15) !important;
  }
  &.error {
    background: rgba(239, 68, 68, 0.12) !important;
  }
}

// 手柄
.slider-handle {
  position: absolute;
  left: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid;
  transition: transform 0.05s, box-shadow 0.2s, background-color 0.2s, border-color 0.2s;
  z-index: 10;
  
  html:not(.dark) & {
    background: #ffffff;
    border-color: rgba(0, 0, 0, 0.1);
    box-shadow: 0 2px 5px rgba(0, 0, 0, 0.08);
    color: #4f46e5;
  }
  
  .dark & {
    background: #1e293b;
    border-color: rgba(255, 255, 255, 0.08);
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
    color: #cbd5e1;
  }
  
  &:hover, &.dragging {
    cursor: grabbing;
    html:not(.dark) & {
      border-color: #4f46e5;
      box-shadow: 0 0 8px rgba(79, 70, 229, 0.25);
    }
    .dark & {
      border-color: #38bdf8;
      color: #38bdf8;
      box-shadow: 0 0 10px rgba(56, 189, 248, 0.3);
    }
  }
  
  &.success {
    background: #10b981 !important;
    border-color: #10b981 !important;
    color: #ffffff !important;
    box-shadow: 0 0 12px rgba(16, 185, 129, 0.4) !important;
  }
  
  &.error {
    background: #ef4444 !important;
    border-color: #ef4444 !important;
    color: #ffffff !important;
    box-shadow: 0 0 10px rgba(239, 68, 68, 0.3) !important;
  }
  
  .handle-arrow {
    font-size: 11px;
    font-weight: bold;
    user-select: none;
  }
}

.sv-cancel-btn {
  background: transparent;
  border: none;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.2s;
  
  html:not(.dark) & {
    color: #64748b;
    &:hover { background: rgba(0, 0, 0, 0.03); color: #ef4444; }
  }
  .dark & {
    color: #94a3b8;
    &:hover { background: rgba(255, 255, 255, 0.03); color: #fca5a5; }
  }
}

// ===== 响应式适配 =====
@media (max-width: 992px) {
  .login-visual-panel {
    display: none;
  }
  .login-form-panel {
    padding: 60px 40px;
    flex: 1;
  }
}

// ===== 动画关键帧 =====
@keyframes rotateRingClockwise {
  to { transform: rotate(360deg); }
}

@keyframes rotateRingCounterClockwise {
  to { transform: rotate(-360deg); }
}

@keyframes pulseRingBreath {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.1); opacity: 0.5; }
}

@keyframes pulse-green {
  0% { transform: scale(0.9); opacity: 0.5; }
  100% { transform: scale(1.2); opacity: 1; }
}

@keyframes bounce-text {
  0% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
  100% { transform: translateY(0); }
}

@keyframes visualFloating {
  0% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
  100% { transform: translateY(0); }
}

.float-animation {
  animation: visualFloating 6s infinite ease-in-out;
}

.float-animation-delayed {
  animation: visualFloating 8s infinite ease-in-out 1s;
}

@keyframes morph {
  0% { border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%; }
  50% { border-radius: 70% 30% 30% 70% / 70% 70% 30% 30%; }
  100% { border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%; }
}

@keyframes float-visual-1 {
  0% { transform: translate(0, 0) rotate(0deg); }
  100% { transform: translate(40px, 30px) rotate(180deg); }
}

@keyframes float-visual-2 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-30px, -40px) scale(1.15); }
}

@keyframes cascadeFall {
  0% { transform: translateY(0); opacity: 0; }
  10% { opacity: 0.3; }
  90% { opacity: 0.3; }
  100% { transform: translateY(550px); opacity: 0; }
}

@keyframes errorShake {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-4px); }
  40%, 80% { transform: translateX(4px); }
}

@keyframes nodeBreath {
  0% { transform: scale(0.85); opacity: 0.5; }
  100% { transform: scale(1.15); opacity: 1; }
}

@keyframes nodeRadarPulse {
  0% { r: 3px; opacity: 0.8; }
  100% { r: 12px; opacity: 0; }
}

@keyframes float-orb-1 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(120px, 100px) scale(1.25); }
}
@keyframes float-orb-2 {
  0% { transform: translate(0, 0) scale(1.1); }
  100% { transform: translate(-140px, -80px) scale(0.85); }
}
@keyframes float-orb-3 {
  0% { transform: translate(-50%, -50%) rotate(0deg) scale(1); }
  100% { transform: translate(-30%, -70%) rotate(180deg) scale(1.2); }
}

@keyframes sparkSpin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes textShimmer {
  to { background-position: 200% center; }
}

@keyframes starFloat {
  0% { transform: translateY(0) rotate(0deg) scale(1); }
  100% { transform: translateY(-8px) rotate(22deg) scale(1.1); }
}

@keyframes logLineIn {
  0% { opacity: 0; transform: translateY(5px); }
  100% { opacity: 1; transform: translateY(0); }
}
</style>
