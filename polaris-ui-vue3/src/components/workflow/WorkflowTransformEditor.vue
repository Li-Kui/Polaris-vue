<template>
  <section class="transform-editor">
    <header class="transform-heading">
      <div>
        <strong>整理数据</strong>
        <small>选择主要数据，再按需组合其他上游数据</small>
      </div>
      <el-tag v-if="rules.length" type="success" effect="plain">{{ rules.length }} 个输出字段</el-tag>
    </header>

    <div class="transform-mode" role="radiogroup" aria-label="整理方式">
      <button
        type="button"
        :class="{active: mode === 'OBJECT_MAP'}"
        :disabled="disabled"
        @click="changeMode('OBJECT_MAP')"
      >
        <strong>整理一个对象</strong>
        <small>从对象中挑选、重命名和转换字段</small>
      </button>
      <button
        type="button"
        :class="{active: mode === 'ARRAY_MAP'}"
        :disabled="disabled"
        @click="changeMode('ARRAY_MAP')"
      >
        <strong>逐项整理数组</strong>
        <small>对数组中的每一项应用同一套规则</small>
      </button>
      <button
        type="button"
        :class="{active: mode === 'VALUE'}"
        :disabled="disabled"
        @click="changeMode('VALUE')"
      >
        <strong>直接转换整份数据</strong>
        <small>筛选、排序或聚合根数组，也可转换单个基础值</small>
      </button>
    </div>

    <section class="transform-source-card">
      <div class="transform-section-title">
        <span><strong>1. 选择数据来源</strong><small>{{ sourceHelp }}</small></span>
        <el-tag v-if="selectedSource" size="small" effect="plain">{{ selectedSource.typeLabel }}</el-tag>
      </div>
      <el-select
        :model-value="sourceExpression"
        filterable
        clearable
        :disabled="disabled"
        :placeholder="mode === 'ARRAY_MAP' ? '选择一个上游数组' : mode === 'VALUE' ? '选择任意上游数据' : '选择一个上游对象'"
        style="width: 100%"
        @change="sourceChanged"
      >
        <el-option-group
          v-for="group in compatibleSourceGroups"
          :key="group.id"
          :label="group.label"
        >
          <el-option
            v-for="option in group.options"
            :key="option.expression"
            :label="option.label"
            :value="option.expression"
          >
            <div class="source-option">
              <span>{{ option.label }}</span>
              <small>{{ option.path || '完整数据' }} · {{ option.typeLabel }}</small>
            </div>
          </el-option>
        </el-option-group>
      </el-select>
      <p v-if="sourceExpression && !selectedSource" class="transform-warning">
        当前来源已失效或暂时无法从上游结构中验证，请重新选择。
      </p>
      <div v-for="source in additionalSources" :key="source.key" class="additional-source-row">
        <span>{{ sourceLabel(source.key) }}</span>
        <el-select
          :model-value="source.expression"
          filterable
          :disabled="disabled"
          style="width: 100%"
          @change="additionalSourceChanged(source.key, $event)"
        >
          <el-option-group v-for="group in sourceGroups" :key="group.id" :label="group.label">
            <el-option
              v-for="option in group.options"
              :key="option.expression"
              :label="`${option.label} · ${option.typeLabel}`"
              :value="option.expression"
              :disabled="usedSourceExpressions.has(option.expression) && option.expression !== source.expression"
            />
          </el-option-group>
        </el-select>
        <el-button
          text
          type="danger"
          :disabled="disabled"
          title="移除辅助来源"
          @click="removeAdditionalSource(source.key)"
        ><el-icon><Delete /></el-icon></el-button>
      </div>
      <el-dropdown
        v-if="selectedSource"
        :disabled="disabled || !availableAdditionalSourceOptions.length"
        trigger="click"
        @command="addAdditionalSource"
      >
        <el-button class="add-source-button" text type="primary" :disabled="disabled || !availableAdditionalSourceOptions.length">
          ＋ 添加辅助数据源
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="option in availableAdditionalSourceOptions"
              :key="option.expression"
              :command="option.expression"
            >{{ option.label }} · {{ option.typeLabel }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </section>

    <el-alert
      v-if="downstreamImpacts.length"
      type="warning"
      :closable="false"
      show-icon
      :title="`当前调整影响 ${downstreamImpacts.length} 个下游字段引用`"
      description="请重新选择下游节点中标记为失效的字段，保存前校验也会再次提示。"
    />

    <section class="transform-rules-card">
      <div class="transform-section-title transform-rules-heading">
        <span>
          <strong>{{ mode === 'VALUE' ? '2. 设置转换方式' : '2. 定义输出字段' }}</strong>
          <small>{{ mode === 'ARRAY_MAP' ? '下面的规则会应用到数组中的每一项，并继续识别内部数组' : mode === 'VALUE' ? '整份输入直接作为处理对象，不额外包裹输出字段' : '对象和数组可以任意嵌套，系统会自动保留层级' }}</small>
        </span>
        <div class="transform-actions">
          <el-dropdown
            v-if="mode !== 'VALUE'"
            split-button
            size="small"
            :disabled="disabled || !availableFields.length || rules.length >= 200"
            trigger="click"
            @click="addAllTopLevelFields"
            @command="autoAddFields"
          >
            自动添加字段
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="TOP_LEVEL">添加一级字段（保留完整结构）</el-dropdown-item>
                <el-dropdown-item command="LEAVES">展开全部末级字段（可分别处理）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown :disabled="disabled || !selectedSource || rules.length >= ruleLimit" trigger="click" @command="addRule">
            <el-button type="primary" size="small" :disabled="disabled || !selectedSource || rules.length >= ruleLimit">
              {{ mode === 'VALUE' ? '设置转换' : '添加字段' }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="COPY">来自上游字段</el-dropdown-item>
                <el-dropdown-item command="CONSTANT">固定值</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div v-if="!selectedSource" class="transform-empty">
        <el-icon><Connection /></el-icon>
        <strong>先选择数据来源</strong>
        <small>系统会根据上游结构列出可选字段。</small>
      </div>
      <div v-else-if="!rules.length" class="transform-empty">
        <el-icon><Operation /></el-icon>
        <strong>尚未添加输出字段</strong>
        <small>可一键添加已有字段，也可以逐个添加并设置转换方式。</small>
      </div>

      <article v-for="(rule, index) in rules" :key="index" class="transform-rule">
        <div class="rule-main-grid">
          <label v-if="mode !== 'VALUE'">
            <span>输出字段</span>
            <el-input
              :model-value="rule.targetPath"
              :disabled="disabled"
              placeholder="例如 用户.姓名、user.name 或 users[].name"
              @input="updateRule(index, {targetPath: $event})"
            />
          </label>
          <label v-if="rule.operation !== 'CONSTANT' && sourceChoices.length > 1">
            <span>使用数据源</span>
            <el-select
              :model-value="rule.sourceKey || 'source'"
              :disabled="disabled"
              style="width: 100%"
              @change="ruleSourceChanged(index, $event)"
            >
              <el-option
                v-for="source in sourceChoices"
                :key="source.key"
                :label="source.label"
                :value="source.key"
              />
            </el-select>
          </label>
          <label v-if="rule.operation !== 'CONSTANT'">
            <span>来源字段</span>
            <el-select
              :model-value="rule.sourcePath || ''"
              filterable
              :disabled="disabled"
              placeholder="选择字段"
              style="width: 100%"
              @change="sourceFieldChanged(index, $event)"
            >
              <el-option label="整个对象 / 当前项" value="" />
              <el-option
                v-for="field in fieldsForRule(rule)"
                :key="field.path"
                :label="`${field.path} · ${field.typeLabel}`"
                :value="field.path"
              />
            </el-select>
          </label>
          <label>
            <span>处理方式</span>
            <el-select
              :model-value="rule.operation"
              :disabled="disabled"
              style="width: 100%"
              @change="operationChanged(index, $event)"
            >
              <el-option
                v-for="option in operationOptionsForRule(rule)"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>
          <el-button
            class="rule-delete"
            text
            type="danger"
            :disabled="disabled"
            title="删除字段"
            aria-label="删除字段"
            @click="removeRule(index)"
          ><el-icon><Delete /></el-icon></el-button>
        </div>

        <div v-if="rule.operation === 'CONSTANT'" class="rule-constant-grid">
          <label>
            <span>固定值类型</span>
            <el-select
              :model-value="rule.resultType || 'string'"
              :disabled="disabled"
              style="width: 100%"
              @change="constantTypeChanged(index, $event)"
            >
              <el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" />
            </el-select>
          </label>
          <label>
            <span>固定值</span>
            <el-switch
              v-if="rule.resultType === 'boolean'"
              :model-value="!!rule.value"
              :disabled="disabled"
              @change="updateRule(index, {value: $event})"
            />
            <el-input
              v-else
              :type="['object', 'array'].includes(rule.resultType) ? 'textarea' : 'text'"
              :rows="['object', 'array'].includes(rule.resultType) ? 3 : undefined"
              :model-value="displayValue(rule.value, rule.resultType)"
              :disabled="disabled"
              :placeholder="constantPlaceholder(rule.resultType)"
              @change="constantValueChanged(index, $event)"
            />
          </label>
        </div>

        <div v-if="rule.operation === 'CONCAT'" class="rule-operation-options">
          <label class="span-2"><span>参与拼接的字段</span>
            <el-select
              :model-value="rule.sourcePaths || []"
              multiple
              filterable
              :disabled="disabled"
              placeholder="选择一个或多个字段"
              style="width: 100%"
              @change="updateOperationOption(index, {sourcePaths: $event})"
            >
              <el-option v-for="field in fieldsForRule(rule)" :key="field.path" :label="field.path" :value="field.path" />
            </el-select>
          </label>
          <label><span>连接符</span><el-input :model-value="rule.separator || ''" :disabled="disabled" @input="updateRule(index, {separator: $event})" /></label>
          <label><span>前缀</span><el-input :model-value="rule.prefix || ''" :disabled="disabled" @input="updateRule(index, {prefix: $event})" /></label>
          <label><span>后缀</span><el-input :model-value="rule.suffix || ''" :disabled="disabled" @input="updateRule(index, {suffix: $event})" /></label>
        </div>

        <div v-else-if="rule.operation === 'DATE_FORMAT'" class="rule-operation-options">
          <label><span>输入格式（可选）</span><el-input :model-value="rule.inputFormat || ''" :disabled="disabled" placeholder="自动识别 ISO 或时间戳" @input="updateRule(index, {inputFormat: $event})" /></label>
          <label><span>输出格式</span><el-input :model-value="rule.outputFormat || 'yyyy-MM-dd HH:mm:ss'" :disabled="disabled" @input="updateRule(index, {outputFormat: $event})" /></label>
          <label><span>时区</span><el-input :model-value="rule.timezone || 'Asia/Shanghai'" :disabled="disabled" @input="updateRule(index, {timezone: $event})" /></label>
        </div>

        <div v-else-if="rule.operation === 'ARRAY_FILTER'" class="rule-operation-options">
          <label><span>按数组项字段</span>
            <el-select :model-value="rule.filterPath || ''" filterable :disabled="disabled" style="width: 100%" @change="updateRule(index, {filterPath: $event})">
              <el-option v-for="field in arrayItemFields(rule)" :key="field.path" :label="field.label" :value="field.path" />
            </el-select>
          </label>
          <label><span>判断方式</span>
            <el-select :model-value="rule.filterOperator || 'EQ'" :disabled="disabled" style="width: 100%" @change="updateRule(index, {filterOperator: $event})">
              <el-option v-for="item in filterOperators" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </label>
          <label v-if="!['IS_NULL', 'NOT_NULL'].includes(rule.filterOperator)"><span>比较值</span>
            <el-switch
              v-if="filterValueType(rule) === 'boolean'"
              :model-value="!!rule.filterValue"
              :disabled="disabled"
              @change="updateRule(index, {filterValue: $event})"
            />
            <el-input v-else :model-value="displayValue(rule.filterValue, filterValueType(rule))" :disabled="disabled" @change="filterValueChanged(index, $event)" />
          </label>
        </div>

        <div v-else-if="rule.operation === 'ARRAY_FLATTEN'" class="rule-operation-options compact">
          <label><span>展开层数</span><el-input-number :model-value="rule.depth || 1" :min="1" :max="10" :disabled="disabled" @change="updateOperationOption(index, {depth: $event})" /></label>
        </div>

        <div v-else-if="rule.operation === 'ARRAY_SORT'" class="rule-operation-options">
          <label><span>排序字段</span><el-select :model-value="rule.sortPath || ''" filterable :disabled="disabled" style="width: 100%" @change="updateRule(index, {sortPath: $event})"><el-option v-for="field in arrayItemFields(rule)" :key="field.path" :label="field.label" :value="field.path" /></el-select></label>
          <label><span>方向</span><el-select :model-value="rule.sortDirection || 'ASC'" :disabled="disabled" @change="updateRule(index, {sortDirection: $event})"><el-option label="升序" value="ASC" /><el-option label="降序" value="DESC" /></el-select></label>
          <label><span>空值位置</span><el-select :model-value="rule.nulls || 'LAST'" :disabled="disabled" @change="updateRule(index, {nulls: $event})"><el-option label="最后" value="LAST" /><el-option label="最前" value="FIRST" /></el-select></label>
        </div>

        <div v-else-if="rule.operation === 'ARRAY_DISTINCT'" class="rule-operation-options compact">
          <label><span>去重依据</span><el-select :model-value="rule.distinctPath || ''" filterable :disabled="disabled" style="width: 100%" @change="updateRule(index, {distinctPath: $event})"><el-option v-for="field in arrayItemFields(rule)" :key="field.path" :label="field.label" :value="field.path" /></el-select></label>
        </div>

        <div v-else-if="rule.operation === 'ARRAY_GROUP'" class="rule-operation-options">
          <label><span>分组字段</span><el-select :model-value="rule.groupPath || ''" filterable :disabled="disabled" style="width: 100%" @change="updateOperationOption(index, {groupPath: $event})"><el-option v-for="field in arrayItemFields(rule)" :key="field.path" :label="field.label" :value="field.path" /></el-select></label>
          <label><span>每组计算（可选）</span><el-select :model-value="rule.groupAggregate || 'NONE'" :disabled="disabled" @change="updateOperationOption(index, {groupAggregate: $event})"><el-option label="只分组，不计算" value="NONE" /><el-option label="每组计数" value="COUNT" /><el-option label="每组求和" value="SUM" /><el-option label="每组平均值" value="AVG" /><el-option label="每组最小值" value="MIN" /><el-option label="每组最大值" value="MAX" /></el-select></label>
          <label v-if="!['NONE', 'COUNT'].includes(rule.groupAggregate || 'NONE')"><span>计算字段</span><el-select :model-value="rule.groupAggregatePath || ''" filterable :disabled="disabled" style="width: 100%" @change="updateOperationOption(index, {groupAggregatePath: $event})"><el-option v-for="field in numericArrayItemFields(rule)" :key="field.path" :label="field.label" :value="field.path" /></el-select></label>
        </div>

        <div v-else-if="rule.operation === 'ARRAY_AGGREGATE'" class="rule-operation-options">
          <label><span>聚合方式</span><el-select :model-value="rule.aggregate || 'COUNT'" :disabled="disabled" @change="updateOperationOption(index, {aggregate: $event})"><el-option label="计数" value="COUNT" /><el-option label="求和" value="SUM" /><el-option label="平均值" value="AVG" /><el-option label="最小值" value="MIN" /><el-option label="最大值" value="MAX" /></el-select></label>
          <label v-if="rule.aggregate !== 'COUNT'"><span>数值字段</span><el-select :model-value="rule.aggregatePath || ''" filterable :disabled="disabled" style="width: 100%" @change="updateRule(index, {aggregatePath: $event})"><el-option v-for="field in numericArrayItemFields(rule)" :key="field.path" :label="field.label" :value="field.path" /></el-select></label>
        </div>

        <div v-else-if="rule.operation === 'TEMPLATE'" class="rule-operation-options single">
          <label><span>文本模板</span><el-input type="textarea" :rows="3" :model-value="rule.template || ''" :disabled="disabled" placeholder="例如：用户 ${name}，来自 ${source2.company}" @input="updateRule(index, {template: $event})" /><small>使用 ${字段} 读取当前来源，使用 ${source2.字段} 读取辅助来源。</small></label>
        </div>

        <div v-else-if="rule.operation === 'EXPRESSION'" class="rule-operation-options expression-options">
          <label class="span-2"><span>受限表达式</span><el-input type="textarea" :rows="3" :model-value="rule.expression || ''" :disabled="disabled" placeholder="$value * 1.2 或 $source.price >= 100" @input="updateRule(index, {expression: $event})" /><small>支持字段读取、+ - * / %、比较、&&、||、! 和括号；不执行脚本或函数。</small></label>
          <label><span>结果类型</span><el-select :model-value="rule.resultType || 'string'" :disabled="disabled" @change="expressionTypeChanged(index, $event)"><el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></label>
        </div>

        <div class="rule-summary">
          <span :class="['mapping-type', `mapping-type--${ruleResultType(rule)}`]">
            {{ typeLabel(ruleResultType(rule)) }}
          </span>
          <span>{{ ruleSummary(rule) }}</span>
          <button type="button" :disabled="disabled" @click="toggleAdvanced(index)">
            {{ advancedRules.includes(index) ? '收起选项' : '默认值与异常处理' }}
            <el-icon><component :is="advancedRules.includes(index) ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
          </button>
        </div>

        <div v-if="advancedRules.includes(index)" class="rule-advanced">
          <label>
            <span>何时使用默认值</span>
            <el-select
              :model-value="rule.defaultWhen || 'NEVER'"
              :disabled="disabled"
              style="width: 100%"
              @change="updateOperationOption(index, {defaultWhen: $event})"
            >
              <el-option label="不使用" value="NEVER" />
              <el-option label="字段不存在时" value="MISSING" />
              <el-option label="值为 null 时" value="NULL" />
              <el-option label="文本为空时" value="BLANK" />
              <el-option label="null 或空文本时" value="NULL_OR_BLANK" />
            </el-select>
          </label>
          <label v-if="(rule.defaultWhen && rule.defaultWhen !== 'NEVER') || rule.onError === 'DEFAULT'">
            <span>默认值</span>
            <el-switch
              v-if="ruleResultType(rule) === 'boolean'"
              :model-value="!!rule.defaultValue"
              :disabled="disabled"
              @change="updateOperationOption(index, {defaultValue: $event})"
            />
            <el-input
              v-else
              :type="['object', 'array'].includes(ruleResultType(rule)) ? 'textarea' : 'text'"
              :rows="['object', 'array'].includes(ruleResultType(rule)) ? 3 : undefined"
              :model-value="displayValue(rule.defaultValue, ruleResultType(rule))"
              :disabled="disabled"
              placeholder="输入默认值"
              @change="defaultValueChanged(index, $event)"
            />
          </label>
          <label>
            <span>转换失败时</span>
            <el-select
              :model-value="rule.onError || 'FAIL'"
              :disabled="disabled"
              style="width: 100%"
              @change="updateOperationOption(index, {onError: $event})"
            >
              <el-option label="停止并提示具体字段" value="FAIL" />
              <el-option label="输出 null" value="NULL" />
              <el-option label="使用默认值" value="DEFAULT" />
              <el-option label="保留原值" value="KEEP" />
            </el-select>
          </label>
          <label class="rule-required">
            <span>字段要求</span>
            <el-checkbox
              :model-value="!!rule.required"
              :disabled="disabled"
              @change="updateRule(index, {required: $event})"
            >来源必须存在</el-checkbox>
          </label>
          <label v-if="rule.operation === 'ARRAY_JOIN'">
            <span>连接符</span>
            <el-input
              :model-value="rule.separator || ','"
              :disabled="disabled"
              @input="updateRule(index, {separator: $event})"
            />
          </label>
        </div>
        <p v-if="ruleIssue(rule, index)" class="transform-warning">{{ ruleIssue(rule, index) }}</p>
      </article>
    </section>

    <section class="transform-output-card">
      <div class="transform-section-title">
        <span><strong>输出结构预览</strong><small>会自动成为下游节点可选字段，无需再手写 Schema</small></span>
      </div>
      <pre>{{ outputPreview }}</pre>
    </section>

    <details class="transform-advanced">
      <summary>高级选项</summary>
      <div class="transform-advanced-content">
        <label v-if="mode !== 'VALUE'">
          <span>保留没有配置的原字段</span>
          <el-switch
            :model-value="!!config.preserveUnmapped"
            :disabled="disabled"
            @change="updateConfig({preserveUnmapped: $event})"
          />
          <small>开启后，整理结果会同时保留来源对象中的其他字段。</small>
        </label>
        <label>
          <span>任一数组最多处理</span>
          <el-input-number
            :model-value="config.maxItems || 1000"
            :min="1"
            :max="10000"
            :disabled="disabled"
            controls-position="right"
            @change="updateConfig({maxItems: $event})"
          />
          <small>顶层或任意嵌套数组超过数量时停止，避免意外处理超大数据。</small>
        </label>
        <label v-if="mode === 'ARRAY_MAP' && additionalSources.length">
          <span>多个数组如何对齐</span>
          <el-select
            :model-value="config.arrayAlignment || 'PRIMARY'"
            :disabled="disabled"
            @change="updateConfig({arrayAlignment: $event})"
          >
            <el-option label="按主要数组逐项处理" value="PRIMARY" />
            <el-option label="长度必须完全一致" value="STRICT" />
            <el-option label="处理到最短数组结束" value="SHORTEST" />
            <el-option label="处理到最长数组结束" value="LONGEST" />
            <el-option label="按相同字段关联" value="KEYED" />
          </el-select>
          <small>默认按下标对齐；严格模式可提前发现漏项。</small>
        </label>
        <label v-if="mode === 'ARRAY_MAP' && additionalSources.length && config.arrayAlignment === 'KEYED'">
          <span>数组关联字段</span>
          <el-select
            :model-value="config.alignmentPath || ''"
            filterable
            :disabled="disabled"
            placeholder="例如 id"
            @change="updateConfig({alignmentPath: $event})"
          >
            <el-option
              v-for="field in alignmentFields"
              :key="field.path"
              :label="field.path"
              :value="field.path"
            />
          </el-select>
          <small v-if="alignmentIssue" class="transform-warning">{{ alignmentIssue }}</small>
          <small v-else>主要数组和所有辅助数组都使用该字段匹配，重复值会直接提示。</small>
        </label>
      </div>
    </details>
  </section>
</template>

<script>
import {ArrowDown, ArrowUp, Connection, Delete, Operation} from '@element-plus/icons-vue'

export default {
  name: 'WorkflowTransformEditor',
  components: {ArrowDown, ArrowUp, Connection, Delete, Operation},
  props: {
    config: {type: Object, default: () => ({})},
    inputMapping: {type: Object, default: () => ({})},
    definition: {type: Object, required: true},
    selectedNode: {type: Object, required: true},
    descriptors: {type: Array, default: () => []},
    resolvedNodeSchemas: {type: Object, default: () => ({})},
    downstreamImpacts: {type: Array, default: () => []},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config', 'update:inputMapping'],
  data() {
    return {
      advancedRules: [],
      operationOptions: [
        {label: '直接使用', value: 'COPY'},
        {label: '固定值', value: 'CONSTANT'},
        {label: '转为文本', value: 'TO_STRING'},
        {label: '转为整数', value: 'TO_INTEGER'},
        {label: '转为数字', value: 'TO_NUMBER'},
        {label: '转为是/否', value: 'TO_BOOLEAN'},
        {label: '去除首尾空格', value: 'TRIM'},
        {label: '转为大写', value: 'UPPERCASE'},
        {label: '转为小写', value: 'LOWERCASE'},
        {label: '数组合并为文本', value: 'ARRAY_JOIN'},
        {label: '统计数量', value: 'ARRAY_LENGTH'},
        {label: '拼接多个字段', value: 'CONCAT'},
        {label: '格式化日期', value: 'DATE_FORMAT'},
        {label: '筛选数组', value: 'ARRAY_FILTER'},
        {label: '展开嵌套数组', value: 'ARRAY_FLATTEN'},
        {label: '排序数组', value: 'ARRAY_SORT'},
        {label: '数组去重', value: 'ARRAY_DISTINCT'},
        {label: '数组分组', value: 'ARRAY_GROUP'},
        {label: '数组聚合', value: 'ARRAY_AGGREGATE'},
        {label: '文本模板', value: 'TEMPLATE'},
        {label: '受限表达式', value: 'EXPRESSION'}
      ],
      filterOperators: [
        {label: '等于', value: 'EQ'}, {label: '不等于', value: 'NE'},
        {label: '大于', value: 'GT'}, {label: '大于等于', value: 'GTE'},
        {label: '小于', value: 'LT'}, {label: '小于等于', value: 'LTE'},
        {label: '包含文本', value: 'CONTAINS'},
        {label: '开头是', value: 'STARTS_WITH'}, {label: '结尾是', value: 'ENDS_WITH'},
        {label: '为空', value: 'IS_NULL'}, {label: '不为空', value: 'NOT_NULL'}
      ],
      valueTypes: [
        {label: '文本', value: 'string'},
        {label: '整数', value: 'integer'},
        {label: '数字', value: 'number'},
        {label: '是/否', value: 'boolean'},
        {label: '对象（JSON）', value: 'object'},
        {label: '数组（JSON）', value: 'array'},
        {label: '空值', value: 'null'}
      ]
    }
  },
  computed: {
    mode() {
      return this.config.mode || 'OBJECT_MAP'
    },
    ruleLimit() {
      return this.mode === 'VALUE' ? 1 : 200
    },
    rules() {
      return Array.isArray(this.config.rules) ? this.config.rules : []
    },
    sourceExpression() {
      return this.inputMapping?.source?.expression || ''
    },
    additionalSources() {
      return Object.entries(this.inputMapping || {})
        .filter(([key, binding]) => key !== 'source' && binding?.expression)
        .map(([key, binding]) => ({key, expression: binding.expression}))
        .sort((left, right) => left.key.localeCompare(right.key, undefined, {numeric: true}))
    },
    sourceChoices() {
      const choices = this.sourceExpression
        ? [{key: 'source', label: '主要数据'}] : []
      return choices.concat(this.additionalSources.map(source => ({
        key: source.key,
        label: this.sourceLabel(source.key)
      })))
    },
    usedSourceExpressions() {
      return new Set(Object.values(this.inputMapping || {})
        .map(binding => binding?.expression).filter(Boolean))
    },
    availableAdditionalSourceOptions() {
      return this.sourceGroups.flatMap(group => group.options)
        .filter(option => !this.usedSourceExpressions.has(option.expression))
    },
    upstreamNodeIds() {
      const parents = new Map()
      ;(this.definition.edges || []).forEach(edge => {
        if (!parents.has(edge.target)) parents.set(edge.target, [])
        parents.get(edge.target).push(edge.source)
      })
      const visited = new Set()
      const stack = [...(parents.get(this.selectedNode.id) || [])]
      while (stack.length) {
        const id = stack.pop()
        if (!id || visited.has(id)) continue
        visited.add(id)
        ;(parents.get(id) || []).forEach(parent => stack.push(parent))
      }
      return visited
    },
    sourceGroups() {
      const groups = []
      if (this.upstreamNodeIds.has('__start__') || !this.upstreamNodeIds.size) {
        groups.push({
          id: '__start__',
          label: '开始 · 流程输入',
          options: this.containerOptions(this.definition.inputs, '$.input', '流程输入')
        })
      }
      ;(this.definition.nodes || [])
        .filter(node => this.upstreamNodeIds.has(node.id))
        .forEach(node => {
          const descriptor = this.descriptors.find(item => item.type === node.type
            && item.handlerVersion === node.typeVersion)
          const schema = this.resolvedNodeSchemas[node.id]?.outputSchema
            || descriptor?.outputSchema
          groups.unshift({
            id: node.id,
            label: `${node.name}${this.directUpstreamIds.has(node.id) ? ' · 直接上游' : ' · 更早上游'}`,
            options: this.containerOptions(schema, `$.nodes.${node.id}.output`, '完整输出')
          })
        })
      return groups
    },
    directUpstreamIds() {
      return new Set((this.definition.edges || [])
        .filter(edge => edge.target === this.selectedNode.id)
        .map(edge => edge.source))
    },
    compatibleSourceGroups() {
      const expected = this.mode === 'ARRAY_MAP'
        ? 'array' : this.mode === 'OBJECT_MAP' ? 'object' : null
      return this.sourceGroups
        .map(group => ({
          ...group,
          options: expected
            ? group.options.filter(option => option.type === expected)
            : group.options
        }))
        .filter(group => group.options.length)
    },
    selectedSource() {
      return this.sourceGroups.flatMap(group => group.options)
        .find(option => option.expression === this.sourceExpression) || null
    },
    sourceHelp() {
      if (this.mode === 'ARRAY_MAP') {
        return '只显示可达上游中的数组；每项仍可以是对象、数组或基础值'
      }
      if (this.mode === 'VALUE') {
        return '可直接处理对象、数组、文本、数字或是/否值，输出不再强制包成对象'
      }
      return '只显示可达上游中的对象，技术路径由系统自动维护'
    },
    sourceItemSchema() {
      if (!this.selectedSource) return null
      return this.mode === 'ARRAY_MAP'
        ? this.selectedSource.schema?.items || {}
        : this.selectedSource.schema
    },
    availableFields() {
      return this.relativeFields(this.sourceItemSchema)
    },
    outputPreview() {
      if (this.mode === 'VALUE') {
        const rule = this.rules[0]
        if (!rule) return '<设置转换后显示结果类型>'
        if (rule.operation === 'CONSTANT') return JSON.stringify(rule.value, null, 2)
        if (['object', 'array'].includes(this.ruleResultType(rule))) {
          return JSON.stringify(this.schemaPreview(rule.resultSchema), null, 2)
        }
        return `<${this.typeLabel(this.ruleResultType(rule))}>`
      }
      const root = {}
      this.rules.forEach(rule => {
        if (!this.validTargetPath(rule.targetPath)) return
        const previewValue = rule.operation === 'CONSTANT'
          ? rule.value
          : (['object', 'array'].includes(this.ruleResultType(rule))
              ? this.schemaPreview(rule.resultSchema)
              : `<${this.typeLabel(this.ruleResultType(rule))}>`)
        this.setPreviewPath(root, this.transformPathTokens(rule.targetPath),
          previewValue)
      })
      return JSON.stringify(this.mode === 'ARRAY_MAP' ? [root] : root, null, 2)
    },
    alignmentFields() {
      const primary = this.relativeFields(this.selectedSource?.schema?.items || {})
        .filter(field => !['object', 'array'].includes(field.type))
      const auxiliaryPathSets = this.additionalSources
        .map(source => this.sourceOption(source.key)?.schema)
        .filter(schema => schema?.type === 'array')
        .map(schema => new Set(this.relativeFields(schema.items || {})
          .filter(field => !['object', 'array'].includes(field.type))
          .map(field => field.path)))
      return primary.filter(field => auxiliaryPathSets.every(paths => paths.has(field.path)))
    },
    alignmentIssue() {
      if (this.mode !== 'ARRAY_MAP' || this.config.arrayAlignment !== 'KEYED') return ''
      if (!this.alignmentFields.length) return '主要数组与辅助数组没有共同的基础字段，不能按字段关联。'
      if (this.config.alignmentPath
        && !this.alignmentFields.some(field => field.path === this.config.alignmentPath)) {
        return '原关联字段已不再被所有数组共同支持，请重新选择。'
      }
      return ''
    }
  },
  methods: {
    cloneSchema(schema) {
      return schema && typeof schema === 'object'
        ? JSON.parse(JSON.stringify(schema)) : undefined
    },
    normalizedType(type) {
      const normalized = Array.isArray(type) ? type.find(value => value !== 'null') : type
      return ['object', 'array', 'string', 'number', 'integer', 'boolean', 'null']
        .includes(normalized) ? normalized : 'object'
    },
    typeLabel(type) {
      return ({
        object: '对象', array: '数组', string: '文本', number: '数字',
        integer: '整数', boolean: '是/否', null: '空值'
      })[this.normalizedType(type)] || '对象'
    },
    containerOptions(schema, expression, label) {
      if (!schema || typeof schema !== 'object') return []
      const result = []
      const visit = (current, currentExpression, path, currentLabel, depth) => {
        const type = this.normalizedType(current?.type)
        if (this.mode === 'VALUE' || ['object', 'array'].includes(type)) {
          result.push({
            label: currentLabel,
            path,
            expression: currentExpression,
            type,
            typeLabel: this.typeLabel(type),
            schema: current
          })
        }
        if (depth >= 8) return
        Object.entries(current?.properties || {}).forEach(([key, property]) => {
          visit(property, `${currentExpression}.${key}`, path ? `${path}.${key}` : key,
            property?.title || key, depth + 1)
        })
      }
      visit(schema, expression, '', label, 0)
      return result
    },
    relativeFields(schema) {
      const result = []
      const visit = (current, path, label, depth, includeCurrent = true) => {
        if (!current || depth > 10) return
        const type = this.normalizedType(current.type)
        if (includeCurrent && path) {
          const hasObjectChildren = type === 'object' && Object.keys(current.properties || {}).length > 0
          const hasArrayChildren = type === 'array' && current.items
            && Object.keys(current.items).length > 0
          result.push({
            label,
            path,
            type,
            typeLabel: this.typeLabel(type),
            schema: current,
            leaf: !hasObjectChildren && !hasArrayChildren
          })
        }
        if (type === 'object') {
          Object.entries(current.properties || {}).forEach(([key, property]) => {
            visit(property, path ? `${path}.${key}` : key,
              property.title || key, depth + 1)
          })
        } else if (type === 'array' && current.items && Object.keys(current.items).length) {
          visit(current.items, `${path || ''}[]`, `${label || '数组'}中的每一项`, depth + 1)
        }
      }
      const rootType = this.normalizedType(schema?.type)
      if (rootType === 'array') visit(schema.items || {}, '[]', '当前数组中的每一项', 0)
      else visit(schema, '', '', 0, false)
      return result
    },
    sourceLabel(key) {
      if (key === 'source') return '主要数据'
      const number = String(key).replace(/^source/, '')
      return `辅助来源 ${number || key}`
    },
    sourceOption(key) {
      const expression = this.inputMapping?.[key]?.expression
      if (!expression) return null
      return this.sourceGroups.flatMap(group => group.options)
        .find(option => option.expression === expression) || null
    },
    sourceSchemaForKey(key) {
      if (!key || key === 'source') return this.sourceItemSchema
      const schema = this.sourceOption(key)?.schema
      if (this.mode === 'ARRAY_MAP' && schema?.type === 'array') return schema.items || {}
      return schema || null
    },
    fieldsForRule(rule) {
      return this.relativeFields(this.sourceSchemaForKey(rule?.sourceKey || 'source'))
    },
    selectedRuleField(rule) {
      const schema = this.sourceSchemaForKey(rule?.sourceKey || 'source')
      if (!rule?.sourcePath) {
        return schema ? {
          path: '', type: this.normalizedType(schema.type), schema,
          label: '整个对象 / 当前项', typeLabel: this.typeLabel(schema.type)
        } : null
      }
      return this.fieldsForRule(rule).find(field => field.path === rule.sourcePath) || null
    },
    arrayItemFields(rule) {
      const field = this.selectedRuleField(rule)
      const itemSchema = field?.schema?.type === 'array' ? field.schema.items || {} : null
      if (!itemSchema) return []
      return [{path: '', label: '数组项本身', type: this.normalizedType(itemSchema.type), schema: itemSchema}]
        .concat(this.relativeFields(itemSchema).map(item => ({...item, label: item.path})))
    },
    numericArrayItemFields(rule) {
      return this.arrayItemFields(rule)
        .filter(field => ['integer', 'number'].includes(field.type))
    },
    filterValueType(rule) {
      return this.arrayItemFields(rule)
        .find(field => field.path === (rule.filterPath || ''))?.type || 'string'
    },
    transformPathTokens(path) {
      const tokens = []
      String(path || '').split('.').filter(Boolean).forEach(segment => {
        const bracket = segment.indexOf('[')
        const field = bracket < 0 ? segment : segment.slice(0, bracket)
        if (field) tokens.push({field, each: false})
        let suffix = bracket < 0 ? '' : segment.slice(bracket)
        while (suffix.startsWith('[]')) {
          tokens.push({field: '', each: true})
          suffix = suffix.slice(2)
        }
      })
      return tokens
    },
    setPreviewPath(root, tokens, value) {
      let current = root
      tokens.forEach((token, index) => {
        const next = tokens[index + 1]
        const leaf = !next
        if (!token.each) {
          if (leaf) {
            current[token.field] = value
          } else {
            const expected = next.each ? [] : {}
            if (current[token.field] === undefined) current[token.field] = expected
            current = current[token.field]
          }
        } else {
          if (!Array.isArray(current)) return
          if (leaf) {
            current[0] = value
          } else {
            const expected = next.each ? [] : {}
            if (current[0] === undefined) current[0] = expected
            current = current[0]
          }
        }
      })
    },
    schemaPreview(schema, depth = 0) {
      if (!schema || depth > 8) return '<数据>'
      const type = this.normalizedType(schema.type)
      if (type === 'object') {
        const entries = Object.entries(schema.properties || {})
        if (!entries.length) return '<对象>'
        return Object.fromEntries(entries.map(([key, child]) =>
          [key, this.schemaPreview(child, depth + 1)]))
      }
      if (type === 'array') {
        if (!schema.items || !Object.keys(schema.items).length) return ['<数据>']
        return [this.schemaPreview(schema.items, depth + 1)]
      }
      return `<${this.typeLabel(type)}>`
    },
    async changeMode(mode) {
      if (this.disabled || mode === this.mode) return
      if (this.rules.length) {
        try {
          await this.$confirm(
            '切换整理方式需要重新配置当前转换规则。确认清空现有规则吗？',
            '切换整理方式',
            {confirmButtonText: '确认切换', cancelButtonText: '保留当前设置', type: 'warning'}
          )
        } catch (error) {
          return
        }
      }
      const expected = mode === 'ARRAY_MAP' ? 'array' : mode === 'OBJECT_MAP' ? 'object' : null
      const keepSource = !expected || this.selectedSource?.type === expected
      this.updateConfig({mode, rules: []})
      if (!keepSource) this.$emit('update:inputMapping', {})
      this.advancedRules = []
    },
    sourceChanged(expression) {
      const inputMapping = {...(this.inputMapping || {})}
      if (expression) inputMapping.source = {expression}
      else delete inputMapping.source
      this.$emit('update:inputMapping', inputMapping)
      this.$nextTick(() => this.reconcileSourceRules('source'))
    },
    reconcileSourceRules(sourceKey) {
      if (!this.rules.length) return
      const rules = this.rules.map(rule => {
        if ((rule.sourceKey || 'source') !== sourceKey || rule.operation === 'CONSTANT') {
          return {...rule}
        }
        const field = this.selectedRuleField(rule)
        const updated = {
          ...rule,
          sourceType: field?.type || this.normalizedType(this.sourceSchemaForKey(sourceKey)?.type)
        }
        if (!this.operationValuesForType(updated.sourceType).has(updated.operation)) {
          updated.operation = 'COPY'
        }
        if (field && updated.operation === 'COPY') {
          updated.resultType = field.type
          updated.resultSchema = this.cloneSchema(field.schema)
        } else if (field) {
          updated.resultType = this.operationType(updated.operation, updated)
          updated.resultSchema = this.operationSchema(updated.operation, updated)
        } else {
          delete updated.resultSchema
        }
        return updated
      })
      this.updateConfig({rules})
    },
    addAdditionalSource(expression) {
      if (!expression) return
      let index = 2
      while (this.inputMapping?.[`source${index}`]) index++
      this.$emit('update:inputMapping', {
        ...(this.inputMapping || {}),
        [`source${index}`]: {expression}
      })
    },
    additionalSourceChanged(key, expression) {
      this.$emit('update:inputMapping', {
        ...(this.inputMapping || {}),
        [key]: {expression}
      })
      this.$nextTick(() => this.reconcileSourceRules(key))
    },
    removeAdditionalSource(key) {
      if (this.rules.some(rule => (rule.sourceKey || 'source') === key)) {
        this.$message.warning(`请先把使用“${this.sourceLabel(key)}”的输出字段切换到其他来源。`)
        return
      }
      const inputMapping = {...(this.inputMapping || {})}
      delete inputMapping[key]
      this.$emit('update:inputMapping', inputMapping)
    },
    autoAddFields(scope) {
      if (scope === 'LEAVES') this.addAllLeafFields()
      else this.addAllTopLevelFields()
    },
    addAllTopLevelFields() {
      const fields = Object.entries(this.sourceItemSchema?.properties || {})
      if (!fields.length) return
      const existing = new Set(this.rules.map(rule => rule.targetPath))
      const added = fields
        .filter(([key]) => !existing.has(this.targetPathFromSource(key)))
        .map(([key, property]) => ({
          targetPath: this.targetPathFromSource(key),
          sourcePath: key,
          sourceKey: 'source',
          sourceType: this.normalizedType(property?.type),
          operation: 'COPY',
          resultType: this.normalizedType(property?.type),
          resultSchema: this.cloneSchema(property),
          defaultWhen: 'NEVER',
          onError: 'FAIL',
          required: false
        }))
      this.appendAutomaticRules(added)
    },
    addAllLeafFields() {
      const existing = new Set(this.rules.map(rule => rule.targetPath))
      const added = this.availableFields
        .filter(field => field.leaf)
        .map(field => ({...field, targetPath: this.targetPathFromSource(field.path)}))
        .filter(field => !existing.has(field.targetPath))
        .map(field => ({
          targetPath: field.targetPath,
          sourcePath: field.path,
          sourceKey: 'source',
          sourceType: field.type,
          operation: 'COPY',
          resultType: field.type,
          resultSchema: this.cloneSchema(field.schema),
          defaultWhen: 'NEVER',
          onError: 'FAIL',
          required: false
        }))
      this.appendAutomaticRules(added)
    },
    appendAutomaticRules(added) {
      const capacity = Math.max(0, 200 - this.rules.length)
      if (added.length > capacity) {
        this.$message.warning(`字段较多，本次先添加 ${capacity} 个；单个转换节点最多配置 200 个字段。`)
      }
      this.updateConfig({rules: [...this.rules, ...added.slice(0, capacity)]})
    },
    addRule(operation) {
      const source = this.mode === 'VALUE' ? null : this.availableFields.find(field =>
        !this.rules.some(rule => rule.sourcePath === field.path))
      const targetPath = operation === 'CONSTANT'
        ? this.nextTargetName() : (source ? this.targetPathFromSource(source.path) : this.nextTargetName())
      const rule = {
        targetPath: this.mode === 'VALUE' ? '$' : targetPath,
        operation,
        sourceKey: 'source',
        sourceType: operation === 'CONSTANT'
          ? 'string' : source?.type || this.normalizedType(this.sourceItemSchema?.type),
        resultType: operation === 'CONSTANT' ? 'string' : source?.type || 'object',
        resultSchema: operation === 'CONSTANT' ? {type: 'string'} : this.cloneSchema(source?.schema),
        defaultWhen: 'NEVER',
        onError: 'FAIL',
        required: false
      }
      if (operation === 'CONSTANT') rule.value = ''
      else rule.sourcePath = source?.path || ''
      this.updateConfig({rules: [...this.rules, rule]})
    },
    nextTargetName() {
      let index = 1
      const names = new Set(this.rules.map(rule => rule.targetPath))
      while (names.has(`field${index}`)) index++
      return `field${index}`
    },
    updateRule(index, patch) {
      const rules = this.rules.map((rule, current) =>
        current === index ? {...rule, ...patch} : {...rule})
      this.updateConfig({rules})
    },
    removeRule(index) {
      this.updateConfig({rules: this.rules.filter((rule, current) => current !== index)})
      this.advancedRules = this.advancedRules
        .filter(current => current !== index)
        .map(current => current > index ? current - 1 : current)
    },
    sourceFieldChanged(index, path) {
      const rule = this.rules[index] || {}
      const field = path
        ? this.fieldsForRule(rule).find(item => item.path === path)
        : this.selectedRuleField({...rule, sourcePath: ''})
      const patch = {sourcePath: path}
      const previous = this.rules[index] || {}
      if (path && (previous.targetPath === this.targetPathFromSource(previous.sourcePath)
        || /^field\d+$/.test(previous.targetPath || ''))) {
        patch.targetPath = this.targetPathFromSource(path)
      }
      patch.sourceType = field?.type || this.normalizedType(this.sourceSchemaForKey(previous.sourceKey || 'source')?.type)
      if (!this.operationValuesForType(patch.sourceType).has(previous.operation)) {
        patch.operation = 'COPY'
      }
      const operation = patch.operation || previous.operation
      if (operation === 'COPY') {
        patch.resultType = field?.type || 'object'
        patch.resultSchema = this.cloneSchema(field?.schema)
      } else {
        const updated = {...previous, ...patch, operation}
        patch.resultType = this.operationType(updated.operation, updated)
        patch.resultSchema = this.operationSchema(updated.operation, updated)
      }
      this.updateRule(index, patch)
    },
    ruleSourceChanged(index, sourceKey) {
      const rule = this.rules[index] || {}
      const fields = this.relativeFields(this.sourceSchemaForKey(sourceKey))
      const first = this.mode === 'VALUE' ? null : fields[0]
      const rootSchema = this.sourceSchemaForKey(sourceKey)
      const patch = {
        sourceKey,
        sourcePath: first?.path || '',
        sourceType: first?.type || this.normalizedType(rootSchema?.type)
      }
      if (!this.operationValuesForType(patch.sourceType).has(rule.operation)) {
        patch.operation = 'COPY'
      }
      const operation = patch.operation || rule.operation
      if (operation === 'COPY') {
        patch.resultType = first?.type || this.normalizedType(rootSchema?.type)
        patch.resultSchema = this.cloneSchema(first?.schema || rootSchema)
      } else {
        const updated = {...rule, ...patch, operation}
        patch.resultType = this.operationType(updated.operation, updated)
        patch.resultSchema = this.operationSchema(updated.operation, updated)
      }
      this.updateRule(index, patch)
    },
    operationChanged(index, operation) {
      const rule = this.rules[index] || {}
      const resultType = this.operationType(operation, rule)
      const patch = {operation, resultType}
      if (operation === 'COPY') {
        const field = this.selectedRuleField(rule)
        patch.resultSchema = this.cloneSchema(field?.schema) || {type: resultType}
      }
      if (operation === 'CONSTANT' && !Object.prototype.hasOwnProperty.call(rule, 'value')) {
        patch.value = ''
        patch.resultType = 'string'
        patch.resultSchema = {type: 'string'}
      }
      if (operation === 'CONCAT') patch.sourcePaths = rule.sourcePath ? [rule.sourcePath] : []
      if (operation === 'DATE_FORMAT') Object.assign(patch, {
        inputFormat: '', outputFormat: 'yyyy-MM-dd HH:mm:ss', timezone: 'Asia/Shanghai'
      })
      if (operation === 'ARRAY_FILTER') Object.assign(patch, {
        filterPath: '', filterOperator: 'EQ', filterValue: ''
      })
      if (operation === 'ARRAY_FLATTEN') patch.depth = 1
      if (operation === 'ARRAY_SORT') Object.assign(patch, {
        sortPath: '', sortDirection: 'ASC', nulls: 'LAST'
      })
      if (operation === 'ARRAY_DISTINCT') patch.distinctPath = ''
      if (operation === 'ARRAY_GROUP') Object.assign(patch, {
        groupPath: '', groupAggregate: 'NONE', groupAggregatePath: ''
      })
      if (operation === 'ARRAY_AGGREGATE') Object.assign(patch, {
        aggregate: 'COUNT', aggregatePath: ''
      })
      if (operation === 'TEMPLATE') patch.template = ''
      if (operation === 'EXPRESSION') Object.assign(patch, {
        expression: '$value', resultType: rule.resultType || 'string'
      })
      patch.resultType = this.operationType(operation, {...rule, ...patch})
      patch.resultSchema = this.operationSchema(operation, {...rule, ...patch})
      this.updateRule(index, patch)
    },
    operationOptionsForRule(rule) {
      const type = this.selectedRuleField(rule)?.type || rule.sourceType || 'object'
      const compatible = this.operationValuesForType(type)
      return this.operationOptions.filter(option => compatible.has(option.value))
    },
    operationValuesForType(type) {
      const common = new Set(['COPY', 'CONSTANT', 'TO_STRING', 'TEMPLATE', 'EXPRESSION'])
      if (['string', 'integer', 'number', 'boolean'].includes(type)) {
        ;['TO_INTEGER', 'TO_NUMBER', 'TO_BOOLEAN'].forEach(value => common.add(value))
      }
      if (type === 'string') {
        ;['TRIM', 'UPPERCASE', 'LOWERCASE', 'DATE_FORMAT'].forEach(value => common.add(value))
      }
      if (['integer', 'number'].includes(type)) common.add('DATE_FORMAT')
      if (type === 'object') {
        common.add('CONCAT')
        common.add('ARRAY_LENGTH')
      }
      if (type === 'array') {
        ;['ARRAY_JOIN', 'ARRAY_LENGTH', 'ARRAY_FILTER', 'ARRAY_FLATTEN', 'ARRAY_SORT',
          'ARRAY_DISTINCT', 'ARRAY_GROUP', 'ARRAY_AGGREGATE'].forEach(value => common.add(value))
      }
      return common
    },
    operationType(operation, rule) {
      if (['TO_STRING', 'TRIM', 'UPPERCASE', 'LOWERCASE', 'ARRAY_JOIN', 'CONCAT',
        'DATE_FORMAT', 'TEMPLATE'].includes(operation)) return 'string'
      if (['TO_INTEGER', 'ARRAY_LENGTH'].includes(operation)) return 'integer'
      if (operation === 'TO_NUMBER') return 'number'
      if (operation === 'ARRAY_AGGREGATE') return rule.aggregate === 'COUNT' ? 'integer' : 'number'
      if (operation === 'TO_BOOLEAN') return 'boolean'
      if (['ARRAY_FILTER', 'ARRAY_FLATTEN', 'ARRAY_SORT', 'ARRAY_DISTINCT',
        'ARRAY_GROUP'].includes(operation)) return 'array'
      if (operation === 'EXPRESSION') return rule.resultType || 'string'
      if (operation === 'CONSTANT') return rule.resultType || 'string'
      return this.selectedRuleField(rule)?.type
        || rule.resultType || 'object'
    },
    operationSchema(operation, rule) {
      const sourceSchema = this.cloneSchema(this.selectedRuleField(rule)?.schema)
      let schema
      if (['COPY', 'ARRAY_FILTER', 'ARRAY_SORT', 'ARRAY_DISTINCT'].includes(operation)) {
        schema = sourceSchema || {type: this.operationType(operation, rule)}
      } else if (operation === 'ARRAY_FLATTEN') {
        const schema = sourceSchema || {type: 'array', items: {}}
        let items = schema.items || {}
        for (let depth = 0; depth < (rule.depth || 1) && items?.type === 'array'; depth++) {
          items = items.items || {}
        }
        return this.schemaWithFallback(
          {type: 'array', items: this.cloneSchema(items) || {}}, rule)
      } else if (operation === 'ARRAY_GROUP') {
        const key = this.arrayItemFields(rule).find(field => field.path === (rule.groupPath || ''))
        const properties = {
          key: this.cloneSchema(key?.schema) || {},
          items: sourceSchema || {type: 'array', items: {}}
        }
        const required = ['key', 'items']
        if ((rule.groupAggregate || 'NONE') !== 'NONE') {
          properties.value = {
            type: rule.groupAggregate === 'COUNT' ? 'integer' : ['number', 'null']
          }
          required.push('value')
        }
        schema = {
          type: 'array',
          items: {
            type: 'object',
            properties,
            required,
            additionalProperties: false
          }
        }
      } else {
        const type = this.operationType(operation, rule)
        schema = {
          type: operation === 'ARRAY_AGGREGATE' && rule.aggregate !== 'COUNT'
            ? [type, 'null'] : type
        }
      }
      return this.schemaWithFallback(schema, rule)
    },
    schemaWithFallback(schema, rule) {
      const result = this.cloneSchema(schema) || {}
      const nullable = rule.onError === 'NULL'
        || (rule.onError === 'DEFAULT' && rule.defaultValue == null)
        || ((rule.defaultWhen || 'NEVER') !== 'NEVER' && rule.defaultValue == null)
        || (rule.targetPath === '$' && !rule.required && rule.defaultWhen !== 'MISSING')
      if (nullable) this.appendSchemaType(result, 'null')
      if (rule.onError === 'KEEP') {
        this.appendSchemaType(result, rule.sourceType || this.selectedRuleField(rule)?.type || 'object')
        if (Array.isArray(result.type)) {
          delete result.properties
          delete result.required
          delete result.items
          delete result.additionalProperties
        }
      }
      return result
    },
    appendSchemaType(schema, type) {
      const values = Array.isArray(schema.type) ? [...schema.type] : [schema.type || type]
      if (!values.includes(type)) values.push(type)
      schema.type = values.length === 1 ? values[0] : values
    },
    updateOperationOption(index, patch) {
      const rule = {...(this.rules[index] || {}), ...patch}
      rule.resultType = this.operationType(rule.operation, rule)
      rule.resultSchema = this.operationSchema(rule.operation, rule)
      this.updateRule(index, {...patch, resultType: rule.resultType, resultSchema: rule.resultSchema})
    },
    filterValueChanged(index, text) {
      const type = this.filterValueType(this.rules[index] || {})
      const parsed = this.parseTypedValue(text, type)
      if (!parsed.valid) {
        this.$message.warning(parsed.message)
        return
      }
      this.updateRule(index, {filterValue: parsed.value})
    },
    expressionTypeChanged(index, resultType) {
      this.updateRule(index, {resultType, resultSchema: {type: resultType}})
    },
    ruleResultType(rule) {
      return this.operationType(rule.operation || 'COPY', rule)
    },
    constantTypeChanged(index, resultType) {
      const initial = ({
        string: '', integer: 0, number: 0, boolean: false,
        object: {}, array: [], null: null
      })[resultType]
      this.updateRule(index, {resultType, resultSchema: this.inferValueSchema(initial), value: initial})
    },
    constantValueChanged(index, text) {
      const type = this.rules[index]?.resultType || 'string'
      const parsed = this.parseTypedValue(text, type)
      if (!parsed.valid) {
        this.$message.warning(parsed.message)
        return
      }
      this.updateRule(index, {value: parsed.value, resultSchema: this.inferValueSchema(parsed.value)})
    },
    defaultValueChanged(index, text) {
      const parsed = this.parseTypedValue(text, this.ruleResultType(this.rules[index]))
      if (!parsed.valid) {
        this.$message.warning(parsed.message)
        return
      }
      this.updateOperationOption(index, {defaultValue: parsed.value})
    },
    parseTypedValue(text, type) {
      try {
        if (type === 'string') return {valid: true, value: String(text)}
        if (type === 'integer') {
          const value = Number(text)
          if (!Number.isInteger(value)) throw new Error('请输入整数')
          return {valid: true, value}
        }
        if (type === 'number') {
          const value = Number(text)
          if (!Number.isFinite(value)) throw new Error('请输入数字')
          return {valid: true, value}
        }
        if (type === 'null') return {valid: true, value: null}
        if (['object', 'array'].includes(type)) {
          const value = JSON.parse(text)
          if (type === 'array' ? !Array.isArray(value) : !value || Array.isArray(value) || typeof value !== 'object') {
            throw new Error(type === 'array' ? '请输入 JSON 数组' : '请输入 JSON 对象')
          }
          return {valid: true, value}
        }
        const normalized = String(text).trim().toLowerCase()
        if (!['true', 'false'].includes(normalized)) throw new Error('请选择是或否')
        return {valid: true, value: normalized === 'true'}
      } catch (error) {
        return {valid: false, message: error.message}
      }
    },
    inferValueSchema(value, depth = 0) {
      if (value === null) return {type: 'null'}
      if (Array.isArray(value)) {
        return {
          type: 'array',
          items: value.length && depth < 10 ? this.inferValueSchema(value[0], depth + 1) : {}
        }
      }
      if (typeof value === 'object') {
        if (depth >= 10) return {type: 'object'}
        const properties = Object.fromEntries(Object.entries(value)
          .map(([key, child]) => [key, this.inferValueSchema(child, depth + 1)]))
        return {
          type: 'object', properties, required: Object.keys(properties), additionalProperties: false
        }
      }
      if (typeof value === 'number') return {type: Number.isInteger(value) ? 'integer' : 'number'}
      return {type: typeof value === 'boolean' ? 'boolean' : 'string'}
    },
    displayValue(value, type) {
      if (value === undefined) return ''
      if (['object', 'array'].includes(type)) return JSON.stringify(value)
      if (value === null) return 'null'
      return String(value)
    },
    constantPlaceholder(type) {
      if (type === 'object') return '例如 {"name":"张三"}'
      if (type === 'array') return '例如 [1,2,3]'
      if (type === 'null') return '固定为空值'
      return '输入固定值'
    },
    toggleAdvanced(index) {
      this.advancedRules = this.advancedRules.includes(index)
        ? this.advancedRules.filter(value => value !== index)
        : [...this.advancedRules, index]
    },
    ruleSummary(rule) {
      if (rule.operation === 'CONSTANT') return '使用固定值'
      const operation = this.operationOptions.find(option => option.value === rule.operation)?.label || '直接使用'
      return `${rule.sourcePath || '整个对象 / 当前项'} · ${operation}`
    },
    validTargetPath(path) {
      if (this.mode === 'VALUE') return path === '$'
      return /^(?:[\p{L}_][\p{L}\p{N}_-]*(?:\[\])*(?:\.[\p{L}_][\p{L}\p{N}_-]*(?:\[\])*){0,9})$/u.test(path || '')
    },
    targetPathFromSource(path) {
      const source = String(path || '')
      if (source.startsWith('[]')) return `values${source}`
      return source.split('.').map(segment => {
        const suffix = (segment.match(/(?:\[\])+$/) || [''])[0]
        let field = (suffix ? segment.slice(0, -suffix.length) : segment)
          .replace(/[^\p{L}\p{N}_-]/gu, '_')
        if (!/^[\p{L}_]/u.test(field)) field = `_${field}`
        return `${field}${suffix}`
      }).join('.')
    },
    transformPathsConflict(left, right) {
      const leftTokens = this.transformPathTokens(left)
      const rightTokens = this.transformPathTokens(right)
      const maximum = Math.min(leftTokens.length, rightTokens.length)
      for (let index = 0; index < maximum; index++) {
        const leftToken = leftTokens[index]
        const rightToken = rightTokens[index]
        if (leftToken.each === rightToken.each && leftToken.field === rightToken.field) continue
        return leftToken.each !== rightToken.each
      }
      return true
    },
    ruleIssue(rule, index) {
      if (!this.validTargetPath(rule.targetPath)) {
        return '输出字段可使用中文、英文、数字、短横线和下划线；对象层级显示为“.”，数组每一项显示为“[]”。'
      }
      if (this.rules.some((item, current) => current !== index
        && this.transformPathsConflict(item.targetPath, rule.targetPath))) {
        return '输出字段层级冲突，请保留父字段或具体子字段中的一种。'
      }
      if (rule.operation !== 'CONSTANT' && rule.sourcePath
        && !this.fieldsForRule(rule).some(field => field.path === rule.sourcePath)) {
        return '来源字段已失效，请重新选择。'
      }
      if (rule.operation !== 'CONSTANT' && !this.sourceOption(rule.sourceKey || 'source')) {
        return '数据来源已失效，请重新选择。'
      }
      const sourceType = this.selectedRuleField(rule)?.type || rule.sourceType || 'object'
      if (!this.operationValuesForType(sourceType).has(rule.operation)) {
        return '当前处理方式不适用于这个字段类型，请重新选择处理方式。'
      }
      const sourceArrays = (String(rule.sourcePath || '').match(/\[\]/g) || []).length
      const targetArrays = (String(rule.targetPath || '').match(/\[\]/g) || []).length
      if (this.mode !== 'VALUE' && rule.operation === 'CONSTANT' && targetArrays) return '固定值不能直接配置到数组每一项。'
      if (this.mode !== 'VALUE' && rule.operation !== 'CONSTANT' && sourceArrays !== targetArrays) {
        return '来源字段和输出字段的数组层级需要保持一致。'
      }
      const arrayOperations = ['ARRAY_FILTER', 'ARRAY_FLATTEN', 'ARRAY_SORT',
        'ARRAY_DISTINCT', 'ARRAY_GROUP', 'ARRAY_AGGREGATE']
      if (arrayOperations.includes(rule.operation) && this.selectedRuleField(rule)?.type !== 'array') {
        return '当前处理方式要求来源字段是数组。'
      }
      if (rule.operation === 'ARRAY_AGGREGATE' && rule.aggregate !== 'COUNT'
        && !this.numericArrayItemFields(rule).some(field => field.path === (rule.aggregatePath || ''))) {
        return '请选择数组中的数值字段。'
      }
      if (rule.operation === 'ARRAY_GROUP' && !['NONE', 'COUNT'].includes(rule.groupAggregate || 'NONE')
        && !this.numericArrayItemFields(rule).some(field => field.path === (rule.groupAggregatePath || ''))) {
        return '请选择每组需要计算的数值字段。'
      }
      if (rule.operation === 'DATE_FORMAT' && !rule.outputFormat) return '请填写输出日期格式。'
      if (rule.operation === 'TEMPLATE' && !rule.template) return '请填写文本模板。'
      if (rule.operation === 'EXPRESSION' && !rule.expression) return '请填写受限表达式。'
      return ''
    },
    updateConfig(patch) {
      this.$emit('update:config', {
        version: 1,
        mode: this.mode,
        rules: this.rules,
        preserveUnmapped: false,
        maxItems: 1000,
        ...this.config,
        ...patch
      })
    }
  }
}
</script>

<style scoped>
.transform-editor {
  display: grid;
  gap: 14px;
}

.transform-heading,
.transform-section-title,
.transform-rules-heading,
.rule-summary,
.transform-actions,
.source-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.transform-heading > div,
.transform-section-title > span {
  display: grid;
  gap: 4px;
}

.transform-heading strong {
  color: var(--workflow-text-strong);
  font-size: 16px;
}

.transform-heading small,
.transform-section-title small,
.source-option small,
.transform-empty small,
.transform-advanced-content small {
  color: var(--workflow-text-muted);
  font-size: 12px;
}

.transform-mode {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.transform-mode button {
  display: grid;
  gap: 5px;
  padding: 13px 15px;
  color: var(--workflow-text);
  text-align: left;
  background: var(--workflow-surface);
  border: 1px solid var(--workflow-border);
  border-radius: 10px;
  cursor: pointer;
}

.transform-mode button.active {
  color: var(--workflow-primary);
  background: var(--workflow-primary-soft);
  border-color: var(--workflow-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--workflow-primary) 10%, transparent);
}

.transform-mode button small {
  color: var(--workflow-text-muted);
}

.transform-source-card,
.transform-rules-card,
.transform-output-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: var(--workflow-surface);
  border: 1px solid var(--workflow-border);
  border-radius: 12px;
}

.transform-section-title strong {
  color: var(--workflow-text-strong);
  font-size: 14px;
}

.source-option {
  width: 100%;
}

.source-option small {
  overflow: hidden;
  max-width: 52%;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.additional-source-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) 32px;
  gap: 8px;
  align-items: center;
}

.additional-source-row > span {
  color: var(--workflow-text-muted);
  font-size: 12px;
}

.add-source-button {
  justify-self: start;
  padding-left: 0;
}

.transform-empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 30px 16px;
  color: var(--workflow-text-muted);
  background: var(--workflow-surface-subtle);
  border: 1px dashed var(--workflow-border-strong);
  border-radius: 10px;
}

