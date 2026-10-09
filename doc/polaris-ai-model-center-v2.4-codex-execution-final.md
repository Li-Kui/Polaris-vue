# Polaris AI 模型中心 V2.4 Codex Execution Final（冻结执行版）

> 项目：Polaris / polaris-vue  
> 目标分支：`dev`（直接在现有 `dev` 分支实施，不新建分支或 worktree，不执行 Git commit）  
> 模型管理页面：`/ai/model`  
> 前提：**旧模型配置数据可以删除，不做 V1 数据迁移；但 Polaris 现有 CHAT、Embedding、Image、Agent、Knowledge、Workflow、OpenAPI、多租户/部门隔离等功能必须继续可用。管理端、中台端现有查询权限、Key 身份解析、租户/部门数据权限逻辑保持不变，不在 Model Center V2 内重新设计。**  
> 文档目标：一次性把模型管理重构为可长期扩展的能力驱动模型中心，避免新增 AUDIO、VIDEO、RERANK、多模态能力时持续修改主表、主 Entity、主 DTO 和主页面。V2.4 在 V2.3.1 冻结架构基础上补齐 Phase 1A 可实施规格、Codex 执行协议、当前 `dev` 复核锚点、事务/缓存一致性、Credential 密钥管理、SSRF/DNS rebinding、防滥用、model_code 命名空间、Schema/Profile 兼容性与最终上线 Gate，作为后续 Codex 分阶段执行的唯一基线。

---

# 1. 最终结论

本次重构不再以“兼容旧模型数据”为目标，而以“**保留现有业务功能，重建模型配置底座**”为目标。

最终模型中心统一采用：

```text
Provider Connection
        +
AI Model
        +
Capability
        +
Schema
        +
Runtime Policy
        +
Adapter
```

必须达到：

```text
新增 AUDIO_TTS
→ 不 ALTER ai_model_config

新增 AUDIO_STT
→ 不修改 AiModelConfig 主 Entity 增加音频字段

新增 VIDEO_GENERATION
→ 不修改 ModelSaveRequest 增加视频字段

新增模型参数
→ 不修改 ModelEditor.vue 主体结构

新增 OpenAI Compatible 厂商
→ 默认复用 OPENAI_COMPATIBLE Protocol Adapter
```

本次允许：

```text
删除旧 ai_model_config 历史模型数据
删除旧模型配置专属字段
重新初始化模型相关数据
重新配置现有模型
```

本次不允许因为模型中心重构而丢失：

```text
CHAT
流式聊天
Reasoning
Tool Calling
Embedding / Knowledge Base
Image Generation / Edit
Agent
Workflow
OpenAI Compatible OpenAPI
租户 / 部门权限
```

---

# 2. 设计原则

## 2.1 模型配置数据可以重建，业务能力不能重建

这次不做：

```text
LegacyModelConverter
V1 / V2 双读
旧字段双写
旧 Provider Connection 去重迁移
Legacy CHAT / Embedding / Image Migration
config_version
```

但是现有成熟的：

```text
LangChain4j Chat 调用
Embedding 调用
图片生成实际请求
Agent Runtime
Knowledge Runtime
Workflow Runtime
OpenAPI 协议层
```

优先复用，不因为配置层重构而全部重写。

即：

> **重做模型管理和模型运行配置抽象，不盲目重写成熟 SDK / Provider 调用实现。**

---

## 2.2 管理端 / 中台端现有查询权限逻辑保持不变

Model Center V2 **不重新设计 Polaris 的权限体系**。

本次明确不新增：

```text
Model Access Policy
新的 Model Scope Resolver
新的 ModelCenterContext
平台模型授权表
新的 DEPT/TENANT/PLATFORM 自动覆盖式可见性规则
另一套租户/部门鉴权算法
```

必须继续复用项目现有：

```text
管理端登录认证
中台端 Key 身份解析
现有 tenant_id / dept_id 查询条件
现有部门 / 数据权限规则
现有 Mapper / Service 查询路径
现有角色、菜单、接口权限
```

模型中心只负责模型配置、Capability、Schema、Provider 和 Runtime，不负责重新决定“当前用户能看到哪些数据”。

当前 Polaris AI 模块已经以 `tenant_id + dept_id` 作为模型、知识库、图片任务等业务数据的租户/部门边界，因此 V2 必须继续沿用**现有字段名、字段类型、NULL 语义、自动填充和查询过滤规则**。本方案不把 AI 模块强行改造成另一套 `create_dept` 权限模型，也不重新定义 `tenant_id = 0` 等新的可见性含义。

Model / Provider Connection 的列表、详情、CRUD 以及 Runtime 按 ID 加载时，都必须沿用当前项目已有的权限感知查询路径；禁止新增无条件 `selectById` 后再手写 tenant/dept 比较的平行权限逻辑。

`ai_model_capability`、`ai_model_runtime_policy` 等子表不单独建立第二套租户/部门字段。任何对子表的读取、修改、删除，都必须先通过现有权限查询确认父 `ai_model_config` 可访问，再在同一 Service 事务中处理子表；禁止提供绕过父模型权限的独立公共 CRUD。

特别约束：

```text
tenant_id 继续使用当前 AI 模块的 BIGINT / NULL 语义
dept_id   继续使用当前 AI 模块的 BIGINT / NULL 语义
create_by / update_by 继续使用当前 AI 模块已有类型
```

如果未来当前分支字段发生变化，以**当前代码和当前 SQL**为准，Model Center 只适配，不自行重构权限字段。

## 2.3 稳定字段进列，能力专属参数进 Capability Config

主模型表只保存所有模型长期稳定的字段：

```text
name
model_code
connection_id
model_name
model_type
status
revision
```

以下不再进主表：

```text
temperature
maxTokens
embeddingDimension
imageSize
voice
sampleRate
speed
fps
duration
```

这些全部由：

```text
Capability + Schema + config_json
```

管理。

---

## 2.4 `model_type` 只做主分类，不参与运行时能力判断

保留：

```text
CHAT
EMBEDDING
RERANK
IMAGE
AUDIO
VIDEO
MULTIMODAL
```

用于：

```text
UI图标
列表分类
快速筛选
运营统计
```

业务运行时禁止依赖：

```java
if (modelType == AUDIO) {
}
```

必须依赖：

```text
Capability
```

---

## 2.5 Polaris 模型身份和 Provider 远程模型身份分离

正式区分：

```text
id
= Polaris 数据库内部 ID

model_code
= Polaris 稳定逻辑模型编码 / OpenAPI 模型 ID

model_name
= Provider 真实远程模型名称
```

例如：

```text
配置名称：客服 DeepSeek
model_code：customer-deepseek
model_name：deepseek-chat
```

另一个配置：

```text
配置名称：分析 DeepSeek
model_code：analysis-deepseek
model_name：deepseek-chat
```

同一个远程模型允许存在多个 Polaris 配置。

`model_code` 第一版创建后不可随意修改，避免影响：

```text
OpenAPI
Workflow
Agent
外部项目配置
```

---

# 3. 总体架构

```text
                         Polaris AI Model Center
                                  │
             ┌────────────────────┼────────────────────┐
             │                    │                    │
    Provider Connection        AI Model          Runtime Policy
             │                    │                    │
             │               Capability                │
             │                    │                    │
             │                  Schema                 │
             │                    │                    │
             └──────────── Model Runtime Resolver ─────┘
                                  │
                         Capability Adapter
                                  │
                          Protocol Adapter
                                  │
                         Provider / SDK / HTTP
```

管理面：

```text
Provider Connection
Model Config
Capability
Schema
Model Editor
Default Model
```

运行面：

```text
Capability Router
Runtime Resolver
Parameter Resolver
Capability Adapter
Protocol Adapter
Model Client
```

---

# 4. 最终数据库结构

第一版模型中心核心使用：

```text
ai_provider_connection
ai_model_config
ai_model_capability
ai_model_runtime_policy
ai_model_default
```

不增加：

```text
ai_model_catalog
ai_capability_definition
ai_schema_definition
ai_model_parameter
metadata_json
```

等后续真正产生明确需求再增加。

---

# 5. `ai_provider_connection`

Provider Connection 直接接入现有 Polaris AI 租户/部门查询体系，不新增新的权限字段语义。

```sql
DROP TABLE IF EXISTS `ai_provider_connection`;
CREATE TABLE `ai_provider_connection` (
    `id` bigint NOT NULL AUTO_INCREMENT,

    `tenant_id` bigint DEFAULT NULL COMMENT '沿用当前AI模块租户语义；NULL语义保持现状',
    `dept_id` bigint DEFAULT NULL COMMENT '沿用当前AI模块部门/数据权限语义',

    `connection_name` varchar(100) NOT NULL COMMENT '连接名称',
    `provider_code` varchar(50) NOT NULL COMMENT '厂商编码',
    `protocol_code` varchar(50) NOT NULL COMMENT '协议编码',

    `network_mode` varchar(20) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/INTERNAL；仅描述出站网络策略',
    `base_url` varchar(500) DEFAULT NULL,

    `credential_ciphertext` text DEFAULT NULL COMMENT '加密后的凭据JSON',
    `extra_config` json DEFAULT NULL COMMENT '仅允许非敏感协议/厂商扩展配置',

    `revision` bigint NOT NULL DEFAULT 1,
    `status` char(1) NOT NULL DEFAULT '1',
    `del_flag` char(1) NOT NULL DEFAULT '0',

    `create_by` varchar(64) DEFAULT '',
    `create_time` datetime DEFAULT NULL,
    `update_by` varchar(64) DEFAULT '',
    `update_time` datetime DEFAULT NULL,
    `remark` varchar(500) DEFAULT NULL,

    PRIMARY KEY (`id`),
    KEY `idx_provider_scope` (`tenant_id`,`dept_id`,`status`,`del_flag`),
    KEY `idx_provider_protocol` (`protocol_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Provider连接配置';
```

权限兼容约束：`tenant_id / dept_id` 的含义、自动填充、NULL 语义和查询过滤方式完全沿用当前 Polaris AI 模块；管理端继续走原管理端权限，中台端继续走原 Key / Tenant / DataPermission 逻辑；Runtime 获取 Connection 时必须复用当前权限感知 Service / Mapper。

`network_mode` 只解决 Provider 出站网络安全，不参与租户权限：

```text
PUBLIC   → 公网 Provider，默认拒绝 loopback / link-local / 私网目标
INTERNAL → Ollama / vLLM / 内部 Gateway，允许业务所需内网目标，但仍执行安全校验
```

# 6. Provider Credential

解密后的逻辑结构可以是：

```json
{
  "apiKey": "...",
  "secretKey": "...",
  "organization": "...",
  "project": "..."
}
```

规则：

```text
数据库：只存密文
日志：禁止明文
GET接口：禁止返回原文
编辑页面：不回填原始Key
```

更新协议：

保留：

```json
{
  "credentialAction": "KEEP"
}
```

替换：

```json
{
  "credentialAction": "REPLACE",
  "credential": {
    "apiKey": "sk-xxx"
  }
}
```

清空：

```json
{
  "credentialAction": "CLEAR"
}
```

禁止用：

```text
apiKey = null
```

猜测用户是“不修改”还是“清空”。

---

# 7. `ai_model_config`

旧模型配置数据允许重建，因此直接采用最终结构；但字段类型必须和当前 Polaris AI 模块保持一致。

```sql
DROP TABLE IF EXISTS `ai_model_config`;
CREATE TABLE `ai_model_config` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint DEFAULT NULL COMMENT '沿用当前AI模块租户语义；NULL语义保持现状',
    `dept_id` bigint DEFAULT NULL COMMENT '沿用当前AI模块部门/数据权限语义',

    `name` varchar(100) NOT NULL COMMENT '模型配置名称',
    `model_code` varchar(100) NOT NULL COMMENT 'Polaris稳定逻辑模型编码',
    `connection_id` bigint NOT NULL COMMENT 'Provider连接ID',
    `model_name` varchar(150) NOT NULL COMMENT 'Provider远程模型名称',
    `model_type` varchar(32) NOT NULL COMMENT '主分类，仅UI/筛选使用',
    `description` varchar(500) DEFAULT NULL,

    `revision` bigint NOT NULL DEFAULT 1 COMMENT '影响运行时的配置修订号',
    `status` char(1) NOT NULL DEFAULT '1',
    `del_flag` char(1) NOT NULL DEFAULT '0',

    `create_by` varchar(64) DEFAULT '',
    `create_time` datetime DEFAULT NULL,
    `update_by` varchar(64) DEFAULT '',
    `update_time` datetime DEFAULT NULL,
    `remark` varchar(500) DEFAULT NULL,

    `tenant_scope_key` varchar(32)
        GENERATED ALWAYS AS (
            CASE
                WHEN `tenant_id` IS NULL THEN 'GLOBAL'
                ELSE CONCAT('T:', CAST(`tenant_id` AS CHAR))
            END
        ) STORED
        COMMENT '仅用于model_code唯一索引；不参与权限查询，不改变tenant_id原有NULL语义',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_code` (`tenant_scope_key`,`model_code`),
    KEY `idx_model_connection` (`connection_id`),
    KEY `idx_model_type` (`tenant_id`,`model_type`,`status`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置';
```

说明：

- `model_code` 是 Polaris 稳定逻辑身份 / OpenAPI 模型 ID；
- `model_name` 是 Provider 真实远程模型名；
- 同一个 `connection_id + model_name` 允许存在多条 Polaris 模型配置；
- 当前平台共享资源如果使用 `tenant_id = NULL`，继续保持该语义；
- `tenant_scope_key` 只用于修复 MySQL `NULL` 唯一索引可重复问题；采用 `GLOBAL / T:<tenantId>` 形式避免把 `tenant_id=0` 假设成保留值，且绝不参与权限查询；
- `model_code` 第一版创建后不允许普通编辑直接修改；
- `model_code` 服务端规范化为小写，并限制为 `[a-z0-9][a-z0-9._-]{0,99}`；禁止空格、斜杠、控制字符和仅大小写差异的逻辑重复；
- 逻辑删除后的 `model_code` 默认不立即复用，避免外部 OpenAPI / Workflow / Agent 对稳定身份产生“旧编码指向新语义”的风险；
- `model_type` 只是 primary type 分类，服务端保存时根据 Invocation Capability 做一致性校验，运行时禁止依赖它判断能力。

# 8. OpenAPI `model` 统一使用 `model_code`

OpenAI Compatible API 请求：

```json
{
  "model": "customer-deepseek"
}
```

Polaris：

```text
customer-deepseek
↓
ai_model_config.model_code
↓
connection_id
↓
model_name = deepseek-chat
↓
Provider
```

`GET /platform/api/v1/models` 返回：

```json
{
  "id": "customer-deepseek",
  "object": "model",
  "owned_by": "deepseek"
}
```

以后同一个 Provider 远程模型可以有多个逻辑配置，而 OpenAPI 不产生路由歧义。

创建模型时可默认建议：

```text
model_code = model_name
```

如果重复，则要求用户提供唯一逻辑编码。

---

# 9. Capability 体系

## 9.1 Invocation Capability

可以直接产生业务调用：

```text
CHAT_COMPLETION
TEXT_EMBEDDING
RERANK
IMAGE_GENERATION
IMAGE_EDIT
IMAGE_INPAINT
IMAGE_VARIATION
AUDIO_TTS
AUDIO_STT
AUDIO_TRANSLATION
VIDEO_GENERATION
```

## 9.2 Feature Capability

附属于某个 Invocation Capability：

```text
REASONING
VISION_INPUT
AUDIO_INPUT
VIDEO_INPUT
TOOL_CALLING
STRUCTURED_OUTPUT
STREAMING
```

Feature 不允许无所属 Invocation 独立存在。

---

# 10. `ai_model_capability`

为了避免：

```text
CHAT支持STREAMING
但TTS不支持STREAMING
```

却只有一条全局 `STREAMING` 的歧义，Feature Capability 必须有 `applies_to_capability_code`。

