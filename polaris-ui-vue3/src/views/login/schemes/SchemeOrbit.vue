<!-- 星轨 Orbit — 分屏宇宙星场与北极星装饰背景 -->
<template>
  <div class="orbit-bg" aria-hidden="true">
    <!-- 左侧星图面板 -->
    <div class="orbit-side">
      <!-- 星场 SVG -->
      <svg class="star-svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        <!-- 轨道圆弧（圆心在左下角） -->
        <circle cx="0" cy="100" r="100" stroke="rgba(255,255,255,0.06)" fill="none" stroke-width="1" />
        <circle cx="0" cy="100" r="220" stroke="rgba(255,255,255,0.06)" fill="none" stroke-width="1" stroke-dasharray="4 6" />
        <circle cx="0" cy="100" r="360" stroke="rgba(255,255,255,0.06)" fill="none" stroke-width="1" />
        <!-- 散落星点 -->
        <circle cx="15" cy="20" r="1.5" fill="rgba(255,255,255,0.3)" />
        <circle cx="70" cy="12" r="2"   fill="rgba(255,255,255,0.25)" />
        <circle cx="55" cy="50" r="1.5" fill="rgba(255,255,255,0.4)" />
        <circle cx="30" cy="65" r="2.5" fill="rgba(255,255,255,0.2)" />
        <circle cx="80" cy="35" r="3"   fill="rgba(255,255,255,0.35)" />
        <circle cx="25" cy="42" r="2"   fill="rgba(255,255,255,0.5)" />
        <circle cx="62" cy="72" r="3.5" fill="rgba(255,255,255,0.2)" />
        <circle cx="48" cy="18" r="1.5" fill="rgba(255,255,255,0.6)" />
        <circle cx="10" cy="80" r="2"   fill="rgba(255,255,255,0.3)" />
        <circle cx="85" cy="60" r="2.5" fill="rgba(255,255,255,0.25)" />
        <!-- 北极星 -->
        <circle class="polaris-star" cx="40" cy="38" r="5" fill="var(--accent)" />
        <!-- 星座连线 -->
        <line x1="40" y1="38" x2="15" y2="20" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
        <line x1="40" y1="38" x2="55" y2="50" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
        <line x1="40" y1="38" x2="25" y2="42" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
        <line x1="40" y1="38" x2="48" y2="18" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
      </svg>
      <!-- 品牌信息 -->
      <div class="brand">
        <div class="brand-name">⬢ Polaris Vue</div>
        <div class="brand-slogan">Navigate your code, ship faster.</div>
        <span class="brand-version">v3.9</span>
      </div>
    </div>

    <!-- 右侧主内容区 -->
    <div class="orbit-main">
      <!-- 移动端水印（桌面端隐藏） -->
      <div class="mobile-watermark">✦ Polaris</div>
    </div>
  </div>
</template>

<script setup>
defineProps({ theme: { type: String, default: 'light' } })
</script>

<style lang="scss" scoped>
/* 主容器 — 分屏布局 */
.orbit-bg {
  display: flex;
  position: absolute;
  inset: 0;
  background-color: var(--bg);
  overflow: hidden;
  z-index: 0;
}

/* 左侧星图面板 */
.orbit-side {
  width: 44%;
  height: 100%;
  background: var(--bg-side);
  position: relative;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
}

/* 星场 SVG 覆盖 */
.star-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

/* 北极星呼吸动画 */
.polaris-star {
  animation: orbit-star-glow 3s ease-in-out infinite;
}

/* 品牌区域 */
.brand {
  position: absolute;
  bottom: 32px;
  left: 32px;
  z-index: 2;
}
.brand-name {
  color: white;
  font-size: 16px;
  font-weight: 600;
  font-family: var(--font-mono);
}
.brand-slogan {
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  margin-top: 4px;
}
.brand-version {
  display: inline-block;
  margin-top: 8px;
  font-size: 9px;
  font-family: var(--font-mono);
  color: rgba(255, 255, 255, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
}

/* 右侧主区域 */
.orbit-main {
  flex: 1;
  position: relative;
}

/* 移动端水印 — 默认隐藏 */
.mobile-watermark {
  display: none;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-3);
  opacity: 0.5;
  position: absolute;
  top: 20px;
  left: 20px;
}

/* 北极星光晕动画 */
@keyframes orbit-star-glow {
  0%, 100% { opacity: 0.7; filter: drop-shadow(0 0 4px var(--accent)); }
  50%      { opacity: 1;   filter: drop-shadow(0 0 14px var(--accent)); }
}

/* 小屏适配：隐藏左面板，显示水印 */
@media (max-width: 960px) {
  .orbit-side { display: none; }
  .mobile-watermark { display: flex; }
}
</style>
