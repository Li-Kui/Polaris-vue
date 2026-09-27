package com.polaris.ai.modelcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Provider Connection 删除前的模型引用检查。 */
@Service
@Transactional(readOnly = true)
public class ProviderConnectionReferenceService {

    private final AiModelConfigMapper modelConfigMapper;

    public ProviderConnectionReferenceService(AiModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    public long countModels(Long connectionId) {
        return modelConfigMapper.selectCount(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getConnectionId,
                        Objects.requireNonNull(connectionId, "connectionId")));
    }

    public void assertNotReferenced(Long connectionId) {
        long references = countModels(connectionId);
        if (references > 0) {
            throw new ServiceException(
                    "Provider Connection 仍被模型引用，不能删除，引用数：" + references);
        }
    }
}
