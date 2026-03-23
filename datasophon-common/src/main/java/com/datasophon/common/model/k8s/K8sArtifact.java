package com.datasophon.common.model.k8s;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sArtifact {

    private List<String> helm;

    private List<String> yaml;
}
