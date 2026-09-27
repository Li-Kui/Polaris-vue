package com.polaris.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.domain.AiModelConfig;

import java.util.List;

/** 模型稳定身份的只读兼容查询服务。 */
public interface IAiModelConfigService extends IService<AiModelConfig> {

    List<AiModelConfig> selectModelConfigList(AiModelConfig config);

    List<AiModelConfig> selectAvailableModelConfigsByCapability(
            String capabilityCode, Long deptId, Boolean isAdmin);
}
