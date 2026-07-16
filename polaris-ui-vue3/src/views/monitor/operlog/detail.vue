<template>
  <el-dialog title="操作日志详细" v-model="dialogVisible" width="780px" append-to-body @close="$emit('update:visible', false)" class="polaris-glass-dialog">
    <div class="detail-wrap">
      <!-- 基本信息 -->
      <div class="detail-card">
        <div class="detail-card-title"><el-icon><InfoFilled /></el-icon> 基本信息</div>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">操作模块</span><span class="detail-value">{{ form.title }}</span></div>
          </el-col>
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">业务类型</span><span class="detail-value">{{ typeLabel }}</span></div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">操作时间</span><span class="detail-value">{{ form.operTime }}</span></div>
          </el-col>
          <el-col :span="12">
            <div class="detail-item">
              <span class="detail-label">执行状态</span>
              <el-tag v-if="form.status === 0" type="success" size="small">正常</el-tag>
              <el-tag v-else type="danger" size="small">异常</el-tag>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 操作人员 -->
      <div class="detail-card">
        <div class="detail-card-title"><el-icon><User /></el-icon> 操作人员</div>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">操作人员</span><span class="detail-value">{{ form.operName }}</span></div>
          </el-col>
          <el-col :span="12" v-if="form.deptName">
            <div class="detail-item"><span class="detail-label">所属部门</span><span class="detail-value">{{ form.deptName }}</span></div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="24">
            <div class="detail-item">
              <span class="detail-label">操作地址</span>
              <span class="detail-value">{{ form.operIp }}&nbsp;&nbsp;<span class="detail-location">{{ form.operLocation }}</span></span>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 请求信息 -->
      <div class="detail-card">
        <div class="detail-card-title"><el-icon><Sort /></el-icon> 请求信息</div>
        <el-row class="detail-row">
          <el-col :span="24">
            <div class="detail-item">
              <span class="detail-label">请求地址</span>
              <span class="detail-value">
                <span :class="'method-tag method-' + form.requestMethod">{{ form.requestMethod }}</span>
                {{ form.operUrl }}
              </span>
            </div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="24">
            <div class="detail-item"><span class="detail-label">操作方法</span><span class="detail-value mono">{{ form.method }}</span></div>
          </el-col>
        </el-row>
        <el-row class="detail-row">
          <el-col :span="12">
            <div class="detail-item"><span class="detail-label">消耗时间</span><span class="detail-value">{{ form.costTime }} 毫秒</span></div>
          </el-col>
        </el-row>
      </div>

      <!-- 请求参数 -->
      <div class="detail-card">
        <div class="detail-card-title"><el-icon><Upload /></el-icon> 请求参数</div>
        <div class="code-body">
          <div class="code-wrap">
            <div class="code-action">
              <el-button size="small" :icon="CopyDocument" @click="copyText(form.operParam)">复制</el-button>
            </div>
            <pre class="code-pre">{{ formatJson(form.operParam) }}</pre>
          </div>
        </div>
      </div>

      <!-- 返回参数 -->
      <div class="detail-card">
        <div class="detail-card-title"><el-icon><Download /></el-icon> 返回参数</div>
        <div class="code-body">
          <div class="code-wrap">
            <div class="code-action">
              <el-button size="small" :icon="CopyDocument" @click="copyText(form.jsonResult)">复制</el-button>
            </div>
            <pre class="code-pre">{{ formatJson(form.jsonResult) }}</pre>
          </div>
        </div>
      </div>

      <!-- 异常信息 -->
      <div class="detail-card" v-if="form.status !== 0">
        <div class="detail-card-title error-title"><el-icon><Warning /></el-icon> 异常信息</div>
        <div class="error-body">
          <div class="error-msg">{{ form.errorMsg }}</div>
        </div>
      </div>

    </div>
  </el-dialog>
</template>

<script setup>
const props = defineProps({
  visible: { type: Boolean, default: false },
  row: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:visible'])

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

 
const { sys_oper_type } = useDict('sys_oper_type')

const form = computed(() => props.row || {})
const typeLabel = computed(() => selectDictLabel(sys_oper_type.value, form.value.businessType) || '-')

function formatJson(str) {
  if (!str) return '（无数据）'
  try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str }
}

function copyText(str) {
  const text = formatJson(str)
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => ElMessage({ message: '已复制', type: 'success', duration: 1500 }))
  } else {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage({ message: '已复制', type: 'success', duration: 1500 })
  }
}
</script>

