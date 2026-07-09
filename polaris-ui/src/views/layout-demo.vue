<template>
  <div :class="['demo-wrapper', 'theme-' + selectedTheme, 'struct-' + selectedStruct]">
    
    <!-- 全局背景（配合方案 A, C, D 的极光光球） -->
    <div v-if="selectedTheme === 'A' || selectedTheme === 'C' || selectedTheme === 'D'" class="mesh-bg">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
      <div class="orb orb-4"></div>
    </div>
    
    <!-- 全局背景（配合方案 B 的纯黑背景） -->
    <div v-if="selectedTheme === 'B'" class="cyber-bg"></div>

    <!-- 星点装饰 -->
    <div class="stars-layer"></div>

    <!-- 主界面布局容器 -->
    <div class="layout-container">
      
      <!-- 1. 悬浮 Dock 栏结构 (struct-1) / 传统侧栏结构 (默认) -->
      <div v-if="selectedStruct === 1 || selectedStruct === 2" class="sidebar-wrapper">
        <div class="sidebar-card">
          <!-- 品牌 Logo 区域 -->
          <div class="logo-area">
            <svg class="polaris-logo" viewBox="0 0 100 100" fill="none">
              <path d="M50 0C50 35 65 50 100 50C65 50 50 65 50 100C50 65 35 50 0 50C35 50 50 35 50 0Z" fill="url(#logosg)"/>
              <defs>
                <linearGradient id="logosg" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#a5b4fc"/>
                  <stop offset="100%" stop-color="#818cf8"/>
                </linearGradient>
              </defs>
            </svg>
            <span class="logo-title">北辰 Polaris</span>
          </div>

          <!-- 双栏结构的极窄轨道 (struct-2) -->
          <div v-if="selectedStruct === 2" class="narrow-rail">
            <div 
              v-for="cat in mockCategories" 
              :key="cat.id" 
              :class="['rail-item', { active: activeCat === cat.id }]"
              @click="activeCat = cat.id"
            >
              <i :class="cat.icon"></i>
            </div>
          </div>

          <!-- 菜单树列表 -->
          <div class="menu-list">
            <div 
              v-for="menu in filteredMenus" 
              :key="menu.path" 
              :class="['menu-item', { active: activeMenu === menu.path }]"
              @click="activeMenu = menu.path"
            >
              <i :class="menu.icon"></i>
              <span class="menu-label">{{ menu.title }}</span>
            </div>
          </div>
          
          <!-- 侧边栏底部版权 -->
          <div class="sidebar-footer">
            <span>v1.2.0</span>
          </div>
        </div>
      </div>

      <!-- 右侧主面板 -->
      <div class="main-workspace">
        
        <!-- 2. 顶部导航栏 -->
        <div class="navbar-card">
          <div class="nav-left">
            <i class="el-icon-s-fold toggle-icon"></i>
            <!-- 面包屑 -->
            <div class="breadcrumb-mock">
              <span class="bc-parent">工作台</span>
              <span class="bc-sep">/</span>
              <span class="bc-active">AI 控制中心</span>
            </div>
            
            <!-- 结构 3 的顶部通栏横向菜单 -->
            <div v-if="selectedStruct === 3" class="topbar-menu">
              <div 
                v-for="cat in mockCategories" 
                :key="cat.id" 
                :class="['topmenu-item', { active: activeCat === cat.id }]"
                @click="activeCat = cat.id"
              >
                <i :class="cat.icon"></i>
                <span>{{ cat.name }}</span>
              </div>
            </div>
          </div>

          <div class="nav-right">
            <!-- 快捷搜索 -->
            <div class="search-box">
              <i class="el-icon-search"></i>
              <input type="text" placeholder="快捷搜索 (Ctrl + K)" />
            </div>

            <!-- 系统按钮 -->
            <div class="nav-actions">
              <i class="el-icon-bell action-btn"></i>
              <i class="el-icon-rank action-btn"></i>
            </div>

            <!-- 头像下拉 -->
            <div class="user-profile">
              <img src="@/assets/logo/logo.png" class="avatar" />
              <span class="username">管理员</span>
            </div>
          </div>
        </div>

        <!-- 3. 主内容看板 -->
        <div class="content-body">
          <!-- 演示控制面板（悬浮球控制器） -->
          <div class="control-panel">
            <h3 class="panel-header">Layout Redesign Sandbox</h3>
            
            <!-- 主题风格切换 -->
            <div class="control-group">
              <label>1. 主题风格 (Theme Style)</label>
              <div class="radio-buttons">
                <button :class="{ active: selectedTheme === 'A' }" @click="selectedTheme = 'A'">
                  🌌 极光玻璃 (暗色)
                </button>
                <button :class="{ active: selectedTheme === 'B' }" @click="selectedTheme = 'B'">
                  ⚡ 赛博流光 (暗色)
                </button>
                <button :class="{ active: selectedTheme === 'C' }" @click="selectedTheme = 'C'">
                  ❄️ 冰川白玻 (亮色)
                </button>
                <button :class="{ active: selectedTheme === 'D' }" @click="selectedTheme = 'D'">
                  🎨 晨曦暖柔 (亮色)
                </button>
              </div>
            </div>

            <!-- 排版结构切换 -->
            <div class="control-group">
              <label>2. 排版结构 (Layout Struct)</label>
              <div class="radio-buttons">
                <button :class="{ active: selectedStruct === 1 }" @click="selectedStruct = 1">
                  🛸 结构 1 (悬浮 Dock 栏)
                </button>
                <button :class="{ active: selectedStruct === 2 }" @click="selectedStruct = 2">
                  📊 结构 2 (双轨微缩栏)
                </button>
                <button :class="{ active: selectedStruct === 3 }" @click="selectedStruct = 3">
                  💻 结构 3 (顶部通栏栏)
                </button>
              </div>
            </div>
            
            <div class="panel-desc">
              <p>💡 <b>提示</b>：点击上述按钮即可实时切换预览。当前方案运行于本地 Vue 实例，完全 1:1 像素级体现！确定心仪的风格后告诉我就好。</p>
            </div>
          </div>

          <!-- 卡片统计指标 -->
          <div class="dashboard-grid">
            <div class="stats-card">
              <div class="card-glow"></div>
              <div class="card-inner">
                <span class="card-title">当前激活智能体</span>
                <h2 class="card-num">18</h2>
                <span class="card-sub">较昨日上涨 12%</span>
              </div>
            </div>
            <div class="stats-card">
              <div class="card-glow"></div>
              <div class="card-inner">
                <span class="card-title">多模态流式请求数</span>
                <h2 class="card-num">1,824K</h2>
                <span class="card-sub">平均响应 240ms</span>
              </div>
            </div>
            <div class="stats-card">
              <div class="card-glow"></div>
              <div class="card-inner">
                <span class="card-title">RAG 向量检索正确率</span>
                <h2 class="card-num">98.6%</h2>
                <span class="card-sub">匹配相似度 > 0.85</span>
              </div>
            </div>
          </div>

          <!-- 模拟内容区 -->
          <div class="data-section">
            <div class="mock-table-card">
              <div class="card-inner">
                <h3>最近运行工作流任务</h3>
                <div class="table-mock">
                  <div class="table-row header">
                    <span>任务名称</span>
                    <span>状态</span>
                    <span>执行时长</span>
                    <span>触发源</span>
                  </div>
                  <div class="table-row">
                    <span>DeepSeek 文档问答工作流</span>
                    <span class="status ok">已完成</span>
                    <span>1.8s</span>
                    <span>API 轮询</span>
                  </div>
                  <div class="table-row">
                    <span>Polaris 向量数据库清洗任务</span>
                    <span class="status run">进行中</span>
                    <span>12.5s</span>
                    <span>定时Cron</span>
                  </div>
                  <div class="table-row">
                    <span>多媒体音频多语种翻译</span>
                    <span class="status err">失败</span>
                    <span>0.4s</span>
                    <span>手动触发</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

        </div>

      </div>

    </div>

  </div>