.transform-empty .el-icon {
  font-size: 24px;
}

.transform-rule {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 14px 54px 14px 14px;
  background: var(--workflow-surface-subtle);
  border: 1px solid var(--workflow-border);
  border-radius: 10px;
}

.rule-main-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(145px, 1fr));
  gap: 10px;
  align-items: end;
}

.rule-main-grid label,
.rule-constant-grid label,
.rule-operation-options label,
.rule-advanced label,
.transform-advanced-content label {
  display: grid;
  gap: 6px;
  color: var(--workflow-text-muted);
  font-size: 12px;
}

.rule-delete {
  position: absolute;
  top: 37px;
  right: 12px;
  width: 32px;
  height: 32px;
  margin-bottom: 1px;
}

.rule-constant-grid,
.rule-advanced {
  display: grid;
  grid-template-columns: minmax(140px, .55fr) minmax(220px, 1fr);
  gap: 10px;
}

.rule-operation-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(140px, 1fr));
  gap: 10px;
  padding: 12px;
  background: var(--workflow-surface);
  border: 1px solid var(--workflow-border);
  border-radius: 8px;
}

.rule-operation-options.compact {
  grid-template-columns: minmax(180px, 420px);
}

.rule-operation-options.single {
  grid-template-columns: 1fr;
}

