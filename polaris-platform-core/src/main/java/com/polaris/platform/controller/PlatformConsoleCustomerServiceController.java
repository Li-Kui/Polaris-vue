package com.polaris.platform.controller;

import com.polaris.ai.customer.application.CustomerServiceCapability;
import com.polaris.ai.customer.application.CustomerServiceConfig;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.service.CustomerServiceCapabilityService;
import com.polaris.platform.service.CustomerServiceConfigurationService;
import com.polaris.platform.service.CustomerServiceConversationService;
import com.polaris.platform.service.CustomerServiceOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


/** 租户管理员客服配置及文字预览，不属于公开分享运行接口。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台数字人客服")
@RestController
@RequestMapping("/platform/console/customer-services")
public class PlatformConsoleCustomerServiceController {
    private final CustomerServiceConfigurationService configurations;
    private final CustomerServiceConversationService conversations;
    private final CustomerServiceRequestReader reader;
    private final ObjectMapper json;
    private final CustomerServiceCapabilityService capabilities;
    private final CustomerServiceOperationService operations;

    public PlatformConsoleCustomerServiceController(CustomerServiceConfigurationService configurations,
            CustomerServiceConversationService conversations, ObjectMapper json, CustomerServiceCapabilityService capabilities,
            CustomerServiceOperationService operations) {
        this.configurations = configurations;
        this.conversations = conversations;
        this.capabilities = capabilities;
        this.operations = operations;
        this.reader = new CustomerServiceRequestReader(json);
        this.json = json;
    }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping
    @Operation(summary = "查询当前租户的客服")
    public ResultData<?> list() { return ResultData.ok(configurations.list()); }

    @GetMapping("/capability-workflows")
    public ResultData<?> capabilityWorkflows() { return ResultData.ok(capabilities.workflows()); }

    @GetMapping("/capability-workflows/{definitionId}/versions")
    public ResultData<?> capabilityVersions(@PathVariable Long definitionId) {
        return ResultData.ok(capabilities.versions(definitionId));
    }

    @GetMapping("/capability-workflows/{definitionId}/versions/{versionId}/contract")
    public ResultData<?> capabilityContract(@PathVariable Long definitionId, @PathVariable String versionId) {
        return ResultData.ok(capabilities.contract(definitionId, versionId));
    }

    @PostMapping("/capability-check")
    public ResultData<?> checkCapability(HttpServletRequest request) {
        return ResultData.ok(capabilities.check(read(request, CustomerServiceCapability.class)));
    }

    @GetMapping("/{id}")
    public ResultData<?> get(@PathVariable Long id) { return ResultData.ok(configurations.get(id)); }

    @PostMapping
    public ResultData<?> create(HttpServletRequest request) {
        return ResultData.ok(configurations.create(read(request, CustomerServiceConfig.class)));
    }

    @PutMapping("/{id}")
    public ResultData<?> update(@PathVariable Long id, HttpServletRequest request) {
        var command = read(request, UpdateRequest.class);
        if (command.config() == null) throw invalid();
        return ResultData.ok(configurations.update(id, command.revision(), command.config()));
    }

    @PostMapping("/{id}/publish")
    public ResultData<?> publish(@PathVariable Long id, HttpServletRequest request) {
        return ResultData.ok(configurations.publish(id, read(request, RevisionRequest.class).revision()));
    }

    @PutMapping("/{id}/status")
    public ResultData<?> status(@PathVariable Long id, HttpServletRequest request) {
        var command = read(request, StatusRequest.class);
        return ResultData.ok(configurations.setStatus(id, command.revision(), command.status()));
    }

    @PostMapping("/{id}/sessions")
    public ResultData<?> createSession(@PathVariable Long id, HttpServletRequest request) {
        var body = read(request, JsonNode.class);
        if (!body.isObject() || body.size() != 0) throw invalid();
        return ResultData.ok(conversations.create(id));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResultData<?> session(@PathVariable String sessionId) { return ResultData.ok(conversations.get(sessionId)); }

    @GetMapping("/sessions/{sessionId}/capabilities")
    public ResultData<?> sessionCapabilities(@PathVariable String sessionId) { return ResultData.ok(operations.capabilities(sessionId)); }

    @GetMapping("/sessions/{sessionId}/operations")
    public ResultData<?> operations(@PathVariable String sessionId) { return ResultData.ok(operations.list(sessionId)); }

    @PostMapping("/sessions/{sessionId}/operations")
    public ResultData<?> startOperation(@PathVariable String sessionId, HttpServletRequest request) {
        var command = read(request, OperationRequest.class);
        return ResultData.ok(operations.start(sessionId, command.requestId(), command.capabilityCode(), command.input()));
    }

    @GetMapping("/sessions/{sessionId}/operations/{requestId}")
    public ResultData<?> operation(@PathVariable String sessionId, @PathVariable String requestId) {
        return ResultData.ok(operations.get(sessionId, requestId));
    }

    @PostMapping("/sessions/{sessionId}/operations/{requestId}/cancel")
    public ResultData<?> cancelOperation(@PathVariable String sessionId, @PathVariable String requestId) {
        operations.cancel(sessionId, requestId);
        return ResultData.ok();
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResultData<?> send(@PathVariable String sessionId, HttpServletRequest request) {
        var command = read(request, MessageRequest.class);
        return ResultData.ok(conversations.send(sessionId, command.requestId(), command.text()));
    }

    /** 同一 Servlet 请求线程保留调用方上下文；每个已校验事件立即 flush，不输出供应商原始流。 */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = "application/x-ndjson")
    public void stream(@PathVariable String sessionId, HttpServletRequest request, HttpServletResponse response) {
        var command = read(request, MessageRequest.class);
        response.setContentType("application/x-ndjson"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("X-Content-Type-Options", "nosniff");
        java.util.function.Consumer<CustomerServiceConversationService.AnswerEvent> sink = event -> {
            try {
                response.getWriter().write(json.writeValueAsString(event) + "\n");
                response.getWriter().flush(); response.flushBuffer();
                if (response.getWriter().checkError()) throw new java.io.IOException();
            } catch (java.io.IOException disconnected) { throw new ServiceException("ANSWER_INVALID: 回答连接已断开", 409); }
        };
        var turn = conversations.sendStream(sessionId, command.requestId(), command.text(), sink);
        sink.accept(new CustomerServiceConversationService.AnswerEvent("completed", turn.id(), turn.requestId(), null, null, turn));
    }

    @PostMapping("/sessions/{sessionId}/interrupt")
    public ResultData<?> interrupt(@PathVariable String sessionId, HttpServletRequest request) {
        conversations.interrupt(sessionId, read(request, InterruptRequest.class).turnId());
        return ResultData.ok();
    }

    @PostMapping("/sessions/{sessionId}/close")
    public ResultData<?> close(@PathVariable String sessionId) {
        conversations.close(sessionId);
        return ResultData.ok();
    }

    @GetMapping("/sessions/{sessionId}/messages/{requestId}/sources/{citationId}")
    public ResultData<?> source(@PathVariable String sessionId, @PathVariable String requestId,
                                @PathVariable String citationId) {
        return ResultData.ok(conversations.source(sessionId, requestId, citationId));
    }

    @PostMapping("/sessions/{sessionId}/messages/{requestId}/run")
    public ResultData<?> runIntent(@PathVariable String sessionId, @PathVariable String requestId, HttpServletRequest request) {
        read(request, EmptyRequest.class);
        return ResultData.ok(conversations.run(sessionId, requestId));
    }

    private <T> T read(HttpServletRequest request, Class<T> type) { return reader.read(request, type); }

    private ServiceException invalid() { return new ServiceException("INVALID_INPUT: 请求字段、格式或长度无效", 400); }

    public record UpdateRequest(Long revision, CustomerServiceConfig config) { }
    public record RevisionRequest(Long revision) { }
    public record StatusRequest(Long revision, String status) { }
    public record MessageRequest(String requestId, String text) { }
    public record InterruptRequest(String turnId) { }
    public record OperationRequest(String requestId, String capabilityCode, JsonNode input) { }
    public record EmptyRequest() { }
}
