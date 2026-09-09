package com.polaris.ai.safety.guard;

import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.model.FinalAction;
import com.polaris.ai.safety.model.LocalDecision;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationFacade;
import com.polaris.ai.safety.service.IModerationPolicyService;
import com.polaris.ai.safety.service.IQuarantineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Component
public class KnowledgeModerationGuard {

    @Autowired(required = false)
    private IQuarantineService quarantineService;

    @Autowired(required = false)
    private AiDocumentMapper documentMapper;

    @Autowired(required = false)
    private IModerationPolicyService policyService;

    public void setQuarantineService(IQuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    public void setDocumentMapper(AiDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public void setPolicyService(IModerationPolicyService policyService) {
        this.policyService = policyService;
    }

    /**
     * 长文档滑动窗口切片分段安全检测
     */
    public ModerationResult moderateContent(
            AiDocument doc,
            String content,
            IModerationFacade moderationFacade) {

        if (content == null || content.isBlank() || moderationFacade == null) {
            return new ModerationResult(
                    LocalDecision.PASS, FinalAction.ALLOW, 0,
                    Set.of(), List.of(), 1L, 1L, null, null
            );
        }

        int segmentChars = 1000;
        int overlapChars = 100;
        if (policyService != null) {
            try {
                ModerationPolicy policy = policyService.resolve(ModerationScene.KNOWLEDGE);
                if (policy != null) {
                    if (policy.getSegmentChars() != null && policy.getSegmentChars() > 0) {
                        segmentChars = policy.getSegmentChars();
                    }
                    if (policy.getSegmentOverlapChars() != null && policy.getSegmentOverlapChars() >= 0) {
                        overlapChars = policy.getSegmentOverlapChars();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (content.length() <= segmentChars) {
            return moderationFacade.moderate(ModerationRequest.of(
                    ModerationScene.KNOWLEDGE,
                    content,
                    "AI_DOCUMENT",
                    doc != null && doc.getId() != null ? doc.getId().toString() : "0"
            ));
        }

        int step = Math.max(1, segmentChars - overlapChars);
        ModerationResult worstResult = null;
        int maxRiskScore = -1;

        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(content.length(), start + segmentChars);
            String chunk = content.substring(start, end);
            ModerationResult result = moderationFacade.moderate(ModerationRequest.of(
                    ModerationScene.KNOWLEDGE,
                    chunk,
                    "AI_DOCUMENT",
                    doc != null && doc.getId() != null ? doc.getId().toString() : "0"
            ));

            if (!result.isAllowed()) {
                return result;
            }

            if (result.riskScore() > maxRiskScore) {
                maxRiskScore = result.riskScore();
                worstResult = result;
            }

            if (end == content.length()) {
                break;
            }
        }

        return worstResult != null ? worstResult : moderationFacade.moderate(ModerationRequest.of(
                ModerationScene.KNOWLEDGE,
                content.substring(0, Math.min(content.length(), segmentChars)),
                "AI_DOCUMENT",
                doc != null && doc.getId() != null ? doc.getId().toString() : "0"
        ));
    }

    /**
     * 将检测结果应用到知识库文档实体
     *
     * @param doc        文档实体
     * @param moderation 检测结果
     * @param sourceFile 源文件路径
     */
    public void apply(AiDocument doc, ModerationResult moderation, Path sourceFile) {
        if (doc == null || moderation == null) {
            return;
        }

        if (moderation.isAllowed()) {
            doc.setModerationStatus("SAFE");
            doc.setModerationVersion(moderation.dictionaryVersion());
            documentMapper.updateById(doc);
        } else {
            quarantineService.quarantine(doc, moderation, sourceFile);
        }
    }

    public void apply(AiDocument doc, ModerationResult moderation) {
        Path sourcePath = null;
        if (doc != null && doc.getFileUrl() != null && !doc.getFileUrl().isBlank()) {
            sourcePath = Path.of(doc.getFileUrl());
        }
        apply(doc, moderation, sourcePath);
    }
}
