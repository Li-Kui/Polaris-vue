<template>
    <div>
        <el-tabs type="border-card">
            <el-tab-pane label="秒" v-if="shouldHide('second')">
                <CrontabSecond
                    @update="updateCrontabValue"
                    :check="checkNumber"
                    :cron="crontabValueObj"
                    ref="cronsecond"
                />
            </el-tab-pane>

            <el-tab-pane label="分钟" v-if="shouldHide('min')">
                <CrontabMin
                    @update="updateCrontabValue"
                    :check="checkNumber"
                    :cron="crontabValueObj"
                    ref="cronmin"
                />
            </el-tab-pane>

            <el-tab-pane label="小时" v-if="shouldHide('hour')">
                <CrontabHour
                    @update="updateCrontabValue"
                    :check="checkNumber"
                    :cron="crontabValueObj"
                    ref="cronhour"
                />
            </el-tab-pane>

            <el-tab-pane label="日" v-if="shouldHide('day')">
                <CrontabDay
                    @update="updateCrontabValue"
                    :check="checkNumber"
                    :cron="crontabValueObj"
                    ref="cronday"
                />
            </el-tab-pane>

            <el-tab-pane label="月" v-if="shouldHide('month')">
                <CrontabMonth
                    @update="updateCrontabValue"
                    :check="checkNumber"
                    :cron="crontabValueObj"
                    ref="cronmonth"
                />
            </el-tab-pane>

            <el-tab-pane label="周" v-if="shouldHide('week')">
                <CrontabWeek
                    @update="updateCrontabValue"
                    :check="checkNumber"
                    :cron="crontabValueObj"
                    ref="cronweek"
                />
            </el-tab-pane>

            <el-tab-pane label="年" v-if="shouldHide('year')">
                <CrontabYear
                    @update="updateCrontabValue"
                    :check="checkNumber"
                    :cron="crontabValueObj"
                    ref="cronyear"
                />
            </el-tab-pane>
        </el-tabs>

        <div class="popup-main">
            <div class="popup-result">
                <p class="title">时间表达式</p>
                <table>
                    <thead>
                        <tr>
                            <th v-for="item of tabTitles" :key="item">{{item}}</th>
                            <th>Cron 表达式</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>
                                <span v-if="crontabValueObj.second.length < 10">{{crontabValueObj.second}}</span>
                                <el-tooltip v-else :content="crontabValueObj.second" placement="top"><span>{{crontabValueObj.second}}</span></el-tooltip>
                            </td>
                            <td>
                                <span v-if="crontabValueObj.min.length < 10">{{crontabValueObj.min}}</span>
                                <el-tooltip v-else :content="crontabValueObj.min" placement="top"><span>{{crontabValueObj.min}}</span></el-tooltip>
                            </td>
                            <td>
                                <span v-if="crontabValueObj.hour.length < 10">{{crontabValueObj.hour}}</span>
                                <el-tooltip v-else :content="crontabValueObj.hour" placement="top"><span>{{crontabValueObj.hour}}</span></el-tooltip>
                            </td>
                            <td>
                                <span v-if="crontabValueObj.day.length < 10">{{crontabValueObj.day}}</span>
                                <el-tooltip v-else :content="crontabValueObj.day" placement="top"><span>{{crontabValueObj.day}}</span></el-tooltip>
                            </td>
                            <td>
                                <span v-if="crontabValueObj.month.length < 10">{{crontabValueObj.month}}</span>
                                <el-tooltip v-else :content="crontabValueObj.month" placement="top"><span>{{crontabValueObj.month}}</span></el-tooltip>
                            </td>
                            <td>
                                <span v-if="crontabValueObj.week.length < 10">{{crontabValueObj.week}}</span>
                                <el-tooltip v-else :content="crontabValueObj.week" placement="top"><span>{{crontabValueObj.week}}</span></el-tooltip>
                            </td>
                            <td>
                                <span v-if="crontabValueObj.year.length < 10">{{crontabValueObj.year}}</span>
                                <el-tooltip v-else :content="crontabValueObj.year" placement="top"><span>{{crontabValueObj.year}}</span></el-tooltip>
                            </td>
                            <td class="result">
                                <span v-if="crontabValueString.length < 90">{{crontabValueString}}</span>
                                <el-tooltip v-else :content="crontabValueString" placement="top"><span>{{crontabValueString}}</span></el-tooltip>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <CrontabResult :ex="crontabValueString"></CrontabResult>

            <div class="pop_btn">
                <el-button type="primary" @click="submitFill">确定</el-button>
                <el-button type="warning" @click="clearCron">重置</el-button>
                <el-button @click="hidePopup">取消</el-button>
            </div>
        </div>
    </div>
