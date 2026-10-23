<template>
  <section v-if="canManage && items.length" class="vector-execution" aria-label="单集合受控清理">
    <h4>单集合受控清理</h4>
    <p>只处理已登记且本地条件满足的一个失效集合。删除不可恢复，不删除原文档文件，不调用模型。</p>
    <p>系统无法阻止外部管理员替换或恢复集合。必须先核对后端实际连接的存储服务，并建立无外部改动的独占运维窗口；无法保证时不要执行。</p>
    <el-select v-model="generation" :disabled="busy" placeholder="选择登记版本／构建代次" aria-label="待检查的集合登记">
      <el-option v-for="row in items" :key="row.buildGeneration" :value="row.buildGeneration" :label="`v${row.indexVersion} · 构建代次 ${row.buildGeneration}`" />
    </el-select>
    <div class="execution-toolbar">
      <el-button :disabled="busy || !selected" @click="refresh">刷新清理状态</el-button>
      <el-button :disabled="busy || !selected || selected.reason !== 'LOCAL_CANDIDATE'" @click="prepare">生成单集合清理计划</el-button>
    </div>
    <p v-if="plan?.blocked" role="status">当前条件不允许清理，请重新预览并核对保护原因；没有执行删除。</p>
    <template v-if="plan?.planId && !plan.blocked">
      <p>本次计划仅对应 v{{ selected?.indexVersion }}／构建代次 {{ generation }}，执行时仍会重新校验。新预览不会自动执行。</p>
      <p>计划有效至：{{ deadlineText() }}（北京时间）。最终有效性以服务端校验为准。</p>
      <p v-if="planExpired" role="status">计划已过期或有效期不可识别，请重新生成计划并确认操作范围；没有自动执行。</p>
      <el-checkbox v-model="controlled" :disabled="busy || planExpired">我已核对目标存储，并确认本次操作期间没有外部创建、恢复、替换或修改归属标记。</el-checkbox>
      <el-button type="warning" :loading="busy" :disabled="busy || planExpired || !controlled" @click="execute">确认不可恢复地清理此集合</el-button>
    </template>
    <div v-if="result" role="status">
      <p>清理状态：{{ stateText(result.state) }}；已尝试 {{ result.attempts }} 次。</p>
      <p v-if="result.errorCode">{{ errorText(result.errorCode) }}</p>
      <p v-if="result.state === 'SUCCEEDED'">远端删除已确认；只退役了同集合的快照元数据，文件载荷另按保留期清理。请重新预览查看最新保护状态。</p>
      <p v-if="result.state === 'RETRY_WAIT'">本次确定未派发删除；至少等待 5 分钟，再重新预览并生成新计划。不会自动重试，最多 5 次。</p>
      <p v-if="result.state === 'RUNNING'">已领取任务，不代表删除成功。请手动刷新；重复点击不会恢复或重新派发。</p>
      <p v-if="result.state === 'MANUAL_REQUIRED'">远端状态需要人工核对。本页不提供强制重试或自动认定成功。</p>
      <el-button v-if="result.state === 'RUNNING' && result.leaseExpired" :disabled="busy" @click="stop">标记中断，转人工核对</el-button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>

<script>
import {
  executeKnowledgeVectorCleanup,
  getKnowledgeVectorCleanup,
  planKnowledgeVectorCleanup,
  stopExpiredKnowledgeVectorCleanup
} from '@/api/ai/knowledge'

