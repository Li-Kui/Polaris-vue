<template>
  <footer v-if="visible" class="copyright">
    <span>Copyright © 2018-2026 Polaris All Rights Reserved.</span>
    <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" class="beian-link">
      湘ICP备2026028400号-1
    </a>
  </footer>
</template>

<script setup>
import {computed} from 'vue'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()

const visible = computed(() => settingsStore.footerVisible)
</script>

<style scoped>
.copyright {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 36px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background-color: var(--polaris-card-bg, #f8fafc);
  color: var(--polaris-text-sub, #64748b);
  font-size: 13px;
  border-top: 1px solid var(--polaris-card-border, #e2e8f0);
  z-index: 999;
}

.beian-link {
  color: inherit;
  text-decoration: none;
  transition: color 0.2s ease;
}

.beian-link:hover {
  color: var(--polaris-brand-color, #3b82f6);
  text-decoration: underline;
}
</style>