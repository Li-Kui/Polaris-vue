import {computed, onScopeDispose, ref, watch} from 'vue'

// Results belong to the current draft only; editing or leaving invalidates them.
export function useCapabilityTests({capabilities, fingerprint, makeRequest, request, clock = () => performance.now()}) {
  const selectedKey = ref('')
  const testingKey = ref('')
  const results = ref({})
  let active = true
  let revision = 0
  watch(() => capabilities.value.map(item => item.key), keys => {
    if (!keys.includes(selectedKey.value)) selectedKey.value = keys[0] || ''
  }, {immediate: true, flush: 'sync'})
  watch(fingerprint, () => { revision++; results.value = {} }, {flush: 'sync'})
  onScopeDispose(() => { active = false; revision++ })
  const selected = computed(() => capabilities.value.find(item => item.key === selectedKey.value))

  async function run() {
    const item = selected.value
    if (!active || !item || testingKey.value) return
    const startedRevision = revision
    const started = clock()
    testingKey.value = item.key
    const current = () => active && startedRevision === revision
    try {
      const response = await request(makeRequest(item))
      if (!current()) return
      if (typeof response?.success !== 'boolean') throw new Error('测试服务返回无效结果，未确认能力可用')
      results.value[item.key] = {
        success: response.success,
        message: response.success ? `${item.schema.name}能力测试通过` : (response.message || '未取得通过结果，请检查配置'),
        elapsedMs: Math.max(0, Math.round(clock() - started)),
        latencyMs: Number.isFinite(response.metrics?.latencyMs) ? response.metrics.latencyMs : null
      }
    } catch (error) {
      if (!current()) return
      results.value[item.key] = {
        success: false,
        message: error?.message || '未取得确定的测试结果，请检查连接与配置后手动测试',
        elapsedMs: Math.max(0, Math.round(clock() - started)),
        latencyMs: null
      }
    } finally {
      if (active) testingKey.value = ''
    }
  }
  return {selectedKey, selected, testingKey, results, run}
}