```sql
DROP TABLE IF EXISTS `ai_model_capability`;
CREATE TABLE `ai_model_capability` (
    `id` bigint NOT NULL AUTO_INCREMENT,

    `model_config_id` bigint NOT NULL,

    `capability_code` varchar(64) NOT NULL,

    `applies_to_capability_code` varchar(64) NOT NULL DEFAULT ''
        COMMENT 'Feature所属Invocation；Invocation自身为空',

    `schema_version` int NOT NULL DEFAULT 1,

    `schema_hash` char(64) DEFAULT NULL COMMENT 'Resolved Schema SHA-256',

    `config_json` json DEFAULT NULL,

    `enabled` char(1) NOT NULL DEFAULT '1',

    `capability_source` varchar(24) NOT NULL DEFAULT 'MANUAL'
        COMMENT 'REMOTE_PROVIDER/MODEL_PROFILE/PROVIDER_PROFILE/PROTOCOL_DEFAULT/INFERRED/MANUAL',

    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_model_capability` (
        `model_config_id`,
        `capability_code`,
        `applies_to_capability_code`
    ),

    KEY `idx_capability_route` (
        `capability_code`,
        `enabled`,
        `model_config_id`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='AI模型能力配置';
```

示例：

```text
CHAT_COMPLETION
capability_code = CHAT_COMPLETION
applies_to = ''

REASONING for CHAT
capability_code = REASONING
applies_to = CHAT_COMPLETION

STREAMING for CHAT
capability_code = STREAMING
applies_to = CHAT_COMPLETION

STREAMING for TTS
capability_code = STREAMING
applies_to = AUDIO_TTS
```

---

# 11. Supported 与 Enabled 分离

必须区分：

```text
Supported Capability
= Provider / Profile 判断模型理论支持什么

Enabled Capability
= 当前 Polaris 模型实例实际开放什么
```

例如 Qwen Omni：

```text
支持：
CHAT_COMPLETION
VISION_INPUT
AUDIO_INPUT
TOOL_CALLING
STREAMING

当前配置只开放：
CHAT_COMPLETION
VISION_INPUT
```

Profile / Discovery 提供 Supported。

`ai_model_capability.enabled` 决定 Enabled。

---

# 12. Runtime Policy 需要支持 Capability 级覆盖

采用：

```text
Capability Runtime Policy
        >
Model Default Runtime Policy
        >
System Runtime Default
```

**关键语义：数据库策略中的 NULL 表示“本层不覆盖，继续继承上一层”。**

```sql
DROP TABLE IF EXISTS `ai_model_runtime_policy`;
CREATE TABLE `ai_model_runtime_policy` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `model_config_id` bigint NOT NULL,
    `capability_code` varchar(64) NOT NULL DEFAULT '' COMMENT '空=模型默认；非空=Invocation Capability覆盖',
    `max_concurrency` int DEFAULT NULL,
    `connect_timeout_ms` int DEFAULT NULL,
    `read_timeout_ms` int DEFAULT NULL,
    `retry_count` int DEFAULT NULL,
    `qps_limit` decimal(12,4) DEFAULT NULL,
    `priority` int DEFAULT NULL,
    `extra_config` json DEFAULT NULL,
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_runtime` (`model_config_id`,`capability_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型运行策略';
```

例如 AUDIO_TTS 只覆盖并发，则超时、重试继续继承 Model / System。不能给可继承字段设置数据库非空默认值，否则 Capability 行会无意覆盖模型级策略。

最终系统默认建议：

```text
retryCount = 0
```

是否允许重试由 Capability / Protocol 的幂等性策略决定；Image / Video / TTS 等可能产生费用或任务副作用的能力，不能因普通网络超时盲目重试。

生产多实例部署时 `max_concurrency / qps_limit` 必须按**集群级**实现，不能只使用 JVM-local Semaphore。

# 13. Default Model

```sql
DROP TABLE IF EXISTS `ai_model_default`;
CREATE TABLE `ai_model_default` (
    `id` bigint NOT NULL AUTO_INCREMENT,

    `scope_type` varchar(20) NOT NULL COMMENT 'GLOBAL/TENANT/DEPT',
    `scope_id` bigint NOT NULL DEFAULT 0,

    `capability_code` varchar(64) NOT NULL,

    `model_config_id` bigint NOT NULL,

    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_default_scope_capability` (
        `scope_type`,
        `scope_id`,
        `capability_code`
    ),

    KEY `idx_default_model` (`model_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='AI默认模型';
```

只允许为 **Invocation Capability** 设置默认模型。

默认解析顺序固定：

```text
DEPT
↓ 没有
TENANT
↓ 没有
GLOBAL
```

即：

```text
部门默认 > 租户默认 > 平台默认
```

设置默认模型必须校验：

```text
Model enabled
Provider Connection enabled
目标 Invocation Capability enabled
目标 Model 必须能够通过现有 Polaris 查询权限逻辑正常获取
```

注意：`scope_type / scope_id` 只用于默认模型选择，不参与 Model / Connection 的数据可见性判断，也不能替代现有管理端 / 中台端查询权限逻辑。

---

# 14. Capability Schema

采用：

> **JSON Schema 子集 + Polaris UI Schema + Condition Rules + Parameter Policy**

完整结构：

```json
{
  "code": "AUDIO_TTS",
  "name": "语音合成",
  "kind": "INVOCATION",
  "category": "AUDIO",
  "schemaVersion": 1,

  "schema": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "voice": {
        "type": "string",
        "title": "默认音色"
      },
      "format": {
        "type": "string",
        "title": "输出格式",
        "enum": ["mp3", "wav", "pcm"],
        "default": "mp3"
      },
      "sampleRate": {
        "type": "integer",
        "title": "采样率",
        "enum": [16000, 24000, 48000]
      },
      "speed": {
        "type": "number",
        "minimum": 0.25,
        "maximum": 4,
        "default": 1
      }
    },
    "required": ["voice"]
  },

  "conditionRules": [
    {
      "target": "sampleRate",
      "type": "REQUIRED_WHEN",
      "when": {
        "field": "format",
        "operator": "IN",
        "value": ["wav", "pcm"]
      }
    }
  ],

  "uiSchema": {
    "voice": {
      "component": "select",
      "optionsResolver": "PROVIDER_VOICES",
      "order": 10
    },
    "speed": {
      "component": "slider",
      "step": 0.05,
      "order": 20
    }
  },

  "parameterPolicy": {
    "voice": {
      "overridable": true
    },
    "speed": {
      "overridable": true
    }
  }
}
```

---

# 15. Schema 前后端职责

后端必须执行：

```text
类型校验
required
enum
minimum / maximum
additionalProperties
REQUIRED_WHEN
Parameter Policy
```

前端负责：

```text
input/select/slider/switch等展示
visibleWhen
disabledWhen
即时校验提示
```

注意：

```text
requiredWhen
```

不能只作为 UI 规则，必须进入后端 `conditionRules` 校验。

攻击者绕过 Vue 直接调用 API 时仍必须被拒绝。

---

# 16. Schema 安全边界

Schema 禁止：

```text
JavaScript
Vue Template
HTML
任意表达式
远程 URL
fetch
任意 HTTP 调用
```

动态 Options 只能使用白名单：

```json
{
  "optionsResolver": "PROVIDER_VOICES"
}
```

后端：

```text
Resolver白名单
↓
权限检查
↓
Provider Adapter
↓
远程Provider
```

禁止 Schema 自己声明：

```text
optionsUrl=https://xxx
```

避免 SSRF、越权和凭据泄漏。

---

# 17. Schema Version + Schema Hash

单纯：

```text
schema_version = 1
```

还不足以防止 Provider / Model Profile 改动导致规则暗漂移。

因此 Capability 保存：

```text
schema_version
schema_hash
```

Resolved Schema：

```text
Capability Base
+
Protocol Overlay
+
Provider Profile
+
Model Profile
↓
Canonical JSON
↓
SHA-256
↓
schema_hash
```

编辑模型时如果：

```text
storedSchemaHash != currentSchemaHash
```

必须重新校验配置。

正式规则：

> 已发布的 Schema / Profile 版本原则上不原地修改语义；有破坏性变更必须提升版本。

---

# 18. Schema 目录

```text
polaris-ai/
└── src/main/resources/
    └── model-schema/
        ├── capability/
        │   ├── chat-completion.json
        │   ├── text-embedding.json
        │   ├── rerank.json
        │   ├── image-generation.json
        │   ├── image-edit.json
        │   ├── audio-tts.json
        │   ├── audio-stt.json
        │   └── video-generation.json
        │
        ├── protocol/
        │   ├── openai-compatible.json
        │   ├── ollama.json
        │   └── dashscope-native.json
        │
        ├── provider/
        │   ├── openai.json
        │   ├── deepseek.json
        │   ├── dashscope.json
        │   └── siliconflow.json
        │
        └── model/
            ├── qwen3-tts-flash.json
            └── qwen-omni.json
```

Schema 第一版不存数据库。

---

# 19. Schema 合并顺序

```text
Global Capability Schema
        ↓
Protocol Overlay
        ↓
Provider Profile
        ↓
Model Profile
        ↓
Resolved Schema
```

规则：

```text
object → 递归 merge
scalar → 后者覆盖
array  → 默认整体覆盖
$remove=true → 删除字段
```

如未来需要 append，再明确支持 `$merge=append`，不要默认复杂化。

---

# 20. Provider 与 Protocol 分离

Provider：

```text
OPENAI
DEEPSEEK
DASHSCOPE
SILICONFLOW
CUSTOM
```

Protocol：

```text
OPENAI_COMPATIBLE
DASHSCOPE_NATIVE
OLLAMA
AZURE_OPENAI
```

例如：

```text
providerCode = DEEPSEEK
protocolCode = OPENAI_COMPATIBLE
```

大量：

```text
DeepSeek
OpenRouter
SiliconFlow
OneAPI
NewAPI
vLLM
内部Gateway
```

可复用：

```text
OpenAiCompatibleProtocolAdapter
```

---

# 21. Runtime 参数优先级

Capability Config 保存的是**模型默认调用参数**，不是永久强制参数。

优先级：

```text
Request Override
        >
Application / Agent / Workflow Override
        >
Model Capability Default
        >
Provider Default
```

但下游只能覆盖：

```text
parameterPolicy.overridable = true
```

的参数。

---

# 22. `ModelRuntimeResolver`

业务模块不得自己分别读取：

```text
Model
Connection
Capability
RuntimePolicy
Schema
```

统一：

```java
public interface ModelRuntimeResolver {

    ResolvedModelRuntime resolve(
        Long modelId,
        String invocationCapability,
        Set<String> requiredFeatures,
        Map<String, Object> applicationOverrides,
        Map<String, Object> requestOverrides
    );
}
```

流程：

```text
通过现有权限感知的 Model Service / Mapper 加载 Model
↓
校验模型状态
↓
通过现有权限感知的 Service / Mapper 加载 Provider Connection
↓
校验 Connection 状态
↓
校验 Invocation Capability enabled
↓
校验 Required Feature + appliesTo
↓
解析 Resolved Schema
↓
校验 schema hash / version
↓
加载 Model Capability Default
↓
合并 Application / Request Override
↓
Parameter Policy
↓
Schema Validate
↓
解析 Capability Runtime Policy
↓
解密 Credential
↓
生成 Runtime Spec
```

权限约束：

> `ModelRuntimeResolver` 不实现新的租户 / 部门可见性算法。Model 与 Provider Connection 是否可查询，必须由当前 Polaris 已有的管理端 / 中台端数据权限链路决定。若现有 Service 已提供权限感知查询，应直接复用；禁止新增平行的 `ScopeResolver`、`ModelAccessPolicy` 或手写 tenant/dept 比较逻辑。

---

# 23. `polaris-ai` 与 `polaris-ai-core` 依赖边界

必须避免 `polaris-ai-core` 反向依赖 `polaris-ai` Entity。

## `polaris-ai`

负责数据库、权限感知查询、Schema、Provider Credential、Runtime Resolver、Capability Router、Default Model、`CapabilityParameterComposer` 和 Model Editor API。

## `polaris-ai-core`

只接收纯运行时 DTO：

```java
public record ModelRuntimeSpec(
    Long modelId,
    Long modelRevision,
    Long connectionId,
    Long connectionRevision,
    String providerCode,
    String protocolCode,
    String baseUrl,
    String modelName,
    String capabilityCode,
    Map<String, Object> invocationParameters,
    Map<String, Map<String, Object>> featureParameters,
    Set<String> activeFeatures,
    Map<String, Object> credentials,
    RuntimePolicySpec runtimePolicy,
    String schemaHash
) {}
```

`ResolvedModelDefinition` 保存并缓存“已启用 Feature + Feature Config”；请求级 `FeatureActivationResolver` 再根据本次调用得到 `activeFeatures`。只有本次真正激活的 Feature 才进入 `ModelRuntimeSpec` 的执行语义，例如 REASONING、STREAMING、TOOL_CALLING。Invocation 参数与 Feature 参数必须分开，禁止重新扁平化成一个无命名空间的 Map。

`polaris-ai-core` 负责 LangChain4j、Capability Executor、Protocol Adapter、Provider HTTP/SDK、Model Client，禁止依赖 AiModelConfig/AiModelCapability Entity、Mapper、Controller、Tenant Service。

# 24. Capability Adapter 与 Protocol Adapter

Capability Adapter：

```text
描述“这种能力怎么调用”
```

例如：

```text
ChatCapabilityAdapter
EmbeddingCapabilityAdapter
ImageGenerationCapabilityAdapter
ImageEditCapabilityAdapter
AudioTtsCapabilityAdapter
AudioSttCapabilityAdapter
```

Protocol Adapter：

```text
描述“这个协议怎么和 Provider 通信”
```

例如：

```text
OpenAiCompatibleProtocolAdapter
DashScopeNativeProtocolAdapter
OllamaProtocolAdapter
```

不要把：

```text
能力
厂商
协议
```

全部写进一个巨大 `switch`。

---

# 25. 复用现有成熟 Runtime

即使数据重新初始化，也不要求把现有稳定实现全部推翻。

例如：

```text
ChatCapabilityAdapter
↓
复用现有 LangChain4j ChatModel 创建/调用逻辑
```

```text
EmbeddingCapabilityAdapter
↓
复用现有 EmbeddingModel 实现
```

```text
ImageGenerationCapabilityAdapter
↓
复用现有图片 Provider 请求实现
```

先通过 Adapter 包装现有能力，再逐步内部统一 Protocol Adapter。

---

# 26. Capability Router

业务模块找模型统一通过 Capability 查询，但**不手工传入 tenantId / deptId 重新实现权限过滤**。

```java
modelCapabilityRouter.findModels(
    capabilityCode,
    requiredFeatures
);
```

底层查询运行在当前已有管理端登录上下文或中台 Key/Tenant 上下文中，并复用当前 Mapper / Service / DataPermission 查询路径。

路由条件：现有权限链路能查询到 Model、Model enabled、Connection enabled、Invocation Capability enabled、Required Feature enabled 且 appliesTo 正确。

禁止先查全量模型后在 Router 中重新手写 tenantId/deptId 比较，也禁止只用 `model_type` 作为运行时能力判断。

# 27. Revision + 乐观锁

`ai_model_config.revision` 同时用于：

```text
Runtime Cache 失效
并发编辑保护
运行日志
Workflow Snapshot 引用
```

编辑请求必须带：

```json
{
  "expectedRevision": 8
}
```

更新：

```sql
UPDATE ai_model_config
SET revision = revision + 1,
    ...
WHERE id = ?
  AND revision = ?;
```

0 行更新：

```text
MODEL_CONFIG_CONFLICT
```

提示：

```text
模型配置已被其他用户修改，请刷新后重试。
```

Provider Connection 同样使用 revision 防止凭据被并发覆盖。

---

# 28. Runtime Cache

缓存必须同时考虑 Model Revision、Connection Revision 和 Schema Hash。

推荐两级缓存：

```text
Model Definition Cache
modelId:modelRevision:schemaHash:capability

Provider Connection / Client Cache
connectionId:connectionRevision
```

Runtime Resolver 每次组合 Model Definition 与当前 Connection Revision / Credential 生成最终 RuntimeSpec。这样修改 API Key / BaseURL / Protocol 时只提升 `connection.revision`，不需要把所有引用该 Connection 的 Model revision 全部 +1。

如果缓存完整 RuntimeSpec，则 Key 至少包含：

```text
modelId:modelRevision:connectionId:connectionRevision:capability:schemaHash
```

禁止把 API Key 明文、密钥哈希或其它 Secret 放进可观测 Cache Key。

# 29. 模型删除与停用

模型：

```text
status = disabled
→ 所有能力不可路由
```

Capability：

```text
enabled = false
→ 仅该能力不可路由
```

Provider Connection：

```text
status = disabled
→ 所有依赖模型不可运行
```

删除模型前通过：

```text
ModelReferenceService
```

检查：

```text
Agent
Knowledge Base
Workflow
Default Model
Open Platform
其它业务引用
```

有引用：

```text
禁止删除
允许停用
```

---

# 30. Model Editor API

建议：

```text
Provider Connection
GET    /ai/provider-connection
POST   /ai/provider-connection
PUT    /ai/provider-connection/{id}
DELETE /ai/provider-connection/{id}
POST   /ai/provider-connection/test

Model
GET    /ai/model/editor/context
GET    /ai/model/{id}/editor
POST   /ai/model
PUT    /ai/model/{id}
POST   /ai/model/test

Metadata
GET    /ai/model/meta/capabilities
GET    /ai/model/meta/schema
GET    /ai/model/meta/options
POST   /ai/model/meta/discover

Runtime Options
GET    /ai/model/options?capability=TEXT_EMBEDDING
```

新系统无需长期挂 `/v2`。

---

# 31. 模型保存 DTO

```java
@Data
public class ModelSaveRequest {

    private Long id;

    private Long expectedRevision;

    @NotBlank
    private String name;

    @NotBlank
    private String modelCode;

    @NotNull
    private Long connectionId;

    @NotBlank
    private String modelName;

    @NotBlank
    private String modelType;

    private String description;

    private List<ModelCapabilitySaveDTO> capabilities;

    private List<ModelRuntimePolicyDTO> runtimePolicies;

    private String remark;
}
```

Capability：

```java
@Data
public class ModelCapabilitySaveDTO {

    @NotBlank
    private String code;

    private String appliesToCapabilityCode;

    @NotNull
    private Integer schemaVersion;

    private Boolean enabled;

    private Map<String, Object> config;
}
```

新增 Audio / Video 参数不再修改 DTO。

权限字段说明：

- `tenantId`、`deptId` 不作为 Model Center 新造的权限参数体系重新设计；
- 继续通过当前项目已有登录上下文、Key 上下文、AI模块现有 Service / Mapper 和数据权限机制自动填充或校验；
- 如果当前管理端页面已有“归属部门”选择能力，则继续复用原 BO / Service 的赋值和校验方式，不新增第二套部门权限字段。

---

# 32. 保存事务

```text
BEGIN
↓
校验 Model Code 唯一
↓
按现有权限感知查询路径获取 Provider Connection，并校验状态
↓
校验 modelName
↓
解析 Supported Capability
↓
校验 Invocation / Feature 关系
↓
解析 Resolved Schema
↓
Schema + Condition Rules 校验
↓
保存 Model
↓
保存 Capability
↓
保存 Runtime Policy
↓
revision + 1
↓
COMMIT
```

模型、能力、运行策略必须强一致。

---

# 33. 前端技术栈约束

当前前端继续按照现有 Vue 3 + JavaScript 风格实现。

不要因为本次模型重构单独把模型模块改成 TypeScript。

页面状态：

```js
const modelForm = reactive({
  id: undefined,
  expectedRevision: undefined,
  name: '',
  modelCode: '',
  connectionId: undefined,
  modelName: '',
  modelType: '',
  deptId: undefined,
  capabilities: [],
  runtimePolicies: [],
  remark: ''
})
```

需要类型提示可使用 JSDoc。

---

# 34. Model Editor 页面

保留当前 Polaris 整体视觉，不需要同时重做 UI 主题。

建议：

```text
┌────────────────────────────────────────────────────────┐
│ 创建 AI 模型                              [取消] [保存] │
├────────────────────────────────────────────────────────┤
│ ① 模型连接                                             │
│ Provider连接   [阿里云生产账号 ▼]                     │
│ 远程模型       [qwen3-tts-flash ▼]                    │
│ 配置名称       [客服TTS]                              │
│ Model Code     [customer-tts]                         │
│ 主分类         [AUDIO ▼]                              │
│ 归属部门       [...]  （沿用现有页面权限/归属逻辑）   │
├────────────────────────────────────────────────────────┤
│ ② 模型能力                                             │
│ [✓ 语音合成] [✓ 流式输出]                            │
├────────────────────────────────────────────────────────┤
│ ③ 能力参数                                             │
│ 语音合成                                               │
│ Voice / Format / SampleRate / Speed ...               │
├────────────────────────────────────────────────────────┤
│ ④ 运行策略                                             │
│ 默认策略                                               │
│ AUDIO_TTS 覆盖策略                                     │
├────────────────────────────────────────────────────────┤
│                                  [测试模型] [保存配置] │
└────────────────────────────────────────────────────────┘
```

---

# 35. 前端组件

```text
views/ai/model.vue                  # 保留现有路由入口

components/
    ModelEditor.vue
    ModelBaseInfo.vue
    ProviderConnectionSelect.vue
    RemoteModelSelect.vue
    CapabilitySelector.vue
    CapabilityCard.vue
    SchemaFormRenderer.vue
    SchemaFieldRenderer.vue
    RuntimePolicyEditor.vue
    ModelTestDialog.vue

schema-components/
    SchemaInput.vue
    SchemaNumber.vue
    SchemaSelect.vue
    SchemaSlider.vue
    SchemaSwitch.vue
    SchemaTags.vue

custom-components/
    VoiceSelector.vue
    ImageSizeSelector.vue
```

禁止：

```text
AudioModelForm.vue
VideoModelForm.vue
ImageModelFormV2.vue
```

这类重新按模型类型写死整套表单的做法。

---

# 36. SchemaFormRenderer

统一：

```vue
<SchemaFormRenderer
  v-model="capability.config"
  :schema="capability.schema"
  :ui-schema="capability.uiSchema"
  :condition-rules="capability.conditionRules"