</template>

<script setup>
import CrontabSecond from "./second.vue"
import CrontabMin from "./min.vue"
import CrontabHour from "./hour.vue"
import CrontabDay from "./day.vue"
import CrontabMonth from "./month.vue"
import CrontabWeek from "./week.vue"
import CrontabYear from "./year.vue"
import CrontabResult from "./result.vue"

const { proxy } = getCurrentInstance()
const emit = defineEmits(['hide', 'fill'])
const props = defineProps({
    hideComponent: {
        type: Array,
        default: () => [],
    },
    expression: {
        type: String,
        default: ""
    }
})
const tabTitles = ref(["秒", "分钟", "小时", "日", "月", "周", "年"])
const tabActive = ref(0)
const hideComponent = ref([])
const expression = ref('')
const crontabValueObj = ref({
    second: "*",
    min: "*",
    hour: "*",
    day: "*",
    month: "*",
    week: "?",
    year: "",
})
const crontabValueString = computed(() => {
    const obj = crontabValueObj.value
    return obj.second
        + " "
        + obj.min
        + " "
        + obj.hour
        + " "
        + obj.day
        + " "
        + obj.month
        + " "
        + obj.week
        + (obj.year === "" ? "" : " " + obj.year)
})
watch(expression, () => resolveExp())
function shouldHide(key) {
    return !(hideComponent.value && hideComponent.value.includes(key))
}
function resolveExp() {
    // 反解析 表达式
    if (expression.value) {
        const arr = expression.value.split(/\s+/)
        if (arr.length >= 6) {
            //6 位以上是合法表达式
            let obj = {
                second: arr[0],
                min: arr[1],
                hour: arr[2],
                day: arr[3],
                month: arr[4],
                week: arr[5],
                year: arr[6] ? arr[6] : ""
            }
            crontabValueObj.value = {
                ...obj,
            }
        }
    } else {
        // 没有传入的表达式 则还原
        clearCron()
    }
}
// tab切换值
function tabCheck(index) {
    tabActive.value = index
}
// 由子组件触发，更改表达式组成的字段值
function updateCrontabValue(name, value, from) {
    crontabValueObj.value[name] = value
}
// 表单选项的子组件校验数字格式（通过-props传递）
function checkNumber(value, minLimit, maxLimit) {
    // 检查必须为整数
    value = Math.floor(value)
    if (value < minLimit) {
        value = minLimit
    } else if (value > maxLimit) {
        value = maxLimit
    }
    return value
}
// 隐藏弹窗
function hidePopup() {
    emit("hide")
}
// 填充表达式
function submitFill() {
    emit("fill", crontabValueString.value)
    hidePopup()
}
function clearCron() {
    // 还原选择项
    crontabValueObj.value = {
        second: "*",
        min: "*",
        hour: "*",
        day: "*",
        month: "*",
        week: "?",
        year: "",
    }
}
onMounted(() => {
    expression.value = props.expression
    hideComponent.value = props.hideComponent
})
</script>

