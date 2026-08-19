package com.polaris.ai.safety.service.impl;

import com.polaris.ai.safety.dto.ModerationRuleRequest;
import com.polaris.ai.safety.model.RuleType;
import com.polaris.ai.safety.service.IDictionaryVersionService;
import com.polaris.ai.safety.service.IModerationRuleImportService;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 敏感词规则导入服务层实现类
 *
 * @author polaris
 */
@Service
public class ModerationRuleImportServiceImpl implements IModerationRuleImportService {

    @Autowired(required = false)
    private IDictionaryVersionService versionService;

    public void setVersionService(IDictionaryVersionService versionService) {
        this.versionService = versionService;
    }

    @Override
    public ImportPreviewResult preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }

        List<ModerationRuleRequest> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        int total = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                total++;
                if (firstLine && (line.toLowerCase().contains("ruletype") || line.toLowerCase().contains("content"))) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                List<String> parts = parseCsvLine(line);
                if (parts.size() < 2) {
                    invalid.add("行 " + total + ": 格式不符合要求 (至少需要 ruleType, content)");
                    continue;
                }

                String typeStr = parts.get(0).trim();
                String content = parts.get(1).trim();
                String category = parts.size() > 2 && !parts.get(2).trim().isEmpty() ? parts.get(2).trim() : "GENERAL";
                int weight = 40;
                if (parts.size() > 3 && !parts.get(3).trim().isEmpty()) {
                    try {
                        weight = Integer.parseInt(parts.get(3).trim());
                    } catch (NumberFormatException e) {
                        invalid.add("行 " + total + ": 权重必须为整数");
                        continue;
                    }
                }

                RuleType ruleType;
                try {
                    ruleType = RuleType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    invalid.add("行 " + total + ": 未知的规则类型 " + typeStr);
                    continue;
                }

                if (ruleType == RuleType.SAFE_CONTEXT && weight > 0) {
                    weight = -Math.abs(weight);
                } else if (ruleType == RuleType.ALLOW_TERM) {
                    weight = 0;
                } else if (ruleType == RuleType.RISK_WORD && weight <= 0) {
                    weight = 40;
                }

                valid.add(new ModerationRuleRequest(
                        null, ruleType, content, category, weight, "CONTAINS", Set.of(), true, "Bulk Import"
                ));
            }
        } catch (Exception e) {
            throw new ServiceException("解析上传文件失败: " + e.getMessage());
        }

        return new ImportPreviewResult(valid, invalid, total, valid.size(), invalid.size());
    }

    private static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if ((c == ',' || c == '\t') && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    @Override
    public int apply(Long draftVersionId, List<ModerationRuleRequest> selectedRules, String operator) {
        if (draftVersionId == null) {
            throw new ServiceException("目标草稿版本ID不能为空");
        }
        if (selectedRules == null || selectedRules.isEmpty() || versionService == null) {
            return 0;
        }
        int inserted = 0;
        for (ModerationRuleRequest req : selectedRules) {
            try {
                versionService.addRule(draftVersionId, req, operator);
                inserted++;
            } catch (Exception ignored) {
            }
        }
        return inserted;
    }
}
