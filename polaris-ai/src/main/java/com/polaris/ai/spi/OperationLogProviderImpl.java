package com.polaris.ai.spi;

import com.polaris.ai.core.spi.OperationLogProvider;
import com.polaris.common.utils.ServletUtils;
import com.polaris.common.utils.ip.IpUtils;
import com.polaris.system.domain.SysOperLog;
import com.polaris.system.service.ISysOperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 基于 polaris-system 的操作日志 SPI 实现
 */
@Slf4j
@Component
public class OperationLogProviderImpl implements OperationLogProvider {

    @Autowired(required = false)
    private ISysOperLogService operLogService;

    @Override
    public void recordLog(String module, String action, String detail) {
        if (operLogService == null) {
            return;
        }
        try {
            SysOperLog operLog = new SysOperLog();
            operLog.setStatus(0);
            operLog.setTitle(module);
            operLog.setBusinessType(1);
            operLog.setMethod(action);
            operLog.setRequestMethod("POST");
            operLog.setOperTime(new Date());
            operLog.setOperParam(detail);
            try {
                operLog.setOperIp(IpUtils.getIpAddr(ServletUtils.getRequest()));
                operLog.setOperUrl(ServletUtils.getRequest().getRequestURI());
            } catch (Exception ignored) {}

            operLogService.insertOperlog(operLog);
        } catch (Exception e) {
            log.warn("记录操作日志失败: {}", e.getMessage());
        }
    }
}
