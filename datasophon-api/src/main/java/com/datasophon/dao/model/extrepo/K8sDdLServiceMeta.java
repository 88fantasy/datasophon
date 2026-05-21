package com.datasophon.dao.model.extrepo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class K8sDdLServiceMeta {

    private String frameCode;

    private String manifest;

    private String name;

    private String version;


    private List<String> dependencies;

    private List<String> charts = new ArrayList<>();


}
