<template>
  <div class="platform-login-container">
    <div class="stars-bg"></div>
    <div class="login-glass-card">
      <div class="login-header">
        <div class="logo-box">
          <img src="@/assets/logo/logo.png" alt="logo" class="logo" />
        </div>
        <h2 class="title">北辰 AI 开放中台</h2>
        <p class="subtitle">Enterprise Middle Platform · 租户控制台</p>
      </div>

      <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" class="login-form" size="large">
        <el-form-item prop="tenantCode">
          <el-input
            v-model="loginForm.tenantCode"
            placeholder="租户编码 (如: default)"
            prefix-icon="OfficeBuilding"
            clearable
          />
        </el-form-item>
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名 (如: admin)"
            prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="登录密码"
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
        <span>系统超管？<router-link to="/login">返回管理端</router-link></span>
        <span class="divider">/</span>
        <a href="/platform/docs/index.html" target="_blank">开发者文档</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import {reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import usePlatformUserStore from '@/store/modules/platformUser'

const router = useRouter()
const route = useRoute()
const platformUserStore = usePlatformUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

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
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 50% 20%, #1e1b4b 0%, #0f172a 60%, #020617 100%);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    width: 600px;
    height: 600px;
    background: radial-gradient(circle, rgba(99, 102, 241, 0.15) 0%, rgba(0, 0, 0, 0) 70%);
    top: 20%;
    left: 50%;
    transform: translate(-50%, -50%);
    pointer-events: none;
  }
}

.login-glass-card {
  width: 440px;
  padding: 44px 40px;
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(28px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.15);
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;

  .logo-box {
    width: 64px;
    height: 64px;
    margin: 0 auto 16px;
    background: rgba(255, 255, 255, 0.08);
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid rgba(255, 255, 255, 0.12);
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);

    .logo {
      width: 40px;
      height: 40px;
    }
  }

  .title {
    font-size: 22px;
    font-weight: 700;
    color: #f8fafc;
    margin: 0 0 8px;
    letter-spacing: -0.5px;
  }

  .subtitle {
    font-size: 13px;
    color: #94a3b8;
    margin: 0;
  }
}

:deep(.el-input__wrapper) {
  background: rgba(0, 0, 0, 0.25) !important;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  border-radius: 12px !important;
  box-shadow: none !important;
  height: 48px;

  &:hover,
  &.is-focus {
    border-color: rgba(99, 102, 241, 0.6) !important;
    box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.3) !important;
  }

  .el-input__inner {
    color: #f1f5f9 !important;
    font-size: 14px;

    &::placeholder {
      color: #64748b;
    }
  }

  .el-input__prefix-inner {
    color: #818cf8;
  }
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border: none;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-top: 8px;
  box-shadow: 0 10px 20px -5px rgba(79, 70, 229, 0.4);
  transition: all 0.3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 26px -4px rgba(79, 70, 229, 0.6);
  }
}

.login-footer {
  margin-top: 28px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #64748b;

  a {
    color: #818cf8;
    text-decoration: none;
    transition: color 0.2s;

    &:hover {
      color: #a5b4fc;
      text-decoration: underline;
    }
  }

  .divider {
    color: #475569;
  }
}
</style>
