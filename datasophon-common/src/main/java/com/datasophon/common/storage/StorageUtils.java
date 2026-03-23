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
public class StorageUtils {


    public static PackageStorage getPackageStorage() {
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


    public static MetaStorage getMetaStorage() {
        ServiceLoader<MetaStorage> loader = ServiceLoaderUtil.load(MetaStorage.class);
        final Iterator<MetaStorage> iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                MetaStorage storage =  iterator.next();
                if (storage.isEnabled()) {
                    log.info("found the MetaStorage instance, type is {}", storage.getClass().getSimpleName());
                    return storage;
                }
            } catch (ServiceConfigurationError ignore) {
                // ignore
            }
        }
        throw new IllegalStateException("no MetaStorage Available");
    }

    public static ImageStorage getImageStorage() {
        ServiceLoader<ImageStorage> loader = ServiceLoaderUtil.load(ImageStorage.class);
        final Iterator<ImageStorage> iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                ImageStorage storage =  iterator.next();
                if (storage.isEnabled()) {
                    log.info("found the ImageStorage instance, type is {}", storage.getClass().getSimpleName());
                    return storage;
                }
            } catch (ServiceConfigurationError ignore) {
                // ignore
            }
        }
        throw new IllegalStateException("no ImageStorage Available");
    }

    public static HelmStorage getHelmStorage() {
        ServiceLoader<HelmStorage> loader = ServiceLoaderUtil.load(HelmStorage.class);
        final Iterator<HelmStorage> iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                HelmStorage storage =  iterator.next();
                if (storage.isEnabled()) {
                    log.info("found the HelmStorage instance, type is {}", storage.getClass().getSimpleName());
                    return storage;
                }
            } catch (ServiceConfigurationError ignore) {
                // ignore
            }
        }
        throw new IllegalStateException("no HelmStorage Available");
    }
}
