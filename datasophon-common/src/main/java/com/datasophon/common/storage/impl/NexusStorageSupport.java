package com.datasophon.common.storage.impl;

import com.datasophon.common.Constants;
import com.datasophon.common.model.uni.NexusUri;

import java.io.File;

/**
 * @author zhanghuangbin
 */
public abstract class NexusStorageSupport {

    public boolean isEnabled() {
        return getNexusUri().isEnabled();
    }

    protected void ensureDirValid(File dir) {
        if (!dir.exists()) {
            throw new IllegalStateException(dir.getAbsolutePath() + " not exists");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir.getAbsolutePath() + " is not a dir");
        }
    }

    protected void ensureFileValid(File file) {
        if (!file.exists()) {
            throw new IllegalStateException(file.getAbsolutePath() + " not exists");
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException(file.getAbsolutePath() + " is not a file");
        }
    }

    protected void ensureNexusEnable() {
        NexusUri registry = getNexusUri();
        if (!registry.isEnabled()) {
            throw new IllegalStateException("datasophon require an nexus available, but nexus is disabled");
        }
    }

    protected static NexusUri getNexusUri() {
        NexusUri uri = new NexusUri();
        uri.setEnabled(Constants.NEXUS_ENABLE);
        uri.setUri(String.format("http://%s:%s", Constants.NEXUS_IP, Constants.NEXUS_PORT));
        uri.setUser(Constants.NEXUS_USERNAME);
        uri.setPassword(Constants.NEXUS_PASSWORD);
        uri.setIp(Constants.NEXUS_IP);
        uri.setPort(Constants.NEXUS_PORT);
        return uri;
    }
}
