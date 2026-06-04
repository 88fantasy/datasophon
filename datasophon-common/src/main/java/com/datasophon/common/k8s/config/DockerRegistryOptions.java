package com.datasophon.common.k8s.config;

import lombok.Data;
import cn.hutool.core.util.StrUtil;

/**
 * Docker 客户端配置选项
 *
 * @author zhanghuangbin
 */
@Data
public class DockerRegistryOptions {
    
    /**
     * 是否采用http
     */
    private boolean insecure = false;
    /**
     *   地址
     */
    private String host;
    
    /**
     * 端口
     */
    private Integer port;
    
    /**
     * nexus的仓库才会有值，harbor没有值
     */
    private String repo;
    
    /**
     * Registry 用户名
     */
    private String username;
    
    /**
     * Registry 密码
     */
    private String password;
    
    public String getRegistry() {
        StringBuilder sb = new StringBuilder();
        sb.append(host);
        if (port != null) {
            sb.append(":").append(port);
        } else {
            sb.append(":").append(insecure ? "80" : "443");
        }
        return sb.toString();
    }
    
    /**
     *  镜像仓库地址
     *  查看文档：https://help.sonatype.com/en/docker-registry.html
     */
    public String getImageRegistry() {
        String repository = getRegistry();
        if (StrUtil.isNotBlank(repo)) {
            repository = repository + "/" + repo;
        }
        return repository;
    }
    
}
