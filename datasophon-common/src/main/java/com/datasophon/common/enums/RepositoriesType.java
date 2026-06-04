package com.datasophon.common.enums;

import lombok.Getter;

@Getter
public enum RepositoriesType {
    APT("apt"),
    
    DOCKER("docker"),
    
    HELM("helm"),
    
    MAVEN2("maven2"),
    
    YUM("yum"),
    
    RAW("raw"),
    
    ;
    
    private final String desc;
    
    RepositoriesType(String desc) {
        this.desc = desc;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public static RepositoriesType of(String desc) {
        for (RepositoriesType type : values()) {
            if (type.desc.equals(desc)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown repository type: " + desc);
    }
}
