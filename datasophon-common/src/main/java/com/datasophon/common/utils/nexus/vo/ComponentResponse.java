package com.datasophon.common.utils.nexus.vo;

import lombok.Data;

import java.util.List;

@Data
public class ComponentResponse {
    private List<Component> items;
    private String continuationToken;


}
