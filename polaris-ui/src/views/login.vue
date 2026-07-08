<template>
  <div class="login-wrapper">

    <!-- 全页面光球背景层（覆盖整个宽度，两侧共用） -->
    <div ref="parallaxBg" class="parallax-layer">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
      <div class="orb orb-4"></div>
    </div>

    <!-- 星点装饰 -->
    <div class="stars-layer"></div>

    <!-- ===== 左侧：品牌展示区 ===== -->
    <div class="brand-panel">
      <!-- 流星 -->
      <div class="meteors">
        <span class="meteor meteor-1"></span>
        <span class="meteor meteor-2"></span>
        <span class="meteor meteor-3"></span>
      </div>

      <!-- 居中品牌内容 -->
      <div class="brand-center">
        <!-- 北辰星图标容器（包括外圈旋转光环 + 脚冲圆） -->
        <div class="star-wrap">
          <!-- 外圈旋转光环 -->
          <div class="star-ring ring-1"></div>
          <div class="star-ring ring-2"></div>
          <!-- 光脆冲圆 -->
          <div class="star-pulse"></div>
          <!-- 主体 SVG -->
          <svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M50 0C50 35 65 50 100 50C65 50 50 65 50 100C50 65 35 50 0 50C35 50 50 35 50 0Z"
              fill="url(#lg1)"/>
            <defs>
              <linearGradient id="lg1" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#e0e7ff"/>
                <stop offset="100%" stop-color="#a5b4fc"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <h1 class="brand-title">Polaris 北辰</h1>
        <div class="brand-rule"></div>
        <p class="brand-quote">"为政以德，譬如北辰，居其所而众星共之。"</p>
        <p class="brand-source">—— 《论语·为政》</p>
      </div>

      <!-- 底部 -->
      <p class="brand-bottom">© 2026 Polaris AI Inc.</p>
    </div>

    <!-- ===== 右侧：登录表单区 =====-->
    <div class="form-panel">

      <!-- 浮动毛玻璃卡片 -->
      <div class="login-card">
        <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form">

          <!-- 卡片内小品牌徽章 -->
          <div class="card-badge">
            <svg class="badge-star" viewBox="0 0 100 100" fill="none">
              <path d="M50 0C50 35 65 50 100 50C65 50 50 65 50 100C50 65 35 50 0 50C35 50 50 35 50 0Z" fill="url(#lg2)"/>
              <defs>
                <linearGradient id="lg2" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#a5b4fc"/>
                  <stop offset="100%" stop-color="#818cf8"/>
                </linearGradient>
              </defs>
            </svg>
            <span class="badge-text">北辰 AI</span>
          </div>

          <div class="form-headline">
            <h2>欢迎回来</h2>
            <p>请登录您的账号以继续</p>
          </div>

          <!-- 用户名 -->
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              auto-complete="off"
              placeholder="账号"
              type="text"
            >
              <svg-icon slot="prefix" class="el-input__icon input-icon" icon-class="user"/>
            </el-input>
          </el-form-item>

          <!-- 密码 -->
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              auto-complete="off"
              placeholder="密码"
              type="password"
              @keyup.enter.native="handleLogin"
            >
              <svg-icon slot="prefix" class="el-input__icon input-icon" icon-class="password"/>
            </el-input>
          </el-form-item>

          <!-- 记住密码 -->
          <div class="form-options">
            <el-checkbox v-model="loginForm.rememberMe" class="custom-checkbox">记住密码</el-checkbox>
            <router-link v-if="register" :to="'/register'" class="link-type">立即注册</router-link>
          </div>

          <!-- 登录按钮 -->
          <el-form-item style="width:100%;margin-top:12px;margin-bottom:0;">
            <el-button
              :loading="loading"
              size="medium"
              class="submit-btn"
              type="primary"
              @click.native.prevent="handleLogin"
            >
              <span v-if="!loading">登 录</span>
              <span v-else>登 录 中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <p class="form-footer">{{ footerContent }}</p>
    </div>

    <!-- 滑动验证码（完整保留） -->
    <Verify ref="verify" :captcha-type="'blockPuzzle'" :mode="'pop'" @success="success"/>
  </div>
</template>

