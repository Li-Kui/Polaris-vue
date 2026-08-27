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
      <div class="field-grid">
        <el-form-item label="数据库名称" prop="databaseName">
          <el-input v-model="form.databaseName" placeholder="orders" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="128" placeholder="workflow_reader" />
        </el-form-item>
      </div>

      <el-alert
        v-if="editing && form.passwordConfigured && form.credentialAction === 'KEEP'"
        title="密码已配置，保存其他字段不会修改现有密码。"
        type="success"
        :closable="false"
        show-icon
        class="credential-alert"
      >
        <template #default>
          <el-button link type="primary" @click="replacePassword">更换密码</el-button>
        </template>
      </el-alert>
      <template v-else>
        <el-form-item label="数据库密码" prop="credential.password">
          <el-input
            v-model="form.credential.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请输入只读数据库账号密码"
          />
        </el-form-item>
        <el-button
          v-if="editing && form.passwordConfigured"
          link
          @click="keepPassword"
        >取消更换，保留原密码</el-button>
      </template>
      <small class="field-hint">系统只允许执行只读查询；保存连接前会自动验证连通性。</small>
    </section>

    <el-collapse class="advanced-settings">
      <el-collapse-item title="SSL 与超时设置" name="advanced">
        <section class="form-section advanced-section">
          <el-form-item label="标准 SSL">
            <el-switch v-model="form.sslEnabled" active-text="启用" inactive-text="关闭" />
          </el-form-item>
          <div class="field-grid">
            <el-form-item label="连接超时">
              <el-input-number v-model="form.connectTimeoutSeconds" :min="1" :max="30" />
              <span class="unit">秒</span>
            </el-form-item>
            <el-form-item label="查询超时">
              <el-input-number v-model="form.queryTimeoutSeconds" :min="1" :max="30" />
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

const props = defineProps({
  form: {type: Object, required: true},
  editing: {type: Boolean, default: false}
})

const formRef = ref(null)
const defaultPorts = {MYSQL: 3306, POSTGRESQL: 5432, SQLSERVER: 1433}

const passwordRequired = (rule, value, callback) => {
  if (props.form.credentialAction === 'REPLACE' && !String(value || '')) {
    callback(new Error('请输入数据库密码'))
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
  username: [{required: true, message: '请输入用户名', trigger: 'blur'}],
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

.credential-alert {
  margin-bottom: 4px;
}

.field-hint {
  display: block;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.advanced-settings {
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.advanced-section {
  padding: 4px 2px 8px;
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
