package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.attachment.AttachmentParserHelper;
import com.polaris.ai.chat.AiAssistant;
import com.polaris.ai.domain.AiConversation;
import com.polaris.ai.mapper.AiChatMapper;
import com.polaris.ai.pivot.AiModelProperties;
import com.polaris.ai.service.IAiChatService;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.ai.tools.base.AiTool;
import com.polaris.common.config.PolarisConfig;
import com.polaris.common.core.domain.entity.SysRole;
import com.polaris.common.utils.SecurityUtils;
import com.polaris.system.service.ISysConfigService;
import com.polaris.system.service.ISysRoleService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

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
    private ISysRoleService roleService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private com.polaris.ai.pivot.AiModelFactory modelFactory;

    @Autowired(required = false)
    private List<AiTool> aiTools = new ArrayList<>();

    // ----------------------------------------------------------------
    // 会话管理
    // ----------------------------------------------------------------

    /**
     * 新建会话 model 参数为空时，取 application.yml 中配置 of 默认模型名
     */
    @Override
    public AiConversation createConversation(Long userId, String model, Long knowledgeBaseId) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle("新对话");
        conv.setModel(model == null ? modelProps.getModelName() : model);
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
    public int updateConversationConfig(Long id, String model, Long knowledgeBaseId, Long userId) {
        return aiChatMapper.updateConversationConfig(id, model, knowledgeBaseId, userId);
    }

    /**
     * 删除会话 先物理删除该会话下所有消息，再逻辑删除会话本身
     */
    @Override
    public int deleteConversation(Long id, Long userId) {
        aiChatMapper.deleteMessagesByConversationId(id);
        return aiChatMapper.deleteConversation(id, userId);
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
    public void chat(Long conversationId, String userInput, String fileUrl, Boolean enableSearch, Long userId, SseEmitter emitter) {
        // 1. 鉴权：会话必须属于当前用户
        AiConversation conv = aiChatMapper.selectConversationById(conversationId, userId);
        if (conv == null) {
            sendSse(emitter, "error", "会话不存在或无权限");
            return;
        }

        // 2. 解析附件并持久化用户消息
        String parsedAttachmentContent = null;
        String fileName = null;
        if (fileUrl != null && !fileUrl.trim().isEmpty()) {
            sendSse(emitter, "status", "正在解析文件附件...");
            parsedAttachmentContent = AttachmentParserHelper.parse(fileUrl);
            sendSse(emitter, "status", "附件解析成功，正在初始化 AI 思考...");
            fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

            // 安全限制：若解析内容过大超过 28000 字符，进行截断，防止超限报错
            if (parsedAttachmentContent != null && parsedAttachmentContent.length() > 28000) {
                parsedAttachmentContent = parsedAttachmentContent.substring(0, 28000)
                        + "\n\n...[由于文件附件体积过大，已自动截断保留前 28000 字符内容]...";
            }
        } else {
            sendSse(emitter, "status", "正在呼叫 AI 助手...");
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

        // 4. 构建完整的消息上下文（SystemMessage + 历史消息）
        List<ChatMessage> messages = buildMessages(conversationId, userId);

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
        SecurityContext securityContext = SecurityContextHolder.getContext();
        String searchKey = null;
        try {
            com.polaris.ai.domain.AiModelConfig modelConfig = modelConfigService.selectModelConfigByModelName(conv.getModel());
            if (modelConfig != null) {
                searchKey = modelConfig.getSearchKey();
            }
        } catch (Exception e) {
            log.error(">>> 查询模型配置失败，无法提取 searchKey: {}", e.getMessage());
        }
        Map<ToolSpecification, ToolExecutor> tools = getContextAwareTools(securityContext, Boolean.TRUE.equals(enableSearch), searchKey);

        // 动态根据当前会话绑定的模型名称，获取对应的执行模型实例
        StreamingChatModel targetChatModel = modelFactory.getStreamingModel(conv.getModel());

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
                        sendSse(emitter, "status", "");
                    }
                    fullReply.append(token);
                    String escapedToken = token != null ? token.replace("\n", "__SSE_NEWLINE__") : "";
                    sendSse(emitter, "message", escapedToken);
                })
                .onPartialThinking(thinking -> {
                    if (thinking != null && thinking.text() != null) {
                        fullReasoning.append(thinking.text());
                        String escapedThinking = thinking.text().replace("\n", "__SSE_NEWLINE__");
                        sendSse(emitter, "reasoning", escapedThinking);
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
                        // 记录本次对话消耗的 Token 总数（用于统计和计费）
                        if (response != null && response.tokenUsage() != null) {
                            aiMsg.setTokens(response.tokenUsage().totalTokenCount());
                        }
                        aiChatMapper.insertMessage(aiMsg);
                    }
                    // 通知前端流式结束，前端收到后关闭 EventSource
                    sendSse(emitter, "done", "[DONE]");
                    emitter.complete();
                })
                .onError(error -> {
                    log.error("LangChain4j 声明式 AI 助手流式调用异常", error);
                    sendSse(emitter, "error", "AI 服务异常：" + error.getMessage());
                    emitter.complete();
                })
                .start();
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
        if (conv != null && conv.getModel() != null) {
            com.polaris.ai.domain.AiModelConfig modelConfig = modelConfigService.selectModelConfigByModelName(conv.getModel());
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
            system = getRoleSpecificSystemPrompt(userId);
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
                    
                    if (isImageFile(fileName)) {
                        // A. 图片多模态处理：转换为 Base64 直接发给大模型
                        String base64 = convertImageToBase64(fileUrl);
                        if (base64 != null) {
                            List<Content> contents = new ArrayList<>();
                            contents.add(TextContent.from(m.getContent()));
                            contents.add(ImageContent.from(base64, getImageMimeType(fileName)));
                            list.add(UserMessage.from(contents));
                        } else {
                            list.add(UserMessage.from(m.getContent()));
                        }
                    } else if (fileName.toLowerCase().endsWith(".pdf")) {
                        // B. PDF 多模态图文解析：运行时利用 PDFBox 渲染前 3 页为图片
                        List<String> pagesBase64 = renderPdfPagesToBase64(fileUrl, 3);
                        if (!pagesBase64.isEmpty()) {
                            List<Content> contents = new ArrayList<>();
                            String combinedPrompt = m.getContent() + "\n\n"
                                    + "--------------------------------------------------\n"
                                    + "[已为您解析并关联对话 PDF 附件: " + fileName + "]\n"
                                    + "--------------------------------------------------\n"
                                    + m.getFileContent();
                            contents.add(TextContent.from(combinedPrompt));
                            for (String pageBase64 : pagesBase64) {
                                contents.add(ImageContent.from(pageBase64, "image/png"));
                            }
                            list.add(UserMessage.from(contents));
                        } else {
                            // 降级为纯文本拼接
                            if (m.getFileContent() != null && !m.getFileContent().trim().isEmpty()) {
                                String combinedPrompt = m.getContent() + "\n\n"
                                        + "--------------------------------------------------\n"
                                        + "[已为您解析并关联对话附件: " + fileName + "]\n"
                                        + "--------------------------------------------------\n"
                                        + m.getFileContent();
                                list.add(UserMessage.from(combinedPrompt));
                            } else {
                                list.add(UserMessage.from(m.getContent()));
                            }
                        }
                    } else {
                        // C. 其他纯文本文档：常规提取纯文本拼接
                        if (m.getFileContent() != null && !m.getFileContent().trim().isEmpty()) {
                            String combinedPrompt = m.getContent() + "\n\n"
                                    + "--------------------------------------------------\n"
                                    + "[已为您解析并关联对话附件: " + fileName + "]\n"
                                    + "--------------------------------------------------\n"
                                    + m.getFileContent();
                            list.add(UserMessage.from(combinedPrompt));
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

    private boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext) || "gif".equals(ext) || "webp".equals(ext) || "bmp".equals(ext);
    }

    private String getImageMimeType(String fileName) {
        if (fileName == null) return "image/jpeg";
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (ext) {
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            default: return "image/jpeg";
        }
    }

    private String convertImageToBase64(String fileUrl) {
        try {
            String localPath = PolarisConfig.getProfile();
            String relativePath = fileUrl;
            if (fileUrl.startsWith("/profile")) {
                relativePath = fileUrl.substring("/profile".length());
            }
            File file = new File(localPath + relativePath);
            if (file.exists()) {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                // 动态缩放与压缩，初始参数设为更安全的 600x600, 0.45f，并在内部支持自适应降级循环
                byte[] compressedBytes = compressImage(fileBytes, 600, 600, 0.45f);
                return Base64.getEncoder().encodeToString(compressedBytes);
            } else {
                log.warn(">>> 未找到多模态图片文件: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error(">>> 读取图片并转换为 Base64 失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private byte[] compressImage(byte[] imageBytes, int maxWidth, int maxHeight, float quality) {
        byte[] resultBytes = imageBytes;
        int currentWidth = maxWidth;
        int currentHeight = maxHeight;
        float currentQuality = quality;

        try {
            // 自适应降级压缩循环，最多尝试 4 次，直到 Base64 长度小于 120,000 字符（对应 90KB 左右）
            for (int i = 0; i < 4; i++) {
                resultBytes = compressImageOnce(imageBytes, currentWidth, currentHeight, currentQuality);
                String base64 = Base64.getEncoder().encodeToString(resultBytes);
                
                if (base64.length() < 120000) {
                    log.info(">>> 图片动态压缩第 {} 次成功，原体积: {} bytes, 压缩后体积: {} bytes, Base64字符长度: {}", 
                            i + 1, imageBytes.length, resultBytes.length, base64.length());
                    break;
                }
                
                log.warn(">>> 图片第 {} 次压缩后 Base64 长度 {} 仍超 120000 限额，启动等比降级...", i + 1, base64.length());
                currentWidth = (int) (currentWidth * 0.75);
                currentHeight = (int) (currentHeight * 0.75);
                currentQuality = currentQuality * 0.75f;
            }
        } catch (Exception e) {
            log.error(">>> 自适应压缩失败，回退到原图: {}", e.getMessage());
        }
        return resultBytes;
    }

    private byte[] compressImageOnce(byte[] imageBytes, int maxWidth, int maxHeight, float quality) throws Exception {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes)) {
            BufferedImage originalImage = ImageIO.read(bais);
            if (originalImage == null) return imageBytes;

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            // 计算等比缩放比例
            if (width > maxWidth || height > maxHeight) {
                double widthRatio = (double) maxWidth / width;
                double heightRatio = (double) maxHeight / height;
                double ratio = Math.min(widthRatio, heightRatio);
                width = (int) (width * ratio);
                height = (int) (height * ratio);
            }

            BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = resizedImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, width, height, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            java.util.Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                javax.imageio.ImageWriter writer = writers.next();
                try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(quality);
                    }
                    writer.write(null, new javax.imageio.IIOImage(resizedImage, null, null), param);
                } catch (Exception err) {
                    baos.reset();
                    ImageIO.write(resizedImage, "jpg", baos);
                } finally {
                    writer.dispose();
                }
            } else {
                ImageIO.write(resizedImage, "jpg", baos);
            }

            return baos.toByteArray();
        }
    }

    private List<String> renderPdfPagesToBase64(String fileUrl, int maxPages) {
        List<String> resultList = new ArrayList<>();
        try {
            String localPath = PolarisConfig.getProfile();
            String relativePath = fileUrl;
            if (fileUrl.startsWith("/profile")) {
                relativePath = fileUrl.substring("/profile".length());
            }
            File file = new File(localPath + relativePath);
            if (!file.exists()) {
                log.warn(">>> 未找到多模态 PDF 文件: {}", file.getAbsolutePath());
                return resultList;
            }

            try (PDDocument document = PDDocument.load(file)) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                int pagesToRender = Math.min(document.getNumberOfPages(), maxPages);
                log.info(">>> 开始对 PDF [{}] 进行渲染，总页数: {}, 限制渲染页数: {}", file.getName(), document.getNumberOfPages(), pagesToRender);
                for (int i = 0; i < pagesToRender; i++) {
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 110, ImageType.RGB);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bim, "png", baos);
                    byte[] bytes = baos.toByteArray();
                    resultList.add(Base64.getEncoder().encodeToString(bytes));
                }
            }
        } catch (Exception e) {
            log.error(">>> 渲染 PDF 页面为图片 Base64 失败: {}", e.getMessage(), e);
        }
        return resultList;
    }

    /**
     * 根据当前登录用户的角色获取对应的系统提示词。
     * 1. 查询用户所属的角色列表。
     * 2. 过滤正常运行的角色并按照 roleSort 升序排列。
     * 3. 逐个查找 sys.ai.prompt.[roleKey] 的参数配置，若存在则收集。
     * 4. 若收集到配置的 Prompt，则按换行符拼接。
     * 5. 若均未配置，则回退到全局默认 Prompt（从 modelProps 获取）。
     *
     * @param userId 用户 ID
     * @return 合并或回退后的系统提示词
     */
    private String getRoleSpecificSystemPrompt(Long userId) {
        if (userId == null) {
            return modelProps.getSystemPrompt();
        }
        try {
            List<SysRole> roles = roleService.selectRolesByUserId(userId);
            if (roles != null && !roles.isEmpty()) {
                List<String> rolePrompts = roles.stream()
                        .filter(role -> "0".equals(role.getStatus()) && (role.getDelFlag() == null || !"2".equals(role.getDelFlag())))
                        .sorted(Comparator.comparing(SysRole::getRoleSort, Comparator.nullsLast(Integer::compareTo)))
                        .map(role -> {
                            String configKey = "sys.ai.prompt." + role.getRoleKey();
                            return configService.selectConfigByKey(configKey);
                        })
                        .filter(prompt -> prompt != null && !prompt.trim().isEmpty())
                        .map(String::trim)
                        .collect(Collectors.toList());

                if (!rolePrompts.isEmpty()) {
                    return String.join("\n", rolePrompts);
                }
            }
        } catch (Exception e) {
            log.error("根据用户ID: {} 获取角色专属提示词发生异常", userId, e);
        }
        return modelProps.getSystemPrompt();
    }

    /**
     * 向前端安全推送一条 SSE 事件
     * 捕获异常防止因客户端断开连接而导致整个线程崩溃
     *
     * @param emitter SSE 发射器
     * @param event   事件名称（message / done / error）
     * @param data    事件数据
     */
    private void sendSse(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.warn("SSE 推送失败，客户端可能已断开连接: event={}", event);
        }
    }

    /**
     * 通用反射解析 AI 工具并绑定当前线程安全上下文与请求上下文
     */
    private Map<ToolSpecification, ToolExecutor> getContextAwareTools(SecurityContext securityContext, boolean enableSearch, String searchKey) {
        Map<ToolSpecification, ToolExecutor> map = new HashMap<>();
        if (aiTools == null || aiTools.isEmpty()) {
            return map;
        }

        // 捕获当前的 RequestAttributes 并拷贝到 SimpleRequestAttributes 容器中，以防容器回收
        SimpleRequestAttributes simpleAttrs = new SimpleRequestAttributes(RequestContextHolder.getRequestAttributes());

        for (Object toolObj : aiTools) {
            Class<?> targetClass = AopUtils.getTargetClass(toolObj);
            // 过滤：如果未开启联网搜索，且当前工具类是 WebSearchTools，则直接跳过不注册
            if (targetClass.getSimpleName().contains("WebSearchTools") && !enableSearch) {
                continue;
            }
            java.lang.reflect.Method[] methods = targetClass.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                    ToolExecutor originalExecutor = new DefaultToolExecutor(toolObj, method);
                    ToolExecutor wrappedExecutor = new SecurityContextPropagatingToolExecutor(originalExecutor, securityContext, simpleAttrs, searchKey);
                    map.put(spec, wrappedExecutor);
                }
            }
        }
        return map;
    }

    /**
     * 通用 ToolExecutor 装饰器，实现跨线程安全上下文、请求上下文与联网搜索密钥传递
     */
    private static class SecurityContextPropagatingToolExecutor implements ToolExecutor {
        private final ToolExecutor delegate;
        private final SecurityContext securityContext;
        private final RequestAttributes requestAttributes;
        private final String searchKey;

        public SecurityContextPropagatingToolExecutor(ToolExecutor delegate, SecurityContext securityContext, RequestAttributes requestAttributes, String searchKey) {
            this.delegate = delegate;
            this.securityContext = securityContext;
            this.requestAttributes = requestAttributes;
            this.searchKey = searchKey;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
            try {
                SecurityContextHolder.setContext(securityContext);
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                // 绑定联网搜索 Key 到执行线程中
                com.polaris.ai.utils.SearchKeyHolder.set(searchKey);
                return delegate.execute(request, memoryId);
            } finally {
                // 清理联网 Key，防止线程池污染
                com.polaris.ai.utils.SearchKeyHolder.clear();
                RequestContextHolder.resetRequestAttributes();
                if (previousAttributes != null) {
                    RequestContextHolder.setRequestAttributes(previousAttributes);
                }
                if (previousContext != null) {
                    SecurityContextHolder.setContext(previousContext);
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
        }
    }

    /**
     * 纯内存的线程安全 RequestAttributes 实现，专门用于在异步线程中传递请求作用域参数
     */
    private static class SimpleRequestAttributes implements RequestAttributes {
        private final Map<String, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();

        public SimpleRequestAttributes(RequestAttributes originalAttrs) {
            if (originalAttrs != null) {
                try {
                    for (String name : originalAttrs.getAttributeNames(SCOPE_REQUEST)) {
                        Object val = originalAttrs.getAttribute(name, SCOPE_REQUEST);
                        if (val != null) {
                            attributes.put(name, val);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public Object getAttribute(String name, int scope) {
            return scope == SCOPE_REQUEST ? attributes.get(name) : null;
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            if (scope == SCOPE_REQUEST) {
                if (value != null) {
                    attributes.put(name, value);
                } else {
                    attributes.remove(name);
                }
            }
        }

        @Override
        public void removeAttribute(String name, int scope) {
            if (scope == SCOPE_REQUEST) {
                attributes.remove(name);
            }
        }

        @Override
        public String[] getAttributeNames(int scope) {
            return scope == SCOPE_REQUEST ? attributes.keySet().toArray(new String[0]) : new String[0];
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback, int scope) {
        }

        @Override
        public Object resolveReference(String key) {
            return null;
        }

        @Override
        public String getSessionId() {
            return "session";
        }

        @Override
        public Object getSessionMutex() {
            return this;
        }
    }
}
