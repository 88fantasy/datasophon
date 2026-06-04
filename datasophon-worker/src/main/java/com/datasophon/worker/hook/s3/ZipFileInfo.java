package com.datasophon.worker.hook.s3;

import lombok.Getter;

@Getter
public class ZipFileInfo {
    private final String version;
    private final String filePath;
    
    public ZipFileInfo(String version, String filePath) {
        this.version = version;
        this.filePath = filePath;
    }
    
    @Override
    public String toString() {
        return version + " (" + filePath + ")";
    }
}
