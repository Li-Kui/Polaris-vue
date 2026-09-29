package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiProviderConnection;
import com.polaris.common.annotation.DataScope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** AI Provider 连接配置数据访问层。 */
public interface AiProviderConnectionMapper extends BaseMapper<AiProviderConnection> {

    @DataScope(deptAlias = "c")
    @Select({"<script>",
            "SELECT c.* FROM ai_provider_connection c",
            "<where>",
            "c.del_flag = '0'",
            "<if test='connectionName != null and connectionName != \"\"'>",
            "AND c.connection_name LIKE CONCAT('%', #{connectionName}, '%')",
            "</if>",
            "<if test='providerCode != null and providerCode != \"\"'>",
            "AND c.provider_code = #{providerCode}",
            "</if>",
            "<if test='protocolCode != null and protocolCode != \"\"'>",
            "AND c.protocol_code = #{protocolCode}",
            "</if>",
            "<if test='networkMode != null and networkMode != \"\"'>",
            "AND c.network_mode = #{networkMode}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND c.status = #{status}",
            "</if>",
            "${params.dataScope}",
            "</where>",
            "ORDER BY c.create_time DESC, c.id DESC",
            "</script>"})
    List<AiProviderConnection> selectConnectionList(AiProviderConnection query);

    @DataScope(deptAlias = "c")
    @Select({"<script>",
            "SELECT c.* FROM ai_provider_connection c",
            "WHERE c.id = #{id} AND c.del_flag = '0'",
            "${params.dataScope}",
            "LIMIT 1",
            "</script>"})
    AiProviderConnection selectAccessibleById(AiProviderConnection query);

    @Update("UPDATE ai_provider_connection SET credential_ciphertext = #{ciphertext}, "
            + "update_by = #{operator}, update_time = NOW() "
            + "WHERE id = #{id} AND revision = 1 AND del_flag = '0'")
    int updateCredentialAfterCreate(
            @Param("id") Long id,
            @Param("ciphertext") String ciphertext,
            @Param("operator") String operator);

    @Update({"<script>",
            "UPDATE ai_provider_connection",
            "SET connection_name = #{connectionName},",
            "network_mode = #{networkMode}, base_url = #{baseUrl},",
            "<if test='credentialWrite'>",
            "credential_ciphertext = #{credentialCiphertext},",
            "</if>",
            "remark = #{remark}, update_by = #{operator}, update_time = NOW(),",
            "revision = revision + 1",
            "WHERE id = #{id} AND revision = #{expectedRevision} AND del_flag = '0'",
            "</script>"})
    int updateMutableFields(
            @Param("id") Long id,
            @Param("connectionName") String connectionName,
            @Param("networkMode") String networkMode,
            @Param("baseUrl") String baseUrl,
            @Param("credentialWrite") boolean credentialWrite,
            @Param("credentialCiphertext") String credentialCiphertext,
            @Param("remark") String remark,
            @Param("operator") String operator,
            @Param("expectedRevision") Long expectedRevision);

    @Update("UPDATE ai_provider_connection SET status = #{status}, "
            + "update_by = #{operator}, update_time = NOW(), revision = revision + 1 "
            + "WHERE id = #{id} AND revision = #{expectedRevision} AND del_flag = '0'")
    int updateStatusByRevision(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("operator") String operator,
            @Param("expectedRevision") Long expectedRevision);

    @Update("UPDATE ai_provider_connection SET del_flag = '2', "
            + "update_by = #{operator}, update_time = NOW(), revision = revision + 1 "
            + "WHERE id = #{id} AND revision = #{expectedRevision} AND del_flag = '0'")
    int deleteByRevision(
            @Param("id") Long id,
            @Param("operator") String operator,
            @Param("expectedRevision") Long expectedRevision);
}
