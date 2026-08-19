package com.polaris.ai.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.safety.model.ModerationRule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModerationRuleMapper extends BaseMapper<ModerationRule> {

    @Insert("""
        INSERT INTO moderation_rule (dictionary_version_id, rule_type, content, normalized_content, normalized_hash, category, weight, source, create_time)
        SELECT #{targetVersionId}, rule_type, content, normalized_content, normalized_hash, category, weight, source, NOW()
        FROM moderation_rule WHERE dictionary_version_id = #{sourceVersionId}
    """)
    int copyRulesFromVersion(@Param("sourceVersionId") Long sourceVersionId,
                             @Param("targetVersionId") Long targetVersionId);
}
