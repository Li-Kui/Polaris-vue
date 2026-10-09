package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.ai.service.IAiModelConfigService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 模型稳定身份的只读兼容查询服务。 */
@Service
public class AiModelConfigServiceImpl
        extends ServiceImpl<AiModelConfigMapper, AiModelConfig>
        implements IAiModelConfigService {

    private final AiModelConfigMapper modelConfigMapper;

    public AiModelConfigServiceImpl(AiModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    @Override
    public List<AiModelConfig> selectModelConfigList(AiModelConfig config) {
        return modelConfigMapper.selectModelConfigList(config);
    }

    @Override
    public List<AiModelConfig> selectAvailableModelConfigsByCapability(
            String capabilityCode, Long deptId, Boolean isAdmin) {
        return modelConfigMapper.selectAvailableModelConfigsByCapability(
                capabilityCode, deptId, isAdmin);
    }
}
