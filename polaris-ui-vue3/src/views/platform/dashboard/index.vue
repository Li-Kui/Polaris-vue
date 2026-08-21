<template>
  <div class="dashboard-container">
    <!-- 顶部 Hero 欢迎卡片 -->
    <div class="hero-banner">
      <div class="hero-content">
        <div class="hero-badge">
          <span class="pulse-point"></span>
          <span>北辰 AI 企业能力中台 · v3.9</span>
        </div>
        <h1 class="hero-title">欢迎使用北辰 AI 开放平台</h1>
        <p class="hero-subtitle">
          为全线业务系统提供大模型对话、知识库增强检索 (RAG)、智能体编排与标准 OpenAI 协议开放接入。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" class="hero-btn-primary" @click="$router.push('/platform/console/apikey')">
            <el-icon><Key /></el-icon>管理 API Key
          </el-button>
          <a href="/platform/docs/index.html" target="_blank" class="hero-btn-secondary">
            <el-icon><Document /></el-icon>开发者接口文档 →
          </a>
        </div>
      </div>
      <div class="hero-deco-graphic">
        <div class="gradient-orb orb-1"></div>
        <div class="gradient-orb orb-2"></div>
      </div>
    </div>

    <!-- 核心运行指标卡片矩阵 (4 列) -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon-wrapper purple">
          <el-icon><Cpu /></el-icon>
        </div>
        <div class="stat-main">
          <div class="stat-label">已消耗 Token</div>
          <div class="stat-value">{{ formatNumber(tenantInfo.usedTokens || 0) }}</div>
          <div class="stat-sub">配额上限: {{ tenantInfo.quotaTokens === -1 ? '无限' : formatNumber(tenantInfo.quotaTokens) }}</div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon-wrapper blue">
          <el-icon><Key /></el-icon>
        </div>
        <div class="stat-main">
          <div class="stat-label">生效 API 密钥</div>
          <div class="stat-value">{{ activeKeysCount }} <span class="unit">把</span></div>
          <div class="stat-sub">支持 OpenAI 标准协议</div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon-wrapper emerald">
          <el-icon><Connection /></el-icon>
        </div>
        <div class="stat-main">
          <div class="stat-label">外部连接器与数据源</div>
          <div class="stat-value">{{ datasourceCount + connectorCount }} <span class="unit">个</span></div>
          <div class="stat-sub">MySQL / PG / REST API</div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon-wrapper amber">
          <el-icon><UserFilled /></el-icon>
        </div>
        <div class="stat-main">
          <div class="stat-label">租户成员规模</div>
          <div class="stat-value">{{ memberCount }} <span class="unit">人</span></div>
          <div class="stat-sub">协同权限与访问管控</div>
        </div>
      </div>
    </div>

    <!-- 中台核心能力功能矩阵 -->
    <div class="section-title-row">
      <h2 class="section-title">核心能力中心</h2>
      <span class="section-desc">即开即用的企业级生成式 AI 基础设施</span>
    </div>

    <div class="feature-matrix-grid">
      <div class="feature-card" @click="$router.push('/platform/console/chat')">
        <div class="feature-icon-box f-purple">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <h3 class="feature-name">AI 对话体验</h3>
        <p class="feature-desc">内置大语言模型交互沙箱，支持毫秒级流式吐字、多轮上下文与系统提示词调优。</p>
        <div class="feature-link">立即体验 ➔</div>
      </div>

      <div class="feature-card" @click="$router.push('/platform/console/knowledge')">
        <div class="feature-icon-box f-blue">
          <el-icon><FolderOpened /></el-icon>
        </div>
        <h3 class="feature-name">企业知识库 (RAG)</h3>
        <p class="feature-desc">基于 Qdrant 向量引擎的多租户物理隔离知识库，支持文档解析、智能分块与混合语义检索。</p>
        <div class="feature-link">管理知识库 ➔</div>
      </div>

      <div class="feature-card" @click="$router.push('/platform/console/agent')">
        <div class="feature-icon-box f-emerald">
          <el-icon><UserFilled /></el-icon>
        </div>
        <h3 class="feature-name">智能体编排</h3>
        <p class="feature-desc">可视化编排具备特定角色、提示词与插件工具能力的自主智能体，赋能垂直业务。</p>
        <div class="feature-link">编排智能体 ➔</div>
      </div>

      <div class="feature-card" @click="$router.push('/platform/console/model')">
        <div class="feature-icon-box f-amber">
          <el-icon><Cpu /></el-icon>
        </div>
        <h3 class="feature-name">大模型多厂商路由</h3>
        <p class="feature-desc">统一接入 DeepSeek、阿里通义千问、OpenAI 等主流大模型，支持按需配置与直连中转。</p>
        <div class="feature-link">配置模型 ➔</div>
      </div>
    </div>

    <!-- 开放接口快速对接 macOS 终端代码窗口 -->
    <div class="terminal-container">
      <div class="terminal-header">
        <div class="traffic-lights">
          <span class="light red"></span>
          <span class="light yellow"></span>
          <span class="light green"></span>
        </div>
        <div class="terminal-tabs">
          <button class="tab-btn" :class="{ active: currentLang === 'curl' }" @click="currentLang = 'curl'">cURL</button>
          <button class="tab-btn" :class="{ active: currentLang === 'python' }" @click="currentLang = 'python'">Python (OpenAI SDK)</button>
          <button class="tab-btn" :class="{ active: currentLang === 'node' }" @click="currentLang = 'node'">Node.js</button>
          <button class="tab-btn" :class="{ active: currentLang === 'java' }" @click="currentLang = 'java'">Java (LangChain4j)</button>
        </div>
        <div class="copy-action">
          <el-button link class="terminal-copy-btn" @click="copyCode">
            <el-icon><CopyDocument /></el-icon> 复制示例代码
          </el-button>
        </div>
      </div>
      <div class="terminal-body">
        <pre class="code-pre"><code>{{ codeSnippets[currentLang] }}</code></pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {
  ChatDotRound,
  Connection,
  CopyDocument,
  Cpu,
  Document,
  FolderOpened,
  Key,
  UserFilled
} from '@element-plus/icons-vue'
import usePlatformUserStore from '@/store/modules/platformUser'
import {listApiKey} from '@/api/platform/apiKey'
import {listDatasource} from '@/api/platform/datasource'
import {listConnector} from '@/api/platform/connector'
import {listTenantUser} from '@/api/platform/user'

