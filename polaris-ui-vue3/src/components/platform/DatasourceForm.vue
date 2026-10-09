<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="datasource-form">
    <section class="form-section">
      <h4>基本信息</h4>
      <el-form-item label="连接名称" prop="dsName">
        <el-input v-model="form.dsName" maxlength="128" placeholder="例如：订单库只读连接" />
      </el-form-item>
      <el-form-item label="数据库类型" prop="dsType">
        <el-select v-model="form.dsType" style="width: 100%" @change="typeChanged">
          <el-option label="MySQL" value="MYSQL" />
          <el-option label="PostgreSQL" value="POSTGRESQL" />
          <el-option label="Microsoft SQL Server" value="SQLSERVER" />
        </el-select>
      </el-form-item>
    </section>

    <section class="form-section">
      <h4>连接信息</h4>
      <div class="field-grid host-grid">
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="form.host" placeholder="db.example.internal" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" />
        </el-form-item>
      </div>
      <el-form-item label="数据库名称" prop="databaseName">
        <el-input v-model="form.databaseName" placeholder="例如：orders" />
      </el-form-item>

      <div class="credential-heading">
        <strong>登录凭据</strong>
        <small>填写用于连接该数据库的只读账号和密码</small>
      </div>
      <div class="field-grid credential-grid">
        <el-form-item label="数据库账号（用户名）" prop="username">
          <el-input v-model="form.username" maxlength="128" placeholder="例如：workflow_reader" />
        </el-form-item>
        <el-form-item
          v-if="editing && form.passwordConfigured && form.credentialAction === 'KEEP'"
          label="数据库密码"
        >
          <div class="credential-status" role="status">
            <el-icon class="credential-status__icon"><CircleCheckFilled /></el-icon>
            <div class="credential-status__copy">
              <strong>密码已配置</strong>
              <span>已安全保存，无需重复输入</span>
            </div>
            <button
              type="button"
              class="credential-status__action"
              @click="replacePassword"
            >更换密码</button>
          </div>
        </el-form-item>
        <el-form-item v-else label="数据库密码" prop="credential.password">
          <el-input
            v-model="form.credential.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请输入该数据库账号的密码"
          />
        </el-form-item>
      </div>
      <template v-if="editing && form.passwordConfigured && form.credentialAction !== 'KEEP'">
        <el-button
          link
          @click="keepPassword"
        >取消更换，保留原密码</el-button>
      </template>
      <small class="field-hint">系统只允许执行只读查询；保存连接前会自动验证连通性。</small>
    </section>

    <el-collapse class="advanced-settings">
      <el-collapse-item name="advanced">
        <template #title>
          <span class="advanced-settings__title">SSL 与超时设置</span>
        </template>
        <section class="form-section advanced-section">
          <el-form-item label="标准 SSL">
            <el-switch
              v-model="form.sslEnabled"
              active-text="启用"
              inactive-text="关闭"
              class="ssl-switch"
            />
          </el-form-item>
          <div class="field-grid">
            <el-form-item label="连接超时">
              <el-input-number
                v-model="form.connectTimeoutSeconds"
                :min="1"
                :max="30"
                class="timeout-number"
              />
              <span class="unit">秒</span>
            </el-form-item>
            <el-form-item label="查询超时">
              <el-input-number
                v-model="form.queryTimeoutSeconds"
                :min="1"
                :max="30"
                class="timeout-number"
              />
              <span class="unit">秒</span>
            </el-form-item>
          </div>
        </section>
      </el-collapse-item>
    </el-collapse>
  </el-form>
</template>

<script setup>
import {ref} from 'vue'
import {CircleCheckFilled} from '@element-plus/icons-vue'

const props = defineProps({
  form: {type: Object, required: true},
  editing: {type: Boolean, default: false}
})

const formRef = ref(null)
const defaultPorts = {MYSQL: 3306, POSTGRESQL: 5432, SQLSERVER: 1433}

const passwordRequired = (rule, value, callback) => {
  if (props.form.credentialAction === 'REPLACE' && !String(value || '')) {
    callback(new Error('请输入账号密码'))
    return
  }
  callback()
}

const rules = {
  dsName: [{required: true, message: '请输入连接名称', trigger: 'blur'}],
  dsType: [{required: true, message: '请选择数据库类型', trigger: 'change'}],
  host: [{required: true, message: '请输入主机地址', trigger: 'blur'}],
  port: [{required: true, message: '请输入端口', trigger: 'change'}],
  databaseName: [{required: true, message: '请输入数据库名称', trigger: 'blur'}],
  username: [{required: true, message: '请输入数据库账号（用户名）', trigger: 'blur'}],
  'credential.password': [{validator: passwordRequired, trigger: 'blur'}]
}

function typeChanged(value) {
  props.form.port = defaultPorts[value] || props.form.port
}

function replacePassword() {
  props.form.credentialAction = 'REPLACE'
  props.form.credential.password = ''
}

function keepPassword() {
  props.form.credentialAction = 'KEEP'
  props.form.credential.password = ''
}

async function validate() {
  return formRef.value?.validate()
}

