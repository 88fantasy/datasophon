package com.datasophon.dao.model.extrepo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class FrameworkMeta {

    private String frameCode;

    private List<VosDdLServiceMeta> vosDdlServices = new ArrayList<>();

    private List<K8sDdLServiceMeta> k8sDdLServices = new ArrayList<>();
}
