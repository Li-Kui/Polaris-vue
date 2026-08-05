package com.polaris.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.polaris.ai.domain.AiImageTask;
import com.polaris.ai.image.ImageGenCommand;
import com.polaris.ai.mapper.AiChatMapper;
import com.polaris.ai.service.IImageGenerationService;
import com.polaris.ai.tools.base.AiAgentTool;
import com.polaris.ai.tools.base.AiTool;
import com.polaris.ai.tools.base.AiToolPermission;
import com.polaris.ai.utils.ChatContextHolder;
import com.polaris.ai.utils.ToolSseHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CancellationException;

/**
 * AI 异步绘图工具，负责上下文垫图抓取与任务编排，具体厂商调用委托给绘图适配器
 * 
 * @author polaris
 */
@Slf4j
@Component
@AiAgentTool("AI绘画工具")
public class ImageGenerateTools implements AiTool {

    @Autowired
    private IImageGenerationService imageGenerationService;

    @Autowired
    private AiChatMapper aiChatMapper;

    @Tool("当用户想要画图、生成图片，或需要基于对话中已上传的图片进行改色、修改细节、换背景、风格参考重绘时调用此工具。" +
         "无需也无法提供图片URL，系统会自动抓取当前对话中用户上传的图片。" +
         "【关键】必须根据用户意图正确设置 editMode 参数：" +
         "1. 用户要求在已上传图片基础上修改（改颜色、改细节、换背景、去除/添加元素等）→ editMode 传 'edit'；" +
         "2. 用户要求参考已上传图片的风格/元素/颜色来生成新图（如'参考图2的颜色画图1的车'）→ editMode 传 'reference'；" +
         "3. 用户只是要求凭空画一张全新的图，与已上传图片无关 → editMode 传 'generate' 或不传。" +
         "【颜色铁律】当用户要求参考另一张图的颜色/风格/材质时，严禁在 prompt 中写出任何具体颜色名称（如红、蓝、橙、深色等）。" +
         "因为你看到的是被压缩、可能严重失真的图片，你识别的颜色往往是错的；真实颜色必须交给绘图模型直接从原图像素读取。" +
         "你只能用『图N的颜色』这类引用方式表达。正确：『将图1汽车的车身颜色替换为图2汽车的车身颜色』；错误：『将图1的车改成橙色』。" +
         "【颜色互换/多图修改场景】当用户要求『互换两张图的颜色』或分别对多张图片进行修改时，修改每张图属于独立的绘图任务。你必须针对每一张图片分别调用一次本工具（例如第一次调用将图1改成图2的颜色，第二次调用将图2改成图1的颜色），严禁合并为同一次工具调用。" +
         "【重要约束】工具执行后会返回一个 JSON 字符串，你可以附带简短的友善说明，但必须在回复中包含工具返回的 JSON 内容。")
    @AiToolPermission("ai:draw:list")
    public String drawImage(
            @P("必须传入，绘图/改图的具体提示词。必须是纯粹独立的单图画面视觉描述（如'一只活泼的橘色小猫...'）。"
             + "【多图纯净铁律】绝对严禁包含'生成N张'、'第1张/第2张'、'1. 2. 3.'等数量词或列表编号！数量请用 count 或 prompts 传。在 prompt 里写数量会导致绘图模型画成多宫格拼图！"
             + "【严禁臆测颜色】参考另一张图的颜色时，绝不允许写出具体颜色名（红/蓝/橙等），只能写『图N的颜色』，让绘图模型自己从原图读取真实颜色。") String prompt,
            @P("操作模式：'edit'=在已上传图片上编辑修改(改色/改细节/换背景)；'reference'=参考已上传图片生成新图；'generate'或不传=纯文生图(不使用任何已上传图片)") String editMode,
            @P("生成图片的张数，正整数，默认 1。当用户要求生成多张图时传入对应的数值") Integer count,
            @P("【推荐多图使用】针对多图生成的独立详细描述列表（例如 ['一只橘色小猫...', '一只灰色小猫...']）。每一条描述必须是纯粹独立的单图视觉描述，绝对严禁包含'第1张'、'1. 2.'等编号！若提供此列表，系统将为每条描述单独并发渲染，效果最佳。") List<String> prompts
    ) {
        ToolSseHolder.ensureActive();
        int realCount = (count != null && count > 0) ? count : 1;
        if (prompts != null && !prompts.isEmpty()) {
            realCount = prompts.size();
        }
        log.info(">>> [ImageGenerateTools] 触发绘图工具, prompt: {}, editMode: {}, count: {}, promptsSize: {}", 
                prompt, editMode, realCount, prompts != null ? prompts.size() : 0);

        // 是否需要使用对话中已上传的图片作为底图/参照图
        boolean useUploaded = "edit".equalsIgnoreCase(editMode) || "reference".equalsIgnoreCase(editMode);

        List<String> imageUrls = new ArrayList<>();
        if (useUploaded) {
            // 图片URL统一由后端从会话上下文自动抓取，不再依赖 LLM 传入（LLM 只持有base64，无真实URL）
            imageUrls = getLatestImageUrlsContext();
            if (imageUrls.isEmpty()) {
                log.warn(">>> [ImageGenerateTools] editMode={} 但未抓取到已上传图片，降级为纯文生图", editMode);
            }
        }

        // 装配命令
        ImageGenCommand cmd = new ImageGenCommand();
        cmd.setPrompt(cleanPrompt(prompt));
        if (prompts != null && !prompts.isEmpty()) {
            List<String> cleanedList = new ArrayList<>();
            for (String p : prompts) {
                String c = cleanPrompt(p);
                if (c != null && !c.isEmpty()) {
                    cleanedList.add(c);
                }
            }
            if (!cleanedList.isEmpty()) {
                cmd.setPrompts(cleanedList);
                realCount = cleanedList.size();
            }
        }
        cmd.setN(realCount);
        cmd.setConversationId(ChatContextHolder.getConversationId());

        if (!imageUrls.isEmpty()) {
            if ("edit".equalsIgnoreCase(editMode)) {
                // 编辑模式：以第一张为底图
                cmd.setGenerationMode("image_edit");
            } else {
                // 参考模式：图生图，以第一张为底图，其余为参照图
                cmd.setGenerationMode("image_to_image");
            }
            cmd.setSourceImages(imageUrls);
            // extra 透传底图/参照图索引，供后端适配器精确路由
            Map<String, Object> extra = new HashMap<>();
            extra.put("baseImageIdx", 0);
            extra.put("refImageIdx", imageUrls.size() > 1 ? 1 : -1);
            cmd.setExtra(extra);
            log.info(">>> [ImageGenerateTools] 参考图共 {} 张, editMode={}, mode={}, targetCount={}",
                    imageUrls.size(), editMode, cmd.getGenerationMode(), realCount);
        } else {
            cmd.setGenerationMode("text_to_image");
        }

        try {
            ToolSseHolder.ensureActive();
            AiImageTask task = imageGenerationService.submit(cmd);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "image-task");
            result.put("taskId", task.getTaskId());
            result.put("prompt", prompt);
            result.put("status", "processing");
            result.put("targetCount", realCount);
            result.put("sourceCount", imageUrls.size());
            String jsonResult = JSON.toJSONString(result);
            ChatContextHolder.addTaskJson(cmd.getConversationId(), jsonResult);
            return jsonResult;
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>> [ImageGenerateTools] 发起绘图任务失败", e);
            Map<String, String> fail = new LinkedHashMap<>();
            fail.put("type", "image-task");
            fail.put("status", "fail");
            fail.put("errorMsg", e.getMessage() != null ? e.getMessage() : "发起绘图任务失败");
            String failJson = JSON.toJSONString(fail);
            ChatContextHolder.addTaskJson(cmd.getConversationId(), failJson);
            return failJson;
        }
    }

    /**
     * 从当前会话或用户最新消息上下文获取垫图/参考图图片 URL 列表
     * @return 所有符合条件的图片 URL（可能为空列表，绝不返回 null）
     */
    private List<String> getLatestImageUrlsContext() {
        try {
            Long conversationId = com.polaris.ai.utils.ChatContextHolder.getConversationId();

            // 1. 首选：从 ThreadLocal 直接获取当前消息的图（单条消息一次传多张的场景，顺序即上传顺序）
            String fileUrl = com.polaris.ai.utils.ChatContextHolder.getFileUrl();
            List<String> imageUrls = parseImageUrls(fileUrl);
            if (!imageUrls.isEmpty()) {
                return imageUrls;
            }

            // 2. 跨消息聚合：当前消息未带图时，按上传先后顺序聚合会话最近带图消息的图片
            //    （用户分多次上传后再要求"把图1改成图2的颜色"时，需要跨消息把多张图凑齐；先上传的排前面=图1）
            if (conversationId != null) {
                List<String> aggregated = aggregateRecentImages(conversationId);
                if (!aggregated.isEmpty()) {
                    return aggregated;
                }
            }

            // 3. 极端兜底：异步线程中 ThreadLocal 与 conversationId 都丢失时，取当前用户最新活动会话聚合
            try {
                Long userId = com.polaris.common.utils.SecurityUtils.getUserId();
                if (userId != null) {
                    List<com.polaris.ai.domain.AiConversation> conversations = aiChatMapper.selectConversationsByUserId(userId);
                    if (conversations != null && !conversations.isEmpty()) {
                        Long latestConvId = conversations.get(0).getId();
                        List<String> aggregated = aggregateRecentImages(latestConvId);
                        if (!aggregated.isEmpty()) {
                            return aggregated;
                        }
                    }
                }
            } catch (Exception ignored) {}

            return Collections.emptyList();
        } catch (Exception e) {
            log.error(">>> [ImageGenerateTools] 提取多图上下文异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 聚合会话中最近带图用户消息的图片 URL。
     * 按消息时间正序展开，保证先上传的图排在前（对应用户口中的"图1"）；
     * 为避免混入过早的无关历史图，只聚合最近的带图用户消息，最多 MAX_CONTEXT_IMAGES 张。
     */
    private List<String> aggregateRecentImages(Long conversationId) {
        final int MAX_MSGS = 3;      // 最多回溯最近 3 条带图用户消息
        final int MAX_IMAGES = 6;    // 最多聚合 6 张图，防止上下文膨胀
        List<com.polaris.ai.domain.AiMessage> history = aiChatMapper.selectMessagesByConversationId(conversationId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        // 从最新往回找带图的 user 消息，收集其 fileUrl（保持消息本身的时间正序）
        List<String> pickedFileUrls = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0 && pickedFileUrls.size() < MAX_MSGS; i--) {
            com.polaris.ai.domain.AiMessage m = history.get(i);
            if ("user".equals(m.getRole()) && m.getFileUrl() != null && !m.getFileUrl().trim().isEmpty()) {
                pickedFileUrls.add(m.getFileUrl());
            }
        }
        // pickedFileUrls 目前是"新→老"，反转成"老→新"，使先上传的图排前面
        Collections.reverse(pickedFileUrls);
        List<String> imageUrls = new ArrayList<>();
        for (String fu : pickedFileUrls) {
            for (String url : parseImageUrls(fu)) {
                if (imageUrls.size() >= MAX_IMAGES) break;
                imageUrls.add(url);
            }
        }
        return imageUrls;
    }

    /**
     * 解析逗号分隔的 fileUrl 字符串，筛出图片 URL（剔除文档类附件），保持原始顺序。
     */
    private List<String> parseImageUrls(String fileUrl) {
        List<String> imageUrls = new ArrayList<>();
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return imageUrls;
        }
        String[] urls = fileUrl.split(",");
        for (String url : urls) {
            String trimmedUrl = url.trim();
            if (trimmedUrl.isEmpty()) continue;
            String lower = trimmedUrl.toLowerCase();
            if (trimmedUrl.startsWith("data:image/") || lower.endsWith(".png") || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif")
                    || lower.contains("/profile/")) {
                imageUrls.add(trimmedUrl);
            }
        }
        return imageUrls;
    }

    /**
     * 清洗提示词：剔除容易诱发绘图模型渲染成多宫格拼图的数量词与标号（如"生成3张..."、"1. xx 2. xx"）
     */
    private String cleanPrompt(String p) {
        if (p == null || p.trim().isEmpty()) return p;
        String s = p.trim();
        // 剔除开头的"生成N张..."或"本次生成N张图片..."
        s = s.replaceAll("^(本次|一共|共|自动|请|帮我)?(生成|绘制|画)\\d+张[^：:\\n]*[：:\\n\\s]*", "");
        // 剔除前缀如 "1. " 或 "图1: "
        s = s.replaceAll("^(第\\d+张|图\\d+|\\d+[\\.\\、\\:\\s])\\s*", "");
        return s.trim();
    }
}
