<template>
  <div class="register">
    <el-form ref="registerForm" :model="registerForm" :rules="registerRules" class="register-form">
      <h3 class="title">{{title}}</h3>
      <el-form-item prop="username">
        <el-input v-model="registerForm.username" auto-complete="off" placeholder="账号" type="text">
          <svg-icon slot="prefix" class="el-input__icon input-icon" icon-class="user" />
        </el-input>
      </el-form-item>
      <el-form-item :rules="registerPwdValidator" prop="password">
        <el-input
          v-model="registerForm.password"
          auto-complete="off"
          placeholder="密码"
          type="password"
          @keyup.enter.native="handleRegister"
        >
          <svg-icon slot="prefix" class="el-input__icon input-icon" icon-class="password" />
        </el-input>
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input
          v-model="registerForm.confirmPassword"
          auto-complete="off"
          placeholder="确认密码"
          type="password"
          @keyup.enter.native="handleRegister"
        >
          <svg-icon slot="prefix" class="el-input__icon input-icon" icon-class="password" />
        </el-input>
      </el-form-item>
      <!-- 去掉数字验证码框 -->
      <el-form-item style="width:100%;">
        <el-button
          :loading="loading"
          size="medium"
          style="width:100%;"
          type="primary"
          @click.native.prevent="handleRegister"
        >
          <span v-if="!loading">注 册</span>
          <span v-else>注 册 中...</span>
        </el-button>
        <div style="float: right;">
          <router-link :to="'/login'" class="link-type">使用已有账户登录</router-link>
        </div>
      </el-form-item>
    </el-form>
    <!--  底部  -->
    <div class="el-register-footer">
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
import {getCodeImg, register} from "@/api/login"
import passwordRule from "@/utils/passwordRule"
import defaultSettings from '@/settings'
import Verify from "@/components/verifition/Verify"

export default {
  mixins: [passwordRule],
  components: {
    Verify
  },
  data() {
    return {
      title: process.env.VUE_APP_TITLE,
      footerContent: defaultSettings.footerContent,
      codeUrl: "",
      registerForm: {
        username: "",
        password: "",
        confirmPassword: "",
        captchaVerification: ""
      },
      loading: false,
      captchaEnabled: true
    }
  },
  computed: {
    registerRules() {
      return {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" },
          { min: 2, max: 20, message: '用户账号长度必须介于 2 和 20 之间', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: "请再次输入您的密码", trigger: "blur" },
          {
            validator: (rule, value, callback) => {
              if (this.registerForm.password !== value) {
                callback(new Error("两次输入的密码不一致"))
              } else {
                callback()
              }
            }, trigger: "blur"
          }
        ]
      }
    }
  },
  created() {
    this.getCode()
  },
  methods: {
    getCode() {
      getCodeImg().then(res => {
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
      })
    },
    handleRegister() {
      this.$refs.registerForm.validate(valid => {
        if (valid) {
          if (this.captchaEnabled) {
            this.$refs.verify.show()
          } else {
            this.loading = true
            this.submitRegister()
          }
        }
      })
    },
    success(params) {
      this.registerForm.captchaVerification = params.captchaVerification
      this.loading = true
      this.submitRegister()
    },
    submitRegister() {
      register(this.registerForm).then(() => {
        const username = this.registerForm.username
        this.$alert("<font color='red'>恭喜你，您的账号 " + username + " 注册成功！</font>", '系统提示', {
          dangerouslyUseHTMLString: true,
          type: 'success'
        }).then(() => {
          this.$router.push("/login")
        }).catch(() => {})
      }).catch(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style lang="scss" rel="stylesheet/scss" scoped>
.register {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-image: url("../assets/images/login-background.jpg");
  background-size: cover;
}
.title {
  margin: 0px auto 30px auto;
  text-align: center;
  color: #707070;
}

.register-form {
  border-radius: 6px;
  background: #ffffff;
  width: 400px;
  padding: 25px 25px 5px 25px;
  .el-input {
    height: 38px;
    input {
      height: 38px;
    }
  }
  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 2px;
  }
}
.register-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}
.register-code {
  width: 33%;
  height: 38px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
  }
}
.el-register-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}
.register-code-img {
  height: 38px;
}
</style>
