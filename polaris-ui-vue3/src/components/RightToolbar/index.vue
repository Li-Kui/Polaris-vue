<template>
  <div class="top-right-btn" :style="style">
    <el-tooltip class="item" effect="dark" :content="showSearch ? '隐藏搜索' : '显示搜索'" placement="top" v-if="search">
      <el-button circle icon="Search" @click="toggleSearch()" />
    </el-tooltip>
    <el-tooltip class="item" effect="dark" content="刷新" placement="top">
      <el-button circle icon="Refresh" @click="refresh()" />
    </el-tooltip>
    <!-- transfer 穿梭框模式 -->
    <el-tooltip class="item" effect="dark" content="显隐列" placement="top" v-if="showColumnsType == 'transfer' && hasColumns">
      <el-button circle icon="Menu" @click="showColumn()" />
    </el-tooltip>
    <!-- checkbox 复选框模式：使用 el-popover 替代 el-dropdown 解决定位问题 -->
    <el-popover
      v-if="showColumnsType == 'checkbox' && hasColumns"
      placement="bottom-end"
      trigger="click"
      :width="180"
      :show-arrow="true"
      popper-class="column-toggle-popover"
    >
      <template #reference>
        <el-button circle icon="Menu" style="margin-left: 12px" />
      </template>
      <div class="column-toggle-panel">
        <div class="column-toggle-header">
          <el-checkbox :indeterminate="isIndeterminate" v-model="isChecked" @change="toggleCheckAll">
            列展示
          </el-checkbox>
        </div>
        <div class="column-toggle-divider"></div>
        <div class="column-toggle-list">
          <template v-for="(item, key) in columns" :key="key">
            <div class="column-toggle-item">
              <el-checkbox v-model="item.visible" @change="checkboxChange($event, key)" :label="item.label" />
            </div>
          </template>
        </div>
      </div>
    </el-popover>
    <!-- transfer 穿梭框弹窗 -->
    <el-dialog :title="title" v-model="open" append-to-body>
      <el-transfer
        :titles="['显示', '隐藏']"
        v-model="value"
        :data="transferData"
        @change="dataChange"
      ></el-transfer>
    </el-dialog>
  </div>
</template>

<script setup>
import cache from '@/plugins/cache'

const props = defineProps({
  /* 是否显示检索条件 */
  showSearch: {
    type: Boolean,
    default: true
  },
  /* 显隐列信息（数组格式、对象格式） */
  columns: {
    type: [Array, Object],
    default: () => ({})
  },
  /* 是否显示检索图标 */
  search: {
    type: Boolean,
    default: true
  },
  /* 显隐列类型（transfer穿梭框、checkbox复选框） */
  showColumnsType: {
    type: String,
    default: "checkbox"
  },
  /* 右外边距 */
  gutter: {
    type: Number,
    default: 10
  },
  /* 列显隐状态记忆的 localStorage key（传入则启用记忆，不传则不记忆） */
  storageKey: {
    type: String,
    default: ""
  }
})

const emits = defineEmits(['update:showSearch', 'queryTable'])

// 显隐数据
const value = ref([])
// 弹出层标题
const title = ref("显示/隐藏")
// 是否显示弹出层
const open = ref(false)

// 是否有列数据
const hasColumns = computed(() => {
  if (Array.isArray(props.columns)) return props.columns.length > 0
  return Object.keys(props.columns).length > 0
})

const style = computed(() => {
  const ret = {}
  if (props.gutter) {
    ret.marginRight = `${props.gutter / 2}px`
  }
  return ret
})

// 是否全选/半选 状态
const isChecked = computed({
  get: () => Array.isArray(props.columns) ? props.columns.every(col => col.visible) : Object.values(props.columns).every((col) => col.visible),
  set: () => {}
})
const isIndeterminate = computed(() => Array.isArray(props.columns) ? props.columns.some((col) => col.visible) && !isChecked.value : Object.values(props.columns).some((col) => col.visible) && !isChecked.value)
const transferData = computed(() => Array.isArray(props.columns) ? props.columns.map((item, index) => ({ key: index, label: item.label })) : Object.keys(props.columns).map((key, index) => ({ key: index, label: props.columns[key].label })))

// 搜索
const { proxy } = getCurrentInstance()
function toggleSearch() {
  let el = proxy.$el
  let targetEl = null
  while ((el = el.parentElement) && el !== document.body) {
    targetEl = el.querySelector('.polaris-filter-card') || el.querySelector('.el-form')
    if (targetEl) break
  }
  if (!targetEl) return emits('update:showSearch', !props.showSearch)
  animateSearch(targetEl, props.showSearch)
}

