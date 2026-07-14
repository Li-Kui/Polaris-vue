<template>
  <router-view />
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import {handleThemeStyle} from '@/utils/theme'

onMounted(() => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
})
</script>

<style lang="scss">
// 引入登录页设计 Token（5 套方案 × 亮/暗）
@import "@/styles/login-tokens.scss";
</style>
