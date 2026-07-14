<!-- 方案三：赛博虚空视界 (Cybernetic Void Horizon) -->
<template>
  <div :class="['new-scheme-horizon', theme]" aria-hidden="true">
    <!-- 地平线上方：天空与激光星束 -->
    <div class="sky-dome">
      <!-- 激光束线 (SVG 放射光束) -->
      <svg class="laser-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 500" preserveAspectRatio="none">
        <line x1="200" y1="260" x2="-100" y2="0" class="laser-beam beam-1" />
        <line x1="200" y1="260" x2="300" y2="-100" class="laser-beam beam-2" />
        <line x1="200" y1="260" x2="700" y2="-50" class="laser-beam beam-3" />
        <line x1="200" y1="260" x2="1100" y2="100" class="laser-beam beam-4" />
      </svg>
      
      <!-- 飘过的数据云带 -->
      <div class="data-cloud cloud-1"></div>
      <div class="data-cloud cloud-2"></div>
    </div>

    <!-- 视觉中轴：发光地平线 -->
    <div class="horizon-line"></div>

    <!-- 地平线下方：透视 3D 网格地面 -->
    <div class="grid-ground-wrap">
      <div class="grid-ground"></div>
      <!-- 3D 深度网格激光扫描线 -->
      <div class="ground-scan-line"></div>
    </div>

    <!-- 视界中心：3D 旋转超立方体 (Hypercube) -->
    <div class="hypercube-container">
      <div class="hypercube-viewport">
        <!-- 3D 旋转盒子 (双层嵌套高维超立方体) -->
        <div class="cube cube-outer">
          <div class="face front"></div>
          <div class="face back"></div>
          <div class="face left"></div>
          <div class="face right"></div>
          <div class="face top"></div>
          <div class="face bottom"></div>
        </div>
        <div class="cube cube-inner">
          <div class="face front"></div>
          <div class="face back"></div>
          <div class="face left"></div>
          <div class="face right"></div>
          <div class="face top"></div>
          <div class="face bottom"></div>
        </div>
        <!-- 立方体下方的全息投影光晕 -->
        <div class="cube-shadow"></div>
      </div>
      <!-- AI 算力原点文字 HUD 嵌入项目名 -->
      <div class="cube-hud">{{ projectTitle.toUpperCase() }} // CORE_ACTIVE</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  theme: {
    type: String,
    default: 'light'
  },
  projectTitle: {
    type: String,
    default: 'Polaris Vue'
  }
})
</script>

<script>
export default {
  name: 'NewSchemeHorizon'
}
</script>

<style lang="scss" scoped>
.new-scheme-horizon {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;
  display: flex;
  flex-direction: column;
  transition: background-color 0.5s ease;

  // ===== 主题色系定义 =====
  &.light {
    --sky-bg: linear-gradient(to bottom, #e0f2fe 0%, #bae6fd 60%, #e0f2fe 100%);
    --horizon-color: rgba(2, 132, 199, 0.4);
    --horizon-shadow: 0 0 15px rgba(2, 132, 199, 0.3);
    --ground-bg: #f0f9ff;
    --grid-color: rgba(2, 132, 199, 0.12);
    --laser-color: rgba(2, 132, 199, 0.15);
    --cloud-color: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.6), transparent);
    --cube-border: 1px solid rgba(2, 132, 199, 0.5);
    --cube-bg: rgba(56, 189, 248, 0.15);
    --cube-glow: rgba(56, 189, 248, 0.4);
    --hud-text-color: rgba(2, 132, 199, 0.7);
  }

  &.dark {
    --sky-bg: linear-gradient(to bottom, #020617 0%, #0c1e3e 70%, #030712 100%);
    --horizon-color: rgba(236, 72, 153, 0.75);
    --horizon-shadow: 0 0 20px rgba(236, 72, 153, 0.5), 0 0 40px rgba(56, 189, 248, 0.3);
    --ground-bg: #030712;
    --grid-color: rgba(236, 72, 153, 0.22);
    --laser-color: rgba(56, 189, 248, 0.3);
    --cloud-color: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.25), transparent);
    --cube-border: 1px solid rgba(236, 72, 153, 0.7);
    --cube-bg: rgba(236, 72, 153, 0.1);
    --cube-glow: rgba(236, 72, 153, 0.5);
    --hud-text-color: rgba(236, 72, 153, 0.75);
  }
}

// ===== 上半部天空与激光 =====
.sky-dome {
  flex: 11;
  position: relative;
  background: var(--sky-bg);
  overflow: hidden;
}

.laser-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.laser-beam {
  stroke: var(--laser-color);
  stroke-width: 1.5;
  stroke-linecap: round;
  
  &.beam-1 { animation: laser-pulse 4s infinite alternate; }
  &.beam-2 { animation: laser-pulse 3s infinite alternate-reverse; }
  &.beam-3 { animation: laser-pulse 5s infinite alternate; }
  &.beam-4 { animation: laser-pulse 3.5s infinite alternate-reverse; }
}

@keyframes laser-pulse {
  0% { opacity: 0.2; stroke-width: 0.8px; }
  100% { opacity: 1; stroke-width: 2px; }
}

.data-cloud {
  position: absolute;
  height: 1px;
  background: var(--cloud-color);
  pointer-events: none;
  
  &.cloud-1 {
    top: 20%;
    left: -100%;
    width: 80%;
    animation: slide-cloud 24s linear infinite;
  }
  &.cloud-2 {
    top: 45%;
    left: -100%;
    width: 60%;
    animation: slide-cloud 18s linear infinite;
    animation-delay: -6s;
  }
}

