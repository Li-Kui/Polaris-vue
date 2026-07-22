package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.attachment.AttachmentParserHelper;
import com.polaris.ai.attachment.MultimodalMediaHelper;
import com.polaris.ai.chat.AiAssistant;
import com.polaris.ai.domain.AiConversation;
import com.polaris.ai.helper.SsePushHelper;
import com.polaris.ai.mapper.AiChatMapper;
import com.polaris.ai.pivot.AiModelProperties;
import com.polaris.ai.prompt.SystemPromptResolver;
import com.polaris.ai.service.IAiAgentService;
import com.polaris.ai.service.IAiChatService;
import com.polaris.ai.tools.AiToolRegistry;
import com.polaris.common.utils.SecurityUtils;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * AI 对话 Service 实现类 —— 基于 LangChain4j
 * <p>
 * 核心设计思路： 业务代码只依赖 LangChain4j 的 StreamingChatLanguageModel 接口， 不感知底层是哪个厂商的模型。 切换模型（通义千问 / DeepSeek / OpenAI / 本地
 * Ollama） 只需修改 application.yml 中的 ai.model.provider，本类代码零改动。
 *
 * @author polaris
 */
@Slf4j
@Service
public class AiChatServiceImpl extends ServiceImpl<AiChatMapper, AiConversation> implements IAiChatService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 流式聊天语言模型
     * 由 AiModelConfig 工厂 Bean 根据 application.yml 中的 provider 配置动态注入，
     * 可能是 OpenAiStreamingChatModel / OllamaStreamingChatModel 等具体实现
     */
    @Autowired
    private StreamingChatModel streamingModel;

    /**
     * AI 模型配置属性（模型名称、温度、最大 Token、系统提示词等）
     */
    @Autowired
    private AiModelProperties modelProps;

    @Autowired
    private AiChatMapper aiChatMapper;

    @Autowired
    private com.polaris.ai.pivot.AiModelFactory modelFactory;

    @Autowired
    private IAiAgentService agentService;

    @Autowired
    private MultimodalMediaHelper mediaHelper;

    @Autowired
    private SystemPromptResolver promptResolver;

    @Autowired
    private AiToolRegistry toolRegistry;

    @Autowired
    private SsePushHelper sseHelper;

    // ----------------------------------------------------------------
    // 会话管理
    // ----------------------------------------------------------------

    /**
     * 新建会话 model 参数为空时，取 application.yml 中配置 of 默认模型名
     */
    @Override
    public AiConversation createConversation(Long userId, Long modelConfigId, Long knowledgeBaseId) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle("新对话");

        if (modelConfigId == null) {
            try {
                com.polaris.ai.domain.AiModelConfig defaultCfg = modelFactory.getDefaultChatModelConfig();
                if (defaultCfg != null) {
                    conv.setModelConfigId(defaultCfg.getId());
                    conv.setModel(defaultCfg.getModelName());
                } else {
                    conv.setModel(modelProps.getModelName());
                }
            } catch (Exception e) {
                conv.setModel(modelProps.getModelName());
            }
        } else {
            conv.setModelConfigId(modelConfigId);
            com.polaris.ai.domain.AiModelConfig cfg = modelFactory.getModelConfig(modelConfigId);
            if (cfg != null) {
                conv.setModel(cfg.getModelName());
            }
        }

        conv.setKnowledgeBaseId(knowledgeBaseId);
        conv.setCreateBy(SecurityUtils.getUsername());
        aiChatMapper.insertConversation(conv);
        return conv;
    }

    @Override
    public List<AiConversation> listConversations(Long userId) {
        List<AiConversation> list = aiChatMapper.selectConversationsByUserId(userId);
        // 防御性处理：MyBatis 查无数据时可能返回 null，统一转为空列表
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 查询会话消息
     * 先鉴权（校验会话是否属于当前用户），再查消息，防止越权访问
     */
    @Override
    public List<com.polaris.ai.domain.AiMessage> listMessages(Long conversationId, Long userId) {
        AiConversation conv = aiChatMapper.selectConversationById(conversationId, userId);
        if (conv == null) {
            return new ArrayList<>();
        }
        return aiChatMapper.selectMessagesByConversationId(conversationId);
    }

    @Override
    public int renameConversation(Long id, String title, Long userId) {
        return aiChatMapper.updateConversationTitle(id, title, userId);
    }

    @Override
    public int updateConversationConfig(Long id, Long modelConfigId, Long knowledgeBaseId, Long userId) {
        String modelName = null;
        if (modelConfigId != null) {
            com.polaris.ai.domain.AiModelConfig cfg = modelFactory.getModelConfig(modelConfigId);
            if (cfg != null) {
                modelName = cfg.getModelName();
            }
        }
        return aiChatMapper.updateConversationConfig(id, modelName, modelConfigId, knowledgeBaseId, userId);
    }

    /**
     * 删除会话 先物理删除该会话下所有消息，再逻辑删除会话本身
     */
    @Override
    public int deleteConversation(Long id, Long userId) {
        aiChatMapper.deleteMessagesByConversationId(id);
        return aiChatMapper.deleteConversation(id, userId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int deleteConversationsBatch(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        aiChatMapper.deleteMessagesByConversationIds(ids);
        return aiChatMapper.deleteConversationsBatch(ids, userId);
    }

    // ----------------------------------------------------------------
    // 核心：LangChain4j 流式对话
    // ----------------------------------------------------------------

    /**
     * 发送消息，流式返回 AI 回复
     * <p>
     * 完整流程：
     * 1. 鉴权 —— 校验会话归属
     * 2. 持久化用户消息
     * 3. 首条消息自动命名会话标题
     * 4. 从 DB 加载历史消息并构建 LangChain4j 消息列表
     * 5. 调用流式模型，逐 token 通过 SSE 推送给前端
     * 6. 流式结束后持久化完整 AI 回复及 Token 消耗
     */
    @Override
    public void chat(Long conversationId, String userInput, String fileUrl, String agentCode, Boolean enableSearch, Long userId, SseEmitter emitter) {
        // 1. 鉴权：会话必须属于当前用户
        AiConversation conv = aiChatMapper.selectConversationById(conversationId, userId);
        if (conv == null) {
            sseHelper.sendSse(emitter, "error", "会话不存在或无权限");
            return;
        }

        // 2. 解析附件并持久化用户消息
        String parsedAttachmentContent = null;
        String fileName = null;
        if (fileUrl != null && !fileUrl.trim().isEmpty()) {
            sseHelper.sendSse(emitter, "status", "正在解析文件附件...");
            String[] urls = fileUrl.split(",");
            StringBuilder sbContent = new StringBuilder();
            StringBuilder sbNames = new StringBuilder();
            for (String url : urls) {
                String trimmedUrl = url.trim();
                if (trimmedUrl.isEmpty()) continue;
                String content = AttachmentParserHelper.parse(trimmedUrl);
                if (content != null && !content.trim().isEmpty()) {
                    sbContent.append("【附件: ").append(trimmedUrl.substring(trimmedUrl.lastIndexOf("/") + 1)).append("】\n")
                            .append(content).append("\n\n");
                }
                String name = trimmedUrl.substring(trimmedUrl.lastIndexOf("/") + 1);
                if (sbNames.length() > 0) sbNames.append(",");
                sbNames.append(name);
            }
            parsedAttachmentContent = sbContent.toString();
            fileName = sbNames.toString();
            sseHelper.sendSse(emitter, "status", "附件解析成功，正在初始化 AI 思考...");
            if (parsedAttachmentContent != null && parsedAttachmentContent.length() > 28000) {
                parsedAttachmentContent = parsedAttachmentContent.substring(0, 28000)
                        + "\n\n...[由于文件附件体积过大，已自动截断保留前 28000 字符内容]...";
            }
        } else {
            sseHelper.sendSse(emitter, "status", "正在呼叫 AI 助手...");
        }

        com.polaris.ai.domain.AiMessage userMsg = new com.polaris.ai.domain.AiMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userInput); // 这里仅存储干净的用户输入内容
        userMsg.setFileUrl(fileUrl);
        userMsg.setFileName(fileName);
        userMsg.setFileContent(parsedAttachmentContent);
        aiChatMapper.insertMessage(userMsg);

        // 3. 首条消息自动命名会话标题（使用原始输入，截取前15字 + 省略号）
        if ("新对话".equals(conv.getTitle())) {
            String autoTitle = userInput.length() > 15 ? userInput.substring(0, 15) + "…" : userInput;
            aiChatMapper.updateConversationTitle(conversationId, autoTitle, userId);
        }

        // 4. 智能体检测与上下文构建
        com.polaris.ai.domain.AiAgent selectedAgent = null;
        if (agentCode != null && !agentCode.trim().isEmpty()) {
            try {
                selectedAgent = agentService.selectAgentByCode(agentCode);
                log.info(">>> 当前对话使用专属智能体: {} ({})", selectedAgent.getAgentName(), agentCode);
            } catch (Exception e) {
                log.error(">>> 查询指定智能体失败: {}", agentCode, e);
            }
        }

        // 构建完整的消息上下文（SystemMessage + 历史消息）
        List<ChatMessage> messages = buildMessages(conversationId, userId);
        if (selectedAgent != null && selectedAgent.getSystemPrompt() != null && !selectedAgent.getSystemPrompt().trim().isEmpty()) {
            messages.add(0, dev.langchain4j.data.message.SystemMessage.from(selectedAgent.getSystemPrompt()));
        }

        // 4.1 判断是否需要启用向量检索器
        ContentRetriever contentRetriever = null;
        if (conv.getKnowledgeBaseId() != null) {
            log.info(">>> 会话关联知识库ID: {}, 启用 RAG 向量检索...", conv.getKnowledgeBaseId());
            contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(5)
                    .minScore(0.5)
                    .filter(metadataKey("knowledge_base_id").isEqualTo(conv.getKnowledgeBaseId().toString()))
                    .build();
        }

        // 5. 使用 AiServices 动态构建代理并注册工具类
        // 设置对话上下文，供绘图工具在执行时抓取会话ID与已上传图片URL
        // （PropagatingExecutor 在构造时快照读取，必须早于 getContextAwareTools）
        com.polaris.ai.utils.ChatContextHolder.setConversationId(conversationId);
        if (fileUrl != null && !fileUrl.trim().isEmpty()) {
            com.polaris.ai.utils.ChatContextHolder.setFileUrl(fileUrl);
        }
        SecurityContext securityContext = SecurityContextHolder.getContext();
        String searchKey = null;
        String enabledTools = null;
        try {
            com.polaris.ai.domain.AiModelConfig modelConfig = modelFactory.getModelConfig(conv.getModelConfigId());
            if (modelConfig != null) {
                searchKey = modelConfig.getSearchKey();
                // 优先使用模型配置中定义的工具白名单（在模型设置页面配置）
                enabledTools = modelConfig.getEnabledTools();
            }
        } catch (Exception e) {
            log.error(">>> 查询模型配置失败，无法提取工具配置: {}", e.getMessage());
        }
        // 1. 若前端开启了 enableSearch 开关（或包含联网搜索指令），将 web_search 融入工具链
        if (Boolean.TRUE.equals(enableSearch)) {
            if (enabledTools == null || enabledTools.trim().isEmpty()) {
                enabledTools = "web_search";
            } else if (!enabledTools.contains("web_search")) {
                enabledTools = enabledTools + ",web_search";
            }
        }

        // 2. 整合智能体绑定的专属工具链
        if (selectedAgent != null && selectedAgent.getTools() != null && !selectedAgent.getTools().trim().isEmpty()) {
            if (enabledTools == null || enabledTools.trim().isEmpty()) {
                enabledTools = selectedAgent.getTools();
            } else {
                enabledTools = enabledTools + "," + selectedAgent.getTools();
            }
        }
        Map<ToolSpecification, ToolExecutor> tools = toolRegistry.getContextAwareTools(securityContext, enabledTools, searchKey);

        // 动态根据当前会话绑定的模型ID，获取对应的执行模型实例
        StreamingChatModel targetChatModel = modelFactory.getStreamingModel(conv.getModelConfigId());

        AiServices<AiAssistant> builder = AiServices.builder(AiAssistant.class)
                .streamingChatModel(targetChatModel)
                .tools(tools);

        if (contentRetriever != null) {
            builder.contentRetriever(contentRetriever);
        }

        AiAssistant assistant = builder.build();


        // 6. 调用 LangChain4j 流式接口，逐 token 通过 SSE 推送给前端
        StringBuilder fullReply = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();

        TokenStream tokenStream = assistant.chat(messages);
        tokenStream.onPartialResponse(token -> {
                    if (fullReply.length() == 0) {
                        sseHelper.sendSse(emitter, "status", "");
                    }
                    fullReply.append(token);
                    String escapedToken = token != null ? token.replace("\n", "__SSE_NEWLINE__") : "";
                    sseHelper.sendSse(emitter, "message", escapedToken);
                })
                .onPartialThinking(thinking -> {
                    if (thinking != null && thinking.text() != null) {
                        fullReasoning.append(thinking.text());
                        String escapedThinking = thinking.text().replace("\n", "__SSE_NEWLINE__");
                        sseHelper.sendSse(emitter, "reasoning", escapedThinking);
                    }
                })
                .onCompleteResponse(response -> {
                    // 7. 持久化 AI 完整回复
                    if (fullReply.length() > 0 || fullReasoning.length() > 0) {
                        com.polaris.ai.domain.AiMessage aiMsg = new com.polaris.ai.domain.AiMessage();
                        aiMsg.setConversationId(conversationId);
                        aiMsg.setRole("assistant");
                        aiMsg.setContent(fullReply.toString());
                        if (fullReasoning.length() > 0) {
                            aiMsg.setReasoningContent(fullReasoning.toString());
                        }
                        // 记录本次对话消耗 of Token 总数（用于统计和计费）
                        if (response != null && response.tokenUsage() != null) {
                            aiMsg.setTokens(response.tokenUsage().totalTokenCount());
                        }
                        aiChatMapper.insertMessage(aiMsg);
                    }
                    // 通知前端流式结束，前端收到后关闭 EventSource
                    sseHelper.sendSse(emitter, "done", "[DONE]");
                    emitter.complete();
                })
                .onError(error -> {
                    log.error("LangChain4j 声明式 AI 助手流式调用异常", error);
                    sseHelper.sendSse(emitter, "error", "AI 服务异常：" + error.getMessage());
                    emitter.complete();
                })
                .start();

        // 各工具的 PropagatingExecutor 已快照捕获上下文，这里清理请求线程防止线程复用污染
        com.polaris.ai.utils.ChatContextHolder.clear();
    }

    // ----------------------------------------------------------------
    // 私有工具方法
    // ----------------------------------------------------------------

    /**
     * 构建发送给 AI 的完整消息列表
     * <p>
     * 结构：[SystemMessage] + [历史 user/assistant 消息（最近 N 条）]
     * <p>
     * 优先根据用户拥有的角色从北辰参数设置 (sys_config) 中读取角色 Prompt 并合并，
     * 若未找到任何角色 Prompt，则回退使用默认在 application.yml 中配置的全局系统提示词。
     * <p>
     * 历史消息数量由 ai.model.max-history-messages 控制，
     * 超出时从最早的消息开始裁剪，保留最近 N 条，防止超出模型 context window。
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID
     * @return LangChain4j 格式的消息列表
     */
    private List<ChatMessage> buildMessages(Long conversationId, Long userId) {
        List<com.polaris.ai.domain.AiMessage> history = aiChatMapper.selectMessagesByConversationId(conversationId);

        List<ChatMessage> list = new ArrayList<>();

        // 1. 优先从模型专属设置中拉取参数
        String system = null;
        int max = modelProps.getMaxHistoryMessages();

        AiConversation conv = aiChatMapper.selectConversationById(conversationId, userId);
        if (conv != null && conv.getModelConfigId() != null) {
            com.polaris.ai.domain.AiModelConfig modelConfig = modelFactory.getModelConfig(conv.getModelConfigId());
            if (modelConfig != null) {
                // 获取模型专属提示词
                if (modelConfig.getSystemPrompt() != null && !modelConfig.getSystemPrompt().trim().isEmpty()) {
                    system = modelConfig.getSystemPrompt();
                }
                // 获取模型专属最大历史消息保留条数
                if (modelConfig.getMaxHistoryMessages() != null) {
                    max = modelConfig.getMaxHistoryMessages();
                }
            }
        }

        // 2. 若无专属提示词，回退获取特定角色的系统提示词（含全局默认兜底）
        if (system == null) {
            system = promptResolver.getRoleSpecificSystemPrompt(userId);
        }

        if (system != null && !system.isEmpty()) {
            list.add(SystemMessage.from(system));
        }

        // 裁剪历史消息，只保留最近 max 条
        int start = Math.max(0, history.size() - max);
        for (int i = start; i < history.size(); i++) {
            com.polaris.ai.domain.AiMessage m = history.get(i);
            if ("user".equals(m.getRole())) {
                String fileUrl = m.getFileUrl();
                if (fileUrl != null && !fileUrl.trim().isEmpty()) {
                    String fileName = m.getFileName() != null ? m.getFileName() : "";

                    String[] urls = fileUrl.split(",");
                    String[] names = fileName.split(",");

                    List<Content> contents = new ArrayList<>();
                    contents.add(TextContent.from(m.getContent()));

                    boolean hasMultimodal = false;
                    int imageCount = 0;
                    for (int j = 0; j < urls.length; j++) {
                        String url = urls[j].trim();
                        if (url.isEmpty()) continue;
                        String name = j < names.length ? names[j].trim() : url.substring(url.lastIndexOf("/") + 1);

                        if (mediaHelper.isImageFile(name)) {
                            String base64 = mediaHelper.convertImageToBase64(url);
                            if (base64 != null) {
                                contents.add(ImageContent.from(base64, mediaHelper.getImageMimeType(name)));
                                hasMultimodal = true;
                                imageCount++;
                            }
                        } else if (name.toLowerCase().endsWith(".pdf")) {
                            List<String> pagesBase64 = mediaHelper.renderPdfPagesToBase64(url, 3);
                            if (pagesBase64 != null && !pagesBase64.isEmpty()) {
                                for (String pageBase64 : pagesBase64) {
                                    contents.add(ImageContent.from(pageBase64, "image/png"));
                                }
                                hasMultimodal = true;
                            }
                        }
                    }

                    // 多图场景注入顺序说明，让模型能准确理解用户对"图1/图2"的指代（改图/参考图工具依赖此顺序）
                    String orderNote = imageCount >= 2
                            ? "【本条消息包含 " + imageCount + " 张图片，按上传先后顺序依次为图1、图2……"
                                + "请严格按此序号理解用户对『图N』的指代】\n"
                            : "";

                    if (m.getFileContent() != null && !m.getFileContent().trim().isEmpty()) {
                        String combinedPrompt = orderNote + m.getContent() + "\n\n"
                                + "--------------------------------------------------\n"
                                + "[已为您解析并关联对话附件: " + fileName + "]\n"
                                + "--------------------------------------------------\n"
                                + m.getFileContent();
                        if (hasMultimodal) {
                            contents.set(0, TextContent.from(combinedPrompt));
                            list.add(UserMessage.from(contents));
                        } else {
                            list.add(UserMessage.from(combinedPrompt));
                        }
                    } else {
                        if (hasMultimodal) {
                            if (!orderNote.isEmpty()) {
                                contents.set(0, TextContent.from(orderNote + m.getContent()));
                            }
                            list.add(UserMessage.from(contents));
                        } else {
                            list.add(UserMessage.from(m.getContent()));
                        }
                    }
                } else {
                    list.add(UserMessage.from(m.getContent()));
                }
            } else {
                // assistant 消息转为 LangChain4j 的 AiMessage 类型
                list.add(AiMessage.from(m.getContent()));
            }
        }
        return list;
    }

}
