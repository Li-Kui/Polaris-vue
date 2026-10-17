<template>
  <header class="runtime-header">
    <div class="header-brand"><span class="brand-icon"><img :src="logo" alt="" /></span><span class="brand-title">北辰 AI</span></div>
    <div class="header-actions"><slot /></div>
  </header>
</template>

<script setup>
import logo from '@/assets/logo/logo.png'
</script>

<style scoped>
.runtime-header { display: flex; flex-shrink: 0; align-items: center; justify-content: space-between; gap: 16px; min-height: 72px; margin: 0 22px; border-bottom: 1px solid var(--runtime-border); }
.header-brand { display: flex; align-items: center; gap: 10px; min-width: 0; }
.brand-icon { display: grid; place-items: center; flex: 0 0 34px; height: 34px; border-radius: 11px; background: linear-gradient(145deg, #7064ed, #4f46e5); box-shadow: 0 3px 9px rgb(79 70 229 / 18%); }
.brand-icon img { width: 26px; height: 26px; object-fit: contain; }
.brand-title { font-size: 17px; font-weight: 650; color: var(--runtime-text); letter-spacing: -.3px; }
.header-actions { flex-shrink: 0; }
</style>
