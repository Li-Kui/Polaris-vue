package com.polaris.ai.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.safety.model.ModerationPolicy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModerationPolicyMapper extends BaseMapper<ModerationPolicy> {
}