export default {
  name: 'KnowledgeVectorCleanupExecution',
  props: {knowledge: {type: Object, required: true}, canManage: Boolean, items: {type: Array, default: () => []}},
  data() {return {generation: null, plan: null, result: null, controlled: false, busy: false, error: '', revision: 0, planExpired: false, expiryTimer: null}},
  computed: {selected() {return this.items.find(row => row.buildGeneration === this.generation) || null}},
  watch: {'knowledge.id'() {this.reset(true)}, 'knowledge.indexVersion'() {this.reset(true)}, 'knowledge.vectorCollection'() {this.reset(true)},
    'knowledge.indexStatus'() {this.reset(true)}, canManage() {this.reset(true)}, items() {this.reset(true)}, generation() {this.reset(false)}},
  beforeUnmount() {this.reset(true)},
  methods: {
    reset(clear) {this.clearExpiry();this.revision++;if (clear) this.generation = null;this.plan = null;this.result = null;this.controlled = false;this.busy = false;this.error = ''},
    clearExpiry() {if (this.expiryTimer !== null) clearTimeout(this.expiryTimer);this.expiryTimer = null;this.planExpired = false},
    deadline() {
      const value = this.plan?.expiresAt;
      if (typeof value === 'number') return value;
      if (typeof value !== 'string') return NaN;
      // 项目 Date 响应采用 GMT+8 的无偏移格式，不使用浏览器所在时区解释。
      const date = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value) ? value.replace(' ', 'T') + '+08:00' : value;
      return /(?:Z|[+-]\d{2}:\d{2})$/.test(date) ? Date.parse(date) : NaN;
    },
    deadlineText() {const deadline = this.deadline();return Number.isFinite(deadline) ? new Date(deadline).toLocaleString('zh-CN', {timeZone: 'Asia/Shanghai', hour12: false}) : '时间未知'},
    expirePlan() {this.planExpired = true;this.controlled = false},
    checkExpiry() {
      if (this.planExpired || !Number.isFinite(this.deadline()) || this.deadline() <= Date.now()) {this.expirePlan();return false}
      return true;
    },
    watchExpiry(ctx) {
      this.clearExpiry();
      if (!this.plan?.planId || this.plan.blocked || !this.checkExpiry()) return;
      const planId = this.plan.planId;
      // 只更新本地提示，不轮询接口；浏览器挂起后还会在确认前、提交前再次检查。
      const timer = setTimeout(() => {
        if (this.expiryTimer === timer && this.current(ctx) && this.plan?.planId === planId) {this.expiryTimer = null;this.expirePlan()}
      }, Math.min(this.deadline() - Date.now(), 300000));
      this.expiryTimer = timer;
    },
    stateText(state) {return {NONE: '尚未执行', RUNNING: '执行中／结果待确认', RETRY_WAIT: '未派发，等待显式重试', SUCCEEDED: '已确认清理', MANUAL_REQUIRED: '需人工核对'}[state] || '状态未知'},
    errorText(code) {return {REMOTE_PRECHECK_UNAVAILABLE: '远端归属读取不可确定，不等于集合不存在。', OWNERSHIP_NOT_MATCHED: '存储路由或归属不匹配，不能删除。',
      CLEANUP_SCOPE_CHANGED: '执行范围已变化，未继续派发。', DELETE_RESULT_UNKNOWN: '删除请求结果未确认，不能自动重删。', INTERRUPTED_RESULT_UNKNOWN: '任务已转人工核对；不能撤回或保证停止已派发请求。'}[code] || '请核对清理状态，不自动重复删除。'},
    context() {return {id: this.knowledge.id, generation: this.generation, revision: this.revision}},
    current(ctx) {return this.canManage && ctx.id === this.knowledge.id && ctx.generation === this.generation && ctx.revision === this.revision},
    valid() {return this.canManage && this.knowledge.id && this.selected && Number.isSafeInteger(this.generation) && this.generation > 0 && !this.busy},
    async prepare() {
      if (!this.valid() || this.selected.reason !== 'LOCAL_CANDIDATE') return;
      const ctx = this.context();this.clearExpiry();this.busy = true;this.plan = null;this.result = null;this.controlled = false;this.error = '';
      try {
        const response = await planKnowledgeVectorCleanup(ctx.id, ctx.generation);
        if (this.current(ctx)) {
          if (response.data?.buildGeneration !== ctx.generation || response.data?.indexVersion !== this.selected.indexVersion) throw new Error('changed');
          this.plan = response.data;
          this.watchExpiry(ctx);
        }
      } catch (_) {if (this.current(ctx)) this.error = '计划生成失败或范围已变化，请重新预览；没有执行删除。'}
      finally {if (this.current(ctx)) this.busy = false}
    },
    async refresh() {
      if (!this.valid()) return;
      const ctx = this.context();this.clearExpiry();this.busy = true;this.plan = null;this.controlled = false;this.result = null;this.error = '';
      try {
        const response = await getKnowledgeVectorCleanup(ctx.id, ctx.generation);
        if (this.current(ctx)) this.accept(response.data, ctx);
      } catch (_) {if (this.current(ctx)) this.error = '清理状态暂不可用，请稍后手动刷新；不会重新派发删除。'}
      finally {if (this.current(ctx)) this.busy = false}
    },
    accept(result, ctx) {
      if (result?.buildGeneration !== ctx.generation || result?.indexVersion !== this.selected?.indexVersion) throw new Error('changed');
      this.result = result;
    },
    async execute() {
      if (!this.valid() || !this.plan?.planId || this.plan.blocked || !this.controlled) return;
      if (!this.checkExpiry()) return;
      const ctx = this.context(), planId = this.plan.planId;this.busy = true;this.error = '';let submitted = false;
      try {
        await this.$modal.confirm(`仅清理 v${this.selected.indexVersion}／构建代次 ${ctx.generation} 的一个失效集合。删除不可恢复；你已核对目标存储并建立无外部替换／恢复的独占运维窗口。确认执行吗？`);
        if (!this.current(ctx) || !this.controlled || planId !== this.plan?.planId) return;
        if (!this.checkExpiry()) return;
        this.clearExpiry();
        submitted = true;
        const response = await executeKnowledgeVectorCleanup(ctx.id, ctx.generation, planId);
        if (this.current(ctx)) this.accept(response.data, ctx);
      } catch (_) {if (submitted && this.current(ctx)) this.error = '执行结果未确认，请刷新清理状态；不会自动再次派发删除。'}
      finally {if (this.current(ctx)) {if (submitted) {this.plan = null;this.controlled = false}this.busy = false}}
    },
    async stop() {
      if (!this.valid() || this.result?.state !== 'RUNNING' || !this.result.leaseExpired) return;
      const ctx = this.context();this.busy = true;this.error = '';let submitted = false;
      try {
        await this.$modal.confirm('仅把过期任务标记为需人工核对，不重新删除，不代表远端已停止或已成功，也不能撤回已派发请求。确认标记吗？');
        if (!this.current(ctx) || this.result?.state !== 'RUNNING' || !this.result.leaseExpired) return;
        submitted = true;
        const response = await stopExpiredKnowledgeVectorCleanup(ctx.id, ctx.generation);
        if (this.current(ctx)) this.accept(response.data, ctx);
      } catch (_) {if (submitted && this.current(ctx)) {this.result = null;this.error = '标记结果未确认，请手动刷新；不会重新删除远端。'}}
      finally {if (this.current(ctx)) this.busy = false}
    }
  }
}
</script>

