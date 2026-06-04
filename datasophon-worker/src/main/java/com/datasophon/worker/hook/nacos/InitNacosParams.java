package com.datasophon.worker.hook.nacos;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class InitNacosParams {
    
    private String serverAddr;
    
    private String namespace;
    
    private String group;
    
    private String username;
    
    private String password;
    
    private List<Resource> resources;
    
    @Data
    public static class Resource {
        
        /**
         * 配置文件路径
         */
        private String path;
        
        private boolean overwrite = false;
        
        /**
         * 为空时，取文件名
         */
        private String configName;
    }
}
