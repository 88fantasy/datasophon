package com.datasophon.common.storage.impl;

import com.datasophon.common.storage.HelmStorage;
import com.datasophon.common.utils.nexus.NexusFacade;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class NexusHelmStorage extends NexusStorageSupport implements HelmStorage {


    @Override
    public void pushHelm(File chart) throws IOException {
        ensureNexusEnable();
        ensureFileValid(chart);
        NexusFacade.getHelmClient().uploadChartToHelmRepo(chart);
    }

    @Override
    public void removeHelm(String chartName) throws IOException {
        ensureNexusEnable();
//        TODO
    }
}
