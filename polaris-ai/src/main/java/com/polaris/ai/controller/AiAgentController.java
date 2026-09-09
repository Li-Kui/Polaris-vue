package com.polaris.ai.controller;

import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.service.IAiAgentService;
import com.polaris.ai.tools.base.AiAgentTool;
import com.polaris.ai.tools.base.AiTool;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import dev.langchain4j.agent.tool.Tool;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI智能体管理控制器
 * 提供智能体的 CRUD 接口
 *
 * @author polaris
 */
@Slf4j
@Tag(name = "AI智能体管理")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/agent")
public class AiAgentController extends BaseController {

    @Autowired
    private IAiAgentService agentService;

    @Autowired(required = false)
    private List<AiTool> allTools;

    @Autowired
    private com.polaris.ai.service.IAiModelConfigService modelConfigService;

    /**
     * 获取系统所有可用的智能体工具列表（基于作用域与运行环境动态过滤）
     * GET /ai/agent/tools
     */
    @Operation(summary = "获取所有可用的工具 Bean 列表")
    @GetMapping("/tools")
    public ResultData<List<Map<String, Object>>> getAvailableTools() {
        List<Map<String, Object>> toolsList = new ArrayList<>();
        boolean isPlatform = com.polaris.ai.core.context.CallerUtils.isPlatformMode();
        boolean hasImageModel = checkHasActiveImageModel();

        if (allTools != null) {
            for (AiTool tool : allTools) {
                Class<?> targetClass = AopUtils.getTargetClass(tool);
                AiAgentTool ann = targetClass.getAnnotation(AiAgentTool.class);
                com.polaris.ai.tools.base.ToolScope scope = (ann != null) ? ann.scope() : com.polaris.ai.tools.base.ToolScope.UNIVERSAL;
                com.polaris.ai.tools.base.ToolRequirement req = (ann != null) ? ann.requirement() : com.polaris.ai.tools.base.ToolRequirement.NONE;

                // 1. 若为中台模式，彻底过滤掉仅管理端专属的特权工具（底层参数、用户审计等）
                if (isPlatform && scope == com.polaris.ai.tools.base.ToolScope.ADMIN_ONLY) {
                    continue;
                }

                Map<String, Object> map = new HashMap<>();
                String simpleName = targetClass.getSimpleName();
                map.put("name", simpleName);
                String label = (ann != null) ? ann.value() + " (" + simpleName + ")" : simpleName + " (自定义工具)";
                map.put("label", label);
                map.put("scope", scope.name());
                map.put("requirement", req.name());

                // 2. 动态计算可用性状态与不可用提示
                if (req == com.polaris.ai.tools.base.ToolRequirement.IMAGE_MODEL) {
                    map.put("available", hasImageModel);
                    map.put("disabledReason", hasImageModel ? "" : "当前租户/系统未配置或未启用 AI 绘图模型");
                } else if (req == com.polaris.ai.tools.base.ToolRequirement.SEARCH_KEY) {
                    // 联网搜索工具由前端与所选大模型 searchKey 联动控制
                    map.put("available", true);
                    map.put("disabledReason", "所选底座大模型未配置联网搜索 API Key (Tavily Key)");
                } else {
                    map.put("available", true);
                    map.put("disabledReason", "");
                }

                toolsList.add(map);
            }
        }
        return ok(toolsList);
    }

    private boolean checkHasActiveImageModel() {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.polaris.ai.domain.AiModelConfig> qw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            qw.eq(com.polaris.ai.domain.AiModelConfig::getModelType, "IMAGE")
              .eq(com.polaris.ai.domain.AiModelConfig::getStatus, "1");
            return modelConfigService.count(qw) > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取系统所有含有 @Tool 注解的方法字典映射（用于前端动态替换英文方法名）
     * GET /ai/agent/tools/dictionary
     */
    @Operation(summary = "获取所有工具方法中英文名称映射词典")
    @GetMapping("/tools/dictionary")
    public ResultData<Map<String, String>> getToolsDictionary() {
        Map<String, String> dictionary = new HashMap<>();
        
        // 1. 扫描 @Tool 注解的方法
        if (allTools != null) {
            for (AiTool tool : allTools) {
                Class<?> targetClass = AopUtils.getTargetClass(tool);
                for (java.lang.reflect.Method method : targetClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Tool.class)) {
                        Tool toolAnn = method.getAnnotation(Tool.class);
                        String methodName = method.getName();
                        
                        // 从 @Tool 的描述里提取第一句话或者前面的中文作为友好展示名
                        String[] descriptions = toolAnn.value();
                        String description = (descriptions != null && descriptions.length > 0) ? descriptions[0] : "";
                        String friendlyName = extractFriendlyName(methodName, description);
                        
                        dictionary.put(methodName, friendlyName);
                    }
                }
            }
        }
        
        // 2. 扫描已有的智能体 code 和名称
        List<AiAgent> agents = agentService.selectAgentList(null);
        if (agents != null) {
            for (AiAgent agent : agents) {
                if (agent.getAgentCode() != null && agent.getAgentName() != null) {
                    dictionary.put(agent.getAgentCode(), agent.getAgentName());
                }
            }
        }
        
