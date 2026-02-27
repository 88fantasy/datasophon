package com.datasophon.worker.hook.s3;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class S3SyncParams {

    /**
     * oss的服务端点
     */
    private String endpoint;

    /**
     * accessKey
     */
    private String accessKey;

    /**
     * 密码
     */
    private String secretKey;

    /**
     * 创建的桶
     */
    private String bucket;

    /**
     * 用于记录版本升级信息的文件名称，如果为空，则为_datasophon_meta.json
     */
    private String metaObjectName;


    private String resourcePath;

    private boolean override;

    private boolean createBucketIfAbsent;

}
