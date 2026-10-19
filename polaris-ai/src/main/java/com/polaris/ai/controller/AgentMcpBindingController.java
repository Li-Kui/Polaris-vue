package com.polaris.ai.controller;

import com.polaris.ai.agent.collaboration.AgentBindingService;
import com.polaris.ai.agent.collaboration.BoundedAgentContracts.Failure;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

/** 仅租户管理员的本地绑定与预检，不暴露运行或重放接口。 */
@RestController
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RequestMapping("/ai/agent/{agentId}/mcp-bindings")
public class AgentMcpBindingController extends BaseController {
    private final AgentBindingService bindings;
    public AgentMcpBindingController(AgentBindingService bindings) { this.bindings = bindings; }
    @GetMapping public ResultData<AgentBindingService.View> get(@PathVariable Long agentId) {
        return safe(() -> bindings.view(agentId));
    }
    @PutMapping public ResultData<AgentBindingService.View> save(@PathVariable Long agentId, @RequestBody AgentBindingService.Save input) {
        return safe(() -> bindings.save(agentId, input));
    }
    @PostMapping("/preflight") public ResultData<AgentBindingService.PreflightResult> preflight(
            @PathVariable Long agentId, @RequestBody AgentBindingService.Preflight input) {
        return safe(() -> bindings.preflight(agentId, input));
    }
    private <T> ResultData<T> safe(Supplier<T> operation) {
        try { return ok(operation.get()); }
        catch (Failure failure) {
            String message = switch (failure.code()) {
                case ACCESS_DENIED -> "智能体不可用或当前主体无权配置协作工具";
                case SNAPSHOT_CHANGED -> "配置、审批或版本已变化，请刷新后重新校验";
                case UNSUPPORTED_SCHEMA -> "工具 Schema 暂不支持有界协作，请选择其他工具";
                case ARGUMENTS_REJECTED -> "固定参数或外发查询参数不符合工具 Schema";
                case INVALID_REQUEST -> "协作工具配置不完整或超过数量限制";
                default -> "协作工具配置校验失败";
            };
            throw new ServiceException(message + "（" + failure.code().name() + "）",
                    failure.code() == com.polaris.ai.agent.collaboration.BoundedAgentContracts.Code.ACCESS_DENIED ? 403 : 409);
        }
    }
}
