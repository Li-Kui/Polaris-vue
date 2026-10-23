<template>
  <section v-if="config" class="customer-avatar" aria-label="数字人形象">
    <div class="avatar-stage" :style="{ '--avatar-fit': config.fit === 'COVER' ? 'cover' : 'contain' }">
      <video v-if="media.type === 'video/mp4' && !videoFailed" :key="media.url" ref="video" :src="media.url" :poster="media.coverUrl"
        muted loop playsinline preload="metadata" :aria-label="`${config.name}人物视频`" @loadeddata="startVideo" @error="failVideo" />
      <img v-else-if="imageUrl" :key="imageUrl" :src="imageUrl" :alt="config.name" @error="failImage" />
      <div v-else class="avatar-placeholder" aria-hidden="true">{{ config.name.slice(0, 1) }}</div>
    </div>
    <div class="avatar-description"><strong>{{ config.name }}</strong><span>AI 数字人</span><span role="status">{{ customerAvatarStatus(state) }}</span></div>
    <p v-if="media.error || imageFailed || videoFailed" class="muted" role="status">人物素材暂不可用，已尝试显示封面；可以继续咨询。</p>
    <el-button v-if="playBlocked && active" size="small" @click="startVideo">播放人物视频</el-button>
  </section>
</template>
<script setup>
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {getCustomerAvatarAsset, getCustomerSessionAvatarAsset} from '@/api/platform/customerService'
import {createCustomerAvatarMedia, customerAvatarStatus} from '@/utils/customerAvatarState'

const props = defineProps({ config: Object, sessionId: String, loadAsset: Function, state: { type: String, default: 'IDLE' }, active: Boolean })
const media = ref({}), video = ref(), playBlocked = ref(false), videoFailed = ref(false), imageFailed = ref(false), imageFallback = ref(false)
const visible = ref(!document.hidden)
const motion = window.matchMedia('(prefers-reduced-motion: reduce)'), reducedMotion = ref(motion.matches)
let disposed = false, player
const imageUrl = computed(() => imageFailed.value ? null : imageFallback.value || videoFailed.value || playBlocked.value ? media.value.coverUrl : media.value.type?.startsWith('image/') ? media.value.url : media.value.coverUrl)
function stopVideo() { const element = video.value; if (element) { element.pause(); element.removeAttribute('src'); element.load() } }
function setupPlayer() {
  player?.dispose()
  player = createCustomerAvatarMedia({
    load: (id, signal) => props.loadAsset ? props.loadAsset(id, signal) : props.sessionId ? getCustomerSessionAvatarAsset(props.sessionId, id, signal) : getCustomerAvatarAsset(id, signal),
    createUrl: blob => URL.createObjectURL(blob), revokeUrl: url => URL.revokeObjectURL(url),
    onMedia: value => {
      if (media.value.url !== value.url) { stopVideo(); playBlocked.value = false; videoFailed.value = false; imageFailed.value = false; imageFallback.value = false }
      media.value = value
    }
  })
}
async function startVideo() {
  const element = video.value, url = media.value.url
  if (!element || !props.active || !visible.value || reducedMotion.value || disposed) return
  element.muted = true
  try { await element.play(); if (disposed || !props.active || !visible.value || video.value !== element || media.value.url !== url) element.pause(); else playBlocked.value = false }
  catch { if (!disposed && video.value === element && media.value.url === url) playBlocked.value = true }
}
function failVideo() { video.value?.pause(); videoFailed.value = true; playBlocked.value = false }
function failImage(event) {
  if (event.currentTarget.src !== imageUrl.value) return
  if (!imageFallback.value && media.value.coverUrl && imageUrl.value !== media.value.coverUrl) imageFallback.value = true
  else imageFailed.value = true
}
function motionChanged(event) { reducedMotion.value = event.matches }
function visibilityChanged() { visible.value = !document.hidden }
watch(() => props.sessionId, () => { setupPlayer() }, { immediate: true, flush: 'sync' })
watch([() => JSON.stringify(props.config), () => props.sessionId, () => props.state, () => props.active, reducedMotion, visible], async () => {
  video.value?.pause()
  await player.show(props.config, props.state, props.active && visible.value, reducedMotion.value)
  await nextTick(); if (media.value.type === 'video/mp4') startVideo()
}, { immediate: true })
motion.addEventListener('change', motionChanged)
document.addEventListener('visibilitychange', visibilityChanged)
onBeforeUnmount(() => { disposed = true; stopVideo(); player.dispose(); motion.removeEventListener('change', motionChanged); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>
<style scoped>
.customer-avatar { margin: 16px 0; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 12px; }
.avatar-stage { height: var(--cs-avatar-height, clamp(180px, 32vh, 320px)); overflow: hidden; border-radius: 4px; background: var(--el-fill-color-light); }
.avatar-stage img, .avatar-stage video { display: block; width: 100%; height: 100%; object-fit: var(--avatar-fit); }
.avatar-placeholder { height: 100%; display: grid; place-items: center; color: var(--el-color-primary); font-size: 48px; }
.avatar-description { display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; margin-top: 12px; }
.avatar-description span, .muted { color: var(--el-text-color-secondary); font-size: 13px; }
@media (max-width: 600px) { .avatar-stage { height: var(--cs-avatar-height, 220px); } }
</style>
