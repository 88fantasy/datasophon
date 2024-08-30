package com.datasophon.common.enums;

import lombok.Getter;

@Getter
public enum ArchType {
    X86("x86_64"),
    
    ARM("aarch64"),
    
    OTHER("other");
    
    private final String arch;
    
    ArchType(String arch) {
        this.arch = arch;
    }
}
