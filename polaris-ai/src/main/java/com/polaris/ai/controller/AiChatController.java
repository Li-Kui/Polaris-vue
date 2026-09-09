package com.polaris.ai.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.domain.AiConversation;
import com.polaris.ai.domain.AiMessage;
import com.polaris.ai.service.IAiChatService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 对话 Controller
 * <p>
 * 职责：
 * 1. 提供 AI 对话页面的路由跳转
 * 2. 提供会话的增删改查 REST 接口（供前端 AJAX 调用）
 * 3. 提供 SSE 流式对话接口（供前端 EventSource 连接）
 * <p>
 * 接口路径前缀：/ai/chat
 * 页面模板路径：templates/ai/chat.html
 *
 * @author polaris
 */
@Tag(name = "AI对话")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Controller
@RequestMapping("/ai/chat")
public class AiChatController extends BaseController {
    @Autowired
    private IAiChatService aiChatService;

    /**
     * 跳转到 AI 对话主页面
     * GET /ai/chat
     *
     * @return Thymeleaf 模板路径 templates/ai/chat.html
     */
    @Operation(summary = "跳转到 AI 对话主页面")
    @GetMapping()
    public String chatPage() {
        return "ai/chat";
    }

    /**
     * 获取当前登录用户的会话列表
     * GET /ai/chat/conversations
     *
     * @return 会话列表，按最后更新时间倒序，最多返回 50 条
     */
    @Operation(summary = "获取当前登录用户的会话列表")
    @GetMapping("/conversations")
    @ResponseBody
    public ResultData listConversations() {
        Long userId = CallerUtils.getUserId();
        List<AiConversation> list = aiChatService.listConversations(userId);
        return ok(list);
    }

    /**
     * 新建会话
     * POST /ai/chat/conversations
     *
     * @param model 指定使用的模型名称（可选），不传时使用 application.yml 配置的默认模型
     * @return 新建成功的会话实体（含 id、title、model 等字段）
     */
    @Operation(summary = "新建会话")
    @PostMapping("/conversations")
    @ResponseBody
    public ResultData createConversation(
            @RequestParam(required = false) Long modelConfigId,
            @RequestParam(required = false) Long knowledgeBaseId) {
        Long userId = CallerUtils.getUserId();
        AiConversation conv = aiChatService.createConversation(userId, modelConfigId, knowledgeBaseId);
        return ok(conv);
    }

    /**
     * 重命名会话标题
     * PUT /ai/chat/conversations/{id}/title
     *
     * @param id    会话 ID（路径参数）
     * @param title 新标题（表单参数）
     * @return 操作结果
     */
    @Operation(summary = "重命名会话标题")
    @PutMapping("/conversations/{id}/title")
    @ResponseBody
    public ResultData renameConversation(@PathVariable Long id, @RequestParam String title) {
        Long userId = CallerUtils.getUserId();
        aiChatService.renameConversation(id, title, userId);
        return ok();
    }

    /**
     * 更新会话配置（大模型和知识库、智能体、工作流）
     * PUT /ai/chat/conversations/{id}/config
     */
    @Operation(summary = "更新会话配置")
    @PutMapping("/conversations/{id}/config")
    @ResponseBody
    public ResultData updateConversationConfig(
            @PathVariable Long id,
            @RequestParam(required = false) Long modelConfigId,
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(required = false) String agentCode,
            @RequestParam(required = false) String workflowCode) {
        Long userId = CallerUtils.getUserId();
        aiChatService.updateConversationConfig(id, modelConfigId, knowledgeBaseId, agentCode, workflowCode, userId);
        return ok();
    }

    /**
     * 删除会话（逻辑删除会话 + 物理删除该会话下所有消息）
     * DELETE /ai/chat/conversations/{id}
     * 操作记录写入北辰操作日志
     *
     * @param id 会话 ID（路径参数）
     * @return 操作结果
     */
    @Operation(summary = "删除会话")
    @Log(title = "AI对话", businessType = BusinessType.DELETE)
    @DeleteMapping("/conversations/{id}")
    @ResponseBody
    public ResultData deleteConversation(@PathVariable Long id) {
        Long userId = CallerUtils.getUserId();
        aiChatService.deleteConversation(id, userId);
        return ok();
    }

    /**
     * 批量删除会话（逻辑删除会话列表 + 物理删除关联的所有消息）
     * DELETE /ai/chat/conversations/batch
     *
     * @param ids 会话 ID 列表
     * @return 操作结果
     */
    @Operation(summary = "批量删除会话")
    @Log(title = "AI对话", businessType = BusinessType.DELETE)
    @DeleteMapping("/conversations/batch")
    @ResponseBody
    public ResultData deleteConversationsBatch(@RequestBody List<Long> ids) {
        Long userId = CallerUtils.getUserId();
        aiChatService.deleteConversationsBatch(ids, userId);
        return ok();
    }

    /**
     * 获取指定会话的消息历史
     * GET /ai/chat/conversations/{id}/messages
     * 会校验会话归属，防止越权查看他人消息
     *
     * @param id 会话 ID（路径参数）
     * @return 消息列表，按时间正序，最多返回 100 条
     */
    @Operation(summary = "获取指定会话的消息历史")
    @GetMapping("/conversations/{id}/messages")
    @ResponseBody
    public ResultData listMessages(@PathVariable Long id) {
        Long userId = CallerUtils.getUserId();
        List<AiMessage> messages = aiChatService.listMessages(id, userId);
        return ok(messages);
    }

