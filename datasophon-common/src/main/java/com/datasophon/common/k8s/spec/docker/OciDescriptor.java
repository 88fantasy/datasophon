package com.datasophon.common.k8s.spec.docker;

import lombok.Data;

@Data
public class OciDescriptor {
    private String mediaType;
    private String digest;
    private long size;
    
}
