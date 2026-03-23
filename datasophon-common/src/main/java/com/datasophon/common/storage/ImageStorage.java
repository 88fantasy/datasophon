package com.datasophon.common.storage;

import java.io.File;
import java.io.IOException;

/**
 * @author zhanghuangbin
 */
public interface ImageStorage {

    boolean isEnabled();


    void pushImages(File dir, PushCallback cb) throws IOException;


    interface PushCallback {

        default void onEntryStart(File file) {}

        default void onEntryCompleted(File file){};
    }

}
