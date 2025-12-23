package com.datasophon.dao.model.extrepo;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class ServiceMeta {

    private String ddl;

    private String name;

    private String version;

    private String packageName;

    private String script;



    private String frameCode;

    private List<String> dependencies;

}
