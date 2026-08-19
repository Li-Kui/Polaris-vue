package com.polaris.ai.safety.guard;

import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.exception.ModerationBlockedException;
import com.polaris.ai.safety.model.FinalAction;
import com.polaris.ai.safety.model.LocalDecision;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class WorkflowModerationSink {

    private static final Logger log = LoggerFactory.getLogger(WorkflowModerationSink.class);

    @Autowired(required = false)
    private IModerationFacade moderationFacade;

    public void setModerationFacade(IModerationFacade moderationFacade) {
        this.moderationFacade = moderationFacade;
    }

    /**
     * 校验工作流输入参数与用户指令
     */
    public ModerationResult moderateInput(String text, String executionId) {
        if (text == null || text.isBlank() || moderationFacade == null) {
            return new ModerationResult(
                    LocalDecision.PASS, FinalAction.ALLOW, 0,
                    Set.of(), List.of(), 1L, 1L, null, null
            );
        }

        ModerationRequest request = ModerationRequest.of(
                ModerationScene.WORKFLOW_INPUT,
                text,
                "WORKFLOW_INPUT",
                executionId
        );

        ModerationResult moderation = moderationFacade.moderate(request);
        if (!moderation.isAllowed()) {
            log.warn("工作流输入触发安全拦截: executionId={}, action={}, score={}",
                    executionId, moderation.finalAction(), moderation.riskScore());
            throw new ModerationBlockedException("工作流输入内容包含敏感信息，已被机器安全检测拦截", moderation);
        }

        return moderation;
    }

    /**
     * 校验工作流中节点产生并在用户端可见的输出文本
     */
    public ModerationResult moderate(String text, String executionId, String nodeId) {
        if (text == null || text.isBlank() || moderationFacade == null) {
            return new ModerationResult(
                    LocalDecision.PASS, FinalAction.ALLOW, 0,
                    Set.of(), List.of(), 1L, 1L, null, null
            );
        }

        ModerationRequest request = ModerationRequest.of(
                ModerationScene.WORKFLOW_OUTPUT,
                text,
                "WORKFLOW_OUTPUT",
                executionId
        );

        ModerationResult moderation = moderationFacade.moderate(request);
        if (!moderation.isAllowed()) {
            log.warn("工作流节点输出触发安全拦截: executionId={}, node={}, action={}, score={}",
                    executionId, nodeId, moderation.finalAction(), moderation.riskScore());
            throw new ModerationBlockedException("工作流节点产生违规内容，已被机器安全检测拦截", moderation);
        }

        return moderation;
    }
}
