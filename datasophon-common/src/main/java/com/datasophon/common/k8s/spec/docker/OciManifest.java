package com.datasophon.common.k8s.spec.docker;

import java.util.List;

import lombok.Data;

@Data
public class OciManifest {
    private int schemaVersion;
    private String mediaType;
    private OciDescriptor config;
    private List<OciDescriptor> layers;
    
}