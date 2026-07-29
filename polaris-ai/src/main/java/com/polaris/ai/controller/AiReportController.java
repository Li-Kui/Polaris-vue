package com.polaris.ai.controller;

import com.polaris.ai.domain.AiReport;
import com.polaris.ai.service.IAiReportService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 分析报告管理控制器
 *
 * @author polaris
 */
@Slf4j
@Tag(name = "AI分析报告管理")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/report")
public class AiReportController extends BaseController {

    @Autowired
    private IAiReportService reportService;

    /**
     * 保存分析报告到数据库
     * POST /ai/report
     */
    @Operation(summary = "归档保存分析报告")
    @Log(title = "报告管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData<Boolean> saveReport(@RequestBody AiReport report) {
        return ok(reportService.saveReport(report));
    }

    /**
     * 修改更新分析报告 (保存美化结构体字段)
     * PUT /ai/report
     */
    @Operation(summary = "修改更新分析报告")
    @Log(title = "报告管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData<Boolean> updateReport(@RequestBody AiReport report) {
        return ok(reportService.updateReport(report));
    }

    /**
     * 分页查询我的报告列表
     * GET /ai/report/list
     */
    @Operation(summary = "条件分页查询报告列表")
    @GetMapping("/list")
    public ResultData<Page<AiReport>> list(AiReport report) {
        startPage();
        List<AiReport> list = reportService.selectReportList(report);
        return ok(getDataPage(list));
    }

    /**
     * 获取报告详情
     * GET /ai/report/{id}
     */
    @Operation(summary = "获取报告详情")
    @GetMapping("/{id}")
    public ResultData<AiReport> getInfo(@PathVariable Long id) {
        return ok(reportService.getReportById(id));
    }

    /**
     * 删除报告
     * DELETE /ai/report/{id}
     */
    @Operation(summary = "删除报告")
    @Log(title = "报告管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData<Boolean> remove(@PathVariable Long id) {
        return ok(reportService.removeReport(id));
    }

    /**
     * 报告中心创建或复用异步美化任务
     * POST /ai/report/{id}/refine
     */
    @Operation(summary = "创建或复用报告中心 AI 美化任务")
    @PostMapping("/{id}/refine")
    public ResultData<java.util.Map<String, Object>> refineReportById(@PathVariable Long id) {
        return ok(reportService.startRefineReport(id));
    }

    /**
     * 查询报告中心美化任务状态
     * GET /ai/report/{id}/refine-status
     */
    @Operation(summary = "查询报告中心 AI 美化状态")
    @GetMapping("/{id}/refine-status")
    public ResultData<java.util.Map<String, Object>> refineReportStatus(@PathVariable Long id) {
        return ok(reportService.getRefineStatus(id));
    }

    /**
     * 调用 AI 智能体深度重塑美化报告（兼容现有调用方）
     * POST /ai/report/refine
     */
    @Operation(summary = "AI 智能美化重塑报告")
    @PostMapping("/refine")
    public ResultData<Object> refineReport(@RequestBody java.util.Map<String, String> body) {
        String content = body != null ? body.get("content") : "";
        return ok(reportService.refineReport(content));
    }

    /**
     * 调用 AI 智能体深度重塑美化报告 (SSE 流式推送)
     * POST /ai/report/refine-stream
     */
    @Operation(summary = "AI 智能美化重塑报告-SSE流式")
    @PostMapping(value = "/refine-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter refineReportStream(@RequestBody java.util.Map<String, String> body) {
        String content = body != null ? body.get("content") : "";
        return reportService.refineReportStream(content);
    }
}
