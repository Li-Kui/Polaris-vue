<template>
  <div class="theme-switcher" @click.stop>
    <button class="switcher-trigger" @click="showPanel = !showPanel" type="button">
      <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24"
        fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="3"/>
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
      </svg>
      <span class="trigger-label">外观</span>
    </button>

    <transition name="panel-pop">
      <div v-if="showPanel" class="switcher-panel">
        <!-- 主题切换 -->
        <div class="section">
          <div class="section-title">主题</div>
          <div class="theme-row">
            <button
              v-for="t in themes" :key="t.value"
              :class="['theme-btn', { active: modelTheme === t.value }]"
              @click="$emit('update:modelTheme', t.value)"
              type="button"
            >
              <span class="dot" :class="t.dotClass"></span>
              {{ t.label }}
            </button>
          </div>
        </div>

        <!-- 方案切换 -->
        <div class="section">
          <div class="section-title">视觉方案</div>
          <div class="scheme-grid">
            <button
              v-for="s in schemes" :key="s.value"
              :class="['scheme-btn', { active: modelScheme === s.value }]"
              @click="$emit('update:modelScheme', s.value); showPanel = false"
              type="button"
            >
              <div class="scheme-thumb" :class="s.thumbClass"></div>
              <div class="scheme-meta">
                <span class="scheme-name">{{ s.label }}</span>
                <span class="scheme-desc">{{ s.desc }}</span>
              </div>
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import {ref} from "vue"

defineProps({
  modelTheme:  { type: String, default: "light" },
  modelScheme: { type: String, default: "flux" },
})

defineEmits(["update:modelTheme", "update:modelScheme"])

const showPanel = ref(false)

const themes = [
  { value: "light", label: "亮色", dotClass: "dot-light" },
  { value: "dark",  label: "暗色", dotClass: "dot-dark" },
]

const schemes = [
  { value: "flux",  label: "流光",   desc: "极光渐变，毛玻璃卡片", thumbClass: "thumb-flux" },
  { value: "pulse", label: "脉冲",   desc: "科技网格，HUD 状态面板", thumbClass: "thumb-pulse" },
  { value: "orbit", label: "星轨",   desc: "宇宙分栏，北极星数据流", thumbClass: "thumb-orbit" },
]
</script>

<style lang="scss" scoped>
.theme-switcher {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 30;
}

.switcher-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  background: var(--card, rgba(255, 255, 255, 0.8));
  backdrop-filter: blur(16px) saturate(1.3);
  border: 1px solid var(--line, rgba(0, 0, 0, 0.06));
  border-radius: 999px;
  color: var(--ink-2, #555);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 200ms;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  &:hover {
    color: var(--ink, #1a1a1a);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    transform: translateY(-1px);
  }
}

.trigger-label { white-space: nowrap; }

.switcher-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 280px;
  background: var(--card, rgba(255, 255, 255, 0.95));
  backdrop-filter: blur(24px) saturate(1.4);
  border: 1px solid var(--line, rgba(0, 0, 0, 0.06));
  border-radius: 14px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

.panel-pop-enter-active { transition: all 200ms var(--ease-out); }
.panel-pop-leave-active { transition: all 150ms ease-in; }
.panel-pop-enter-from { opacity: 0; transform: translateY(6px) scale(0.97); }
.panel-pop-leave-to   { opacity: 0; transform: translateY(4px) scale(0.98); }

.section {
  padding: 14px 16px;
  &:not(:last-child) { border-bottom: 1px solid var(--line, rgba(0, 0, 0, 0.05)); }
}

.section-title {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-3, #999);
  margin-bottom: 8px;
  font-family: var(--font-mono);
}

.theme-row {
  display: flex;
  gap: 8px;
}

.theme-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 0;
  border: 1px solid var(--line, rgba(0, 0, 0, 0.08));
  border-radius: 8px;
  background: transparent;
  color: var(--ink-2, #555);
  font-size: 12px;
  cursor: pointer;
  transition: all 120ms;

  &.active {
    border-color: var(--accent, #6366f1);
    color: var(--accent, #6366f1);
    background: rgba(99, 102, 241, 0.05);
  }
}

.dot {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
  &.dot-light { background: linear-gradient(135deg, #6366f1, #06b6d4); }
  &.dot-dark  { background: linear-gradient(135deg, #818cf8, #c084fc); }
}

.scheme-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.scheme-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--line, rgba(0, 0, 0, 0.06));
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  transition: all 120ms;
  text-align: left;

  &:hover {
    background: var(--card-2, rgba(0, 0, 0, 0.02));
    transform: translateX(2px);
  }

  &.active {
    border-color: var(--accent, #6366f1);
    background: rgba(99, 102, 241, 0.04);
  }
}

.scheme-thumb {
  width: 40px; height: 40px;
  border-radius: 8px;
  flex-shrink: 0;
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.thumb-flux {
  background: linear-gradient(135deg, #e8e0ff, #fce7f3, #d5f5f6);
}

.thumb-pulse {
  background: #0f172a;
  background-image:
    linear-gradient(to right, rgba(34, 211, 238, 0.15) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(34, 211, 238, 0.15) 1px, transparent 1px);
  background-size: 8px 8px;
}

.thumb-orbit {
  background: linear-gradient(to right, #0c1b3a 44%, #fafbfc 44%);
  position: relative;
  &::after {
    content: '✦';
    position: absolute;
    top: 30%; left: 18%;
    font-size: 8px;
    color: #fbbf24;
  }
}

.scheme-meta {
  display: flex;
  flex-direction: column;
}

.scheme-name {
  font-size: 13px;
  font-weight: var(--fw-semibold, 600);
  color: var(--ink, #1a1a1a);
}

.scheme-desc {
  font-size: 11px;
  color: var(--ink-3, #999);
  line-height: 1.3;
}
</style>
