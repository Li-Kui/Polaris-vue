<template>
  <div v-show="showBox" :class="mode=='pop'?'mask':''">
    <div :class="mode=='pop'?'verifybox':''" :style="{'max-width':parseInt(imgSize.width)+30+'px'}">
      <div v-if="mode=='pop'" class="verifybox-top">
        请完成安全验证
        <span class="verifybox-close" @click="closeBox">
          <i class="iconfont icon-close" />
        </span>
      </div>
      <div :style="{padding:mode=='pop'?'15px':'0'}" class="verifybox-bottom">
        <!-- 验证码容器 -->
        <component
          :is="componentMap[componentType]"
          v-if="componentType"
          ref="instance"
          :arith="arith"
          :bar-size="barSize"
          :block-size="blockSize"
          :captcha-type="captchaType"
          :default-img="defaultImg"
          :explain="explain"
          :figure="figure"
          :img-size="imgSize"
          :mode="mode"
          :type="verifyType"
          :v-space="vSpace"
          @success="handleSuccess"
          @error="handleError"
          @ready="handleReady"
        />
      </div>
    </div>
  </div>
</template>
<script>
/**
 * Verify 验证码组件
 * @description 分发验证码使用
 * */
import VerifySlide from './Verify/VerifySlide.vue'
import VerifyPoints from './Verify/VerifyPoints.vue'
import defaultImg from '@/assets/images/profile.jpg'

