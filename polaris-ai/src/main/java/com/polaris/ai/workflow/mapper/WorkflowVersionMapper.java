package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作流不可变版本数据访问接口。 */
@Mapper
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersion> {

    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM ai_workflow_version "
            + "WHERE definition_id = #{definitionId}")
    int selectNextVersionNo(@Param("definitionId") Long definitionId);

    @Select("SELECT * FROM ai_workflow_version WHERE version_id = #{versionId} LIMIT 1")
    WorkflowVersion selectByVersionId(@Param("versionId") String versionId);
}
