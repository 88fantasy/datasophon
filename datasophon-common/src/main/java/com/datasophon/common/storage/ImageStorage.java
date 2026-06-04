package com.datasophon.common.storage;

import com.datasophon.common.k8s.vo.docker.LoadImageResult;

import java.io.File;

/**
 * @author zhanghuangbin
 */
public interface ImageStorage {
    
    String REPO = "docker";
    
    boolean isEnabled();
    
    void pushImages(File dir, PushCallback cb);
    
    interface PushCallback {
        
        default void onEntryLoad(File file, double progress) {
        };
        
        default void onEntryPush(LoadImageResult image, double progress) {
        };
        
        default void onManifest(String imageId, double progress) {
        };
        
    }
    
}
