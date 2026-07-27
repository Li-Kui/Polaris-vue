package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.domain.AiReport;
import com.polaris.ai.mapper.AiReportMapper;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiReportService;
import com.polaris.common.utils.SecurityUtils;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * AI 分析报告服务层实现类
 *
 * @author polaris
 */
@Slf4j
@Service
public class AiReportServiceImpl extends ServiceImpl<AiReportMapper, AiReport> implements IAiReportService {

    @Autowired
    private AiModelFactory modelFactory;

    @Override
    public boolean saveReport(AiReport report) {
        if (report == null) {
            return false;
        }
        if (report.getCreateTime() == null) {
            report.setCreateTime(new Date());
        }
        if (report.getCreateBy() == null || report.getCreateBy().trim().isEmpty()) {
            try {
                report.setCreateBy(SecurityUtils.getUsername());
            } catch (Exception e) {
                report.setCreateBy("admin");
            }
        }
        if (report.getAgentCode() == null || report.getAgentCode().trim().isEmpty()) {
            report.setAgentCode("POLARIS-ANALYST");
        }
        if (report.getReportCode() == null || report.getReportCode().trim().isEmpty()) {
            report.setReportCode("REP-" + System.currentTimeMillis());
        }
        return saveOrUpdate(report);
    }

    @Override
    public List<AiReport> selectReportList(AiReport report) {
        LambdaQueryWrapper<AiReport> wrapper = new LambdaQueryWrapper<>();
        if (report != null) {
            if (report.getReportCode() != null && !report.getReportCode().trim().isEmpty()) {
                wrapper.like(AiReport::getReportCode, report.getReportCode().trim());
            }
            if (report.getReportTitle() != null && !report.getReportTitle().trim().isEmpty()) {
                wrapper.like(AiReport::getReportTitle, report.getReportTitle().trim());
            }
            if (report.getAgentCode() != null && !report.getAgentCode().trim().isEmpty()) {
                wrapper.eq(AiReport::getAgentCode, report.getAgentCode().trim());
            }
        }
        wrapper.orderByDesc(AiReport::getCreateTime);
        return list(wrapper);
    }

