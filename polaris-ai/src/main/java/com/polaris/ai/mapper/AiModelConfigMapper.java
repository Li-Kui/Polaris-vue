package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiModelConfig;

import java.util.List;

/**
 * AI 模型配置数据访问层
 * 
 * @author polaris
 */
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig>
{
    /**
     * 查询模型配置列表
     */
    List<AiModelConfig> selectModelConfigList(AiModelConfig config);

    /**
     * 查询当前用户可用的模型列表（系统共享 + 指定部门独享）
     */
    List<AiModelConfig> selectAvailableModelConfigs(
            @org.apache.ibatis.annotations.Param("deptId") Long deptId,
            @org.apache.ibatis.annotations.Param("isAdmin") Boolean isAdmin);

    /**
     * 根据 ID 获取模型配置详情
     */
    AiModelConfig selectModelConfigById(Long id);

    /**
     * 根据模型名称（例如 deepseek-chat）获取模型配置
     */
    AiModelConfig selectModelConfigByModelName(String modelName);

    /**
     * 获取默认的聊天对话模型配置 (is_default = '1')
     */
    AiModelConfig selectDefaultChatModel();

    /**
     * 获取默认的向量模型配置 (is_default_embedding = '1')
     */
    AiModelConfig selectDefaultEmbeddingModel();

    /**
     * 新增模型配置
     */
    int insertModelConfig(AiModelConfig config);

    /**
     * 修改模型配置
     */
    int updateModelConfig(AiModelConfig config);



    /**
     * 重置所有模型配置的默认聊天模型状态 (将 is_default 置为 '0')
     */
    int cleanDefaultChatStatus();

    /**
     * 重置所有模型配置的默认向量模型状态 (将 is_default_embedding 置为 '0')
     */
    int cleanDefaultEmbeddingStatus();
}
