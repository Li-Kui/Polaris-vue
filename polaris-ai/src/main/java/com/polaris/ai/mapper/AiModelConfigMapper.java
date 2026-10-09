package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiModelConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** AI 模型稳定身份数据访问层。 */
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    @Update("""
            UPDATE ai_model_config
            SET name = #{name}, model_code = #{modelCode},
                connection_id = #{connectionId}, model_name = #{modelName},
                model_type = #{modelType}, description = #{description},
                dept_id = #{deptId}, status = #{status},
                revision = revision + 1,
                update_by = #{updateBy}, update_time = NOW()
            WHERE id = #{id} AND revision = #{expectedRevision}
              AND del_flag = '0'
            """)
    int updateAggregateBase(
            @Param("id") Long id,
            @Param("expectedRevision") Long expectedRevision,
            @Param("name") String name,
            @Param("modelCode") String modelCode,
            @Param("connectionId") Long connectionId,
            @Param("modelName") String modelName,
            @Param("modelType") String modelType,
            @Param("description") String description,
            @Param("deptId") Long deptId,
            @Param("status") String status,
            @Param("updateBy") String updateBy);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT id, tenant_id, dept_id, name, model_code, connection_id,",
            "model_name, model_type, description, revision, status, del_flag,",
            "create_by, create_time, update_by, update_time, remark",
            "FROM ai_model_config WHERE id = #{id} AND del_flag = '0'",
            "<choose><when test='tenantId != null'>AND tenant_id = #{tenantId}</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "LIMIT 1", "</script>"})
    AiModelConfig selectWorkflowResource(
            @Param("tenantId") Long tenantId, @Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT DISTINCT m.id, m.tenant_id, m.dept_id, m.name,",
            "m.model_code, m.connection_id, m.model_name, m.model_type,",
            "m.description, m.revision, m.status, m.del_flag,",
            "m.create_by, m.create_time, m.update_by, m.update_time, m.remark",
            "FROM ai_model_config m",
            "JOIN ai_model_capability c ON c.model_config_id = m.id",
            "AND c.capability_code = #{capabilityCode}",
            "AND c.applies_to_capability_code = '' AND c.enabled = '1'",
            "WHERE m.del_flag = '0' AND m.status = '1'",
            "<choose><when test='tenantId != null'>AND m.tenant_id = #{tenantId}</when>",
            "<otherwise>AND m.tenant_id IS NULL</otherwise></choose>",
            "ORDER BY m.name, m.id", "</script>"})
    List<AiModelConfig> selectWorkflowResourcesByCapability(
            @Param("tenantId") Long tenantId,
            @Param("capabilityCode") String capabilityCode);

    List<AiModelConfig> selectModelConfigList(AiModelConfig config);

    List<AiModelConfig> selectAvailableModelConfigs(
            @Param("deptId") Long deptId,
            @Param("isAdmin") Boolean isAdmin);

    List<AiModelConfig> selectAvailableModelConfigsByCapability(
            @Param("capabilityCode") String capabilityCode,
            @Param("deptId") Long deptId,
            @Param("isAdmin") Boolean isAdmin);

    AiModelConfig selectModelConfigById(Long id);
}
