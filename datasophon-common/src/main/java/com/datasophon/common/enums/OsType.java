package com.datasophon.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum OsType {
    CENTOS_7("centos-7"),

    CENTOS_8("centos-8"),

    OPENEULER_22_03_LTS_SP4("openEuler-22.03-LTS-SP4"),

    OPENEULER_22_03_LTS_SP1("openEuler-22.03-LTS-SP1"),

    OTHER("other"),

    AUTO("auto")
    
    ;
    
    private final String desc;
    
    OsType(String desc) {
        this.desc = desc;
    }

    public static OsType of(String desc) {
        Optional<OsType> optional = Arrays.stream(OsType.values()).filter(type -> type.getDesc().equals(desc)).findAny();
        return optional.orElse(OsType.OTHER);
    }
}
