package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowConcurrencyQuota;
import org.apache.ibatis.annotations.*;

/** 基于行锁的工作流原子并发配额数据访问接口。 */
@Mapper
public interface WorkflowConcurrencyQuotaMapper extends BaseMapper<WorkflowConcurrencyQuota> {

    @Insert("INSERT IGNORE INTO ai_workflow_concurrency_quota "
            + "(tenant_id, scope_key, scope_type, max_active, active_count, create_time, update_time) "
            + "VALUES (#{tenantId}, #{scopeKey}, #{scopeType}, #{maxActive}, 0, NOW(), NOW())")
    int insertIfAbsent(
            @Param("tenantId") Long tenantId,
            @Param("scopeKey") String scopeKey,
            @Param("scopeType") String scopeType,
            @Param("maxActive") int maxActive);

    @Select("SELECT * FROM ai_workflow_concurrency_quota "
            + "WHERE scope_key = #{scopeKey} FOR UPDATE")
    WorkflowConcurrencyQuota selectByScopeForUpdate(@Param("scopeKey") String scopeKey);

    @Update("UPDATE ai_workflow_concurrency_quota SET active_count = active_count + 1, "
            + "max_active = #{maxActive} WHERE scope_key = #{scopeKey} "
            + "AND active_count < #{maxActive}")
    int reserve(@Param("scopeKey") String scopeKey, @Param("maxActive") int maxActive);

    @Update("UPDATE ai_workflow_concurrency_quota SET "
            + "active_count = GREATEST(0, active_count - 1) WHERE scope_key = #{scopeKey}")
    int release(@Param("scopeKey") String scopeKey);
}