/>
```

Renderer 不知道：

```text
CHAT
IMAGE
AUDIO
VIDEO
```

只知道：

```text
string
number
boolean
select
slider
switch
```

复杂 UI 使用白名单 `customComponent` 插件注册。

---

# 37. 模型发现

选择 Provider Connection：

```text
Provider Connection
↓
Protocol Adapter.listModels()
↓
Remote Model
↓
Model Profile / Provider Metadata
↓
Supported Capability
↓
推荐 Enabled Capability
```

Provider 不支持 `/models` 时：

```text
允许 MANUAL_INPUT
```

系统不知道的模型：

```text
允许管理员手动声明 Capability
```

页面必须标识来源：

```text
REMOTE_PROVIDER
MODEL_PROFILE
PROVIDER_PROFILE
PROTOCOL_DEFAULT
INFERRED
MANUAL
```

不要使用虚假数值置信度。

---

# 38. CHAT Schema

```json
{
  "code": "CHAT_COMPLETION",
  "kind": "INVOCATION",
  "schemaVersion": 1,
  "schema": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "maxTokens": {
        "type": "integer",
        "minimum": 1,
        "maximum": 200000,
        "default": 2048
      },
      "temperature": {
        "type": "number",
        "minimum": 0,
        "maximum": 2,
        "default": 0.7
      }
    }
  }
}
```

Reasoning 不塞回 CHAT 参数中，而是：

```text
REASONING
appliesTo = CHAT_COMPLETION
```

Streaming：

```text
STREAMING
appliesTo = CHAT_COMPLETION
```

---

# 39. Embedding Schema

```json
{
  "code": "TEXT_EMBEDDING",
  "kind": "INVOCATION",
  "schemaVersion": 1,
  "schema": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "dimension": {
        "type": "integer"
      },
      "dimensionMode": {
        "type": "string",
        "enum": ["MODEL_DEFAULT", "REQUEST"],
        "default": "MODEL_DEFAULT"
      },
      "maxInputTokens": {
        "type": "integer"
      },
      "batchSize": {
        "type": "integer",
        "default": 16
      }
    }
  }
}
```

---

# 40. Image Capability

直接使用：

```text
IMAGE_GENERATION
IMAGE_EDIT
IMAGE_INPAINT
IMAGE_VARIATION
```

不要重新使用：

```text
IMAGE_GENERATION + capabilities[]
```

工作流需要图片编辑：

```text
requiredCapability = IMAGE_EDIT
```

Image 原来的：

```text
maxConcurrency
```

放到对应 IMAGE Capability Runtime Policy。

---

# 41. Audio

新增：

```text
AUDIO_TTS
AUDIO_STT
AUDIO_TRANSLATION
```

TTS Schema 示例参数：

```text
voice
format
sampleRate
speed
pitch
stream
```

STT：

```text
language
timestamps
speakerDiarization
punctuation
```

强制要求：

```text
0 个 ai_model_config audio_* 字段
0 个 ModelSaveRequest audio 专属字段
0 个 AudioModelForm.vue
```

如果新增 Audio 时必须修改主表或主 DTO，说明架构未达标。

---

# 42. Video

未来直接增加：

```text
VIDEO_GENERATION
```

例如参数：

```text
duration
fps
resolution
aspectRatio
seed
```

要求：

```text
数据库主表不改
AiModelConfig主结构不改
ModelSaveRequest不改
ModelEditor.vue主结构不改
```

---

# 43. Agent 集成

Agent 只引用：

```text
modelId
invocationCapability = CHAT_COMPLETION
```

如果 Agent 使用工具：

```text
requiredFeature = TOOL_CALLING
appliesTo = CHAT_COMPLETION
```

如果支持图片输入：

```text
requiredFeature = VISION_INPUT
appliesTo = CHAT_COMPLETION
```

Agent 自己保存：

```text
systemPrompt
history
knowledgeBase
tools
search config
```

模型中心不再保存这些业务策略。

---

# 44. Knowledge Base 集成

知识库选择模型：

```text
requiredCapability = TEXT_EMBEDDING
```

保留业务关系：

```text
embedding_model_id → ai_model_config.id
```

但增加一个重要保护规则：

> **已被知识库使用的 Embedding 模型，如果改变向量维度，不能静默生效。**

建议 Knowledge Base 保存：

```text
embedding_model_id
embedding_dimension
embedding_schema_hash / config_hash（可选）
```

如果模型编辑导致：

```text
1024维 → 1536维
```

而已有知识库引用：

```text
阻止直接修改
```

或要求：

```text
重新向量化 / 重建索引
```

这样不会因为模型中心配置变化破坏已有向量库。

---

# 45. Image 模块集成

Image 页面 / API 按：

```text
IMAGE_GENERATION
IMAGE_EDIT
IMAGE_INPAINT
```

筛选模型。

一次图片任务应该保存实际参数快照：

```text
modelId
modelRevision
capability
imageParams
```

管理员后续修改模型默认尺寸，不影响已创建任务的可追溯性。

---

# 46. Workflow 集成

工作流节点：

```json
{
  "nodeType": "ai",
  "requiredCapability": "AUDIO_TTS",
  "requiredFeatures": ["STREAMING"],
  "modelSelection": {
    "mode": "FIXED",
    "modelId": 1012
  },
  "modelOverrides": {
    "voice": "Cherry"
  }
}
```

也允许：

```text
mode = DEFAULT
```

通过调用身份对应的 Default Resolver 选择默认模型：管理端部门上下文为 `DEPT → TENANT`；只有既有管理员权限明确允许时才可继续读 `GLOBAL`，平台 API Key 仅使用所属 `TENANT`。

---

# 47. Workflow 发布必须做模型快照

只记录 `modelRevision` 不够，因为数据库不会自动保留历史 Revision 的完整模型配置。

Workflow 发布时必须保存**影响节点语义的非密钥 Runtime Snapshot**：

```json
{
  "modelId": 1001,
  "modelCode": "customer-deepseek",
  "modelRevision": 8,
  "connectionId": 12,
  "connectionRevision": 4,
  "providerCode": "DEEPSEEK",
  "protocolCode": "OPENAI_COMPATIBLE",
  "baseUrl": "https://api.deepseek.com",
  "modelName": "deepseek-chat",
  "capability": "CHAT_COMPLETION",
  "features": [
    {"code": "REASONING", "config": {"effort": "high"}}
  ],
  "schemaVersion": 1,
  "schemaHash": "xxx",
  "parameters": {"temperature": 0.3, "maxTokens": 4096},
  "runtimePolicy": {"readTimeoutMs": 120000}
}
```

不快照 Credential 明文。运行时使用 Snapshot 中的 provider/protocol/baseUrl/modelName/parameters/features/runtimePolicy，加上 `connectionId` 当前有效 Credential。

这样 API Key 可轮换，但修改 BaseURL / Protocol / Model 默认参数不会让已发布 Workflow 语义漂移。若 Connection 被停用、删除或 Credential 不可用，则 Workflow 明确失败，不偷偷切换 Provider。

# 48. OpenAPI 集成

OpenAI Compatible：

```text
GET /platform/api/v1/models
POST /platform/api/v1/chat/completions
```

CHAT 接口只暴露：

```text
CHAT_COMPLETION enabled
```

的模型。

外部 `model` 参数：

```text
model_code
```

Streaming 请求还要校验：

```text
STREAMING
appliesTo = CHAT_COMPLETION
```

后续可以自然增加：

```text
/images
/audio/speech
/audio/transcriptions
```

按对应 Capability 路由，而无需重新设计模型身份体系。

---

# 49. System Prompt / History / Tools / Search

模型中心最终**不保存**：

```text
system_prompt
max_history_messages
enabled_tools
enable_search
search_key
```

职责边界：System Prompt → Agent / Direct Chat / Application / Workflow；History → Conversation / Agent / Direct Chat；Tools/Search → Agent / Workflow / Direct Chat Policy；Search Credential → 工具凭据配置。

但是删除模型层这些字段之前，**普通直接聊天必须有明确的业务策略来源**，否则属于现有功能退化。

建议统一业务侧接口：

```java
public interface DirectChatPolicyResolver {
    DirectChatPolicy resolve();
}
```

配置来源优先复用当前 Chat / Conversation 配置；如果当前项目没有独立承载点，再新增轻量 `ai_chat_policy`。至少承接：

```text
defaultSystemPrompt
maxHistoryMessages
toolPolicy
searchPolicy
```

它属于 Chat/Application 业务层，不属于 Model Center。正式切换新模型表前必须回归普通聊天的 System Prompt、历史消息、工具/搜索策略。

# 50. 模型测试

新增页面保存前支持：

```text
测试连接
测试模型
```

CHAT：

```text
最小聊天请求
```

Embedding：

```text
"test"
并返回实际维度
```

TTS：

```text
"你好"
```

STT：

```text
短音频
```

Image / Video：

```text
默认只测试连接和模型可达性
真实生成由用户明确触发
并提示可能产生费用
```

测试逻辑通过：

```text
CapabilityTestHandler Registry
```

禁止全部写进 Controller。

---

# 51. 标准 Runtime 错误码

至少：

```text
MODEL_NOT_FOUND
MODEL_DISABLED
MODEL_CONFIG_CONFLICT
MODEL_PERMISSION_DENIED
MODEL_CODE_DUPLICATED
PROVIDER_CONNECTION_DISABLED
MODEL_CREDENTIAL_MISSING
CAPABILITY_NOT_ENABLED
FEATURE_NOT_ENABLED
CAPABILITY_CONFIG_INVALID
SCHEMA_VERSION_MISMATCH
SCHEMA_HASH_MISMATCH
MODEL_PROVIDER_UNAVAILABLE
MODEL_TIMEOUT
MODEL_RATE_LIMITED
EMBEDDING_DIMENSION_IN_USE
```

Agent / Workflow / OpenAPI 统一使用。

---

# 52. Fresh Install 与升级脚本

虽然旧模型数据可以删除，但不能只 DROP / RECREATE `ai_model_config` 后保留所有引用旧 `model_config_id` 的业务数据。

当前业务关系包括：

```text
Knowledge.embedding_model_id
Conversation.model_config_id
Agent.model_config_id
ImageTask.model_config_id
Workflow / Open Platform 中的模型引用
```

模型主表 ID 重建而这些数据继续保留，会产生悬空引用。

因此只支持两种初始化模式：

```text
A. 空数据/新系统初始化
→ 直接建立新模型中心

