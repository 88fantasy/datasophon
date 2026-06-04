package com.datasophon.common.utils.nexus.vo;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class Component {
    private String id;
    private String repository;
    private String format;
    private String name;
    private List<Assert> assets;
    
    private String version;
}