function animateSearch(el, isHide) {
  const DURATION = 260
  const TRANSITION = 'max-height 0.25s ease, opacity 0.2s ease'
  const clear = () => Object.assign(el.style, { transition: '', maxHeight: '', opacity: '', overflow: '' })
  Object.assign(el.style, { overflow: 'hidden', transition: '' })
  if (isHide) {
    Object.assign(el.style, { maxHeight: el.scrollHeight + 'px', opacity: '1', transition: TRANSITION })
    requestAnimationFrame(() => Object.assign(el.style, { maxHeight: '0', opacity: '0' }))
    setTimeout(() => { emits('update:showSearch', false); clear() }, DURATION)
  } else {
    emits('update:showSearch', true)
    nextTick(() => {
      Object.assign(el.style, { maxHeight: '0', opacity: '0' })
      requestAnimationFrame(() => requestAnimationFrame(() => {
        Object.assign(el.style, { transition: TRANSITION, maxHeight: el.scrollHeight + 'px', opacity: '1' })
      }))
      setTimeout(clear, DURATION)
    })
  }
}

// 刷新
function refresh() {
  emits("queryTable")
}

// 右侧列表元素变化
function dataChange(data) {
  if (Array.isArray(props.columns)) {
    for (let item in props.columns) {
      const key = props.columns[item].key
      props.columns[item].visible = !data.includes(key)
    }
  } else {
    Object.keys(props.columns).forEach((key, index) => {
      props.columns[key].visible = !data.includes(index)
    })
  }
  saveStorage()
}

// 打开显隐列dialog
function showColumn() {
  open.value = true
}

// 如果传入了 storageKey，从 localStorage 恢复列显隐状态
if (props.storageKey) {
  try {
    const saved = cache.local.getJSON(props.storageKey)
    if (saved && typeof saved === 'object') {
      if (Array.isArray(props.columns)) {
        props.columns.forEach((col, index) => {
          if (saved[index] !== undefined) col.visible = saved[index]
        })
      } else {
        Object.keys(props.columns).forEach(key => {
          if (saved[key] !== undefined) props.columns[key].visible = saved[key]
        })
      }
    }
  } catch (e) {}
}
if (props.showColumnsType == "transfer") {
  // transfer穿梭显隐列初始默认隐藏列
  if (Array.isArray(props.columns)) {
    for (let item in props.columns) {
      if (props.columns[item].visible === false) {
        value.value.push(parseInt(item))
      }
    }
  } else {
    Object.keys(props.columns).forEach((key, index) => {
      if (props.columns[key].visible === false) {
        value.value.push(index)
      }
    })
  }
}

// 单勾选
function checkboxChange(event, key) {
  if (Array.isArray(props.columns)) {
    props.columns.filter(item => item.key == key)[0].visible = event
  } else {
    props.columns[key].visible = event
  }
  saveStorage()
}

// 切换全选/反选
function toggleCheckAll() {
  const newValue = !isChecked.value
  if (Array.isArray(props.columns)) {
    props.columns.forEach((col) => (col.visible = newValue))
  } else {
    Object.values(props.columns).forEach((col) => (col.visible = newValue))
  }
  saveStorage()
}

// 将当前列显隐状态持久化到 localStorage
function saveStorage() {
  if (!props.storageKey) return
  try {
    let state = {}
    if (Array.isArray(props.columns)) {
      props.columns.forEach((col, index) => { state[index] = col.visible })
    } else {
      Object.keys(props.columns).forEach(key => { state[key] = props.columns[key].visible })
    }
    cache.local.setJSON(props.storageKey, state)
  } catch (e) {}
}
</script>

<style lang='scss' scoped>
:deep(.el-transfer__button) {
  border-radius: 50%;
  display: block;
  margin-left: 0px;
}
:deep(.el-transfer__button:first-child) {
  margin-bottom: 10px;
}

/* 显隐列、搜索、刷新按钮亮暗双模式适配 */
.top-right-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.top-right-btn :deep(.el-button) {
  background-color: rgba(255, 255, 255, 0.45) !important;
  border: 1px solid rgba(0, 0, 0, 0.08) !important;
  color: #475569 !important;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1) !important;
  box-shadow: 0 2px 8px -1px rgba(0, 0, 0, 0.02) !important;

  /* 悬浮/聚焦/激活状态 */
  &:hover,
  &:focus,
  &:active {
    background-color: #4f46e5 !important;
    border-color: #4f46e5 !important;
    color: #ffffff !important;
    box-shadow: 0 4px 12px -2px rgba(79, 70, 229, 0.3) !important;
    transform: translateY(-1px);
  }

  /* 强制继承颜色给图标，防止悬浮时图标变白隐藏 */
  .el-icon,
  svg {
    color: inherit !important;
    fill: currentColor !important;
  }
}

/* 暗色模式样式 - 独立于嵌套结构声明，避免 postcss 对 :global() 嵌套编译出错污染全局的 Bug */
</style>

