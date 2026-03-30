package com.datasophon.common.k8s.spec.docker;

import lombok.Data;

import java.util.Map;

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