const platformUserStore = usePlatformUserStore()
const tenantInfo = computed(() => platformUserStore.tenant || {})

const activeKeysCount = ref(1)
const datasourceCount = ref(0)
const connectorCount = ref(0)
const memberCount = ref(1)

const currentLang = ref('curl')

const codeSnippets = {
  curl: `curl -X POST "http://localhost:8080/platform/api/v1/chat/completions" \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: sk-your-api-key-here" \\
  -d '{
    "model": "polaris-default",
    "messages": [
      { "role": "user", "content": "你好！请介绍一下北辰 AI 开放平台。" }
    ],
    "stream": true
  }'`,

  python: `from openai import OpenAI

# 兼容标准 OpenAI SDK，只需配置 Base URL 与 API Key
client = OpenAI(
    base_url="http://localhost:8080/platform/api/v1",
    api_key="sk-your-api-key-here"
)

response = client.chat.completions.create(
    model="polaris-default",
    messages=[{"role": "user", "content": "用三句话总结大模型技术的核心价值。"}],
    stream=True
)

for chunk in response:
    content = chunk.choices[0].delta.content
    if content:
        print(content, end="", flush=True)`,

  node: `import OpenAI from 'openai'

const client = new OpenAI({
  baseURL: 'http://localhost:8080/platform/api/v1',
  apiKey: 'sk-your-api-key-here'
})

async function main() {
  const stream = await client.chat.completions.create({
    model: 'polaris-default',
    messages: [{ role: 'user', content: '写一段关于分布式中台架构的介绍。' }],
    stream: true,
  })

  for await (const chunk of stream) {
    process.stdout.write(chunk.choices[0]?.delta?.content || '')
  }
}

main()`,

  java: `import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public class PolarisMiddlePlatformDemo {
    public static void main(String[] args) {
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("http://localhost:8080/platform/api/v1")
                .apiKey("sk-your-api-key-here")
                .modelName("polaris-default")
                .temperature(0.7)
                .build();

        model.generate("你好，请为一家金融企业生成智能客服欢迎语。", new dev.langchain4j.model.StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) { System.out.print(token); }
            @Override
            public void onComplete(dev.langchain4j.model.output.Response response) { System.out.println("\\n[完成]"); }
            @Override
            public void onError(Throwable error) { error.printStackTrace(); }
        });
    }
}`
}

