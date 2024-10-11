package com.datasophon.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum OsType {
    CentOS7("centos-7"),
    CentOS8("centos-7"),
    OpenEuler22("openEuler-22"),
    Other(""),
    Auto("")
    
    ;
    
    private final String osRegex;
    
    OsType(String osRegex) {
        this.osRegex = osRegex;
    }

    public static OsType of(String osRegex) {
        Optional<OsType> optional = Arrays.stream(OsType.values()).filter(type -> type.getOsRegex().equals(osRegex)).findAny();
        return optional.orElse(OsType.Other);
    }
}
