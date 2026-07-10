package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.ai.service.IAiModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 模型配置服务层实现类
 * 
 * @author polaris
 */
@Slf4j
@Service
public class AiModelConfigServiceImpl extends ServiceImpl<AiModelConfigMapper, AiModelConfig> implements IAiModelConfigService
{
    @Autowired
    private AiModelConfigMapper modelConfigMapper;

    @Override
    public List<AiModelConfig> selectModelConfigList(AiModelConfig config)
    {
        return modelConfigMapper.selectModelConfigList(config);
    }

    @Override
    public List<AiModelConfig> selectAvailableModelConfigs(Long deptId, Boolean isAdmin)
    {
        return modelConfigMapper.selectAvailableModelConfigs(deptId, isAdmin);
    }

    @Override
    public AiModelConfig selectModelConfigById(Long id)
    {
        return modelConfigMapper.selectModelConfigById(id);
    }

    @Override
    public AiModelConfig selectModelConfigByModelName(String modelName)
    {
        return modelConfigMapper.selectModelConfigByModelName(modelName);
    }

    @Override
    public AiModelConfig selectDefaultChatModel(Long userDeptId, String dataScopeSql)
    {
        return modelConfigMapper.selectDefaultChatModel(userDeptId, dataScopeSql);
    }

    @Override
    public AiModelConfig selectDefaultEmbeddingModel(Long userDeptId, String dataScopeSql)
    {
        return modelConfigMapper.selectDefaultEmbeddingModel(userDeptId, dataScopeSql);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int insertModelConfig(AiModelConfig config)
    {
        return modelConfigMapper.insertModelConfig(config);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int updateModelConfig(AiModelConfig config)
    {
        return modelConfigMapper.updateModelConfig(config);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int cleanDefaultChatStatus(Long deptId)
    {
        return modelConfigMapper.cleanDefaultChatStatus(deptId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int cleanDefaultEmbeddingStatus(Long deptId)
    {
        return modelConfigMapper.cleanDefaultEmbeddingStatus(deptId);
    }

    @Override
    public int deleteModelConfigById(Long id)
    {
        return modelConfigMapper.deleteById(id);
    }
}