function formatNumber(num) {
  if (num === null || num === undefined) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return String(num)
}

function copyCode() {
  const text = codeSnippets[currentLang.value]
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('代码已成功复制到剪贴板！')
  })
}

function loadOverviewStats() {
  listApiKey().then(res => { if (res.rows) activeKeysCount.value = res.rows.length })
  listDatasource().then(res => { if (res.rows) datasourceCount.value = res.rows.length })
  listConnector().then(res => { if (res.rows) connectorCount.value = res.rows.length })
  listTenantUser().then(res => { if (res.rows) memberCount.value = res.rows.length })
}

onMounted(() => {
  loadOverviewStats()
})
</script>

<style lang="scss" scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

/* ================= Hero 欢迎卡片 ================= */
.hero-banner {
  padding: 36px 40px;
  border-radius: 20px;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%);
  color: #ffffff;
  position: relative;
  overflow: hidden;
  box-shadow: 0 10px 30px -5px rgba(49, 46, 129, 0.4);

  .hero-content {
    position: relative;
    z-index: 2;
    max-width: 760px;
  }

  .hero-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 4px 12px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.12);
    border: 1px solid rgba(255, 255, 255, 0.2);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.3px;
    margin-bottom: 16px;

    .pulse-point {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #34d399;
      box-shadow: 0 0 8px #34d399;
    }
  }

  .hero-title {
    font-size: 28px;
    font-weight: 800;
    line-height: 1.2;
    margin: 0 0 12px;
    letter-spacing: -0.5px;
  }

  .hero-subtitle {
    font-size: 14px;
    color: #e0e7ff;
    line-height: 1.6;
    margin: 0 0 24px;
  }

  .hero-actions {
    display: flex;
    align-items: center;
    gap: 16px;

    .hero-btn-primary {
      height: 42px;
      padding: 0 20px;
      border-radius: 10px;
      font-weight: 700;
      background: #ffffff;
      color: #4338ca;
      border: none;
      box-shadow: 0 4px 14px rgba(0, 0, 0, 0.15);
      transition: all 0.2s;

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
      }
    }

    .hero-btn-secondary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 42px;
      padding: 0 18px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 600;
      color: #ffffff;
      background: rgba(255, 255, 255, 0.12);
      border: 1px solid rgba(255, 255, 255, 0.25);
      text-decoration: none;
      transition: all 0.2s;

      &:hover {
        background: rgba(255, 255, 255, 0.2);
        transform: translateY(-2px);
      }
    }
  }

  .hero-deco-graphic {
    position: absolute;
    right: -60px;
    top: -60px;
    width: 320px;
    height: 320px;
    pointer-events: none;

    .gradient-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(50px);
      opacity: 0.6;
    }

    .orb-1 {
      width: 220px;
      height: 220px;
      background: #818cf8;
      right: 40px;
      top: 20px;
    }

    .orb-2 {
      width: 180px;
      height: 180px;
      background: #c084fc;
      right: 120px;
      bottom: 20px;
    }
  }
}

