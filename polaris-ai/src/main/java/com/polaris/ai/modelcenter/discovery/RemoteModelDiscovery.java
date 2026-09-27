package com.polaris.ai.modelcenter.discovery;

import com.polaris.ai.modelcenter.protocol.ProtocolEndpoint;
import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;

import java.util.List;

/** 按协议列出远程模型身份的受控发现器。 */
public interface RemoteModelDiscovery {

    String protocolCode();

    List<RemoteModelInfo> listModels(
            ProviderConnectionRuntime connection,
            ProtocolEndpoint endpoint);
}
