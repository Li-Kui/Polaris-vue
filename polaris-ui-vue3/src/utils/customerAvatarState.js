export const customerAvatarSlots = [
  { key: 'coverAssetId', label: '静态封面', cover: true },
  { key: 'idleAssetId', label: '待机素材' }, { key: 'listeningAssetId', label: '倾听素材' },
  { key: 'thinkingAssetId', label: '思考素材' }, { key: 'speakingAssetId', label: '讲话素材' }
]
export function customerAvatarConfiguration(value = {}) {
  return { name: value.name || '', coverAssetId: value.coverAssetId || null, idleAssetId: value.idleAssetId || null,
    listeningAssetId: value.listeningAssetId || null, thinkingAssetId: value.thinkingAssetId || null,
    speakingAssetId: value.speakingAssetId || null, fit: value.fit || 'CONTAIN' }
}
// 以实际录音／音频播放事件为准，不能在等待合成时就播放讲话动画。
export function customerAvatarState({ enabled, visible, closing, speaking, speechState, thinking, voiceState }) {
  if (!enabled || !visible || closing) return 'STOPPED'
  if (speaking) return 'SPEAKING'
  if (speechState === 'RECORDING') return 'LISTENING'
  if (thinking || ['RECOGNIZING', 'SYNTHESIZING'].includes(speechState) || voiceState === 'THINKING') return 'THINKING'
  if (['PAUSED', 'STOPPING'].includes(voiceState) || ['REQUESTING', 'UNKNOWN'].includes(speechState)) return 'PAUSED'
  return 'IDLE'
}
export function customerAvatarAsset(config, state, reducedMotion = false) {
  if (!config) return null
  if (reducedMotion || ['STOPPED', 'PAUSED'].includes(state)) return config.coverAssetId
  const key = { IDLE: 'idleAssetId', LISTENING: 'listeningAssetId', THINKING: 'thinkingAssetId', SPEAKING: 'speakingAssetId' }[state]
  return config[key] || config.idleAssetId || config.coverAssetId
}
export const customerAvatarStatus = state => ({ IDLE: '准备接待', LISTENING: '正在倾听', THINKING: '正在处理', SPEAKING: '正在讲话', PAUSED: '已暂停', STOPPED: '已停止' })[state] || '准备接待'

/** 最多保留封面和当前素材；切换、隐藏、结束时取消迟到加载并释放Blob，不缓存整个素材集。 */
export function createCustomerAvatarMedia({ load, createUrl, revokeUrl, onMedia }) {
  let generation = 0, controller, cover, current, disposed = false
  function revoke(item) { if (item?.url) revokeUrl(item.url) }
  function clear() {
    generation++; controller?.abort(); controller = null
    if (current !== cover) revoke(current)
    revoke(cover); cover = null; current = null; onMedia({})
  }
  async function read(id, signal) {
    const blob = await load(id, signal)
    if (!blob || !['image/png', 'image/jpeg', 'image/gif', 'video/mp4'].includes(blob.type) || blob.size < 1 || blob.size > 8 * 1024 * 1024) throw new Error('人物素材不可用')
    return blob
  }
  async function show(config, state, active, reducedMotion = false) {
    if (disposed) return
    if (!active || !config) { clear(); return }
    const ticket = ++generation
    controller?.abort(); controller = new AbortController()
    const signal = controller.signal
    try {
      if (!cover || cover.id !== config.coverAssetId) {
        if (current !== cover) revoke(current)
        revoke(cover); cover = null; current = null; onMedia({ loading: true })
        const blob = await read(config.coverAssetId, signal)
        if (disposed || ticket !== generation) return
        if (!['image/png', 'image/jpeg'].includes(blob.type)) throw new Error('封面必须是静态图片')
        cover = { id: config.coverAssetId, url: createUrl(blob), type: blob.type }
      }
      const id = customerAvatarAsset(config, state, reducedMotion)
      if (current?.id === id) { onMedia({ ...current, coverUrl: cover.url }); return }
      if (current !== cover) revoke(current)
      current = cover; onMedia({ ...cover, coverUrl: cover.url, loading: id !== cover.id })
      if (id === cover.id) return
      const blob = await read(id, signal)
      if (disposed || ticket !== generation) return
      current = { id, url: createUrl(blob), type: blob.type }
      onMedia({ ...current, coverUrl: cover.url })
    } catch (error) {
      if (!disposed && ticket === generation) {
        if (current !== cover) revoke(current)
        current = cover; onMedia({ ...(cover || {}), coverUrl: cover?.url, error: true })
      }
    }
  }
  return { show, clear, dispose() { disposed = true; clear() } }
}
