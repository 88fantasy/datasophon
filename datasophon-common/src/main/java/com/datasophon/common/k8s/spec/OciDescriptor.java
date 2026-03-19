package com.datasophon.common.k8s.spec;

import lombok.Data;

@Data
public class OciDescriptor {
    private String mediaType;
    private String digest;
    private long size;

}
