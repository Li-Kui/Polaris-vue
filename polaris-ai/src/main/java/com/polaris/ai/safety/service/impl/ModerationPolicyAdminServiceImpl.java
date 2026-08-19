package com.polaris.ai.safety.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.ai.safety.dto.ModerationPolicyUpdateRequest;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.dto.ModerationTestRequest;
import com.polaris.ai.safety.mapper.ModerationPolicyMapper;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.model.PolicyMode;
import com.polaris.ai.safety.model.PolicyPreset;
import com.polaris.ai.safety.service.IModerationFacade;
import com.polaris.ai.safety.service.IModerationPolicyAdminService;
import com.polaris.ai.safety.vo.ModerationTestView;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * AI 敏感内容安全策略管理服务层实现类
 *
 * @author polaris
 */
@Service
public class ModerationPolicyAdminServiceImpl implements IModerationPolicyAdminService {

    @Autowired(required = false)
    private ModerationPolicyMapper policyMapper;

    @Autowired(required = false)
    private ModerationProperties properties;

    @Autowired(required = false)
    private IModerationFacade moderationFacade;

    public void setPolicyMapper(ModerationPolicyMapper policyMapper) {
        this.policyMapper = policyMapper;
    }

    public void setProperties(ModerationProperties properties) {
        this.properties = properties;
    }

    public void setModerationFacade(IModerationFacade moderationFacade) {
        this.moderationFacade = moderationFacade;
    }

