package com.polaris.ai.tools.base;

import java.lang.annotation.*;

/**
 * 智能体工具类描述注解
 * 用于在管理后台动态提取并展现易读的中文名称
 *
 * @author polaris
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiAgentTool {
    /**
     * 工具的易读中文描述名称
     */
    String value();

    /**
     * 工具适用范围，默认为全员通用
     */
    ToolScope scope() default ToolScope.UNIVERSAL;

    /**
     * 工具依赖的前置条件，默认为无依赖
     */
    ToolRequirement requirement() default ToolRequirement.NONE;
}
