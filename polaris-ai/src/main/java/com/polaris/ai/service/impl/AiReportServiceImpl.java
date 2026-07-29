package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.domain.AiReport;
import com.polaris.ai.mapper.AiReportMapper;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiReportService;
import com.polaris.common.constant.HttpStatus;
import com.polaris.common.exception.ServiceException;
import com.polaris.common.utils.SecurityUtils;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final int MAX_REPORT_CONTENT_LENGTH = 120_000;
    private static final int MAX_REFINED_SCHEMA_LENGTH = 300_000;
    private static final String REFINE_STATUS_NONE = "NONE";
    private static final String REFINE_STATUS_RUNNING = "RUNNING";
    private static final String REFINE_STATUS_SUCCESS = "SUCCESS";
    private static final String REFINE_STATUS_FAILED = "FAILED";
    private static final String REFINE_SCHEMA_VERSION = "1.0";
    private static final String REFINE_PROMPT_VERSION = "report-refine-v1";

    private final Map<Long, Object> refineLocks = new ConcurrentHashMap<>();

    @Autowired
    private AiModelFactory modelFactory;

    @Autowired(required = false)
    @Qualifier("threadPoolTaskExecutor")
    private TaskExecutor refineTaskExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean saveReport(AiReport report) {
        if (report == null) {
            return false;
        }
        if (report.getId() != null) {
            throw new ServiceException("归档接口不允许覆盖已有报告", HttpStatus.BAD_REQUEST);
        }
        Long userId = SecurityUtils.getUserId();
        report.setUserId(userId);
        report.setCreateBy(SecurityUtils.getUsername());
        report.setCreateTime(new Date());
        if (report.getAgentCode() == null || report.getAgentCode().trim().isEmpty()) {
            report.setAgentCode("POLARIS-ANALYST");
        }
        if (report.getReportCode() == null || report.getReportCode().trim().isEmpty()) {
            report.setReportCode("REP-" + System.currentTimeMillis());
        }
        validateReportContent(report.getReportContent());
        report.setRefineStatus(REFINE_STATUS_NONE);
        report.setRefineError("");
        return save(report);
    }

    @Override
    public List<AiReport> selectReportList(AiReport report) {
        LambdaQueryWrapper<AiReport> wrapper = new LambdaQueryWrapper<>();
        if (!SecurityUtils.isAdmin()) {
            Long userId = SecurityUtils.getUserId();
            String username = SecurityUtils.getUsername();
            wrapper.and(query -> query.eq(AiReport::getUserId, userId)
                    .or(legacy -> legacy.isNull(AiReport::getUserId)
                            .eq(AiReport::getCreateBy, username)));
        }
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
        wrapper.select(AiReport::getId, AiReport::getReportCode, AiReport::getReportTitle,
                AiReport::getAgentCode, AiReport::getConversationId, AiReport::getUserId,
                AiReport::getCreateBy, AiReport::getCreateTime, AiReport::getUpdateTime,
                AiReport::getRefineStatus, AiReport::getRefineSchemaVersion,
                AiReport::getRefinePromptVersion, AiReport::getRefinedAt,
                AiReport::getRefineError);
        wrapper.orderByDesc(AiReport::getCreateTime);
        return list(wrapper);
    }

    @Override
    public AiReport getReportById(Long id) {
        AiReport report = getById(id);
        assertAccessible(report);
        return report;
    }

    @Override
    public boolean updateReport(AiReport report) {
        if (report == null || report.getId() == null) {
            throw new ServiceException("报告 ID 不能为空", HttpStatus.BAD_REQUEST);
        }
        AiReport existing = getReportById(report.getId());
        String refinedSchema = report.getRefinedSchema();
        if (refinedSchema != null) {
            validateRefinedSchema(refinedSchema);
        }
        AiReport patch = new AiReport();
        patch.setId(existing.getId());
        patch.setRefinedSchema(refinedSchema);
        patch.setUpdateBy(SecurityUtils.getUsername());
        patch.setUpdateTime(new Date());
        return updateById(patch);
    }

    @Override
    public boolean removeReport(Long id) {
        AiReport report = getReportById(id);
        return removeById(report.getId());
    }

    @Override
    public Map<String, Object> startRefineReport(Long reportId) {
        AiReport report = getReportById(reportId);
        validateReportContent(report.getReportContent());
        String sourceHash = calculateSourceHash(report.getReportContent());

        if (isRefinedCacheValid(report, sourceHash)) {
            return buildRefineStatus(report, parseRefinedSchema(report.getRefinedSchema()));
        }

        if (REFINE_STATUS_RUNNING.equals(report.getRefineStatus())
                && Objects.equals(sourceHash, report.getRefinedSourceHash())
                && !isRefineTimedOut(report)) {
            return buildRefineStatus(report, null);
        }

        Long submitUserId = SecurityUtils.getUserId();
        String submitUsername = SecurityUtils.getUsername();
        boolean submitAdmin = SecurityUtils.isAdmin();
        markRefineRunning(reportId, sourceHash, submitUsername);
        Runnable task = () -> {
            try {
                refineReport(reportId, submitUserId, submitUsername, submitAdmin);
            } catch (Exception e) {
                log.warn(">>> [AiReport] 异步美化任务失败，reportId={}, message={}", reportId, e.getMessage());
            }
        };
        if (refineTaskExecutor != null) {
            refineTaskExecutor.execute(task);
        } else {
            new Thread(task, "report-refine-" + reportId).start();
        }
        return buildRefineStatus(getById(reportId), null);
    }

    @Override
    public Map<String, Object> getRefineStatus(Long reportId) {
        AiReport report = getReportById(reportId);
        Map<String, Object> status = buildRefineStatus(report, null);
        if (REFINE_STATUS_SUCCESS.equals(report.getRefineStatus())
                && report.getRefinedSchema() != null) {
            status.put("schema", parseRefinedSchema(report.getRefinedSchema()));
        }
        return status;
    }

    private Map<String, Object> buildRefineStatus(AiReport report, Map<String, Object> schema) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("reportId", report.getId());
        result.put("status", report.getRefineStatus() == null ? REFINE_STATUS_NONE : report.getRefineStatus());
        result.put("sourceHash", report.getRefinedSourceHash());
        result.put("schemaVersion", report.getRefineSchemaVersion());
        result.put("promptVersion", report.getRefinePromptVersion());
        result.put("refinedAt", report.getRefinedAt());
        result.put("error", report.getRefineError());
        if (schema != null) {
            result.put("schema", schema);
        }
        return result;
    }

    @Override
    public Object refineReport(Long reportId) {
        if (reportId == null) {
            throw new ServiceException("报告 ID 不能为空", HttpStatus.BAD_REQUEST);
        }
        return refineReport(reportId, SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin());
    }

    /**
     * 在指定用户上下文中执行报告美化，供异步任务使用，避免依赖请求线程的 SecurityContext。
     */
    private Object refineReport(Long reportId, Long userId, String username, boolean admin) {
        Object lock = refineLocks.computeIfAbsent(reportId, key -> new Object());
        synchronized (lock) {
            AiReport report = getById(reportId);
            assertAccessible(report, userId, username, admin);
            validateReportContent(report.getReportContent());
            String sourceHash = calculateSourceHash(report.getReportContent());

            if (isRefinedCacheValid(report, sourceHash)) {
                return parseRefinedSchema(report.getRefinedSchema());
            }

            markRefineRunning(reportId, sourceHash, username);
            try {
                Object result = refineReport(report.getReportContent());
                Map<String, Object> schema = toRefinedSchema(result);
                schema.put("schemaVersion", REFINE_SCHEMA_VERSION);
                String schemaJson;
                try {
                    schemaJson = objectMapper.writeValueAsString(schema);
                } catch (Exception e) {
                    throw new ServiceException("AI 美化结果序列化失败", HttpStatus.ERROR);
                }
                validateRefinedSchema(schemaJson);

                AiReport patch = new AiReport();
                patch.setId(reportId);
                patch.setRefinedSchema(schemaJson);
                patch.setRefineStatus(REFINE_STATUS_SUCCESS);
                patch.setRefinedSourceHash(sourceHash);
                patch.setRefineSchemaVersion(REFINE_SCHEMA_VERSION);
                patch.setRefinePromptVersion(REFINE_PROMPT_VERSION);
                patch.setRefineStartedAt(null);
                patch.setRefinedAt(new Date());
                patch.setRefineError("");
                patch.setUpdateBy(username);
                patch.setUpdateTime(new Date());
                updateRefinePatch(patch);
                return schema;
            } catch (ServiceException e) {
                markRefineFailed(reportId, sourceHash, e.getMessage(), username);
                throw e;
            } catch (Exception e) {
                String message = e.getMessage() == null ? "未知异常" : e.getMessage();
                markRefineFailed(reportId, sourceHash, message, username);
                throw new ServiceException("AI 美化失败: " + message, HttpStatus.ERROR);
            }
        }
    }

    private boolean isRefinedCacheValid(AiReport report, String sourceHash) {
        if (!REFINE_STATUS_SUCCESS.equals(report.getRefineStatus())
                || !Objects.equals(sourceHash, report.getRefinedSourceHash())
                || !Objects.equals(REFINE_SCHEMA_VERSION, report.getRefineSchemaVersion())
                || !Objects.equals(REFINE_PROMPT_VERSION, report.getRefinePromptVersion())
                || report.getRefinedSchema() == null
                || report.getRefinedSchema().trim().isEmpty()) {
            return false;
        }
        try {
            parseRefinedSchema(report.getRefinedSchema());
            return true;
        } catch (Exception e) {
            log.warn(">>> [AiReport] 美化缓存校验失败，将重新生成，reportId={}", report.getId());
            return false;
        }
    }

    private void markRefineRunning(Long reportId, String sourceHash) {
        markRefineRunning(reportId, sourceHash, SecurityUtils.getUsername());
    }

    private void markRefineRunning(Long reportId, String sourceHash, String username) {
        AiReport patch = new AiReport();
        patch.setId(reportId);
        patch.setRefineStatus(REFINE_STATUS_RUNNING);
        patch.setRefinedSourceHash(sourceHash);
        patch.setRefineSchemaVersion(REFINE_SCHEMA_VERSION);
        patch.setRefinePromptVersion(REFINE_PROMPT_VERSION);
        patch.setRefineStartedAt(new Date());
        patch.setRefineError("");
        patch.setUpdateBy(username);
        patch.setUpdateTime(new Date());
        updateRefinePatch(patch);
    }

    private void markRefineFailed(Long reportId, String sourceHash, String errorMessage) {
        markRefineFailed(reportId, sourceHash, errorMessage, SecurityUtils.getUsername());
    }

    private void markRefineFailed(Long reportId, String sourceHash, String errorMessage, String username) {
        try {
            AiReport patch = new AiReport();
            patch.setId(reportId);
            patch.setRefineStatus(REFINE_STATUS_FAILED);
            patch.setRefinedSourceHash(sourceHash);
            patch.setRefineSchemaVersion(REFINE_SCHEMA_VERSION);
            patch.setRefinePromptVersion(REFINE_PROMPT_VERSION);
            patch.setRefineStartedAt(null);
            patch.setRefineError(truncateError(errorMessage));
            patch.setUpdateBy(username);
            patch.setUpdateTime(new Date());
            updateRefinePatch(patch);
        } catch (Exception updateError) {
            log.error(">>> [AiReport] 美化失败状态回写失败，reportId={}", reportId, updateError);
        }
    }

    private boolean isRefineTimedOut(AiReport report) {
        Date startedAt = report.getRefineStartedAt();
        return startedAt != null && System.currentTimeMillis() - startedAt.getTime() > TimeUnit.MINUTES.toMillis(10);
    }

    private void updateRefinePatch(AiReport patch) {
        if (!updateById(patch)) {
            throw new ServiceException("报告美化状态保存失败", HttpStatus.ERROR);
        }
    }

    private String truncateError(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "AI 美化失败";
        }
        String normalized = message.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String calculateSourceHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hash.append(String.format("%02x", value));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("报告内容版本计算失败", HttpStatus.ERROR);
        }
    }

    private void assertAccessible(AiReport report) {
        assertAccessible(report, SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin());
    }

    private void assertAccessible(AiReport report, Long currentUserId, String username, boolean admin) {
        if (report == null) {
            throw new ServiceException("报告不存在", HttpStatus.NOT_FOUND);
        }
        if (admin) {
            return;
        }
        boolean ownedByUser = Objects.equals(currentUserId, report.getUserId());
        boolean legacyOwnedByUsername = report.getUserId() == null
                && Objects.equals(username, report.getCreateBy());
        if (!ownedByUser && !legacyOwnedByUsername) {
            throw new ServiceException("没有权限访问该报告", HttpStatus.FORBIDDEN);
        }
    }

    private void validateReportContent(String reportContent) {
        if (reportContent == null || reportContent.trim().isEmpty()) {
            throw new ServiceException("报告内容不能为空", HttpStatus.BAD_REQUEST);
        }
        if (reportContent.length() > MAX_REPORT_CONTENT_LENGTH) {
            throw new ServiceException("报告内容超过 AI 美化处理上限", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> toRefinedSchema(Object result) {
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> schema = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> schema.put(String.valueOf(key), value));
            return schema;
        }
        if (result instanceof String text && !text.trim().isEmpty()) {
            return parseRefinedSchema(text);
        }
        throw new ServiceException("AI 返回的美化结果格式无效", HttpStatus.ERROR);
    }

    private Map<String, Object> parseRefinedSchema(String schemaJson) {
        validateRefinedSchemaLength(schemaJson);
        try {
            Map<String, Object> schema = objectMapper.readValue(schemaJson, Map.class);
            validateRefinedSchema(schema);
            return schema;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("AI 返回的美化结果不是有效 JSON", HttpStatus.ERROR);
        }
    }

    private void validateRefinedSchema(String schemaJson) {
        validateRefinedSchemaLength(schemaJson);
        validateRefinedSchema(parseRefinedSchemaWithoutLengthCheck(schemaJson));
    }

    private Map<String, Object> parseRefinedSchemaWithoutLengthCheck(String schemaJson) {
        try {
            Map<String, Object> schema = objectMapper.readValue(schemaJson, Map.class);
            validateRefinedSchema(schema);
            return schema;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("AI 美化结果不是有效 JSON", HttpStatus.ERROR);
        }
    }

    private void validateRefinedSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            throw new ServiceException("AI 美化结果不能为空", HttpStatus.ERROR);
        }
        boolean hasRecognizedField = schema.containsKey("executiveSummary")
                || schema.containsKey("kpiCards")
                || schema.containsKey("visualizations")
                || schema.containsKey("compareMatrix")
                || schema.containsKey("swot")
                || schema.containsKey("actionPlan")
                || schema.containsKey("sections");
        if (!hasRecognizedField) {
            throw new ServiceException("AI 美化结果缺少有效报告字段", HttpStatus.ERROR);
        }
        Object schemaVersion = schema.get("schemaVersion");
        if (schemaVersion != null && !(schemaVersion instanceof String)) {
            throw new ServiceException("AI 美化结果 schemaVersion 格式无效", HttpStatus.ERROR);
        }
        validateKpiCards(schema.get("kpiCards"));
        validateVisualizations(schema.get("visualizations"));
        validateActionPlan(schema.get("actionPlan"));
    }

    private void validateKpiCards(Object value) {
        List<?> cards = validateCollectionField(value, "kpiCards", 8);
        if (cards == null) {
            return;
        }
        for (Object item : cards) {
            if (!(item instanceof Map<?, ?> card)) {
                throw new ServiceException("AI 美化结果 KPI 数据格式无效", HttpStatus.ERROR);
            }
            requireTextField(card, "label", "KPI 标签");
            requireTextField(card, "value", "KPI 数值");
            Object status = card.get("status");
            if (status != null && !(status instanceof String)) {
                throw new ServiceException("AI 美化结果 KPI 状态格式无效", HttpStatus.ERROR);
            }
        }
    }

    private void validateVisualizations(Object value) {
        List<?> visualizations = validateCollectionField(value, "visualizations", 6);
        if (visualizations == null) {
            return;
        }
        for (Object item : visualizations) {
            if (!(item instanceof Map<?, ?> visualization)) {
                throw new ServiceException("AI 美化结果图表格式无效", HttpStatus.ERROR);
            }
            Object chartType = visualization.get("chartType");
            if (!(chartType instanceof String)
                    || !List.of("bar", "line", "pie").contains(chartType)) {
                throw new ServiceException("AI 美化结果包含不支持的图表类型", HttpStatus.ERROR);
            }
            Object chartData = visualization.get("chartData");
            if (!(chartData instanceof Map<?, ?> data)) {
                throw new ServiceException("AI 美化结果图表数据不能为空", HttpStatus.ERROR);
            }
            validateChartData(data, (String) chartType);
        }
    }

    private void validateChartData(Map<?, ?> chartData, String chartType) {
        Object categoriesValue = chartData.get("categories");
        Object seriesValue = chartData.get("series");
        if (!(categoriesValue instanceof List<?> categories)
                || !(seriesValue instanceof List<?> series)
                || categories.isEmpty() || series.isEmpty()
                || categories.size() > 50 || series.size() > 8) {
            throw new ServiceException("AI 美化结果图表维度无效", HttpStatus.ERROR);
        }
        if ("pie".equals(chartType) && series.size() != 1) {
            throw new ServiceException("饼图只允许一个数据系列", HttpStatus.ERROR);
        }
        for (Object item : series) {
            if (!(item instanceof Map<?, ?> currentSeries)) {
                throw new ServiceException("AI 美化结果数据系列格式无效", HttpStatus.ERROR);
            }
            requireTextField(currentSeries, "name", "图表系列名称");
            Object dataValue = currentSeries.get("data");
            if (!(dataValue instanceof List<?> data) || data.size() != categories.size()) {
                throw new ServiceException("AI 美化结果图表数据数量不匹配", HttpStatus.ERROR);
            }
            for (Object point : data) {
                if (!(point instanceof Number)) {
                    throw new ServiceException("AI 美化结果图表数值格式无效", HttpStatus.ERROR);
                }
            }
        }
    }

    private void validateActionPlan(Object value) {
        List<?> actions = validateCollectionField(value, "actionPlan", 12);
        if (actions == null) {
            return;
        }
        for (Object item : actions) {
            if (!(item instanceof Map<?, ?> action)) {
                throw new ServiceException("AI 美化结果行动计划格式无效", HttpStatus.ERROR);
            }
            requireTextField(action, "action", "行动计划内容");
        }
    }

    private List<?> validateCollectionField(Object value, String fieldName, int maxSize) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> list) || list.size() > maxSize) {
            throw new ServiceException("AI 美化结果字段格式无效: " + fieldName, HttpStatus.ERROR);
        }
        return list;
    }

    private void requireTextField(Map<?, ?> value, String fieldName, String displayName) {
        Object field = value.get(fieldName);
        if (!(field instanceof String text) || text.trim().isEmpty() || text.length() > 500) {
            throw new ServiceException("AI 美化结果" + displayName + "无效", HttpStatus.ERROR);
        }
    }

    private void validateRefinedSchemaLength(String schemaJson) {
        if (schemaJson == null || schemaJson.trim().isEmpty()
                || schemaJson.length() > MAX_REFINED_SCHEMA_LENGTH) {
            throw new ServiceException("AI 美化结果长度无效", HttpStatus.ERROR);
        }
    }

    @Override
    public Object refineReport(String reportContent) {
        validateReportContent(reportContent);
        log.info(">>> [AiReport] 开始重塑报告, contentLength={}", reportContent.length());
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
