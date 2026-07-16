<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <div class="polaris-table-card iframe-card">
            <i-frame v-model:src="url"></i-frame>
         </div>
      </div>
   </div>
</template>

<script setup>
import iFrame from '@/components/iFrame'
import {ref} from 'vue'

const url = ref(import.meta.env.VITE_APP_BASE_API + '/druid/login.html')
</script>

<style lang="scss" scoped>
/* 覆盖全局 .app-container 的 padding: 20px */
.app-container.no-sidebar-manage-wrap {
  padding: 16px !important;
  height: 100%;
  box-sizing: border-box;
}

.content-inner {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.iframe-card {
  padding: 0 !important;
  overflow: hidden;
  height: 100%;
  border-radius: 22px;

  :deep(> div) {
    height: 100% !important;
  }
}
</style>
