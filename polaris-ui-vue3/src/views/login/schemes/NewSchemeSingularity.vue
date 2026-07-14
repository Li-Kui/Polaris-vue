<!-- 方案二：引力坍缩矩阵 (Gravitational Singularity Matrix) -->
<template>
  <div :class="['new-scheme-singularity', theme]" aria-hidden="true">
    <!-- 引力扭曲三维网格 (SVG 扭曲透视网格) -->
    <div class="gravity-grid">
      <svg class="grid-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000" preserveAspectRatio="none">
        <!-- 横向网格线 —— 向中心凹陷扭曲 -->
        <path d="M 0,100 Q 500,200 1000,100" class="grid-line" />
        <path d="M 0,250 Q 500,380 1000,250" class="grid-line" />
        <path d="M 0,400 Q 500,550 1000,400" class="grid-line" />
        <path d="M 0,550 Q 500,680 1000,550" class="grid-line" />
        <path d="M 0,700 Q 500,800 1000,700" class="grid-line" />
        <path d="M 0,850 Q 500,900 1000,850" class="grid-line" />

        <!-- 纵向网格线 —— 向中心收拢 -->
        <path d="M 100,0 Q 200,500 100,1000" class="grid-line" />
        <path d="M 250,0 Q 380,500 250,1000" class="grid-line" />
        <path d="M 400,0 Q 500,500 400,1000" class="grid-line" />
        <path d="M 600,0 Q 500,500 600,1000" class="grid-line" />
        <path d="M 750,0 Q 620,500 750,1000" class="grid-line" />
        <path d="M 900,0 Q 800,500 900,1000" class="grid-line" />
      </svg>
    </div>

    <!-- 吸积盘粒子风暴 (绕卡片核心旋转并向内坍缩) -->
    <div class="accretion-disk">
      <div v-for="i in 20" :key="i" :class="['space-dust', `dust-${i}`]" :style="dustStyles[i-1]"></div>
    </div>

    <!-- 引力中心视觉效果 (位于右侧，卡片后方做背景透射) -->
    <div class="singularity-core-wrap">
      <div class="singularity-core"></div>
      <div class="gravity-ring ring-first"></div>
      <div class="gravity-ring ring-second"></div>
      <div class="gravity-ring ring-third"></div>
      <!-- 动态引力波涟漪 -->
      <div class="gravity-ripple ripple-1"></div>
      <div class="gravity-ripple ripple-2"></div>
      <!-- HUD 项目名字嵌入 -->
      <div class="singularity-hud">{{ projectTitle.toUpperCase() }} // SINGULARITY GRID</div>
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'

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

// 动态生成不同粒子的旋转速度、大小和延迟，展现复杂的奇点坍缩流
const dustStyles = computed(() => {
  return Array.from({ length: 20 }).map((_, idx) => {
    // 旋转半径
    const radius = 120 + (idx % 4) * 45
    // 动画时长
    const duration = 6 + (idx % 3) * 2.5
    // 动画延迟
    const delay = `${idx * -0.6}s`
    // 大小
    const size = 3 + (idx % 3) * 2
    // 初始旋转角度
    const angle = idx * 18
    return {
      width: `${size}px`,
      height: `${size}px`,
      transform: `rotate(${angle}deg) translateX(${radius}px)`,
      animationDuration: `${duration}s`,
      animationDelay: delay,
    }
  })
})
</script>

<script>
export default {
  name: 'NewSchemeSingularity'
}
</script>

<style lang="scss" scoped>
.new-scheme-singularity {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;
  transition: background-color 0.5s ease;

  // ===== 主题色系定义 =====
  &.light {
    --bg-color: #f7f5f4;
    --grid-stroke: rgba(220, 95, 30, 0.07);
    --dust-color: rgba(220, 95, 30, 0.65);
    --singularity-grad: radial-gradient(circle, rgba(251, 146, 60, 0.4) 0%, rgba(244, 63, 94, 0.15) 40%, rgba(255, 255, 255, 0) 70%);
    --ring-border: 1px solid rgba(220, 95, 30, 0.2);
    --ring-glow: rgba(251, 146, 60, 0.1);
  }

  &.dark {
    --bg-color: #080302;
    --grid-stroke: rgba(249, 115, 22, 0.14);
    --dust-color: rgba(249, 115, 22, 0.7);
    --singularity-grad: radial-gradient(circle, rgba(249, 115, 22, 0.5) 0%, rgba(225, 29, 72, 0.2) 50%, rgba(0, 0, 0, 0) 70%);
    --ring-border: 1px solid rgba(249, 115, 22, 0.35);
    --ring-glow: rgba(249, 115, 22, 0.25);
  }

  background-color: var(--bg-color);
}

// ===== 引力扭曲三维网格 =====
.gravity-grid {
  position: absolute;
  inset: -10%;
  width: 120%;
  height: 120%;
  pointer-events: none;
  transform: perspective(600px) rotateX(15deg);
  animation: grid-fluctuate 15s ease-in-out infinite alternate;
}

