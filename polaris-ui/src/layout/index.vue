<template>
  <div :class="[classObj, 'polaris-theme-' + polarisTheme, 'polaris-layout-' + polarisLayout]" :style="{'--current-color': theme, '--current-color-light': theme + '1a', '--current-color-dark-bg': theme + '33'}" class="app-wrapper">
    <!-- 全局极光背景层 -->
    <div v-if="polarisTheme === 'A' || polarisTheme === 'C'" class="polaris-mesh-bg">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
      <div class="orb orb-4"></div>
    </div>
    <div v-if="polarisTheme === 'B'" class="polaris-cyber-bg"></div>
    <div v-if="polarisTheme !== 'default'" class="polaris-stars-layer"></div>

    <div v-if="device==='mobile'&&sidebar.opened" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!sidebar.hide" class="sidebar-container"/>
    <div :class="{hasTagsView:needTagsView,sidebarHide:sidebar.hide}" class="main-container">
      <div :class="{'fixed-header':fixedHeader}">
        <navbar @setLayout="setLayout"/>
        <tags-view v-if="needTagsView"/>
      </div>
      <app-main/>
      <settings ref="settingRef"/>
    </div>
    <!-- 全局悬浮 AI 聊天 -->
    <ai-float-chat />
  </div>
</template>

<script>
import {AppMain, Navbar, Settings, Sidebar, TagsView} from './components'
import ResizeMixin from './mixin/ResizeHandler'
import {mapState} from 'vuex'
import variables from '@/assets/styles/variables.scss'
import AiFloatChat from '@/components/AiFloatChat'

export default {
  name: 'Layout',
  components: {
    AppMain,
    Navbar,
    Settings,
    Sidebar,
    TagsView,
    AiFloatChat
  },
  mixins: [ResizeMixin],
  computed: {
    ...mapState({
      theme: state => state.settings.theme,
      sideTheme: state => state.settings.sideTheme,
      sidebar: state => state.app.sidebar,
      device: state => state.app.device,
      needTagsView: state => state.settings.tagsView,
      fixedHeader: state => state.settings.fixedHeader,
      polarisTheme: state => state.settings.polarisTheme,
      polarisLayout: state => state.settings.polarisLayout
    }),
    classObj() {
      return {
        hideSidebar: !this.sidebar.opened,
        openSidebar: this.sidebar.opened,
        withoutAnimation: this.sidebar.withoutAnimation,
        mobile: this.device === 'mobile'
      }
    },
    variables() {
      return variables
    }
  },
  mounted() {
    console.log("Polaris Debug - Vuex Layout:", this.polarisLayout, "Theme:", this.polarisTheme)
    this.$nextTick(() => {
      const el = document.querySelector('.app-wrapper')
      if (el) {
        console.log("Polaris Debug - DOM wrapper classes:", el.className)
      } else {
        console.log("Polaris Debug - DOM element .app-wrapper not found!")
      }
    })
  },
  methods: {
    handleClickOutside() {
      this.$store.dispatch('app/closeSideBar', { withoutAnimation: false })
    },
    setLayout() {
      this.$refs.settingRef.openSetting()
    }
  },
  watch: {
    polarisTheme: {
      immediate: true,
      handler(val) {
        // 先移除已有的 polaris-body-theme- 前缀类名
        document.body.className = document.body.className.replace(/\bpolaris-body-theme-\S+/g, '').trim()
        if (val) {
          document.body.classList.add('polaris-body-theme-' + val)
        }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
  @import "~@/assets/styles/mixin.scss";
  @import "~@/assets/styles/variables.scss";

  .app-wrapper {
    @include clearfix;
    position: relative;
    height: 100%;
    width: 100%;

    &.mobile.openSidebar {
      position: fixed;
      top: 0;
    }
  }

  .main-container:has(.fixed-header) {
    height: 100vh;
    overflow: hidden;
  }

  .drawer-bg {
    background: #000;
    opacity: 0.3;
    width: 100%;
    top: 0;
    height: 100%;
    position: absolute;
    z-index: 999;
  }

  .fixed-header {
    position: fixed;
    top: 0;
    right: 0;
    z-index: 9;
    width: calc(100% - #{$base-sidebar-width});
    transition: width 0.28s;
  }

  .hideSidebar .fixed-header {
    width: calc(100% - 54px);
  }

  .sidebarHide .fixed-header {
    width: 100%;
  }

  .mobile .fixed-header {
    width: 100%;
  }
</style>
