package com.datasophon.common.k8s.spec;

import lombok.Data;

import java.util.List;

@Data
public class OciManifest {
    private int schemaVersion;
    private String mediaType;
    private OciDescriptor config;
    private List<OciDescriptor> layers;

}