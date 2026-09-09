package com.polaris.ai.controller;

import com.polaris.ai.safety.service.IModerationStatisticsService;
import com.polaris.ai.safety.service.ModerationCleanupTask;
import com.polaris.ai.safety.vo.ModerationSummaryView;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI敏感内容安全统计与告警")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/moderation")
public class AiModerationStatisticsController extends BaseController {

    @Autowired
    private IModerationStatisticsService statisticsService;

    @Autowired
    private ModerationCleanupTask cleanupTask;

    public void setStatisticsService(IModerationStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    public void setCleanupTask(ModerationCleanupTask cleanupTask) {
        this.cleanupTask = cleanupTask;
    }

    @Operation(summary = "获取安全检测统计汇总与趋势")
    @PreAuthorize("@ss.hasPermi('ai:moderation:statistics:list')")
    @GetMapping("/statistics/summary")
    public ResultData<ModerationSummaryView> getSummary(@RequestParam(required = false, defaultValue = "7") Integer days) {
        return ok(statisticsService.getSummary(days));
    }

    @Operation(summary = "手动触发敏感内容过期清理")
    @PreAuthorize("@ss.hasPermi('ai:moderation:statistics:list')")
    @Log(title = "AI敏感内容清理", businessType = BusinessType.CLEAN)
    @PostMapping("/cleanup/trigger")
    public ResultData<Void> triggerCleanup() {
        cleanupTask.run();
        return ok();
    }
}
