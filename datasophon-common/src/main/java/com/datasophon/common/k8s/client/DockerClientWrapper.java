package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.dto.ReTagDTO;
import com.datasophon.common.k8s.vo.ImageManifest;

import java.io.File;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DockerClientWrapper {

    List<ImageManifest> load(File file);

    String tag(ReTagDTO dto);

    /**
     * 返回符合私库规范的tag
     * @param tag
     * @return
     */
    String normalTag(String tag);

    /**
     *
     * @param imageId 全路径，eg: docker.io/library/portal:3.3.0
     */
    void push(String imageId);


}
