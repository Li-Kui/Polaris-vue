package com.polaris.platform.controller;

import com.alibaba.fastjson2.JSON;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.workflow.application.*;
import com.polaris.common.config.PolarisConfig;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import com.polaris.common.utils.file.FileUploadUtils;
import com.polaris.common.utils.file.MimeTypeUtils;
import com.polaris.platform.auth.ShareCallerContext;
import com.polaris.platform.auth.WorkflowShareOriginPolicy;
import com.polaris.platform.auth.WorkflowShareRateLimiter;
import com.polaris.platform.domain.WorkflowShare;
import com.polaris.platform.dto.WorkflowShareChatRequest;
import com.polaris.platform.dto.WorkflowShareExecutionView;
import com.polaris.platform.dto.WorkflowShareManifest;
import com.polaris.platform.service.IWorkflowShareService;
import com.polaris.platform.service.WorkflowShareAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 运行时端：工作流分享公开接口（permitAll）
 *
 * @author polaris
 */
@Tag(name = "运行时端：工作流分享")
@Slf4j
@RestController
@RequestMapping("/platform/runtime/shares")
public class WorkflowShareRuntimeController {

    private final IWorkflowShareService shareService;
    private final WorkflowExecutionApplicationFacade workflowFacade;
    private final WorkflowEventStreamApplicationFacade eventStreamFacade;
    private final WorkflowShareRateLimiter shareRateLimiter;
    private final WorkflowShareAttachmentService attachmentService;
    private static final String VISITOR_COOKIE = "polaris_share_visitor";

    public WorkflowShareRuntimeController(
            IWorkflowShareService shareService,
            WorkflowExecutionApplicationFacade workflowFacade,
            WorkflowEventStreamApplicationFacade eventStreamFacade,
            WorkflowShareRateLimiter shareRateLimiter,
            WorkflowShareAttachmentService attachmentService) {
        this.shareService = shareService;
        this.workflowFacade = workflowFacade;
        this.eventStreamFacade = eventStreamFacade;
        this.shareRateLimiter = shareRateLimiter;
        this.attachmentService = attachmentService;
    }

    /**
     * 验证分享并设置 CallerContext
     */
    private ShareSetup setupContext(String shareCode) {
        return setupContext(shareCode, WorkflowShareRateLimiter.RequestType.READ);
    }

    private ShareSetup setupContext(String shareCode, WorkflowShareRateLimiter.RequestType type) {
        WorkflowShare share = type == WorkflowShareRateLimiter.RequestType.READ
                ? shareService.validateAndGet(shareCode) : shareService.validateAndGet(shareCode, type);
        CallerContextHolder.set(new ShareCallerContext(
                share.getTenantId(), share.getId(), null));
        try {
            WorkflowShareDefinitionView definition = workflowFacade.getShareDefinition(
                    share.getWorkflowDefinitionId());
            return new ShareSetup(share, definition);
        } catch (RuntimeException e) {
            CallerContextHolder.clear();
            throw e;
        }
    }

    private ShareSetup setupContext(String shareCode, HttpServletRequest request) {
        return setupContext(shareCode, request, WorkflowShareRateLimiter.RequestType.READ);
    }

