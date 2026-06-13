package com.datasophon.dao.model.extrepo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class FrameworkMeta {
    
    private String frameCode;
    
    private List<PhysicalDdlServiceMeta> physicalDdlServices = new ArrayList<>();
    
    private List<K8sDdLServiceMeta> k8sDdLServices = new ArrayList<>();
}
