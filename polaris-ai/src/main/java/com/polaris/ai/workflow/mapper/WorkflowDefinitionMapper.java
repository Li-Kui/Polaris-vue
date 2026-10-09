package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 工作流定义及草稿并发控制的数据访问接口。 */
@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinition> {

    @Select("SELECT * FROM ai_workflow_definition WHERE id = #{id} AND del_flag = '0' FOR UPDATE")
    WorkflowDefinition selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE ai_workflow_definition SET default_page_type = #{defaultPageType,jdbcType=VARCHAR}, "
            + "share_page_config_json = #{sharePageConfigJson,jdbcType=VARCHAR}, "
            + "update_by = #{updateBy}, update_time = NOW(), lock_version = lock_version + 1 "
            + "WHERE id = #{id} AND lock_version = #{expectedLockVersion} AND del_flag = '0'")
    int updateShareDefaults(
            @Param("id") Long id,
            @Param("expectedLockVersion") Integer expectedLockVersion,
            @Param("defaultPageType") String defaultPageType,
            @Param("sharePageConfigJson") String sharePageConfigJson,
            @Param("updateBy") String updateBy);

    @Update("UPDATE ai_workflow_definition SET workflow_name = #{workflowName}, "
            + "description = #{description}, tags_json = #{tagsJson}, "
            + "draft_schema_version = #{draftSchemaVersion}, draft_json = #{draftJson}, "
            + "draft_revision = draft_revision + 1, update_by = #{updateBy}, update_time = NOW(), "
            + "lock_version = lock_version + 1 "
            + "WHERE id = #{id} AND draft_revision = #{expectedRevision} AND del_flag = '0'")
    int updateDraft(
            @Param("id") Long id,
            @Param("expectedRevision") Long expectedRevision,
            @Param("workflowName") String workflowName,
            @Param("description") String description,
            @Param("tagsJson") String tagsJson,
            @Param("draftSchemaVersion") String draftSchemaVersion,
            @Param("draftJson") String draftJson,
            @Param("updateBy") String updateBy);
}
