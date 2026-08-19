package com.polaris.ai.controller;

import com.polaris.ai.safety.dto.ModerationPolicyUpdateRequest;
import com.polaris.ai.safety.dto.ModerationTestRequest;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationPolicyAdminService;
import com.polaris.ai.safety.vo.ModerationTestView;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.enums.BusinessType;
import com.polaris.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI敏感内容安全策略管理")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/moderation")
public class AiModerationPolicyController extends BaseController {

    @Autowired
    private IModerationPolicyAdminService adminService;

    public void setAdminService(IModerationPolicyAdminService adminService) {
        this.adminService = adminService;
    }


    @Operation(summary = "获取所有场景安全检测策略")
    @PreAuthorize("@ss.hasPermi('ai:moderation:policy:list')")
    @GetMapping("/policies")
    public ResultData<List<ModerationPolicy>> list() {
        return ok(adminService.listPolicies());
    }

    @Operation(summary = "更新指定场景安全检测策略")
    @PreAuthorize("@ss.hasPermi('ai:moderation:policy:edit')")
    @Log(title = "AI敏感内容安全策略", businessType = BusinessType.UPDATE)
    @PutMapping("/policies/{scene}")
    public ResultData<ModerationPolicy> update(
            @PathVariable String scene,
            @Validated @RequestBody ModerationPolicyUpdateRequest request) {
        ModerationScene moderationScene = ModerationScene.valueOf(scene);
        String username;
        try {
            username = SecurityUtils.getUsername();
        } catch (Exception e) {
            username = "admin";
        }
        return ok(adminService.updatePolicy(moderationScene, request, username));
    }

    @Operation(summary = "安全检测测试句评估")
    @PreAuthorize("@ss.hasPermi('ai:moderation:test')")
    @PostMapping("/test")
    public ResultData<ModerationTestView> testSentence(
            @Validated @RequestBody ModerationTestRequest request) {
        return ok(adminService.testSentence(request));
    }
}
