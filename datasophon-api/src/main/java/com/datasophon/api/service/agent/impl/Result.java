package com.datasophon.api.service.agent.impl;

import lombok.Data;

@Data
public class Result<T> {


    private Integer code;
    private String msg;

    private T data;


    public boolean isSuccess() {
        return code == 200;
    }


}