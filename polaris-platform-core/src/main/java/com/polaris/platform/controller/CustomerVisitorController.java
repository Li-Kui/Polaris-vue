package com.polaris.platform.controller;

import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.service.CustomerServiceAvatarAssetService;
import com.polaris.platform.service.CustomerServiceConversationService;
import com.polaris.platform.service.CustomerVisitorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 独立访客接口；短期凭证放在请求头，不接受租户、管理员或业务身份参数。 */
@RestController
@RequestMapping("/platform/runtime/customer-visitors/{code}")
public class CustomerVisitorController {
    private final CustomerVisitorService visitors;
    private final CustomerServiceConversationService conversations;
    private final CustomerServiceAvatarAssetService assets;
    private final CustomerServiceRequestReader reader;
    private final ObjectMapper json;
    public CustomerVisitorController(CustomerVisitorService visitors, CustomerServiceConversationService conversations,
            CustomerServiceAvatarAssetService assets, ObjectMapper json) {
        this.visitors = visitors; this.conversations = conversations; this.assets = assets;
        this.reader = new CustomerServiceRequestReader(json); this.json = json;
    }
    @ModelAttribute
    public void guard(HttpServletRequest request, HttpServletResponse response) {
        guardRequest(request, response);
    }
    static void guardRequest(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!request.getParameterMap().isEmpty()) throw new ServiceException("INVALID_INPUT: 接待接口不接受查询参数", 400);
        String origin = request.getHeader("Origin");
        int port = request.getServerPort();
        String self = request.getScheme() + "://" + request.getServerName()
                + ((port == 80 && "http".equals(request.getScheme()) || port == 443 && "https".equals(request.getScheme())) ? "" : ":" + port);
        if (origin != null && !origin.equals(self) || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
            throw new ServiceException("ACCESS_DENIED: 请通过接待页面咨询", 403);
        }
    }
    @GetMapping("/entry")
    public ResultData<?> entry(@PathVariable String code, HttpServletRequest request) {
        return ResultData.ok(visitors.entry(code, request.getRemoteAddr()));
    }
    @PostMapping("/sessions")
    public ResultData<?> create(@PathVariable String code, HttpServletRequest request) {
        reader.read(request, PlatformConsoleCustomerServiceController.EmptyRequest.class);
        return ResultData.ok(visitors.create(code, request.getRemoteAddr()));
    }
    @GetMapping("/sessions/{id}")
    public ResultData<?> get(@PathVariable String code, @PathVariable String id, HttpServletRequest request) {
        return call(code, id, request, false, () -> conversations.get(id));
    }
    @PostMapping("/sessions/{id}/messages")
    public ResultData<?> send(@PathVariable String code, @PathVariable String id, HttpServletRequest request) {
        var command = reader.read(request, PlatformConsoleCustomerServiceController.MessageRequest.class);
        return call(code, id, request, true, () -> conversations.send(id, command.requestId(), command.text()));
    }
    /** 与管理员接待复用已校验的 NDJSON 分段，不向访客透传供应商原始事件。 */
    @PostMapping(value = "/sessions/{id}/messages/stream", produces = "application/x-ndjson")
    public void stream(@PathVariable String code, @PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        var command = reader.read(request, PlatformConsoleCustomerServiceController.MessageRequest.class);
        visitors.session(code, id, request.getHeader("Customer-Session-Token"), true, request.getRemoteAddr(), () -> {
            response.setContentType("application/x-ndjson"); response.setCharacterEncoding("UTF-8");
            response.setHeader("X-Accel-Buffering", "no");
            java.util.function.Consumer<CustomerServiceConversationService.AnswerEvent> sink = event -> {
                try {
                    response.getWriter().write(json.writeValueAsString(event) + "\n");
                    response.getWriter().flush(); response.flushBuffer();
                    if (response.getWriter().checkError()) throw new java.io.IOException();
                } catch (java.io.IOException disconnected) { throw new ServiceException("ANSWER_INVALID: 回答连接已断开",409); }
            };
            var turn = conversations.sendStream(id, command.requestId(), command.text(), sink);
            sink.accept(new CustomerServiceConversationService.AnswerEvent("completed",turn.id(),turn.requestId(),null,null,turn));
            return null;
        });
    }

    @PostMapping("/sessions/{id}/interrupt")
    public ResultData<?> interrupt(@PathVariable String code, @PathVariable String id, HttpServletRequest request) {
        var command = reader.read(request, PlatformConsoleCustomerServiceController.InterruptRequest.class);
        return call(code, id, request, false, () -> { conversations.interrupt(id, command.turnId()); return null; });
    }
    @PostMapping("/sessions/{id}/close")
    public ResultData<?> close(@PathVariable String code, @PathVariable String id, HttpServletRequest request) {
        reader.read(request, PlatformConsoleCustomerServiceController.EmptyRequest.class);
        return call(code, id, request, false, () -> { conversations.close(id); return null; });
    }
    @GetMapping("/sessions/{id}/messages/{requestId}/sources/{citationId}")
    public ResultData<?> source(@PathVariable String code, @PathVariable String id, @PathVariable String requestId,
            @PathVariable String citationId, HttpServletRequest request) {
        return call(code, id, request, false, () -> conversations.source(id, requestId, citationId));
    }
    @GetMapping("/sessions/{id}/avatar-assets/{assetId}")
    public ResponseEntity<byte[]> asset(@PathVariable String code, @PathVariable String id, @PathVariable String assetId, HttpServletRequest request) {
        var data = visitors.session(code, id, request.getHeader("Customer-Session-Token"), false, request.getRemoteAddr(),
                () -> assets.readVisitor(assetId, id, conversations));
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Content-Security-Policy", "default-src 'none'; sandbox")
                .contentType(MediaType.parseMediaType(data.contentType())).body(data.bytes());
    }
    private ResultData<?> call(String code, String id, HttpServletRequest request, boolean message, java.util.function.Supplier<?> action) {
        return ResultData.ok(visitors.session(code, id, request.getHeader("Customer-Session-Token"), message, request.getRemoteAddr(), action));
    }
}