</template>

<script>
export default {
  name: "LayoutDemo",
  data() {
    return {
      selectedTheme: 'A', // A, B, C, D
      selectedStruct: 1, // 1, 2, 3
      activeCat: 1,
      activeMenu: '/dashboard',
      mockCategories: [
        { id: 1, name: '智能面板', icon: 'el-icon-menu' },
        { id: 2, name: '系统设置', icon: 'el-icon-setting' },
        { id: 3, name: 'AI智能体', icon: 'el-icon-cpu' }
      ],
      mockMenus: [
        { catId: 1, title: 'AI 仪表盘', path: '/dashboard', icon: 'el-icon-pie-chart' },
        { catId: 1, title: 'RAG 知识库', path: '/rag', icon: 'el-icon-document-copy' },
        { catId: 2, title: '用户中心', path: '/user', icon: 'el-icon-user' },
        { catId: 2, title: '参数配置', path: '/config', icon: 'el-icon-set-up' },
        { catId: 3, title: '智能体编排', path: '/agent', icon: 'el-icon-connection' },
        { catId: 3, title: '工作流引擎', path: '/workflow', icon: 'el-icon-guide' }
      ]
    }
  },
  computed: {
    filteredMenus() {
      // 结构 1 / 结构 2 下按分类过滤；结构 3 下显示全部
      if (this.selectedStruct === 3) {
        return this.mockMenus
      }
      return this.mockMenus.filter(m => m.catId === this.activeCat)
    }
  }
}
</script>