B. 已有环境整体重置AI业务数据
→ 明确清理所有依赖旧 model_config_id 的业务数据
→ 再重建模型中心
```

本方案不做旧 Model ID 映射迁移，因此**不支持“只重建模型表但无损保留旧 Agent / Knowledge / Conversation / Image / Workflow 数据”**。

必须单独提供增量入口，且不得修改现有 `sql/ry_ai.sql`：

```text
sql/ry_ai.sql
+ sql/model-center-v2.4/ 按编号顺序执行的增量SQL
model-center-reset.sql
```

`model-center-reset.sql` 执行前必须明确列出将被清理的业务表/数据；开发测试环境可直接重置，正式环境先备份。

# 53. 运行安全、平台兼容与更新语义

## 53.1 平台 Key 限流 / 租户配额 / Usage 与 RuntimePolicy 分层

必须区分：

```text
Platform API Key rate limit → Key调用速率
Tenant quota               → 租户可消费额度
Model RuntimePolicy        → Provider/模型自身并发、QPS、超时
```

调用链保持：现有 API Key 权限/RateLimit/Tenant Quota → Model Runtime Resolver → RuntimePolicy → Provider → 现有 Usage/Token 计量。Model Center 不新建第二套平台配额体系，也不能绕过原有 Usage 记账。

## 53.2 内容安全 / Moderation Pipeline 不变

Model Center 只替换模型解析和调用底座，不改变现有 CHAT_INPUT、AI_OUTPUT、WORKFLOW_INPUT、WORKFLOW_OUTPUT、Streaming/SSE 审核的位置和语义。CapabilityAdapter 不能绕过原有内容安全链路。

## 53.3 Provider BaseURL SSRF 防护

所有 Connection 只允许 `http/https`；禁止 file/ftp/gopher/jar 等协议、云元数据地址；PUBLIC 模式默认拒绝 loopback、link-local、RFC1918 私网及 DNS 解析后的上述目标；重定向后必须重新校验。INTERNAL 模式可服务 Ollama/vLLM/内部 Gateway，但仍禁止云元数据地址，并复用现有接口权限决定谁能创建/修改 INTERNAL Connection。

## 53.4 `extra_config` 禁止存 Secret

Provider Profile 区分 `credentialSchema` 与 `connectionConfigSchema`。API Key、SecretKey、Token、Password、Authorization 等敏感字段必须进入 `credential_ciphertext`；`extra_config` 只允许 Provider Profile `connectionConfigSchema` 白名单声明的非敏感字段，`additionalProperties=false`；后端同时做递归、大小写不敏感的 Secret 防御校验，拒绝 API Key、SecretKey、Token、Password、Authorization、Credential、Header 等敏感/任意请求头字段误入。

## 53.5 Retry 按 Capability / Protocol 幂等性判断

系统默认 `retryCount = 0`。只有明确安全或 Provider 支持 Idempotency-Key 时才允许自动重试。Image/Video/TTS 等收费型或任务型能力在请求状态不确定时默认不自动重试。

## 53.6 并发 / QPS 在生产多实例环境必须是集群级

生产使用 Redis / 分布式 Semaphore / 分布式令牌桶；单机开发模式可 local fallback。RuntimePolicy 的并发/QPS语义始终是 Polaris 集群级，而不是单 JVM 级。

## 53.7 CapabilityParameterComposer

统一组合 Invocation Capability 参数、Feature Config、Model Defaults、Application Overrides、Request Overrides。Protocol Adapter 不允许自己重新查询 Feature 表。

例如：CHAT_COMPLETION.temperature=0.7 + REASONING.effort=high，最终由 Adapter 映射为 Provider 所需请求字段。

## 53.8 Capability 更新语义

普通“取消启用”使用 `enabled=false` 并保留 config_json；重新启用时恢复配置并重新 Schema 校验。只有显式永久删除动作才物理删除能力记录。Runtime / Router 只使用 `enabled=1`。

## 53.9 `model_type` 一致性校验

`model_type` 继续作为 UI primary type，但服务端根据 Invocation Capability 做一致性检查，禁止出现 `model_type=EMBEDDING` 但只启用 AUDIO_TTS 等明显冲突。

## 53.10 Schema Default 保存时物化

保存模型时执行 Schema Default + 用户输入 → Normalize → Validate → 保存完整 Model Capability Defaults。这样 Schema 资源默认值以后变化，也不会让已保存模型行为静默漂移；`schema_hash` 继续负责发现规则变化。

---

# 54. 功能上线顺序

本文后续第 77～143 节给出详细实施规范；本节只保留唯一的 Canonical Phase Map，后续所有 Codex Prompt、PR 拆分和验收均以此为准：

```text
Phase 1  数据底座与开发过渡结构
Phase 2  Schema Engine
Phase 3  Provider / Protocol / Discovery
Phase 4  Unified Runtime
Phase 5  现有业务逐模块切换
Phase 6  Schema Driven Model Editor
Phase 7  Audio TTS/STT 架构验收
Phase 8  Final Cleanup + Reset + Video/Rerank 架构验证
```

关键顺序约束：

```text
Phase 1 不立即删除旧字段，保证每个 PR 可编译、可启动
Phase 4 先跑通 CHAT_COMPLETION 新 Runtime
Phase 5 完成 CHAT → Embedding/Knowledge → Image → Agent → Workflow → OpenAPI
Phase 6 再正式替换为统一 Schema Driven Editor
Phase 7 用 Audio 证明架构无需主表/主DTO/专属大表单扩展
Phase 8 才删除旧字段、旧 Runtime 分支和临时 Feature Flag
```

禁止再使用早期“Phase 5=Editor、Phase 6=业务接入”的旧编号。

---

# 55. 发布开关

即使模型数据可重建，也建议运行代码切换期间提供：

```text
model.center.new-editor-enabled
model.center.new-runtime-enabled
model.center.audio-enabled
```

原因不是兼容旧模型数据，而是：

```text
CHAT / Knowledge / Agent / Workflow / OpenAPI
```

仍属于现有业务功能。

遇到运行问题时可以快速关闭新入口，而无需数据库回滚。

新系统稳定后可删除临时 Feature Flag。

---

# 56. 功能保证：CHAT

必须完整回归：

```text
普通聊天
流式聊天
Temperature
Max Tokens
Reasoning
Tool Calling
Vision Input（若模型支持）
上下文/历史策略仍由会话或Agent管理
```

验收要求：

```text
ModelRuntimeResolver 可生成正确 Runtime Spec
CHAT_COMPLETION 正常调用
STREAMING Feature 正确绑定 CHAT_COMPLETION
REASONING Feature 正确绑定 CHAT_COMPLETION
```

---

# 57. 功能保证：Knowledge / Embedding

必须完整回归：

```text
Embedding模型选择
知识库创建
文档向量化
向量检索
批量Embedding
维度读取
```

必须增加：

```text
Embedding Dimension Compatibility Guard
```

防止知识库建立后模型维度被静默改变。

---

# 58. 功能保证：Image

必须完整回归：

```text
文生图
图生图 / Image Edit
Inpaint（若已有）
尺寸参数
图片任务
并发限制
```

原来图片专属并发策略迁为：

```text
IMAGE_* Runtime Policy
```

不能错误使用全模型统一并发覆盖。

---

# 59. 功能保证：Agent

必须完整回归：

```text
模型选择
System Prompt
Knowledge Base
Tools
Tool Calling
流式输出
```

模型中心只提供：

```text
CHAT_COMPLETION
TOOL_CALLING
STREAMING
VISION_INPUT
```

Agent 自己保存业务策略。

---

# 60. 功能保证：Workflow

必须完整回归：

```text
固定模型
默认模型
节点参数Override
模型能力校验
发布版本不可变
执行实例可追溯
```

必须实现 Runtime Snapshot，不能只保存 `modelRevision`。

---

# 61. 功能保证：OpenAPI

必须完整回归：

```text
GET /platform/api/v1/models
POST /platform/api/v1/chat/completions
Streaming
API Key / Tenant 权限
错误码
```

OpenAPI `model` 使用：

```text
model_code
```

不得直接以：

```text
remote model_name
```

作为唯一 Polaris 模型身份。

---

# 62. 功能保证：管理端 / 中台端原有查询权限

本次 Model Center V2 **不改变现有查询权限语义**。

必须回归：

```text
管理端模型列表 / 详情 / CRUD 的原有数据范围
中台端模型列表 / 详情 / CRUD 的原有数据范围
中台 Key 身份解析与 Tenant 上下文
现有部门 / 数据权限过滤
现有角色、菜单、接口权限
越权 Connection ID
越权 Model ID
OpenAPI 原有租户隔离
```

要求：

```text
相同测试数据 + 相同身份 / Key
重构前后的可见结果集和越权行为保持一致
```

禁止为了 Model Center V2 新增：

```text
ModelAccessPolicy
ScopeResolver
ModelCenterContext
新的平台/租户/部门自动共享或覆盖规则
```

如果当前项目已经存在对应 Context / Resolver，则直接复用现有实现，不再创建第二套。

---

# 63. 功能回归矩阵

| 模块 | 必测场景 | 上线阻断 |
|---|---|---|
| Provider | 新建、修改Key、测试连接、停用 | 是 |
| CHAT | 普通、流式、Reasoning | 是 |
| Tool Calling | Agent工具调用 | 是 |
| Embedding | 向量化、维度、批量 | 是 |
| Knowledge | 建库、入库、检索 | 是 |
| Image | 文生图、编辑、并发 | 是 |
| Agent | Prompt、Knowledge、Tools | 是 |
| Workflow | Fixed/Default、Snapshot | 是 |
| OpenAPI | models/chat/stream | 是 |
| 平台Key/配额/计量 | Key rate limit、Tenant quota、Usage记账 | 是 |
| 内容安全 | Chat/Agent/Workflow/OpenAPI输入输出及SSE审核 | 是 |
| 现有查询权限 | 管理端/中台端结果集、Key上下文、跨租户/跨部门越权 | 是 |
| Provider出站安全 | BaseURL SSRF、重定向、INTERNAL/PUBLIC策略 | 是 |
| Runtime限流 | 多实例集群级并发/QPS | 是 |
| Credential | 明文不回传、不打印、extra_config无Secret | 是 |
| Audio | TTS/STT | Audio上线阻断 |
| Video | Schema扩展无主表改动 | 架构验收 |

任何“是”的核心项失败，不允许正式切换模型中心。

---

# 64. 测试建议

## Schema

```text
Schema Load
Schema Merge
Schema Hash
Schema Version
additionalProperties=false
Condition Rules
Parameter Policy
Provider Profile
Model Profile
```

## Runtime

```text
ModelRuntimeResolver
CapabilityRouter
Feature appliesTo
RuntimePolicy Override
Default Model Scope
Revision Cache
Optimistic Lock
```

## Security

```text
Credential Encryption
Credential KEEP/REPLACE/CLEAR
Provider BaseURL SSRF / redirect / metadata endpoint
extra_config Secret 拒绝
安全 Retry / Idempotency
分布式 QPS / Concurrency
现有管理端查询权限回归
现有中台Key/Tenant查询权限回归
现有部门/DataPermission回归
OptionsResolver越权
日志脱敏
```

## Integration

```text
CHAT
Embedding
Knowledge
Image
Agent
Workflow
OpenAPI
Direct Chat Policy
Platform Key RateLimit / Quota / Usage
Moderation Pipeline
Audio
```

---

# 65. 架构验收测试

必须增加类似：

```java
@Test
void shouldSupportAudioWithoutChangingModelEntity() {
    // AUDIO_TTS Schema
    // 保存
    // 读取
    // Runtime Resolve
    // Test Handler
    // AiModelConfig 无 TTS 专属字段
}
```

以及：

```java
@Test
void shouldSupportVideoSchemaWithoutChangingSaveDto() {
    // VIDEO_GENERATION
    // duration/fps/resolution
    // 保存和读取
    // ModelSaveRequest 无视频字段
}
```

---

# 66. Codex 开发约束

整个开发期间必须遵守：

1. 新 Capability 不允许向 `ai_model_config` 增专属字段；
2. 新 Capability 不允许向 `ModelSaveRequest` 增专属参数；
3. 新 Capability 不允许建立一整套专属 Model Editor；
4. Provider 差异优先 Profile，其次 Protocol Adapter；
5. 只有协议真正不同才增加 Protocol Adapter；
6. Runtime 业务不得直接通过 `model_type` 判断能力；
7. `polaris-ai-core` 不得依赖 `polaris-ai` Entity；
8. Schema 不允许执行任意代码 / URL；
9. Model Code 是稳定外部身份；
10. Workflow 发布必须快照最终模型 Runtime 参数；
11. Embedding Dimension 改变必须检查 Knowledge 引用；
12. 原有 CHAT / Knowledge / Image / Agent / Workflow / OpenAPI 回归通过才算模型中心完成；
13. 管理端 / 中台端现有查询权限、Key 身份解析、租户 / 部门数据权限逻辑不得重写；
14. 禁止新增 `ModelAccessPolicy`、新的 `ScopeResolver`、新的 `ModelCenterContext` 等平行权限体系；若项目已有同名/同类组件，只能复用现有实现；
15. Model / Provider Connection 的列表、详情、CRUD、Runtime 按 ID 读取必须遵循现有权限感知查询方式，禁止绕过数据权限直接无条件读取；
16. Capability / RuntimePolicy 等子表不得开放绕过父 Model 的独立公共 CRUD，操作前必须先按现有权限逻辑确认父 Model 可访问。
17. RuntimePolicy 的可继承字段必须允许 NULL，NULL 表示继承上层。
18. Runtime Cache 必须考虑 Connection Revision。
19. ModelRuntimeSpec 必须携带 Resolved Feature 及 Feature Config。
20. Direct Chat 的 System Prompt / History 等业务配置必须有独立落点后，才能从 Model Center 删除。
21. 平台 Key rate limit、Tenant quota、Usage 计量和 Moderation Pipeline 不得被绕过。
22. Provider BaseURL 必须有 SSRF / redirect / metadata endpoint 防护；INTERNAL连接显式声明。
23. extra_config 禁止保存 Secret。
24. Retry 必须按 Capability / Protocol 幂等性判断。
25. 生产多实例环境的并发/QPS限制必须是集群级。
26. Schema Default 保存时必须物化到 config_json。
27. Capability 取消启用默认使用 enabled=false 保留配置。

---

# 67. Codex Prompt 使用规则（冻结）

第 67～72 节早期 Prompt 已被后续详细实施章节替代，不再作为独立规范使用，避免与最终 Phase 编号和开发过渡策略发生冲突。

Codex 开发必须以以下章节为唯一实施来源：

```text
Phase 1 → 第 77～84 节
Phase 2 → 第 85～95 节
Phase 3 → 第 96～102 节
Phase 4 → 第 103～114 节
Phase 5 → 第 115～122 节
Phase 6 → 第 123～132 节
Phase 7 → 第 133～137 节
Phase 8 → 第 138～143 节
```

特别禁止根据旧 Prompt 执行以下已经废弃的做法：

```text
Phase 1 直接 DROP / 重建现有 ai_model_config
已有环境只重建模型中心表但保留悬空业务引用
把 Phase 5/6 的 Editor 与业务迁移顺序颠倒
只使用 Model Revision 而忽略 Connection Revision
把 Enabled Feature 当成每次请求都自动 Activated
```

---

# 73. 最终验收清单

数据库与模型：

- [ ] `model_code` 与 Provider `model_name` 分离；
- [ ] 同一远程模型可配置多个 Polaris 模型实例；
- [ ] 新 Capability 不改主模型表；
- [ ] Feature Capability 支持 `appliesTo`；
- [ ] Runtime Policy 支持 Capability 覆盖；
- [ ] Default Model 遵循调用身份：管理端 DEPT → TENANT，GLOBAL 仅限既有管理员权限，平台 API Key 仅 TENANT；
- [ ] Revision 支持乐观锁和缓存失效；
- [ ] Runtime Cache 同时考虑 Connection Revision；
- [ ] RuntimePolicy 的 NULL 继承语义生效；
- [ ] ModelRuntimeSpec 区分 Invocation Parameters / Feature Parameters / Active Features；

Schema：

- [ ] Schema Version 生效；
- [ ] Runtime Schema Hash 生效，纯 UI 变化不触发 Runtime Hash；
- [ ] `additionalProperties=false`；
- [ ] RequiredWhen 服务端校验；
- [ ] OptionsResolver 白名单；
- [ ] Schema 不可执行任意代码；

安全：

- [ ] Credential 密文；
- [ ] GET 不返回密钥；
- [ ] 日志不输出密钥；
- [ ] Provider Connection 查询/CRUD 复用现有管理端 / 中台端权限逻辑及当前AI模块真实字段约定；
- [ ] Model 查询/CRUD 复用现有管理端 / 中台端权限逻辑及当前AI模块真实字段约定；
- [ ] 中台 Key 身份解析逻辑未改变；
- [ ] 未把当前 `tenant_id / dept_id` 权限字段改造成另一套权限模型；
- [ ] 未新增平行的 ModelAccessPolicy / ScopeResolver / ModelCenterContext；
- [ ] Capability / RuntimePolicy 子表不能绕过父 Model 权限独立访问；
- [ ] Provider BaseURL SSRF / redirect / metadata endpoint 防护生效；
- [ ] extra_config 不保存 Secret；
- [ ] Retry 按幂等性策略执行；
- [ ] 多实例并发/QPS限制为集群级；

现有功能：

- [ ] CHAT 正常；
- [ ] Streaming 正常；
- [ ] Reasoning 正常；
- [ ] Tool Calling 正常；
- [ ] Embedding 正常；
- [ ] Knowledge Base 正常；
- [ ] Image Generation 正常；
- [ ] Image Edit 正常；
- [ ] Agent 正常；
- [ ] Workflow 正常；
- [ ] Workflow 发布 Snapshot 生效；
- [ ] OpenAPI `/models` 正常；
- [ ] OpenAPI `/chat/completions` 正常；
- [ ] OpenAPI Streaming 正常；
- [ ] Direct Chat 的 System Prompt / History 等原有业务能力有明确新落点并正常；
- [ ] 平台 Key rate limit、Tenant quota、Usage 计量正常；
- [ ] 内容安全 / Moderation Pipeline 正常；
- [ ] 原有管理端 / 中台端租户、部门、数据权限查询行为保持不变；

扩展能力：

- [ ] AUDIO_TTS 不改主表；
- [ ] AUDIO_STT 不改主表；
- [ ] VIDEO_GENERATION 测试不改主表；
- [ ] 新参数不改 ModelSaveRequest；
- [ ] 新参数不改 ModelEditor 主体；

以上核心功能全部通过后，才算 Model Center 重构完成，而不是“模型管理页面能保存”就算完成。

---

# 74. 最终实施路线

```text
最终数据库结构
↓
Schema Engine
↓
Provider Connection
↓
Runtime Resolver / Router
↓
Schema Driven Model Editor
↓
CHAT 接入并回归
↓
Embedding / Knowledge 接入并回归
↓
Image 接入并回归
↓
Agent 接入并回归
↓
Workflow + Snapshot 接入并回归
↓
OpenAPI + model_code 接入并回归
↓
AUDIO_TTS / STT 验收
↓
VIDEO / RERANK 扩展验证
```

核心原则：

> **旧模型数据可以重建，但现有产品能力不能退化。**

> **先保证 Runtime 和业务模块能使用新模型中心，再认为模型管理重构成功。**

> **新增模型能力最终应主要通过 Schema / Profile / Adapter 扩展，而不是继续向主表、主 DTO 和主页面堆字段。**
> **Model Center V2 不重新设计权限：管理端、中台端、Key、租户、部门、DataPermission 全部沿用当前系统已有查询逻辑。**
> **模型中心必须嵌入现有平台能力：Key限流、租户配额、Usage计量、内容安全链路不得被绕过。**
> **Provider Connection 是服务器出站入口，必须把 SSRF、Secret 分类、重定向和内网访问作为一等安全边界。**

---

# 75. V2.4 实施约束：本节及后续内容优先级最高

V2.4 不推翻前文架构，而是把前文架构转换为**可连续编译、可连续上线、可回归、可最终清理，并可由 Codex 按 PR Gate 执行**的实施方案。

如果前文较早的 Phase Prompt 与本节之后的实施细则发生冲突，以 **75 节以后**为准。

特别是以下规则正式锁定：

```text
1. 最终数据库可以重建，但开发过程不在第一步物理删除全部旧字段。
2. 不做 V1 数据迁移，不代表开发过程中必须让系统中间态不可运行。
3. 新旧 Runtime 只允许短期 Feature Flag 过渡，最终必须删除旧分支。
4. 权限体系继续完全复用现有 Polaris tenant_id / dept_id / Key / DataPermission。
5. Model Center 不新建模型授权、Scope、Access Policy。
6. Schema/Profile 第一版是代码资源，不支持后台编辑、热加载和动态脚本。
7. Provider 是厂商配置，Protocol 才是通信实现；OpenAI Compatible 厂商优先复用协议 Adapter。
8. Runtime 配置动态化，但业务 Invocation 保持 Java 强类型。
9. Workflow、Image/Video 等异步/发布业务使用非 Secret Runtime Snapshot。
10. Audio 和 Video/Rerank 是架构验收，不允许倒逼主表/主DTO/主Editor扩字段。
```

---

# 76. 实施期数据库状态：Transition Schema 与 Final Schema 分离

## 76.1 为什么需要 Transition Schema

当前现有 Chat、Embedding、Image、Workflow、Factory、Mapper 等代码仍可能直接读取旧 `ai_model_config` 字段。

因此 Phase 1 不允许立即把旧字段全部 DROP，否则会导致后续 Phase 2～5 的开发分支无法保持：

```text
可编译
可启动
现有功能可回归
每个PR可独立验证
```

这不是 Legacy Migration，也不是 V1/V2 长期双读，只是**开发过渡结构**。

## 76.2 Phase 1 Transition Schema

现有 `ai_model_config` 暂时保留旧字段，只增加：

```sql
ALTER TABLE `ai_model_config`
    ADD COLUMN `model_code` varchar(100) DEFAULT NULL
        COMMENT 'Polaris稳定逻辑模型编码',
    ADD COLUMN `connection_id` bigint DEFAULT NULL
        COMMENT 'Provider Connection ID，V2开发过渡期允许NULL',
    ADD COLUMN `revision` bigint NOT NULL DEFAULT 1
        COMMENT '运行配置修订号',
    ADD COLUMN `tenant_scope_key` varchar(32)
        GENERATED ALWAYS AS (
            CASE
                WHEN `tenant_id` IS NULL THEN 'GLOBAL'
                ELSE CONCAT('T:', CAST(`tenant_id` AS CHAR))
            END
        ) STORED
        COMMENT '仅用于model_code唯一索引，不参与权限判断',
    ADD UNIQUE KEY `uk_model_code`
        (`tenant_scope_key`, `model_code`);
```

开发过渡阶段：

```text
旧模型：model_code / connection_id 允许 NULL
新V2模型：model_code / connection_id 必须由V2服务写入
```

最终 Cleanup 后：

```text
model_code NOT NULL
connection_id NOT NULL
```

## 76.3 Transition 阶段不做历史数据迁移

禁止为了“看起来完整”做：

```sql
UPDATE ai_model_config
SET model_code = model_name;
```

旧数据最终本来就允许清理，因此过渡阶段旧记录保持 NULL 即可。

---

# 77. Phase 1：数据底座实施拆分

Phase 1 拆成三个独立 PR。

## 77.1 PR 1A：SQL + Domain

只做：

```text
新增 ai_provider_connection
新增 ai_model_capability
新增 ai_model_runtime_policy
新增 ai_model_default
ai_model_config 增加 model_code / connection_id / revision / tenant_scope_key + uk_model_code
新增对应 Entity
准备 model-center-reset.sql 骨架
```

不修改任何现有 Runtime。

`AiModelConfig` 第一阶段只新增：

```java
private String modelCode;
private Long connectionId;
private Long revision;
```

旧字段先不删。

## 77.2 子表 Entity 的 JSON 字段

Phase 1 建议 Entity 层：

```text
config_json
extra_config
```

暂时用 `String` 存储，不急于引入复杂 JSON TypeHandler。

业务层统一：

```text
String JSON
↕
Map<String,Object>
```

Schema 才是参数业务真相，Entity 只是存储模型。

## 77.3 PR 1B：Mapper + Internal Service

新增：

```text
AiProviderConnectionMapper / Service
AiModelCapabilityMapper / internal Service
AiModelRuntimePolicyMapper / internal Service
AiModelDefaultMapper / Service
ModelReferenceService
ProviderConnectionReferenceService
```

Capability / RuntimePolicy 不创建公共 Controller。

## 77.4 PR 1C：Provider Connection API

实现：

```text
list
detail
create
update
changeStatus
delete
Credential KEEP/REPLACE/CLEAR
revision optimistic lock
VO secret isolation
endpoint security validation
```

仍然不切换现有 Chat/Embedding/Image Runtime。

---

# 78. Provider Connection Service 最终接口边界

管理 API 与 Runtime 获取必须分开。

```java
public interface IAiProviderConnectionService {

    ProviderConnectionVO get(Long id);

    List<ProviderConnectionVO> list(ProviderConnectionQuery query);

    Long create(ProviderConnectionCreateRequest request);

    void update(ProviderConnectionUpdateRequest request);

    void delete(Long id);

    void changeStatus(Long id, String status, Long expectedRevision);

    ProviderConnectionRuntime getRuntime(Long id);
}
```

管理端 `ProviderConnectionVO` 禁止包含：

```text
credentialCiphertext
apiKey
secretKey
token
password
```

只返回：

```text
credentialConfigured = true / false
```

内部 `ProviderConnectionRuntime` 才可以携带解密后的 Credential。

---

# 79. Provider Credential Service

Credential 加解密独立抽象：

```java
public interface ProviderCredentialService {

    String encrypt(Map<String, Object> credential);

    Map<String, Object> decrypt(String ciphertext);

    boolean isConfigured(String ciphertext);
}
```

这样未来可替换：

```text
本地 AES
KMS
Vault
云 Secret Manager
```

而不改 Provider Connection 业务逻辑。

日志、异常、VO、缓存 Key 均禁止包含明文 Credential。

---

# 80. Credential 更新协议

Create：直接接受初始 Credential，不支持 KEEP。

Update：

```java
public enum CredentialAction {
    KEEP,
    REPLACE,
    CLEAR
}
```

正式语义：

```text
KEEP
→ credential 必须为空
→ 保持旧密文

REPLACE
→ credential 必须非空
→ 校验 + 重新加密

CLEAR
→ credential 必须为空
→ ciphertext = NULL
```

禁止通过 null 猜测用户意图。

---

# 81. Provider Connection 乐观锁

禁止：

```text
先 select revision
再 updateById
```

必须：

```sql
UPDATE ai_provider_connection
SET ..., revision = revision + 1
WHERE id = ?
  AND revision = ?;
