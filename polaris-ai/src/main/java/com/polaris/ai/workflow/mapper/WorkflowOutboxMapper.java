package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 基于领取机制、至少投递一次的工作流发件箱数据访问接口。 */
@Mapper
public interface WorkflowOutboxMapper extends BaseMapper<WorkflowOutbox> {

    @Select("SELECT id FROM ai_workflow_outbox WHERE attempt_count < 10 AND ("
            + "publish_status = 'PENDING' "
            + "OR (publish_status = 'FAILED' AND next_retry_time <= NOW()) "
            + "OR (publish_status = 'PUBLISHING' AND claim_until < NOW())) "
            + "ORDER BY id LIMIT #{limit}")
    List<Long> selectPublishCandidates(@Param("limit") int limit);

    @Update("UPDATE ai_workflow_outbox SET publish_status = 'PUBLISHING', "
            + "claimed_by = #{publisherId}, claim_until = DATE_ADD(NOW(), INTERVAL 30 SECOND), "
            + "attempt_count = attempt_count + 1 WHERE id = #{id} AND attempt_count < 10 AND ("
            + "publish_status = 'PENDING' "
            + "OR (publish_status = 'FAILED' AND next_retry_time <= NOW()) "
            + "OR (publish_status = 'PUBLISHING' AND claim_until < NOW()))")
    int claim(@Param("id") Long id, @Param("publisherId") String publisherId);

    @Update("UPDATE ai_workflow_outbox SET publish_status = 'PUBLISHED', "
            + "published_time = NOW(), claimed_by = NULL, claim_until = NULL "
            + "WHERE id = #{id} AND claimed_by = #{publisherId} AND publish_status = 'PUBLISHING'")
    int markPublished(@Param("id") Long id, @Param("publisherId") String publisherId);

    @Update("UPDATE ai_workflow_outbox SET publish_status = 'FAILED', "
            + "next_retry_time = DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND), "
            + "claimed_by = NULL, claim_until = NULL "
            + "WHERE id = #{id} AND claimed_by = #{publisherId} AND publish_status = 'PUBLISHING'")
    int markFailed(
            @Param("id") Long id,
            @Param("publisherId") String publisherId,
            @Param("delaySeconds") int delaySeconds);
}
