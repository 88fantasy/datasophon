package com.datasophon.common.storage;

import java.io.File;
import java.io.IOException;

/**
 * @author zhanghuangbin
 */
public interface HelmStorage {
    
    boolean isEnabled();
    
    void pushHelm(File chart) throws IOException;
    
    void removeHelm(String chartName) throws IOException;
    
}
