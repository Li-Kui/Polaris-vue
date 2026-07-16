<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <el-row :gutter="16">
        <!-- 缓存列表 -->
        <el-col :span="8" class="card-box">
          <div class="polaris-table-card list-card">
            <div class="card-header-custom">
              <div class="card-header-title">
                <el-icon class="header-icon"><Collection /></el-icon>
                <span>缓存列表</span>
              </div>
              <el-button
                class="refresh-btn-header"
                link
                type="primary"
                icon="Refresh"
                @click="refreshCacheNames()"
              ></el-button>
            </div>
            <el-table
              v-loading="loading"
              :data="cacheNames"
              :height="tableHeight"
              highlight-current-row
              @row-click="getCacheKeys"
              style="width: 100%"
              class="polaris-el-table"
            >
              <el-table-column
                label="序号"
                width="60"
                type="index"
              ></el-table-column>

              <el-table-column
                label="缓存名称"
                align="center"
                prop="cacheName"
                :show-overflow-tooltip="true"
                :formatter="nameFormatter"
              ></el-table-column>

              <el-table-column
                label="备注"
                align="center"
                prop="remark"
                :show-overflow-tooltip="true"
              />
              <el-table-column
                label="操作"
                width="60"
                align="center"
                class-name="small-padding fixed-width"
              >
                <template #default="scope">
                  <el-button
                    link
                    type="primary"
                    icon="Delete"
                    @click="handleClearCacheName(scope.row)"
                  ></el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>

        <!-- 键名列表 -->
        <el-col :span="8" class="card-box">
          <div class="polaris-table-card list-card">
            <div class="card-header-custom">
              <div class="card-header-title">
                <el-icon class="header-icon"><Key /></el-icon>
                <span>键名列表</span>
              </div>
              <el-button
                class="refresh-btn-header"
                link
                type="primary"
                icon="Refresh"
                @click="refreshCacheKeys()"
              ></el-button>
            </div>
            <el-table
              v-loading="subLoading"
              :data="cacheKeys"
              :height="tableHeight"
              highlight-current-row
              @row-click="handleCacheValue"
              style="width: 100%"
              class="polaris-el-table"
            >
              <el-table-column
                label="序号"
                width="60"
                type="index"
              ></el-table-column>
              <el-table-column
                label="缓存键名"
                align="center"
                :show-overflow-tooltip="true"
                :formatter="keyFormatter"
              >
              </el-table-column>
              <el-table-column
                label="操作"
                width="60"
                align="center"
                class-name="small-padding fixed-width"
              >
                <template #default="scope">
                  <el-button
                    link
                    type="primary"
                    icon="Delete"
                    @click="handleClearCacheKey(scope.row)"
                  ></el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>

        <!-- 缓存内容 -->
        <el-col :span="8" class="card-box">
          <div class="polaris-table-card list-card">
            <div class="card-header-custom">
              <div class="card-header-title">
                <el-icon class="header-icon"><Document /></el-icon>
                <span>缓存内容</span>
              </div>
              <el-button
                class="clear-all-btn-header"
                link
                type="primary"
                icon="Delete"
                @click="handleClearCacheAll()"
              >清理全部</el-button>
            </div>
            <el-form :model="cacheForm" class="polaris-cache-form" label-position="top">
              <el-row :gutter="16">
                <el-col :span="24">
                  <el-form-item label="缓存名称" prop="cacheName">
                    <el-input v-model="cacheForm.cacheName" :readOnly="true" />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="缓存键名" prop="cacheKey">
                    <el-input v-model="cacheForm.cacheKey" :readOnly="true" />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="缓存内容" prop="cacheValue">
                    <el-input
                      v-model="cacheForm.cacheValue"
                      type="textarea"
                      :rows="12"
                      :readOnly="true"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup name="CacheList">
import {
  clearCacheAll,
  clearCacheKey,
  clearCacheName,
  getCacheValue,
  listCacheKey,
  listCacheName
} from "@/api/monitor/cache"
import {getCurrentInstance, ref} from 'vue'

