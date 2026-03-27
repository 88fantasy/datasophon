package com.datasophon.common.utils.nexus.vo;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class Assert {
    private String id;
    private String repository;
    private String format;
    private Checksum checksum;

    private String downloadUrl;

    public String getMd5() {
        return checksum == null ? null : checksum.getMd5();
    }

    @Data
    public static class Checksum {
        private String md5;
    }
}
