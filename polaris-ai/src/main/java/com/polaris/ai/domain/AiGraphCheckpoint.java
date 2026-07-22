package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图工作流执行检查点实体类
 * 对应数据库表 ai_graph_checkpoint
 * <p>
 * 一期建表但暂不使用，二期 MySQL Checkpoint 持久化时启用
 *
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_graph_checkpoint")
@Schema(description = "图工作流执行检查点")
public class AiGraphCheckpoint extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "会话线程ID")
    private String threadId;

    @Schema(description = "工作流编码")
    private String workflowCode;

    @Schema(description = "checkpoint唯一ID")
    private String checkpointId;

    @Schema(description = "父checkpoint ID")
    private String parentCheckpointId;

    @Schema(description = "序列化的AgentState纯数据JSON")
    private String stateJson;

    @Schema(description = "checkpoint元数据JSON")
    private String metadataJson;

    @Schema(description = "中间写入操作JSON")
    private String writesJson;

    @Schema(description = "状态：running/paused/done/error")
    private String status;
}
