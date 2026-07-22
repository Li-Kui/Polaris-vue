<template>
  <div class="lock-container" :class="isDark ? 'theme-dark' : 'theme-light'">
    <!-- 动态粒子背景 -->
    <canvas ref="particleCanvas" class="particle-bg"></canvas>

    <!-- 时钟 -->
    <div class="lock-time">{{ currentTime }}</div>
    <div class="lock-date">{{ currentDate }}</div>

    <!-- 锁屏卡片 -->
    <div class="lock-card">
      <div class="avatar-wrap">
        <img :src="userStore.avatar" class="lock-avatar" @error="onAvatarError" />
        <div class="lock-icon">🔒</div>
      </div>
      <div class="lock-username">{{ userStore.nickName }}</div>
      <div class="lock-hint">系统已锁定，请输入密码解锁</div>

      <div class="input-wrap" :class="{ shake: isShaking }">
        <input ref="passwordInput" v-model="password" type="password" placeholder="请输入登录密码" class="lock-input" @keydown.enter="handleUnlock" autocomplete="off" />
        <button class="unlock-btn" @click="handleUnlock" :disabled="loading">
          <span v-if="!loading">→</span>
          <span v-else class="loading-dot">···</span>
        </button>
      </div>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

      <div class="lock-footer">
        <a href="javascript:;" @click="goLogin">退出重新登录</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import useSettingsStore from '@/store/modules/settings'
import {unlockScreen} from '@/api/login'
import defAva from '@/assets/images/profile.jpg'

const router = useRouter()
const userStore = useUserStore()
const lockStore = useLockStore()
const settingsStore = useSettingsStore()

const isDark = computed(() => settingsStore.isDark)

const password = ref('')
const loading = ref(false)
const errorMsg = ref('')
const isShaking = ref(false)
const currentTime = ref('')
const currentDate = ref('')
const passwordInput = ref(null)
const particleCanvas = ref(null)

let timer = null
let animationId = null
let particles = []

const onAvatarError = (e) => {
  e.target.src = defAva
}

const startClock = () => {
  const update = () => {
    const now = new Date()
    const pad = n => String(n).padStart(2, '0')
    currentTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
    const days = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
    currentDate.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 ${days[now.getDay()]}`
  }
  update()
  timer = setInterval(update, 1000)
}

const handleUnlock = async () => {
  if (!password.value) {
    showError('请输入密码')
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await unlockScreen(password.value)
    const lockPath = lockStore.lockPath
    lockStore.unlockScreen()
    router.replace(lockPath)
  } catch (err) {
    const msg = err.message || err.toString()
    showError(msg)
    password.value = ''
    nextTick(() => passwordInput.value?.focus())
  } finally {
    loading.value = false
  }
}

const showError = (msg) => {
  errorMsg.value = msg
  isShaking.value = true
  setTimeout(() => { isShaking.value = false }, 600)
}

const goLogin = () => {
  lockStore.unlockScreen()
  userStore.logOut().then(() => {
    router.push('/login')
  })
}

const initParticles = () => {
  const canvas = particleCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const resize = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)

  particles = Array.from({ length: 80 }, () => ({
    x: Math.random() * canvas.width,
    y: Math.random() * canvas.height,
    r: Math.random() * 2 + 1,
    dx: (Math.random() - 0.5) * 0.6,
    dy: (Math.random() - 0.5) * 0.6,
    alpha: Math.random() * 0.5 + 0.2
  }))

  const draw = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    
    // 根据当前模式自适应粒子的颜色与线条透明度
    const colorRGB = isDark.value ? '255,255,255' : '79,70,229'
    const lineAlphaBase = isDark.value ? 0.15 : 0.22

    particles.forEach(p => {
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(${colorRGB},${p.alpha})`
      ctx.fill()
      p.x += p.dx
      p.y += p.dy
      if (p.x < 0 || p.x > canvas.width) p.dx *= -1
      if (p.y < 0 || p.y > canvas.height) p.dy *= -1
    })
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j]
        const dist = Math.hypot(a.x - b.x, a.y - b.y)
        if (dist < 120) {
          ctx.beginPath()
          ctx.moveTo(a.x, a.y)
          ctx.lineTo(b.x, b.y)
          ctx.strokeStyle = `rgba(${colorRGB},${lineAlphaBase * (1 - dist / 120)})`
          ctx.lineWidth = 0.5
          ctx.stroke()
        }
      }
    }
    animationId = requestAnimationFrame(draw)
  }
  draw()
}

onMounted(() => {
  startClock()
  initParticles()
  nextTick(() => passwordInput.value?.focus())
})

onBeforeUnmount(() => {
  clearInterval(timer)
  cancelAnimationFrame(animationId)
})
</script>

