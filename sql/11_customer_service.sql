-- 数字人客服合并脚本：包含原 SQL 11～21 的全部内容。
-- 合并日期：2026-10-23。依赖当前 01～10，按原迁移编号顺序执行，保留全部语句和字段注释。
-- 适用于尚未执行原 SQL 11～21 的数据库，仅执行一次；已经全部执行的数据库无需重复执行。
-- 部分执行过旧版本的数据库，应先核对结构，再按下方原迁移分段补齐，不能从头重复执行。
-- 本文件不自动执行迁移，不含默认客服、模型凭据、公开渠道或交易数据。
-- 各分段开头保留原批次说明，用于追溯当时范围；最终结构以全部分段执行完成后的结果为准。

-- ============================================================================
-- 原迁移 11：11_customer_service.sql
-- ============================================================================
-- 数字人客服 A：配置、不可变发布与登录态文字预览。
-- 依赖当前整理后的 01～10；新增版本，不修改旧脚本，不恢复已撤除的内部应用。
-- 由部署人员备份后执行一次；本脚本不含业务数据、默认客服、公开渠道或交易能力。
-- 不使用 IF NOT EXISTS 掩盖已存在但不兼容的结构。MySQL 5.7/8，utf8mb4。

CREATE TABLE platform_customer_service (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '客服应用主键，自增ID',
    tenant_id bigint NOT NULL COMMENT '所属租户ID，所有读写均校验租户归属',
    name varchar(64) NOT NULL COMMENT '客服名称，与草稿配置中的名称同步',
    config_json json NOT NULL COMMENT '客服草稿配置，包含人设、欢迎语、无依据回复、模型及知识库引用，不存凭据',
    revision bigint NOT NULL DEFAULT 1 COMMENT '配置修订号，编辑、发布及启停时递增，用于乐观锁校验',
    runtime_epoch bigint NOT NULL DEFAULT 1 COMMENT '停用围栏，重新启用不恢复旧会话',
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '客服状态：ACTIVE启用、DISABLED停用',
    current_release_id char(36) DEFAULT NULL COMMENT '当前发布快照ID，关联platform_customer_release.id，NULL表示尚未发布',
    create_by varchar(64) NOT NULL COMMENT '创建人，记录租户管理员用户名',
    create_time datetime(3) NOT NULL COMMENT '创建时间，毫秒精度，由服务端写入',
    update_time datetime(3) NOT NULL COMMENT '最近配置、发布或启停更新时间，毫秒精度',
    -- 客服应用唯一标识。
    PRIMARY KEY (id),
    -- 支持按租户、启停状态及ID检索客服列表。
    KEY idx_customer_tenant (tenant_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='可配置客服应用';

CREATE TABLE platform_customer_release (
    id char(36) NOT NULL COMMENT '发布快照ID，服务端生成的UUID',
    tenant_id bigint NOT NULL COMMENT '所属租户ID，与客服应用的租户一致',
    app_id bigint NOT NULL COMMENT '客服应用ID，关联platform_customer_service.id',
    source_revision bigint NOT NULL COMMENT '发布时采用的应用修订号，对应发布指针更新前的revision',
    config_json json NOT NULL COMMENT '不可变客服配置快照，已有会话固定使用该快照，不随草稿更新',
    model_hash char(64) NOT NULL COMMENT '对话模型运行时定义哈希，调用前核对，防止静默切换配置',
    create_by varchar(64) NOT NULL COMMENT '发布人，记录租户管理员用户名',
    create_time datetime(3) NOT NULL COMMENT '发布时间，毫秒精度，由服务端写入',
    -- 发布快照唯一标识。
    PRIMARY KEY (id),
    -- 同一租户、客服及草稿修订只能生成一份发布快照。
    UNIQUE KEY uk_customer_release (tenant_id, app_id, source_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='客服不可变发布快照';

CREATE TABLE platform_customer_session (
    id char(36) NOT NULL COMMENT '预览会话ID，服务端生成的UUID，不作为访问凭证',
    tenant_id bigint NOT NULL COMMENT '所属租户ID，读取及变更时均校验',
    app_id bigint NOT NULL COMMENT '客服应用ID，关联platform_customer_service.id',
    release_id char(36) NOT NULL COMMENT '会话固定的发布快照ID，关联platform_customer_release.id',
    user_id bigint NOT NULL COMMENT '会话所有者的租户用户ID，关联platform_tenant_user.id',
    runtime_epoch bigint NOT NULL COMMENT '创建会话时的应用运行代次，与应用当前代次不一致时禁止继续输入',
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '持久化会话状态：ACTIVE活跃、CLOSED关闭；过期依据expires_at判断',
    turn_count int NOT NULL DEFAULT 0 COMMENT '已受理轮次数，包含失败和打断轮次，用于分配序号及限制总轮数',
    create_time datetime(3) NOT NULL COMMENT '会话创建时间，毫秒精度，由服务端写入',
    expires_at datetime(3) NOT NULL COMMENT '会话过期时间，过期后禁止新输入，不代表自动删除正文',
    -- 预览会话唯一标识。
    PRIMARY KEY (id),
    -- 支持按租户、所有者及客服查询会话，并按创建时间检索。
    KEY idx_customer_session_owner (tenant_id, user_id, app_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='客服登录态预览会话';

CREATE TABLE platform_customer_turn (
    id char(36) NOT NULL COMMENT '对话轮次ID，服务端生成的UUID',
    tenant_id bigint NOT NULL COMMENT '所属租户ID，与会话的租户一致',
    session_id char(36) NOT NULL COMMENT '所属会话ID，关联platform_customer_session.id',
    request_id varchar(64) NOT NULL COMMENT '客户端输入去重标识，同会话重试沿用原值，不允许同标识更换问题',
    turn_no int NOT NULL COMMENT '会话内轮次序号，从1开始，由服务端串行分配',
    question text NOT NULL COMMENT '本轮用户问题，仅去除首尾空白，不接受客户端伪造历史',
    answer text DEFAULT NULL COMMENT '通过输出及引用编号核验的回答正文，未成功生成时为NULL',
    citations_json json DEFAULT NULL COMMENT '本轮知识引用指针，包含来源编号、文档及索引版本和片段哈希，不保存原文副本',
    status varchar(16) NOT NULL COMMENT '轮次状态：RUNNING生成中、COMPLETED完成、FAILED失败、INTERRUPTED打断或超时',
    error_code varchar(32) DEFAULT NULL COMMENT '受控错误码或计量异常标识，不保存供应商原始异常，正常完成时为NULL',
    input_tokens int DEFAULT NULL COMMENT '已报告的回答模型输入Token数，NULL表示未知，不按零处理',
    output_tokens int DEFAULT NULL COMMENT '已报告的回答模型输出Token数，NULL表示未知，不按零处理',
    total_tokens int DEFAULT NULL COMMENT '已报告的回答模型总Token数，NULL表示未知，不含未取得的向量等用量',
    usage_source varchar(32) DEFAULT NULL COMMENT '用量来源：PROVIDER_REPORTED、LOCAL_TOKENIZER、CHAR_ESTIMATED或UNKNOWN；未记录时为NULL',
    create_time datetime(3) NOT NULL COMMENT '轮次受理时间，毫秒精度，由服务端写入',
    deadline datetime(3) NOT NULL COMMENT '轮次结果提交截止时间，超时后禁止迟到结果提交，不保证立即终止远端计算',
    finished_time datetime(3) DEFAULT NULL COMMENT '轮次完成、失败、打断或标记超时的时间，未结束时为NULL',
    -- 对话轮次唯一标识。
    PRIMARY KEY (id),
    -- 防止同一会话的输入重放创建第二个轮次。
    UNIQUE KEY uk_customer_request (tenant_id, session_id, request_id),
    -- 保证会话内轮次序号唯一，支持按序读取。
    UNIQUE KEY uk_customer_turn_no (tenant_id, session_id, turn_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='客服有界文字轮次及输入去重';

-- ============================================================================
-- 原迁移 12：12_customer_service_operation.sql
-- ============================================================================
-- 客服 B2：仅无外部资源的工作流测试任务。依赖 11，备份后执行一次。
-- 只记录客服请求与既有工作流执行的关联；状态与结果以原工作流执行为准。
-- 不修改旧迁移，不包含默认数据、订单接口、交易能力或公开渠道。
CREATE TABLE platform_customer_operation (
    id char(36) NOT NULL COMMENT '客服测试任务ID，服务端生成的UUID',
    tenant_id bigint NOT NULL COMMENT '所属租户ID，所有查询和写入均校验租户归属',
    session_id char(36) NOT NULL COMMENT '所属客服会话ID，关联platform_customer_session.id，访问时另校验会话所有者',
    request_id varchar(64) NOT NULL COMMENT '客户端同次任务的去重标识，重试必须保持相同能力和输入',
    capability_code varchar(32) NOT NULL COMMENT '会话固定发布配置中的能力编码，不接受任意工作流标识',
    definition_id bigint NOT NULL COMMENT '服务端从固定能力配置解析的工作流定义ID',
    workflow_version_id char(36) NOT NULL COMMENT '服务端从固定能力配置解析的不可变工作流发布版本ID',
    execution_id char(36) NOT NULL COMMENT '关联ai_workflow_execution.execution_id，与任务关联在同一事务提交',
    input_json json NOT NULL COMMENT '校验并按字段排序的最终输入，含服务端身份映射值，仅用于去重，不保存凭据',
    create_time datetime(3) NOT NULL COMMENT '任务受理时间，毫秒精度，由服务端写入',
    -- 任务唯一标识。
    PRIMARY KEY (id),
    -- 保证同会话同请求不会产生第二次工作流启动。
    UNIQUE KEY uk_customer_operation_request (tenant_id, session_id, request_id),
    -- 一个工作流执行只能归属一个客服测试任务。
    UNIQUE KEY uk_customer_operation_execution (tenant_id, execution_id),
    -- 支持按会话读取有界任务列表与计数。
    KEY idx_customer_operation_session (tenant_id, session_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='客服无外部调用的工作流测试任务';

-- ============================================================================
-- 原迁移 13：13_customer_service_intent.sql
-- ============================================================================
-- 数字人客服 B3：在原轮次上保存服务端核验后的能力选择和补参状态。
-- 依赖 11、12，备份后执行一次；不覆盖既有轮次，不自动运行工作流。
-- NULL 兼容旧知识轮次；不保存模型原始响应、凭据或身份映射值。
ALTER TABLE platform_customer_turn
    ADD COLUMN intent_json json DEFAULT NULL COMMENT '服务端核验的需求状态：COLLECTING待补参、READY待点击运行、KNOWLEDGE知识咨询、CLARIFY澄清、CANCEL取消补参；仅保存允许的能力编码、用户参数和缺参字段，不含服务端身份值';

-- ============================================================================
-- 原迁移 14：14_customer_service_channel.sql
-- ============================================================================
-- 数字人客服 C1：需租户管理员登录的受控文字渠道，不开放匿名资料或业务身份。
-- 依赖 11、12、13，备份后执行一次；不改既有发布版本和预览会话。
CREATE TABLE platform_customer_channel (
    id varchar(36) NOT NULL COMMENT '客服渠道唯一标识，由服务端生成',
    tenant_id bigint NOT NULL COMMENT '所属租户ID，所有管理和运行访问均校验租户',
    app_id bigint NOT NULL COMMENT '所属客服ID，不复用单工作流分享关系',
    release_id varchar(36) NOT NULL COMMENT '创建时固定的客服发布版本ID，后续发布不自动切换',
    code varchar(32) NOT NULL COMMENT '随机渠道入口码，仅定位入口，不作为身份凭证',
    name varchar(64) NOT NULL COMMENT '渠道名称',
    allowed_origins_json json NOT NULL COMMENT '允许嵌入的精确HTTP或HTTPS来源数组，不支持通配符和路径',
    capability_codes_json json NOT NULL COMMENT '允许使用的能力编码数组，必须为固定发布版本的子集，空数组仅知识咨询',
    status varchar(16) NOT NULL DEFAULT 'DISABLED' COMMENT '渠道状态：ACTIVE启用、DISABLED停用；新建默认停用',
    revision bigint NOT NULL DEFAULT 1 COMMENT '乐观锁修订号，同时作为渠道运行围栏，修改后旧会话失效',
    expires_at datetime(3) NOT NULL COMMENT '渠道到期时间，到期阻断会话创建、输入及结果读取',
    create_by varchar(64) NOT NULL COMMENT '创建渠道的真实平台管理员账号',
    create_time datetime(3) NOT NULL COMMENT '创建时间',
    update_time datetime(3) NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_channel_code (code),
    KEY idx_customer_channel_app (tenant_id, app_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人客服受控文字渠道，当前仅真实租户管理员登录使用';

ALTER TABLE platform_customer_session
    ADD COLUMN channel_id varchar(36) DEFAULT NULL COMMENT '来源客服渠道ID，NULL表示原管理员预览会话';
ALTER TABLE platform_customer_session
    ADD COLUMN channel_revision bigint DEFAULT NULL COMMENT '会话创建时固定的渠道修订号，渠道修改、停用再启用均不能恢复旧会话';
CREATE INDEX idx_customer_session_channel ON platform_customer_session (tenant_id, channel_id);

-- ============================================================================
-- 原迁移 15：15_customer_service_speech.sql
-- ============================================================================
-- 数字人客服 C2：语音请求去重及发布快照；依赖 11～14，备份后执行一次。
-- 不保存原始音频，不开放连续实时识别、匿名语音或自动执行能力。
ALTER TABLE platform_customer_release
    ADD COLUMN speech_model_hash varchar(64) DEFAULT NULL COMMENT '发布时固定的语音识别模型定义哈希，NULL兼容未配置识别的旧发布';
ALTER TABLE platform_customer_release
    ADD COLUMN playback_model_hash varchar(64) DEFAULT NULL COMMENT '发布时固定的语音合成模型定义哈希，NULL表示未配置供应商播报';
CREATE TABLE platform_customer_speech (
    id varchar(36) NOT NULL COMMENT '语音请求主键，服务端生成',
    tenant_id bigint NOT NULL COMMENT '所属租户ID，所有读写显式限定租户',
    session_id varchar(36) NOT NULL COMMENT '所属客服会话ID，沿用真实平台用户、渠道和运行代次检查',
    request_id varchar(64) NOT NULL COMMENT '客户端稳定请求标识，同会话同标识不得替换输入或自动再次调用供应商',
    input_hash varchar(64) NOT NULL COMMENT '录音或服务端回答内容SHA256指纹，取消墓碑使用固定零值，不保存音频',
    kind varchar(8) NOT NULL DEFAULT 'STT' COMMENT '语音用途：STT短录音识别、TTS已完成回答播报、CANCEL取消墓碑',
    turn_id varchar(36) DEFAULT NULL COMMENT '供应商播报关联的已完成回答ID，由服务端核验，不接受任意文本',
    audio_millis int NOT NULL COMMENT '由PCM长度计算的输入时长毫秒，不冒充供应商计费时长',
    status varchar(16) NOT NULL COMMENT '语音状态：RUNNING处理中、COMPLETED完成、FAILED失败、INTERRUPTED取消或超时',
    transcript varchar(2000) DEFAULT NULL COMMENT '经校验的识别草稿，返回后仍需用户核对并点击发送，不直接运行能力',
    error_code varchar(40) DEFAULT NULL COMMENT '受控错误编码，不保存供应商异常、端点或凭据',
    usage_json json DEFAULT NULL COMMENT '白名单标准化用量，未知值保留NULL，不换算音频费用为Token',
    create_time datetime(3) NOT NULL COMMENT '请求创建时间',
    deadline datetime(3) NOT NULL COMMENT '处理完成围栏时间，超时请求不自动重试供应商',
    finished_time datetime(3) DEFAULT NULL COMMENT '完成、失败或取消时间；草稿和合成音频最多五分钟可读，音频仅限内存缓存',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_speech_request (tenant_id, session_id, request_id),
    KEY idx_customer_speech_session (tenant_id, session_id, status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人客服识别与播报账本，仅存去重状态、短文本和有界用量，不存原始录音';

-- ============================================================================
-- 原迁移 16：16_customer_service_realtime_speech.sql
-- ============================================================================
-- 数字人客服 C3：复用现有语音账本，追加实时请求语义；依赖 SQL 15。
-- 不新增音频存储；字段容量与约束保持原定义，更新注释后执行一次即可。
ALTER TABLE platform_customer_speech
    MODIFY COLUMN kind varchar(8) NOT NULL DEFAULT 'STT' COMMENT '语音用途：STT完整短录音、STREAM实时识别、TTS回答播报、CANCEL取消墓碑',
    MODIFY COLUMN input_hash varchar(64) NOT NULL COMMENT '输入SHA256指纹：STT为WAV、TTS为回答；STREAM处理中为请求标记、完成时为PCM指纹；CANCEL为零值，不保存音频',
    MODIFY COLUMN audio_millis int NOT NULL COMMENT '按16kHz单声道PCM字节数计算的输入时长毫秒；STREAM创建时为0，完成时结算，不冒充供应商计费时长';

-- ============================================================================
-- 原迁移 17：17_customer_service_avatar.sql
-- ============================================================================
-- 数字人客服 C9：私有人物素材。依赖既有客服表；配置仍保存于草稿／发布的 config_json，不改旧发布。
-- 素材不可变，不提供覆盖或公开静态 URL；租户内最多 100 份、合计 200MiB，由上传服务在租户行锁下限制。
CREATE TABLE IF NOT EXISTS platform_customer_avatar_asset (
    id varchar(36) NOT NULL COMMENT '服务端生成的不可变素材UUID，同时作为私有存储文件名',
    tenant_id bigint NOT NULL COMMENT '素材所属租户ID，所有读写必须显式限定租户',
    name varchar(180) NOT NULL COMMENT '上传时的展示文件名，仅显示，不参与物理路径拼接',
    kind varchar(8) NOT NULL COMMENT '素材类别：IMAGE静态PNG或JPEG、GIF动画、VIDEO无声播放的MP4',
    content_type varchar(32) NOT NULL COMMENT '根据文件内容识别的受支持MIME类型，不信任客户端声明',
    byte_size bigint NOT NULL COMMENT '素材实际字节数，单文件最多8MiB',
    content_hash varchar(64) NOT NULL COMMENT '文件内容SHA256，读取时核验，不允许同ID覆盖内容',
    create_by bigint NOT NULL COMMENT '上传素材的租户管理员用户ID',
    create_time datetime(3) NOT NULL COMMENT '素材上传并登记的时间',
    PRIMARY KEY (id),
    KEY idx_customer_avatar_tenant (tenant_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人客服私有人物素材，不保存远程URL或公开访问令牌';

-- ============================================================================
-- 原迁移 18：18_customer_service_records.sql
-- ============================================================================
-- 数字人客服 C10：本人接待记录的游标查询索引；依赖 11～17，备份后执行一次。
-- 不新增字段、不修改历史正文或会话状态，不扩大租户及真实用户访问范围。
-- 排序使用创建时间和主键，避免同毫秒多会话翻页遗漏；每次最多读取21条。
CREATE INDEX idx_customer_session_records
    ON platform_customer_session (tenant_id, user_id, app_id, create_time, id);

-- ============================================================================
-- 原迁移 19：19_customer_service_answer_stream.sql
-- ============================================================================
-- 数字人客服 C12：受校验回答分段与分段播报；依赖 11～18，备份后执行一次。
-- 仅存通过内容审核及来源编号校验的分段，不保存原始增量、推理过程或音频。
ALTER TABLE platform_customer_turn
    ADD COLUMN answer_parts_json json DEFAULT NULL COMMENT '当前轮次通过来源编号校验及内容审核的有序回答分段，最多8段，每段最多600字；失败或中断后不可播报，不替代最终answer';
ALTER TABLE platform_customer_speech
    ADD COLUMN part_no int DEFAULT NULL COMMENT '服务端已校验回答分段的1起始序号，范围1至8；NULL沿用已完成整轮回答播报，客户端不能指定合成文字';
ALTER TABLE platform_customer_speech
    ADD UNIQUE KEY uk_customer_speech_part (tenant_id, session_id, turn_id, part_no);
-- 更新既有字段说明，容量与默认值不变。
ALTER TABLE platform_customer_speech
    MODIFY COLUMN turn_id varchar(36) DEFAULT NULL COMMENT '播报关联的当前最新回答ID；part_no为NULL时仅允许已完成整轮，非NULL时可播报运行中已校验分段',
    MODIFY COLUMN input_hash varchar(64) NOT NULL COMMENT '输入SHA256指纹：STT为WAV，TTS为轮次与可选分段序号及服务端文字，STREAM为请求标记或PCM，CANCEL为零值；不存音频';

-- ============================================================================
-- 原迁移 20：20_customer_service_visitor.sql
-- ============================================================================
-- 数字人客服 C13：访客文字接待与网站嵌入。依赖 11～19，执行一次。
-- 旧渠道继续要求管理员登录；新渠道仍默认停用，不自动公开既有知识库。
ALTER TABLE platform_customer_channel
    ADD COLUMN access_mode varchar(20) NOT NULL DEFAULT 'TENANT_ADMIN' COMMENT '访问方式：TENANT_ADMIN租户管理员登录、VISITOR免登录访客知识咨询';
ALTER TABLE platform_customer_channel
    ADD COLUMN visitor_daily_sessions int NOT NULL DEFAULT 200 COMMENT '访客渠道每日新建会话上限，范围1至10000，按UTC自然日统计';
ALTER TABLE platform_customer_channel
    ADD COLUMN visitor_message_limit int NOT NULL DEFAULT 20 COMMENT '每个访客会话最多问答轮数，范围1至50';
ALTER TABLE platform_customer_session
    ADD COLUMN visitor_token_hash varchar(64) DEFAULT NULL COMMENT '访客专属随机会话凭证的SHA256摘要；NULL为管理员会话，原始凭证不落库';
ALTER TABLE platform_customer_session
    MODIFY COLUMN user_id bigint NOT NULL COMMENT '管理员会话为真实平台用户ID；访客会话固定0并强制校验专属会话凭证，不代表管理员身份';
CREATE INDEX idx_customer_session_visitor_records ON platform_customer_session (tenant_id, app_id, user_id, create_time, id);

-- ============================================================================
-- 原迁移 21：21_customer_service_visitor_speech.sql
-- ============================================================================
-- 数字人客服：访客语音显式授权及每会话请求限额。依赖 SQL20，只执行一次。
-- 旧渠道默认关闭语音；修改开关或限额沿用渠道修订，旧会话及实时票据失效。
ALTER TABLE platform_customer_channel
    ADD COLUMN visitor_speech_enabled tinyint NOT NULL DEFAULT 0 COMMENT '访客语音开关：0关闭、1允许固定发布语音模型及设备本地播报；仅VISITOR渠道生效';
ALTER TABLE platform_customer_channel
    ADD COLUMN visitor_speech_limit int NOT NULL DEFAULT 20 COMMENT '每个访客会话识别和播报请求总上限，范围1至40；包含实时识别、短录音、回答分段合成及取消墓碑';

