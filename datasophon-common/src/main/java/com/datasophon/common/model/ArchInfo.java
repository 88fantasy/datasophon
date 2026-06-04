package com.datasophon.common.model;

import java.io.Serializable;

import lombok.Data;

@Data
public class ArchInfo implements Serializable {
    
    private String packageName;
    
    /**
     * 必填：该架构包解压后的顶层目录名。
     * 例如 HDFS 两个架构均为 "hadoop-3.5.0"，VALKEY 按架构不同：
     *   x86_64 → "valkey-8.1.7-jammy-x86_64"，aarch64 → "valkey-8.1.7-jammy-arm64"。
     */
    private String decompressPackageName;
    
}