    @Override
    public Object refineReport(String reportContent) {
        log.info(">>> [AiReport] 重塑报告: {}", reportContent);
        if (reportContent == null || reportContent.trim().isEmpty()) {
            return "{}";
        }
        try {
            StreamingChatModel streamingModel = modelFactory.getDefaultStreamingModel();
            if (streamingModel == null) {
                log.warn(">>> [AiReport] 未配置默认 StreamingChatModel 实例");
                return "{}";
            }

            String prompt = "你是一个全领域高级商业与技术报告重塑专家。\n" +
                    "你的任务是将用户提供的原始 Markdown 报告，深度提炼并转化为符合现代高级 UI 可视化大屏要求的结构化 JSON。\n\n" +
                    "【输出规则 - 严格遵守】：\n" +
                    "1. 绝对忠实于原文数据与事实，不得虚构或篡改任何数据与结论。\n" +
                    "2. 所有文本值必须是纯净的显示文本，不得包含任何 Markdown 标记（如 **、*、`、#）。\n" +
                    "3. 所有文本值不得包含 JSON 键名作为内容（如 \"status\"、\"primary\" 不能出现在 value 中）。\n" +
                    "4. status 字段只允许使用语义化中文值：\"正常\"、\"危险\"、\"警告\"、\"一般\"、\"无\"。\n" +
                    "5. 提取 150 字以内的高管摘要 (executiveSummary)，概括核心发现与决策建议。\n" +
                    "6. 提取 3-4 个关键 KPI 衡量指标 (kpiCards)，格式为数组：[{\"label\":\"用户总数\",\"value\":\"3人\",\"status\":\"正常\"}]。\n" +
                    "7. 如果原文包含表格或多维数据，务必提炼为可视化图表 (visualizations)，格式为：[{\"chartType\":\"bar|line|pie\",\"title\":\"维度分布图\",\"chartData\":{\"categories\":[\"研发\",\"测试\"],\"series\":[{\"name\":\"人数\",\"data\":[1,2]}]}}]。\n" +
                    "8. 提取明确的建议与行动计划 (actionPlan)，格式为：[{\"priority\":\"P1\",\"action\":\"建议措施\",\"owner\":\"责任部门\",\"deadline\":\"近期\"}]。\n\n" +
                    "【原始报告内容】：\n" + reportContent + "\n\n" +
                    "请仅输出标准的 JSON 格式，所有字符串值必须是纯净的中文或英文显示文本，绝不要包含 Markdown 标记或原始字段名。";

            StringBuilder resultBuffer = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);

            List<ChatMessage> messages = Collections.singletonList(UserMessage.from(prompt));

            streamingModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse != null) {
                        resultBuffer.append(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    log.error(">>> [AiReportService] 流式生成异常: {}", error.getMessage());
                    latch.countDown();
                }
            });

            boolean finished = latch.await(60, TimeUnit.SECONDS);
            if (!finished) {
                log.warn(">>> [AiReportService] AI 重塑超时");
            }

            String response = resultBuffer.toString();
            if (!response.trim().isEmpty()) {
                String cleanJson = response.trim();
                if (cleanJson.contains("```")) {
                    cleanJson = cleanJson.replaceAll("```json", "").replaceAll("```", "");
                }
                int firstBrace = cleanJson.indexOf("{");
                int lastBrace = cleanJson.lastIndexOf("}");
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    cleanJson = cleanJson.substring(firstBrace, lastBrace + 1);
                }

                try {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map jsonMap = om.readValue(cleanJson, java.util.Map.class);
                    log.info(">>> [AiReportService] 重塑成功并完成 Jackson Map 结构化转换！");
                    return jsonMap;
                } catch (Exception parseErr) {
                    log.warn(">>> [AiReportService] Jackson 转 Map 降级: {}", parseErr.getMessage());
                    return cleanJson.trim();
                }
            }
        } catch (Exception e) {
            log.error(">>> [AiReportService] AI 重塑报告失败: {}", e.getMessage(), e);
        }
        return java.util.Collections.emptyMap();
    }

    @Override
    public SseEmitter refineReportStream(String reportContent) {
        log.info(">>> 启动 AI 智能报告重塑-SSE流式");
        SseEmitter emitter = new SseEmitter(180000L);
        if (reportContent == null || reportContent.trim().isEmpty()) {
            try {
                emitter.send("{}");
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        try {
            StreamingChatModel streamingModel = modelFactory.getDefaultStreamingModel();
            if (streamingModel == null) {
                log.warn(">>> [AiReport] 未配置默认 StreamingChatModel 实例");
                emitter.send("{}");
                emitter.complete();
                return emitter;
            }

            String prompt = "你是一个全领域高级商业与技术报告重塑专家。\n" +
                    "你的任务是将用户提供的任意领域的原始 Markdown 报告（包括安全审计、财务对比、竞品研究、项目汇报等），深度提炼并转化为结构化的 JSON 报告。\n\n" +
                    "【输出规则 - 严格遵守】：\n" +
                    "1. 绝对忠实于原文数据与事实，不得虚构或篡改任何数据与结论。\n" +
                    "2. 所有文本值（label、action、description 等）必须是纯净的显示文本，不得包含任何 Markdown 标记（如 **、*、`、#）。\n" +
                    "3. 所有文本值不得包含 JSON 键名作为内容（如 \"status\"、\"primary\" 不能出现在 value 中）。\n" +
                    "4. status 字段只允许使用以下语义化中文值：\"正常\"、\"危险\"、\"警告\"、\"一般\"、\"无\"。\n" +
                    "5. 自动识别报告所属领域 (SECURITY | FINANCIAL | COMPETITION | PROJECT | OPERATIONAL | GENERAL)。\n" +
                    "6. 提炼出 150 字以内的高管摘要 (executiveSummary)，概括最核心发现与决策建议。\n" +
                    "7. 抽取 2-4 个最关键的 KPI 衡量指标 (kpiCards)，格式如：[{\"label\":\"高危风险\",\"value\":\"1项\",\"status\":\"危险\"}]。\n" +
                    "8. 智能识别数据图表类型 (visualizations)，格式如：[{\"chartType\":\"bar|line|pie|radar\",\"title\":\"图表标题\",\"description\":\"说明\",\"chartData\":{\"categories\":[\"A\",\"B\"],\"series\":[{\"name\":\"数据\",\"data\":[10,20]}]}}]。\n" +
                    "9. 提取明确的行动计划 (actionPlan)，格式如：[{\"priority\":\"P1\",\"action\":\"建议措施\",\"owner\":\"责任部门\"}]。\n\n" +
                    "【原始报告内容】：\n" + reportContent + "\n\n" +
                    "请仅输出紧凑标准的 JSON 格式，所有字符串值必须是纯净的中文或英文显示文本，绝不要包含 Markdown 标记或原始字段名。";

            List<ChatMessage> messages = Collections.singletonList(UserMessage.from(prompt));

            streamingModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse != null) {
                        try {
                            emitter.send(partialResponse);
                        } catch (Exception e) {
                            log.warn(">>> [AiReportService] SSE 发送片段失败: {}", e.getMessage());
                        }
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    try {
                        emitter.send("[DONE]");
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.error(">>> [AiReportService] 流式生成异常: {}", error.getMessage());
                    emitter.completeWithError(error);
                }
            });
        } catch (Exception e) {
            log.error(">>> [AiReportService] AI 重塑报告 SSE 启动失败: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
