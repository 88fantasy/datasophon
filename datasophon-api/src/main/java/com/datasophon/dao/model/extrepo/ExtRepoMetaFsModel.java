package com.datasophon.dao.model.extrepo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class ExtRepoMetaFsModel {
    
    private String template;
    
    private String meta;
    
    private List<FrameworkMeta> frameworks = new ArrayList<>();
    
}
