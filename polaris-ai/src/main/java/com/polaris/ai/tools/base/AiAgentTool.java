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
}
