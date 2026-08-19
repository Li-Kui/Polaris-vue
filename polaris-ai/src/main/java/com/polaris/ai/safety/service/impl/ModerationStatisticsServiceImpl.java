package com.polaris.ai.safety.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.safety.mapper.ModerationCandidateMapper;
import com.polaris.ai.safety.mapper.ModerationEventMapper;
import com.polaris.ai.safety.model.ModerationCandidate;
import com.polaris.ai.safety.model.ModerationEvent;
import com.polaris.ai.safety.service.IModerationStatisticsService;
import com.polaris.ai.safety.vo.ModerationSummaryView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AI 敏感内容安全统计与告警服务层实现类
 *
 * @author polaris
 */
@Service
public class ModerationStatisticsServiceImpl implements IModerationStatisticsService {

    @Autowired(required = false)
    private ModerationEventMapper eventMapper;

    @Autowired(required = false)
    private AiDocumentMapper documentMapper;

    @Autowired(required = false)
    private ModerationCandidateMapper candidateMapper;

    public void setEventMapper(ModerationEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    public void setDocumentMapper(AiDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public void setCandidateMapper(ModerationCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    @Override
    public ModerationSummaryView getSummary(Integer days) {
        int windowDays = (days != null && days > 0) ? days : 7;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -windowDays);
        Date since = cal.getTime();

        List<ModerationEvent> events = List.of();
        if (eventMapper != null) {
            events = eventMapper.selectList(new LambdaQueryWrapper<ModerationEvent>()
                    .ge(ModerationEvent::getCreateTime, since)
                    .orderByAsc(ModerationEvent::getCreateTime));
        }

        long total = events.size();
        long allow = 0;
        long block = 0;
        long quarantine = 0;
        long replace = 0;
        long providerCalls = 0;
        long providerCacheHits = 0;
        Map<String, Long> categoryMap = new HashMap<>();
        Map<String, Map<String, Long>> trendMap = new LinkedHashMap<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = windowDays - 1; i >= 0; i--) {
            Calendar d = Calendar.getInstance();
            d.add(Calendar.DAY_OF_YEAR, -i);
            Map<String, Long> dayData = new HashMap<>();
            dayData.put("total", 0L);
            dayData.put("allow", 0L);
            dayData.put("block", 0L);
            dayData.put("provider", 0L);
            trendMap.put(sdf.format(d.getTime()), dayData);
        }

        for (ModerationEvent e : events) {
            String act = e.getFinalAction();
            if ("BLOCK".equals(act)) {
                block++;
            } else if ("QUARANTINE".equals(act)) {
                quarantine++;
            } else if ("REPLACE".equals(act)) {
                replace++;
            } else {
                allow++;
            }

            boolean isProviderCalled = e.getProvider() != null || e.getProviderDecision() != null;
            if (isProviderCalled) {
                providerCalls++;
            }

            if (e.getCategories() != null && !e.getCategories().isBlank()) {
                String[] cats = e.getCategories().split(",");
                for (String c : cats) {
                    c = c.trim();
                    if (!c.isEmpty()) {
                        categoryMap.put(c, categoryMap.getOrDefault(c, 0L) + 1);
                    }
                }
            }

            if (e.getCreateTime() != null) {
                String dateStr = sdf.format(e.getCreateTime());
                Map<String, Long> dayData = trendMap.computeIfAbsent(dateStr, k -> new HashMap<>());
                dayData.put("total", dayData.getOrDefault("total", 0L) + 1);
                if ("BLOCK".equals(act)) {
                    dayData.put("block", dayData.getOrDefault("block", 0L) + 1);
                } else {
                    dayData.put("allow", dayData.getOrDefault("allow", 0L) + 1);
                }
                if (isProviderCalled) {
                    dayData.put("provider", dayData.getOrDefault("provider", 0L) + 1);
                }
            }
        }

        BigDecimal cost = BigDecimal.valueOf(providerCalls)
                .multiply(BigDecimal.valueOf(0.0005))
                .setScale(4, RoundingMode.HALF_UP);

        long activeQuarantine = 0;
        if (documentMapper != null) {
            activeQuarantine = documentMapper.selectCount(new LambdaQueryWrapper<AiDocument>()
                    .eq(AiDocument::getModerationStatus, "QUARANTINED")
                    .gt(AiDocument::getQuarantineExpireTime, new Date()));
        }

        long pendingCandidates = 0;
        if (candidateMapper != null) {
            pendingCandidates = candidateMapper.selectCount(new LambdaQueryWrapper<ModerationCandidate>()
                    .eq(ModerationCandidate::getStatus, "OBSERVED"));
        }

        return new ModerationSummaryView(
                total,
                allow,
                block,
                quarantine,
                replace,
                providerCalls,
                providerCacheHits,
                cost,
                activeQuarantine,
                pendingCandidates,
                categoryMap,
                trendMap
        );
    }
}