```

0 行：

```text
PROVIDER_CONNECTION_CONFLICT
```

Model 更新同理。

第一版为了降低遗漏风险，Provider Connection 任意有效 PUT 均可 `revision + 1`。

---

# 82. Provider Endpoint 安全

新增：

```java
public interface ProviderEndpointSecurityValidator {
    void validate(String baseUrl, String networkMode);
}
```

基本规则：

```text
只允许 http / https
必须有 host
禁止 URL userInfo
禁止 file / ftp / gopher / jar 等协议
DNS解析后检查所有IP
重定向后重新校验
云 Metadata 永久禁止
```

PUBLIC：

```text
拒绝 loopback
拒绝 link-local
拒绝 RFC1918 私网
拒绝 metadata
```

INTERNAL：

```text
允许业务所需私网
仍永久禁止 metadata
仍执行DNS/redirect安全校验
```

`network_mode` 只表示出站网络策略，不参与 tenant/dept 权限。

---

# 83. Capability / RuntimePolicy 是 Model Aggregate 子资源

禁止创建：

```text
AiModelCapabilityController
AiModelRuntimePolicyController
```

外部模型保存必须通过 Model Aggregate Service：

```text
Model
+
Capabilities
+
RuntimePolicies
```

在同一权限边界和事务中完成。

Capability 普通取消使用：

```text
enabled = false
```

保留 `config_json`。

第一版可以完全不支持物理删除 Capability。

---

# 84. RuntimePolicy Resolver

`NULL` = 本层不覆盖。

逐字段：

```text
Capability non-null
>
Model non-null
>
System default
```

禁止整对象覆盖。

必须正确处理：

```text
retryCount = 0
```

不能把 0 当成“空”。

推荐独立：

```java
public interface RuntimePolicyResolver {
    RuntimePolicySpec resolve(...);
}
```

并单测：

```text
system only
model partial override
capability partial override
0值
null继承
```

---

# 85. Phase 2：Schema Engine 最终对象模型

第一版是受控 JSON Schema 子集，不实现完整 JSON Schema Draft。

支持：

```text
object
string
integer
number
boolean
array<string>

required
enum
default
minimum
maximum
multipleOf
minLength
maxLength
additionalProperties=false
```

暂不支持：

```text
任意 $ref
anyOf / oneOf 通用表达式
递归 Schema
JavaScript
SpEL
任意正则执行
任意 HTTP
```

Schema Engine 内部使用明确 Java 对象，而不是全程 Map。

---

# 86. Schema 资源必须显式版本化

目录改为：

```text
model-schema/
  capability/
    chat-completion/
      v1.json
      v2.json
    text-embedding/
      v1.json
    audio-tts/
      v1.json

  protocol/
    openai-compatible/
      v1.json

  provider/
    dashscope/
      v1.json

  model/
    qwen3-tts-flash/
      v1.json
```

禁止直接覆盖历史版本文件改变语义。

Schema/Profile 第一版：

```text
代码资源
应用启动加载
不可后台编辑
不可热加载
```

---

# 87. Runtime Schema Hash 与 UI Hash 分离

数据库 `schema_hash` 正式定义为：

> Runtime Schema Hash

只计算：

```text
validation schema
conditionRules
parameterPolicy
必要 Capability 语义
```

不计算：

```text
title
description
placeholder
order
span
group
纯UI布局
```

UI 文案变化不得导致 Runtime Schema Hash 改变。

Canonicalization：

```text
Object key 字典序
Array 保持顺序
Number 标准化
UTF-8
SHA-256
lowercase hex
```

统一通过 `SchemaHasher` 实现。

---

# 88. Schema Merge Engine

固定顺序：

```text
Capability Base
→ Protocol Overlay
→ Provider Profile
→ Model Profile
```

规则：

```text
object → recursive merge
scalar → later wins
array → replace
$remove=true → remove field
```

不使用通用 JSON Merge Patch 直接代替业务规则。

Model Profile 默认只允许进一步收紧约束；如确需放宽，只允许可信内置资源显式声明特殊能力，不能开放给普通后台配置。

---

# 89. Profile 匹配

Model Profile Key：

```text
providerCode + modelNamePattern
```

只支持：

```text
exact
safe glob '*'
```

禁止任意 Regex、SpEL、JS。

优先级：

```text
exact modelName
>
longest glob match
>
provider default
```

同优先级冲突：启动失败。

---

# 90. Schema Registry Fail Fast

应用启动时一次性扫描、解析、编译、校验：

```text
重复 code/version
未知 field type
未知 operator
未知 component
未知 optionsResolver
Feature allowedAppliesTo 错误
Profile引用不存在Capability
Profile冲突
非法 $remove
非法 Schema结构
```

任一错误：

```text
Application startup fail
```

运行时 Registry 为不可变内存结构，不每次重新读 classpath。

---

# 91. Condition Rules DSL

第一版 Operator：

```text
EQ
NE
IN
NOT_IN
EXISTS
NOT_EXISTS
GT
GTE
LT
LTE
```

Rule：

```text
REQUIRED_WHEN
ENABLED_WHEN
VISIBLE_WHEN
```

支持受限：

```json
{
  "all": [...]
}
```

和：

```json
{
  "any": [...]
}
```

建议限制：

```text
最大嵌套深度 3
最大条件数 20
```

服务端必须执行影响合法性的 REQUIRED/ENABLED 规则；VISIBLE 主要用于 UI。

---

# 92. Parameter Policy

默认：

```text
overridable = false
```

支持：

```json
{
  "temperature": {
    "overridable": true,
    "allowedSources": ["APPLICATION", "AGENT", "WORKFLOW", "REQUEST"]
  }
}
```

Override 中：

```text
字段不存在 → 不覆盖
字段存在且 null → 第一版默认非法
```

第一版不需要支持 nullable parameter。

---

# 93. Schema 保存流程

保存 Capability Config：

```text
Schema Default
+
User Input
↓
DefaultMaterializer
↓
Normalizer
↓
ConditionRuleEvaluator
↓
Validator
↓
保存完整 config_json
```

Schema 默认值必须物化，例如用户只填 `voice`，数据库也保存 `format=mp3`、`speed=1` 等已确定默认值。

后端严格类型，不把字符串 `"1.0"` 自动转换为 number `1.0`。

---

# 94. Feature Capability 使用同一 Schema Engine

REASONING、STREAMING、TOOL_CALLING、VISION_INPUT 等不是只有 boolean。

它们可以拥有：

```text
schema
config_json
parameterPolicy
conditionRules
```

Feature Definition 还必须定义：

```text
allowedAppliesTo
```

例如：

```text
REASONING → CHAT_COMPLETION
STREAMING → CHAT_COMPLETION / AUDIO_TTS
```

拒绝非法：

```text
REASONING → IMAGE_GENERATION
```

---

# 95. OptionsResolver

Schema 不允许声明任意 URL。

统一：

```java
public interface SchemaOptionsResolver {
    String code();
    List<SchemaOption> resolve(OptionsResolveContext context);
}
```

前端只传：

```text
connectionId
modelName
capability
field
```

providerCode/protocolCode 必须后端通过权限感知 Connection 查询得到，不信任客户端覆盖。

Options Cache Key 至少包含：

```text
resolverCode
connectionId
connectionRevision
modelName
capability
field
```

---

# 96. Phase 3：Provider / Protocol / Discovery

最终四层：

```text
Provider Profile
→ 厂商默认值/差异

Protocol Adapter
→ 通信协议

Capability Adapter
→ 能力语义

Model Discovery
→ 远程模型/能力提示
```

禁止按厂商建立巨型 Adapter。

DeepSeek、OpenRouter、SiliconFlow、OneAPI、NewAPI、vLLM 等如果协议兼容，优先复用：

```text
OpenAiCompatibleProtocolAdapter
```

只有真正不同协议才新增 Adapter。

---

# 97. ProtocolAdapter Registry

Protocol Code 是代码级能力。

```java
public interface ProtocolAdapterRegistry {
    ProtocolAdapter getRequired(String protocolCode);
    Optional<ProtocolAdapter> find(String protocolCode);
}
```

同一 protocolCode 注册多个 Adapter：启动失败。

Provider Code 则可以是配置级字符串：

```text
新Provider可能只加Profile，不发代码
新Protocol一定需要代码Adapter
```

---

# 98. OpenAI Compatible 的边界

`OPENAI_COMPATIBLE` 只表示协议族，不代表：

```text
/models
embeddings
images
audio
tools
reasoning
streaming全部一定支持
```

Supported Capability 必须综合：

```text
Protocol能力
Provider Profile
Model Profile
Remote Discovery
Manual声明
```

Feature 运行前还必须同时满足：

```text
Model enabled
Protocol supports
Capability Adapter supports
```

---

# 99. Model Discovery 与 Capability Discovery 分离

```java
public interface RemoteModelDiscovery {
    List<RemoteModelInfo> listModels(...);
}
```

```java
public interface ModelCapabilityDiscovery {
    DiscoveredModelCapabilities discover(...);
}
```

`/models` 返回只有 model id 时，不能伪造 Capability 信息。

Discovery 失败：

```text
不能直接判定 Connection 不可用
```

如果 `/models` 不支持：

```text
MODEL_DISCOVERY_NOT_SUPPORTED
```

UI 允许 MANUAL_INPUT。

---

# 100. Discovery API 与缓存

已保存 Connection：

```text
GET /ai/provider-connection/{id}/models
```

后端按 connectionId 权限感知读取配置和 Credential。

未保存 Connection 可以有专用临时测试/发现接口，但：

```text
不落库
不打印Credential
不持久化Request
仍执行SSRF校验
```

Discovery Cache：

```text
connectionId:connectionRevision:discoveryType
```

TTL 短期缓存。

---

# 101. Provider Client Factory

统一：

```java
public interface ProviderHttpClientFactory {
    ProviderHttpClient get(ProviderRuntimeContext context);
}
```

Client Cache：

```text
connectionId:connectionRevision
```

Connection 修改后自然获得新 Client；旧 Client 可 TTL 回收，不立即强杀正在进行的 Streaming。

---

# 102. Provider Error Translation

统一分类：

```text
AUTH_FAILED
RATE_LIMITED
MODEL_NOT_FOUND
REQUEST_INVALID
PROVIDER_TIMEOUT
PROVIDER_UNAVAILABLE
CONTENT_REJECTED
UNKNOWN
```

Provider 原始错误不能直接返回业务层。

日志只记录：

```text
Provider
HTTP status
provider request id
sanitized error type/message
latency
```

不记录完整 Authorization / Prompt / 文件内容 / Base64。

---

# 103. Phase 4：Runtime 两层解析

Runtime 分两层：

```text
ModelDefinitionResolver
→ 可缓存静态运行定义

ModelRuntimeResolver
→ 每次请求动态参数组合
```

`ResolvedModelDefinition` 不包含用户请求 override，也不长期缓存解密 Credential。

Definition Cache：

```text
modelId:modelRevision:connectionRevision:capability:schemaHash
```

每次调用再：

```text
Definition
+
Application/Agent/Workflow Override
+
Request Override
+
Current Credential
↓
ModelRuntimeSpec
```

---

# 104. Runtime Parameter 内部结构

不要把 Invocation 和 Feature 参数无脑扁平化。

内部统一：

```json
{
  "invocation": {
    "temperature": 0.7,
    "maxTokens": 4096
  },
  "features": {
    "REASONING": {
      "effort": "high"
    },
    "STREAMING": {
      "includeUsage": true
    }
  }
}
```

Protocol Adapter 再映射成 Provider 请求格式。

---

# 105. Feature Enabled 与 Activated 分离

必须区分：

```text
Enabled
= 这个模型配置允许使用

Activated
= 当前这次请求实际开启
```

例如：

```text
STREAMING → request.stream=true 才激活
TOOL_CALLING → 当前调用确实携带 tools 才激活
REASONING → application/request明确要求或其activation policy为默认开启
```

不能因为模型支持 Reasoning 就让每次 Chat 都自动进入昂贵 Reasoning。

---

# 106. Override Source

长期接口不要只固定两个 Map。

建议：

```java
public enum OverrideSource {
    APPLICATION,
    AGENT,
    WORKFLOW,
    REQUEST
}
```

ParameterPolicy 用 `allowedSources` 控制来源。

---

# 107. Typed Capability Invocation

Model 配置动态，业务请求保持强类型。

统一：

```java
interface CapabilityInvocation {
    String capabilityCode();
}
```

实现：

```text
ChatInvocation
EmbeddingInvocation
ImageGenerationInvocation
AudioTtsInvocation
AudioSttInvocation
```

禁止最终所有调用退化成：

```java
execute(String capability, Map<String,Object> request)
```

---

# 108. Model Execution Pipeline

推荐固定流水线：

```text
1. resolve model definition
2. resolve active features
3. compose parameters
4. validate override
5. resolve runtime policy
6. load current credential
7. reuse existing platform quota/rate-limit hook
8. model qps limiter
9. model concurrency limiter
10. circuit breaker hook
11. get/build provider client
12. capability adapter
13. protocol adapter
14. provider error translation
15. usage normalization/accounting
16. trace/audit
17. release permits
```

如果平台 Key rate-limit / quota 已经在 Gateway/Filter 层执行，则 Runtime 不能重复扣减。

---

# 109. Rate Limit / Concurrency

两者分离：

```text
QPS → 单位时间请求速率
Concurrency → 同时在途请求数
```

Limiter Key 必须含 Capability：

```text
model:{modelId}:cap:{capabilityCode}
```

生产多实例：

```text
Redis / Redisson / 分布式Semaphore / token bucket
```

不能只用 JVM local Semaphore。

Streaming Permit 必须在：

```text
complete
error
client cancel
timeout
```

之后释放。

---

# 110. Retry

Retry 只包 Provider Transport Call，不包：

```text
Moderation
Quota
Task创建
Usage记账
整个业务方法
```

默认：

```text
retryCount = 0
```

不重试：

```text
AUTH
INVALID_REQUEST
MODEL_NOT_FOUND
CONTENT_REJECTED
```

Image / Video / TTS 状态未知时默认不重试。

Streaming TTS 明确不自动 Retry。

---

# 111. Streaming 内部统一事件

不同协议流统一转换为：

```text
TextDelta
ReasoningDelta
ToolCallDelta
UsageEvent
CompletedEvent
ErrorEvent
AudioMetadata
AudioChunk
```

管理端 Chat 继续通过 Legacy SSE Adapter 保持旧前端协议。

OpenAPI 通过 OpenAI Stream Adapter 输出兼容 SSE。

Runtime 核心不绑定外部 SSE 结构。

---

# 112. Usage Normalization

统一 Usage：

```text
inputTokens
outputTokens
totalTokens
inputCharacters
audioInputMillis
audioOutputMillis
details
```

Provider 不可靠返回时：

```text
null / unknown
```

禁止为了正式计费随意按字符串长度伪造 Token。

Usage Metrics 与 Billing Policy 分离。

---

# 113. Runtime Observability

每次调用至少记录：

```text
traceId
modelId
modelCode
modelRevision
connectionId
connectionRevision
provider
protocol
capability
activeFeatures
latency
success/failure
errorCategory
providerRequestId
usage
```

默认不记录：

```text
完整Prompt
Credential
文件内容
音频正文
图片Base64
```

优先复用现有平台日志/Usage/Workflow Execution 设施，不为 Model Center 重复造日志平台。

---

# 114. Runtime Snapshot 统一结构

Workflow、Image Task、Video Task 等长期/异步场景统一使用：

```java
public record ModelRuntimeSnapshot(
    Long modelId,
    String modelCode,
    Long modelRevision,
    Long connectionId,
    Long connectionRevision,
    String providerCode,
    String protocolCode,
    String baseUrl,
    String modelName,
    String capabilityCode,
    Map<String,Object> invocationParameters,
    Map<String,Map<String,Object>> featureParameters,
    RuntimePolicySpec runtimePolicy,
    Integer schemaVersion,
    String schemaHash
) {}
```

不含 Credential。

运行时：

```text
Snapshot非Secret语义
+
当前 connectionId 的有效 Credential
```

Connection 停用/删除/密钥不可用：明确失败，不自动改 Provider。

---

# 115. Phase 5：业务切换顺序

严格按：

```text
5A1 Chat non-stream
5A2 Chat stream

5B1 Reasoning
5B2 Tool Calling
5B3 Vision

5C1 Embedding direct
5C2 Knowledge create
5C3 document ingestion
5C4 retrieval

5D1 Image generation
5D2 Image edit
5D3 async snapshot

5E Agent

5F1 Workflow draft
5F2 publish snapshot
5F3 execution