function buildPayload() {
  return {
    id: props.form.id,
    dsName: String(props.form.dsName || '').trim(),
    dsType: props.form.dsType,
    host: String(props.form.host || '').trim(),
    port: Number(props.form.port),
    databaseName: String(props.form.databaseName || '').trim(),
    username: String(props.form.username || '').trim(),
    credentialAction: props.form.credentialAction,
    credential: props.form.credentialAction === 'REPLACE'
      ? {password: props.form.credential.password}
      : undefined,
    sslEnabled: !!props.form.sslEnabled,
    connectTimeoutSeconds: Number(props.form.connectTimeoutSeconds || 5),
    queryTimeoutSeconds: Number(props.form.queryTimeoutSeconds || 10),
    status: props.form.status || '0',
    remark: props.form.remark
  }
}

defineExpose({validate, buildPayload})
</script>

<style scoped lang="scss">
.datasource-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-section h4 {
  margin: 0 0 14px;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.host-grid {
  grid-template-columns: minmax(0, 1fr) 150px;
}

.credential-status {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 48px;
  padding: 8px 10px;
  border: 1px solid var(--el-color-success-light-7);
  border-radius: 10px;
  background: var(--el-color-success-light-9);
}

.credential-status__icon {
  color: var(--el-color-success);
  font-size: 20px;
}

.credential-status__copy {
  min-width: 0;

  strong,
  span {
    display: block;
  }

  strong {
    color: var(--el-text-color-primary);
    font-size: 13px;
    line-height: 1.35;
  }

  span {
    margin-top: 2px;
    color: var(--el-text-color-secondary);
    font-size: 11px;
    line-height: 1.35;
  }
}

.credential-status__action {
  appearance: none;
  height: 30px;
  padding: 4px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #4f46e5;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover,
  &:focus {
    background: rgba(79, 70, 229, 0.1);
    color: #3730a3;
  }

  &:focus-visible {
    outline: 2px solid rgba(79, 70, 229, 0.42);
    outline-offset: 1px;
  }

  &:active {
    background: rgba(79, 70, 229, 0.16);
    color: #312e81;
  }
}

.credential-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 4px 0 12px;

  strong {
    color: var(--el-text-color-primary);
    font-size: 14px;
  }

  small {
    color: var(--el-text-color-secondary);
  }
}

.field-hint {
  display: block;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.advanced-settings {
  --advanced-border: rgba(79, 70, 229, 0.2);
  --advanced-bg: rgba(79, 70, 229, 0.065);
  --advanced-body-bg: rgba(255, 255, 255, 0.58);

  overflow: hidden;
  margin-top: 2px;
  border: 1px solid var(--advanced-border);
  border-radius: 12px;
  background: var(--advanced-bg);
  box-shadow: 0 8px 22px -18px rgba(79, 70, 229, 0.7);

  :deep(.el-collapse-item__header) {
    min-height: 50px;
    padding: 0 16px;
    border-bottom: 0;
    background: transparent;
    color: var(--el-text-color-primary);
    transition: background-color 0.2s ease;

    &:hover {
      background: rgba(79, 70, 229, 0.08);
    }
  }

  :deep(.el-collapse-item__arrow) {
    margin-left: auto;
    color: #6366f1;
  }

  :deep(.el-collapse-item__wrap) {
    border-top: 1px solid var(--advanced-border);
    border-bottom: 0;
    background: var(--advanced-body-bg);
  }

  :deep(.el-collapse-item__content) {
    padding: 16px;
  }
}

.advanced-settings__title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
}

.advanced-section {
  padding: 0;
}

.ssl-switch {
  --el-switch-on-color: #4f46e5;
  --el-switch-off-color: #cbd5e1;

  :deep(.el-switch__core) {
    border-color: #cbd5e1 !important;
    background-color: #cbd5e1 !important;
    box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.06);
  }

  :deep(.el-switch__action) {
    background-color: #ffffff !important;
    box-shadow: 0 1px 4px rgba(15, 23, 42, 0.24);
  }

  :deep(.el-switch__label) {
    color: var(--el-text-color-regular) !important;
    font-weight: 600;
    opacity: 1;
  }

  :deep(.el-switch__label.is-active) {
    color: #4f46e5 !important;
    font-weight: 700;
  }

  &.is-checked :deep(.el-switch__core) {
    border-color: #4f46e5 !important;
    background-color: #4f46e5 !important;
  }
}

.timeout-number {
  :deep(.el-input-number__decrease),
  :deep(.el-input-number__increase) {
    border-color: var(--el-border-color) !important;
    background: var(--el-fill-color-light) !important;
    color: var(--el-text-color-regular) !important;
    opacity: 1;

    &:hover {
      color: #4f46e5 !important;
    }
  }
}

:global(.dark) .ssl-switch,
:global(.theme-dark) .ssl-switch {
  --el-switch-on-color: #38bdf8;
  --el-switch-off-color: #475569;

  :deep(.el-switch__core) {
    border-color: #475569 !important;
    background-color: #475569 !important;
  }

  :deep(.el-switch__label.is-active) {
    color: #38bdf8 !important;
  }

  &.is-checked :deep(.el-switch__core) {
    border-color: #38bdf8 !important;
    background-color: #38bdf8 !important;
  }
}

:global(.dark) .advanced-settings,
:global(.theme-dark) .advanced-settings {
  --advanced-border: rgba(56, 189, 248, 0.22);
  --advanced-bg: rgba(56, 189, 248, 0.08);
  --advanced-body-bg: rgba(15, 23, 42, 0.42);

  :deep(.el-collapse-item__header:hover) {
    background: rgba(56, 189, 248, 0.08);
  }

  :deep(.el-collapse-item__arrow) {
    color: #38bdf8;
  }
}

.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 640px) {
  .field-grid,
  .host-grid {
    grid-template-columns: 1fr;
  }
}
</style>
