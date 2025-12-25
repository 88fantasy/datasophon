package com.datasophon.common.storage;

import cn.hutool.core.util.ServiceLoaderUtil;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * @author zhanghuangbin
 */
public class PackageStorageUtils {


    public static PackageStorage getStorage() {
        ServiceLoader<PackageStorage> loader = ServiceLoaderUtil.load(PackageStorage.class);
        final Iterator<PackageStorage> iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                PackageStorage storage =  iterator.next();
                if (storage.isEnabled()) {
                    return storage;
                }
            } catch (ServiceConfigurationError ignore) {
                // ignore
            }
        }
        throw new IllegalStateException("no PackageStorage Available");
    }
}
