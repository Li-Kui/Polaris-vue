<!-- 方案一：AI 意识觉醒 (A.I. Consciousness Awakening) -->
<template>
  <div :class="['new-scheme-awakening', theme]" aria-hidden="true">
    <!-- 神经网络背景拓扑 -->
    <div class="neuron-network">
      <svg class="network-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000" preserveAspectRatio="xMidYMid slice">
        <!-- 连线 (底层静态虚线) -->
        <line x1="100" y1="150" x2="250" y2="300" class="net-line" />
        <line x1="250" y1="300" x2="200" y2="550" class="net-line" />
        <line x1="250" y1="300" x2="450" y2="350" class="net-line" />
        <line x1="450" y1="350" x2="500" y2="150" class="net-line" />
        <line x1="450" y1="350" x2="600" y2="500" class="net-line" />
        <line x1="600" y1="500" x2="800" y2="350" class="net-line" />
        <line x1="600" y1="500" x2="700" y2="750" class="net-line" />
        <line x1="200" y1="550" x2="400" y2="700" class="net-line" />
        <line x1="400" y1="700" x2="600" y2="500" class="net-line" />
        <line x1="700" y1="750" x2="900" y2="600" class="net-line" />

        <!-- 神经脉冲流动层 (顶层高亮流动线) -->
        <line x1="100" y1="150" x2="250" y2="300" class="net-flow-line flow-delay-1" />
        <line x1="250" y1="300" x2="200" y2="550" class="net-flow-line flow-delay-2" />
        <line x1="250" y1="300" x2="450" y2="350" class="net-flow-line flow-delay-3" />
        <line x1="450" y1="350" x2="500" y2="150" class="net-flow-line flow-delay-4" />
        <line x1="450" y1="350" x2="600" y2="500" class="net-flow-line flow-delay-1" />
        <line x1="600" y1="500" x2="800" y2="350" class="net-flow-line flow-delay-2" />
        <line x1="600" y1="500" x2="700" y2="750" class="net-flow-line flow-delay-3" />
        <line x1="200" y1="550" x2="400" y2="700" class="net-flow-line flow-delay-4" />
        <line x1="400" y1="700" x2="600" y2="500" class="net-flow-line flow-delay-2" />
        <line x1="700" y1="750" x2="900" y2="600" class="net-flow-line flow-delay-1" />

        <!-- 节点 -->
        <circle cx="100" cy="150" r="4" class="net-node node-slow" />
        <circle cx="250" cy="300" r="6" class="net-node node-fast" />
        <circle cx="200" cy="550" r="5" class="net-node node-slow" />
        <circle cx="450" cy="350" r="7" class="net-node node-normal" />
        <circle cx="500" cy="150" r="4" class="net-node node-slow" />
        <circle cx="600" cy="500" r="8" class="net-node node-fast" />
        <circle cx="800" cy="350" r="5" class="net-node node-normal" />
        <circle cx="700" cy="750" r="6" class="net-node node-slow" />
        <circle cx="400" cy="700" r="5" class="net-node node-normal" />
        <circle cx="900" cy="600" r="4" class="net-node node-slow" />
      </svg>
    </div>

    <!-- 代码意识粒子雨 -->
    <div class="code-particles">
      <div v-for="i in 15" :key="i" :class="['particle', `p-${i}`]" :style="particleStyles[i-1]">
        {{ randomCodes[i % randomCodes.length] }}
      </div>
    </div>

    <!-- 3D 全息智慧球主体区 (偏左布局，留右侧给表单) -->
    <div class="hologram-container">
      <div class="hologram-viewport">
        <!-- 核心发光体 -->
        <div class="aura-core"></div>
        <div class="aura-glow"></div>
        
        <!-- 3D 旋转嵌套轨道 -->
        <div class="holo-ring ring-x"></div>
        <div class="holo-ring ring-y"></div>
        <div class="holo-ring ring-z"></div>
        
        <!-- 外部数据刻度弧 -->
        <svg class="hud-arc" viewBox="0 0 200 200">
          <circle cx="100" cy="100" r="85" class="hud-circle-dash" />
          <circle cx="100" cy="100" r="75" class="hud-circle-solid" />
        </svg>
      </div>
      <!-- HUD 数据标题嵌入项目名称 -->
      <div class="hologram-hud">{{ projectTitle.toUpperCase() }} // COGNITIVE CORE</div>
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

const randomCodes = ['01', '10', 'AI', 'f(x)', 'λ', 'ReLU', 'CNN', 'Data', 'θ', 'W', 'T']