<style lang="scss" scoped>
/* ====================================================
   1. 基础布局及重置
   ==================================================== */
.demo-wrapper {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  color: #f1f5f9;
  display: flex;
}

.layout-container {
  position: relative;
  z-index: 10;
  width: 100%;
  height: 100%;
  display: flex;
  padding: 0;
  box-sizing: border-box;
}

/* ====================================================
   2. 主题 A：星空极光 + 玻璃拟态 样式
   ==================================================== */
.theme-A {
  background: #040a14;

  /* 极光背景层 */
  .mesh-bg {
    position: absolute;
    inset: 0;
    z-index: 0;
    pointer-events: none;
  }

  .orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    mix-blend-mode: screen;
    opacity: 0.6;
  }

  .orb-1 { width: 50vw; height: 50vw; top: -10%; left: -10%; background: radial-gradient(circle, rgba(99, 102, 241, 0.5) 0%, transparent 70%); }
  .orb-2 { width: 40vw; height: 40vw; bottom: -10%; left: 30%; background: radial-gradient(circle, rgba(6, 182, 212, 0.45) 0%, transparent 70%); }
  .orb-3 { width: 35vw; height: 35vw; top: 20%; right: -5%; background: radial-gradient(circle, rgba(139, 92, 246, 0.4) 0%, transparent 70%); }
  .orb-4 { width: 30vw; height: 30vw; bottom: 20%; right: 40%; background: radial-gradient(circle, rgba(124, 58, 237, 0.3) 0%, transparent 70%); }

  /* 悬浮/磨砂玻璃侧栏 */
  .sidebar-card {
    background: rgba(255, 255, 255, 0.04);
    backdrop-filter: blur(32px);
    -webkit-backdrop-filter: blur(32px);
    border: 1px solid rgba(255, 255, 255, 0.07);
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.35);
  }

  .navbar-card {
    background: rgba(4, 8, 18, 0.45);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  }

  .menu-item {
    color: rgba(255, 255, 255, 0.6);
    transition: all 0.25s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.03);
      color: #fff;
    }

    &.active {
      background: rgba(255, 255, 255, 0.06);
      color: #c4b5fd;
      box-shadow: inset 3px 0 0 #818cf8;
      text-shadow: 0 0 10px rgba(165, 180, 252, 0.4);
    }
  }

  .stats-card, .mock-table-card {
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid rgba(255, 255, 255, 0.06);
    backdrop-filter: blur(16px);
  }
}

/* ====================================================
   3. 主题 B：赛博霓虹流光 样式
   ==================================================== */
