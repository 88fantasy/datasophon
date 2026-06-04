package com.datasophon.common.utils.nexus.vo;

import java.util.List;

import lombok.Data;

@Data
public class ComponentResponse {
    private List<Component> items;
    private String continuationToken;
    
}