// 动态生成每个粒子的延迟与速度，避免视觉同步
const particleStyles = computed(() => {
  return Array.from({ length: 15 }).map((_, idx) => {
    const left = `${5 + idx * 6.5}%`
    const duration = `${14 + (idx % 5) * 3}s`
    const delay = `${(idx % 4) * -4.5}s`
    const fontSize = `${10 + (idx % 3) * 3}px`
    const opacity = 0.08 + (idx % 3) * 0.06
    return {
      left,
      animationDuration: duration,
      animationDelay: delay,
      fontSize,
      opacity
    }
  })
})
</script>

<script>
// 设置组件名字，方便调试和动态组件渲染
export default {
  name: 'NewSchemeAwakening'
}
</script>

<style lang="scss" scoped>
.new-scheme-awakening {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;
  transition: background-color 0.5s ease;

  // ===== 主题色系定义 =====
  &.light {
    --bg-color: #f6f8fd;
    --net-color: rgba(99, 102, 241, 0.08);
    --line-color: rgba(99, 102, 241, 0.05);
    --flow-stroke: rgba(99, 102, 241, 0.45);
    --core-grad: radial-gradient(circle, rgba(99, 102, 241, 0.6) 0%, rgba(168, 85, 247, 0.3) 50%, rgba(255, 255, 255, 0) 70%);
    --glow-color: rgba(99, 102, 241, 0.15);
    --ring-border: 1px dashed rgba(99, 102, 241, 0.25);
    --hud-color: rgba(99, 102, 241, 0.3);
    --particle-color: rgba(99, 102, 241, 0.7);
  }

  &.dark {
    --bg-color: #05050a;
    --net-color: rgba(129, 140, 248, 0.15);
    --line-color: rgba(129, 140, 248, 0.06);
    --flow-stroke: rgba(34, 211, 238, 0.6);
    --core-grad: radial-gradient(circle, rgba(167, 139, 250, 0.6) 0%, rgba(34, 211, 238, 0.3) 50%, rgba(0, 0, 0, 0) 70%);
    --glow-color: rgba(167, 139, 250, 0.25);
    --ring-border: 1px dashed rgba(167, 139, 250, 0.35);
    --hud-color: rgba(34, 211, 238, 0.4);
    --particle-color: rgba(34, 211, 238, 0.7);
  }

  background-color: var(--bg-color);
}

// ===== 神经网络背景 =====
.neuron-network {
  position: absolute;
  inset: -20px;
  width: calc(100% + 40px);
  height: calc(100% + 40px);
  pointer-events: none;
  transform: translate(calc(var(--mouse-x, 0) * -18px), calc(var(--mouse-y, 0) * -18px));
  transition: transform 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
}
.network-svg {
  width: 100%;
  height: 100%;
  opacity: 0.85;
}
.net-line {
  stroke: var(--line-color);
  stroke-width: 1;
}

// 神经脉冲流动连线样式
.net-flow-line {
  stroke: var(--flow-stroke);
  stroke-width: 1.5;
  stroke-dasharray: 20 180;
  fill: none;
  animation: net-signal-flow 8s linear infinite;
  
  &.flow-delay-1 { animation-duration: 6s; animation-delay: -1s; }
  &.flow-delay-2 { animation-duration: 8s; animation-delay: -3s; }
  &.flow-delay-3 { animation-duration: 10s; animation-delay: -5s; }
  &.flow-delay-4 { animation-duration: 7s; animation-delay: -2s; }
}

@keyframes net-signal-flow {
  0% { stroke-dashoffset: 200; }
  100% { stroke-dashoffset: 0; }
}

.net-node {
  fill: var(--net-color);
  stroke: var(--bg-color);
  stroke-width: 2;
  
  &.node-slow { animation: node-pulse 5s ease-in-out infinite; }
  &.node-normal { animation: node-pulse 3.5s ease-in-out infinite; animation-delay: -1s; }
  &.node-fast { animation: node-pulse 2s ease-in-out infinite; animation-delay: -0.5s; }
}

@keyframes node-pulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.4); opacity: 1; }
}

// ===== 代码粒子飘流 =====
.code-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.particle {
  position: absolute;
  bottom: -40px;
  font-family: monospace;
  font-weight: bold;
  color: var(--particle-color);
  animation: float-up linear infinite;
  user-select: none;
}