<style lang="scss">
/* 操作日志详情弹窗局部重写（全局样式，但限定在 .polaris-glass-dialog 中以隔离保护） */
.polaris-glass-dialog {
  .detail-wrap {
    padding: 4px 8px;
  }

  .detail-card {
    background: rgba(255, 255, 255, 0.45) !important;
    border: 1px solid rgba(226, 232, 240, 0.5) !important;
    border-radius: 12px !important;
    margin-bottom: 18px !important;
    backdrop-filter: blur(4px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  }

  .detail-card-title {
    background: rgba(247, 249, 251, 0.5) !important;
    color: #1e293b !important;
    border-bottom: 1px solid rgba(226, 232, 240, 0.5) !important;
    font-size: 13.5px !important;
    padding: 10px 16px !important;
    display: flex;
    align-items: center;
    gap: 6px;

    .el-icon {
      color: #4f46e5 !important; /* 经典紫色 */
      font-size: 15px !important;
      margin-right: 0 !important;
    }

    &.error-title {
      color: #ef4444 !important;
      .el-icon {
        color: #ef4444 !important;
      }
    }
  }

  .detail-row {
    padding: 4px 8px !important;
  }

  .detail-item {
    border-bottom: 1px solid rgba(241, 245, 249, 0.6) !important;
    padding: 12px 10px !important;
    display: flex;
    align-items: center;
  }

  .detail-label {
    color: #64748b !important;
    font-weight: 500;
    width: 80px !important;
  }

  .detail-value {
    color: #1e293b !important;
    
    .method-tag {
      border-radius: 4px !important;
      padding: 2px 8px !important;
      display: inline-block;
      font-size: 11px;
      font-weight: 700;
      line-height: 1.2;
      vertical-align: middle;
      
      &.method-GET {
        background-color: rgba(16, 185, 129, 0.08) !important;
        color: #10b981 !important;
        border: 1px solid rgba(16, 185, 129, 0.15) !important;
      }
      &.method-POST {
        background-color: rgba(79, 70, 229, 0.08) !important;
        color: #4f46e5 !important;
        border: 1px solid rgba(79, 70, 229, 0.15) !important;
      }
      &.method-PUT {
        background-color: rgba(245, 158, 11, 0.08) !important;
        color: #f59e0b !important;
        border: 1px solid rgba(245, 158, 11, 0.15) !important;
      }
      &.method-DELETE {
        background-color: rgba(239, 68, 68, 0.08) !important;
        color: #ef4444 !important;
        border: 1px solid rgba(239, 68, 68, 0.15) !important;
      }
    }
  }

  .detail-location {
    color: #94a3b8 !important;
  }

  /* 参数 JSON 代码框美化 */
  .code-body {
    padding: 12px 16px !important;
  }

  .code-wrap {
    background: rgba(248, 250, 252, 0.5) !important;
    border: 1px solid rgba(226, 232, 240, 0.6) !important;
    border-radius: 8px !important;
  }

  .code-action {
    .el-button {
      background: rgba(255, 255, 255, 0.8) !important;
      border: 1px solid rgba(226, 232, 240, 0.8) !important;
      border-radius: 6px !important;
      color: #64748b !important;
      transition: all 0.2s ease;
      
      &:hover {
        background: #ffffff !important;
        color: #4f46e5 !important;
        border-color: rgba(79, 70, 229, 0.5) !important;
      }
    }
  }

  .code-pre {
    color: #334155 !important;
    font-size: 12.5px !important;
    line-height: 1.65 !important;
  }

  /* 异常体美化 */
  .error-body {
    padding: 12px 16px !important;
    background: rgba(254, 242, 242, 0.4) !important;
    border-top: 1px solid rgba(239, 68, 68, 0.1) !important;
    border-radius: 0 0 12px 12px;
  }
  
  .error-msg {
    color: #dc2626 !important;
    font-family: Consolas, monospace !important;
    font-size: 12.5px !important;
    line-height: 1.6 !important;
  }
}

/* 暗色模式适配 */
.dark {
  .polaris-glass-dialog {
    .detail-card {
      background: rgba(255, 255, 255, 0.03) !important;
      border-color: rgba(255, 255, 255, 0.05) !important;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
    }

    .detail-card-title {
      background: rgba(255, 255, 255, 0.02) !important;
      color: #f1f5f9 !important;
      border-bottom-color: rgba(255, 255, 255, 0.05) !important;
      
      .el-icon {
        color: #818cf8 !important;
      }
    }

    .detail-item {
      border-bottom-color: rgba(255, 255, 255, 0.02) !important;
    }

    .detail-label {
      color: #94a3b8 !important;
    }

    .detail-value {
      color: #e2e8f0 !important;
    }

    .code-wrap {
      background: rgba(15, 23, 42, 0.25) !important;
      border-color: rgba(255, 255, 255, 0.05) !important;
    }

    .code-action {
      .el-button {
        background: rgba(255, 255, 255, 0.05) !important;
        border-color: rgba(255, 255, 255, 0.08) !important;
        color: #94a3b8 !important;
        
        &:hover {
          background: rgba(255, 255, 255, 0.1) !important;
          color: #818cf8 !important;
          border-color: rgba(129, 140, 248, 0.4) !important;
        }
      }
    }

    .code-pre {
      color: #cbd5e1 !important;
    }

    .error-body {
      background: rgba(239, 68, 68, 0.05) !important;
      border-top-color: rgba(239, 68, 68, 0.08) !important;
    }
    
    .error-msg {
      color: #f87171 !important;
    }
  }
}
</style>
