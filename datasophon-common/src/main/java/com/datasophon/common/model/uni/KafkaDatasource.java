package com.datasophon.common.model.uni;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.io.Serializable;

@Data
public class KafkaDatasource implements Serializable {

    private String userName;

    private String password;

    private String host;

    private JSONObject other;

    private int isKerberos;

    private String bootstrapServers;

    private String securityProtocol;
}
