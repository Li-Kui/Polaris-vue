<template>
  <section aria-label="数字人形象配置">
    <el-divider content-position="left">数字人形象</el-divider>
    <el-form-item label="人物展示"><el-switch v-model="enabled" active-text="启用数字人形象" :disabled="uploading" /></el-form-item>
    <template v-if="enabled">
      <el-form-item label="人物名称"><el-input v-model="draft.name" maxlength="64" placeholder="例如：小美" /></el-form-item>
      <el-form-item label="画面适配"><el-select v-model="draft.fit" popper-class="customer-service-menu"><el-option label="完整显示人物" value="CONTAIN" /><el-option label="铺满画面（可能裁切）" value="COVER" /></el-select></el-form-item>
      <el-form-item v-for="slot in customerAvatarSlots" :key="slot.key" :label="`${slot.label}${slot.cover ? '（必选）' : '（可选）'}`">
        <el-select v-model="draft[slot.key]" clearable filterable class="full-width" :placeholder="slot.cover ? '选择PNG或JPEG静态封面' : '未配置时沿用待机素材或封面'" popper-class="customer-service-menu">
          <el-option v-for="asset in assets.filter(item => !slot.cover || item.kind === 'IMAGE')" :key="asset.id" :label="`${asset.name} · ${asset.kind}`" :value="asset.id" />
        </el-select>
      </el-form-item>
      <div class="toolbar"><el-button :loading="uploading" @click="fileInput.click()">上传人物素材</el-button><el-button :disabled="uploading" @click="loadAssets">刷新素材</el-button></div>
      <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/gif,video/mp4" hidden @change="upload" />
      <p class="muted">支持PNG/JPG、GIF、MP4，单文件最多8MiB；图片最长边4096、最多800万像素。封面需静态PNG/JPG。视频静音循环，不包含口型同步；浏览器无法解码时显示封面。素材上传后可在本租户复用，最多100份、合计200MiB。</p>
      <template v-if="draft.coverAssetId">
        <el-form-item label="预览状态">
          <el-radio-group v-model="previewState" aria-label="人物预览状态" class="preview-states">
            <el-radio value="IDLE">待机</el-radio>
            <el-radio value="LISTENING">倾听</el-radio>
            <el-radio value="THINKING">思考</el-radio>
            <el-radio value="SPEAKING">讲话</el-radio>
            <el-radio value="PAUSED">暂停</el-radio>
          </el-radio-group>
        </el-form-item>
        <p class="muted">仅模拟人物状态，不会开启麦克风、发起问答或播放语音。暂停显示静态封面；其他状态未配置时沿用待机素材或封面。</p>
        <CustomerAvatar :config="draft" :state="previewState" :active="active" />
      </template>
      <p class="muted">保存并发布后对新会话生效。待机素材可使用图片、GIF或视频，其余状态未配置时沿用待机；暂停、后台和减少动态效果时使用静态封面。</p>
    </template>
  </section>
</template>
<script setup>
import {onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import CustomerAvatar from './CustomerAvatar.vue'
import {customerAvatarConfiguration, customerAvatarSlots} from '@/utils/customerAvatarState'
import {listCustomerAvatarAssets, uploadCustomerAvatarAsset} from '@/api/platform/customerService'

const props = defineProps({ modelValue: Object, active: { type: Boolean, default: true } })
const emit = defineEmits(['update:modelValue', 'busy'])
const enabled = ref(!!props.modelValue), draft = reactive(customerAvatarConfiguration(props.modelValue || {}))
const assets = ref([]), uploading = ref(false), fileInput = ref()
// 预览状态仅属于编辑器，不进入保存、发布配置。
const previewState = ref('IDLE')
let disposed = false
watch([enabled, draft], () => emit('update:modelValue', enabled.value ? { ...draft } : null), { deep: true })
async function loadAssets() {
  try { const result = await listCustomerAvatarAssets(); if (!disposed) assets.value = result.data || [] }
  catch { if (!disposed) ElMessage.warning('人物素材未加载，请检查登录状态与数据库迁移。') }
}
async function upload(event) {
  const file = event.target.files?.[0]; event.target.value = ''
  if (!file || uploading.value) return
  if (!file.size || file.size > 8 * 1024 * 1024) { ElMessage.warning('请选择8MiB以内的素材'); return }
  uploading.value = true; emit('busy', true)
  try {
    const result = await uploadCustomerAvatarAsset(file)
    if (!disposed) {
      assets.value = [result.data, ...assets.value]
      if (!draft.coverAssetId && result.data.kind === 'IMAGE') draft.coverAssetId = result.data.id
      else if (!draft.idleAssetId) draft.idleAssetId = result.data.id
      ElMessage.success('素材已上传，可以选择用于人物状态')
    }
  } catch {
    if (!disposed) ElMessage.warning('上传结果尚未确认，请先刷新素材列表核对，再决定是否重新上传。')
  } finally { uploading.value = false; if (!disposed) emit('busy', false) }
}
onMounted(loadAssets)
// 对话框关闭会卸载预览并释放素材；上传结果不写回已销毁的编辑器。
onBeforeUnmount(() => { disposed = true; emit('busy', false) })
</script>
<style scoped>
.full-width { width: 100%; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; }
.preview-states { flex-wrap: wrap; gap: 6px 0; }
.muted { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
</style>