5G1 OpenAPI models
5G2 chat
5G3 stream
```

每一步回归完成后再进入下一项。

---

# 116. CHAT 切换

最终业务不再直接：

```text
读取 AiModelConfig.apiKey/baseUrl/temperature/maxTokens
自己构建 Provider Client
```

改为：

```text
modelId
→ CHAT_COMPLETION
→ required/active features
→ ModelRuntimeResolver
→ ModelExecutor
```

现有成熟 `AiModelFactory` 可以先新增：

```java
createChatModel(ModelRuntimeSpec runtime)
```

暂时保留旧入口，直到所有 Chat 路径完成切换。

Direct Chat 的 System Prompt/History/Tool/Search 从 `DirectChatPolicyResolver` 获取，不放回 Model Center。

---

# 117. Embedding / Knowledge 切换

模型筛选：

```text
TEXT_EMBEDDING
```

不再使用：

```text
model_type = EMBEDDING
```

Knowledge 建议持久化：

```text
embedding_model_id
embedding_dimension
embedding_model_revision
embedding_schema_hash
```

Dimension：

```text
配置值
+
模型测试/首次实际Embedding返回维度
```

不一致：

```text
EMBEDDING_DIMENSION_MISMATCH
```

创建、入库、检索阶段均做维度兼容校验。

---

# 118. Image 切换

使用独立 Invocation：

```text
IMAGE_GENERATION
IMAGE_EDIT
IMAGE_INPAINT
IMAGE_VARIATION
```

独立业务调用就定义 Invocation Capability；只是修饰调用才是 Feature/parameter。

Image Task 创建时立即 Snapshot，不在异步执行时重新读取当前模型默认配置。

---

# 119. Agent 切换

Agent 继续保存：

```text
modelId
```

运行时实时 Resolve：

```text
CHAT_COMPLETION
+
所需 Feature
```

Agent 自己保存：

```text
Prompt
History
Knowledge
Tools
Search
```

如果 Agent 没有发布版本概念，不强制 Snapshot。

编辑时如果 Agent 启用了 Tools/Vision 等业务能力，应提前校验目标模型对应 Feature。

---

# 120. Workflow Draft / Published 语义

Draft：

```text
FIXED modelId
或 DEFAULT
动态校验
```

Published：

```text
无论 FIXED 还是 DEFAULT
发布时解析成具体 Model
生成 ModelRuntimeSnapshot
```

运行时禁止重新找“当前默认模型”。

因此：

```text
Draft动态
Published固定
```

作为硬规则。

---

# 121. OpenAPI 切换

外部唯一模型身份：

```text
model_code
```

不 fallback 到 remote `model_name`。

`GET /platform/api/v1/models`：

```text
当前Key/Tenant权限可访问
AND Model enabled
AND Connection enabled
AND CHAT_COMPLETION enabled
```

才返回。

`model_code` 查询必须走原权限感知路径，禁止全局无条件 select。

内部错误通过 OpenAI-compatible Error Adapter 转换成外部错误结构。

---

# 122. Legacy Field 状态与 CI Guard

旧字段状态：

```text
ACTIVE
→ DEPRECATED
→ UNUSED
→ REMOVED
```

模块切换后先 DEPRECATED，不马上删列。

最终增加 CI 脚本扫描：

```text
AiModelConfig.getApiKey
AiModelConfig.getBaseUrl
AiModelConfig.getTemperature
AiModelConfig.getMaxTokens
AiModelConfig.getEmbedding*
AiModelConfig.getImage*
AiModelConfig.getIsDefault
```

最终 Cleanup 阶段有任何遗留引用：

```text
CI fail
```

---

# 123. Phase 6：Schema Driven Model Editor 最终协议

固定页面结构：

```text
ModelBaseInfo
ProviderConnectionSelect
RemoteModelSelect
CapabilitySelector
CapabilityCard
SchemaFormRenderer
RuntimePolicyEditor
ModelTestDialog
```

`model_type` 只用于：

```text
分类
图标
筛选
```

禁止用于渲染能力参数。

`SchemaFormRenderer` 接口不传：

```text
providerCode
modelType
capabilityCode
```

避免内部 if/switch 偷偷硬编码。

---

# 124. Frontend Capability State

推荐：

```text
Invocation key = CHAT_COMPLETION
Feature key = REASONING@CHAT_COMPLETION
Feature key = STREAMING@AUDIO_TTS
```

Feature Key 必须含 `appliesTo`。

Supported 与 Enabled 分离：

```text
Discovery/Profile → Supported / 推荐
User saved config → Enabled
```

自动发现不能静默修改已保存配置。

---

# 125. Connection / Model 切换 UX

切 Connection：

```text
清 Remote Model
清 Supported Discovery
清/重校验 Options
保留基础模型编辑信息
```

切 Remote Model：

```text
重新Discovery
重新Resolved Schema
旧Capability参数若仍合法则保留
不合法则标错
不支持则disable/要求确认
```

禁止静默丢用户配置。

---

# 126. Schema Renderer Component Registry

普通：

```text
input
number
select
slider
switch
tags
```

Custom Field：

```text
voice-selector
image-size-selector
```

允许“字段级专属交互”，禁止“模型类型级整套专属表单”。

禁止：

```text
远程Vue组件
动态import路径
eval
Vue Template URL
```

未知组件：阻止保存并明确提示。

---

# 127. RuntimePolicyEditor 三态语义

每个字段：

```text
INHERIT → 保存 null
VALUE → 保存实际值
```

UI 必须显示“继承”，不能用空 input 含糊表达。

`0` 是合法值，尤其 retryCount。

---

# 128. Model Editor Save / Dirty / Conflict

禁止：

```js
api.save(editorState)
```

必须：

```text
buildPersistableRequest(editorState)
```

只输出：

```text
稳定Model字段
Capability code/version/enabled/config
RuntimePolicies
expectedRevision
```

Schema、uiSchema、Options、Loading/Error UI state 不上传作为可信配置。

Dirty Check 也基于 persistable request，而不是整个 editorState。

`MODEL_CONFIG_CONFLICT`：提示刷新最新配置，不做复杂协同 Merge。

---

# 129. Model Test Draft

Model Test 必须走真实：

```text
RuntimeResolver
→ CapabilityAdapter
→ ProtocolAdapter
```

未保存草稿测试只传：

```text
connectionId
modelName
capability code/version/config
runtime policy
测试能力
```

后端重新加载可信 Schema/Profile 并校验。

不允许前端把完整 Schema 当可信输入传回来。

---

# 130. Admin / Middle Platform UI 复用

可以有不同菜单入口，但共用：

```text
ProviderConnectionList
ProviderConnectionEditor
ModelList
ModelEditor
DefaultModelEditor
```

权限差异继续来自现有：

```text
登录身份
Key
Tenant Context
Dept/DataPermission
按钮/菜单权限
```

禁止建立：

```text
AdminModelEditor
TenantModelEditor
```

两套业务逻辑。

---

# 131. Default Model UI

Default Model 建议独立管理：

```text
Chat Completion → xxx
Text Embedding → xxx
Image Generation → xxx
Audio TTS → xxx
```

旧“设为默认 Chat/Embedding/Image”按钮可以保留为兼容 UX，但内部统一写：

```text
ai_model_default
```

最终不存在 `AiModelConfig.isDefault` 双真相源。

---

# 132. 前端架构验收 Capability

增加：

```text
TEST_VIDEO_GENERATION
```

Schema 参数：

```text
duration
fps
resolution
```

要求：

```text
不改 ModelEditor 主结构
不加 VideoModelForm
不改 ModelSaveRequest
不 ALTER ai_model_config
```

仍可完成：

```text
动态渲染
保存
读取
再次编辑
```

---

# 133. Phase 7：Audio TTS/STT 架构验收

Audio 必须证明：

```text
文本输入 → 音频输出
音频输入 → 文本输出
```

仍不需要主模型结构专属字段。

新增主要落点只应是：

```text
Schema
Profile
Typed Invocation/Result
Capability Adapter
Protocol Mapping
Tests
```

---

# 134. TTS

Capability：

```text
AUDIO_TTS
```

Feature：

```text
STREAMING@AUDIO_TTS
```

参数示例：

```text
voice
format
sampleRate
speed
pitch
```

Voice 使用 `PROVIDER_VOICES` OptionsResolver，不在 Vue 中按 Provider 写死音色列表。

Typed Invocation：

```java
public record AudioTtsInvocation(
    String text,
    boolean stream,
    Map<String,Object> overrides
) implements CapabilityInvocation {}
```

---

# 135. STT

Capability：

```text
AUDIO_STT
```

参数：

```text
language
timestamps
speakerDiarization
punctuation
```

输入使用可扩展 AudioInput：

```text
ByteArrayAudioInput
StreamAudioInput
ObjectStorageAudioInput
```

避免大音频强制一次性读进 JVM 内存。

结果：

```text
text
segments(start/end/speaker)
usage
```

Provider 不支持时间戳时不伪造。

---

# 136. Audio Streaming

统一内部事件增加：

```text
AudioMetadata
AudioChunk
```

Runtime 不固定：

```text
SSE Base64
WebSocket binary
HTTP chunked
```

这些属于业务输出 Adapter。

Streaming TTS 默认禁止自动 Retry，避免重复播放和重复计费。

---

# 137. Audio Usage

Usage 可表达：

```text
inputCharacters
audioInputMillis
audioOutputMillis
tokens
```

Model Center 负责指标标准化，不自己定义：

```text
1秒音频 = N token
```

计费/Quota 换算属于平台 Billing Policy。

---

# 138. Phase 8：Final Cleanup Gate

只有全部通过才能 Cleanup：

```text
CHAT
Streaming
Reasoning
Tool Calling
Embedding
Knowledge
Image
Agent
Workflow
OpenAPI
Audio架构验收
权限回归
Moderation
Platform RateLimit
Tenant Quota
Usage
```

任何关键项失败：不删旧字段。

---

# 139. 最终 ai_model_config

最终只保留稳定身份：

```text
id
tenant_id
dept_id
name
model_code
connection_id
model_name
model_type
description
revision
status
del_flag
create_by
create_time
update_by
update_time
remark
tenant_scope_key
```

删除：

```text
provider
api_key
base_url
temperature
max_tokens
reasoning旧字段
embedding_*
image_*
is_default
system_prompt
max_history_messages
模型层tool/search字段
```

---

# 140. Final Legacy Code Cleanup

删除：

```text
旧 selectDefaultChatModel/selectDefaultEmbeddingModel
cleanDefaultStatus
model_type运行筛选旧Mapper
旧 AiModelFactory(AiModelConfig)入口
V1 Runtime分支
临时FeatureFlags
旧固定模型参数表单
旧模型Credential字段DTO/VO
```

保留但内部适配的旧 API 路径，仅在确认仍被现有页面/外部依赖时保留；其实现必须已经走 V2 Service，不得继续读取旧字段。

---

# 141. Final SQL

最终必须提供两个入口；现有 `sql/ry_ai.sql` 保持不修改：

```text
sql/ry_ai.sql + sql/model-center-v2.4/全部增量SQL
model-center-reset.sql
```

两者执行后的 Model Center Schema 必须完全一致。

Reset 顺序必须先处理旧 `model_config_id` 业务引用，再重建 Model Center。

本方案不承诺旧 Agent / Knowledge / Conversation / Image / Workflow 数据无损保留。

Reset 文件名和头部必须显式高危提示，正式环境执行前要求备份。

---

# 142. Cache 最终收敛

最终只保留明确版本化缓存：

```text
Schema Registry
Resolved Model Definition Cache
Provider Client Cache
Options/Discovery Cache
```

Key 依赖 revision/hash 自然失效。

逐步删除旧：

```text
AiModelFactory legacy cache
ModelConfig全局cache clear
Controller主动clearAll
```

Revision-based key 优先于大范围主动清缓存。

---

# 143. Video / Rerank 最终架构验证

Cleanup 后再验证两类完全不同能力。

VIDEO_GENERATION：

```text
duration
fps
resolution
aspectRatio
```

RERANK：

```text
Invocation：query + documents
Config：topN / returnDocuments
```

两者都必须做到：

```text
0 ALTER ai_model_config
0 ModelSaveRequest新增类型字段
0 ModelEditor主体修改
```

如果成立，说明架构不仅适合生成模型，也适合检索/排序类模型。

---

# 144. V2.4 推荐最终 PR 路线

```text
PR 1A SQL + Domain
PR 1B Mapper + Internal Services
PR 1C Provider Connection API

PR 2A Schema object model + Registry
PR 2B Merge / Hash / Validator / Condition / Policy
PR 2C Profiles / Options / Contract Tests

PR 3A Protocol Registry + OpenAI Compatible
PR 3B Discovery + Provider Profiles
PR 3C Provider Client / Error Translation / Model Test foundation

PR 4A Definition Resolver + Cache
PR 4B Parameter / Policy / Limiter / Retry
PR 4C Executor / Stream Event / Usage
PR 4D CHAT new Runtime path

PR 5A CHAT non-stream/stream
PR 5B Reasoning/Tool/Vision
PR 5C Embedding/Knowledge
PR 5D Image/Snapshot
PR 5E Agent
PR 5F Workflow
PR 5G OpenAPI

PR 6A Editor Context/List/Detail VO
PR 6B Schema Renderer
PR 6C Capability Selector/Cards
PR 6D RuntimePolicy/Test
PR 6E Provider UI/Admin+Middle Platform reuse

PR 7 Audio TTS/STT architecture acceptance

PR 8 Final Cleanup + Reset + CI guards
```

原则：

> 每个 PR 都应保持项目可编译、可启动；除明确业务切换 PR 外，不能无意改变已有产品行为。

---

# 145. V2.4 CI 架构守卫

建议在 CI 增加：

```text
Legacy AiModelConfig field usage scan
Schema registry contract tests
Schema hash determinism tests
Feature allowedAppliesTo tests
RuntimePolicy null inheritance tests
Connection optimistic lock tests
SSRF tests
Secret leak tests
Runtime cache revision tests
Streaming permit release tests
Embedding dimension guard tests
Workflow snapshot immutability tests
TEST_VIDEO_GENERATION no-main-table-change test
RERANK no-main-table-change test
```

以及代码审查红线：

```text
出现新的 audio_* / video_* 主表字段 → 拒绝
出现新的 AudioModelForm/VideoModelForm → 拒绝
出现 Runtime 通过 model_type 判断能力 → 拒绝
出现 capability/controller 独立越过父Model权限 → 拒绝
出现任意 Schema URL/JS/SpEL → 拒绝
出现 Credential 明文返回/日志 → 拒绝
```

---

# 146. V2.4 最终验收 Definition of Done

只有以下全部成立，Model Center V2.4 才算完成：

```text
数据库
✓ 主模型表稳定化
✓ Provider Connection分离
✓ Capability/Feature结构稳定
✓ RuntimePolicy NULL继承
✓ Default Model单一真相源

Schema
✓ Versioned resources
✓ Runtime hash稳定
✓ Merge规则稳定
✓ Server-side validation
✓ Options白名单
✓ Fail-fast registry

Runtime
✓ Model/Connection revision cache生效
✓ Credential不进通用Definition cache
✓ Typed Invocation
✓ Feature Enabled/Activated分离
✓ Distributed QPS/Concurrency
✓ Retry幂等控制
✓ Unified Stream Event
✓ Unified Usage

业务
✓ Chat
✓ Embedding/Knowledge
✓ Image
✓ Agent
✓ Workflow Snapshot
✓ OpenAPI model_code
✓ Direct Chat Policy

平台能力
✓ 原Key/Tenant/Dept/DataPermission不变
✓ Platform rate limit不变
✓ Tenant quota不变
✓ Usage不变
✓ Moderation不变

前端
✓ One ModelEditor
✓ Schema Renderer
✓ Capability/Feature appliesTo UI
✓ RuntimePolicy inheritance UI
✓ Admin/Middle Platform组件复用

扩展验收
✓ AUDIO_TTS
✓ AUDIO_STT
✓ VIDEO_GENERATION architecture test
✓ RERANK architecture test

清理
✓ Legacy Runtime removed
✓ Legacy fields removed
✓ Legacy flags removed
✓ 原始安装 SQL + 全部增量 SQL 与 reset schema 一致
✓ CI legacy usage scan = 0
```

---

# 147. 最终架构结论

最终稳定结构：

```text
Existing Polaris Identity / Key / Tenant / Dept / DataPermission
                         │
                         ▼
               Provider Connection
                         │
                         ▼
                     AI Model
                         │
             ┌───────────┴───────────┐
             ▼                       ▼
     Invocation Capability     Feature Capability
             │                       │
             └───────────┬───────────┘
                         ▼
                 Versioned Schema
                         │
                         ▼
              Model Definition Resolver
                         │
                         ▼
             CapabilityParameterComposer
                         │
                         ▼
                Runtime Policy
                         │
                         ▼
                 Model Executor
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
       Capability Adapter      Protocol Adapter
                                    │
                                    ▼
                           Provider / SDK / HTTP
```

最终必须坚持的五条边界：

> **权限不重做：沿用 Polaris。**

> **模型身份不等于 Provider 远程模型名：model_code 与 model_name 分离。**

> **能力变化不改主结构：Capability + Schema。**

> **厂商变化优先 Profile，协议变化才新增 Protocol Adapter。**

> **开发允许过渡，最终不保留 Legacy 双运行。**

---

# 148. V2.4 最终一致性冻结规则

本节用于消除历史版本合并过程中可能产生的前后歧义。若前文任何旧示例与本节冲突，以第 77～162 节的最终实施、漏洞修正与 Codex 执行规则为准。

## 148.1 唯一 Phase 顺序

```text
1 数据底座/过渡
2 Schema
3 Provider/Protocol/Discovery
4 Runtime
5 业务迁移
6 Editor
7 Audio验收
8 Cleanup/Reset/Video+Rerank验证
```

## 148.2 RuntimePolicy 唯一继承语义

```text
Capability non-null
>
Model non-null
>
System default
```

`NULL = 本层不覆盖`。Provider Connection 第一版不承载另一套模型 RuntimePolicy 默认值；网络连接级参数如果未来需要，必须通过独立、明确的数据模型引入，不能隐式塞入 `extra_config` 改写上述优先级。

## 148.3 Runtime DTO 唯一语义

```text
ResolvedModelDefinition
= 可缓存：
  Model/Connection身份与revision
  Invocation默认参数
  Enabled Feature及配置
  RuntimePolicy
  Runtime Schema Hash
  不含解密Credential
  不含请求级Override

ModelRuntimeSpec
= 请求级：
  Invocation Parameters
  Feature Parameters
  Active Features
  Current Credential
  已解析RuntimePolicy
  Runtime Schema Hash
```

Enabled Feature 与 Active Feature 永远不是同一个概念。

## 148.4 Schema Hash 过渡语义

Phase 1A 数据库 `ai_model_capability.schema_hash` 暂存单一 Capability 的 **Runtime Schema Hash**；Phase 2 起按 163.6 升级为分用途 Schema refs + `runtime_definition_hash`，不得继续用一个 hash 混合表达 Invocation 与多个 Feature Schema。

参与：

```text
validation schema
conditionRules
parameterPolicy
必要Capability语义
```

不参与：

```text
纯UI title/description/placeholder/order/span/group/layout
```

纯 UI 改动不得迫使历史模型重新保存。

## 148.5 Capability 更新唯一语义

第一版普通模型编辑：

```text
启用    → enabled=1
取消启用 → enabled=0，保留 config_json
```

第一版可以完全不提供物理删除 Capability 的普通 UI/API。若未来增加永久删除，必须是显式动作，不能把“请求中没出现”解释成自动删除。

## 148.6 Reset 唯一语义

全新系统先执行现有 `sql/ry_ai.sql`，再按文件名前缀顺序执行 `sql/model-center-v2.4/` 下全部增量 SQL，得到最终结构。禁止直接修改 `sql/ry_ai.sql`。

`model-center-reset.sql` 用于明确接受数据重置的已有环境，并且必须先处理旧 Model ID 的所有业务引用。禁止：

```text
只 DROP ai_model_config
+
保留 Agent/Knowledge/Conversation/Image/Workflow 旧引用
```

本方案不承诺旧 AI 业务数据无损迁移。

## 148.7 权限唯一语义

```text
tenant_id BIGINT / NULL语义沿用当前项目
dept_id BIGINT / NULL语义沿用当前项目
```

Model Center 不新增授权体系、不新增平行 Scope Resolver、不把 Default Model Scope 当成可见性规则。

## 148.8 Snapshot 唯一语义

Workflow / Image / Video 等发布或异步任务冻结：

```text
Model/Connection ID + Revision
Provider/Protocol/BaseURL/ModelName
Capability
Invocation Parameters
Feature Parameters
RuntimePolicy
Schema Version/Hash
```

不冻结 Credential 明文；执行时只读取 snapshot.connectionId 当前有效 Credential。Connection 被停用/删除时明确失败，不自动漂移到别的 Provider。

## 148.9 最终开工 Gate

开始 Phase 1 编码前必须确认：

- [ ] 当前 `dev` 的 tenant/dept/audit 字段类型再次核对；
- [ ] 现有 `sql/ry_ai.sql` 保持不修改，所有数据库变化均进入新增的编号增量 SQL；
- [ ] Phase 1 不删除旧运行字段；
- [ ] Capability/RuntimePolicy 无独立公共 Controller；
- [ ] Credential 更新三态 KEEP/REPLACE/CLEAR；
- [ ] RuntimePolicy nullable override；
- [ ] Runtime Schema Hash 规则已写测试；
- [ ] Connection Revision 已进入缓存设计；
- [ ] Direct Chat Policy 有明确迁移落点；
- [ ] API Key rate-limit / Tenant quota / Usage / Moderation 列入回归阻断项；
- [ ] Reset 脚本不会制造悬空 Model ID 引用。

满足以上条件后，方案可以冻结并进入实现阶段。

---

# 149. 2026-09-21 当前 `dev` 执行基线

Codex 开工前必须先对当前 `dev` 做 `git status` 和源码定位。不得自行切换/新建分支或 worktree，不得执行 Git commit；工作树已有改动必须保留。本节中的公开 `master` 链接只作为历史源码锚点，不替代执行时的实际 `dev` 源码。

公开锚点：

```text
仓库：
https://gitee.com/Li_kui/polaris-vue

当前模型 Mapper：
https://gitee.com/Li_kui/polaris-vue/blob/master/polaris-ai/src/main/java/com/polaris/ai/mapper/AiModelConfigMapper.java

当前模型 Service：
https://gitee.com/Li_kui/polaris-vue/blob/master/polaris-ai/src/main/java/com/polaris/ai/service/impl/AiModelConfigServiceImpl.java

