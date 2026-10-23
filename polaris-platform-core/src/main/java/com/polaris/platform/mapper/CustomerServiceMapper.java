package com.polaris.platform.mapper;

import com.polaris.platform.domain.CustomerServiceApp;
import com.polaris.platform.domain.CustomerServiceRelease;
import com.polaris.platform.domain.CustomerServiceSession;
import com.polaris.platform.domain.CustomerServiceTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/** 所有 SQL 显式限定租户；会话操作另限定真实用户。 */
@Mapper
public interface CustomerServiceMapper {
    List<CustomerServiceApp> selectApps(@Param("tenantId") Long tenantId);
    CustomerServiceApp selectApp(@Param("tenantId") Long tenantId, @Param("id") Long id);
    CustomerServiceApp lockApp(@Param("tenantId") Long tenantId, @Param("id") Long id);
    int insertApp(CustomerServiceApp app);
    int updateApp(CustomerServiceApp app);
    int insertRelease(CustomerServiceRelease release);
    CustomerServiceRelease selectRelease(@Param("tenantId") Long tenantId, @Param("id") String id);
    int insertSession(CustomerServiceSession session);
    CustomerServiceSession selectSession(@Param("tenantId") Long tenantId,
            @Param("userId") Long userId, @Param("id") String id);
    List<CustomerServiceSession> selectSessionRecords(@Param("tenantId") Long tenantId,
            @Param("userId") Long userId, @Param("appId") Long appId,
            @Param("before") CustomerServiceSession before);
    int countOpenSessions(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("appId") Long appId, @Param("now") Date now);
    CustomerServiceSession lockSession(@Param("tenantId") Long tenantId,
            @Param("userId") Long userId, @Param("id") String id);
    int advanceSession(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("id") String id, @Param("turnCount") int turnCount);
    int closeSession(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("id") String id);
    List<CustomerServiceTurn> selectTurns(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);
    CustomerServiceTurn selectRequest(@Param("tenantId") Long tenantId,
            @Param("sessionId") String sessionId, @Param("requestId") String requestId);
    int insertTurn(CustomerServiceTurn turn);
    int finishTurn(CustomerServiceTurn turn);
    int saveAnswerParts(CustomerServiceTurn turn);
    int expireTurns(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId,
            @Param("now") Date now);
    int interruptTurns(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId,
            @Param("id") String id, @Param("now") Date now);
    int countActiveSpeech(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId, @Param("now") Date now);
    int countActive(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("sessionId") String sessionId, @Param("turnId") String turnId, @Param("now") Date now);
}