    @Override
    public List<ModerationPolicy> listPolicies() {
        List<ModerationPolicy> persistedList = policyMapper != null
                ? policyMapper.selectList(Wrappers.emptyWrapper())
                : List.of();

        Map<String, ModerationPolicy> policyMap = new HashMap<>();
        if (persistedList != null) {
            for (ModerationPolicy p : persistedList) {
                if (p.getScene() != null) {
                    policyMap.put(p.getScene(), p);
                }
            }
        }

        List<ModerationPolicy> result = new ArrayList<>();
        for (ModerationScene scene : ModerationScene.values()) {
            ModerationPolicy p = policyMap.get(scene.name());
            if (p == null) {
                p = createDefaultPolicyFromProperties(scene);
            }
            result.add(p);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationPolicy updatePolicy(
            ModerationScene scene,
            ModerationPolicyUpdateRequest req,
            String operator) {
        if (scene == null || req == null) {
            throw new ServiceException("请求参数不能为空");
        }

        validateUpdateRequest(req);

        ModerationPolicy policy = null;
        if (policyMapper != null) {
            policy = policyMapper.selectOne(
                    Wrappers.<ModerationPolicy>query()
                            .eq("scene", scene.name())
                            .last("LIMIT 1")
            );
        }

        if (policy == null) {
            policy = createDefaultPolicyFromProperties(scene);
        }

        if (req.preset() != null) policy.setPreset(req.preset());
        if (req.mode() != null) policy.setMode(req.mode());
        if (req.enabled() != null) policy.setEnabled(req.enabled());
        if (req.suspectThreshold() != null) policy.setSuspectThreshold(req.suspectThreshold());
        if (req.blockThreshold() != null) policy.setBlockThreshold(req.blockThreshold());
        if (req.providerEnabled() != null) policy.setProviderEnabled(req.providerEnabled());
        if (req.providerTimeoutMs() != null) policy.setProviderTimeoutMs(req.providerTimeoutMs());
        if (req.providerDailyLimit() != null) policy.setProviderDailyLimit(req.providerDailyLimit());
        if (req.providerMonthlyBudget() != null) policy.setProviderMonthlyBudget(req.providerMonthlyBudget());
        if (req.segmentChars() != null) policy.setSegmentChars(req.segmentChars());
        if (req.segmentOverlapChars() != null) policy.setSegmentOverlapChars(req.segmentOverlapChars());
        if (req.outputBufferChars() != null) policy.setOutputBufferChars(req.outputBufferChars());
        if (req.quarantineDays() != null) policy.setQuarantineDays(req.quarantineDays());

        Long currentVer = policy.getPolicyVersion() == null ? 0L : policy.getPolicyVersion();
        policy.setPolicyVersion(currentVer + 1);
        policy.setUpdateTime(new Date());

        if (policyMapper != null) {
            if (policy.getId() == null) {
                policy.setCreateTime(new Date());
                policyMapper.insert(policy);
            } else {
                policyMapper.updateById(policy);
            }
        }

        return policy;
    }

    @Override
    public ModerationTestView testSentence(ModerationTestRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            throw new ServiceException("待测文本不能为空");
        }

        ModerationScene scene = req.scene() != null ? req.scene() : ModerationScene.CHAT_INPUT;
        ModerationRequest moderationRequest = ModerationRequest.of(scene, req.text(), "TEST", "ADMIN");

        long start = System.currentTimeMillis();
        ModerationResult result = moderationFacade != null
                ? moderationFacade.moderate(moderationRequest)
                : null;
        long latencyMs = System.currentTimeMillis() - start;

        if (result == null) {
            return new ModerationTestView(
                    null, null, 0, Set.of(), List.of(), 1L, 1L, false, latencyMs, "服务不可用"
            );
        }

        return new ModerationTestView(
                result.localDecision(),
                result.finalAction(),
                result.riskScore(),
                result.categories() != null ? result.categories() : Set.of(),
                result.matches() != null ? result.matches() : List.of(),
                result.policyVersion(),
                result.dictionaryVersion(),
                result.providerResult() != null,
                latencyMs,
                result.fallbackReason()
        );
    }

    private void validateUpdateRequest(ModerationPolicyUpdateRequest req) {
        if (req.preset() != null) {
            try {
                PolicyPreset.valueOf(req.preset());
            } catch (IllegalArgumentException e) {
                throw new ServiceException("无效的策略预设: " + req.preset());
            }
        }
        if (req.mode() != null) {
            try {
                PolicyMode.valueOf(req.mode());
            } catch (IllegalArgumentException e) {
                throw new ServiceException("无效的执行模式: " + req.mode());
            }
        }

        Integer suspect = req.suspectThreshold();
        Integer block = req.blockThreshold();
        if (suspect != null && (suspect < 0 || suspect > 100)) {
            throw new ServiceException("疑似阈值必须在 0 到 100 之间");
        }
        if (block != null && (block < 0 || block > 100)) {
            throw new ServiceException("拦截阈值必须在 0 到 100 之间");
        }
        if (suspect != null && block != null && suspect >= block) {
            throw new ServiceException("疑似阈值必须小于拦截阈值");
        }

        if (req.providerTimeoutMs() != null && (req.providerTimeoutMs() < 100 || req.providerTimeoutMs() > 10000)) {
            throw new ServiceException("第三方超时时间必须在 100ms 到 10000ms 之间");
        }
        if (req.providerDailyLimit() != null && req.providerDailyLimit() < 0) {
            throw new ServiceException("第三方每日限额不能为负数");
        }
        if (req.providerMonthlyBudget() != null && req.providerMonthlyBudget().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("第三方每月预算不能为负数");
        }
        if (req.quarantineDays() != null && (req.quarantineDays() < 1 || req.quarantineDays() > 90)) {
            throw new ServiceException("隔离保存天数必须在 1 到 90 天之间");
        }
        if (req.segmentChars() != null && req.segmentChars() <= 0) {
            throw new ServiceException("分段长度必须大于 0");
        }
        if (req.segmentOverlapChars() != null && req.segmentOverlapChars() < 0) {
            throw new ServiceException("重叠长度不能为负数");
        }
        if (req.segmentChars() != null && req.segmentOverlapChars() != null && req.segmentOverlapChars() > req.segmentChars()) {
            throw new ServiceException("分段重叠长度不能大于分段长度");
        }
        if (req.outputBufferChars() != null && req.outputBufferChars() <= 0) {
            throw new ServiceException("输出缓冲区大小必须大于 0");
        }
    }

    private ModerationPolicy createDefaultPolicyFromProperties(ModerationScene scene) {
        ModerationPolicy policy = new ModerationPolicy();
        policy.setScene(scene.name());
        policy.setEnabled(true);
        policy.setPolicyVersion(1L);

        if (properties != null && properties.getScenes() != null) {
            ModerationProperties.ScenePolicy source = properties.getScenes().get(scene);
            if (source != null) {
                policy.setPreset(source.getPreset() != null ? source.getPreset().name() : "BALANCED");
                policy.setMode(source.getMode() != null ? source.getMode().name() : "ENFORCE");
                policy.setEnabled(source.isEnabled());
                policy.setSuspectThreshold(source.getSuspectThreshold());
                policy.setBlockThreshold(source.getBlockThreshold());
                policy.setProviderEnabled(source.isProviderEnabled());
                policy.setProviderTimeoutMs(source.getProviderTimeoutMs());
                policy.setProviderDailyLimit(source.getProviderDailyLimit());
                policy.setProviderMonthlyBudget(source.getProviderMonthlyBudget());
                policy.setSegmentChars(source.getSegmentChars());
                policy.setSegmentOverlapChars(source.getSegmentOverlapChars());
                policy.setOutputBufferChars(source.getOutputBufferChars());
                policy.setQuarantineDays(source.getQuarantineDays());
                policy.setPolicyVersion(source.getPolicyVersion());
                return policy;
            }
        }

        policy.setPreset("BALANCED");
        policy.setMode("ENFORCE");
        policy.setSuspectThreshold(30);
        policy.setBlockThreshold(70);
        policy.setProviderEnabled(false);
        policy.setProviderTimeoutMs(1500);
        policy.setProviderDailyLimit(1000);
        policy.setProviderMonthlyBudget(new BigDecimal("50.00"));
        policy.setSegmentChars(200);
        policy.setSegmentOverlapChars(50);
        policy.setOutputBufferChars(300);
        policy.setQuarantineDays(7);
        return policy;
    }
}
