package com.polaris.ai.controller;

import com.polaris.ai.modelcenter.discovery.ProviderModelDiscoveryService;
import com.polaris.ai.modelcenter.discovery.RemoteModelInfo;
import com.polaris.ai.modelcenter.dto.ProviderConnectionCreateRequest;
import com.polaris.ai.modelcenter.dto.ProviderConnectionQuery;
import com.polaris.ai.modelcenter.dto.ProviderConnectionStatusRequest;
import com.polaris.ai.modelcenter.dto.ProviderConnectionUpdateRequest;
import com.polaris.ai.modelcenter.service.IAiProviderConnectionService;
import com.polaris.ai.modelcenter.vo.ProviderConnectionVO;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.annotation.RateLimiter;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import com.polaris.common.enums.LimitType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Provider Connection 管理控制器。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "AI Provider Connection 管理")
@RestController
@RequestMapping("/ai/provider-connection")
public class AiProviderConnectionController extends BaseController {

    private final IAiProviderConnectionService connectionService;
    private final ProviderModelDiscoveryService discoveryService;

    public AiProviderConnectionController(
            IAiProviderConnectionService connectionService,
            ProviderModelDiscoveryService discoveryService) {
        this.connectionService = connectionService;
        this.discoveryService = discoveryService;
    }

    @Operation(summary = "查询 Provider Connection 列表")
    @GetMapping("/list")
    public ResultData<Page<ProviderConnectionVO>> list(ProviderConnectionQuery query) {
        startPage();
        List<ProviderConnectionVO> list = connectionService.list(query);
        return ResultData.ok(Page.of(com.github.pagehelper.PageInfo.of(list)));
    }

    @Operation(summary = "获取 Provider Connection 详情")
    @GetMapping("/{id}")
    public ResultData<ProviderConnectionVO> get(@PathVariable Long id) {
        return ResultData.ok(connectionService.get(id));
    }

    @Operation(summary = "发现 Provider Connection 的远程模型")
    @RateLimiter(key = "model_center_discovery:", time = 60, count = 20,
            limitType = LimitType.IP)
    @GetMapping("/{id}/models")
    public ResultData<List<RemoteModelInfo>> listRemoteModels(
            @PathVariable Long id) {
        return ResultData.ok(discoveryService.listModels(id));
    }

    @Operation(summary = "新增 Provider Connection")
    @Log(title = "Provider Connection 管理", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public ResultData<Long> create(
            @Validated @RequestBody ProviderConnectionCreateRequest request) {
        return ResultData.ok(connectionService.create(request));
    }

    @Operation(summary = "修改 Provider Connection")
    @Log(title = "Provider Connection 管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{id}")
    public ResultData<Void> update(
            @PathVariable Long id,
            @Validated @RequestBody ProviderConnectionUpdateRequest request) {
        connectionService.update(id, request);
        return ResultData.ok();
    }

    @Operation(summary = "修改 Provider Connection 状态")
    @Log(title = "Provider Connection 管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{id}/status")
    public ResultData<Void> changeStatus(
            @PathVariable Long id,
            @Validated @RequestBody ProviderConnectionStatusRequest request) {
        connectionService.changeStatus(id, request.status(), request.expectedRevision());
        return ResultData.ok();
    }

    @Operation(summary = "删除 Provider Connection")
    @Log(title = "Provider Connection 管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData<Void> delete(@PathVariable Long id) {
        connectionService.delete(id);
        return ResultData.ok();
    }
}
