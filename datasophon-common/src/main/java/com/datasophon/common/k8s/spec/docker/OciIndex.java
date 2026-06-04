package com.datasophon.common.k8s.spec.docker;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class OciIndex {
    private int schemaVersion;
    private List<OciManifestRef> manifests;
    private String mediaType;
    
}
