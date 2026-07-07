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
    @GetMapping("/list/all")
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