@keyframes grid-fluctuate {
  0% { transform: perspective(600px) rotateX(12deg) rotateY(-2deg) scale(0.98); }
  100% { transform: perspective(600px) rotateX(18deg) rotateY(2deg) scale(1.02); }
}

.grid-svg {
  width: 100%;
  height: 100%;
}

.grid-line {
  fill: none;
  stroke: var(--grid-stroke);
  stroke-width: 1.2;
}

// 动态引力波涟漪样式
.gravity-ripple {
  position: absolute;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  border: 1px solid var(--dust-color);
  opacity: 0;
  z-index: 1;
  pointer-events: none;
  
  &.ripple-1 {
    animation: ripple-wave 8s cubic-bezier(0.1, 0.8, 0.1, 1) infinite;
  }
  
  &.ripple-2 {
    animation: ripple-wave 8s cubic-bezier(0.1, 0.8, 0.1, 1) infinite;
    animation-delay: 4s;
  }
}

@keyframes ripple-wave {
  0% { transform: scale(0.4); opacity: 0.6; }
  50% { opacity: 0.3; }
  100% { transform: scale(3.5); opacity: 0; }
}

// ===== 引力中心 (通常与卡片位置重叠，以增强卡片的能量场视觉) =====
.singularity-core-wrap {
  position: absolute;
  right: 18%;
  top: 50%;
  transform: translateY(-50%) translate(calc(var(--mouse-x, 0) * 16px), calc(var(--mouse-y, 0) * 16px)) rotateX(calc(var(--mouse-y, 0) * -8deg)) rotateY(calc(var(--mouse-x, 0) * 8deg));
  width: 500px;
  height: 500px;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
  transform-style: preserve-3d;
  transition: transform 0.25s cubic-bezier(0.2, 0.8, 0.2, 1);
  
  @media (max-width: 960px) {
    right: 50%;
    transform: translate(50%, -50%) translate(calc(var(--mouse-x, 0) * 10px), calc(var(--mouse-y, 0) * 10px));
  }
}

.singularity-hud {
  position: absolute;
  bottom: -32px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--dust-color);
  letter-spacing: 0.2em;
  opacity: 0.7;
  text-shadow: 0 0 8px var(--dust-color);
  animation: hud-glitch-pulse 4s infinite alternate;
}

@keyframes hud-glitch-pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 0.9; }
}

.singularity-core {
  position: absolute;
  width: 250px;
  height: 250px;
  background: var(--singularity-grad);
  border-radius: 50%;
  filter: blur(15px);
  z-index: 1;
  animation: core-fluctuate 5s ease-in-out infinite alternate;
}

@keyframes core-fluctuate {
  0% { transform: scale(0.85); opacity: 0.8; }
  100% { transform: scale(1.15); opacity: 1; }
}

.gravity-ring {
  position: absolute;
  border-radius: 50%;
  border: var(--ring-border);
  box-shadow: 0 0 15px var(--ring-glow);
  transform: rotateX(60deg) rotateY(15deg);
  z-index: 2;
  
  &.ring-first {
    width: 320px;
    height: 320px;
    animation: spin-ring-cw 12s linear infinite;
  }
  &.ring-second {
    width: 400px;
    height: 400px;
    animation: spin-ring-ccw 16s linear infinite;
  }
  &.ring-third {
    width: 480px;
    height: 480px;
    border-style: dashed;
    animation: spin-ring-cw 22s linear infinite;
  }
}

@keyframes spin-ring-cw {
  0% { transform: rotateX(60deg) rotateY(15deg) rotateZ(0deg); }
  100% { transform: rotateX(60deg) rotateY(15deg) rotateZ(360deg); }
}

@keyframes spin-ring-ccw {
  0% { transform: rotateX(60deg) rotateY(15deg) rotateZ(360deg); }
  100% { transform: rotateX(60deg) rotateY(15deg) rotateZ(0deg); }
}

// ===== 吸积盘粒子风暴 =====
.accretion-disk {
  position: absolute;
  right: 18%;
  top: 50%;
  transform: translateY(-50%);
  width: 1px;
  height: 1px;
  pointer-events: none;
  
  @media (max-width: 960px) {
    right: 50%;
  }
}

.space-dust {
  position: absolute;
  border-radius: 50%;
  background-color: var(--dust-color);
  box-shadow: 0 0 8px var(--dust-color);
  animation: dust-spiral linear infinite;
  transform-origin: 0 0;
  opacity: 0;
}

@keyframes dust-spiral {
  0% {
    opacity: 0;
    transform: rotate(0deg) translateX(var(--start-radius, 250px)) scale(1.2);
  }
  15% {
    opacity: 0.8;
  }
  85% {
    opacity: 0.8;
  }
  100% {
    opacity: 0;
    // 绕中心旋转并向核心坍缩
    transform: rotate(360deg) translateX(0px) scale(0.1);
  }
}

// 通过 SCSS 遍历设置各粒子的坍缩动画变量
@for $i from 1 through 20 {
  .dust-#{$i} {
    // 根据 i 值动态控制起始坍缩半径
    --start-radius: #{140 + ($i % 4) * 45}px;
  }
}
</style>