    private ShareSetup setupContext(String shareCode, HttpServletRequest request,
                                   WorkflowShareRateLimiter.RequestType requestType) {
        ShareSetup ctx = setupContext(shareCode, requestType);
        String visitor = visitorCookie(request);
        String type = Optional.ofNullable(ctx.share.getPageType())
                .orElse(Optional.ofNullable(ctx.definition.defaultPageType())
                        .orElse(ctx.definition.recommendedPageType()));
        if (visitor != null && "chat".equals(type)) {
            try {
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(visitor.getBytes(StandardCharsets.UTF_8)));
                CallerContextHolder.set(new ShareCallerContext(ctx.share.getTenantId(), ctx.share.getId(), null, hash));
            } catch (java.security.NoSuchAlgorithmException e) {
                CallerContextHolder.clear();
                throw new IllegalStateException("访客会话校验不可用", e);
            }
        }
        return ctx;
    }

    @Operation(summary = "获取 iframe 嵌入页")
    @GetMapping(value = "/{shareCode}/embed", produces = MediaType.TEXT_HTML_VALUE)
    public String getEmbedPage(
            @PathVariable String shareCode,
            HttpServletResponse response) {
        WorkflowShare share = shareService.validateAndGet(shareCode);
        List<String> allowedOrigins = parseAllowedOrigins(share.getAllowedOrigins());
        setSecurityHeaders(response, allowedOrigins);

        String appPath = "/app/" + URLEncoder.encode(shareCode, StandardCharsets.UTF_8)
                + "?embed=1";
        return """
                <!doctype html>
                <html lang="zh-CN">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Polaris Workflow</title>
                    <style>
                      html, body { margin: 0; min-height: 100%%; background: #f0f2f5; }
                      #polaris-app { display: block; width: 100%%; min-height: 600px; border: 0; }
                    </style>
                  </head>
                  <body>
                    <iframe id="polaris-app" src="%s" title="Polaris Workflow"></iframe>
                    <script>
                      const frame = document.getElementById('polaris-app');
                      const allowedOrigins = %s;
                      let parentOrigin = '';
                      try { parentOrigin = new URL(document.referrer).origin; } catch (_) {}
                      window.addEventListener('message', event => {
                        if (event.source !== frame.contentWindow || event.origin !== window.location.origin) return;
                        if (event.data?.type === 'polaris:resize') {
                          const height = Math.max(240, Math.ceil(Number(event.data.height) || 0));
                          if (height) frame.style.height = `${height}px`;
                        }
                        if (window.parent !== window
                            && (parentOrigin === window.location.origin || allowedOrigins.includes(parentOrigin))) {
                          window.parent.postMessage(event.data, parentOrigin);
                        }
                      });
                    </script>
                  </body>
                </html>
                """.formatted(appPath, JSON.toJSONString(allowedOrigins));
    }

    @Operation(summary = "获取分享清单")
    @GetMapping("/{shareCode}/manifest")
    public ResultData<WorkflowShareManifest> getManifest(
            @PathVariable String shareCode,
            HttpServletRequest request,
            HttpServletResponse response) {
        ShareSetup ctx = setupContext(shareCode);
        try {
            setSecurityHeaders(response, parseAllowedOrigins(ctx.share.getAllowedOrigins()));

            // 合并页面配置：分享覆盖 > 工作流默认 > form
            String finalPageType = Optional.ofNullable(ctx.share.getPageType())
                    .orElse(Optional.ofNullable(ctx.definition.defaultPageType())
                            .orElse(Optional.ofNullable(ctx.definition.recommendedPageType())
                                    .orElse("form")));
            if ("chat".equals(finalPageType)) visitorSession(request, response);
            Object pageConfig = mergePageConfig(
                    ctx.definition.sharePageConfigJson(), ctx.share.getPageConfigJson());

            return ResultData.ok(new WorkflowShareManifest(
                    ctx.share.getShareName(),
                    ctx.definition.workflowCode(),
                    finalPageType,
                    ctx.definition.inputSchema(),
                    pageConfig));
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Operation(summary = "启动工作流执行")
    @PostMapping("/{shareCode}/executions")
    public ResultData<WorkflowShareExecutionView> execute(
            @PathVariable String shareCode,
            HttpServletRequest servletRequest,
            @RequestBody(required = false) WorkflowShareChatRequest request) {
        ShareSetup ctx = setupContext(shareCode, servletRequest, WorkflowShareRateLimiter.RequestType.EXECUTION);
        try {
            WorkflowShareChatRequest value = request == null
                    ? new WorkflowShareChatRequest(null, null, null) : request;
            if (value.attachments() != null && !value.attachments().isEmpty()) requireChat(ctx);
            Object input = attachmentService.prepareInput(
                    ctx.share, visitorCookie(servletRequest), value.input(), value.attachments());
            WorkflowExecutionView execution = workflowFacade.startByCode(new WorkflowExecutionByCodeCommand(
                    ctx.definition.workflowCode(),
                    input,
                    value.environment(),
                    null));
            return ResultData.ok(toShareView(execution));
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Operation(summary = "上传分享聊天私有附件")
    @PostMapping("/{shareCode}/attachments")
    public ResultData<WorkflowShareAttachmentService.UploadedAttachment> uploadAttachment(
            @PathVariable String shareCode, @RequestParam("file") MultipartFile file,
            HttpServletRequest request, HttpServletResponse response) {
        ShareSetup ctx = setupContext(shareCode, WorkflowShareRateLimiter.RequestType.UPLOAD);
        try {
            requireChat(ctx);
            if (file.isEmpty() || file.getSize() > WorkflowShareAttachmentService.MAX_FILE_SIZE) {
                throw new ServiceException("附件不能为空，最大10MB");
            }
            long dailyLimit = Math.min(pageConfigLong(ctx, "maxDailyUploadSize", 100L * 1024 * 1024),
                    10L * 1024 * 1024 * 1024);
            if (!shareRateLimiter.tryConsumeUploadBytes(ctx.share.getId(), file.getSize(), dailyLimit)) {
                throw new ServiceException("今日上传总量已达到上限", 429);
            }
            return ResultData.ok(attachmentService.stage(ctx.share, visitorSession(request, response), file));
        } finally { CallerContextHolder.clear(); }
    }

    @Operation(summary = "读取分享聊天私有附件")
    @GetMapping("/{shareCode}/attachments/{token}")
    public ResponseEntity<Resource> getAttachment(
            @PathVariable String shareCode, @PathVariable String token, HttpServletRequest request) {
        ShareSetup ctx = setupContext(shareCode);
        try {
            requireChat(ctx);
            var attachment = attachmentService.owned(ctx.share, visitorCookie(request), token);
            boolean image = attachment.mediaType().startsWith("image/");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            (image ? ContentDisposition.inline() : ContentDisposition.attachment())
                                    .filename(attachment.name(), StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType(attachment.mediaType()))
                    .body(new FileSystemResource(attachmentService.path(attachment)));
        } finally { CallerContextHolder.clear(); }
    }

    @Operation(summary = "移除未发送的分享聊天附件")
    @DeleteMapping("/{shareCode}/attachments/{token}")
    public ResultData<Void> deleteAttachment(
            @PathVariable String shareCode, @PathVariable String token, HttpServletRequest request) {
        ShareSetup ctx = setupContext(shareCode);
        try {
            requireChat(ctx);
            attachmentService.remove(ctx.share, visitorCookie(request), token);
            return ResultData.ok();
        } finally { CallerContextHolder.clear(); }
    }

    private void requireChat(ShareSetup ctx) {
        String type = Optional.ofNullable(ctx.share.getPageType())
                .orElse(Optional.ofNullable(ctx.definition.defaultPageType())
                        .orElse(Optional.ofNullable(ctx.definition.recommendedPageType()).orElse("form")));
        if (!"chat".equals(type)) throw new ServiceException("该分享不支持聊天附件");
    }

    private String visitorCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (VISITOR_COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[a-f0-9]{32}")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String visitorSession(HttpServletRequest request, HttpServletResponse response) {
        String visitor = visitorCookie(request);
        if (visitor == null) {
            visitor = java.util.UUID.randomUUID().toString().replace("-", "");
            // HTTPS 分区 Cookie 可在跨站 iframe 中使用，并按顶层站点隔离访客。
            // 始终 Secure，避免依赖 TLS 终止代理的请求 scheme，也不允许公网明文传输。
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(VISITOR_COOKIE, visitor)
                    .httpOnly(true).secure(true).sameSite("None").partitioned(true).path("/")
                    .maxAge(java.time.Duration.ofHours(2)).build().toString());
        }
        return visitor;
    }

    @Operation(summary = "查询执行状态")
    @GetMapping("/{shareCode}/executions/{executionId}")
    public ResultData<WorkflowShareExecutionView> getExecution(
            @PathVariable String shareCode,
            @PathVariable String executionId, HttpServletRequest request) {
        setupContext(shareCode, request);
        try {
            return ResultData.ok(toShareView(workflowFacade.get(executionId)));
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Operation(summary = "获取执行事件")
    @GetMapping("/{shareCode}/executions/{executionId}/events")
    public ResultData<List<WorkflowExecutionEventView>> getEvents(
            @PathVariable String shareCode,
            @PathVariable String executionId,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "200") int limit) {
        setupContext(shareCode, request);
        try {
            return ResultData.ok(workflowFacade.listEvents(executionId, afterSequence, limit));
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Operation(summary = "SSE 订阅执行事件")
    @GetMapping(value = "/{shareCode}/executions/{executionId}/events/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable String shareCode,
            @PathVariable String executionId,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") long afterSequence) {
        setupContext(shareCode, request);
        try {
            return eventStreamFacade.subscribe(executionId, Math.max(0, afterSequence));
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Operation(summary = "取消执行")
    @PostMapping("/{shareCode}/executions/{executionId}/cancel")
    public ResultData<WorkflowShareExecutionView> cancelExecution(
            @PathVariable String shareCode,
            @PathVariable String executionId, HttpServletRequest request) {
        setupContext(shareCode, request);
        try {
            return ResultData.ok(toShareView(workflowFacade.cancel(executionId)));
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Operation(summary = "上传文件")
    @PostMapping("/{shareCode}/upload")
    public ResultData<Map<String, String>> upload(
            @PathVariable String shareCode,
            @RequestParam("file") MultipartFile file) {
        ShareSetup ctx = setupContext(shareCode, WorkflowShareRateLimiter.RequestType.UPLOAD);
        try {
            if (file.isEmpty()) {
                throw new ServiceException("上传文件不能为空");
            }
            long maxUploadSize = Math.min(
                    pageConfigLong(ctx, "maxUploadSize", 10L * 1024 * 1024),
                    50L * 1024 * 1024);
            if (file.getSize() > maxUploadSize) {
                throw new ServiceException("文件大小超过限制");
            }
            if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                throw new ServiceException("仅允许上传图片文件");
            }
            long dailyUploadLimit = Math.min(
                    pageConfigLong(ctx, "maxDailyUploadSize", 100L * 1024 * 1024),
                    10L * 1024 * 1024 * 1024);
            if (!shareRateLimiter.tryConsumeUploadBytes(
                    ctx.share.getId(), file.getSize(), dailyUploadLimit)) {
                throw new ServiceException("今日上传总量已达到上限", 429);
            }
            String subDir = "share/" + ctx.share.getTenantId() + "/" + ctx.share.getId();
            String filePath = FileUploadUtils.upload(
                    PolarisConfig.getUploadPath() + "/" + subDir,
                    file,
                    MimeTypeUtils.IMAGE_EXTENSION,
                    true);
            return ResultData.ok(Map.of("url", filePath));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("工作流分享上传失败，shareId={}", ctx.share.getId(), e);
            throw new ServiceException("文件上传失败，请稍后重试或联系管理员");
        } finally {
            CallerContextHolder.clear();
        }
    }

    private Object mergePageConfig(String defaults, String overrides) {
        com.alibaba.fastjson2.JSONObject merged = new com.alibaba.fastjson2.JSONObject();
        if (defaults != null && !defaults.isBlank()) {
            merged.putAll(JSON.parseObject(defaults));
        }
        if (overrides != null && !overrides.isBlank()) {
            merged.putAll(JSON.parseObject(overrides));
        }
        return merged;
    }

    private long pageConfigLong(ShareSetup ctx, String key, long defaultValue) {
        Object config = mergePageConfig(
                ctx.definition.sharePageConfigJson(), ctx.share.getPageConfigJson());
        if (!(config instanceof com.alibaba.fastjson2.JSONObject json)) return defaultValue;
        Long value = json.getLong(key);
        return value == null || value <= 0 ? defaultValue : value;
    }

    private List<String> parseAllowedOrigins(String allowedOriginsJson) {
        return allowedOriginsJson == null || allowedOriginsJson.isBlank()
                ? List.of() : JSON.parseArray(allowedOriginsJson, String.class).stream()
                .filter(WorkflowShareOriginPolicy::isSafeOrigin)
                .distinct()
                .toList();
    }

    private void setSecurityHeaders(HttpServletResponse response, List<String> origins) {
        if (origins.isEmpty()) {
            response.setHeader("Content-Security-Policy", "frame-ancestors 'self'");
            response.setHeader("X-Frame-Options", "SAMEORIGIN");
        } else {
            response.setHeader("Content-Security-Policy",
                    "frame-ancestors 'self' " + String.join(" ", origins));
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
    }

    private WorkflowShareExecutionView toShareView(WorkflowExecutionView execution) {
        return new WorkflowShareExecutionView(
                execution.executionId(),
                execution.status(),
                execution.outputJson(),
                sanitizeError(execution.errorMessage()),
                execution.eventSequence(),
                execution.cancelRequested(),
                execution.createTime(),
                execution.startTime(),
                execution.finishTime());
    }

    private String sanitizeError(String message) {
        if (message == null || message.isBlank()) return message;
        String sanitized = message.lines().findFirst().orElse("执行失败")
                .replaceAll("(?i)(jdbc:[^\\s]+|https?://[^\\s]+)", "[internal]")
                .replaceAll("(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?", "[internal]")
                .replaceAll("(?i)(select|insert|update|delete)\\s+[^;]+", "[internal]");
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    /** 内部辅助 */
    private record ShareSetup(
            WorkflowShare share,
            WorkflowShareDefinitionView definition) {}
}
