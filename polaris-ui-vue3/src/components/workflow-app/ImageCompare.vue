<template>
  <div class="image-compare" ref="containerRef" @mousemove="onMouseMove" @mouseup="onMouseUp" @mouseleave="onMouseUp" @touchmove="onTouchMove" @touchend="onMouseUp">
    <!-- 原图 -->
    <div class="img-wrapper source-wrapper">
      <img :src="assetUrl(sourceUrl)" class="compare-img" :alt="sourceLabel" draggable="false" />
      <div v-if="sourceLabel" class="label source-label">{{ sourceLabel }}</div>
    </div>
    <!-- 结果图 (覆盖在上面，使用 clip-path) -->
    <div class="img-wrapper result-wrapper" :style="{ clipPath: `inset(0 0 0 ${sliderPosition}%)` }">
      <img :src="assetUrl(resultUrl)" class="compare-img" :alt="resultLabel" draggable="false" />
      <div v-if="resultLabel" class="label result-label">{{ resultLabel }}</div>
    </div>
    <!-- 滑块 -->
    <div class="slider" role="slider" tabindex="0" aria-label="前后对比位置" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="Math.round(sliderPosition)"
      :style="{ left: `${sliderPosition}%` }" @keydown="onKeyDown" @mousedown.prevent="onMouseDown" @touchstart.prevent="onMouseDown">
      <div class="slider-line"></div>
      <div class="slider-handle">
        <el-icon><Switch /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref} from 'vue'
import {Switch} from '@element-plus/icons-vue'
import {resolveWorkflowAssetUrl} from '@/utils/workflowAssetUrl'

const props = defineProps({
  sourceUrl: String,
  resultUrl: String,
  sourceLabel: { type: String, default: '处理前' },
  resultLabel: { type: String, default: '处理后' }
})

const containerRef = ref(null)
const assetUrl = value => resolveWorkflowAssetUrl(value, import.meta.env.VITE_APP_BASE_API)
const sliderPosition = ref(50)
const isDragging = ref(false)
const onKeyDown = event => {
  const keys = { ArrowLeft: -5, ArrowDown: -5, ArrowRight: 5, ArrowUp: 5 }
  if (event.key in keys) { event.preventDefault(); sliderPosition.value = Math.max(0, Math.min(100, sliderPosition.value + keys[event.key])) }
  if (event.key === 'Home' || event.key === 'End') { event.preventDefault(); sliderPosition.value = event.key === 'Home' ? 0 : 100 }
}

const onMouseDown = () => {
  isDragging.value = true
}

const onMouseUp = () => {
  isDragging.value = false
}

const updatePosition = (clientX) => {
  if (!isDragging.value || !containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  let x = clientX - rect.left
  if (x < 0) x = 0
  if (x > rect.width) x = rect.width
  sliderPosition.value = (x / rect.width) * 100
}

const onMouseMove = (e) => {
  updatePosition(e.clientX)
}

const onTouchMove = (e) => {
  if (e.touches.length > 0) {
    updatePosition(e.touches[0].clientX)
  }
}
</script>

<style scoped>
.image-compare {
  position: relative;
  width: 100%;
  height: auto;
  min-height: 300px;
  overflow: hidden;
  user-select: none;
  background-color: var(--runtime-muted, #f0f2f5);
  border: 1px solid var(--runtime-border, #dbe2ea);
  border-radius: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.img-wrapper {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
.compare-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.label {
  position: absolute;
  top: 10px;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.5);
  color: white;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
}
.source-label { left: 10px; }
.result-label { right: 10px; }

.slider {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 4px;
  margin-left: -2px;
  cursor: ew-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}
.slider-line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 2px;
  margin-left: -1px;
  background-color: white;
  box-shadow: 0 0 3px rgba(0,0,0,0.5);
}
.slider-handle {
  position: relative;
  width: 32px;
  height: 32px;
  background-color: white;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0,0,0,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
}
.slider:focus-visible .slider-handle { outline: 3px solid var(--workflow-primary, #4f46e5); outline-offset: 3px; }
.slider { touch-action: none; }
@media (max-width: 600px) { .image-compare { min-height: 240px; } }
</style>
