package com.datasophon.common.model.uni;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DorisDatasource implements Serializable {

    private String userName;

    private String port;

    private String webPort;

    private String password;

    private String host;

    private JSONObject other;

    private List<String> beHostPorts;

    private int isKerberos;

    private String hivePrincipal;
}
