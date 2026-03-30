package com.datasophon.common.k8s.spec.docker;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class OciIndex {
    private int schemaVersion;
    private List<OciManifestRef> manifests;
    private String mediaType;

}