<script>
import {getCodeImg} from "@/api/login"
import Cookies from "js-cookie"
import {decrypt, encrypt} from '@/utils/jsencrypt'
import defaultSettings from '@/settings'
import Verify from "@/components/verifition/Verify"

export default {
  name: "Login",
  components: {Verify},
  data() {
    return {
      title: process.env.VUE_APP_TITLE,
      footerContent: defaultSettings.footerContent,
      codeUrl: "",
      loginForm: {
        username: "admin",
        password: "admin123",
        rememberMe: false,
        captchaVerification: ""
      },
      loginRules: {
        username: [{required: true, trigger: "blur", message: "请输入您的账号"}],
        password: [{required: true, trigger: "blur", message: "请输入您的密码"}]
      },
      loading: false,
      captchaEnabled: true,
      register: false,
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler(route) { this.redirect = route.query && route.query.redirect },
      immediate: true
    }
  },
  created() {
    this.getCode()
    this.getCookie()
  },
  mounted() {
    this.initParallax()
  },
  beforeDestroy() {
    if (this._handleMouse) window.removeEventListener('mousemove', this._handleMouse)
  },
  methods: {
    initParallax() {
      const bg = this.$refs.parallaxBg
      if (!bg) return
      const handleMouse = (e) => {
        const cx = window.innerWidth / 2
        const cy = window.innerHeight / 2
        const dx = (e.clientX - cx) / cx  // -1 ~ 1
        const dy = (e.clientY - cy) / cy  // -1 ~ 1
        // 视差偏移：每个光球有不同系数，产生深度层次
        bg.style.transform = `translate(${dx * 18}px, ${dy * 12}px)`
        // 单独对各 orb 施加更细腻的差异偏移
        const orbs = bg.querySelectorAll('.orb')
        if (orbs[0]) orbs[0].style.transform = `translate(${dx * 25}px, ${dy * 18}px)`
        if (orbs[1]) orbs[1].style.transform = `translate(${-dx * 20}px, ${-dy * 15}px)`
        if (orbs[2]) orbs[2].style.transform = `translate(${dx * 12}px, ${-dy * 22}px)`
      }
      window.addEventListener('mousemove', handleMouse)
      this._handleMouse = handleMouse
    },
    getCode() {
      getCodeImg().then(res => {
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
      })
    },
    getCookie() {
      const username = Cookies.get("username")
      const password = Cookies.get("password")
      const rememberMe = Cookies.get('rememberMe')
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
      }
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          if (this.captchaEnabled) {
            this.$refs.verify.show()
          } else {
            this.loading = true
            this.submitLogin()
          }
        }
      })
    },
    success(params) {
      this.loginForm.captchaVerification = params.captchaVerification
      this.loading = true
      this.submitLogin()
    },
    submitLogin() {
      if (this.loginForm.rememberMe) {
        Cookies.set("username", this.loginForm.username, {expires: 30})
        Cookies.set("password", encrypt(this.loginForm.password), {expires: 30})
        Cookies.set('rememberMe', this.loginForm.rememberMe, {expires: 30})
      } else {
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove('rememberMe')
      }
      this.$store.dispatch("Login", this.loginForm).then(() => {
        this.$router.push({path: this.redirect || "/"}).catch(() => {})
      }).catch(() => { this.loading = false })
    }
  }
}
</script>

<style lang="scss" rel="stylesheet/scss" scoped>
/* ================================================
   全局容器
   ================================================ */
.login-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
  // 全页底色——两侧共享
  background: #040a14;
}

/* ================================================
   左侧：品牌视觉区（背景透明，显示共用光球）
   ================================================ */
.brand-panel {
  position: relative;
  flex: 1.15;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: 56px 60px;
  overflow: visible; // 允许内容超资
  background: transparent; // 透明！展示全页共用光球

  @media (max-width: 960px) {
    display: none;
  }
}

/* 视差容器：覆盖整个页面，两侧共用 */
.parallax-layer {
  position: absolute;
  top: -15%;
  left: -10%;
  width: 120%;
  height: 130%;
  pointer-events: none;
  z-index: 0;
  transition: transform 0.15s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

/* 有机光球 —— 产生自然流动的极光感 */
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  mix-blend-mode: screen;
  animation: orbFloat 24s ease-in-out infinite;
  pointer-events: none;
}

