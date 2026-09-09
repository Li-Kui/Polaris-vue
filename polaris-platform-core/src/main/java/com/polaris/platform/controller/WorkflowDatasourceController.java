package com.polaris.platform.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.enums.BusinessType;
import com.polaris.platform.dto.DatasourceQueryRequest;
import com.polaris.platform.dto.DatasourceResponse;
import com.polaris.platform.dto.DatasourceSaveRequest;
import com.polaris.platform.service.IPlatformDatasourceService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 工作流数据库连接创建与只读查询预览入口。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "工作流数据库连接")
@RestController
@RequestMapping("/ai/workflow/datasources")
public class WorkflowDatasourceController {

    private final IPlatformDatasourceService datasourceService;

    public WorkflowDatasourceController(IPlatformDatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    @Operation(summary = "创建管理端工作流共享数据库连接")
    @Log(title = "工作流共享数据库连接", businessType = BusinessType.INSERT)
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping
    public ResultData<DatasourceResponse> add(@RequestBody DatasourceSaveRequest request) {
        CallerContext caller = CallerContextHolder.require();
        if (caller.isPlatformMode()) {
            throw new IllegalStateException("中台模式请使用租户数据库连接接口");
        }
        return ResultData.ok(DatasourceResponse.from(
                datasourceService.insertSharedDatasource(request, caller.getUsername())));
    }

    @Operation(summary = "预览工作流数据库只读查询")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping("/{id}/query")
    public ResultData<List<Map<String, Object>>> query(
            @PathVariable Long id, @RequestBody DatasourceQueryRequest request) {
        CallerContext caller = CallerContextHolder.require();
        Long tenantId = caller.isPlatformMode() ? PlatformTenantGuard.requireTenantId() : null;
        return ResultData.ok(datasourceService.executeWorkflowDatasourceQuery(
                tenantId, id, request.getSql(), request.getParameters(), request.getMaxRows(),
                request.getQueryTimeoutSeconds()));
    }
}
