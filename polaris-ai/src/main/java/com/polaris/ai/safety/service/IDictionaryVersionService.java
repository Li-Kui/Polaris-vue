package com.polaris.ai.safety.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.safety.dto.CandidateBatchRequest;
import com.polaris.ai.safety.dto.ModerationRuleRequest;
import com.polaris.ai.safety.model.ModerationDictionaryVersion;
import com.polaris.ai.safety.model.ModerationRule;
import com.polaris.ai.safety.rule.DictionarySnapshotManager;
import com.polaris.ai.safety.vo.DictionaryVersionDiffView;
import com.polaris.ai.safety.vo.DictionaryVersionView;

import java.util.List;

/**
 * AI 敏感词库版本与规则管理服务层接口
 *
 * @author polaris
 */
public interface IDictionaryVersionService extends IService<ModerationDictionaryVersion>, DictionarySnapshotManager.SnapshotLoader {

    List<ModerationDictionaryVersion> findPublished();

    boolean checksumExists(String checksum);

    List<DictionaryVersionView> listVersionViews();

    Long getOrCreateDraft(Long baseVersionId, String operator);

    List<ModerationRule> listRules(Long versionId, String keyword, String ruleType, String category);

    ModerationRule addRule(Long versionId, ModerationRuleRequest req, String operator);

    ModerationRule updateRule(Long versionId, Long ruleId, ModerationRuleRequest req, String operator);

    void deleteRule(Long versionId, Long ruleId, String operator);

    void batchAcceptCandidates(CandidateBatchRequest req, String operator);

    void batchRejectCandidates(CandidateBatchRequest req, String operator);

    DictionaryVersionDiffView diffVersion(Long targetVersionId, Long baseVersionId);

    void exportRules(Long versionId, jakarta.servlet.http.HttpServletResponse response);

    ImportOutcome importSeed(DictionarySeedSource.SeedBundle seed, boolean publishIfNoPublished);

    PublicationOutcome publish(long versionId, String operator);

    PublicationOutcome rollback(long versionId, String operator);

    record ImportOutcome(Long versionId, boolean imported, boolean published) {}

    record PublicationOutcome(long versionId, boolean changed) {}
}
