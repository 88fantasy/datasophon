package com.datasophon.api.service.extrepo.ctx;

import com.datasophon.dao.model.extrepo.ServiceResource;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class SimpleServiceResource implements ServiceResource<SimpleServiceResource> {

    private String name;

    private String version;

}
