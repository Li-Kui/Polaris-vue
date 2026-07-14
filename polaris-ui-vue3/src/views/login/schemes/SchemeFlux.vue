<!-- 流光 Flux — 高级玻璃拟态浮动渐变光球背景 -->
<template>
  <div class="flux-bg" aria-hidden="true">
    <!-- 渐变光球 -->
    <div class="orb orb-1" />
    <div class="orb orb-2" />
    <div class="orb orb-3" />
  </div>
</template>

<script setup>
defineProps({ theme: { type: String, default: 'light' } })
</script>

<style lang="scss" scoped>
/* 主容器 —— 覆盖整个登录页 */
.flux-bg {
  position: absolute;
  inset: 0;
  background-color: var(--bg);
  overflow: hidden;
  z-index: 0;

  /* 噪点纹理覆盖层 */
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    opacity: 0.03;
    background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.5'/%3E%3C/svg%3E");
    pointer-events: none;
  }
}

/* 通用光球样式 */
.orb {
  position: absolute;
  border-radius: 50%;
  opacity: var(--decor-opacity);
  pointer-events: none;
}

/* 三个光球各自的位置、尺寸和动画 */
.orb-1 {
  width: 500px; height: 500px;
  top: -80px; right: -60px;
  background: var(--orb-1);
  filter: blur(100px);
  animation: flux-drift-1 20s ease-in-out infinite alternate;
}
.orb-2 {
  width: 600px; height: 600px;
  bottom: -120px; left: -80px;
  background: var(--orb-2);
  filter: blur(120px);
  animation: flux-drift-2 26s ease-in-out infinite alternate-reverse;
}
.orb-3 {
  width: 420px; height: 420px;
  top: 40%; left: 30%;
  background: var(--orb-3);
  filter: blur(90px);
  animation: flux-drift-3 22s linear infinite;
}

/* 光球漂移动画 */
@keyframes flux-drift-1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(60px, -40px) scale(1.05); }
  66% { transform: translate(-30px, 20px) scale(0.95); }
}
@keyframes flux-drift-2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-50px, 30px) scale(1.08); }
  66% { transform: translate(40px, -60px) scale(0.92); }
}
@keyframes flux-drift-3 {
  0%, 100% { transform: translate(0, 0) rotate(0deg) scale(1); }
  50% { transform: translate(30px, -20px) rotate(180deg) scale(1.1); }
}
</style>