export default {
  name: 'Vue3Verify',
  components: {
    VerifySlide,
    VerifyPoints
  },
  emits: ['success', 'error', 'ready'],
  props: {
    // 双语化
    locale: {
      require: false,
      type: String,
      default() {
        // 默认语言不输入为浏览器语言
        var language = 'zh-CN'
        if (typeof navigator !== 'undefined') {
          if (navigator.language) {
            language = navigator.language
          } else if (navigator.browserLanguage) {
            language = navigator.browserLanguage
          }
        }
        return language
      }
    },
    captchaType: {
      type: String,
      required: true
    },
    figure: {
      type: Number
    },
    arith: {
      type: Number
    },
    mode: {
      type: String,
      default: 'pop'
    },
    vSpace: {
      type: Number
    },
    explain: {
      type: String
    },
    imgSize: {
      type: Object,
      default() {
        return {
          width: '310px',
          height: '155px'
        }
      }
    },
    blockSize: {
      type: Object
    },
    barSize: {
      type: Object
    },
  },
  data() {
    return {
      clickShow: false,
      // 内部类型
      verifyType: undefined,
      // 所用组件类型
      componentType: undefined,
      // 默认图片
      defaultImg: defaultImg,
      componentMap: {
        VerifySlide,
        VerifyPoints
      }
    }
  },
  computed: {
    instance() {
      return this.$refs.instance || {}
    },
    showBox() {
      if (this.mode == 'pop') {
        return this.clickShow
      } else {
        return true
      }
    }
  },
  watch: {
    captchaType: {
      immediate: true,
      handler(captchaType) {
        if (!captchaType) return
        switch (captchaType.toString()) {
          case 'blockPuzzle':
            this.verifyType = '2'
            this.componentType = 'VerifySlide'
            break
          case 'clickWord':
            this.verifyType = ''
            this.componentType = 'VerifyPoints'
            break
        }
      }
    },
  },
  mounted() {
    this.uuid()
  },
  methods: {
    // 生成 uuid
    uuid() {
      var s = []
      var hexDigits = '0123456789abcdef'
      for (var i = 0; i < 36; i++) {
        s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1)
      }
      s[14] = '4' // bits 12-15 of the time_hi_and_version field to 0010
      s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1) // bits 6-7 of the clock_seq_hi_and_reserved to 01
      s[8] = s[13] = s[18] = s[23] = '-'

      var slider = 'slider' + '-' + s.join('')
      var point = 'point' + '-' + s.join('')
      if (typeof localStorage !== 'undefined') {
        if (!localStorage.getItem('slider')) {
          localStorage.setItem('slider', slider)
        }
        if (!localStorage.getItem('point')) {
          localStorage.setItem('point', point)
        }
      }
    },
    refresh() {
      if (this.instance.refresh) {
        this.instance.refresh()
      }
    },
    closeBox() {
      this.clickShow = false
      this.refresh()
    },
    show() {
      if (this.mode == 'pop') {
        this.clickShow = true
      }
    },
    handleSuccess(params) {
      this.$emit('success', params)
    },
    handleError(params) {
      this.$emit('error', params)
    },
    handleReady(params) {
      this.$emit('ready', params)
    }
  },
}
</script>
<style lang="scss">
    /* 全局遮罩层 */
    .mask {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 2001; /* 确保盖在所有一般 dialog 顶层 */
        width: 100%;
        height: 100vh;
        background: rgba(0, 0, 0, 0.5) !important;
        backdrop-filter: blur(3px);
        -webkit-backdrop-filter: blur(3px);
        transition: all .5s;
    }

    /* 验证框主体 - 适配项目与暗黑模式 */
    .verifybox {
        position: relative;
        box-sizing: border-box;
        border-radius: 8px !important;
        border: 1px solid var(--el-border-color-light) !important;
        background-color: var(--el-bg-color-overlay) !important;
        box-shadow: var(--el-box-shadow-dark) !important;
        left: 50%;
        top: 50%;
        transform: translate(-50%, -50%);
        overflow: hidden;
        transition: all 0.3s;
    }

    /* 头部标题区域 */
    .verifybox-top {
        padding: 0 20px !important;
        height: 50px;
        line-height: 50px;
        text-align: left;
        font-size: 15px !important;
        font-weight: 600 !important;
        color: var(--el-text-color-primary) !important;
        border-bottom: 1px solid var(--el-border-color-light) !important;
        box-sizing: border-box;
        background: var(--el-fill-color-light) !important;
        position: relative;
    }

    /* 关闭按钮 */
    .verifybox-close {
        position: absolute;
        top: 13px;
        right: 15px;
        width: 24px;
        height: 24px;
        text-align: center;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: transform 0.3s;
        
        .iconfont {
            color: var(--el-text-color-secondary) !important;
            transition: color 0.3s;
        }
        
        &:hover {
            transform: rotate(90deg);
            .iconfont {
                color: var(--el-color-danger) !important;
            }
        }
    }

    /* 底部验证区域 */
    .verifybox-bottom {
        padding: 20px !important;
        box-sizing: border-box;
    }

    /* 提示字条样式（成功/失败） */
    .verify-tips {
        position: absolute;
        left: 0px;
        bottom: 0px;
        width: 100%;
        height: 30px;
        line-height: 30px;
        color: #fff !important;
        font-size: 13px !important;
        text-align: center;
        z-index: 10;
        border-bottom-left-radius: 8px;
        border-bottom-right-radius: 8px;
    }
    .suc-bg {
        background-color: var(--el-color-success) !important;
    }
    .err-bg {
        background-color: var(--el-color-danger) !important;
    }

    /* 常规验证码输入框及按钮 */
    .verify-code {
        font-size: 20px;
        text-align: center;
        cursor: pointer;
        margin-bottom: 5px;
        border: 1px solid var(--el-border-color-light) !important;
        background-color: var(--el-fill-color-blank) !important;
        color: var(--el-text-color-primary) !important;
        border-radius: 6px;
    }
    .varify-input-code {
        background-color: var(--el-fill-color-blank) !important;
        border: 1px solid var(--el-border-color-light) !important;
        color: var(--el-text-color-primary) !important;
        border-radius: 6px;
        padding: 4px 8px;
    }
    .verify-change-code {
        color: var(--el-color-primary) !important;
        cursor: pointer;
        &:hover {
            color: var(--el-color-primary-light-3) !important;
        }
    }
    .verify-btn {
        width: 200px;
        height: 34px;
        background: var(--el-color-primary) !important;
        color: #FFFFFF !important;
        border: none !important;
        margin-top: 10px;
        border-radius: 6px;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.3s;
        
        &:hover {
            background: var(--el-color-primary-light-3) !important;
        }
    }

    /* 滑动验证轨道区 - 必须使用 content-box */
    .verify-bar-area {
        position: relative;
        background: var(--el-fill-color-light) !important;
        text-align: center;
        -webkit-box-sizing: content-box !important;
        -moz-box-sizing: content-box !important;
        box-sizing: content-box !important;
        border: 1px solid var(--el-border-color-light) !important;
        border-radius: 8px !important;
        
        .verify-msg {
            color: var(--el-text-color-secondary) !important;
            font-size: 14px;
        }
    }

    /* 移动滑块 - 必须使用 content-box */
    .verify-bar-area .verify-move-block {
        position: absolute;
        top: 0px;
        left: 0;
        background: var(--el-bg-color-overlay) !important;
        border: 1px solid var(--el-border-color-light) !important;
        cursor: pointer;
        -webkit-box-sizing: content-box !important;
        -moz-box-sizing: content-box !important;
        box-sizing: content-box !important;
        box-shadow: var(--el-box-shadow-light) !important;
        border-radius: 8px !important;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: background 0.3s, border-color 0.3s, box-shadow 0.3s;
        
        &:hover {
            background: var(--el-color-primary) !important;
            border-color: var(--el-color-primary) !important;
            .iconfont {
                color: #ffffff !important;
            }
        }
    }

    /* 左侧滑过的区域 - 必须使用 content-box */
    .verify-bar-area .verify-left-bar {
        position: absolute;
        top: -1px;
        left: -1px;
        background: var(--el-color-primary-light-9) !important;
        cursor: pointer;
        -webkit-box-sizing: content-box !important;
        -moz-box-sizing: content-box !important;
        box-sizing: content-box !important;
        border: 1px solid var(--el-color-primary) !important;
        border-radius: 8px !important;
        
        .verify-msg {
            color: var(--el-color-primary) !important;
            font-size: 14px;
        }
    }

    /* 移动滑块内的拼图小块 - 必须加回绝对定位，否则滑块会错位并且遮挡鼠标事件 */
    .verify-bar-area .verify-move-block .verify-sub-block {
        position: absolute !important;
        text-align: center !important;
        z-index: 3 !important;
    }

    /* 验证图片面板 - 必须使用 content-box */
    .verify-img-panel {
        margin: 0;
        -webkit-box-sizing: content-box !important;
        -moz-box-sizing: content-box !important;
        box-sizing: content-box !important;
        border: 1px solid var(--el-border-color-light) !important;
        border-radius: 8px !important;
        position: relative;
        overflow: hidden;
    }

    /* 刷新按钮 */
    .verify-img-panel .verify-refresh {
        width: 30px;
        height: 30px;
        text-align: center;
        padding: 0;
        cursor: pointer;
        position: absolute;
        top: 8px;
        right: 8px;
        z-index: 12;
        background: var(--el-fill-color-light);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        border: 1px solid var(--el-border-color-light);
        transition: background 0.3s, transform 0.3s;
        
        &:hover {
            background: var(--el-fill-color);
            transform: rotate(180deg);
        }
        
        .icon-refresh {
            font-size: 16px;
            color: var(--el-text-color-primary) !important;
        }
    }

    /* 底图镂空的遮挡背景板 */
    .verify-img-panel .verify-gap {
        background-color: #fff !important;
        position: relative !important;
        z-index: 2 !important;
        border: 1px solid #fff !important;
    }

    /* 图标与 Base64 反色滤镜 */
    .icon-right:before,
    .icon-close:before,
    .icon-check:before,
    .icon-refresh:before {
        filter: none !important; /* 去除原本的白反色滤镜，以支持彩色图标 */
    }

    /* iconfont 优雅降级字符样式 */
    .iconfont {
        font-family: inherit !important;
        font-style: normal;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
        display: inline-block;
        text-align: center;
        vertical-align: middle;
    }
    .icon-close::before {
        content: "✕" !important;
        font-size: 16px;
        font-weight: bold;
    }
    .icon-refresh::before {
        content: "↻" !important;
        font-size: 16px;
        font-weight: bold;
    }
    .icon-check::before {
        content: "✓" !important;
        font-size: 16px;
        font-weight: bold;
    }
    .icon-right::before {
        content: "→" !important;
        font-size: 16px;
        font-weight: bold;
    }
</style>