@keyframes float-up {
  0% {
    transform: translateY(0) translateX(0) rotate(0deg);
    opacity: 0;
  }
  10% {
    opacity: 0.8;
  }
  25% {
    transform: translateY(-25vh) translateX(15px) rotate(45deg);
  }
  50% {
    transform: translateY(-50vh) translateX(-20px) rotate(90deg);
  }
  75% {
    transform: translateY(-75vh) translateX(15px) rotate(135deg);
  }
  90% {
    opacity: 0.8;
  }
  100% {
    transform: translateY(-105vh) translateX(0) rotate(180deg);
    opacity: 0;
  }
}

// ===== 3D全息核心容器 =====
.hologram-container {
  position: absolute;
  left: 15%;
  top: 50%;
  transform: translateY(-50%);
  width: 400px;
  height: 480px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  pointer-events: none;
  
  @media (max-width: 1024px) {
    display: none; // 窄屏隐藏 3D 核心，留出完整空间给卡片
  }
}

.hologram-hud {
  margin-top: 36px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--hud-color);
  letter-spacing: 0.18em;
  text-shadow: 0 0 10px var(--glow-color);
  animation: hud-glitch-pulse 3s infinite alternate;
}

@keyframes hud-glitch-pulse {
  0%, 100% { opacity: 0.55; transform: scale(0.98); }
  50% { opacity: 1; transform: scale(1.02); }
}

.hologram-viewport {
  position: relative;
  width: 320px;
  height: 320px;
  perspective: 1000px;
  transform-style: preserve-3d;
  transform: translate(calc(var(--mouse-x, 0) * 15px), calc(var(--mouse-y, 0) * 15px)) rotateX(calc(var(--mouse-y, 0) * -12deg)) rotateY(calc(var(--mouse-x, 0) * 12deg));
  transition: transform 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
}

// ===== 核心发光体 =====
.aura-core {
  position: absolute;
  inset: 20%;
  border-radius: 50%;
  background: var(--core-grad);
  animation: core-breath 4s ease-in-out infinite alternate;
  filter: blur(10px);
  z-index: 2;
}

.aura-glow {
  position: absolute;
  inset: 10%;
  border-radius: 50%;
  border: 1px solid var(--glow-color);
  background: transparent;
  box-shadow: 0 0 30px var(--glow-color);
  animation: core-breath 6s ease-in-out infinite alternate-reverse;
  z-index: 1;
}

@keyframes core-breath {
  0% { transform: scale(0.9); opacity: 0.7; }
  100% { transform: scale(1.1); opacity: 1; }
}

// ===== 3D嵌套旋转环 =====
.holo-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: var(--ring-border);
  box-sizing: border-box;
  transform-style: preserve-3d;
  
  &::before {
    content: '';
    position: absolute;
    top: 10px;
    left: 50%;
    width: 6px;
    height: 6px;
    background-color: var(--hud-color);
    border-radius: 50%;
    box-shadow: 0 0 10px var(--hud-color);
  }

  &.ring-x {
    animation: rotate-x 16s linear infinite;
  }
  &.ring-y {
    animation: rotate-y 20s linear infinite;
  }
  &.ring-z {
    animation: rotate-z 24s linear infinite;
  }
}

@keyframes rotate-x {
  0% { transform: rotateX(45deg) rotateY(0deg) rotateZ(0deg); }
  100% { transform: rotateX(45deg) rotateY(360deg) rotateZ(360deg); }
}

@keyframes rotate-y {
  0% { transform: rotateX(-45deg) rotateY(360deg) rotateZ(0deg); }
  100% { transform: rotateX(-45deg) rotateY(0deg) rotateZ(360deg); }
}

@keyframes rotate-z {
  0% { transform: rotateX(75deg) rotateY(0deg) rotateZ(360deg); }
  100% { transform: rotateX(75deg) rotateY(360deg) rotateZ(0deg); }
}

// ===== 外部 HUD 刻度环 =====
.hud-arc {
  position: absolute;
  inset: -20px;
  width: calc(100% + 40px);
  height: calc(100% + 40px);
  transform: rotate(-45deg);
  z-index: 5;
  pointer-events: none;
}

.hud-circle-dash {
  stroke: var(--hud-color);
  stroke-width: 1.5;
  fill: none;
  stroke-dasharray: 8 16;
  animation: hud-spin-cw 30s linear infinite;
  transform-origin: 100px 100px;
}

.hud-circle-solid {
  stroke: var(--hud-color);
  stroke-width: 0.8;
  fill: none;
  stroke-dasharray: 100 80;
  animation: hud-spin-ccw 20s linear infinite;
  transform-origin: 100px 100px;
}

@keyframes hud-spin-cw {
  to { transform: rotate(360deg); }
}

@keyframes hud-spin-ccw {
  to { transform: rotate(-360deg); }
}
</style>
