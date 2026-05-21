package com.datasophon.api.service.extrepo.ctx;

import com.datasophon.dao.model.extrepo.ServiceResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zhanghuangbin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimpleServiceResource implements ServiceResource<SimpleServiceResource> {

    private String name;

    private String version;

}