@keyframes slide-cloud {
  to { left: 100%; }
}

// ===== 发光地平线 =====
.horizon-line {
  height: 2px;
  background-color: var(--horizon-color);
  box-shadow: var(--horizon-shadow);
  z-index: 5;
}

// ===== 下半部 3D 地面网格 =====
.grid-ground-wrap {
  flex: 9;
  position: relative;
  background-color: var(--ground-bg);
  overflow: hidden;
  perspective: 200px;
}

.grid-ground {
  position: absolute;
  inset: -100% 0 0 0;
  width: 100%;
  height: 200%;
  background-image:
    linear-gradient(to right, var(--grid-color) 1px, transparent 1px),
    linear-gradient(to bottom, var(--grid-color) 1px, transparent 1px);
  background-size: 40px 40px;
  transform: rotateX(80deg);
  transform-origin: top center;
  animation: grid-flow 25s linear infinite;
}

// 3D 扫描波纹
.ground-scan-line {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 12px;
  background: linear-gradient(to bottom, transparent, var(--horizon-color), transparent);
  box-shadow: 0 0 24px var(--horizon-color);
  opacity: 0;
  transform: rotateX(80deg);
  transform-origin: top center;
  animation: ground-scan-beam 8s ease-in-out infinite;
}

@keyframes ground-scan-beam {
  0% { top: 0%; opacity: 0; }
  10% { opacity: 0.8; }
  90% { opacity: 0.8; }
  100% { top: 180%; opacity: 0; }
}

@keyframes grid-flow {
  from { background-position-y: 0; }
  to { background-position-y: 1000px; } // 往前移动的网格流
}

// ===== 3D 超立方体 (AI 核心) =====
.hypercube-container {
  position: absolute;
  left: 20%;
  top: 36%;
  width: 200px;
  height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
  transform: translate(calc(var(--mouse-x, 0) * 18px), calc(var(--mouse-y, 0) * 18px));
  transition: transform 0.25s cubic-bezier(0.2, 0.8, 0.2, 1);
  
  @media (max-width: 1024px) {
    display: none; // 窄屏隐藏超立方体，保障卡片清晰展示
  }
}

.hypercube-viewport {
  position: relative;
  width: 100px;
  height: 100px;
  perspective: 600px;
  transform-style: preserve-3d;
}

// 外层立方体 (顺时针)
.cube-outer {
  position: absolute;
  width: 60px;
  height: 60px;
  top: 20px;
  left: 20px;
  transform-style: preserve-3d;
  animation: rotate-cube-cw 16s linear infinite;
}

// 内层立方体 (逆时针、小体量)
.cube-inner {
  position: absolute;
  width: 32px;
  height: 32px;
  top: 34px;
  left: 34px;
  transform-style: preserve-3d;
  animation: rotate-cube-ccw 8s linear infinite;
  
  .face {
    width: 32px;
    height: 32px;
    &.front  { transform: rotateY(0deg) translateZ(16px); }
    &.back   { transform: rotateY(180deg) translateZ(16px); }
    &.left   { transform: rotateY(-90deg) translateZ(16px); }
    &.right  { transform: rotateY(90deg) translateZ(16px); }
    &.top    { transform: rotateX(90deg) translateZ(16px); }
    &.bottom { transform: rotateX(-90deg) translateZ(16px); }
  }
}

.face {
  position: absolute;
  width: 60px;
  height: 60px;
  border: var(--cube-border);
  background-color: var(--cube-bg);
  box-shadow: 0 0 10px var(--cube-glow);
  box-sizing: border-box;

  &.front  { transform: rotateY(0deg) translateZ(30px); }
  &.back   { transform: rotateY(180deg) translateZ(30px); }
  &.left   { transform: rotateY(-90deg) translateZ(30px); }
  &.right  { transform: rotateY(90deg) translateZ(30px); }
  &.top    { transform: rotateX(90deg) translateZ(30px); }
  &.bottom { transform: rotateX(-90deg) translateZ(30px); }
}

@keyframes rotate-cube-cw {
  0% { transform: rotateX(0deg) rotateY(0deg) rotateZ(0deg); }
  100% { transform: rotateX(360deg) rotateY(360deg) rotateZ(360deg); }
}

@keyframes rotate-cube-ccw {
  0% { transform: rotateX(360deg) rotateY(360deg) rotateZ(360deg); }
  100% { transform: rotateX(0deg) rotateY(0deg) rotateZ(0deg); }
}

.cube-shadow {
  position: absolute;
  bottom: -15px;
  left: 10px;
  width: 80px;
  height: 15px;
  background: var(--cube-glow);
  filter: blur(10px);
  border-radius: 50%;
  transform: rotateX(85deg);
  animation: shadow-breath 3s ease-in-out infinite alternate;
}

@keyframes shadow-breath {
  0% { transform: rotateX(85deg) scale(0.8); opacity: 0.4; }
  100% { transform: rotateX(85deg) scale(1.2); opacity: 0.8; }
}

.cube-hud {
  margin-top: 40px;
  font-family: monospace;
  font-size: 10px;
  color: var(--hud-text-color);
  letter-spacing: 0.1em;
  text-shadow: 0 0 4px var(--cube-glow);
  animation: hud-flash 2s infinite alternate;
}

@keyframes hud-flash {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}
</style>
