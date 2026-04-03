package com.datasophon.common.k8s.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;

/**
 * Docker 客户端配置选项
 *
 * @author zhanghuangbin
 */
@Data
public class DockerOptions {

    /**
     * 是否采用http
     */
    private boolean insecure = false;
    /**
     *   地址
     */
    private String repoHost;

    private Integer repoPort;

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

    public String getRepository() {
        String repository = getRegistry();
        if (StrUtil.isNotBlank(repo)) {
            repository = repository + "/" + repo;
        }
        return repository;
    }

    public String getRegistry() {
        StringBuilder sb = new StringBuilder();
        sb.append(repoHost);
        if (repoPort != null) {
            sb.append(":").append(repoPort);
        } else {
            sb.append(":").append(insecure ? "80" : "443");
        }
        return sb.toString();
    }

}
