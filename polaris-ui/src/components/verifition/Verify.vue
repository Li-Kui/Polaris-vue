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
<style>
    .verifybox{
        position: relative;
        box-sizing: border-box;
        border-radius: 2px;
        border: 1px solid #e4e7eb;
        background-color: #fff;
        box-shadow: 0 0 10px rgba(0,0,0,.3);
        left: 50%;
        top:50%;
        transform: translate(-50%,-50%);
    }
    .verifybox-top{
        padding: 0 15px;
        height: 50px;
        line-height: 50px;
        text-align: left;
        font-size: 16px;
        color: #45494c;
        border-bottom: 1px solid #e4e7eb;
        box-sizing: border-box;
    }
    .verifybox-bottom{
        padding: 15px;
        box-sizing: border-box;
    }
    .verifybox-close{
        position: absolute;
        top: 13px;
        right: 9px;
        width: 24px;
        height: 24px;
        text-align: center;
        cursor: pointer;
    }
    .mask{
        position: fixed;
        top: 0;
        left:0;
        z-index: 1001;
        width: 100%;
        height: 100vh;
        background: rgba(0,0,0,.3);
        /* display: none; */
        transition: all .5s;
    }
    .verify-tips{
        position: absolute;
        left: 0px;
        bottom:0px;
        width: 100%;
        height: 30px;
        line-height:30px;
        color: #fff;
    }
    .suc-bg{
       background-color:rgba(92, 184, 92,.5);
       filter: progid:DXImageTransform.Microsoft.gradient(startcolorstr=#7f5CB85C, endcolorstr=#7f5CB85C);
    }
    .err-bg{
       background-color:rgba(217, 83, 79,.5);
       filter: progid:DXImageTransform.Microsoft.gradient(startcolorstr=#7fD9534F, endcolorstr=#7fD9534F);
    }
    .tips-enter,.tips-leave-to{
        bottom: -30px;
    }
    .tips-enter-active,.tips-leave-active{
        transition: bottom .5s;
    }
    /* ---------------------------- */
    /*常规验证码*/
    .verify-code {
        font-size: 20px;
        text-align: center;
        cursor: pointer;
        margin-bottom: 5px;
        border: 1px solid #ddd;
    }

    .cerify-code-panel {
        height: 100%;
        overflow: hidden;
    }

    .verify-code-area {
        float: left;
    }

    .verify-input-area {
        float: left;
        width: 60%;
        padding-right: 10px;

    }

    .verify-change-area {
        line-height: 30px;
        float: left;
    }

    .varify-input-code {
        display: inline-block;
        width: 100%;
        height: 25px;
    }

    .verify-change-code {
        color: #337AB7;
        cursor: pointer;
    }

    .verify-btn {
        width: 200px;
        height: 30px;
        background-color: #337AB7;
        color: #FFFFFF;
        border: none;
        margin-top: 10px;
    }

    /*滑动验证码*/
    .verify-bar-area {
        position: relative;
        background: #FFFFFF;
        text-align: center;
        -webkit-box-sizing: content-box;
        -moz-box-sizing: content-box;
        box-sizing: content-box;
        border: 1px solid #ddd;
        -webkit-border-radius: 4px;
    }

    .verify-bar-area .verify-move-block {
        position: absolute;
        top: 0px;
        left: 0;
        background: #fff;
        cursor: pointer;
        -webkit-box-sizing: content-box;
        -moz-box-sizing: content-box;
        box-sizing: content-box;
        box-shadow: 0 0 2px #888888;
        -webkit-border-radius: 1px;
    }

    .verify-bar-area .verify-move-block:hover {
        background-color: #337ab7;
        color: #FFFFFF;
    }

    .verify-bar-area .verify-left-bar {
        position: absolute;
        top: -1px;
        left: -1px;
        background: #f0fff0;
        cursor: pointer;
        -webkit-box-sizing: content-box;
        -moz-box-sizing: content-box;
        box-sizing: content-box;
        border: 1px solid #ddd;
    }

    .verify-img-panel {
        margin: 0;
        -webkit-box-sizing: content-box;
        -moz-box-sizing: content-box;
        box-sizing: content-box;
        border-top: 1px solid #ddd;
        border-bottom: 1px solid #ddd;
        border-radius: 3px;
        position: relative;
    }

    .verify-img-panel .verify-refresh {
        width: 25px;
        height: 25px;
        text-align: center;
        padding: 5px;
        cursor: pointer;
        position: absolute;
        top: 0;
        right: 0;
        z-index: 2;
    }

    .verify-img-panel .icon-refresh {
        font-size: 20px;
        color: #fff;
    }

    .verify-img-panel .verify-gap {
        background-color: #fff;
        position: relative;
        z-index: 2;
        border: 1px solid #fff;
    }

    .verify-bar-area .verify-move-block .verify-sub-block {
        position: absolute;
        text-align: center;
        z-index: 3;
        /* border: 1px solid #fff; */
    }

    .verify-bar-area .verify-move-block .verify-icon {
        font-size: 18px;
    }

    .verify-bar-area .verify-msg {
        z-index: 3;
    }

    .iconfont {
        font-family: "iconfont" !important;
        font-size: 16px;
        font-style: normal;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
    }

    .icon-check:before {
        content: " ";
        display: block;
        width: 16px;
        height: 16px;
        position: absolute;
        margin: auto;
        left: 0;
        right: 0;
        top: 0;
        bottom: 0;
        z-index: 9999;
        background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADIEAYAAAD9yHLdAAAABGdBTUEAALGPC/xhBQAAAAFzUkdCAK7OHOkAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAAAAZiS0dEAAAAAAAA+UO7fwAAAAlwSFlzAAAASAAAAEgARslrPgAAIlFJREFUeNrt3X1cVNW6B/BnbcS3xJd7fLmSeo+op/Qmyp4BFcQEwpd8Nyc9iZppgUfE49u1tCwlNcMySCM1S81jCoaioiJvKoYgswfUo5wSJ69SZFKCKSAws+4f2/GetFFRYG3g9/2Hz2xj+O2J4Zm19trrIQIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKgjmOgAAADwOBhz83TzdPNs397qanW1ujJ2s8fNHjd7FBTkhuSG5IbculVdP1kSfeoAAPBwdFzHdXzgQN0S3RLdkpgY2SJbZMvNm9It6ZZ064cfGmQ2yGyQmZfX3KO5R3OPwkJdsi5Zl5yYKIfL4XL4mDHqs7AqGzhgBAIAoFFdI7pGdI1o1KjFlhZbWmxZv149OmXK4z3r4cPEiROfOFExKSbFVFDwqM+EEQgAgMY8y5/lz/LGjZu3bt66eev9+9Wjj1s4bAYNIkaMWHKyx3mP8x7nmzd/1GdyEP1CAQCASifrZJ3s6FjmWuZa5rprF3uLvcXeGjq0en5au3a8nJfz8k6d8lPyU/JTYmIq+wwYgQAAaIIk0WgaTaO/+IJm0SyaNWJEtf/IPMqjvJde0g/QD9APcHOrdGIhrxMAANzGmJwr58q569ZRLMVS7MSJNfajFVJIYYy/wF/gL7z0UmW/vUGNvk4AAHCHTqfT6XQrVtB4Gk/jg4KEBfmBfqAf+vSp7LdhBAIAUMPUwvH66+oj21eBSqmUStu3r+y3oYAAANQQtXDMmKE+WrlSdB4bvpwv58t/+62y34cCAgBQzeSt8lZ568SJFEiBFLh2reg8d2MD2UA28PTpyn4fCggAQDXRh+pD9aEjR1IABVDA5s20ntbTeklzf3eZF/NiXvv2Vfb7NHciAAC1nRwsB8vBvr5Wf6u/1X/nTubO3Jl7A+0tWvImb/LOyemc3zm/c/6ePZX9dmxlAgBQRfTd9N303Tw8rFusW6xbEhPZLDaLzXJyEp3rHjNoBs24dYt/wj/hn3h5mUwmk8mkKJV9GoxAAAAekz5AH6APeOYZ6znrOeu5Awc0WzgCKZACrVZ2hB1hR15++VELhw1GIAAAj0hdVdWli/ooNVX9WvnlsNUflHSk45wbuZEbg4LUwrFhw+M+LUYgAACV1CuoV1CvoCef5Kv4Kr4qIUE9qsHCcRsv4AW8YOHCqiocNtq7qAMAoFHqZoetW9MgGkSDDh+mhbSQFnbuLDrX/YWGmmJMMaaYsLCqfmZMYQEAPIBt23PLp5ZPLZ8mJ9MROkJHdDr蕾特码码");
        background-size: contain;
    }

    .icon-close:before {
        content: " ";
        display: block;
        width: 16px;
        height: 16px;
        position: absolute;
        margin: auto;
        left: 0;
        right: 0;
        top: 0;
        bottom: 0;
        z-index: 9999;
        background-image: url("data:image/png;base64,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");
        background-size: contain;
    }

    .icon-right:before {
        content: " ";
        display: block;
        width: 16px;
        height: 16px;
        position: absolute;
        margin: auto;
        left: 0;
        right: 0;
        top: 0;
        bottom: 0;
        background-size: cover;
        z-index: 9999;
        background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADIEAYAAAD9yHLdAAAABGdBTUEAALGPC/xhBQAAAAFzUkdCAK7OHOkAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAAAAZiS0dEAAAAAAAA+UO7fwAAAAlwSFlzAAAASAAAAEgARslrPgAAJ4pJREFUeNrt3XtcVXW6P/Dn2VwCBxUzNbnkkXRSGzXW2huQRLyMIqKRJF7Q1CkrDS+VGp3Gy9g5YzI6qVsNfTmlqGmipQiIiJqAcnOvhaKRHidshoatpKaBogL7OX+s6Mz8flO5CfzutXne/+zXWhR8QOXZ3+93Pd8vAHuAEKW10lpp7dix0mXpsnR5/34pX8qX8r/7TpZlWZaJGl//9f6+fY3/X+PnEf2dMMYY/yJqYcbbxtvG2/7+lEM5lLN7NyyCRbBowICmfj56m96mt/PzDZGGSEPkxImWNpY2ljYVFaK/T8ZY6+MiOoCzMn1t+tr09a9/TQfpIB0sLITlsByW9+r1Sz8v5mEe5vn7Q3toD+0nT/Y77Xfa73ROTuWNyhuVNyorRX/fjLHWg0cgzUybcmrThvIoj/JUFcMwDMOeeKLFvmA8xEN8TQ2sh/Ww/rnnFFVRFfXwYdE/B8aY8zOIDuBsqDf1pt6vvdbaui//wL90DawAMXdHc7BOTi3Y4e8R94j75kwR/RPhDHmvHgKqpqZYk2xptinnrKV28pt5SUlkPM0ogE0gAb88Y/qenW9un7x4u/vkuhcjDH94xFIQ6lX6pV6pXiZ/mX5l+Vf/8sP6SAdpC/1199Wd4O7wd3QfPtt+YV8IT/Yv596eHiKzsUYcxw8gmgqT086Tadpd+/STr29aR/t026qqtT0qKq2f00NfUff0Xdf/lXqHekd6R2TkyM6F2PMcfEUVuujcTE6ICAgICAgOpr20B7ao79GPpyH83Deww8b0IAGzM4OzI+1H2QpKhYdiTFHwlNYLYc0WZoszZZmjx6NFbAMlsGyGzdE52qqL7/Ueucf1lT0R3/0//CD6FyMseblIsIWQtpEaZI0SVo0diwqoyIqatw40bmabsoUZZAySJncuVPeIe+Qd8TGio7FGGMti9dAGGPthN95MMbYj+MCwlh74HceTOfi94Eyxh6o/yFpjDSGNZKyyP+QNIay0tLSstIZM0Tna6oklqySFau0tLTG/zL+l/G/Hn20sLdhr2FvKCpEdCzWWnENhLHmJh/yR/5+/jxaR+to3dSpdJAO0tFvvxWdr2leeklb0n/rFuVQDnnySeVQDn/6E+VQDlOnio7FWmvBBYSxZid1yjpJnbI2bICt2NZR9/1h7N+xDZZhGZb95S98D/fgnm++oRAKoRBeAxGDRyCMNQMpUyW/kt/2xBO2L1u2fX3DhiJ312D/RjW2Xq5e/exv1r3Wvda9fMIE7cOFC6JzMdYK8FMwxh5Enn6aLtPlDRuYwAxmK1eKztV0e/ZoY87mzfA/PPA/PPDOO/y/s2PHW8dG7iLdM3gEwpjL8tpr2qQ2K4u+oS/oa9mXq/p1qgJb1mU2fJn2f5l2Zc4cbZPh6ePqLzkGexQ8gmDMdfmlnO1T+2n7tF27tA69bdrY5W13o8z1tq8Zf+rKkCFcRBh7BDyCYMxl2fGnvLfyV7+SHyX+RfxX/x9mG/5j3O3/X79e8Vf8ld/k5Ohc9eWnBvLqA49AGDsv21+0H/xbeU//d/h7/P/iF8W/jv+Pvy+wL/An+BO8Tz6Z59Hz7NmeX5UeqR6pHqkeeeKJJ81F4y/G/w9X21Fh9aQ1+AAAAABJRU5ErkJggg==");
        background-size: contain;
    }

    .icon-refresh:before {
        content: " ";
        display: block;
        width: 16px;
        height: 16px;
        position: absolute;
        margin: auto;
        left: 0;
        right: 0;
        top: 0;
        bottom: 0;
        z-index: 9999;
        background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADIEAYAAAD9yHLdAAAABGdBTUEAALGPC/xhBQAAAAFzUkdCAK7OHOkAAAAgY0hSTQAAeiYAAICEAAD6AAAAgOgAAHUwAADqYAAAOpgAABdwnLpRPAAAAAZiS0dEAAAAAAAA+UO7fwAAAAlwSFlzAAAASAAAAEgARslrPgAAMQpJREFUeNrt3XlcVHX3B/Bz7rCISi6IC+ijkpZpIswMyBLgluVuKm4pqWmEuG/hUpr5uFYoiuaSFrklZvroo+jPFRURZgYVxZ1K3HIXUBSGe35/XC9PWpYL8J2B8/6H1wwGn3sb5sz93u/3fAEYY4wxxhhjjDHGGGOMMcYYY4wxxhhjjDHGGGOMMcYYY4wxxhhjjDHGGGOMMcYYY4wxxhhjjDHGGGOM/QUUHYCx59F0dd");
        background-size: contain;
    }
</style>