.rule-operation-options .span-2 {
  grid-column: span 2;
}

.rule-operation-options small {
  color: var(--workflow-text-muted);
  line-height: 1.5;
}

.rule-summary {
  justify-content: flex-start;
  color: var(--workflow-text-muted);
  font-size: 12px;
}

.rule-summary button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  color: var(--workflow-primary);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.mapping-type {
  padding: 2px 8px;
  color: #535bd7;
  font-style: normal;
  background: #eef0ff;
  border-radius: 999px;
}

.mapping-type--string { color: #087f5b; background: #e8f8ef; }
.mapping-type--integer,
.mapping-type--number { color: #ad6200; background: #fff3d8; }
.mapping-type--boolean { color: #a33b61; background: #ffe9f1; }

.transform-warning {
  margin: 0;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
}

.transform-output-card pre {
  min-height: 76px;
  max-height: 220px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: var(--workflow-text);
  background: var(--workflow-surface-subtle);
  border-radius: 8px;
}

.transform-advanced {
  background: var(--workflow-surface);
  border: 1px solid var(--workflow-border);
  border-radius: 10px;
}

.transform-advanced summary {
  padding: 13px 15px;
  color: var(--workflow-text-strong);
  font-weight: 600;
  cursor: pointer;
}

.transform-advanced-content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 0 15px 15px;
}

@media (max-width: 900px) {
  .rule-main-grid,
  .rule-constant-grid,
  .rule-operation-options,
  .rule-advanced,
  .transform-advanced-content {
    grid-template-columns: 1fr;
  }

  .rule-delete {
    top: 37px;
  }

  .rule-operation-options .span-2 { grid-column: auto; }
}
</style>
