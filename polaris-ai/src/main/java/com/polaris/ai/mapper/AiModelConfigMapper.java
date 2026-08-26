package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiModelConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 模型配置数据访问层
 * 
 * @author polaris
 */
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig>
{
    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT * FROM ai_model_config WHERE id = #{id} AND del_flag = '0'",
            "<choose><when test='tenantId != null'>AND (tenant_id = #{tenantId} OR tenant_id IS NULL)</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "LIMIT 1", "</script>"})
    AiModelConfig selectWorkflowResource(
            @Param("tenantId") Long tenantId, @Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT * FROM ai_model_config WHERE del_flag = '0'",
            "<choose><when test='tenantId != null'>AND (tenant_id = #{tenantId} OR tenant_id IS NULL)</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "ORDER BY CASE WHEN tenant_id IS NULL THEN 1 ELSE 0 END, name, id",
            "</script>"})
    List<AiModelConfig> selectWorkflowResources(@Param("tenantId") Long tenantId);

    /**
     * 按模型名称查询当前工作流作用域内优先级最高的聊天模型。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT * FROM ai_model_config",
            "WHERE model_name = #{modelName} AND model_type = 'CHAT'",
            "AND status = '1' AND del_flag = '0'",
            "<choose><when test='tenantId != null'>AND (tenant_id = #{tenantId} OR tenant_id IS NULL)</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "<choose><when test='tenantId != null'>",
            "ORDER BY CASE WHEN tenant_id = #{tenantId} THEN 0 ELSE 1 END, is_default DESC, id DESC",
            "</when><otherwise>ORDER BY is_default DESC, id DESC</otherwise></choose>",
            "LIMIT 1", "</script>"})
    AiModelConfig selectWorkflowResourceByModelName(
            @Param("tenantId") Long tenantId, @Param("modelName") String modelName);

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
     * 查询当前用户可用的指定类型模型。
     */
    List<AiModelConfig> selectAvailableModelConfigsByType(
            @org.apache.ibatis.annotations.Param("modelType") String modelType,
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
    AiModelConfig selectDefaultChatModel(
            @org.apache.ibatis.annotations.Param("userDeptId") Long userDeptId,
            @org.apache.ibatis.annotations.Param("dataScopeSql") String dataScopeSql);

    /**
     * 获取默认的向量模型配置 (is_default_embedding = '1')
     */
    AiModelConfig selectDefaultEmbeddingModel(
            @org.apache.ibatis.annotations.Param("userDeptId") Long userDeptId,
            @org.apache.ibatis.annotations.Param("dataScopeSql") String dataScopeSql);

    /**
     * 获取指定类型下的默认模型配置 (is_default = '1')
     */
    AiModelConfig selectDefaultModel(
            @org.apache.ibatis.annotations.Param("modelType") String modelType,
            @org.apache.ibatis.annotations.Param("userDeptId") Long userDeptId,
            @org.apache.ibatis.annotations.Param("dataScopeSql") String dataScopeSql);

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
    int cleanDefaultChatStatus(@org.apache.ibatis.annotations.Param("deptId") Long deptId);

    /**
     * 重置所有模型配置的默认向量模型状态 (将 is_default_embedding 置为 '0')
     */
    int cleanDefaultEmbeddingStatus(@org.apache.ibatis.annotations.Param("deptId") Long deptId);

    /**
     * 清除指定类型下的默认模型状态
     */
    int cleanDefaultStatus(
            @org.apache.ibatis.annotations.Param("modelType") String modelType,
            @org.apache.ibatis.annotations.Param("deptId") Long deptId);
}
