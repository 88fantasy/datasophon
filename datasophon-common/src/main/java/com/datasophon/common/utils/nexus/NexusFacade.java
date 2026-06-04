package com.datasophon.common.utils.nexus;

import com.datasophon.common.Constants;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.utils.nexus.client.CommonNexusClient;
import com.datasophon.common.utils.nexus.client.HelmRepoClient;
import com.datasophon.common.utils.nexus.client.RawRepoClient;

import lombok.extern.slf4j.Slf4j;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class NexusFacade {
    
    public static final String RAW_REPO = "raw";
    public static final String HELM_REPO = "helm";
    
    public static NexusUri getNexusUri() {
        NexusUri uri = new NexusUri();
        uri.setEnabled(Constants.NEXUS_ENABLE);
        uri.setIp(Constants.NEXUS_IP);
        uri.setPort(Constants.NEXUS_PORT);
        uri.setUri(String.format("http://%s:%s", uri.getIp(), uri.getPort()));
        uri.setUser(Constants.NEXUS_USERNAME);
        uri.setPassword(Constants.NEXUS_PASSWORD);
        return uri;
    }
    
    public static RawRepoClient getRawRepoClient() {
        return new RawRepoClient(RAW_REPO);
    }
    
    public static HelmRepoClient getHelmClient() {
        return new HelmRepoClient(HELM_REPO);
    }
    
    public static CommonNexusClient getCommonClient() {
        return new CommonNexusClient();
    }
    
}