        // 3. 扫描工作流节点 executor 常用名称（比如 intent_router 等）
        dictionary.put("intent_router", "意图分发员");
        dictionary.put("sys_user_analyst", "系统用户审计师");
        dictionary.put("sys_user_query", "系统用户查询员");
        dictionary.put("sys_user_audit", "系统用户审计师");
        
        return ok(dictionary);
    }
    
    private String extractFriendlyName(String methodName, String description) {
        if (description == null || description.trim().isEmpty()) {
            return methodName;
        }
        // 对 Tool 描述文本进行处理，提取出第一个中文字句
        String firstSentence = description.split("[,，.。;；\\n]")[0].trim();
        if (firstSentence.length() > 20) {
            return firstSentence.substring(0, 20) + "...";
        }
        return firstSentence.isEmpty() ? methodName : firstSentence;
    }

    /**
     * 条件分页查询智能体配置列表
     * GET /ai/agent/list
     */
    @Operation(summary = "条件分页查询智能体列表")
    @GetMapping("/list")
    public ResultData<Page<AiAgent>> list(AiAgent agent) {
        startPage();
        List<AiAgent> list = agentService.selectAgentList(agent);
        return ok(getDataPage(list));
    }

    /**
     * 获取所有启用的智能体列表（用于工作流编排下拉绑定）
     * GET /ai/agent/list/all
     */
    @Operation(summary = "获取所有启用的智能体列表")
    @GetMapping({"/list/all", "/active/list"})
    public ResultData<List<AiAgent>> listAll() {
        AiAgent query = new AiAgent();
        query.setStatus("1");
        List<AiAgent> list = agentService.selectAgentList(query);
        return ok(list);
    }

    /**
     * 获取智能体详细信息
     * GET /ai/agent/{id}
     */
    @Operation(summary = "获取智能体详情")
    @GetMapping("/{id}")
    public ResultData getInfo(@PathVariable Long id) {
        AiAgent agent = agentService.getById(id);
        return ok(belongsToCurrentScope(agent) ? agent : null);
    }

    /**
     * 新增智能体
     * POST /ai/agent
     */
    @Operation(summary = "新增智能体")
    @Log(title = "智能体管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody AiAgent agent) {
        validateAgentTools(agent.getTools());
        agent.setTenantId(currentTenantId());
        agent.setCreateBy(com.polaris.ai.core.context.CallerUtils.getUsername());
        return toAjaxResult(agentService.save(agent));
    }

    /**
     * 修改智能体
     * PUT /ai/agent
     */
    @Operation(summary = "修改智能体")
    @Log(title = "智能体管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody AiAgent agent) {
        validateAgentTools(agent.getTools());
        AiAgent existing = agent.getId() == null ? null : agentService.getById(agent.getId());
        if (!belongsToCurrentScope(existing)) {
            throw new com.polaris.common.exception.ServiceException("智能体不存在或无权修改");
        }
        agent.setTenantId(currentTenantId());
        agent.setUpdateBy(com.polaris.ai.core.context.CallerUtils.getUsername());
        return toAjaxResult(agentService.updateById(agent));
    }

    /**
     * 校验智能体工具绑定权限，严防跨租户越权绑定管理端专属工具
     */
    private void validateAgentTools(String tools) {
        if (tools == null || tools.trim().isEmpty() || allTools == null) {
            return;
        }
        boolean isPlatform = com.polaris.ai.core.context.CallerUtils.isPlatformMode();
        if (!isPlatform) {
            return; // 管理后台模式拥有全量权限
        }
        String[] toolNames = tools.split(",");
        for (String name : toolNames) {
            String trimmed = name.trim();
            for (AiTool tool : allTools) {
                Class<?> targetClass = AopUtils.getTargetClass(tool);
                if (targetClass.getSimpleName().equalsIgnoreCase(trimmed)) {
                    AiAgentTool ann = targetClass.getAnnotation(AiAgentTool.class);
                    com.polaris.ai.tools.base.ToolScope scope = (ann != null) ? ann.scope() : com.polaris.ai.tools.base.ToolScope.UNIVERSAL;
                    if (scope == com.polaris.ai.tools.base.ToolScope.ADMIN_ONLY) {
                        throw new com.polaris.common.exception.ServiceException("中台租户无权绑定系统底层管理专属工具: " + trimmed);
                    }
                }
            }
        }
    }

    /**
     * 逻辑删除智能体
     * DELETE /ai/agent/{id}
     */
    @Operation(summary = "删除智能体")
    @Log(title = "智能体管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        AiAgent existing = agentService.getById(id);
        if (!belongsToCurrentScope(existing)) {
            throw new com.polaris.common.exception.ServiceException("智能体不存在或无权删除");
        }
        return toAjaxResult(agentService.removeById(id));
    }

    private Long currentTenantId() {
        if (!com.polaris.ai.core.context.CallerUtils.isPlatformMode()) return null;
        try {
            long tenantId = Long.parseLong(
                    com.polaris.ai.core.context.CallerUtils.getTenantId());
            if (tenantId <= 0) throw new NumberFormatException();
            return tenantId;
        } catch (Exception e) {
            throw new com.polaris.common.exception.ServiceException("中台租户ID格式错误");
        }
    }

    private boolean belongsToCurrentScope(AiAgent agent) {
        return agent != null && java.util.Objects.equals(agent.getTenantId(), currentTenantId());
    }
}
