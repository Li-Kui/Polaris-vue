package com.polaris.ai.agent.collaboration;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** 仅审计元数据，绝不携带工具参数或结果正文。 */
@Getter @Setter
public class ChatToolSourceRow {
    private Long toolId;
    private Integer callNo;
    private String invocationId,toolName,connectionName,status;
    private Date startedTime,finishedTime;
}
