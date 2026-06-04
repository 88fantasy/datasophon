package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.vo.docker.LoadImageResult;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DockerClientWrapper {
    
    void login();
    
    List<LoadImageResult> load(File file) throws IOException;
    
    /**
     *
     * @param imageId 全路径，eg: docker.io/library/portal:3.3.0
     */
    void push(String imageId);
    
    void createManifest(String oldTag, List<LoadImageResult> images);
}
