package com.polaris.ai.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.safety.model.ModerationDictionaryVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModerationDictionaryVersionMapper extends BaseMapper<ModerationDictionaryVersion> {
    @Select("""
            SELECT id, version_no, checksum, status, source_version, source_location,
                   published_by, published_time, create_time, update_time
            FROM ai_moderation_dictionary_version
            ORDER BY id
            FOR UPDATE
            """)
    List<ModerationDictionaryVersion> findAllForUpdate();
}
