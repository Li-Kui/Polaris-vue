package com.polaris.ai.tools.rag;

import com.polaris.ai.tools.SecurityContextToolExecutor;
import com.polaris.ai.tools.base.AiAgentTool;
import com.polaris.ai.tools.base.AiTool;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具语义检索器与 Tool-RAG 匹配中心
 * <p>
 * 在 Spring 容器刷新完成后，自动扫描所有实现 AiTool 接口并标注有 @AiAgentTool / @Tool 的 Bean，
 * 提取元数据并在运行期根据用户输入提问按需检索 Top-N 最匹配的安全工具。
 *
 * @author polaris
 */
@Slf4j
@Component
public class ToolRetriever implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private ApplicationContext applicationContext;

    /** 最小匹配置信度阈值（低于此梯度的杂质工具将被物理隔离） */
    public static final double MIN_SCORE_THRESHOLD = 5.0;

    /** 全局工具集 Token 消耗预算软上限（防止 Tool JSON Schema 撑爆 Prompt 上下文） */
    public static final int MAX_TOOL_TOKEN_BUDGET = 1200;

    /** 注册到内存中的所有工具方法元数据列表 */
    private final List<ToolMethodMetadata> registeredToolMetadata = new CopyOnWriteArrayList<>();

    @Data
    public static class ToolMethodMetadata {
        private String toolClassName;
        private String categoryName;
        private String methodName;
        private String toolDescription;
        private Object targetBean;
        private Method targetMethod;
        private ToolSpecification specification;
        private ToolSpecification slimSpecification; // 第二阶：Slim 降维历史工具规范
        private int tokenCost; // 第三阶：预估 Token 成本缓存
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 防止父子容器重复触发
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        initToolRegistry();
    }

    /**
     * 自动扫描并提取容器中所有的 AI 工具元数据
     */
    public synchronized void initToolRegistry() {
        registeredToolMetadata.clear();
        Map<String, AiTool> toolBeans = applicationContext.getBeansOfType(AiTool.class);
        log.info(">>> [ToolRetriever] 正在初始化工具向量索引，检测到 {} 个 AiTool 实例", toolBeans.size());

        for (Map.Entry<String, AiTool> entry : toolBeans.entrySet()) {
            Object toolObj = entry.getValue();
            Class<?> targetClass = AopUtils.getTargetClass(toolObj);
            String className = targetClass.getSimpleName();

            AiAgentTool classAnno = targetClass.getAnnotation(AiAgentTool.class);
            String categoryName = classAnno != null ? classAnno.value() : className;

            Method[] methods = targetClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Tool.class)) {
                    Tool toolAnno = method.getAnnotation(Tool.class);
                    String desc = toolAnno.value() != null && toolAnno.value().length > 0
                            ? String.join("\n", toolAnno.value())
                            : method.getName();

                    ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);

                    // 第二阶：构造 Slim 降维精简 Schema（仅保留 name 与基础 description，擦除复杂入参规范）
                    ToolSpecification slimSpec = ToolSpecification.builder()
                            .name(spec.name())
                            .description(categoryName + " - " + (spec.description() != null ? spec.description() : desc))
                            .build();

                    // 第三阶：预算 Token 消耗成本（以字符数/3 + 基础结构开销进行静态预估与缓存）
                    int cost = (spec.toString().length() / 3) + 20;

                    ToolMethodMetadata metadata = new ToolMethodMetadata();
                    metadata.setToolClassName(className);
                    metadata.setCategoryName(categoryName);
                    metadata.setMethodName(method.getName());
                    metadata.setToolDescription(categoryName + " - " + desc);
                    metadata.setTargetBean(toolObj);
                    metadata.setTargetMethod(method);
                    metadata.setSpecification(spec);
                    metadata.setSlimSpecification(slimSpec);
                    metadata.setTokenCost(cost);

                    registeredToolMetadata.add(metadata);
                    log.info(">>> [ToolRetriever] 注册工具元数据: {}#{} [预估消耗: {} Tokens]", className, method.getName(), cost);
                }
            }
        }
    }

    /**
     * 根据用户输入的提问及安全上下文，按语义相似度检索 Top-N 最匹配的安全工具
     *
     * @param userPrompt      用户当前提问
     * @param securityContext 线程安全上下文
     * @param searchKey       联网搜索 Key（若有）
     * @param topN            最大返回的工具数量
     * @return 过滤并封装后的 ToolSpecification 与 ToolExecutor 映射
     */
    public Map<ToolSpecification, ToolExecutor> retrieveTools(String userPrompt, SecurityContext securityContext, String searchKey, int topN) {
        return retrieveTools(userPrompt, securityContext, searchKey, topN, null);
    }

    public Map<ToolSpecification, ToolExecutor> retrieveTools(String userPrompt, SecurityContext securityContext, String searchKey, int topN, SseEmitter emitter) {
        Map<ToolSpecification, ToolExecutor> result = new HashMap<>();
        if (userPrompt == null || userPrompt.trim().isEmpty() || registeredToolMetadata.isEmpty()) {
            return result;
        }

        // 计算相似度得分并排序
        List<ScoredTool> scoredTools = new ArrayList<>();
        String promptLower = userPrompt.toLowerCase();

        for (ToolMethodMetadata meta : registeredToolMetadata) {
            // 特殊过滤：联网搜索工具若未开启/无 key，跳过
            if (meta.getToolClassName().contains("WebSearchTools")
                    && (searchKey == null || searchKey.trim().isEmpty())) {
                continue;
            }

            double score = calculateSimilarityScore(promptLower, meta);
            if (score >= MIN_SCORE_THRESHOLD) {
                scoredTools.add(new ScoredTool(meta, score));
            }
        }

        // 按得分倒序排列
        scoredTools.sort((a, b) -> Double.compare(b.score, a.score));

        // 第一阶：贪心预算自适应装配算法 (MAX_TOOL_TOKEN_BUDGET = 1200)
        int currentTokens = 0;
        int count = 0;
        for (ScoredTool st : scoredTools) {
            if (count >= topN) {
                break;
            }
            ToolMethodMetadata meta = st.metadata;
            int cost = meta.getTokenCost();
            if (currentTokens + cost > MAX_TOOL_TOKEN_BUDGET) {
                log.warn(">>> [ToolRetriever] 触发全局 Token 预算保护: 已装配 {} Tokens, 工具 {}#{} 需要 {} Tokens (已自动防爆跳过)",
                        currentTokens, meta.getToolClassName(), meta.getMethodName(), cost);
                continue;
            }
            ToolExecutor originalExecutor = new DefaultToolExecutor(meta.getTargetBean(), meta.getTargetMethod());
            // 封装安全上下文传播执行器
            ToolExecutor wrappedExecutor = SecurityContextToolExecutor.wrapExecutor(originalExecutor, securityContext, searchKey, emitter);
            result.put(meta.getSpecification(), wrappedExecutor);
            currentTokens += cost;
            count++;
            log.info(">>> [ToolRetriever] 动态按需注入工具: {}#{} (匹配得分: {}, 预算消耗: {} Tokens, 当前总预算: {}/{})",
                    meta.getToolClassName(), meta.getMethodName(), String.format("%.2f", st.score), cost, currentTokens, MAX_TOOL_TOKEN_BUDGET);
        }

        return result;
    }

    /**
     * 根据多轮历史调用的工具名称集合，装配并保留 Slim 瘦身模式的历史工具 Schema（第二阶：降维立省 80% Token 消耗）
     */
    public Map<ToolSpecification, ToolExecutor> getSlimToolsByNames(Set<String> toolNames, SecurityContext securityContext, String searchKey) {
        return getSlimToolsByNames(toolNames, securityContext, searchKey, null);
    }

    public Map<ToolSpecification, ToolExecutor> getSlimToolsByNames(Set<String> toolNames, SecurityContext securityContext, String searchKey, SseEmitter emitter) {
        Map<ToolSpecification, ToolExecutor> result = new HashMap<>();
        if (toolNames == null || toolNames.isEmpty() || registeredToolMetadata.isEmpty()) {
            return result;
        }

        for (ToolMethodMetadata meta : registeredToolMetadata) {
            if (toolNames.contains(meta.getToolClassName()) || toolNames.contains(meta.getMethodName())) {
                if (meta.getToolClassName().contains("WebSearchTools")
                        && (searchKey == null || searchKey.trim().isEmpty())) {
                    continue;
                }
                ToolExecutor originalExecutor = new DefaultToolExecutor(meta.getTargetBean(), meta.getTargetMethod());
                ToolExecutor wrappedExecutor = SecurityContextToolExecutor.wrapExecutor(originalExecutor, securityContext, searchKey, emitter);
                // 使用 Slim 精简降维 Schema，擦除复杂的参数详细描述
                ToolSpecification slimSpec = meta.getSlimSpecification() != null ? meta.getSlimSpecification() : meta.getSpecification();
                result.put(slimSpec, wrappedExecutor);
                log.info(">>> [ToolRetriever] 降维 Slim 模式补充历史工具 Schema: {}#{}", meta.getToolClassName(), meta.getMethodName());
            }
        }
        return result;
    }

    /**
     * 根据指定的工具名称或方法名集合（如多轮历史调用的工具名），检索并装配工具执行器
     */
    public Map<ToolSpecification, ToolExecutor> getToolsByNames(Set<String> toolNames, SecurityContext securityContext, String searchKey) {
        Map<ToolSpecification, ToolExecutor> result = new HashMap<>();
        if (toolNames == null || toolNames.isEmpty() || registeredToolMetadata.isEmpty()) {
            return result;
        }

        for (ToolMethodMetadata meta : registeredToolMetadata) {
            if (toolNames.contains(meta.getToolClassName()) || toolNames.contains(meta.getMethodName())) {
                if (meta.getToolClassName().contains("WebSearchTools")
                        && (searchKey == null || searchKey.trim().isEmpty())) {
                    continue;
                }
                ToolExecutor originalExecutor = new DefaultToolExecutor(meta.getTargetBean(), meta.getTargetMethod());
                ToolExecutor wrappedExecutor = SecurityContextToolExecutor.wrapExecutor(originalExecutor, securityContext, searchKey);
                result.put(meta.getSpecification(), wrappedExecutor);
                log.info(">>> [ToolRetriever] 补充并保留多轮对话历史工具 Schema: {}#{}", meta.getToolClassName(), meta.getMethodName());
            }
        }
        return result;
    }

    /**
     * 判断当前提问是否能在工具库中匹配到置信度得分 >= MIN_SCORE_THRESHOLD 的相关工具
     */
    public boolean hasMatchingTools(String userPrompt) {
        if (userPrompt == null || userPrompt.trim().isEmpty() || registeredToolMetadata.isEmpty()) {
            return false;
        }
        String promptLower = userPrompt.toLowerCase();
        for (ToolMethodMetadata meta : registeredToolMetadata) {
            double score = calculateSimilarityScore(promptLower, meta);
            if (score >= MIN_SCORE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算输入提问与工具语义描述的相似度得分
     */
    private double calculateSimilarityScore(String promptLower, ToolMethodMetadata meta) {
        double score = 0.0;
        String descLower = meta.getToolDescription().toLowerCase();
        String categoryLower = meta.getCategoryName().toLowerCase();

        // 1. 分纲分类/精确名称命中（高分 10.0）
        if (promptLower.contains(categoryLower) || promptLower.contains(meta.getToolClassName().toLowerCase())) {
            score += 10.0;
        }

        // 2. 强组合特征词打分（每项 +6.0，精准领域词组匹配）
        String[][] strongCombinations = {
                {"用户", "分析"}, {"用户", "查询"}, {"用户", "列表"}, {"系统", "用户"}, {"用户", "报告"}, {"用户", "数据"},
                {"生成", "图片"}, {"画", "图"}, {"生图", "画图"}, {"画", "一张"}, {"修改", "图片"},
                {"搜索", "联网"}, {"网页", "检索"}, {"系统", "日志"}, {"操作", "日志"}
        };
        for (String[] combo : strongCombinations) {
            if (promptLower.contains(combo[0]) && promptLower.contains(combo[1])) {
                if (descLower.contains(combo[0]) || descLower.contains(combo[1])) {
                    score += 6.0;
                }
            }
        }

        // 3. 领域核心关键词打分（移除泛词“生成”，防止“生成报告”误触发画图）
        String[] singleKeywords = {"用户", "分析", "查询", "审计", "生图", "画图", "图片", "画一张", "改图", "搜索", "检索", "密码", "新增", "账号", "日志"};
        for (String kw : singleKeywords) {
            if (promptLower.contains(kw) && descLower.contains(kw)) {
                score += 2.0;
            }
        }

        // 4. 词汇包含匹配（排除停用词和通用动词：生成、修改、可以、提供等，防止短语误伤）
        Set<String> ignoreWords = new HashSet<>(Arrays.asList("生成", "修改", "可以", "提供", "包含", "或者", "这个", "调用"));
        String[] descWords = descLower.split("[\\s,，。;；:：\\(\\)（）\\【\\】]+");
        for (String word : descWords) {
            if (word.length() >= 2 && !ignoreWords.contains(word) && promptLower.contains(word)) {
                score += 1.0;
            }
        }

        return score;
    }

    @Data
    private static class ScoredTool {
        private final ToolMethodMetadata metadata;
        private final double score;
    }
}
