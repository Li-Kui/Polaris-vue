package com.polaris.ai.safety.service;

import com.polaris.ai.safety.dto.ModerationRuleRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI 敏感词规则导入服务层接口
 *
 * @author polaris
 */
public interface IModerationRuleImportService {

    record ImportPreviewResult(
            @Schema(description = "解析有效规则列表")
            List<ModerationRuleRequest> validRules,
            @Schema(description = "格式错误行说明列表")
            List<String> invalidRows,
            @Schema(description = "总行数")
            int totalCount,
            @Schema(description = "有效规则数")
            int validCount,
            @Schema(description = "无效规则数")
            int invalidCount
    ) {}

    ImportPreviewResult preview(MultipartFile file);

    int apply(Long draftVersionId, List<ModerationRuleRequest> selectedRules, String operator);
}