const { proxy } = getCurrentInstance()

const cacheNames = ref([])
const cacheKeys = ref([])
const cacheForm = ref({})
const loading = ref(true)
const subLoading = ref(false)
const nowCacheName = ref("")
const tableHeight = ref(window.innerHeight - 200)

/** 查询缓存名称列表 */
function getCacheNames() {
  loading.value = true
  listCacheName().then(response => {
    cacheNames.value = response.data
    loading.value = false
  })
}

/** 刷新缓存名称列表 */
function refreshCacheNames() {
  getCacheNames()
  proxy.$modal.msgSuccess("刷新缓存列表成功")
}

/** 清理指定名称缓存 */
function handleClearCacheName(row) {
  clearCacheName(row.cacheName).then(response => {
    proxy.$modal.msgSuccess("清理缓存名称[" + row.cacheName + "]成功")
    getCacheKeys()
  })
}

/** 查询缓存键名列表 */
function getCacheKeys(row) {
  const cacheName = row !== undefined ? row.cacheName : nowCacheName.value
  if (cacheName === "") {
    return
  }
  subLoading.value = true
  listCacheKey(cacheName).then(response => {
    cacheKeys.value = response.data
    subLoading.value = false
    nowCacheName.value = cacheName
  })
}

/** 刷新缓存键名列表 */
function refreshCacheKeys() {
  getCacheKeys()
  proxy.$modal.msgSuccess("刷新键名列表成功")
}

/** 清理指定键名缓存 */
function handleClearCacheKey(cacheKey) {
  clearCacheKey(cacheKey).then(response => {
    proxy.$modal.msgSuccess("清理缓存键名[" + cacheKey + "]成功")
    getCacheKeys()
  })
}

/** 列表前缀去除 */
function nameFormatter(row) {
  return row.cacheName.replace(":", "")
}

/** 键名前缀去除 */
function keyFormatter(cacheKey) {
  return cacheKey.replace(nowCacheName.value, "")
}

/** 查询缓存内容详细 */
function handleCacheValue(cacheKey) {
  getCacheValue(nowCacheName.value, cacheKey).then(response => {
    cacheForm.value = response.data
  })
}

/** 清理全部缓存 */
function handleClearCacheAll() {
  clearCacheAll().then(response => {
    proxy.$modal.msgSuccess("清理全部缓存成功")
  })
}

getCacheNames()
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

.list-card {
  height: calc(100vh - 125px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
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

.refresh-btn-header,
.clear-all-btn-header {
  font-size: 14px;
  font-weight: 600;
  padding: 0;
  height: auto;
  
  :deep(.el-icon) {
    font-size: 16px;
  }
}

.polaris-cache-form {
  overflow-y: auto;
  flex: 1;
  padding-right: 4px;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    border-radius: 99px;
    background-color: rgba(79, 70, 229, 0.1);
    
    .dark & {
      background-color: rgba(56, 189, 248, 0.12);
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 14px;
  }

  :deep(.el-form-item__label) {
    font-size: 12px;
    font-weight: 700;
    color: #64748b;
    padding-bottom: 6px;
    line-height: 1;
    
    .dark & {
      color: #94a3b8;
    }
  }
  
  :deep(.el-input__wrapper),
  :deep(.el-textarea__wrapper) {
    border-radius: 10px;
    background-color: rgba(255, 255, 255, 0.4) !important;
    border: 1px solid rgba(0, 0, 0, 0.05) !important;
    box-shadow: none !important;
    transition: all 0.3s;
    
    .dark & {
      background-color: rgba(0, 0, 0, 0.2) !important;
      border-color: rgba(255, 255, 255, 0.05) !important;
    }
  }
  
  :deep(.el-input__inner),
  :deep(.el-textarea__inner) {
    color: #334155 !important;
    font-weight: 500;
    font-size: 13px;
    
    .dark & {
      color: #cbd5e1 !important;
    }
  }
}
</style>
