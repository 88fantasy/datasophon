package com.datasophon.common.utils.nexus.vo;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class Component {
    private String id;
    private String repository;
    private String format;
    private String name;
    private String downloadUrl;
    private List<Assert> assets;
}