<style scoped>
.vector-execution {--execution-check: #fff; padding: 16px; margin-top: 16px; border: 1px solid var(--el-border-color); border-radius: 10px; color: var(--el-text-color-primary); overflow-wrap: anywhere}
.vector-execution h4 {margin: 0 0 10px; color: var(--el-text-color-primary)}
.vector-execution p {line-height: 1.7; color: var(--el-text-color-regular)}
.execution-toolbar {display: flex; gap: 10px; flex-wrap: wrap; margin: 12px 0}
.vector-execution :deep(.el-select) {width: 100%; max-width: 360px}
.vector-execution :deep(.el-checkbox) {height: auto; display: flex; align-items: flex-start; margin: 12px 0; white-space: normal}
.vector-execution :deep(.el-checkbox__label) {white-space: normal; color: var(--el-text-color-regular) !important; line-height: 1.7}
.vector-execution :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {background: var(--vector-accent) !important; border-color: var(--vector-accent) !important}
.vector-execution :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) {border-color: var(--execution-check) !important}
.vector-execution :deep(.el-checkbox__input.is-focus .el-checkbox__inner) {outline: 2px solid var(--vector-accent); outline-offset: 3px}
.vector-execution :deep(.el-button.el-button.el-button.el-button.el-button--warning) {color: #78350f !important; background: #fef3c7 !important; border-color: #b45309 !important; white-space: normal; height: auto; min-height: 32px; line-height: 1.6}
.vector-execution :deep(.el-button.el-button.el-button.el-button.el-button--warning:not(.is-disabled):is(:hover, :focus-visible)) {background: #fde68a !important; color: #78350f !important}
.vector-execution :deep(.el-button.el-button.el-button.el-button.el-button--warning.is-disabled) {color: var(--vector-muted) !important; background: var(--vector-disabled) !important; border-color: var(--el-border-color) !important}
</style>
<style>
html.dark .vector-execution {--execution-check: #0f172a}
</style>
