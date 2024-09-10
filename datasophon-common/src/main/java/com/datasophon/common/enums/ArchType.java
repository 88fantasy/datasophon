package com.datasophon.common.enums;

import java.util.Arrays;
import java.util.Optional;

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
    
    public static ArchType of(String arch) {
        Optional<ArchType> optional = Arrays.stream(ArchType.values()).filter(type -> type.getArch().equals(arch)).findAny();
        return optional.orElse(ArchType.OTHER);
    }
}
