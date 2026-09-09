package com.polaris.ai.safety.guard.impl;

import com.polaris.ai.attachment.AttachmentTextExtractor;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.dto.PreparedAiInput;
import com.polaris.ai.safety.dto.ResolvedAttachment;
import com.polaris.ai.safety.exception.ModerationBlockedException;
import com.polaris.ai.safety.guard.IModeratedInputPreparationService;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationFacade;
import com.polaris.ai.safety.service.IPrivateAttachmentService;
import com.polaris.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 输入与附件文本提取前置安全准备服务层实现类
 *
 * @author polaris
 */
@Service
public class ModeratedInputPreparationServiceImpl implements IModeratedInputPreparationService {

    private static final Logger log = LoggerFactory.getLogger(ModeratedInputPreparationServiceImpl.class);

    @Autowired(required = false)
    private IPrivateAttachmentService attachmentService;

    @Autowired(required = false)
    private AttachmentTextExtractor textExtractor;

    @Autowired(required = false)
    private IModerationFacade moderationFacade;

    public void setAttachmentService(IPrivateAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    public void setPrivateAttachmentService(IPrivateAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    public void setTextExtractor(AttachmentTextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    public void setModerationFacade(IModerationFacade moderationFacade) {
        this.moderationFacade = moderationFacade;
    }

    @Override
    public PreparedAiInput prepare(
            ModerationScene scene,
            String message,
            List<String> tokens,
            Long userId,
            String resourceType,
            String resourceId) {

        boolean hasMessage = message != null && !message.isBlank();
        boolean hasTokens = tokens != null && !tokens.isEmpty();
        if (!hasMessage && !hasTokens) {
            throw new ServiceException("消息内容与附件不能同时为空");
        }

        List<ResolvedAttachment> attachments = (hasTokens && attachmentService != null)
                ? attachmentService.resolveOwned(tokens, userId)
                : List.of();

        StringBuilder attachmentTextBuilder = new StringBuilder();
        List<String> names = new ArrayList<>();
        for (ResolvedAttachment att : attachments) {
            names.add(att.originalName());
            String extracted = null;
            if (textExtractor != null) {
                try {
                    extracted = textExtractor.extract(att.path(), att.originalName());
                } catch (Exception e) {
                    log.error("附件文本提取失败: name={}, token={}", att.originalName(), att.token(), e);
                    throw new ServiceException("附件文本解析失败: " + att.originalName());
                }
            }
            if (extracted != null && !extracted.isBlank()) {
                attachmentTextBuilder.append("\n\n[附件内容: ").append(att.originalName()).append("]\n")
                        .append(extracted);
            }
        }

        String attachmentText = attachmentTextBuilder.toString().trim();
        String combinedText = ((hasMessage ? message.trim() : "") + (attachmentText.isEmpty() ? "" : "\n" + attachmentText)).trim();

        ModerationRequest request = ModerationRequest.of(scene, combinedText, resourceType, resourceId);
        ModerationResult moderation = moderationFacade != null
                ? moderationFacade.moderate(request)
                : new ModerationResult(com.polaris.ai.safety.model.LocalDecision.PASS, com.polaris.ai.safety.model.FinalAction.ALLOW, 0, Set.of(), List.of(), 1L, 1L, null, null);

        if (moderation.isAllowed()) {
            if (hasTokens && attachmentService != null) {
                attachmentService.markClaimed(tokens, userId);
            }
            return new PreparedAiInput(
                    hasMessage ? message.trim() : "",
                    attachmentText,
                    tokens != null ? tokens : List.of(),
                    String.join(", ", names),
                    moderation
            );
        } else {
            if (hasTokens && attachmentService != null) {
                attachmentService.markRejected(tokens, userId);
            }
            log.warn("用户输入已被安全拦截: scene={}, action={}, score={}", scene, moderation.finalAction(), moderation.riskScore());
            throw new ModerationBlockedException("输入内容包含敏感信息，已被机器安全检测拦截", moderation);
        }
    }
}
