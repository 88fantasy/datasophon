package com.datasophon.api.service.storage;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 */
public interface StorageService {


    void moveToStorage(File dir, boolean includeDir) throws IOException;

    void moveToStorage(File file, Function<File, String> relativePathHandler) throws IOException;
}
