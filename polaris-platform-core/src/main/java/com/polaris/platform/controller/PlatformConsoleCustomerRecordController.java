package com.polaris.platform.controller;

import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.service.CustomerServiceRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** 本人接待记录专用入口，不提供音频、任务运行、重试或会话恢复。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/platform/console/customer-services/{appId}/records")
public class PlatformConsoleCustomerRecordController {
    private final CustomerServiceRecordService records;
    public PlatformConsoleCustomerRecordController(CustomerServiceRecordService records) { this.records = records; }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping
    public ResultData<?> list(@PathVariable Long appId, HttpServletRequest request) {
        parameters(request, Set.of("before"));
        return ResultData.ok(records.list(appId, request.getParameter("before")));
    }

    @GetMapping("/{sessionId}")
    public ResultData<?> detail(@PathVariable Long appId, @PathVariable String sessionId, HttpServletRequest request) {
        parameters(request, Set.of());
        return ResultData.ok(records.detail(appId, sessionId));
    }

    private void parameters(HttpServletRequest request, Set<String> allowed) {
        if (request.getParameterMap().entrySet().stream().anyMatch(item -> !allowed.contains(item.getKey()) || item.getValue().length != 1)) {
            throw new ServiceException("INVALID_INPUT: 接待记录查询参数无效", 400);
        }
    }
}
