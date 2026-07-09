<template>
  <el-drawer :append-to-body="true" :before-close="closeSetting" :lock-scroll="false" :visible="showSettings" :with-header="false" custom-class="polaris-settings-drawer" size="280px">
    <div class="drawer-container">
      <div>
        <div class="setting-drawer-content">
          <div class="setting-drawer-title">
            <h3 class="drawer-title">Polaris 极光视觉设置</h3>
          </div>

          <div class="drawer-item">
            <span>主题风格</span>
            <el-select v-model="polarisTheme" size="mini" style="float: right; width: 140px; margin-top: -3px;">
              <el-option label="🌌 极光玻璃 (A - 暗)" value="A" />
              <el-option label="⚡ 赛博霓虹 (B - 暗)" value="B" />
              <el-option label="⚙️ 钛金极简 (C - 暗)" value="C" />
              <el-option label="经典原版" value="default" />
            </el-select>
          </div>

          <div class="drawer-item">
            <span>排版结构</span>
            <el-select v-model="polarisLayout" size="mini" style="float: right; width: 120px; margin-top: -3px;">
              <el-option label="🛸 悬浮 Dock (1)" value="1" />
              <el-option label="📊 双轨微缩 (2)" value="2" />
              <el-option label="💻 顶部通栏 (3)" value="3" />
              <el-option label="经典左侧" value="default" />
            </el-select>
          </div>
        </div>

        <el-divider/>

        <h3 class="drawer-title">系统布局配置</h3>

        <div class="drawer-item">
          <span>开启页签</span>
          <el-switch v-model="tagsView" class="drawer-switch" />
        </div>

        <div class="drawer-item">
          <span>持久化标签页</span>
          <el-switch v-model="tagsViewPersist" :disabled="!tagsView" class="drawer-switch" />
        </div>

        <div class="drawer-item">
          <span>显示页签图标</span>
          <el-switch v-model="tagsIcon" :disabled="!tagsView" class="drawer-switch" />
        </div>

        <div class="drawer-item">
          <span>标签页样式</span>
          <el-radio-group v-model="tagsViewStyle" :disabled="!tagsView" class="drawer-switch" size="mini">
            <el-radio-button label="card">卡片</el-radio-button>
            <el-radio-button label="chrome">谷歌</el-radio-button>
          </el-radio-group>
        </div>

        <div class="drawer-item">
          <span>固定 Header</span>
          <el-switch v-model="fixedHeader" class="drawer-switch" />
        </div>

        <div class="drawer-item">
          <span>显示 Logo</span>
          <el-switch v-model="sidebarLogo" class="drawer-switch" />
        </div>

        <div class="drawer-item">
          <span>动态标题</span>
          <el-switch v-model="dynamicTitle" class="drawer-switch" />
        </div>

        <div class="drawer-item">
          <span>底部版权</span>
          <el-switch v-model="footerVisible" class="drawer-switch" />
        </div>

        <el-divider/>

        <el-button icon="el-icon-document-add" plain size="small" type="primary" @click="saveSetting">保存配置</el-button>
        <el-button icon="el-icon-refresh" plain size="small" @click="resetSetting">重置配置</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import ThemePicker from '@/components/ThemePicker'

