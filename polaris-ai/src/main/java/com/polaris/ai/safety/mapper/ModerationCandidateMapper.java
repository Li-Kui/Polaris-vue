package com.polaris.ai.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.safety.model.ModerationCandidate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModerationCandidateMapper extends BaseMapper<ModerationCandidate> {
    @Insert("""
            INSERT INTO ai_moderation_candidate
              (candidate_term, expression_hash, masked_excerpt, category, source_event_id,
               source_signal, observation_count, status, create_time, update_time)
            VALUES
              (#{candidate.candidateTerm}, #{candidate.expressionHash},
               #{candidate.maskedExcerpt}, #{candidate.category}, #{candidate.sourceEventId},
               #{candidate.sourceSignal}, #{candidate.observationCount}, #{candidate.status},
               #{candidate.createTime}, #{candidate.updateTime})
            ON DUPLICATE KEY UPDATE
              masked_excerpt = VALUES(masked_excerpt),
              source_signal = CASE
                WHEN source_signal = 'PROVIDER_HIGH_RISK' THEN source_signal
                WHEN #{candidate.sourceSignal} = 'PROVIDER_HIGH_RISK'
                THEN 'PROVIDER_HIGH_RISK'
                ELSE source_signal END,
              status = CASE
                WHEN status IN ('ACCEPTED', 'REJECTED') THEN status
                WHEN status = 'PENDING' THEN 'PENDING'
                WHEN observation_count + 1 >= #{observationThreshold}
                THEN 'PENDING' ELSE 'OBSERVING' END,
              observation_count = observation_count + 1,
              update_time = VALUES(update_time)
            """)
    int observe(@Param("candidate") ModerationCandidate candidate,
                @Param("observationThreshold") int observationThreshold);
}
