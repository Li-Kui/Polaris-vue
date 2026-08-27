package com.polaris.platform.connector;

import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/** 阻止数据库连接指向本机、回环或链路本地地址。 */
@Component
public class DatasourceHostSafetyPolicy {

    public void validate(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    throw new ServiceException("数据库主机不能指向本机、回环或链路本地地址");
                }
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("数据库主机无法解析");
        }
    }
}
