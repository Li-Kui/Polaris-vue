package com.polaris.ai.prompt;

import com.polaris.ai.pivot.AiModelProperties;
import com.polaris.common.core.domain.entity.SysRole;
import com.polaris.system.service.ISysConfigService;
import com.polaris.system.service.ISysRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户系统角色提示词解析组件
 * 
 * @author polaris
 */
@Component
public class SystemPromptResolver {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptResolver.class);

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private AiModelProperties modelProps;

    /**
     * 根据当前登录用户的角色获取对应的系统提示词。
     * 优先匹配 sys.ai.prompt.[roleKey] 并合并，兜底使用默认 modelProps 全局提示词。
     */
    public String getRoleSpecificSystemPrompt(Long userId) {
        if (userId == null) {
            return modelProps.getSystemPrompt();
        }
        try {
            List<SysRole> roles = roleService.selectRolesByUserId(userId);
            if (roles != null && !roles.isEmpty()) {
                List<String> rolePrompts = roles.stream()
                        .filter(role -> "0".equals(role.getStatus()) && (role.getDelFlag() == null || !"2".equals(role.getDelFlag())))
                        .sorted(Comparator.comparing(SysRole::getRoleSort, Comparator.nullsLast(Integer::compareTo)))
                        .map(role -> {
                            String configKey = "sys.ai.prompt." + role.getRoleKey();
                            return configService.selectConfigByKey(configKey);
                        })
                        .filter(prompt -> prompt != null && !prompt.trim().isEmpty())
                        .map(String::trim)
                        .collect(Collectors.toList());

                if (!rolePrompts.isEmpty()) {
                    return String.join("\n", rolePrompts);
                }
            }
        } catch (Exception e) {
            log.error("根据用户ID: {} 获取角色专属提示词发生异常", userId, e);
        }
        return modelProps.getSystemPrompt();
    }
}