.orb-1 {
  width: 60vw;
  height: 60vw;
  top: -20%;
  left: -15%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.55) 0%, transparent 65%);
  animation-duration: 26s;
}

.orb-2 {
  width: 55vw;
  height: 55vw;
  bottom: -15%;
  right: 5%; // 伸入右侧表单区
  background: radial-gradient(circle, rgba(6, 182, 212, 0.65) 0%, transparent 65%);
  animation-duration: 22s;
  animation-delay: -8s;
}

.orb-3 {
  width: 42vw;
  height: 42vw;
  top: 30%;
  left: 30%;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.42) 0%, transparent 65%);
  animation-duration: 30s;
  animation-delay: -15s;
}

// 右侧资际光晕，让右侧面板也有彩色渐变
.orb-4 {
  width: 45vw;
  height: 45vw;
  top: 5%;
  right: -8%;
  background: radial-gradient(circle, rgba(124, 58, 237, 0.5) 0%, transparent 65%);
  animation-duration: 28s;
  animation-delay: -5s;
}

@keyframes orbFloat {
  0%   { transform: translate(0, 0) scale(1); }
  25%  { transform: translate(6%, 10%) scale(1.1); }
  50%  { transform: translate(12%, 4%) scale(0.93); }
  75%  { transform: translate(4%, 12%) scale(1.06); }
  100% { transform: translate(0, 0) scale(1); }
}

/* CSS 全页面星点 */
.stars-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
  background-image:
    radial-gradient(1px 1px at 8% 12%, rgba(255,255,255,0.7) 0%, transparent 100%),
    radial-gradient(1px 1px at 22% 38%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 38% 7%, rgba(255,255,255,0.6) 0%, transparent 100%),
    radial-gradient(1px 1px at 55% 22%, rgba(255,255,255,0.45) 0%, transparent 100%),
    radial-gradient(1px 1px at 73% 52%, rgba(255,255,255,0.5) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 18% 68%, rgba(255,255,255,0.55) 0%, transparent 100%),
    radial-gradient(1px 1px at 88% 14%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 48% 78%, rgba(255,255,255,0.45) 0%, transparent 100%),
    radial-gradient(1px 1px at 65% 88%, rgba(255,255,255,0.35) 0%, transparent 100%),
    radial-gradient(1px 1px at 92% 62%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 78% 33%, rgba(255,255,255,0.3) 0%, transparent 100%),
    radial-gradient(1.5px 1.5px at 33% 85%, rgba(255,255,255,0.5) 0%, transparent 100%);
  animation: starsTwinkle 6s ease-in-out infinite alternate;
}

@keyframes starsTwinkle {
  0%   { opacity: 0.5; }
  100% { opacity: 1; }
}

/* 品牌中心内容 */
.brand-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin: auto;
  z-index: 2;
  animation: fadeInUp 1.2s cubic-bezier(0.22, 1, 0.36, 1) both;

  .star-wrap {
    position: relative;
    width: 90px;
    height: 90px;
    margin-bottom: 28px;
    display: flex;
    justify-content: center;
    align-items: center;

    svg {
      position: relative;
      z-index: 3;
      width: 68px;
      height: 68px;
      filter: drop-shadow(0 0 20px rgba(165, 180, 252, 0.75));
      animation: starGlow 3s ease-in-out infinite alternate;
    }
  }

  .brand-title {
    margin: 0 0 20px;
    font-size: 44px;
    font-weight: 700;
    color: #ffffff;
    letter-spacing: 4px;
    text-shadow: 0 0 40px rgba(165, 180, 252, 0.4);
    font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  }

  .brand-rule {
    width: 48px;
    height: 3px;
    background: linear-gradient(to right, #818cf8, #06b6d4);
    border-radius: 2px;
    margin: 0 auto 24px;
  }

  .brand-quote {
    margin: 0 0 10px;
    font-size: 16px;
    line-height: 1.8;
    color: rgba(255, 255, 255, 0.65);
    letter-spacing: 1px;
    max-width: 360px;
  }

  .brand-source {
    margin: 0;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.3);
    letter-spacing: 1px;
  }
}

