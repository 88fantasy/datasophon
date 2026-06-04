package com.datasophon.common.k8s.spec.docker;

import java.util.List;

import lombok.Data;

@Data
public class DockerManifestEntry {
    private String Config;
    private List<String> RepoTags;
    private List<String> Layers;
}