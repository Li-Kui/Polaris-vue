<template>
  <div
    :class="['login-container', 'login', `scheme-${activeScheme}`, activeTheme]"
    :style="globalStyle"
    @mousemove="handleMouseMove"
    @mouseleave="handleMouseLeave"
  >
    <!-- 右上角个性化视觉切换器 -->
    <ThemeSwitcher
      v-model:model-theme="activeTheme"
      v-model:model-scheme="activeScheme"
    />

    <!-- 登录主体包裹层 -->
    <div class="login-wrapper">
      <!-- 背景视觉方案过渡层 -->
      <transition name="scheme-fade" mode="out-in">
        <component
          :is="schemeComponents[activeScheme] || schemeComponents['flux']"
          :key="activeScheme"
          :theme="activeTheme"
          :project-title="projectTitle"
          class="scheme-layer"
        />
      </transition>

      <!-- 登录表单卡片层 -->
      <div class="login-card-container">
        <LoginCard
          ref="loginCardRef"
          :scheme="activeScheme"
          :project-title="projectTitle"
          @login-success="handleLoginSuccess"
          @request-verify="verifyRef?.show()"
        />
      </div>
    </div>

    <!-- 全局页脚 -->
    <footer class="el-login-footer">
      <span>{{ footerContent }}</span>
    </footer>

    <!-- 图形滑块验证码 -->
    <Verify
      ref="verifyRef"
      :captcha-type="'blockPuzzle'"
      :mode="'pop'"
      @success="handleVerifySuccess"
    />
  </div>
</template>

<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import defaultSettings from '@/settings'
import Verify from "@/components/verifition/Verify.vue"
import ThemeSwitcher from "./login/ThemeSwitcher.vue"
import LoginCard from "./login/LoginCard.vue"

// 引入全新的 3 种 AI 背景方案组件
import SchemeFlux from "./login/schemes/NewSchemeAwakening.vue"
import SchemePulse from "./login/schemes/NewSchemeSingularity.vue"
import SchemeOrbit from "./login/schemes/NewSchemeHorizon.vue"

const footerContent = defaultSettings.footerContent
const router = useRouter()
const projectTitle = import.meta.env.VITE_APP_TITLE || 'Polaris Vue'

const schemeComponents = {
  flux:  SchemeFlux,
  pulse: SchemePulse,
  orbit: SchemeOrbit,
}

const verifyRef = ref(null)
const loginCardRef = ref(null)

// 3D 全局鼠标物理视差感应
const mouseX = ref(0)
const mouseY = ref(0)

const globalStyle = computed(() => {
  return {
    '--mouse-x': mouseX.value,
    '--mouse-y': mouseY.value
  }
})

function handleMouseMove(e) {
  const x = e.clientX - window.innerWidth / 2
  const y = e.clientY - window.innerHeight / 2
  mouseX.value = x / (window.innerWidth / 2)
  mouseY.value = y / (window.innerHeight / 2)
}

function handleMouseLeave() {
  mouseX.value = 0
  mouseY.value = 0
}

function handleVerifySuccess(params) {
  loginCardRef.value?.handleVerifySuccess(params)
}

function handleLoginSuccess({ path, query }) {
  router.push({ path, query })
}

// 偏好持久化
const THEME_KEY = 'polaris.login.theme'
const SCHEME_KEY = 'polaris.login.scheme'

const validThemes = ['light', 'dark']
const validSchemes = ['flux', 'pulse', 'orbit']

const rawTheme  = localStorage.getItem(THEME_KEY) || 'light'
const rawScheme = localStorage.getItem(SCHEME_KEY) || 'flux'

const activeTheme  = ref(validThemes.includes(rawTheme) ? rawTheme : 'light')
const activeScheme = ref(validSchemes.includes(rawScheme) ? rawScheme : 'flux')

function applyTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

watch(activeTheme, (v) => { localStorage.setItem(THEME_KEY, v); applyTheme(v) })
watch(activeScheme, (v) => { localStorage.setItem(SCHEME_KEY, v) })

onMounted(() => applyTheme(activeTheme.value))
</script>

<style lang='scss' scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  width: 100vw;
  background-color: var(--bg);
  transition: var(--bg-transition);
  position: relative;
  overflow: hidden;
}

.login-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  position: relative;
}

.scheme-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.login-card-container {
  display: flex;
  flex: 1;
  justify-content: center;
  align-items: center;
  padding: var(--space-5);
  position: relative;
  z-index: 10;
}

// Orbit 方案：卡片居右
.scheme-orbit .login-card-container {
  justify-content: flex-end;
  padding-right: 10%;
}

// Pulse 方案：卡片偏右（留出左侧 HUD 面板空间）
.scheme-pulse .login-card-container {
  justify-content: flex-end;
  padding-right: 12%;
}

.el-login-footer {
  position: fixed;
  bottom: var(--space-4);
  left: 0;
  right: 0;
  text-align: center;
  font-size: var(--fs-caption);
  color: var(--ink-3);
  z-index: 10;
  pointer-events: none;
  font-family: var(--font-body);
}

@media (max-width: 960px) {
  .scheme-orbit .login-card-container,
  .scheme-pulse .login-card-container {
    justify-content: center;
    padding-right: var(--space-5);
  }
}
</style>
