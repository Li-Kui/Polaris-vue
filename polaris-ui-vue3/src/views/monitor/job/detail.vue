<template>
  <el-dialog :title="type === 'log' ? '调度日志详细' : '任务详细'" v-model="dialogVisible" width="780px" append-to-body class="polaris-glass-dialog">
    <div class="detail-wrap">
      <template v-if="type === 'log'">
        <!-- 基本信息 -->
        <div class="detail-card">
          <div class="detail-card-title">
            <el-icon><InfoFilled /></el-icon> 基本信息
          </div>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">日志编号</span><span class="detail-value">{{ form.jobLogId }}</span></div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item">
                <span class="detail-label">执行状态</span>
                <el-tag v-if="form.status == 0" type="success" size="small">正常</el-tag>
                <el-tag v-else type="danger" size="small">失败</el-tag>
              </div>
            </el-col>
          </el-row>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">开始时间</span><span class="detail-value">{{ form.startTime }}</span></div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">结束时间</span><span class="detail-value">{{ form.endTime }}</span></div>
            </el-col>
          </el-row>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">记录时间</span><span class="detail-value">{{ form.createTime }}</span></div>
            </el-col>
            <el-col :span="12" v-if="form.status == 0 && form.startTime && form.endTime">
              <div class="detail-item"><span class="detail-label">执行耗时</span><span class="detail-value">{{ costTime }} 毫秒</span></div>
            </el-col>
          </el-row>
        </div>
        <!-- 任务信息 -->
        <div class="detail-card">
          <div class="detail-card-title">
            <el-icon><Clock /></el-icon> 任务信息
          </div>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">任务名称</span><span class="detail-value">{{ form.jobName }}</span></div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item">
                <span class="detail-label">任务分组</span>
                <dict-tag :options="sys_job_group" :value="form.jobGroup" />
              </div>
            </el-col>
          </el-row>
          <el-row class="detail-row">
            <el-col :span="24">
              <div class="detail-item"><span class="detail-label">日志信息</span><span class="detail-value">{{ form.jobMessage }}</span></div>
            </el-col>
          </el-row>
        </div>
        <!-- 调用目标 -->
        <div class="detail-card">
          <div class="detail-card-title">
            <el-icon><Operation /></el-icon> 调用目标
          </div>
          <div class="code-body">
            <div class="code-wrap"><pre class="code-pre">{{ form.invokeTarget || '（无）' }}</pre></div>
          </div>
        </div>
        <!-- 异常信息 -->
        <div class="detail-card" v-if="form.status == 1">
          <div class="detail-card-title error-title">
            <el-icon><Warning /></el-icon> 异常信息
          </div>
          <div class="error-body"><div class="error-msg">{{ form.exceptionInfo }}</div></div>
        </div>
      </template>

      <template v-else>
        <!-- 任务配置 -->
        <div class="detail-card">
          <div class="detail-card-title">
            <el-icon><Setting /></el-icon> 任务配置
          </div>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">任务编号</span><span class="detail-value">{{ form.jobId }}</span></div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">任务名称</span><span class="detail-value">{{ form.jobName }}</span></div>
            </el-col>
          </el-row>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item">
                <span class="detail-label">任务分组</span>
                <dict-tag :options="sys_job_group" :value="form.jobGroup" />
              </div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item">
                <span class="detail-label">执行状态</span>
                <el-tag v-if="form.status == 0" type="success" size="small">正常</el-tag>
                <el-tag v-else type="info" size="small">暂停</el-tag>
              </div>
            </el-col>
          </el-row>
        </div>
        <!-- 调度信息 -->
        <div class="detail-card">
          <div class="detail-card-title">
            <el-icon><Calendar /></el-icon> 调度信息
          </div>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">cron 表达式</span><span class="detail-value mono">{{ form.cronExpression }}</span></div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">下次执行时间</span><span class="detail-value">{{ parseTime(form.nextValidTime) }}</span></div>
            </el-col>
          </el-row>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item">
                <span class="detail-label">执行策略</span>
                <el-tag v-if="form.misfirePolicy == 0" type="info" size="small">默认策略</el-tag>
                <el-tag v-else-if="form.misfirePolicy == 1" type="warning" size="small">立即执行</el-tag>
                <el-tag v-else-if="form.misfirePolicy == 2" type="primary" size="small">执行一次</el-tag>
                <el-tag v-else-if="form.misfirePolicy == 3" type="danger" size="small">放弃执行</el-tag>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item">
                <span class="detail-label">并发执行</span>
                <el-tag v-if="form.concurrent == 0" type="success" size="small">允许</el-tag>
                <el-tag v-else type="danger" size="small">禁止</el-tag>
              </div>
            </el-col>
          </el-row>
        </div>
        <!-- 执行方法 -->
        <div class="detail-card">
          <div class="detail-card-title">
            <el-icon><Operation /></el-icon> 执行方法
          </div>
          <div class="code-body">
            <div class="code-wrap"><pre class="code-pre">{{ form.invokeTarget || '（无）' }}</pre></div>
          </div>
        </div>
        <!-- 元信息 -->
        <div class="detail-card">
          <div class="detail-card-title">
            <el-icon><Document /></el-icon> 元信息
          </div>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">创建人</span><span class="detail-value">{{ form.createBy || '-' }}</span></div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">{{ form.createTime }}</span></div>
            </el-col>
          </el-row>
          <el-row class="detail-row">
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">更新人</span><span class="detail-value">{{ form.updateBy || '-' }}</span></div>
            </el-col>
            <el-col :span="12">
              <div class="detail-item"><span class="detail-label">更新时间</span><span class="detail-value">{{ form.updateTime || '-' }}</span></div>
            </el-col>
          </el-row>
          <el-row class="detail-row" v-if="form.remark">
            <el-col :span="24">
              <div class="detail-item"><span class="detail-label">备注</span><span class="detail-value">{{ form.remark }}</span></div>
            </el-col>
          </el-row>
        </div>
      </template>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">关 闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="JobDetail">
const props = defineProps({
  visible: { type: Boolean, default: false },
  row: { type: Object, default: () => ({}) },
  // 'job' 任务详细 | 'log' 调度日志详细
  type: { type: String, default: 'job' }
})

const emit = defineEmits(['update:visible'])

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const { proxy } = getCurrentInstance()
const { sys_job_group } = useDict('sys_job_group')

const form = computed(() => props.row || {})

const costTime = computed(() => {
  if (!form.value.startTime || !form.value.endTime) return 0
  return new Date(form.value.endTime).getTime() - new Date(form.value.startTime).getTime()
})
</script>

<style lang="scss">
/* 定时任务详情弹窗局部重写（全局样式，但限定在 .polaris-glass-dialog 中以隔离保护） */
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
      color: #4f46e5 !important;
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
    width: 90px !important;
    flex-shrink: 0;
  }

  .detail-value {
    color: #1e293b !important;
    word-break: break-all;
    
    &.mono {
      font-family: Consolas, Monaco, monospace;
      font-size: 12.5px;
      color: #0f172a !important;
    }
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

  .code-pre {
    color: #334155 !important;
    font-size: 12.5px !important;
    line-height: 1.65 !important;
    white-space: pre-wrap;
    word-break: break-all;
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
    white-space: pre-wrap;
    word-break: break-all;
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
      
      &.mono {
        color: #cbd5e1 !important;
      }
    }

    .code-wrap {
      background: rgba(15, 23, 42, 0.25) !important;
      border-color: rgba(255, 255, 255, 0.05) !important;
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
