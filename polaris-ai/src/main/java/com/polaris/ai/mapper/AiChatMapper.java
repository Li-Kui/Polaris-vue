package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiConversation;
import com.polaris.ai.domain.AiMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 对话数据访问层
 * 对应 mapper/ai/AiChatMapper.xml
 * 提供会话和消息的增删改查操作
 * 
 * @author polaris
 */
public interface AiChatMapper extends BaseMapper<AiConversation>
{
    // ================================================================
    //  会话相关操作
    // ================================================================

    /**
     * 新增会话
     * 使用 useGeneratedKeys 自动回填主键 id 到实体对象
     *
     * @param conversation 会话实体（title、userId、model 必填）
     * @return 影响行数
     */
    int insertConversation(AiConversation conversation);

    /**
     * 修改会话标题
     * 同时校验 userId，防止越权修改其他用户的会话
     *
     * @param id     会话 ID
     * @param title  新标题
     * @param userId 当前登录用户 ID
     * @return 影响行数
     */
    int updateConversationTitle(@Param("id") Long id, @Param("title") String title, @Param("userId") Long userId);

    /**
     * 更新会话配置大模型与绑定的知识库
     *
     * @param id              会话 ID
     * @param model           大模型名称
     * @param knowledgeBaseId 知识库 ID
     * @param userId          用户 ID
     */
    int updateConversationConfig(@Param("id") Long id, @Param("model") String model, @Param("knowledgeBaseId") Long knowledgeBaseId, @Param("userId") Long userId);

    /**
     * 逻辑删除会话（将 status 置为 0，不物理删除）
     * 同时校验 userId，防止越权删除其他用户的会话
     *
     * @param id     会话 ID
     * @param userId 当前登录用户 ID
     * @return 影响行数
     */
    int deleteConversation(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 查询用户的所有会话列表
     * 只返回 status=1 的正常会话，按 update_time 倒序，最多返回 50 条
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    List<AiConversation> selectConversationsByUserId(Long userId);

    /**
     * 根据会话 ID 查询单个会话
     * 同时校验 userId，防止越权查看其他用户的会话
     *
     * @param id     会话 ID
     * @param userId 当前登录用户 ID
     * @return 会话实体，不存在或无权限时返回 null
     */
    AiConversation selectConversationById(@Param("id") Long id, @Param("userId") Long userId);

    // ================================================================
    //  消息相关操作
    // ================================================================

    /**
     * 新增一条消息记录
     * role 为 user 时记录用户输入，role 为 assistant 时记录 AI 回复
     *
     * @param message 消息实体（conversationId、role、content 必填）
     * @return 影响行数
     */
    int insertMessage(AiMessage message);

    /**
     * 查询指定会话下的所有消息
     * 按 id 升序排列（即时间正序），最多返回 100 条
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    List<AiMessage> selectMessagesByConversationId(Long conversationId);

    /**
     * 物理删除指定会话下的所有消息
     * 通常在删除会话时级联调用，先删消息再删会话
     *
     * @param conversationId 会话 ID
     * @return 影响行数
     */
    int deleteMessagesByConversationId(Long conversationId);
}
