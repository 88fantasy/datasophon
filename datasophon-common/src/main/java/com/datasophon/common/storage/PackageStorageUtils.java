package com.datasophon.common.storage;

import cn.hutool.core.util.ServiceLoaderUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class PackageStorageUtils {


    public static PackageStorage getStorage() {
        ServiceLoader<PackageStorage> loader = ServiceLoaderUtil.load(PackageStorage.class);
        final Iterator<PackageStorage> iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                PackageStorage storage =  iterator.next();
                if (storage.isEnabled()) {
                    log.info("found the PackageStorage instance, type is {}", storage.getClass().getSimpleName());
                    return storage;
                }
            } catch (ServiceConfigurationError ignore) {
                // ignore
            }
        }
        throw new IllegalStateException("no PackageStorage Available");
    }
}