@keyframes starGlow {
  0%   { filter: drop-shadow(0 0 8px rgba(165, 180, 252, 0.4)); transform: scale(0.82); }
  100% { filter: drop-shadow(0 0 40px rgba(165, 180, 252, 1)); transform: scale(1.18); }
}

/* 外圈旋转光环 */
.star-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid transparent;
  pointer-events: none;
}

.ring-1 {
  width: 110px;
  height: 110px;
  // conic 渐变仅占圆周 30% 为亮色光环
  background: conic-gradient(
    from 0deg,
    transparent 0%,
    rgba(165, 180, 252, 0.6) 20%,
    transparent 40%
  );
  -webkit-mask: radial-gradient(transparent 48px, black 49.5px, black 54px, transparent 55px);
  mask: radial-gradient(transparent 48px, black 49.5px, black 54px, transparent 55px);
  animation: ringRotate1 6s linear infinite;
}

.ring-2 {
  width: 132px;
  height: 132px;
  background: conic-gradient(
    from 180deg,
    transparent 0%,
    rgba(6, 182, 212, 0.45) 15%,
    transparent 35%
  );
  -webkit-mask: radial-gradient(transparent 59px, black 60.5px, black 65px, transparent 66px);
  mask: radial-gradient(transparent 59px, black 60.5px, black 65px, transparent 66px);
  animation: ringRotate2 9s linear infinite;
}

@keyframes ringRotate1 {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
@keyframes ringRotate2 {
  from { transform: rotate(0deg); }
  to   { transform: rotate(-360deg); }
}

/* 光脆冲圆（周期性向外扩散消失） */
.star-pulse {
  position: absolute;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  border: 1.5px solid rgba(165, 180, 252, 0.5);
  animation: pulseRipple 3.5s ease-out infinite;
  pointer-events: none;
  z-index: 1;
}

@keyframes pulseRipple {
  0%   { transform: scale(1);   opacity: 0.7; }
  80%  { transform: scale(2.4); opacity: 0; }
  100% { transform: scale(2.4); opacity: 0; }
}

/* 流星 */
.meteors {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 1;
}

.meteor {
  position: absolute;
  height: 1.5px;
  background: linear-gradient(to right, rgba(255,255,255,0.9), transparent);
  border-radius: 1px;
  opacity: 0;
  animation: meteorFly 12s ease-in-out infinite;
}

.meteor-1 {
  width: 100px;
  top: 15%;
  left: -100px;
  transform: rotate(20deg);
  animation-delay: 0s;
  animation-duration: 14s;
}

.meteor-2 {
  width: 70px;
  top: 35%;
  left: -70px;
  transform: rotate(15deg);
  animation-delay: 5s;
  animation-duration: 11s;
}

.meteor-3 {
  width: 85px;
  top: 60%;
  left: -85px;
  transform: rotate(25deg);
  animation-delay: 9s;
  animation-duration: 13s;
}

@keyframes meteorFly {
  0%   { opacity: 0;   transform: translateX(0) rotate(20deg); }
  5%   { opacity: 0.9; }
  20%  { opacity: 0;   transform: translateX(70vw) rotate(20deg); }
  100% { opacity: 0;   transform: translateX(70vw) rotate(20deg); }
}

/* 入场淡入动画 */
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(24px); }
  to   { opacity: 1; transform: translateY(0); }
}

.brand-bottom {
  margin: 0;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
  letter-spacing: 1px;
  z-index: 2;
}



/* ================================================
   右侧：登录表单区（半透明暗色覆盖，展现共用背景）
   ================================================ */
.form-panel {
  position: relative;
  width: 480px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  // 关键：降低不透明度 + 减少模糊，让光球色彩明显透过
  background: rgba(4, 8, 18, 0.45);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  // 左侧用极细变云装饰线软化边界
  border-left: 1px solid rgba(255, 255, 255, 0.05);
  padding: 40px;
  z-index: 10;

  @media (max-width: 960px) {
    width: 100%;
    backdrop-filter: none;
    background: #040a14;
  }
}

