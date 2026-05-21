package com.datasophon.common.model.uni;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.io.Serializable;

@Data
public class HiveDatasource implements Serializable {

    private String userName;

    private String port;

    private String password;

    private String host;

    private JSONObject other;

    private String defaultFs;

    private String hadoopConfig;

    private int isKerberos;

    private String thriftUrls;

    private String warehouse;

    private String authentication;
}