/* ================= 核心指标卡片 ================= */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  padding: 20px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  display: flex;
  align-items: flex-start;
  gap: 16px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 24px -4px rgba(0, 0, 0, 0.06);
    border-color: rgba(99, 102, 241, 0.3);
  }

  .stat-icon-wrapper {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    flex-shrink: 0;

    &.purple {
      background: rgba(99, 102, 241, 0.1);
      color: #6366f1;
    }
    &.blue {
      background: rgba(2, 132, 199, 0.1);
      color: #0284c7;
    }
    &.emerald {
      background: rgba(16, 185, 129, 0.1);
      color: #10b981;
    }
    &.amber {
      background: rgba(245, 158, 11, 0.1);
      color: #f59e0b;
    }
  }

  .stat-main {
    flex: 1;

    .stat-label {
      font-size: 12px;
      font-weight: 600;
      color: #64748b;
      margin-bottom: 4px;
    }

    .stat-value {
      font-size: 24px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.5px;
      line-height: 1.2;

      .unit {
        font-size: 13px;
        font-weight: 500;
        color: #94a3b8;
      }
    }

    .stat-sub {
      font-size: 11px;
      color: #94a3b8;
      margin-top: 4px;
    }
  }

  .is-dark & {
    background: #0f172a;
    border-color: #1e293b;

    .stat-main {
      .stat-label { color: #94a3b8; }
      .stat-value { color: #f8fafc; }
      .stat-sub { color: #64748b; }
    }
  }
}

/* ================= 核心能力矩阵 ================= */
.section-title-row {
  margin-top: 8px;

  .section-title {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 4px;
  }

  .section-desc {
    font-size: 13px;
    color: #64748b;
  }

  .is-dark & {
    .section-title { color: #f8fafc; }
    .section-desc { color: #94a3b8; }
  }
}

.feature-matrix-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.feature-card {
  padding: 24px 20px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  transition: all 0.3s;

  &:hover {
    transform: translateY(-4px);
    border-color: #6366f1;
    box-shadow: 0 12px 28px -6px rgba(79, 70, 229, 0.12);

    .feature-link {
      color: #4f46e5;
      transform: translateX(4px);
    }
  }

  .feature-icon-box {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    margin-bottom: 16px;

    &.f-purple { background: linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(79, 70, 229, 0.05) 100%); color: #6366f1; }
    &.f-blue { background: linear-gradient(135deg, rgba(2, 132, 199, 0.15) 0%, rgba(2, 132, 199, 0.05) 100%); color: #0284c7; }
    &.f-emerald { background: linear-gradient(135deg, rgba(16, 185, 129, 0.15) 0%, rgba(16, 185, 129, 0.05) 100%); color: #10b981; }
    &.f-amber { background: linear-gradient(135deg, rgba(245, 158, 11, 0.15) 0%, rgba(245, 158, 11, 0.05) 100%); color: #f59e0b; }
  }

  .feature-name {
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 8px;
  }

  .feature-desc {
    font-size: 12px;
    color: #64748b;
    line-height: 1.6;
    flex: 1;
    margin: 0 0 16px;
  }

  .feature-link {
    font-size: 13px;
    font-weight: 700;
    color: #94a3b8;
    transition: all 0.2s;
  }

  .is-dark & {
    background: #0f172a;
    border-color: #1e293b;

    .feature-name { color: #f8fafc; }
    .feature-desc { color: #94a3b8; }
    .feature-link { color: #64748b; }

    &:hover {
      border-color: #818cf8;
      box-shadow: 0 12px 28px -6px rgba(0, 0, 0, 0.5);

      .feature-link {
        color: #818cf8;
      }
    }
  }
}

/* ================= 终端代码展示窗 ================= */
.terminal-container {
  border-radius: 16px;
  background: #0d1117;
  border: 1px solid #30363d;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

.terminal-header {
  height: 46px;
  background: #161b22;
  border-bottom: 1px solid #30363d;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;

  .traffic-lights {
    display: flex;
    align-items: center;
    gap: 8px;

    .light {
      width: 12px;
      height: 12px;
      border-radius: 50%;

      &.red { background: #ff5f56; }
      &.yellow { background: #ffbd2e; }
      &.green { background: #27c93f; }
    }
  }

  .terminal-tabs {
    display: flex;
    gap: 4px;

    .tab-btn {
      padding: 6px 14px;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 600;
      color: #8b949e;
      background: transparent;
      border: none;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        color: #c9d1d9;
        background: rgba(255, 255, 255, 0.05);
      }

      &.active {
        color: #58a6ff;
        background: rgba(56, 139, 253, 0.15);
      }
    }
  }

  .terminal-copy-btn {
    color: #8b949e !important;
    font-size: 12px;

    &:hover {
      color: #58a6ff !important;
    }
  }
}

.terminal-body {
  padding: 20px 24px;
  overflow-x: auto;

  .code-pre {
    margin: 0;
    font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
    font-size: 13px;
    line-height: 1.6;
    color: #e6edf3;
  }
}
</style>
