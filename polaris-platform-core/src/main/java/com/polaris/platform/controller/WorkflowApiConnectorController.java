package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.enums.BusinessType;
import com.polaris.platform.dto.ApiConnectorResponse;
import com.polaris.platform.dto.ApiConnectorSaveRequest;
import com.polaris.platform.service.IPlatformApiConnectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端工作流使用的共享 API 连接创建入口。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "管理端工作流 API 连接")
@RestController
@RequestMapping("/ai/workflow/connectors")
public class WorkflowApiConnectorController {

    private final IPlatformApiConnectorService connectorService;

    public WorkflowApiConnectorController(IPlatformApiConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Operation(summary = "创建管理端工作流共享 API 连接")
    @Log(title = "工作流共享 API 连接", businessType = BusinessType.INSERT)
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping
    public ResultData<ApiConnectorResponse> add(@RequestBody ApiConnectorSaveRequest request) {
        CallerContext caller = CallerContextHolder.require();
        if (caller.isPlatformMode()) {
            throw new IllegalStateException("中台模式请使用租户 API 连接接口");
        }
        return ResultData.ok(ApiConnectorResponse.from(
                connectorService.insertSharedConnector(request, caller.getUsername())));
    }
}
