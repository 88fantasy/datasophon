package com.datasophon.common.storage;

import com.datasophon.common.storage.vo.DownloadResult;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

/**
 * 安装包存储的抽象类。约定所有的操作，均通过这个类来进行
 *
 * @author zhanghuangbin
 */
public interface PackageStorage {
    
    boolean isEnabled();
    
    void moveToStorage(File src, boolean includeDir) throws IOException;
    
    void moveToStorage(File file, Function<File, String> relativePathHandler) throws IOException;
    
    String readPackageMd5(String packageName);
    
    DownloadResult downloadPackageToLocal(String packageName);
    
    DownloadResult downloadResourceToLocal(String resourceName);
    
    void deletePackage(String packageName);
}
