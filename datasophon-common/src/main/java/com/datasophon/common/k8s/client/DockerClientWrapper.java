package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.dto.ReTagDTO;
import com.datasophon.common.k8s.vo.ImageManifest;
import com.github.dockerjava.api.model.Identifier;

import java.io.File;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DockerClientWrapper {

    List<ImageManifest> load(File file);

    String tag(ReTagDTO dto);


    String normalTag(String tag);

    void push(Identifier image);


}