<style lang="scss" scoped>
.pop_btn {
    text-align: center;
    margin-top: 24px;
    display: flex;
    justify-content: flex-end;
    gap: 12px;
}
.popup-main {
    position: relative;
    margin: 16px auto 0;
    border-radius: 8px;
    font-size: 12px;
    overflow: hidden;
}
.popup-title {
    overflow: hidden;
    line-height: 34px;
    padding-top: 6px;
    background: transparent;
}
.popup-result {
    box-sizing: border-box;
    line-height: 24px;
    margin: 32px auto 16px;
    padding: 20px 16px 16px;
    border: 1px solid rgba(0, 0, 0, 0.06);
    background-color: rgba(0, 0, 0, 0.02);
    border-radius: 12px;
    position: relative;
    backdrop-filter: blur(4px);

    .dark & {
        border-color: rgba(255, 255, 255, 0.08);
        background-color: rgba(255, 255, 255, 0.02);
    }
}
.popup-result .title {
    position: absolute;
    top: -15px;
    left: 20px;
    width: auto;
    font-size: 12px;
    font-weight: 700;
    padding: 2px 10px;
    text-align: center;
    line-height: 20px;
    background: rgba(79, 70, 229, 0.1);
    color: #4f46e5;
    border-radius: 6px;
    border: 1px solid rgba(79, 70, 229, 0.15);

    .dark & {
        background: rgba(56, 189, 248, 0.15);
        color: #38bdf8;
        border-color: rgba(56, 189, 248, 0.2);
    }
}
.popup-result table {
    text-align: center;
    width: 100%;
    margin: 0 auto;
    border-collapse: separate;
    border-spacing: 4px 6px;
}
.popup-result table th {
    font-weight: 600;
    color: #64748b;
    font-size: 11px;
    padding-bottom: 4px;
    .dark & {
        color: #94a3b8;
    }
}
.popup-result table td:not(.result) {
    width: 4rem;
    min-width: 4rem;
    max-width: 4rem;
}
.popup-result table span {
    display: block;
    width: 100%;
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
    line-height: 28px;
    height: 28px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    border: 1px solid rgba(0, 0, 0, 0.06);
    background-color: rgba(255, 255, 255, 0.6);
    border-radius: 6px;
    color: #334155;
    transition: all 0.2s ease;

    .dark & {
        border-color: rgba(255, 255, 255, 0.06);
        background-color: rgba(15, 23, 42, 0.4);
        color: #cbd5e1;
    }
}
.popup-result table td.result span {
    border-color: rgba(79, 70, 229, 0.25);
    background-color: rgba(79, 70, 229, 0.05);
    color: #4f46e5;
    font-weight: 700;

    .dark & {
        border-color: rgba(56, 189, 248, 0.3);
        background-color: rgba(56, 189, 248, 0.1);
        color: #38bdf8;
    }
}
.popup-result-scroll {
    font-size: 12px;
    line-height: 24px;
    height: 10em;
    overflow-y: auto;
}
</style>

<style lang="scss">
/* 针对 Cron 表达式生成器的全局组件样式重塑 */
.polaris-glass-dialog {
  /* 隐藏 Cron 生成器弹窗里 tabs 组件多余的边框和生硬白卡底色 */
  .el-tabs--border-card {
    background: transparent !important;
    border: none !important;
    box-shadow: none !important;
    
    .el-tabs__header {
      background: rgba(0, 0, 0, 0.02) !important;
      border-bottom: 1px solid rgba(0, 0, 0, 0.05) !important;
      border-radius: 8px 8px 0 0;
      
      .dark & {
        background: rgba(255, 255, 255, 0.02) !important;
        border-bottom-color: rgba(255, 255, 255, 0.05) !important;
      }
    }
    
    .el-tabs__item {
      border: none !important;
      color: #64748b !important;
      font-weight: 600;
      transition: all 0.25s ease;
      font-size: 13px !important;
      padding: 0 16px !important;
      
      &.is-active {
        background-color: rgba(79, 70, 229, 0.08) !important;
        color: #4f46e5 !important;
        border-radius: 6px;
        
        .dark & {
          background-color: rgba(56, 189, 248, 0.12) !important;
          color: #38bdf8 !important;
        }
      }
      
      &:hover:not(.is-active) {
        color: #0f172a !important;
        .dark & {
          color: #f1f5f9 !important;
        }
      }
    }
    
    .el-tabs__content {
      padding: 16px 8px !important;
      background: transparent !important;
    }
  }

  /* 对齐 tabpane 内部的单选行与其数字输入框 */
  .el-tab-pane {
    .el-form-item {
      margin-bottom: 14px !important;
    }
    
    .el-form-item__content {
      align-items: center !important;
      
      .el-radio {
        display: inline-flex !important;
        align-items: center !important;
        height: auto !important;
        white-space: normal !important;
        line-height: 1.8 !important;
        margin-right: 0 !important;
        width: 100%;
        
        .el-radio__label {
          display: inline-flex !important;
          align-items: center !important;
          flex-wrap: wrap !important;
          gap: 6px !important;
          padding-left: 10px !important;
          color: #334155;
          font-weight: 500;
          font-size: 13px !important;
          
          .dark & {
            color: #cbd5e1;
          }
        }
      }
      
      /* 数字微调框与多选下拉框的样式规范 */
      .el-input-number {
        margin: 0 4px !important;
        width: 110px !important;
        
        .el-input__wrapper {
          padding-left: 28px !important;
          padding-right: 28px !important;
        }
      }
      
      .el-select {
        margin: 0 4px !important;
        width: 240px !important;
      }
    }
  }
}
</style>