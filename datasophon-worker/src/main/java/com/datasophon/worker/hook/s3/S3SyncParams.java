package com.datasophon.worker.hook.s3;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class S3SyncParams {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket;

    private String metaObjectName;

    private String resourcePath;

    private boolean override;

    private boolean createBucketIfAbsent;

}
