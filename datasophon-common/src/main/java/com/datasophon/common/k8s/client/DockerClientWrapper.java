package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.vo.ImageManifest;

import java.io.File;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DockerClientWrapper {



    List<ImageManifest> load(File file);

    /**
     *
     * @param imageId 全路径，eg: docker.io/library/portal:3.3.0
     */
    void push(String imageId);


}
