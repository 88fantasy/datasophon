package com.datasophon.common.storage.impl;

import com.datasophon.common.storage.HelmStorage;
import com.datasophon.common.utils.NexusFileUtils;
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
        NexusFileUtils.uploadChartToHelmRepo(chart);
    }
}
