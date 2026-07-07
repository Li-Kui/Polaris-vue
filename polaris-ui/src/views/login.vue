<template>
  <div class="login-wrapper">
    <!-- 神经网络动画背景 Canvas -->
    <canvas ref="canvas" class="login-bg-canvas"></canvas>

    <div class="login-card-wrapper">
      <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form">
        <!-- 科技感 Logo 标题区 -->
        <div class="brand-container">
          <div class="brand-logo">
            <span class="logo-dot"></span>
          </div>
          <h3 class="title">{{title}}</h3>
          <p class="subtitle">AI Intelligent Platform</p>
        </div>

        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            auto-complete="off"
            placeholder="账号"
            type="text"
          >
            <svg-icon slot="prefix" class="el-input__icon input-icon" icon-class="user" />
          </el-input>
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            auto-complete="off"
            placeholder="密码"
            type="password"
            @keyup.enter.native="handleLogin"
          >
            <svg-icon slot="prefix" class="el-input__icon input-icon" icon-class="password" />
          </el-input>
        </el-form-item>
        
        <div class="form-options">
          <el-checkbox v-model="loginForm.rememberMe" class="custom-checkbox">记住密码</el-checkbox>
          <div v-if="register" class="register-link-container">
            <router-link :to="'/register'" class="link-type">立即注册</router-link>
          </div>
        </div>

        <el-form-item style="width:100%; margin-top: 10px;">
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

    <!--  底部  -->
    <div class="el-login-footer">
      <span>{{ footerContent }}</span>
    </div>
    
    <!-- 引入滑动验证码组件 -->
    <Verify
      ref="verify"
      :captcha-type="'blockPuzzle'"
      :mode="'pop'"
      @success="success"
    />
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
  components: {
    Verify
  },
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
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" }
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入您的密码" }
        ]
      },
      loading: false,
      // 验证码开关
      captchaEnabled: true,
      // 注册开关
      register: false,
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  created() {
    this.getCode()
    this.getCookie()
  },
  mounted() {
    this.initCanvas()
  },
  beforeDestroy() {
    if (this._handleResize) {
      window.removeEventListener('resize', this._handleResize)
    }
    if (this._animationFrameId) {
      cancelAnimationFrame(this._animationFrameId)
    }
  },
  methods: {
    initCanvas() {
      const canvas = this.$refs.canvas
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      
      let width = canvas.width = window.innerWidth
      let height = canvas.height = window.innerHeight
      
      const particles = []
      const particleCount = Math.min(80, Math.floor((width * height) / 15000))
      
      for (let i = 0; i < particleCount; i++) {
        particles.push({
          x: Math.random() * width,
          y: Math.random() * height,
          vx: (Math.random() - 0.5) * 0.8,
          vy: (Math.random() - 0.5) * 0.8,
          radius: Math.random() * 2 + 1
        })
      }
      
      const handleResize = () => {
        if (!canvas) return
        width = canvas.width = window.innerWidth
        height = canvas.height = window.innerHeight
      }
      
      window.addEventListener('resize', handleResize)
      this._handleResize = handleResize
      
      const draw = () => {
        if (!canvas) return
        ctx.clearRect(0, 0, width, height)
        
        const bgGradient = ctx.createLinearGradient(0, 0, width, height)
        bgGradient.addColorStop(0, '#0a0f1d')
        bgGradient.addColorStop(0.5, '#070a13')
        bgGradient.addColorStop(1, '#0f172a')
        ctx.fillStyle = bgGradient
        ctx.fillRect(0, 0, width, height)
        
        for (let i = 0; i < particles.length; i++) {
          const p = particles[i]
          p.x += p.vx
          p.y += p.vy
          
          if (p.x < 0 || p.x > width) p.vx = -p.vx
          if (p.y < 0 || p.y > height) p.vy = -p.vy
          
          ctx.beginPath()
          ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2)
          ctx.fillStyle = 'rgba(14, 165, 233, 0.35)'
          ctx.fill()
          
          for (let j = i + 1; j < particles.length; j++) {
            const p2 = particles[j]
            const dist = Math.hypot(p.x - p2.x, p.y - p2.y)
            
            if (dist < 120) {
              ctx.beginPath()
              ctx.moveTo(p.x, p.y)
              ctx.lineTo(p2.x, p2.y)
              const alpha = (1 - dist / 120) * 0.12
              ctx.strokeStyle = `rgba(14, 165, 233, ${alpha})`
              ctx.lineWidth = 0.8
              ctx.stroke()
            }
          }
        }
        
        this._animationFrameId = requestAnimationFrame(draw)
      }
      
      draw()
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
        Cookies.set("username", this.loginForm.username, { expires: 30 })
        Cookies.set("password", encrypt(this.loginForm.password), { expires: 30 })
        Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 30 })
      } else {
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove('rememberMe')
      }
      this.$store.dispatch("Login", this.loginForm).then(() => {
        this.$router.push({ path: this.redirect || "/" }).catch(()=>{})
      }).catch(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style lang="scss" rel="stylesheet/scss" scoped>
.login-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  background-color: #070a13;
}