    /**
     * 发送消息，以 SSE（Server-Sent Events）流式返回 AI 回复
     * GET /ai/chat/stream?conversationId=xxx&message=xxx
     * <p>
     * 工作原理：
     * 1. 创建 SseEmitter（timeout=0 不超时，由服务端在对话完成后主动关闭）
     * 2. 启动新线程异步调用 AI 接口，避免占用 Tomcat 线程池
     * 3. AI 每生成一个 token，通过 emitter 实时推送给前端
     * 4. 前端使用原生 EventSource API 接收，实现打字机效果
     * <p>
     * 前端监听的事件类型：
     * message —— 收到一个 token 片段
     * done    —— 流式输出完成
     * error   —— 服务端发生异常
     *
     * @param conversationId 会话 ID
     * @param message        用户输入的消息内容
     * @return SseEmitter 实例（Spring 自动将其转为 text/event-stream 响应）
     */
    @Operation(summary = "流式发送消息并获取回复")
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    @ResponseBody
    public SseEmitter stream(@RequestParam Long conversationId,
                             @RequestParam String message,
                             @RequestParam(required = false) String fileUrl,
                             @RequestParam(required = false) String agentCode,
                             @RequestParam(required = false, defaultValue = "false") Boolean enableSearch) {
        SseEmitter emitter = new SseEmitter(180_000L);
        java.util.concurrent.atomic.AtomicBoolean isCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        emitter.onCompletion(() -> isCancelled.set(true));
        emitter.onTimeout(() -> {
            isCancelled.set(true);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(e -> isCancelled.set(true));

        Long userId = CallerUtils.getUserId();

        // 抓取当前主线程的域名与端口，利用 polaris-common 的 ServletUtils 规避跨模块依赖
        String baseUrl = "";
        try {
            jakarta.servlet.http.HttpServletRequest request = com.polaris.common.utils.ServletUtils.getRequest();
            StringBuffer url = request.getRequestURL();
            String contextPath = request.getServletContext().getContextPath();
            baseUrl = url.delete(url.length() - request.getRequestURI().length(), url.length()).append(contextPath).toString();
        } catch (Exception e) {
            baseUrl = "";
        }
        final String finalBaseUrl = baseUrl;

        // 获取当前主线程的安全上下文与调用者上下文（包含已登录用户信息）
        final SecurityContext context = SecurityContextHolder.getContext();
        final CallerContext callerCtx = CallerContextHolder.get();

        // 新线程异步执行，当前 Tomcat 线程立即返回 emitter，不阻塞线程池
        new Thread(() -> {
            try {
                // 将安全上下文与调用者上下文绑定到子线程
                SecurityContextHolder.setContext(context);
                if (callerCtx != null) {
                    CallerContextHolder.set(callerCtx);
                }
                // 将域名设置到 ThreadLocal 中
                com.polaris.ai.utils.BaseUrlHolder.set(finalBaseUrl);
                aiChatService.chat(conversationId, message, fileUrl, agentCode, enableSearch, userId, emitter, isCancelled);
            } finally {
                // 清理 ThreadLocal
                com.polaris.ai.utils.BaseUrlHolder.clear();
                CallerContextHolder.clear();
                // 执行完成后清除上下文，避免对线程造成污染
                SecurityContextHolder.clearContext();
            }
        }).start();
        return emitter;
    }

    /**
     * POST 流式发送消息（支持私有附件 tokens 与标准 JSON 请求体）
     */
    @Operation(summary = "流式发送消息并获取回复 (POST)")
    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    @ResponseBody
    public SseEmitter streamPost(@RequestBody com.polaris.ai.dto.ChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        java.util.concurrent.atomic.AtomicBoolean isCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        emitter.onCompletion(() -> isCancelled.set(true));
        emitter.onTimeout(() -> {
            isCancelled.set(true);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(e -> isCancelled.set(true));

        Long userId = CallerUtils.getUserId();

        String baseUrl = "";
        try {
            jakarta.servlet.http.HttpServletRequest req = com.polaris.common.utils.ServletUtils.getRequest();
            StringBuffer url = req.getRequestURL();
            String contextPath = req.getServletContext().getContextPath();
            baseUrl = url.delete(url.length() - req.getRequestURI().length(), url.length()).append(contextPath).toString();
        } catch (Exception e) {
            baseUrl = "";
        }
        final String finalBaseUrl = baseUrl;
        final SecurityContext context = SecurityContextHolder.getContext();
        final CallerContext callerCtx = CallerContextHolder.get();

        new Thread(() -> {
            try {
                SecurityContextHolder.setContext(context);
                if (callerCtx != null) {
                    CallerContextHolder.set(callerCtx);
                }
                com.polaris.ai.utils.BaseUrlHolder.set(finalBaseUrl);
                aiChatService.chat(request, userId, emitter, isCancelled);
            } finally {
                com.polaris.ai.utils.BaseUrlHolder.clear();
                CallerContextHolder.clear();
                SecurityContextHolder.clearContext();
            }
        }).start();
        return emitter;
    }
}
