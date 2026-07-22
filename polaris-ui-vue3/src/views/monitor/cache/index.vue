<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <el-row :gutter="16">
        <!-- 基本信息 -->
        <el-col :span="24" class="card-box">
          <div class="polaris-table-card">
            <div class="card-header-custom">
              <div class="card-header-title">
                <el-icon class="header-icon"><Monitor /></el-icon>
                <span>基本信息</span>
              </div>
            </div>
            <div class="polaris-el-table">
              <table cellspacing="0" style="width: 100%">
                <tbody>
                  <tr>
                    <td class="el-table__cell is-leaf"><div class="cell">Redis版本</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.redis_version }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">运行模式</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.redis_mode == "standalone" ? "单机" : "集群" }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">端口</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.tcp_port }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">客户端数</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.connected_clients }}</div></td>
                  </tr>
                  <tr>
                    <td class="el-table__cell is-leaf"><div class="cell">运行时间(天)</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.uptime_in_days }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">使用内存</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.used_memory_human }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">使用CPU</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ parseFloat(cache.info.used_cpu_user_children).toFixed(2) }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">内存配置</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.maxmemory_human }}</div></td>
                  </tr>
                  <tr>
                    <td class="el-table__cell is-leaf"><div class="cell">AOF是否开启</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.aof_enabled == "0" ? "否" : "是" }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">RDB是否成功</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.rdb_last_bgsave_status }}</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">Key数量</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.dbSize">{{ cache.dbSize }} </div></td>
                    <td class="el-table__cell is-leaf"><div class="cell">网络入口/出口</div></td>
                    <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.instantaneous_input_kbps }}kps/{{cache.info.instantaneous_output_kbps}}kps</div></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </el-col>

        <!-- 命令统计 -->
        <el-col :span="12" class="card-box">
          <div class="polaris-table-card">
            <div class="card-header-custom">
              <div class="card-header-title">
                <el-icon class="header-icon"><PieChart /></el-icon>
                <span>命令统计</span>
              </div>
            </div>
            <div class="chart-container">
              <div ref="commandstats" class="chart-div" />
            </div>
          </div>
        </el-col>

        <!-- 内存信息 -->
        <el-col :span="12" class="card-box">
          <div class="polaris-table-card">
            <div class="card-header-custom">
              <div class="card-header-title">
                <el-icon class="header-icon"><Odometer /></el-icon>
                <span>内存信息</span>
              </div>
            </div>
            <div class="chart-container">
              <div ref="usedmemory" class="chart-div" />
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup name="Cache">
import {getCache} from '@/api/monitor/cache'
import * as echarts from 'echarts'
import {getCurrentInstance, ref} from 'vue'

const cache = ref([])
const commandstats = ref(null)
const usedmemory = ref(null)
const { proxy } = getCurrentInstance()

function getList() {
  proxy.$modal.loading("正在加载缓存监控数据，请稍候！")
  getCache().then(response => {
    proxy.$modal.closeLoading()
    cache.value = response.data

    const commandstatsIntance = echarts.init(commandstats.value, "macarons")
    commandstatsIntance.setOption({
      tooltip: {
        trigger: "item",
        formatter: "{a} <br/>{b} : {c} ({d}%)"
      },
      series: [
        {
          name: "命令",
          type: "pie",
          roseType: "radius",
          radius: [15, 95],
          center: ["50%", "38%"],
          data: response.data.commandStats,
          animationEasing: "cubicInOut",
          animationDuration: 1000
        }
      ]
    })
    const usedmemoryInstance = echarts.init(usedmemory.value, "macarons")
    usedmemoryInstance.setOption({
      tooltip: {
        formatter: "{b} <br/>{a} : " + cache.value.info.used_memory_human
      },
      series: [
        {
          name: "峰值",
          type: "gauge",
          min: 0,
          max: 1000,
          detail: {
            formatter: cache.value.info.used_memory_human
          },
          data: [
            {
              value: parseFloat(cache.value.info.used_memory_human),
              name: "内存消耗"
            }
          ]
        }
      ]
    })
    window.addEventListener("resize", () => {
      commandstatsIntance.resize()
      usedmemoryInstance.resize()
    })
  })
}

getList()
</script>

<style lang="scss" scoped>
/* 覆盖全局 .app-container 的 padding: 20px */
.app-container.no-sidebar-manage-wrap {
  padding: 16px !important;
}

.content-inner {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.card-box {
  margin-bottom: 16px;
}

.card-header-custom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  margin-bottom: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  
  .dark & {
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  }
}

.card-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  
  .dark & {
    color: #f1f5f9;
  }
  
  .header-icon {
    font-size: 16px;
    color: #4f46e5;
    
    .dark & {
      color: #38bdf8;
    }
  }
}

.chart-container {
  width: 100%;
  overflow: hidden;
}

.chart-div {
  width: 100%;
  height: 420px;
}
</style>