.login-bg-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

.login-card-wrapper {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 40px 35px 30px 35px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.4),
              inset 0 1px 0 rgba(255, 255, 255, 0.1);
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: -50%;
    width: 200%;
    height: 100%;
    background: linear-gradient(
      90deg,
      transparent,
      rgba(14, 165, 233, 0.15),
      rgba(139, 92, 246, 0.15),
      transparent
    );
    transform: rotate(30deg);
    animation: flow 8s linear infinite;
    pointer-events: none;
  }
}

@keyframes flow {
  0% {
    transform: rotate(30deg) translate(-50%, -50%);
  }
  100% {
    transform: rotate(30deg) translate(50%, 50%);
  }
}

.login-form {
  position: relative;
  z-index: 2;
}

.brand-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 35px;
  
  .brand-logo {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    background: linear-gradient(135deg, #0ea5e9, #8b5cf6);
    display: flex;
    justify-content: center;
    align-items: center;
    box-shadow: 0 0 20px rgba(139, 92, 246, 0.4);
    margin-bottom: 12px;
    position: relative;
    
    .logo-dot {
      width: 14px;
      height: 14px;
      border-radius: 50%;
      background: #ffffff;
      box-shadow: 0 0 10px rgba(255, 255, 255, 0.8);
      animation: pulse 2s infinite;
    }
  }
  
  .title {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 1px;
    background: linear-gradient(to right, #ffffff, #94a3b8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    text-align: center;
  }
  
  .subtitle {
    margin: 6px 0 0 0;
    font-size: 12px;
    color: #64748b;
    text-transform: uppercase;
    letter-spacing: 2px;
  }
}

@keyframes pulse {
  0%, 100% {
    transform: scale(0.85);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.15);
    opacity: 1;
  }
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  
  .link-type {
    color: #38bdf8;
    font-size: 14px;
    text-decoration: none;
    transition: color 0.3s;
    
    &:hover {
      color: #7dd3fc;
      text-shadow: 0 0 8px rgba(125, 211, 252, 0.4);
    }
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  border: none !important;
  background: linear-gradient(135deg, #0ea5e9, #8b5cf6) !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  font-size: 16px !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 15px rgba(139, 92, 246, 0.3) !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
  cursor: pointer;
  
  &:hover:not(.is-loading) {
    background: linear-gradient(135deg, #38bdf8, #a78bfa) !important;
    box-shadow: 0 6px 20px rgba(139, 92, 246, 0.5), 0 0 10px rgba(14, 165, 233, 0.4) !important;
    transform: translateY(-1px);
  }
  
  &:active:not(.is-loading) {
    transform: translateY(1px);
  }
}

.el-login-footer {
  position: absolute;
  bottom: 20px;
  width: 100%;
  text-align: center;
  color: #475569;
  font-family: Inter, sans-serif;
  font-size: 12px;
  letter-spacing: 1px;
  z-index: 1;
  pointer-events: none;
}

::v-deep {
  .el-form-item {
    margin-bottom: 22px;
  }
  
  .el-input {
    .el-input__inner {
      background-color: rgba(30, 41, 59, 0.4) !important;
      border: 1px solid rgba(255, 255, 255, 0.08) !important;
      color: #e2e8f0 !important;
      height: 44px;
      line-height: 44px;
      border-radius: 8px;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      padding-left: 38px !important;
      
      &::placeholder {
        color: #475569;
      }
      
      &:focus {
        border-color: #0ea5e9 !important;
        background-color: rgba(30, 41, 59, 0.7) !important;
        box-shadow: 0 0 12px rgba(14, 165, 233, 0.25) !important;
      }
    }
    
    .el-input__prefix {
      left: 12px;
      display: flex;
      align-items: center;
    }
  }

  .input-icon {
    height: 18px !important;
    width: 18px !important;
    color: #475569 !important;
    transition: color 0.3s;
  }

  .el-input__inner:focus + .el-input__prefix .input-icon {
    color: #38bdf8 !important;
  }

  .custom-checkbox.el-checkbox {
    color: #64748b;
    display: flex;
    align-items: center;
    
    .el-checkbox__inner {
      background-color: rgba(30, 41, 59, 0.4);
      border-color: rgba(255, 255, 255, 0.08);
      border-radius: 4px;
    }
    
    &.is-checked {
      .el-checkbox__label {
        color: #38bdf8;
      }
      .el-checkbox__inner {
        background-color: #0ea5e9;
        border-color: #0ea5e9;
      }
    }
    
    .el-checkbox__inner:after {
      border-color: #fff;
    }
  }
}
</style>

