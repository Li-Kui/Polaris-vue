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
import com.polaris.common.utils.SecurityUtils;
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

    /**
     * 获取系统所有可用的智能体工具列表（动态扫描）
     * GET /ai/agent/tools
     */
    @Operation(summary = "获取所有可用的工具 Bean 列表")
    @GetMapping("/tools")
    public ResultData<List<Map<String, String>>> getAvailableTools() {
        List<Map<String, String>> toolsList = new ArrayList<>();
        if (allTools != null) {
            for (AiTool tool : allTools) {
                Class<?> targetClass = AopUtils.getTargetClass(tool);
                Map<String, String> map = new HashMap<>();
                String simpleName = targetClass.getSimpleName();
                map.put("name", simpleName);
                
                AiAgentTool ann = targetClass.getAnnotation(AiAgentTool.class);
                String label = (ann != null) ? ann.value() + " (" + simpleName + ")" : simpleName + " (自定义工具)";
                map.put("label", label);
                toolsList.add(map);
            }
        }
        return ok(toolsList);
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
        List<AiAgent> agents = agentService.list();
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
        query.setStatus("0");
        List<AiAgent> list = agentService.selectAgentList(query);
        if (list == null || list.isEmpty()) {
            list = agentService.list();
        }
        return ok(list);
    }

    /**
     * 获取智能体详细信息
     * GET /ai/agent/{id}
     */
    @Operation(summary = "获取智能体详情")
    @GetMapping("/{id}")
    public ResultData getInfo(@PathVariable Long id) {
        return ok(agentService.getById(id));
    }

    /**
     * 新增智能体
     * POST /ai/agent
     */
    @Operation(summary = "新增智能体")
    @Log(title = "智能体管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody AiAgent agent) {
        agent.setCreateBy(SecurityUtils.getUsername());
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
        agent.setUpdateBy(SecurityUtils.getUsername());
        return toAjaxResult(agentService.updateById(agent));
    }

    /**
     * 逻辑删除智能体
     * DELETE /ai/agent/{id}
     */
    @Operation(summary = "删除智能体")
    @Log(title = "智能体管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        return toAjaxResult(agentService.removeById(id));
    }
}
