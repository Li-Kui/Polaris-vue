<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    :label-position="compact ? 'left' : 'top'"
    :label-width="compact ? '112px' : undefined"
    :class="['api-connector-form', {'api-connector-form--compact': compact}]"
  >
    <section class="connector-form-section">
      <h4>基本信息</h4>
      <el-form-item label="连接名称" prop="connectorName">
        <el-input v-model="form.connectorName" maxlength="128" placeholder="例如：订单系统" />
      </el-form-item>
      <el-form-item label="Base URL" prop="baseUrl">
        <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" />
        <small class="field-hint">这里只填写服务基础地址，具体接口路径在工作流节点中配置。</small>
      </el-form-item>
    </section>

    <section class="connector-form-section">
      <h4>身份认证</h4>
      <el-form-item label="认证方式" prop="authType">
        <el-select v-model="form.authType" style="width: 100%" @change="authTypeChanged">
          <el-option label="无需认证" value="NONE" />
          <el-option label="API Key（Header 传递）" value="API_KEY" />
          <el-option label="Bearer Token" value="BEARER" />
        </el-select>
      </el-form-item>

      <template v-if="form.authType !== 'NONE'">
        <el-alert
          v-if="editing && form.credentialConfigured && form.credentialAction === 'KEEP'"
          title="凭证已配置，保存其他字段不会修改现有凭证。"
          type="success"
          :closable="false"
          show-icon
          class="credential-alert"
        >
          <template #default>
            <el-button link type="primary" @click="replaceCredential">更换凭证</el-button>
          </template>
        </el-alert>
        <template v-else>
          <el-form-item v-if="form.authType === 'API_KEY'" label="Header 名称" prop="credential.headerName">
            <el-input v-model="form.credential.headerName" placeholder="X-API-Key" />
          </el-form-item>
          <el-form-item
            :label="form.authType === 'API_KEY' ? 'API Key' : 'Bearer Token'"
            prop="credential.secret"
          >
            <el-input
              v-model="form.credential.secret"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="请输入认证凭证"
            />
          </el-form-item>
          <el-button
            v-if="editing && form.credentialConfigured && form.authType === form.originalAuthType"
            link
            @click="keepCredential"
          >取消更换，保留原凭证</el-button>
        </template>
      </template>
    </section>

    <el-collapse class="connector-advanced">
      <el-collapse-item title="默认请求头与超时时间" name="advanced">
        <section class="connector-form-section advanced-section">
          <div class="header-list-title">
            <span>默认请求头</span>
            <el-button link type="primary" icon="Plus" @click="addHeader">添加请求头</el-button>
          </div>
          <div v-for="(item, index) in form.headerRows" :key="index" class="header-row">
            <el-input v-model="item.name" placeholder="Header 名称" />
            <el-input v-model="item.value" placeholder="Header 值" />
            <el-button link type="danger" icon="Delete" @click="removeHeader(index)" />
          </div>
          <small v-if="!form.headerRows.length" class="field-hint">暂无默认请求头。</small>
          <el-form-item label="超时时间" prop="timeoutSeconds" class="timeout-field">
            <el-input-number v-model="form.timeoutSeconds" :min="1" :max="120" :step="1" />
            <span>秒</span>
          </el-form-item>
        </section>
      </el-collapse-item>
      <el-collapse-item title="响应数据结构（JSON Schema）" name="responseSchema">
        <section class="connector-form-section advanced-section">
          <el-form-item label="响应体 Schema" prop="responseSchemaText" class="response-schema-field">
            <el-input
              v-model="form.responseSchemaText"
              type="textarea"
              :rows="8"
              spellcheck="false"
              placeholder='{"type":"object","properties":{"data":{"type":"object"}}}'
            />
            <small class="field-hint">
              可选。声明接口响应 body 的结构后，工作流映射面板可以直接选择其中字段；不会读取真实响应数据。
            </small>
          </el-form-item>
        </section>
      </el-collapse-item>
    </el-collapse>
  </el-form>
</template>

<script setup>
import {ref} from 'vue'

const props = defineProps({
  form: {
    type: Object,
    required: true
  },
  editing: {
    type: Boolean,
    default: false
  },
  compact: {
    type: Boolean,
    default: false
  }
})

const formRef = ref(null)

const credentialRequired = (rule, value, callback) => {
  if (props.form.authType !== 'NONE'
      && props.form.credentialAction === 'REPLACE'
      && !String(value || '').trim()) {
    callback(new Error('请输入认证凭证'))
    return
  }
  callback()
}

const headerNameRequired = (rule, value, callback) => {
  if (props.form.authType === 'API_KEY'
      && props.form.credentialAction === 'REPLACE'
      && !String(value || '').trim()) {
    callback(new Error('请输入 Header 名称'))
    return
  }
  callback()
}

