package com.datasophon.worker.hook.s3;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class VersionMetaModel {

    private List<String> syncVersions;
}
