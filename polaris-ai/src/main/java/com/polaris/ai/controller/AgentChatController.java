package com.polaris.ai.controller;

import com.polaris.ai.agent.collaboration.*;
import com.polaris.ai.agent.collaboration.AgentChatContracts.Input;
import com.polaris.ai.agent.collaboration.AgentChatContracts.PreparedView;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

/** 不记录输入正文；执行必须消费当前确认，重复请求只返回已有元数据。 */
@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/chat/collaboration")
public class AgentChatController {
    private final AgentChatPreparationService service;
    private final AgentChatExecutionService execution;
    private final AgentChatCitationService citations;
    private final AgentChatSourcesService sources;
    public AgentChatController(AgentChatPreparationService service,AgentChatExecutionService execution,AgentChatCitationService citations,AgentChatSourcesService sources) { this.service = service;this.execution=execution;this.citations=citations;this.sources=sources; }
    @PostMapping("/prepare") public ResultData<PreparedView> prepare(@RequestBody Input input) { return safe(() -> service.prepare(input)); }
    @GetMapping("/requests/{requestId}") public ResultData<PreparedView> get(@PathVariable String requestId) { return safe(() -> service.get(requestId)); }
    @GetMapping("/runs/{runId}") public ResultData<PreparedView> run(@PathVariable String runId) {return safe(()->service.forRun(runId));}
    @GetMapping("/conversations/{conversationId}/messages/{messageId}/sources")
    public ResultData<AgentChatSourcesService.View> sources(@PathVariable Long conversationId,@PathVariable Long messageId) {return safe(()->sources.get(conversationId,messageId));}
    @GetMapping("/conversations/{conversationId}/messages/{messageId}/history-citations/{originId}/{citationId}")
    public ResultData<com.polaris.ai.knowledge.ChatKnowledgeEvidenceService.Preview> historical(@PathVariable Long conversationId,
            @PathVariable Long messageId,@PathVariable Long originId,@PathVariable String citationId) {
        return safe(()->citations.preview(conversationId,messageId,originId,citationId));
    }
    @PostMapping("/requests/{requestId}/cancel") public ResultData<PreparedView> cancel(@PathVariable String requestId) { return safe(() -> service.cancel(requestId)); }
    @PostMapping(value="/stream",produces="text/event-stream;charset=UTF-8")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@RequestBody AgentChatExecutionService.Start start) {
        var admitted=guarded(()->execution.admit(start));var emitter=new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(180000L);
        java.util.function.BiConsumer<String,Object> events=(name,value)-> {
            try {emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name(name).data(value));}
            catch(java.io.IOException disconnected) {throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.CANCELLED);}
        };
        if(admitted.existing()!=null) {events.accept("existing",admitted.existing());emitter.complete();return emitter;}
        var session=admitted.session();
        Runnable cancel=()-> {try {session.cancel();}catch(RuntimeException unavailable) { /* 本机取消已标记，原租约不能被重放。 */ }};
        emitter.onCompletion(cancel);emitter.onTimeout(cancel);emitter.onError(error->cancel.run());
        guarded(()-> {session.start(events,emitter::complete);return true;});return emitter;
    }
    private <T> ResultData<T> safe(Supplier<T> operation) {
        return ResultData.ok(guarded(operation));
    }
    private <T>T guarded(Supplier<T> operation) {
        try {return operation.get();}
        catch (ServiceException safe) { throw safe; }
        catch (BoundedAgentContracts.Failure failure) {
            throw new ServiceException("协作资源不可用，请核对权限、配置及版本（" + failure.code().name() + "）",
                    failure.code() == BoundedAgentContracts.Code.ACCESS_DENIED ? 403 : 409);
        }
        catch (RuntimeException unavailable) { throw new ServiceException("协作请求暂不可用，请确认 SQL 11 已加载或稍后重试", 503); }
    }
}
