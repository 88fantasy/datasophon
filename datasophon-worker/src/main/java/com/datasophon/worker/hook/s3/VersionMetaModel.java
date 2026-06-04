package com.datasophon.worker.hook.s3;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class VersionMetaModel {
    
    private List<String> syncVersions;
}