Vue3 前端目录：
https://gitee.com/Li_kui/polaris-vue/tree/master/polaris-ui-vue3
```

冻结时已核对到：

```text
dev 存在 polaris-ui-vue3，README 明确 Vue3 + JavaScript + Vite/Element Plus；
AiModelConfigMapper 仍有 model_type='CHAT'、model_name、is_default、
selectDefaultModel、cleanDefaultStatus 等旧架构查询；`is_default_embedding` 只存在于过时注释/方法命名，真实 SQL 列不存在；
Workflow 查询仍存在直接按 model_type/model_name 读取旧模型的路径；
AiModelConfigServiceImpl 仍直接代理 selectAvailableModelConfigsByType、
selectDefaultModel、insertModelConfig、updateModelConfig、cleanDefaultStatus 等旧入口。
```

因此 Phase 1A 必须遵守：

> **只加 V2 底座，不删除旧字段，不切现有 Runtime，不顺手重构旧 Mapper/Service。**

如果 Codex 执行时 `dev` 已与上述事实不同：

```text
停止直接套用行号/文件假设
→ 以当前实际代码为准重新映射
→ 保持本文架构约束不变
→ 在执行报告中列出差异
```

---

# 150. Phase 1A 可直接实施规格

## 150.1 PR 目标

PR 1A 的唯一目标：

```text
数据库具备 V2 数据底座
+
Java 具备 V2 存储 Entity/Mapper
+
当前产品行为 = 0 非预期变化
```

理想 Diff：

```text
A  sql/model-center-v2.4/001_phase1a_model_center_base.sql
A  sql/model-center-v2.4/README.md  # 增量SQL执行顺序、适用环境与回滚说明
A  sql/model-center-reset.sql       # 本阶段仅安全骨架

M  AiModelConfig.java

A  AiProviderConnection.java
A  AiModelCapability.java
A  AiModelRuntimePolicy.java
A  AiModelDefault.java

A  AiProviderConnectionMapper.java
A  AiModelCapabilityMapper.java
A  AiModelRuntimePolicyMapper.java
A  AiModelDefaultMapper.java

A/M 对应数据库/Mapper测试
```

以下文件 Phase 1A 理想为 0 Diff：

```text
AiModelConfigController
AiModelConfigServiceImpl
AiModelConfigMapper
AiModelFactory
Chat Runtime
Knowledge Runtime
Image Runtime
Agent Runtime
Workflow Runtime
OpenAPI Runtime
```

## 150.2 Transition SQL

`sql/model-center-v2.4/001_phase1a_model_center_base.sql` 是追加式增量 SQL：已有开发库直接执行；全新环境先执行原 `sql/ry_ai.sql`，再执行本文件。该文件必须同时包含四张新表的 `CREATE TABLE` 与以下 `ALTER TABLE`：

```sql
ALTER TABLE `ai_model_config`
    ADD COLUMN `model_code` varchar(100) DEFAULT NULL
        COMMENT 'Polaris稳定逻辑模型编码',
    ADD COLUMN `connection_id` bigint DEFAULT NULL
        COMMENT 'Provider Connection ID，V2开发过渡期允许NULL',
    ADD COLUMN `revision` bigint NOT NULL DEFAULT 1
        COMMENT '运行配置修订号',
    ADD COLUMN `tenant_scope_key` varchar(32)
        GENERATED ALWAYS AS (
            CASE
                WHEN `tenant_id` IS NULL THEN 'GLOBAL'
                ELSE CONCAT('T:', CAST(`tenant_id` AS CHAR))
            END
        ) STORED
        COMMENT '仅用于model_code唯一索引，不参与权限判断',
    ADD UNIQUE KEY `uk_model_code`
        (`tenant_scope_key`, `model_code`);
```

过渡期：

```text
旧模型：
model_code = NULL
connection_id = NULL
继续由旧 Runtime 使用