<style scoped>
.lock-container {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
}

.particle-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.lock-time {
  position: relative;
  z-index: 1;
  font-size: 72px;
  font-weight: 200;
  letter-spacing: 4px;
  margin-bottom: 8px;
  font-variant-numeric: tabular-nums;
}

.lock-date {
  position: relative;
  z-index: 1;
  font-size: 15px;
  margin-bottom: 48px;
  letter-spacing: 2px;
}

.lock-card {
  position: relative;
  z-index: 1;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 40px 48px;
  width: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-wrap {
  position: relative;
  margin-bottom: 16px;
}

.lock-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}

.lock-icon {
  position: absolute;
  bottom: -4px;
  right: -4px;
  border-radius: 50%;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  backdrop-filter: blur(8px);
}

.lock-username {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 6px;
  letter-spacing: 1px;
}

.lock-hint {
  font-size: 13px;
  margin-bottom: 28px;
}

.input-wrap {
  width: 100%;
  display: flex;
  align-items: center;
  border-radius: 50px;
  padding: 4px 4px 4px 20px;
  transition: border-color 0.3s, background-color 0.3s;
}

.input-wrap.shake {
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-8px); }
  40% { transform: translateX(8px); }
  60% { transform: translateX(-6px); }
  80% { transform: translateX(6px); }
}

.lock-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 15px;
  padding: 10px 0;
}

.unlock-btn {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: none;
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  transition: transform 0.2s, opacity 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.unlock-btn:hover:not(:disabled) {
  transform: scale(1.08);
}

.unlock-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-dot {
  font-size: 13px;
  letter-spacing: 1px;
}

.error-msg {
  margin-top: 14px;
  color: #ff7675;
  font-size: 13px;
  text-align: center;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.lock-footer {
  margin-top: 24px;
}

.lock-footer a {
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s;
}

/* ================== 暗色模式样式 ================== */
.lock-container.theme-dark {
  background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
}
.theme-dark .lock-time {
  color: #fff;
  text-shadow: 0 0 40px rgba(255,255,255,0.3);
}
.theme-dark .lock-date {
  color: rgba(255,255,255,0.6);
}
.theme-dark .lock-card {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.4);
}
.theme-dark .lock-avatar {
  border: 3px solid rgba(255,255,255,0.3);
}
.theme-dark .lock-icon {
  background: rgba(255,255,255,0.15);
}
.theme-dark .lock-username {
  color: #fff;
}
.theme-dark .lock-hint {
  color: rgba(255,255,255,0.5);
}
.theme-dark .input-wrap {
  background: rgba(255,255,255,0.1);
  border: 1px solid rgba(255,255,255,0.2);
}
.theme-dark .input-wrap:focus-within {
  border-color: rgba(255,255,255,0.6);
  background: rgba(255,255,255,0.13);
}
.theme-dark .lock-input {
  color: #fff;
}
.theme-dark .lock-input::placeholder {
  color: rgba(255,255,255,0.35);
}
.theme-dark .unlock-btn {
  background: linear-gradient(135deg, #667eea, #764ba2);
}
.theme-dark .lock-footer a {
  color: rgba(255,255,255,0.4);
}
.theme-dark .lock-footer a:hover {
  color: rgba(255,255,255,0.8);
}

/* ================== 亮色模式样式 ================== */
.lock-container.theme-light {
  background: linear-gradient(135deg, #f3f4f6, #e0e7ff, #f3e8ff);
}
.theme-light .lock-time {
  color: #0f172a;
  text-shadow: 0 0 40px rgba(15, 23, 42, 0.08);
}
.theme-light .lock-date {
  color: rgba(15, 23, 42, 0.6);
}
.theme-light .lock-card {
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 25px 60px rgba(15, 23, 42, 0.1);
}
.theme-light .lock-avatar {
  border: 3px solid rgba(15, 23, 42, 0.1);
}
.theme-light .lock-icon {
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(15, 23, 42, 0.06);
}
.theme-light .lock-username {
  color: #0f172a;
}
.theme-light .lock-hint {
  color: rgba(15, 23, 42, 0.5);
}
.theme-light .input-wrap {
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(15, 23, 42, 0.12);
}
.theme-light .input-wrap:focus-within {
  border-color: rgba(79, 70, 229, 0.5);
  background: rgba(255, 255, 255, 0.9);
}
.theme-light .lock-input {
  color: #0f172a;
}
.theme-light .lock-input::placeholder {
  color: rgba(15, 23, 42, 0.38);
}
.theme-light .unlock-btn {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
}
.theme-light .lock-footer a {
  color: rgba(15, 23, 42, 0.55);
}
.theme-light .lock-footer a:hover {
  color: #4f46e5;
}
</style>
