package com.datasophon.common.k8s.spec;

import lombok.Data;

import java.util.List;

@Data
public class DockerManifestEntry {
    private String Config;
    private List<String> RepoTags;
    private List<String> Layers;
}