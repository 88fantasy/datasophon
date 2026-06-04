package com.datasophon.common.k8s.spec.docker;

import java.util.Map;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class OciManifestRef {
    private String mediaType;
    private String digest;
    private ImageHostPlatform platform;
    private Map<String, String> annotations;
    
}
