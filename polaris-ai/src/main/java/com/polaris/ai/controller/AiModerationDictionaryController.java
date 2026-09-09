package com.polaris.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.safety.dto.CandidateBatchRequest;
import com.polaris.ai.safety.dto.ModerationRuleRequest;
import com.polaris.ai.safety.mapper.ModerationCandidateMapper;
import com.polaris.ai.safety.model.ModerationCandidate;
import com.polaris.ai.safety.model.ModerationRule;
import com.polaris.ai.safety.service.IDictionaryVersionService;
import com.polaris.ai.safety.service.IModerationRuleImportService;
import com.polaris.ai.safety.service.IModerationRuleImportService.ImportPreviewResult;
import com.polaris.ai.safety.vo.DictionaryVersionDiffView;
import com.polaris.ai.safety.vo.DictionaryVersionView;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.TableDataInfo;
import com.polaris.common.enums.BusinessType;
import com.polaris.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "AI敏感内容词库管理")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/moderation")
public class AiModerationDictionaryController extends BaseController {

    @Autowired
    private IDictionaryVersionService versionService;

    @Autowired
    private IModerationRuleImportService importService;

    @Autowired
    private ModerationCandidateMapper candidateMapper;

    public void setVersionService(IDictionaryVersionService versionService) {
        this.versionService = versionService;
    }

    public void setImportService(IModerationRuleImportService importService) {
        this.importService = importService;
    }

    public void setCandidateMapper(ModerationCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }


    @Operation(summary = "获取词库版本列表")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:list')")
    @GetMapping("/dictionary/versions")
    public ResultData<List<DictionaryVersionView>> listVersions() {
        return ok(versionService.listVersionViews());
    }

    @Operation(summary = "创建草稿版本")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:edit')")
    @Log(title = "AI安全词库", businessType = BusinessType.INSERT)
    @PostMapping("/dictionary/drafts")
    public ResultData<Long> createDraft(@RequestParam(required = false) Long baseVersionId) {
        String username = getSafeUsername();
        return ok(versionService.getOrCreateDraft(baseVersionId, username));
    }

    @Operation(summary = "查询指定版本的规则列表（支持分页）")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:list')")
    @GetMapping("/dictionary/versions/{id}/rules")
    public TableDataInfo listRules(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String category) {
        startPage();
        List<ModerationRule> list = versionService.listRules(id, keyword, ruleType, category);
        return getDataTable(list);
    }

    @Operation(summary = "对比词库版本差异")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:list')")
    @GetMapping("/dictionary/versions/{id}/diff")
    public ResultData<DictionaryVersionDiffView> diffVersion(
            @PathVariable Long id,
            @RequestParam(required = false) Long baseVersionId) {
        return ok(versionService.diffVersion(id, baseVersionId));
    }

    @Operation(summary = "导出词库规则文件")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:export')")
    @GetMapping("/dictionary/versions/{id}/export")
    public void exportRules(@PathVariable Long id, HttpServletResponse response) {
        versionService.exportRules(id, response);
    }

    @Operation(summary = "向草稿版本添加规则")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:edit')")
    @Log(title = "AI安全词库", businessType = BusinessType.INSERT)
    @PostMapping("/dictionary/versions/{id}/rules")
    public ResultData<ModerationRule> addRule(
            @PathVariable Long id,
            @Validated @RequestBody ModerationRuleRequest request) {
        String username = getSafeUsername();
        return ok(versionService.addRule(id, request, username));
    }

    @Operation(summary = "修改草稿版本中的规则")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:edit')")
    @Log(title = "AI安全词库", businessType = BusinessType.UPDATE)
    @PutMapping("/dictionary/versions/{id}/rules/{ruleId}")
    public ResultData<ModerationRule> updateRule(
            @PathVariable Long id,
            @PathVariable Long ruleId,
            @Validated @RequestBody ModerationRuleRequest request) {
        String username = getSafeUsername();
        return ok(versionService.updateRule(id, ruleId, request, username));
    }

    @Operation(summary = "删除草稿版本中的规则")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:edit')")
    @Log(title = "AI安全词库", businessType = BusinessType.DELETE)
    @DeleteMapping("/dictionary/versions/{id}/rules/{ruleId}")
    public ResultData<Void> deleteRule(@PathVariable Long id, @PathVariable Long ruleId) {
        String username = getSafeUsername();
        versionService.deleteRule(id, ruleId, username);
        return ok();
    }

    @Operation(summary = "发布词库版本")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:publish')")
    @Log(title = "AI安全词库发布", businessType = BusinessType.UPDATE)
    @PostMapping("/dictionary/versions/{id}/publish")
    public ResultData<Void> publishVersion(@PathVariable Long id) {
        String username = getSafeUsername();
        versionService.publish(id, username);
        return ok();
    }

    @Operation(summary = "回滚至历史词库版本")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:rollback')")
    @Log(title = "AI安全词库回滚", businessType = BusinessType.UPDATE)
    @PostMapping("/dictionary/versions/{id}/rollback")
    public ResultData<Void> rollbackVersion(@PathVariable Long id) {
        String username = getSafeUsername();
        versionService.rollback(id, username);
        return ok();
    }

    @Operation(summary = "导入规则预览")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:import')")
    @PostMapping("/dictionary/import/preview")
    public ResultData<ImportPreviewResult> previewImport(@RequestParam("file") MultipartFile file) {
        return ok(importService.preview(file));
    }

    @Operation(summary = "应用导入规则到草稿版本")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:import')")
    @Log(title = "AI安全词库批量导入", businessType = BusinessType.INSERT)
    @PostMapping("/dictionary/import/apply")
    public ResultData<Integer> applyImport(
            @RequestParam Long draftVersionId,
            @RequestBody List<ModerationRuleRequest> rules) {
        String username = getSafeUsername();
        return ok(importService.apply(draftVersionId, rules, username));
    }

    @Operation(summary = "获取候选敏感词列表")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:list')")
    @GetMapping("/candidates")
    public ResultData<List<ModerationCandidate>> listCandidates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        if (candidateMapper == null) {
            return ok(List.of());
        }
        LambdaQueryWrapper<ModerationCandidate> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(ModerationCandidate::getStatus, status.trim());
        }
        if (category != null && !category.isBlank()) {
            query.eq(ModerationCandidate::getCategory, category.trim());
        }
        query.orderByDesc(ModerationCandidate::getObservationCount);
        return ok(candidateMapper.selectList(query));
    }

    @Operation(summary = "批量采纳候选词到草稿版本")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:edit')")
    @Log(title = "AI候选词批量采纳", businessType = BusinessType.INSERT)
    @PostMapping("/candidates/batch-accept")
    public ResultData<Void> batchAccept(@Validated @RequestBody CandidateBatchRequest request) {
        String username = getSafeUsername();
        versionService.batchAcceptCandidates(request, username);
        return ok();
    }

    @Operation(summary = "批量拒绝候选词")
    @PreAuthorize("@ss.hasPermi('ai:moderation:dictionary:edit')")
    @Log(title = "AI候选词批量拒绝", businessType = BusinessType.UPDATE)
    @PostMapping("/candidates/batch-reject")
    public ResultData<Void> batchReject(@Validated @RequestBody CandidateBatchRequest request) {
        String username = getSafeUsername();
        versionService.batchRejectCandidates(request, username);
        return ok();
    }

    private String getSafeUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "admin";
        }
    }
}