<!-- 全局样式：el-popover 弹出面板（亮暗双模式适配） -->
<style lang="scss">
/* 暗色模式样式 - 写入全局样式块以避开 vue-loader 对 :global() 嵌套编译的 Bug */
.dark .top-right-btn .el-button,
.theme-dark .top-right-btn .el-button {
  background-color: rgba(255, 255, 255, 0.05) !important;
  border-color: rgba(255, 255, 255, 0.08) !important;
  color: #cbd5e1 !important;
}

.dark .top-right-btn .el-button:hover,
.dark .top-right-btn .el-button:focus,
.dark .top-right-btn .el-button:active,
.theme-dark .top-right-btn .el-button:hover,
.theme-dark .top-right-btn .el-button:focus,
.theme-dark .top-right-btn .el-button:active {
  background-color: #38bdf8 !important;
  border-color: #38bdf8 !important;
  color: #0f172a !important;
  box-shadow: 0 4px 12px -2px rgba(56, 189, 248, 0.4) !important;
}

.column-toggle-popover {
  /* 亮色模式 */
  --ct-bg: rgba(255, 255, 255, 0.92) !important;
  --ct-border: rgba(0, 0, 0, 0.08) !important;
  --ct-divider: rgba(0, 0, 0, 0.06) !important;
  --ct-text: #334155 !important;
  --ct-text-checked: #4f46e5 !important;
  --ct-hover: rgba(79, 70, 229, 0.06) !important;
  --ct-checkbox-border: rgba(0, 0, 0, 0.15) !important;
  --ct-checkbox-bg: #ffffff !important;
  --ct-checkbox-active-bg: #4f46e5 !important;
  --ct-checkbox-active-border: #4f46e5 !important;
  --ct-shadow: 0 8px 30px -4px rgba(0, 0, 0, 0.12) !important;

  background: var(--ct-bg) !important;
  backdrop-filter: blur(20px) !important;
  -webkit-backdrop-filter: blur(20px) !important;
  border: 1px solid var(--ct-border) !important;
  border-radius: 14px !important;
  box-shadow: var(--ct-shadow) !important;
  padding: 6px 0 !important;

  /* 暗色模式 */
  .dark &,
  .theme-dark & {
    --ct-bg: rgba(15, 23, 42, 0.92) !important;
    --ct-border: rgba(255, 255, 255, 0.08) !important;
    --ct-divider: rgba(255, 255, 255, 0.08) !important;
    --ct-text: #cbd5e1 !important;
    --ct-text-checked: #38bdf8 !important;
    --ct-hover: rgba(56, 189, 248, 0.08) !important;
    --ct-checkbox-border: rgba(255, 255, 255, 0.15) !important;
    --ct-checkbox-bg: rgba(0, 0, 0, 0.3) !important;
    --ct-checkbox-active-bg: #38bdf8 !important;
    --ct-checkbox-active-border: #38bdf8 !important;
    --ct-shadow: 0 8px 30px -4px rgba(0, 0, 0, 0.4) !important;
  }

  /* popover 箭头 */
  .el-popper__arrow::before {
    background: var(--ct-bg) !important;
    border-color: var(--ct-border) !important;
  }
}

/* 面板内部结构 */
.column-toggle-panel {
  .column-toggle-header {
    padding: 6px 16px;
  }

  .column-toggle-divider {
    height: 1px;
    margin: 4px 12px;
    background: var(--ct-divider, rgba(0, 0, 0, 0.06));
  }

  .column-toggle-list {
    max-height: 280px;
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 4px;
    }
    &::-webkit-scrollbar-thumb {
      border-radius: 99px;
      background: rgba(0, 0, 0, 0.1);
    }
  }

  .column-toggle-item {
    padding: 4px 16px;
    transition: background-color 0.15s;
    cursor: pointer;

    &:hover {
      background-color: var(--ct-hover, rgba(79, 70, 229, 0.06));
    }
  }

  /* 复选框主题适配 */
  .el-checkbox {
    display: flex;
    align-items: center;
    width: 100%;
    color: var(--ct-text, #334155) !important;
    height: 26px;

    .el-checkbox__label {
      font-size: 12px !important;
      font-weight: 600 !important;
      color: inherit !important;
      padding-left: 8px;
    }

    &.is-checked {
      color: var(--ct-text-checked, #4f46e5) !important;
    }
  }

  .el-checkbox__inner {
    border-color: var(--ct-checkbox-border, rgba(0, 0, 0, 0.15)) !important;
    background-color: var(--ct-checkbox-bg, #ffffff) !important;
    border-radius: 4px !important;
    width: 15px;
    height: 15px;
    transition: all 0.2s;
  }

  .el-checkbox__input.is-checked .el-checkbox__inner,
  .el-checkbox__input.is-indeterminate .el-checkbox__inner {
    background-color: var(--ct-checkbox-active-bg, #4f46e5) !important;
    border-color: var(--ct-checkbox-active-border, #4f46e5) !important;
  }
}
</style>
