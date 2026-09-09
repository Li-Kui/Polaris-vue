package com.polaris.ai.tools;

import com.github.pagehelper.PageHelper;
import com.polaris.ai.tools.base.*;
import com.polaris.ai.utils.ToolSseHolder;
import com.polaris.system.domain.SysOperLog;
import com.polaris.system.service.ISysOperLogService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author k
 * @date 2026/6/26
 */
@Component
@AiAgentTool(value = "系统底层参数", scope = ToolScope.ADMIN_ONLY)
public class PolarisSystemTools implements AiTool {

    @Autowired
    private ISysOperLogService operLogService;

    /**
     * 该方法将被注册为大模型的外部工具。
     * 描述信息（注解内的文本）非常重要，大模型靠它来判断何时调用此工具。
     */
    @Tool("获取最近的系统操作日志，用于分析系统最近发生的非正常操作、错误或特定用户的动作")
    @AiToolPermission(value = "monitor:operlog:list", sideEffect = ToolSideEffect.READ)
    public List<String> getRecentSystemLogs() {
        ToolSseHolder.ensureActive();
        SysOperLog query = new SysOperLog();
        // 查询最近的操作日志，并提取关键信息返回给大模型
        List<SysOperLog> logs;
        PageHelper.startPage(1, 10, false);
        try {
            logs = operLogService.selectOperLogList(query);
        } finally {
            PageHelper.clearPage();
        }
        ToolSseHolder.ensureActive();
        return logs.stream()
                .limit(10)
                .map(log -> String.format("[%s] 用户:%s 操作:%s 模块:%s 状态:%s",
                        log.getOperTime(), log.getOperName(), log.getTitle(),
                        log.getBusinessType(), log.getStatus() == 0 ? "成功" : "失败"))
                .collect(Collectors.toList());
    }
}
