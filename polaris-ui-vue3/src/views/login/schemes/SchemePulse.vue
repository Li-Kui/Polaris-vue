<!-- 脉冲 Pulse — 赛博朋克 HUD 网格扫描线背景 -->
<template>
  <div class="pulse-bg" aria-hidden="true">
    <!-- 网格覆盖层 -->
    <div class="pulse-grid" />
    <!-- 水平扫描线 -->
    <div class="scan-line" />
    <div class="scan-line scan-line--delayed" />
    <!-- 左侧 HUD 状态面板 -->
    <div class="hud-panel">
      <!-- 四角装饰括号 -->
      <div class="corner corner-tl" />
      <div class="corner corner-tr" />
      <div class="corner corner-bl" />
      <div class="corner corner-br" />
      <!-- 状态文本 -->
      <div class="hud-text">POLARIS AI ENGINE v3.9
─────────────────────
STATUS    <span class="accent">● ACTIVE</span>
UPTIME    4d 12h 36m
NEURAL    ████████░░ 81%
MEMORY    █████░░░░░ 52%
LATENCY   18ms
─────────────────────
READY FOR AUTH</div>
    </div>
  </div>
</template>

<script setup>
defineProps({ theme: { type: String, default: 'light' } })
</script>

<style lang="scss" scoped>
/* 主容器 */
.pulse-bg {
  position: absolute;
  inset: 0;
  background-color: var(--bg);
  overflow: hidden;
  z-index: 0;
}

/* 背景网格 */
.pulse-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, var(--grid-color) 1px, transparent 1px),
    linear-gradient(to bottom, var(--grid-color) 1px, transparent 1px);
  background-size: 32px 32px;
  opacity: var(--decor-opacity);
}

/* 扫描线 */
.scan-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  animation: pulse-scan 8s linear infinite;

  &--delayed { animation-delay: 4s; }
}

/* HUD 状态面板 */
.hud-panel {
  position: absolute;
  left: 32px;
  top: 50%;
  transform: translateY(-50%);
  width: 220px;
  padding: 16px;
  background: rgba(var(--card-rgb, 15, 23, 42), 0.6);
  backdrop-filter: blur(12px);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  z-index: 5;
}

/* 状态文本排版 */
.hud-text {
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-2);
  white-space: pre;
}
.accent { color: var(--accent); }

/* 四角装饰括号 — 通用 */
.corner {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid var(--accent);
}
.corner-tl { top: -1px; left: -1px; border-right: none; border-bottom: none; }
.corner-tr { top: -1px; right: -1px; border-left: none; border-bottom: none; }
.corner-bl { bottom: -1px; left: -1px; border-right: none; border-top: none; }
.corner-br { bottom: -1px; right: -1px; border-left: none; border-top: none; }

/* 扫描线动画 */
@keyframes pulse-scan {
  0%   { top: -3px; opacity: 0; }
  5%   { opacity: 1; }
  95%  { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

/* 小屏隐藏面板 */
@media (max-width: 960px) {
  .hud-panel { display: none; }
}
</style>
