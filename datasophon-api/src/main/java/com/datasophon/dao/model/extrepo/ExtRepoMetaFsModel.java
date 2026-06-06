package com.datasophon.dao.model.extrepo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ExtRepoMetaFsModel {
    
    private String template;
    
    private String meta;
    
    private List<FrameworkMeta> frameworks = new ArrayList<>();
    
}
