package com.datasophon.worker.hook.resource;

import com.datasophon.worker.hook.db.MetaStorage;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ResourceStrategyParams {

    private String type;

    private String driver;

    private String username;

    private String password;


    private String resourceKey;

    private String scriptPath;

    private MetaStorage metaStorage;


}