const responseSchemaValid = (rule, value, callback) => {
  const text = String(value || '').trim()
  if (!text) {
    callback()
    return
  }
  try {
    const schema = JSON.parse(text)
    if (!schema || Array.isArray(schema) || typeof schema !== 'object') {
      callback(new Error('响应 Schema 必须是 JSON 对象'))
      return
    }
    if (schema.type !== undefined
      && typeof schema.type !== 'string'
      && !Array.isArray(schema.type)) {
      callback(new Error('响应 Schema 的 type 必须是字符串或字符串数组'))
      return
    }
    callback()
  } catch (error) {
    callback(new Error('请输入合法的 JSON Schema'))
  }
}

const rules = {
  connectorName: [{required: true, message: '请输入连接名称', trigger: 'blur'}],
  baseUrl: [{required: true, message: '请输入 Base URL', trigger: 'blur'}],
  'credential.headerName': [{validator: headerNameRequired, trigger: 'blur'}],
  'credential.secret': [{validator: credentialRequired, trigger: 'blur'}],
  responseSchemaText: [{validator: responseSchemaValid, trigger: 'blur'}]
}

function authTypeChanged(value) {
  if (value === 'NONE') {
    props.form.credentialAction = 'CLEAR'
    props.form.credential.secret = ''
    return
  }
  props.form.credentialAction = props.editing
      && props.form.credentialConfigured
      && value === props.form.originalAuthType ? 'KEEP' : 'REPLACE'
  if (value === 'API_KEY' && !props.form.credential.headerName) {
    props.form.credential.headerName = 'X-API-Key'
  }
}

function replaceCredential() {
  props.form.credentialAction = 'REPLACE'
  props.form.credential.secret = ''
}

function keepCredential() {
  if (props.form.authType !== props.form.originalAuthType) return
  props.form.credentialAction = 'KEEP'
  props.form.credential.secret = ''
}

function addHeader() {
  props.form.headerRows.push({name: '', value: ''})
}

function removeHeader(index) {
  props.form.headerRows.splice(index, 1)
}

async function validate() {
  return formRef.value?.validate()
}

function buildPayload() {
  const defaultHeaders = {}
  props.form.headerRows.forEach(item => {
    const name = String(item.name || '').trim()
    if (name) defaultHeaders[name] = item.value || ''
  })
  return {
    id: props.form.id,
    connectorName: String(props.form.connectorName || '').trim(),
    baseUrl: String(props.form.baseUrl || '').trim(),
    authType: props.form.authType,
    credentialAction: props.form.credentialAction,
    credential: props.form.credentialAction === 'REPLACE'
      ? {
          headerName: props.form.authType === 'API_KEY'
            ? String(props.form.credential.headerName || '').trim() : undefined,
          secret: props.form.credential.secret
        }
      : undefined,
    defaultHeaders,
    responseSchema: String(props.form.responseSchemaText || '').trim()
      ? JSON.parse(props.form.responseSchemaText) : null,
    timeoutMs: Number(props.form.timeoutSeconds || 30) * 1000,
    status: props.form.status || '0',
    remark: props.form.remark
  }
}

defineExpose({validate, buildPayload})
</script>

<style scoped lang="scss">
.api-connector-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.connector-form-section {
  h4 {
    margin: 0 0 14px;
    color: #1e293b;
    font-size: 15px;
  }
}

.credential-alert {
  margin-bottom: 4px;
}

.connector-advanced {
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.advanced-section {
  padding: 4px 2px 10px;
}

.header-list-title,
.header-row,
.timeout-field :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-list-title {
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.header-row {
  margin-bottom: 8px;
}

.timeout-field {
  margin-top: 18px;
  margin-bottom: 0;
}

.response-schema-field {
  margin-bottom: 0;
}

.response-schema-field :deep(textarea) {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
}

.field-hint {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.api-connector-form--compact {
  gap: 0;

  .connector-form-section h4 {
    display: none;
  }

  .connector-form-section :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  .connector-form-section :deep(.el-form-item__label) {
    padding-right: 18px;
    color: var(--workflow-text, var(--el-text-color-primary));
    font-weight: 500;
  }

  .connector-form-section :deep(.el-input__wrapper),
  .connector-form-section :deep(.el-select__wrapper) {
    min-height: 42px;
    border-radius: 8px;
  }

  .connector-advanced {
    margin-top: 2px;
    border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
    border-radius: 8px;
    overflow: hidden;
  }

  .connector-advanced :deep(.el-collapse-item__header) {
    height: 46px;
    padding: 0 14px;
    border-bottom: 0;
  }

  .connector-advanced :deep(.el-collapse-item__wrap) {
    border-bottom: 0;
  }

  .connector-advanced :deep(.el-collapse-item__content) {
    padding: 0 14px 14px;
  }
}
</style>
