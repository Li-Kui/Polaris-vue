package com.polaris.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.domain.AiModelConfig;

import java.util.List;

/**
 * AI 模型配置服务层接口
 * 
 * @author polaris
 */
public interface IAiModelConfigService extends IService<AiModelConfig>
{
    /**
     * 查询模型配置列表
     */
    List<AiModelConfig> selectModelConfigList(AiModelConfig config);

    /**
     * 查询当前用户可用的模型列表（系统共享 + 指定部门独享）
     */
    List<AiModelConfig> selectAvailableModelConfigs(Long deptId, Boolean isAdmin);

    /** 查询当前用户可用的指定类型模型。 */
    List<AiModelConfig> selectAvailableModelConfigsByType(String modelType, Long deptId, Boolean isAdmin);

    /**
     * 根据 ID 获取模型配置详情
     */
    AiModelConfig selectModelConfigById(Long id);

    /**
     * 根据模型名称获取模型配置
     */
    AiModelConfig selectModelConfigByModelName(String modelName);

    /**
     * 获取默认的聊天对话模型配置
     */
    AiModelConfig selectDefaultChatModel(Long userDeptId, String dataScopeSql);

    /**
     * 获取默认的向量模型配置
     */
    AiModelConfig selectDefaultEmbeddingModel(Long userDeptId, String dataScopeSql);

    /**
     * 获取指定类型下的默认模型配置
     */
    AiModelConfig selectDefaultModel(String modelType, Long userDeptId, String dataScopeSql);

    /**
     * 新增模型配置
     */
    int insertModelConfig(AiModelConfig config);

    /**
     * 修改模型配置
     */
    int updateModelConfig(AiModelConfig config);

    /**
     * 重置所有模型配置的默认聊天模型状态
     */
    int cleanDefaultChatStatus(Long deptId);

    /**
     * 重置所有模型配置的默认向量模型状态
     */
    int cleanDefaultEmbeddingStatus(Long deptId);

    /**
     * 清除指定类型下的默认模型状态
     */
    int cleanDefaultStatus(String modelType, Long deptId);

    /**
     * 根据 ID 删除模型配置
     */
    int deleteModelConfigById(Long id);
}