/* 毛玻璃卡片（无硬色边框） */
.login-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 380px;
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(32px);
  -webkit-backdrop-filter: blur(32px);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 20px;
  padding: 44px 36px 40px;
  box-shadow:
    0 30px 70px rgba(0, 0, 0, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  // 入场动画（比左侧延迟 0.3s）
  animation: fadeInUp 1.2s 0.3s cubic-bezier(0.22, 1, 0.36, 1) both, cardFloat 4s 1.5s ease-in-out infinite;
}

@keyframes cardFloat {
  0%, 100% { transform: translateY(0px); }
  50%       { transform: translateY(-16px); }
}

/* 卡片内小品牌徽章 */
.card-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;

  .badge-star {
    width: 22px;
    height: 22px;
    filter: drop-shadow(0 0 6px rgba(165, 180, 252, 0.6));
  }

  .badge-text {
    font-size: 13px;
    font-weight: 600;
    color: rgba(165, 180, 252, 0.9);
    letter-spacing: 1.5px;
  }
}

/* 表单标题区 */
.form-headline {
  margin-bottom: 30px;

  h2 {
    margin: 0 0 6px;
    font-size: 24px;
    font-weight: 700;
    color: #f8fafc;
    letter-spacing: 0.5px;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.35);
  }
}

/* 表单选项行 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 6px 0 22px;

  .link-type {
    font-size: 13px;
    color: #818cf8;
    text-decoration: none;
    transition: color 0.2s;
    &:hover { color: #a5b4fc; }
  }
}

/* 登录按钮 */
.submit-btn {
  width: 100%;
  height: 48px;
  border: none !important;
  background: linear-gradient(135deg, #6366f1, #8b5cf6) !important;
  color: #fff !important;
  font-weight: 600 !important;
  font-size: 15px !important;
  letter-spacing: 2px;
  border-radius: 12px !important;
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4) !important;
  transition: all 0.3s ease !important;

  &:hover:not(.is-loading) {
    background: linear-gradient(135deg, #818cf8, #a78bfa) !important;
    box-shadow: 0 8px 28px rgba(99, 102, 241, 0.55) !important;
    transform: translateY(-2px);
  }

  &:active:not(.is-loading) {
    transform: translateY(0);
  }
}

.form-footer {
  margin-top: 36px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
  letter-spacing: 0.5px;
  text-align: center;
}

/* ================================================
   Element UI 深度覆盖
   ================================================ */
::v-deep {
  .el-form-item {
    margin-bottom: 18px;

    .el-form-item__error {
      color: #f87171;
      font-size: 12px;
    }
  }

  .el-input {
    .el-input__inner {
      height: 46px;
      line-height: 46px;
      background: rgba(255, 255, 255, 0.05) !important;
      border: 1px solid rgba(255, 255, 255, 0.1) !important;
      border-radius: 10px !important;
      color: #f1f5f9 !important;
      font-size: 14px;
      padding-left: 42px !important;
      transition: all 0.25s ease;

      &::placeholder { color: rgba(255, 255, 255, 0.25); }

      &:hover {
        border-color: rgba(255, 255, 255, 0.18) !important;
        background: rgba(255, 255, 255, 0.07) !important;
      }

      &:focus {
        border-color: rgba(129, 140, 248, 0.55) !important;
        background: rgba(255, 255, 255, 0.08) !important;
        box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.18) !important;
      }
    }

    .el-input__prefix {
      left: 14px;
      display: flex;
      align-items: center;
    }
  }

  .input-icon {
    width: 16px !important;
    height: 16px !important;
    color: rgba(255, 255, 255, 0.3) !important;
    transition: color 0.25s;
  }

  .el-input__inner:focus + .el-input__prefix .input-icon {
    color: #a5b4fc !important;
  }

  .custom-checkbox.el-checkbox {
    color: rgba(255, 255, 255, 0.35);
    font-size: 13px;

    .el-checkbox__inner {
      background: rgba(255, 255, 255, 0.05);
      border-color: rgba(255, 255, 255, 0.15);
      border-radius: 5px;
      width: 15px;
      height: 15px;
    }

    &.is-checked {
      .el-checkbox__label { color: #a5b4fc; }
      .el-checkbox__inner {
        background: #6366f1;
        border-color: #6366f1;
      }
    }

    .el-checkbox__inner::after {
      border-color: #fff;
      height: 7px;
      left: 4px;
      top: 1px;
    }
  }
}
</style>