V2 新模型：
model_code != NULL
connection_id != NULL
```

禁止：

```sql
UPDATE ai_model_config
SET model_code = model_name;
```

本方案不做旧模型身份迁移。

Transition DDL 不是最终结构；Codex 不得修改现有 `sql/ry_ai.sql`。后续结构变化继续新增 `002_...sql`、`003_...sql`，不得回写历史增量文件。

## 150.3 `ai_provider_connection`

第一版不保留语义未定义的 `access_mode`。若未来真正出现 Relay/Gateway 需求，再以明确执行语义新增，不预埋无实现字段。

```sql
CREATE TABLE `ai_provider_connection` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint DEFAULT NULL,
    `dept_id` bigint DEFAULT NULL,

    `connection_name` varchar(100) NOT NULL,
    `provider_code` varchar(50) NOT NULL,
    `protocol_code` varchar(50) NOT NULL,
    `network_mode` varchar(20) NOT NULL DEFAULT 'PUBLIC',

    `base_url` varchar(500) DEFAULT NULL,
    `credential_ciphertext` text DEFAULT NULL,
    `extra_config` json DEFAULT NULL,

    `revision` bigint NOT NULL DEFAULT 1,
    `status` char(1) NOT NULL DEFAULT '1',
    `del_flag` char(1) NOT NULL DEFAULT '0',

    `create_by` varchar(64) DEFAULT '',
    `create_time` datetime DEFAULT NULL,
    `update_by` varchar(64) DEFAULT '',
    `update_time` datetime DEFAULT NULL,
    `remark` varchar(500) DEFAULT NULL,

    PRIMARY KEY (`id`),
    KEY `idx_provider_scope`
        (`tenant_id`,`dept_id`,`status`,`del_flag`),
    KEY `idx_provider_protocol`
        (`protocol_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='AI Provider连接配置';
```

## 150.4 `ai_model_capability`

```sql
CREATE TABLE `ai_model_capability` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `model_config_id` bigint NOT NULL,

    `capability_code` varchar(64) NOT NULL,
    `applies_to_capability_code` varchar(64) NOT NULL DEFAULT '',

    `schema_version` int NOT NULL DEFAULT 1,
    `schema_hash` char(64) DEFAULT NULL,
    `config_json` json DEFAULT NULL,

    `enabled` char(1) NOT NULL DEFAULT '1',
    `capability_source` varchar(24) NOT NULL DEFAULT 'MANUAL'
        COMMENT 'REMOTE_PROVIDER/MODEL_PROFILE/PROVIDER_PROFILE/PROTOCOL_DEFAULT/INFERRED/MANUAL',

    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_capability`
        (`model_config_id`,`capability_code`,`applies_to_capability_code`),
    KEY `idx_capability_route`
        (`capability_code`,`enabled`,`model_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='AI模型能力配置';
```

最终正式保存的 Capability 必须能得到 Runtime Schema Hash；Phase 1A 因 Schema Engine 尚未启用，表结构允许 `schema_hash=NULL`，但 Phase 2 起 Service 层禁止新建“enabled 且无可解析 Schema”的 Capability。

## 150.5 `ai_model_runtime_policy`

```sql
CREATE TABLE `ai_model_runtime_policy` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `model_config_id` bigint NOT NULL,
    `capability_code` varchar(64) NOT NULL DEFAULT '',

    `max_concurrency` int DEFAULT NULL,
    `connect_timeout_ms` int DEFAULT NULL,
    `read_timeout_ms` int DEFAULT NULL,
    `retry_count` int DEFAULT NULL,
    `qps_limit` decimal(12,4) DEFAULT NULL,
    `priority` int DEFAULT NULL,

    `extra_config` json DEFAULT NULL,
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_runtime`
        (`model_config_id`,`capability_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='AI模型运行策略';
```

所有 Override 字段 Java 必须使用：

```text
Integer
BigDecimal
```

禁止 primitive `int`，否则 `0` 会破坏 `NULL = inherit`。

## 150.6 `ai_model_default`

```sql
CREATE TABLE `ai_model_default` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `scope_type` varchar(20) NOT NULL,
    `scope_id` bigint NOT NULL DEFAULT 0,
    `capability_code` varchar(64) NOT NULL,
    `model_config_id` bigint NOT NULL,
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_default_scope_capability`
        (`scope_type`,`scope_id`,`capability_code`),
    KEY `idx_default_model`
        (`model_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='AI默认模型';
```

不迁移旧 `is_default`；当前真实数据库不存在 `is_default_embedding` 列。

## 150.7 Phase 1A Entity

`AiModelConfig` 只新增：

```java
private String modelCode;
private Long connectionId;
private Long revision;
```

`tenant_scope_key` 是数据库内部生成列，第一版不要求映射到 Entity。

新增四个 Entity：

```text
AiProviderConnection
AiModelCapability
AiModelRuntimePolicy
AiModelDefault
```

`config_json / extra_config` 第一版使用 `String`，不要引入新的 JSON TypeHandler。

四个 Mapper 第一版只继承 `BaseMapper<T>`，不预先堆积业务 SQL。

## 150.8 Phase 1A 测试

至少：

```text
Spring Context / MyBatis 正常启动
旧 model_code=NULL / connection_id=NULL 可继续读取
多条旧 model_code=NULL 不触发唯一冲突
GLOBAL 下相同非NULL model_code 不可重复
不同 tenant 可保存相同 model_code
RuntimePolicy NULL 入库读取仍为 NULL
retryCount=0 可与 NULL 明确区分
STREAMING@CHAT_COMPLETION 与 STREAMING@AUDIO_TTS 可同时存在
相同 code+appliesTo 不可重复
现有 /ai/model 和当前 Runtime 回归不变
```

---

# 151. `model_code` 有效命名空间与 OpenAPI 消歧义

`(tenant scope, model_code)` 唯一索引负责租户内逻辑编码唯一；权限链路继续负责决定调用方可以看到哪些模型。

正式规则：

```text
model_code 服务端统一 lowercase
格式：[a-z0-9][a-z0-9._-]{0,99}
```

`ModelCodeResolver` 只能在当前权限链已经返回的集合中精确查找，不得额外查询 `tenant_id IS NULL` 作为共享兜底。普通平台 API Key 只解析所属租户模型；GLOBAL 仅在既有管理员权限明确允许时可见。V2.4 不提供“租户覆盖共享模型”语义。

`/platform/api/v1/models` 使用同一可见性规则；若当前权限集合内同一个 `model_code` 出现多条，视为数据/权限配置错误，不做优先级猜测或静默去重。

禁止：

```text
随机 selectOne
ORDER BY id 猜优先级
按 model_name fallback
```

如果权限上下文无法形成唯一结果：

```text
MODEL_CODE_AMBIGUOUS
```

而不是静默选一条。

逻辑删除后的 code 默认不立即复用；若未来提供“永久释放编码”，必须是显式管理动作，并先通过 `ModelReferenceService` 确认无外部/业务引用。

---

# 152. Model / Connection Revision、事务与缓存一致性

## 152.1 Model Revision 的触发范围

任何影响 Runtime 的变化都必须在同一事务内使：

```text
ai_model_config.revision + 1
```

包括：

```text
模型稳定字段的运行相关修改
connection_id / model_name
Capability enable/disable
Capability config_json
Capability schema version/hash
Feature config
RuntimePolicy
```

不影响 Runtime 的纯展示字段是否 bump 可以后续优化；第一版允许“Model 任意有效 PUT 都 bump”以降低漏失效风险。

`ai_model_default` 是业务选择规则，不改变目标模型自身 Runtime，修改 Default 不要求 bump 目标 Model revision。

## 152.2 原子更新顺序

推荐 Model Aggregate Update：

```text
BEGIN
↓
按现有权限链路读取父Model
↓
UPDATE ai_model_config
SET ..., revision = revision + 1
WHERE id=? AND revision=expectedRevision
↓
0 row → MODEL_CONFIG_CONFLICT
↓
写 Capability / RuntimePolicy 子表
↓
COMMIT
```

如果任一子表写失败，整个事务回滚，Model revision 也必须回滚。

禁止：

```text
先更新子表
提交
再 bump revision
```

否则缓存可能看到“新子表 + 旧revision”。

## 152.3 不混用两套乐观锁

若采用显式：

```sql
WHERE id=? AND revision=?
```

则不要同时让 MyBatis-Plus `@Version` 再自动递增同一字段，避免双增或 SQL 语义不一致。

## 152.4 多节点 Cache Invalidation

“Revision 作为 Cache Key”仍有一个隐藏前提：节点必须知道**当前 revision**。

因此禁止长期缓存：

```text
modelId → oldRevision
connectionId → oldRevision
```

而没有失效机制。

生产建议：

```text
不可变 Definition Cache：
modelId:modelRevision:connectionRevision:capability:schemaHash

当前 revision 元数据：
DB读取
或短TTL元数据缓存 + after-commit Redis Pub/Sub/事件失效
```

Model / Connection 更新事务提交成功后，再发布失效事件；事务回滚不得发布。

Provider Client Cache 同理使用：

```text
connectionId:connectionRevision
```

旧 Client 允许正在执行的请求自然结束，随后 TTL 回收。

---

# 153. Credential 加密、密钥轮换与日志防泄漏

仅写“数据库存密文”仍不足够。

正式实现最低要求：

```text
AEAD：AES-GCM 或等价认证加密
随机 nonce/IV
主密钥不与数据库同库存储
主密钥来自环境/KMS/Vault/Secret Manager
Ciphertext Envelope 带 keyId / algorithm / version
支持未来 key rotation
```

推荐密文逻辑结构：

```json
{
  "v": 1,
  "alg": "AES-256-GCM",
  "kid": "model-center-key-2026-01",
  "iv": "...",
  "ciphertext": "...",
  "tag": "..."
}
```

更新：

```text
KEEP    → 不解密/重加密旧值
REPLACE → Validate → Encrypt → Replace
CLEAR   → NULL
```

GET / VO 永不返回：

```text
credentialCiphertext
credential
apiKey
secretKey
token
password
```

Credential 还必须从以下位置排除：

```text
Controller request debug log
AOP操作日志的请求体序列化
APM body capture
异常 message
Redis key
Cache key
审计 diff
前端 localStorage/sessionStorage/persisted Pinia
```

未保存 Connection 的 Test/Discovery 接口尤其要关闭请求体日志。

---

# 154. Provider Endpoint SSRF / DNS Rebinding / 测试接口防滥用

仅“解析一次 DNS 后校验”仍可能遭遇 DNS rebinding。

必须同时满足：

```text
URI canonicalize
scheme 仅 http/https
禁止 userInfo
base_url 禁止 fragment
默认禁止把 Credential 放 query string
限制 redirect 次数
每一跳 redirect 重新校验
禁止 https → http 静默降级
解析得到混合 public/private IP 时按最严格规则拒绝
实际连接所用 IP 必须来自已校验解析结果，或在 connect 前重新验证
```

PUBLIC 永久拒绝：

```text
loopback
link-local
RFC1918/private
multicast
unspecified
云 metadata IP/hostname
```

INTERNAL 不是“关闭 SSRF”：

```text
仍拒绝 metadata
仍拒绝危险协议
仍重新校验 redirect/DNS
```

生产建议增加服务端配置的 INTERNAL 网络 Allowlist，例如：

```text
model-center.network.internal-allowed-cidrs
model-center.network.internal-allowed-hosts
```

这是网络安全边界，不是新的模型授权体系。

如果确实需要本机 Ollama：

```text
loopback 只能通过服务端显式 allowlist 开启
```

不能因为客户端传 `networkMode=INTERNAL` 就自动获得 localhost 扫描能力。

Connection Test / Discover Models / OptionsResolver 都是潜在网络扫描与 DoS 入口，因此必须复用当前认证权限，并增加：

```text
调用频率限制
低于真实模型调用的超时
响应大小上限
远程模型列表条数上限/分页
禁止任意请求Body模板
Secret sanitization
```

---

# 155. `extra_config` / Schema / Snapshot 的输入体积与结构上限

动态 JSON 如果没有限制，会成为内存、数据库和日志 DoS 面。

后端统一增加限制，具体数值可按现有网关限制调整，但必须存在：

```text
Provider connectionConfig / extra_config：大小上限
Capability config_json：大小上限
Model Draft Test payload：大小上限
Workflow/Task Runtime Snapshot：大小上限
Schema resource：大小/字段数/嵌套深度上限
Condition DSL：深度<=3、条件数<=20
OptionsResolver 返回条数上限
Remote Model Discovery 分页/条数上限
```

`connectionConfigSchema`：

```text
additionalProperties=false
```

禁止任意 headers / arbitrary HTTP request template 作为普通 `extra_config` 进入 Provider HTTP Client。

Capability Code / Provider Code / Protocol Code 也必须做格式和长度白名单校验，避免不可见字符、超长键和日志污染。

---

# 156. Provider Connection 身份稳定性与 Snapshot 校验

Provider Connection 被多个 Model 引用后，不允许把它当成一个可随意“换厂商”的普通表单。

正式规则：

```text
credential
→ 允许轮换
→ revision +1

providerCode / protocolCode / baseUrl / networkMode / identity-related extraConfig
→ Connection 创建后不可原地修改
→ 如需切换，创建新 Connection，再显式迁移 Model
```

理由：

```text
DeepSeek Connection
```

如果直接改成：

```text
OpenAI Connection
```

当前 Model、缓存、Workflow Snapshot 的 Credential/协议语义都会发生非局部漂移。

Workflow/Image/Video Snapshot 执行时，除了检查 `connectionId` 当前 Credential，还必须验证：

```text
当前 Connection 未删除/未停用
current providerCode == snapshot providerCode
current protocolCode == snapshot protocolCode
normalized current baseUrl == normalized snapshot baseUrl
current networkMode == snapshot networkMode
```

不一致：

```text
RUNTIME_SNAPSHOT_CONNECTION_MISMATCH
```

不得偷偷使用“新厂商 Credential + 旧 Snapshot 协议语义”。

Snapshot 中的 `baseUrl` 属于运行非 Secret 配置，但可能是内部网络信息；普通外部 API/租户不可无条件回显完整 Snapshot。

---

# 157. Schema / Profile / Adapter 的版本保留与部署兼容

“Workflow Snapshot 冻结配置”并不等于“冻结可执行二进制”。

因此进一步锁定：

```text
已发布 Schema/Profile 版本不得原地改变语义
历史版本资源不得因为新版本发布就直接删除
```

部署前增加 Compatibility Check：

```text
数据库 enabled capability 的 schemaVersion 必须仍能由 Registry 解析
stored schema_hash 与同版本 Runtime Schema Hash 必须一致
```

Hash 不一致代表有人原地改了历史 Schema/Profile 语义：

```text
CI/预发布检查失败
```

而不是上线后让所有模型突然 `SCHEMA_HASH_MISMATCH`。

Protocol/Capability Adapter 建议提供：

```text
adapterVersion
```

运行日志和 Snapshot 可记录版本用于排障。

第一版不要求永久保留所有历史 Java Adapter，但必须遵守：

> 同一 adapterVersion 的请求映射保持兼容；破坏性映射变化必须提升 Adapter Version，并为已发布 Workflow 制定迁移/兼容策略。

Provider Profile 不允许演化成“任意 Endpoint/Headers/Request Template 执行器”；运行传输语义继续归 Protocol Adapter，从源头减少 Profile 版本漂移。

---

# 158. Default Model 的异常与回退语义

默认选择顺序按调用身份确定：

```text
管理端部门上下文：DEPT → TENANT
既有全局管理员上下文：在现有权限明确允许时可读 GLOBAL
平台 API Key：TENANT only
```

“回退”只发生在：

```text
本 scope 没有 default row
```

如果某 scope 已配置 Default，但目标模型：

```text
不可访问
已停用
Connection已停用
目标 Invocation Capability已禁用
```

视为配置错误：

```text
DEFAULT_MODEL_INVALID
```

第一版不静默跳到下一层，否则部门管理员会以为正在使用部门默认模型，实际悄悄跑到平台模型。

设置 Default 时必须在同一权限上下文验证目标 Model 可访问且 Invocation Capability Enabled。

Model 删除前 `ai_model_default` 必须计入引用检查。

---

# 159. Codex 执行协议

将本文件直接交给 Codex 时，必须先让 Codex执行以下协议，而不是一次性“按文档把全部 Phase 写完”。

## 159.1 开工前

```text
1. 确认当前分支和工作树状态
2. 读取当前 `dev` 实际代码；不拉取、不切换或新建分支/worktree
3. 定位 pom.xml、模块目录、前端 package.json、现有测试命令
4. 重新核对 ai_model_config/tenant/dept/audit 实际字段
5. 输出“当前代码 vs 文档假设”差异
6. 差异不影响架构时再开始 PR 1A
```

禁止：

```text
猜文件路径
猜 Maven/Node 命令
根据旧聊天摘要修改不存在的类
```

## 159.2 每个 PR 的固定循环

```text
读取当前实现
↓
列出本 PR 修改文件
↓
实施
↓
编译/单测/必要回归
↓
静态搜索禁止项
↓
输出变更摘要
↓
输出测试结果
↓
输出未解决问题
↓
达到 Gate 后才进入下一 PR
```

## 159.3 Scope 控制

Codex 不得因为“顺手优化”：

```text
重构现有权限框架
把 Vue3 JS 改 TypeScript
改现有 OpenAPI 外部协议
重写成熟 LangChain4j/Image Runtime
引入新的模型授权表
改 tenant/dept 语义
```

除非本文对应 Phase 明确要求。

## 159.4 数据库安全

Codex 可以：

```text
在 sql/model-center-v2.4/ 新增有序增量 SQL
为增量 SQL 编写数据库迁移测试
新增 reset 脚本的安全骨架
```

Codex 不得修改既有 `sql/ry_ai.sql`，也不得回写已经交付的编号增量 SQL；任何后续数据库结构变化都新增下一个编号文件。未经用户明确授权，不执行任何业务 SQL。

但不得自动对未知/正式数据库执行：

```text
model-center-reset.sql
DROP 业务表
批量清业务数据
```

破坏性 Reset 必须由用户显式确认执行环境和备份后再运行。

## 159.5 失败处理

出现：

```text
编译失败
测试失败
权限回归失败
Schema hash不确定
现有代码与文档关键假设冲突
```

则：

```text
停止进入下一 Phase
修复当前 PR 或报告阻塞点
```

禁止为了“把任务完成”跳过失败测试。

---

# 160. Phase 1A Codex 冻结 Prompt

以下内容可以直接作为第一次 Codex 执行指令：

```text
基于当前仓库 `dev`，实现 Polaris Model Center V2.4 的 Phase 1A：SQL + Domain 数据底座。

开始前先读取当前实际源码和 SQL，确认工作树、模块、数据库字段与测试方式。
不得新建或切换分支/worktree，不得执行 git commit；保留工作树已有改动。
如果当前 `dev` 与文档假设不同，先列出差异，不要猜。

本 PR 只允许：
1. 新增 ai_provider_connection / ai_model_capability /
   ai_model_runtime_policy / ai_model_default。
2. 对现有 ai_model_config 仅增加：
   model_code(nullable transition)、
   connection_id(nullable transition)、
   revision、
   tenant_scope_key generated column + uk_model_code。
3. AiModelConfig 只增加 modelCode / connectionId / revision。
4. 新增 4 个 Entity 和 4 个 BaseMapper。
5. config_json / extra_config 在 Entity 中暂用 String。
6. RuntimePolicy nullable 数值全部使用 Integer/BigDecimal。
7. 新增 sql/model-center-v2.4/001_phase1a_model_center_base.sql、增量说明和 destructive reset 安全骨架；
   禁止修改 sql/ry_ai.sql；后续变化只能新增 002、003 等编号 SQL。
8. 增加数据库/Mapper测试。

本 PR 禁止：
- 删除现有 ai_model_config 旧字段；
- 修改现有 Chat/Embedding/Image/Agent/Workflow/OpenAPI Runtime；
- 修改现有 AiModelConfigMapper 的旧业务查询语义；
- 迁移旧 model_code 或 is_default；
- 创建 Capability/RuntimePolicy 公共 Controller；
- 新增权限体系；
- 新增数据库 FK/CASCADE；
- 执行 destructive reset；
- 顺手重构 AiModelFactory/Controller/Service。

tenant/dept/create_by/update_by 必须跟当前 `dev` 的真实 AI 业务表一致。
tenant_scope_key 只用于唯一索引，禁止用于权限判断。
model_code 新 V2 路径必须 lowercase 且符合 [a-z0-9][a-z0-9._-]{0,99}。

完成后必须：
- 编译；
- 跑本 PR 相关测试；
- 跑现有关键测试；
- grep 确认现有 Runtime 未改成依赖新字段；
- 输出修改文件、测试结果、与文档任何偏差。
未达到 Gate 不进入 PR 1B。
```

---

# 161. 新增漏洞复核结论与冻结修正

本轮在 V2.3.1 基础上额外发现并关闭以下问题：

```text
[已修] tenant_scope_key 使用 0 sentinel 可能与真实 tenant_id=0 冲突
       → 改为 GLOBAL / T:<id> generated key

[已修] Provider 表预留 access_mode=direct/relay 但没有执行语义
       → 第一版移除，真正需要时再设计

[已修] capability_source 命名过粗且与 Discovery 语义不统一
       → 统一 REMOTE_PROVIDER / MODEL_PROFILE /
         PROVIDER_PROFILE / PROTOCOL_DEFAULT / INFERRED / MANUAL

[已修] tenant 自有 model_code 与共享 model_code 同名会让 OpenAPI 歧义
       → 不新增共享兜底；只在现有权限可见集合内精确解析，重复即 MODEL_CODE_AMBIGUOUS

[已修] 只靠 revision cache key 仍可能缓存“当前 revision 指针”
       → after-commit invalidation + 不可变 revision cache

[已修] Capability/RuntimePolicy 改动可能忘记 bump Model revision
       → Model Aggregate 同事务统一 bump

[已修] 明文密钥虽然不回 GET，但主密钥/算法/轮换未定义
       → AEAD envelope + 外部 Key 管理 + 日志/APM排除

[已修] SSRF 仅 DNS 校验一次仍可能 DNS rebinding
       → 实际连接地址校验、redirect每跳校验、INTERNAL server allowlist

[已修] Connection Test/Discovery 可被滥用作内网扫描/DoS
       → 权限 + rate limit + timeout/response limits

[已修] extra_config 仍可能成为 arbitrary headers / secret 垃圾桶
       → Profile字段白名单 + additionalProperties=false + recursive secret deny

[已修] Provider/Protocol 可在被引用 Connection 上任意切换导致非局部漂移
       → 被引用后默认不可直接换 provider/protocol，使用新 Connection 迁移

[已修] Snapshot 取当前 Credential 但未验证 Connection 身份
       → provider/protocol identity mismatch 明确失败

[已修] Schema/Profile 历史版本删除或原地修改会破坏已保存模型
       → 版本不可变 + pre-deploy compatibility check

[已修] Default Model 上层配置失效时“静默回落”会隐藏配置错误
       → 只有 row 缺失才回落；row存在但无效直接 DEFAULT_MODEL_INVALID

[已修] 动态 JSON/Discovery/Options 没有体积上限会形成资源型DoS
       → 统一 size/depth/count limits

[已修] 同一 Adapter 代码升级仍可能让 Workflow 可执行语义漂移
       → Adapter version 进入日志/可选Snapshot；breaking change必须版本化
```

仍然存在但属于实现期验证而非架构缺口的事项：

```text
1. 当前 `dev` 的真实 SQL/Entity 字段必须由 Codex 开工时再次确认；
2. 当前项目使用的 HTTP Client 能否直接支持“validated DNS/IP pinning”需要按实际实现选择方案；
3. 当前 Redis/Redisson 能力决定分布式 Semaphore/Token Bucket 的最终类；
4. 当前操作日志/AOP/APM 是否捕获请求体，需要在 PR 1C 做实际 Secret Leak Test；
5. 现有 Direct Chat Policy 的具体承载点必须在 CHAT 切换前从当前代码中确认；
6. Workflow Snapshot 当前存储结构应在 PR 5F 前对照现有不可变版本机制接入，而不是另建平行版本体系。
```

这些项目若实际代码不满足，不允许 Codex自行“猜一个新框架”；应先复用 Polaris 当前基础设施，缺口明确后再做最小新增。

---

# 162. 最终执行 Gate

文档从 V2.4 起视为冻结执行规范。

正式允许开始 Codex PR 1A 的条件：

```text
✓ 当前分支确认为 `dev`，实际源码已读取并确认
✓ 工作树可控
✓ 不新建/切换分支或 worktree，不执行 Git commit
✓ 现有 `sql/ry_ai.sql` 不修改，数据库变化只新增有序增量 SQL
✓ tenant/dept/audit真实字段已核对
✓ 当前 ai_model_config 旧依赖已搜索
✓ 当前前端确认为 polaris-ui-vue3 / Vue3 JavaScript
✓ destructive reset 不会被自动执行
✓ Phase 1A Scope 被锁定
```

之后每个 PR 都必须满足：

```text
Compile Gate
Test Gate
Permission Regression Gate
Security Gate
Legacy Compatibility Gate（Cleanup前）
Architecture Guard Gate
```

任一 Gate 失败：

> **停止进入下一 PR。**

这比一次性让 Codex“完成整个 Model Center V2”更重要；本方案的目标是每一步都可验证、可回退、不会把现有 Polaris 业务链路在中途打断。

---

# 163. V2.4.1 最终实施修正（最高优先级）

本节是 2026-09-21 对全篇的最终收口。若前文与本节冲突，以本节为准；实施过程中不得用旧章节恢复被本节否决的设计。

## 163.1 仓库与交付约束

```text
工作分支：仅当前 dev
新分支/worktree：禁止
Git commit：禁止
实现风格：以当前 dev 同模块的包结构、命名、Controller/Service/Mapper、异常、响应对象和测试风格为准
已有工作树改动：保留，不覆盖、不回滚、不混入无关修改
```

数据库变更采用严格 append-only：

```text
sql/ry_ai.sql                              # 只读，永不修改
sql/model-center-v2.4/001_*.sql            # 第一个增量
sql/model-center-v2.4/002_*.sql            # 后续变化新增文件
sql/model-center-v2.4/003_*.sql            # 继续递增
sql/model-center-reset.sql                  # 高危人工入口，不自动执行
```

不得回写已经存在的编号 SQL。全新环境执行 `sql/ry_ai.sql` 后再按编号执行全部增量；已有环境只执行尚未执行的增量。README 必须记录顺序、前置版本、幂等性、验证 SQL 和回滚限制。未经用户明确授权，只生成和测试 SQL，不连接或修改未知/正式数据库。

## 163.2 “新增模型类型不新增字段”是硬性架构 Gate

新增 `AUDIO_TTS`、`AUDIO_STT`、`VIDEO_GENERATION`、`RERANK` 或未来任意能力时，允许新增的内容仅限：

```text
Capability/Profile/Schema 资源或数据
Adapter 实现及注册
Schema Renderer 的通用控件（仅当现有控件无法表达）
能力级测试夹具
```

禁止因此新增：

```text
ai_model_config 的 xxx_model / xxx_enabled / xxx_config / xxx_default 字段
AiModelConfig Entity、创建/更新 DTO、VO 的模型类型专用属性
模型编辑页中的模型类型专用固定表单字段
按模型类型不断增长的 if/switch 运行分支
新的 is_default_xxx 列或 cleanDefaultXxx 方法
```

`capability_code`、`feature_code`、`provider_code`、`protocol_code` 使用受校验的稳定字符串标识，不用每加一种能力就修改数据库枚举。运行时通过注册表解析 Adapter，通过 Schema/Profile 驱动参数校验与 UI。CI 必须加入架构扫描：新增模型能力的测试样例在不修改主表/主 Entity/通用 DTO/主页面固定字段的前提下可以完成注册、保存、读取和 Adapter 解析，否则 Gate 失败。

## 163.3 Phase 1A 的真实边界

Phase 1A 只建立兼容性数据底座，不切换任何现有 Runtime，不删除旧字段，不改变现有查询、权限和默认模型行为。`AiModelConfig` 的持久化 Entity 当前位于 `polaris-ai-core`，本阶段原地最小扩展，禁止为了“分层更漂亮”跨模块搬家。

公共 Runtime 契约必须遵循现有依赖方向：

```text
polaris-ai-core：ModelRuntimeResolver 接口、稳定输入/输出 DTO、错误契约
polaris-ai：Resolver 实现、数据库聚合、Provider Adapter、运行编排
```

禁止让 core 反向依赖 ai，也禁止把数据库 Entity 直接作为运行时公共 DTO。

PR/Phase 只是实施批次名称；本任务不创建 PR、不提交 commit。每个 Phase 完成后先展示 diff、测试结果和剩余风险，经确认后再进入下一 Phase。

## 163.4 权限和默认模型解析

Model Center 不引入“共享模型自动可见”或新的授权体系。Model 与 Provider Connection 必须继续经过当前 Polaris 的租户、部门和数据权限链；新增表必须纳入与同类 AI 业务表一致的租户拦截配置，不能因为遗漏白名单/拦截器配置而变成跨租户可见或不可用。

默认模型查找与模型可见性分离：Default row 只能指向当前调用身份本来就可访问的模型，不能授予可见性。冻结规则为：

```text
管理端部门上下文：DEPT → TENANT；仅当前既有管理员权限明确允许时才可读 GLOBAL
平台 API Key：仅 Key 所属 TENANT，不自动回落到共享/GLOBAL
GLOBAL：只服务当前既有全局管理语义，不作为普通租户兜底
```

因此前文“tenant > shared 同名覆盖并在 /models 去重”不进入 V2.4 实现。若未来需要共享模型，必须作为单独的权限需求设计、评审和测试，不能借 Default Resolver 绕过 `PlatformTenantLineHandler` 或现有数据权限。

## 163.5 Connection、Credential 与 Snapshot

Connection 创建后，下列传输身份字段不可原地修改：

```text
provider_code / protocol_code / base_url / network_mode / identity-related extra_config
```

端点或协议变化必须创建新 Connection 并显式迁移 Model。允许原地修改的仅是显示名称、启停状态和 Credential 轮换；所有变化都 bump connection revision 并 after-commit 失效缓存。

Credential 使用 AES-GCM/等价 AEAD envelope；AAD 至少绑定：

```text
connection_id + tenant_id + provider_code + protocol_code + normalized_base_url
```

密文复制到其他 Connection 或身份字段不匹配时必须解密失败。密钥版本写入 envelope，支持轮换；任何 GET、日志、异常、审计和 APM 不得返回明文。

Workflow/异步任务 Snapshot 冻结 Connection ID、当时的 Connection Revision、Provider/Protocol/BaseURL 身份、Adapter Version 和 Runtime Definition Hash。Credential 不冻结明文，执行时允许读取同一不可变身份 Connection 的当前有效 Credential，以支持正常密钥轮换；执行时必须逐项比对不可变身份并要求状态有效。仅 Credential、显示名称等允许项变化导致 revision 增长时可以继续，并记录实际 revision；任何传输身份不一致都明确失败，不静默漂移。

## 163.6 Schema 引用与运行定义哈希

单个 `schema_version/schema_hash` 不能同时准确表达 Invocation 参数和多个 Feature 参数。Phase 2 起 Capability 聚合必须保存按用途寻址的 Schema refs（可采用 `schema_refs_json`，通过后续编号增量 SQL 新增），语义示例：

```json
{
  "invocation": {"id": "chat.openai", "version": 2, "hash": "..."},
  "features": {
    "TOOLS": {"id": "feature.tools", "version": 1, "hash": "..."},
    "VISION": {"id": "feature.vision", "version": 1, "hash": "..."}
  }
}
```

运行缓存和 Snapshot 使用确定性 `runtime_definition_hash`，其输入至少包含排序后的 Schema refs、归一化 Capability config、Feature config、Runtime Policy、Provider/Profile/Protocol/Adapter 版本。纯 UI 文案和布局不参与哈希。Hash canonicalization、排序和缺省值规则必须有固定向量测试，禁止依赖普通 JSON 序列化偶然顺序。

## 163.7 业务策略的独立落点

System Prompt、History、Tools、Search 不是 Model 能力字段。迁移 Direct Chat 前，必须在当前代码结构中建立明确的 `DirectChatPolicy` 持久化/解析落点（表名可按现有命名规范最终确定），并遵循覆盖顺序：

```text
REQUEST → WORKFLOW → AGENT → APPLICATION/DIRECT_CHAT → MODEL → PROVIDER
```

不适用的层跳过；同一参数只允许一个合并器定义最终值。Search Credential 进入专用凭据配置，不能进入 Model Capability config 或 Provider `extra_config`。

## 163.8 OpenAPI、Usage 与回归定义

现有对外根路径保持 `/platform/api/v1`，不得实现成 `/platform/v1`。新增兼容接口先复用当前 Key 身份解析、限流、租户配额、响应/错误格式；文档中的 `/models`、chat/embedding/image 路径均挂在该根路径下。

Usage 必须记录来源，禁止把字符估算伪装成 Provider token：

```text
PROVIDER_REPORTED：Provider 返回的真实 usage
LOCAL_TOKENIZER：本地 tokenizer 计算
CHAR_ESTIMATED：为兼容当前配额逻辑保留的明确估算
UNKNOWN：无法可靠获得
```

Moderation 若当前 OpenAPI 从未提供，则作为新能力验收，不写成“旧接口回归”；现有管理端 Moderation 行为仍是回归阻断项。

## 163.9 测试与开工条件

MySQL generated column、JSON、唯一索引与 `NULL` 语义不能只用 H2 或 Mockito 证明。仓库当前若没有 Testcontainers/H2 基础设施，先使用项目现有测试方式；确需新增真实 MySQL 集成测试依赖时必须把它作为明确的小步骤说明，不能在 Phase 1A 隐式引入大套测试框架。

正式开始代码实现前只需满足：

```text
当前分支是 dev，工作树状态已记录
本节与 Phase 1A 文件清单已对齐
sql/ry_ai.sql 将保持零 diff
已有业务改动不会被覆盖
第一批只做 001 增量 SQL + Domain/Mapper + 对应测试
不执行 commit，不执行 reset，不执行未知数据库 SQL
```

满足以上条件后，文档可以直接用于分阶段实现；不能一次性跨 Phase 大改。每阶段必须做到可编译、可测试、行为边界可说明，再继续下一阶段。
