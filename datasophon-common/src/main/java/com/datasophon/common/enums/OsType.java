package com.datasophon.common.enums;

import lombok.Getter;

@Getter
public enum OsType {
    CentOS7(""),
    CentOS8(""),
    OpenEuler(""),
    Other("")
    
    ;
    
    private final String osRegex;
    
    OsType(String osRegex) {
        this.osRegex = osRegex;
    }
}