.theme-B {
  background: #060608;

  .cyber-bg {
    position: absolute;
    inset: 0;
    background: linear-gradient(185deg, #09090e 0%, #050508 100%);
    z-index: 0;
  }

  .sidebar-card {
    background: #08080c;
    border-right: 1.5px solid rgba(139, 92, 246, 0.15);
    position: relative;

    &::after {
      content: '';
      position: absolute;
      top: 0;
      right: -1.5px;
      width: 1.5px;
      height: 100%;
      background: linear-gradient(to bottom, #00f2fe, #8b5cf6, #ec4899);
      animation: neonFlow 8s linear infinite;
    }
  }

  .navbar-card {
    background: #08080c;
    border-bottom: 1.5px solid rgba(139, 92, 246, 0.15);
  }

  .menu-item {
    color: #94a3b8;

    &:hover {
      background: rgba(139, 92, 246, 0.08);
      color: #00f2fe;
    }

    &.active {
      background: rgba(139, 92, 246, 0.15);
      color: #ffffff;
      border: 1px solid rgba(0, 242, 254, 0.4);
      box-shadow: 0 0 12px rgba(0, 242, 254, 0.15);
    }
  }

  .stats-card, .mock-table-card {
    background: #0a0a0f;
    border: 1px solid rgba(139, 92, 246, 0.15);
  }
}

@keyframes neonFlow {
  0% { filter: hue-rotate(0deg); }
  100% { filter: hue-rotate(360deg); }
}

/* ====================================================
   4. 主题 C：冰川白玻 (Glacier Glass) 亮色风格
   ==================================================== */
.theme-C {
  background: #f3f4f6;

  .stars-layer {
    display: none !important;
  }

  /* 极光背景层 (亮色调节) */
  .orb {
    opacity: 0.5;
    mix-blend-mode: multiply;
  }
  .orb-1 { background: radial-gradient(circle, rgba(147, 197, 253, 0.4) 0%, transparent 70%); }
  .orb-2 { background: radial-gradient(circle, rgba(167, 243, 208, 0.4) 0%, transparent 70%); }
  .orb-3 { background: radial-gradient(circle, rgba(196, 181, 253, 0.45) 0%, transparent 70%); }
  .orb-4 { background: radial-gradient(circle, rgba(233, 213, 255, 0.4) 0%, transparent 70%); }

  /* 悬浮/磨砂玻璃侧栏 */
  .sidebar-card {
    background: rgba(255, 255, 255, 0.65) !important;
    backdrop-filter: blur(30px) !important;
    -webkit-backdrop-filter: blur(30px) !important;
    border: 1px solid rgba(255, 255, 255, 0.5) !important;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05) !important;
  }

  .logo-title {
    color: #0f172a !important;
  }

  .narrow-rail {
    border-right: 1px solid rgba(0, 0, 0, 0.05) !important;

    .rail-item {
      color: #64748b !important;

      &:hover {
        color: #0f172a !important;
        background: rgba(0, 0, 0, 0.03) !important;
      }

      &.active {
        color: #2563eb !important;
        background: rgba(37, 99, 235, 0.08) !important;
      }
    }
  }

  .menu-item {
    color: #475569 !important;

    &:hover {
      background: rgba(0, 0, 0, 0.03) !important;
      color: #0f172a !important;
    }

    &.active {
      background: #2563eb !important;
      color: #ffffff !important;
      box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25) !important;
    }
  }

  .sidebar-footer {
    color: #94a3b8 !important;
    border-top: 1px solid rgba(0, 0, 0, 0.04) !important;
  }

  /* 顶部导航栏 */
  .navbar-card {
    background: rgba(255, 255, 255, 0.6) !important;
    backdrop-filter: blur(24px) !important;
    -webkit-backdrop-filter: blur(24px) !important;
    border-bottom: 1px solid rgba(0, 0, 0, 0.04) !important;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02) !important;
  }

  .breadcrumb-mock {
    .bc-parent { color: #64748b !important; }
    .bc-sep { color: #cbd5e1 !important; }
    .bc-active { color: #0f172a !important; }
  }

  .topmenu-item {
    color: #475569 !important;

    &:hover { color: #2563eb !important; }
    &.active {
      color: #2563eb !important;
      border-bottom-color: #2563eb !important;
    }
  }

  .search-box {
    background: rgba(255, 255, 255, 0.8) !important;
    border: 1px solid #e2e8f0 !important;
    color: #64748b !important;

    input {
      color: #0f172a !important;
      &::placeholder { color: #94a3b8 !important; }
    }
  }

  .action-btn {
    color: #475569 !important;
    background: rgba(0, 0, 0, 0.02) !important;
    border: 1px solid rgba(0, 0, 0, 0.05) !important;

    &:hover {
      background: rgba(0, 0, 0, 0.05) !important;
      color: #0f172a !important;
    }
  }

  .user-profile {
    .username { color: #334155 !important; }
  }

  /* 面板卡片 */
  .control-panel, .stats-card, .mock-table-card {
    background: rgba(255, 255, 255, 0.7) !important;
    backdrop-filter: blur(20px) !important;
    -webkit-backdrop-filter: blur(20px) !important;
    border: 1px solid rgba(255, 255, 255, 0.8) !important;
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.03) !important;
  }

  .control-panel {
    .panel-header {
      color: #0f172a !important;
      border-bottom: 1px solid rgba(0, 0, 0, 0.05) !important;
    }
    label { color: #334155 !important; }
    
    .radio-buttons button {
      background: #ffffff !important;
      border: 1px solid #d1d5db !important;
      color: #374151 !important;

      &:hover {
        background: #f3f4f6 !important;
      }

      &.active {
        background: #2563eb !important;
        border-color: #2563eb !important;
        color: #ffffff !important;
        box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2) !important;
      }
    }
    .panel-desc {
      background: rgba(0, 0, 0, 0.02) !important;
      border-left-color: #2563eb !important;
      color: #475569 !important;
    }
  }

  .stats-card {
    .card-glow {
      background: radial-gradient(circle, rgba(37, 99, 235, 0.03) 0%, transparent 60%) !important;
    }
    .card-title { color: #64748b !important; }
    .card-num { color: #0f172a !important; }
    .card-sub { color: #2563eb !important; }
  }

  .mock-table-card {
    h3 {
      color: #0f172a !important;
      border-bottom: 1px solid rgba(0, 0, 0, 0.04) !important;
    }

    .table-row {
      border-bottom: 1px solid #f1f5f9 !important;
      color: #334155 !important;

      &.header {
        color: #0f172a !important;
        background: rgba(0, 0, 0, 0.01) !important;
      }

      &:hover:not(.header) {
        background: #f8fafc !important;
      }
    }

    .status.ok { background: rgba(16, 185, 129, 0.1) !important; color: #10b981 !important; }
    .status.run { background: rgba(59, 130, 246, 0.1) !important; color: #3b82f6 !important; }
    .status.err { background: rgba(239, 68, 68, 0.1) !important; color: #ef4444 !important; }
  }
}

/* ====================================================
   4.5 主题 D：晨曦暖柔 (Sunrise Warmth) 亮色风格
   ==================================================== */
.theme-D {
  background: #fdfbf7;

  .stars-layer {
    display: none !important;
  }

  /* 极光背景层 (暖金色调) */
  .orb {
    opacity: 0.45;
    mix-blend-mode: multiply;
  }
  .orb-1 { background: radial-gradient(circle, rgba(253, 230, 138, 0.45) 0%, transparent 70%); }
  .orb-2 { background: radial-gradient(circle, rgba(254, 205, 211, 0.45) 0%, transparent 70%); }
  .orb-3 { background: radial-gradient(circle, rgba(253, 186, 116, 0.4) 0%, transparent 70%); }
  .orb-4 { background: radial-gradient(circle, rgba(254, 215, 170, 0.4) 0%, transparent 70%); }

  /* 悬浮/磨砂玻璃侧栏 (暖色调) */
  .sidebar-card {
    background: rgba(255, 254, 252, 0.75) !important;
    backdrop-filter: blur(25px) !important;
    -webkit-backdrop-filter: blur(25px) !important;
    border: 1px solid rgba(251, 191, 36, 0.15) !important;
    box-shadow: 0 10px 30px rgba(217, 119, 6, 0.03) !important;
  }

  .logo-title {
    color: #451a03 !important;
  }

  .narrow-rail {
    border-right: 1px solid rgba(217, 119, 6, 0.05) !important;

    .rail-item {
      color: #78350f !important;

      &:hover {
        color: #451a03 !important;
        background: rgba(217, 119, 6, 0.03) !important;
      }

      &.active {
        color: #d97706 !important;
        background: rgba(217, 119, 6, 0.08) !important;
      }
    }
  }

  .menu-item {
    color: #78350f !important;

    &:hover {
      background: rgba(217, 119, 6, 0.03) !important;
      color: #451a03 !important;
    }

    &.active {
      background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%) !important;
      color: #ffffff !important;
      box-shadow: 0 4px 12px rgba(217, 119, 6, 0.25) !important;
    }
  }

  .sidebar-footer {
    color: #b45309 !important;
    border-top: 1px solid rgba(217, 119, 6, 0.04) !important;
  }

  /* 顶部导航栏 */
  .navbar-card {
    background: rgba(255, 254, 252, 0.7) !important;
    backdrop-filter: blur(24px) !important;
    -webkit-backdrop-filter: blur(24px) !important;
    border-bottom: 1px solid rgba(217, 119, 6, 0.05) !important;
    box-shadow: 0 4px 20px rgba(217, 119, 6, 0.02) !important;
  }

  .breadcrumb-mock {
    .bc-parent { color: #78350f !important; }
    .bc-sep { color: #fcd34d !important; }
    .bc-active { color: #451a03 !important; }
  }

  .topmenu-item {
    color: #78350f !important;

    &:hover { color: #d97706 !important; }
    &.active {
      color: #d97706 !important;
      border-bottom-color: #d97706 !important;
    }
  }

  .search-box {
    background: rgba(255, 254, 252, 0.8) !important;
    border: 1px solid rgba(217, 119, 6, 0.15) !important;
    color: #78350f !important;

    input {
      color: #451a03 !important;
      &::placeholder { color: #b45309 !important; }
    }
  }

  .action-btn {
    color: #78350f !important;
    background: rgba(217, 119, 6, 0.02) !important;
    border: 1px solid rgba(217, 119, 6, 0.05) !important;

    &:hover {
      background: rgba(217, 119, 6, 0.05) !important;
      color: #451a03 !important;
    }
  }

  .user-profile {
    .username { color: #78350f !important; }
  }

  /* 面板卡片 */
  .control-panel, .stats-card, .mock-table-card {
    background: rgba(255, 254, 252, 0.8) !important;
    backdrop-filter: blur(20px) !important;
    -webkit-backdrop-filter: blur(20px) !important;
    border: 1px solid rgba(217, 119, 6, 0.1) !important;
    box-shadow: 0 8px 30px rgba(217, 119, 6, 0.03) !important;
  }

  .control-panel {
    .panel-header {
      color: #451a03 !important;
      border-bottom: 1px solid rgba(217, 119, 6, 0.05) !important;
    }
    label { color: #78350f !important; }
    
    .radio-buttons button {
      background: #ffffff !important;
      border: 1px solid #fcd34d !important;
      color: #78350f !important;

      &:hover {
        background: #fefbeb !important;
      }

      &.active {
        background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%) !important;
        border-color: #d97706 !important;
        color: #ffffff !important;
        box-shadow: 0 4px 12px rgba(217, 119, 6, 0.2) !important;
      }
    }
    .panel-desc {
      background: rgba(217, 119, 6, 0.02) !important;
      border-left-color: #d97706 !important;
      color: #78350f !important;
    }
  }

  .stats-card {
    .card-glow {
      background: radial-gradient(circle, rgba(245, 158, 11, 0.03) 0%, transparent 60%) !important;
    }
    .card-title { color: #b45309 !important; }
    .card-num { color: #451a03 !important; }
    .card-sub { color: #d97706 !important; }
  }

  .mock-table-card {
    h3 {
      color: #451a03 !important;
      border-bottom: 1px solid rgba(217, 119, 6, 0.04) !important;
    }

    .table-row {
      border-bottom: 1px solid rgba(217, 119, 6, 0.05) !important;
      color: #78350f !important;

      &.header {
        color: #451a03 !important;
        background: rgba(217, 119, 6, 0.01) !important;
      }

      &:hover:not(.header) {
        background: rgba(217, 119, 6, 0.02) !important;
      }
    }

    .status.ok { background: rgba(16, 185, 129, 0.1) !important; color: #10b981 !important; }
    .status.run { background: rgba(59, 130, 246, 0.1) !important; color: #3b82f6 !important; }
    .status.err { background: rgba(239, 68, 68, 0.1) !important; color: #ef4444 !important; }
  }
}

/* ====================================================
   5. 排版结构样式 (Structures)
   ==================================================== */

/* 结构 1：悬浮 Dock 栏 */
.struct-1 {
  .sidebar-wrapper {
    width: 260px;
    padding: 16px 0 16px 16px;
    box-sizing: border-box;
    height: 100vh;
    display: flex;
  }

  .sidebar-card {
    width: 100%;
    height: 100%;
    border-radius: 20px;
    display: flex;
    flex-direction: column;
    padding: 24px 18px;
    box-sizing: border-box;
  }

  .main-workspace {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 16px;
    box-sizing: border-box;
    height: 100vh;
    overflow: hidden;
  }

  .navbar-card {
    width: 100%;
    height: 60px;
    border-radius: 16px;
    padding: 0 20px;
    box-sizing: border-box;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    box-shadow: 0 4px 20px rgba(0,0,0,0.15);
  }

  .content-body {
    flex: 1;
    overflow-y: auto;
    padding-right: 4px;
  }
}

/* 结构 2：极简双栏 */
.struct-2 {
  .sidebar-wrapper {
    width: 290px;
    height: 100vh;
    display: flex;
  }

  .sidebar-card {
    width: 100%;
    height: 100%;
    display: flex;
    padding: 0;
  }

  // 极窄轨道
  .narrow-rail {
    width: 64px;
    height: 100%;
    border-right: 1px solid rgba(255, 255, 255, 0.05);
    display: flex;
    flex-direction: column;
    align-items: center;
    padding-top: 24px;
    gap: 16px;
    flex-shrink: 0;

    .rail-item {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      display: flex;
      justify-content: center;
      align-items: center;
      color: rgba(255,255,255,0.4);
      cursor: pointer;
      font-size: 20px;
      transition: all 0.2s;

      &:hover {
        background: rgba(255,255,255,0.04);
        color: #fff;
      }

      &.active {
        background: #818cf8;
        color: #fff;
        box-shadow: 0 4px 12px rgba(129,140,248,0.4);
      }
    }
  }

  .menu-list {
    flex: 1;
    padding: 24px 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .logo-area {
    display: none; // 双轨结构下顶部由轨道管理，不显示全局Logo
  }

  .main-workspace {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .navbar-card {
    width: 100%;
    height: 56px;
    padding: 0 24px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .content-body {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
  }
}

/* 结构 3：顶部通栏导航 */
.struct-3 {
  .sidebar-wrapper {
    display: none; // 彻底取消左侧菜单
  }

  .main-workspace {
    width: 100vw;
    height: 100vh;
    display: flex;
    flex-direction: column;
  }

  .navbar-card {
    width: 100%;
    height: 64px;
    padding: 0 32px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .content-body {
    flex: 1;
    overflow-y: auto;
    padding: 32px;
  }
}

/* ====================================================
   6. 细节子组件样式
   ==================================================== */

/* 星空点缀 */
.stars-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    radial-gradient(1px 1px at 15% 15%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 45% 45%, rgba(255,255,255,0.3) 0%, transparent 100%),
    radial-gradient(1px 1px at 85% 25%, rgba(255,255,255,0.4) 0%, transparent 100%),
    radial-gradient(1px 1px at 30% 75%, rgba(255,255,255,0.3) 0%, transparent 100%),
    radial-gradient(1px 1px at 70% 85%, rgba(255,255,255,0.4) 0%, transparent 100%);
  z-index: 1;
}

/* 导航栏左侧 */
.nav-left {
  display: flex;
  align-items: center;
  gap: 16px;

  .toggle-icon {
    font-size: 20px;
    cursor: pointer;
    color: rgba(255,255,255,0.6);
    &:hover { color: #fff; }
  }

  .breadcrumb-mock {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    
    .bc-parent { color: rgba(255,255,255,0.45); }
    .bc-sep { color: rgba(255,255,255,0.25); }
    .bc-active { color: rgba(255,255,255,0.85); font-weight: 500; }
  }
}

/* 结构 3 的顶部导航项 */
.topbar-menu {
  display: flex;
  align-items: center;
  margin-left: 32px;
  gap: 24px;
  height: 100%;

  .topmenu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    color: rgba(255,255,255,0.55);
    font-size: 14px;
    cursor: pointer;
    height: 100%;
    padding: 0 4px;
    position: relative;
    transition: color 0.2s;

    &:hover { color: #fff; }

    &.active {
      color: #818cf8;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        bottom: -22px;
        left: 0;
        right: 0;
        height: 2px;
        background: #818cf8;
        box-shadow: 0 0 10px #818cf8;
      }
    }
  }
}

/* 导航栏右侧 */
.nav-right {
  display: flex;
  align-items: center;
  gap: 20px;

  .search-box {
    display: flex;
    align-items: center;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    padding: 6px 12px;
    gap: 8px;
    width: 180px;

    i { color: rgba(255,255,255,0.3); }
    input {
      background: transparent;
      border: none;
      color: #fff;
      font-size: 12px;
      width: 100%;
      outline: none;
      &::placeholder { color: rgba(255,255,255,0.3); }
    }
  }

  .nav-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .action-btn {
      font-size: 18px;
      color: rgba(255,255,255,0.5);
      cursor: pointer;
      &:hover { color: #fff; }
    }
  }

  .user-profile {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;

    .avatar {
      width: 28px;
      height: 28px;
      border-radius: 50%;
    }

    .username {
      font-size: 13px;
      color: rgba(255,255,255,0.7);
    }
  }
}

/* 菜单区域 */
.logo-area {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 32px;

  .polaris-logo {
    width: 28px;
    height: 28px;
    filter: drop-shadow(0 0 8px rgba(165,180,252,0.6));
  }

  .logo-title {
    font-size: 16px;
    font-weight: 700;
    letter-spacing: 0.5px;
  }
}

.menu-list {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .menu-item {
    display: flex;
    align-items: center;
    gap: 12px;
    height: 40px;
    border-radius: 8px;
    padding: 0 16px;
    cursor: pointer;
    font-size: 14px;

    i { font-size: 16px; }
  }
}

.sidebar-footer {
  margin-top: auto;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.15);
  text-align: center;
}

/* 控制台沙盒 */
.control-panel {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.1);
  margin-bottom: 24px;
  backdrop-filter: blur(12px);

  .panel-header {
    margin: 0 0 20px;
    font-size: 18px;
    font-weight: 600;
    background: linear-gradient(135deg, #a5b4fc, #818cf8, #06b6d4);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .control-group {
    margin-bottom: 18px;

    label {
      display: block;
      font-size: 13px;
      color: rgba(255,255,255,0.45);
      margin-bottom: 8px;
    }

    .radio-buttons {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;

      button {
        background: rgba(255, 255, 255, 0.04);
        border: 1px solid rgba(255, 255, 255, 0.08);
        border-radius: 8px;
        color: rgba(255,255,255,0.65);
        padding: 8px 16px;
        font-size: 13px;
        cursor: pointer;
        transition: all 0.25s;

        &:hover {
          background: rgba(255, 255, 255, 0.08);
          color: #fff;
        }

        &.active {
          background: #818cf8;
          border-color: #818cf8;
          color: #fff;
          box-shadow: 0 4px 12px rgba(129,140,248,0.3);
        }
      }
    }
  }

  .panel-desc {
    margin: 12px 0 0;
    font-size: 12px;
    color: rgba(255,255,255,0.4);
    line-height: 1.5;
  }
}

/* 统计卡片网格 */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stats-card {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);

  .card-inner {
    padding: 24px;
    position: relative;
    z-index: 2;
  }

  .card-title {
    font-size: 13px;
    color: rgba(255,255,255,0.45);
    display: block;
    margin-bottom: 12px;
  }

  .card-num {
    font-size: 32px;
    font-weight: 700;
    margin: 0 0 6px;
    color: #fff;
  }

  .card-sub {
    font-size: 12px;
    color: #34d399;
  }
}

/* 数据任务区 */
.mock-table-card {
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.1);

  h3 { margin: 0 0 16px; font-size: 15px; font-weight: 600; }

  .table-mock {
    display: flex;
    flex-direction: column;
    gap: 10px;

    .table-row {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1.2fr;
      padding: 10px 14px;
      border-radius: 8px;
      font-size: 13px;
      color: rgba(255,255,255,0.75);
      background: rgba(255, 255, 255, 0.02);
      align-items: center;

      &.header {
        background: transparent;
        color: rgba(255,255,255,0.35);
        font-weight: 500;
      }

      .status {
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 11px;
        width: fit-content;
        
        &.ok { background: rgba(52, 211, 153, 0.15); color: #34d399; }
        &.run { background: rgba(96, 165, 250, 0.15); color: #60a5fa; }
        &.err { background: rgba(248, 113, 113, 0.15); color: #f87171; }
      }
    }
  }
}
</style>
