package com.datasophon.common.utils.nexus.vo;

import lombok.Data;

@Data
public class ExecResult {

    private final boolean success;

    private final String message;


    public static ExecResult success(String message) {
        return new ExecResult(true, message);
    }


    public static ExecResult fail(String message) {
        return new ExecResult(false, message);
    }

}