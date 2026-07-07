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
        <components
          :is="componentType"
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
        />
      </div>
    </div>
  </div>
</template>
<script type="text/babel">
/**
 * Verify 验证码组件
 * @description 分发验证码使用
 * */
import VerifySlide from './Verify/VerifySlide'
import VerifyPoints from './Verify/VerifyPoints'

export default {
  name: 'Vue2Verify',
  components: {
    VerifySlide,
    VerifyPoints
  },
  props: {
    // 双语化
    locale: {
      require: false,
      type: String,
      default() {
        // 默认语言不输入为浏览器语言
        if (navigator.language) {
          var language = navigator.language
        } else {
          var language = navigator.browserLanguage
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
      // showBox:true,
      clickShow: false,
      // 内部类型
      verifyType: undefined,
      // 所用组件类型
      componentType: undefined,
      // 默认图片（修改为项目已有的 profile.jpg，避免 require 失败）
      defaultImg: require('@/assets/images/profile.jpg')
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
      // 判断下是否存在 slider
      console.log(localStorage.getItem('slider'))
      if (!localStorage.getItem('slider')) {
        localStorage.setItem('slider', slider)
      }
      if (!localStorage.getItem('point')) {
        localStorage.setItem('point', point)
      }
    },
    /**
             * i18n
             * @description 兼容vue-i18n 调用$t来转换ok
             * @param {String} text-被转换的目标
             * @return {String} i18n的结果
             * */
    i18n(text) {
      if (this.$t) {
        return this.$t(text)
      } else {
        // 兼容不存在的语言
        const i18n = this.$options.i18n.messages[this.locale] || this.$options.i18n.messages['en-US']
        return i18n[text]
      }
    },
    /**
             * refresh
             * @description 刷新
             * */
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
        background: rgba(7, 10, 19, 0.65) !important;
        backdrop-filter: blur(8px);
        -webkit-backdrop-filter: blur(8px);
        transition: all .5s;
    }

    /* 验证框主体 - 暗黑毛玻璃风格 */
    .verifybox {
        position: relative;
        box-sizing: border-box;
        border-radius: 16px !important;
        border: 1px solid rgba(255, 255, 255, 0.08) !important;
        background-color: rgba(15, 23, 42, 0.85) !important;
        backdrop-filter: blur(25px);
        -webkit-backdrop-filter: blur(25px);
        box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5),
                    inset 0 1px 0 rgba(255, 255, 255, 0.1) !important;
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
        color: #e2e8f0 !important;
        border-bottom: 1px solid rgba(255, 255, 255, 0.08) !important;
        box-sizing: border-box;
        background: rgba(30, 41, 59, 0.2) !important;
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
            color: #64748b !important;
            transition: color 0.3s;
        }
        
        &:hover {
            transform: rotate(90deg);
            .iconfont {
                color: #f43f5e !important;
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
        background-color: rgba(16, 185, 129, 0.85) !important;
    }
    .err-bg {
        background-color: rgba(239, 68, 68, 0.85) !important;
    }

    /* 常规验证码输入框及按钮 */
    .verify-code {
        font-size: 20px;
        text-align: center;
        cursor: pointer;
        margin-bottom: 5px;
        border: 1px solid rgba(255, 255, 255, 0.08) !important;
        background-color: rgba(30, 41, 59, 0.4) !important;
        color: #e2e8f0 !important;
        border-radius: 6px;
    }
    .varify-input-code {
        background-color: rgba(30, 41, 59, 0.4) !important;
        border: 1px solid rgba(255, 255, 255, 0.08) !important;
        color: #e2e8f0 !important;
        border-radius: 6px;
        padding: 4px 8px;
    }
    .verify-change-code {
        color: #38bdf8 !important;
        cursor: pointer;
        &:hover {
            color: #7dd3fc !important;
        }
    }
    .verify-btn {
        width: 200px;
        height: 34px;
        background: linear-gradient(135deg, #0ea5e9, #8b5cf6) !important;
        color: #FFFFFF !important;
        border: none !important;
        margin-top: 10px;
        border-radius: 6px;
        font-weight: 600;
        box-shadow: 0 4px 12px rgba(139, 92, 246, 0.2);
        cursor: pointer;
        transition: all 0.3s;
        
        &:hover {
            background: linear-gradient(135deg, #38bdf8, #a78bfa) !important;
            box-shadow: 0 6px 16px rgba(139, 92, 246, 0.4);
        }
    }

    /* 滑动验证轨道区 - 必须使用 content-box */
    .verify-bar-area {
        position: relative;
        background: rgba(30, 41, 59, 0.4) !important;
        text-align: center;
        -webkit-box-sizing: content-box !important;
        -moz-box-sizing: content-box !important;
        box-sizing: content-box !important;
        border: 1px solid rgba(255, 255, 255, 0.08) !important;
        border-radius: 8px !important;
        
        .verify-msg {
            color: #64748b !important;
            font-size: 14px;
        }
    }

    /* 移动滑块 - 必须使用 content-box */
    .verify-bar-area .verify-move-block {
        position: absolute;
        top: 0px;
        left: 0;
        background: linear-gradient(135deg, #0ea5e9, #8b5cf6) !important;
        cursor: pointer;
        -webkit-box-sizing: content-box !important;
        -moz-box-sizing: content-box !important;
        box-sizing: content-box !important;
        box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3) !important;
        border-radius: 8px !important;
        border: none !important;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: background 0.3s, box-shadow 0.3s;
        
        &:hover {
            background: linear-gradient(135deg, #38bdf8, #a78bfa) !important;
            box-shadow: 0 6px 16px rgba(139, 92, 246, 0.4), 0 0 8px rgba(14, 165, 233, 0.3) !important;
        }
    }

    /* 左侧滑过的区域 - 必须使用 content-box */
    .verify-bar-area .verify-left-bar {
        position: absolute;
        top: -1px;
        left: -1px;
        background: rgba(14, 165, 233, 0.1) !important;
        cursor: pointer;
        -webkit-box-sizing: content-box !important;
        -moz-box-sizing: content-box !important;
        box-sizing: content-box !important;
        border: 1px solid #0ea5e9 !important;
        border-radius: 8px !important;
        
        .verify-msg {
            color: #38bdf8 !important;
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
        border: 1px solid rgba(255, 255, 255, 0.08) !important;
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
        background: rgba(15, 23, 42, 0.6);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        border: 1px solid rgba(255, 255, 255, 0.1);
        transition: background 0.3s, transform 0.3s;
        
        &:hover {
            background: rgba(15, 23, 42, 0.85);
            transform: rotate(180deg);
        }
        
        .icon-refresh {
            font-size: 16px;
            color: #e2e8f0 !important;
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
        filter: invert(1) brightness(2) !important;
    }
</style>