export default {
  components: { ThemePicker },
  expose: ['openSetting'],
  data() {
    return {
      theme: this.$store.state.settings.theme,
      sideTheme: this.$store.state.settings.sideTheme,
      navType: this.$store.state.settings.navType,
      showSettings: false
    }
  },
  computed: {
    fixedHeader: {
      get() {
        return this.$store.state.settings.fixedHeader
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'fixedHeader',
          value: val
        })
      }
    },
    tagsViewPersist: {
      get() {
        return this.$store.state.settings.tagsViewPersist
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'tagsViewPersist',
          value: val
        })
      }
    },
    tagsView: {
      get() {
        return this.$store.state.settings.tagsView
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'tagsView',
          value: val
        })
      }
    },
    tagsIcon: {
      get() {
        return this.$store.state.settings.tagsIcon
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'tagsIcon',
          value: val
        })
      }
    },
    tagsViewStyle: {
      get() {
        return this.$store.state.settings.tagsViewStyle
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'tagsViewStyle',
          value: val
        })
      }
    },
    sidebarLogo: {
      get() {
        return this.$store.state.settings.sidebarLogo
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'sidebarLogo',
          value: val
        })
      }
    },
    dynamicTitle: {
      get() {
        return this.$store.state.settings.dynamicTitle
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'dynamicTitle',
          value: val
        })
        this.$store.dispatch('settings/setTitle', this.$store.state.settings.title)
      }
    },
    footerVisible: {
      get() {
        return this.$store.state.settings.footerVisible
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'footerVisible',
          value: val
        })
      }
    },
    polarisTheme: {
      get() {
        return this.$store.state.settings.polarisTheme
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'polarisTheme',
          value: val
        })
      }
    },
    polarisLayout: {
      get() {
        return this.$store.state.settings.polarisLayout
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'polarisLayout',
          value: val
        })
        // 联动导航模式
        if (val === '1' || val === '2') {
          this.handleNavType(1)
        } else if (val === '3') {
          this.handleNavType(3)
        }
      }
    }
  },
  watch: {
    navType: {
      handler(val) {
        if (val == 1) {
          this.$store.dispatch("app/toggleSideBarHide", false)
          if (this.polarisLayout === '3') {
            this.$store.dispatch('settings/changeSetting', { key: 'polarisLayout', value: '1' })
          }
        }
        if (val == 2) {
          if (this.polarisLayout === '3') {
            this.$store.dispatch('settings/changeSetting', { key: 'polarisLayout', value: '1' })
          }
        }
        if (val == 3) {
          this.$store.dispatch("app/toggleSideBarHide", true)
          this.$store.dispatch('settings/changeSetting', { key: 'polarisLayout', value: '3' })
        }
        if ([1, 3].includes(val)) {
          this.$store.commit("SET_SIDEBAR_ROUTERS",this.$store.state.permission.defaultRoutes)
        }
      },
      immediate: true,
      deep: true
    }
  },
  methods: {
    themeChange(val) {
      this.$store.dispatch('settings/changeSetting', {
        key: 'theme',
        value: val
      })
      this.theme = val
    },
    handleTheme(val) {
      this.$store.dispatch('settings/changeSetting', {
        key: 'sideTheme',
        value: val
      })
      this.sideTheme = val
    },
    handleNavType(val) {
      this.$store.dispatch('settings/changeSetting', {
        key: 'navType',
        value: val
      })
      this.navType = val
    },
    openSetting() {
      this.showSettings = true
    },
    closeSetting(){
      this.showSettings = false
    },
    saveSetting() {
      this.$modal.loading("正在保存到本地，请稍候...")
      if (!this.tagsViewPersist) {
        this.$cache.local.remove('tags-view-visited')
      }
      this.$cache.local.set(
        "layout-setting",
        `{
            "navType":${this.navType},
            "tagsView":${this.tagsView},
            "tagsIcon":${this.tagsIcon},
            "tagsViewStyle":"${this.tagsViewStyle}",
            "tagsViewPersist":${this.tagsViewPersist},
            "fixedHeader":${this.fixedHeader},
            "sidebarLogo":${this.sidebarLogo},
            "dynamicTitle":${this.dynamicTitle},
            "footerVisible":${this.footerVisible},
            "sideTheme":"${this.sideTheme}",
            "theme":"${this.theme}",
            "polarisTheme":"${this.polarisTheme}",
            "polarisLayout":"${this.polarisLayout}"
          }`
      )
      setTimeout(this.$modal.closeLoading(), 1000)
    },
    resetSetting() {
      this.$modal.loading("正在清除设置缓存并刷新，请稍候...")
      this.$cache.local.remove('tags-view-visited')
      this.$cache.local.remove("layout-setting")
      setTimeout("window.location.reload()", 1000)
    }
  }
}
</script>

<style lang="scss" scoped>
.setting-drawer-content {
  .setting-drawer-title {
    margin-bottom: 12px;
    color: rgba(0, 0, 0, .85);
    font-size: 14px;
    line-height: 22px;
    font-weight: bold;
  }

  .setting-drawer-block-checbox {
    display: flex;
    justify-content: flex-start;
    align-items: center;
    margin-top: 10px;
    margin-bottom: 20px;

    .setting-drawer-block-checbox-item {
      position: relative;
      margin-right: 16px;
      border-radius: 2px;
      cursor: pointer;

      img {
        width: 48px;
        height: 48px;
      }

      .setting-drawer-block-checbox-selectIcon {
        position: absolute;
        top: 0;
        right: 0;
        width: 100%;
        height: 100%;
        padding-top: 15px;
        padding-left: 24px;
        color: #1890ff;
        font-weight: 700;
        font-size: 14px;
      }
    }
  }
}

.drawer-container {
  padding: 20px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;

  .drawer-title {
    margin-bottom: 12px;
    color: rgba(0, 0, 0, .85);
    font-size: 14px;
    line-height: 22px;
  }

  .drawer-item {
    color: rgba(0, 0, 0, .65);
    font-size: 14px;
    padding: 12px 0;
  }

  .drawer-switch {
    float: right
  }
}

// 导航模式
.nav-wrap {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  margin-top: 10px;
  margin-bottom: 20px;

  .activeItem {
    border: 2px solid #{'var(--theme)'} !important;
  }

  .item {
    position: relative;
    margin-right: 16px;
    cursor: pointer;
    width: 56px;
    height: 48px;
    border-radius: 4px;
    background: #f0f2f5;
    border: 2px solid transparent;
  }

  .left {
    b:first-child {
      display: block;
      height: 30%;
      background: #fff;
    }
    b:last-child {
      width: 30%;
      background: #1b2a47;
      position: absolute;
      height: 100%;
      top: 0;
      border-radius: 4px 0 0 4px;
    }
  }
  .mix {
    b:first-child {
      border-radius: 4px 4px 0 0;
      display: block;
      height: 30%;
      background: #1b2a47;
    }
    b:last-child {
      width: 30%;
      background: #1b2a47;
      position: absolute;
      height: 70%;
      border-radius: 0 0 0 4px;
    }
  }
  .top {
    b:first-child {
      display: block;
      height: 30%;
      background: #1b2a47;
      border-radius: 4px 4px 0 0;
    }
  }
}
</style>
