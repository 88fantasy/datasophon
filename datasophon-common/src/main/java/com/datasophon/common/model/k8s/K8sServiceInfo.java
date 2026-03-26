package com.datasophon.common.model.k8s;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInfo {

    @NotBlank(message = "name不能为空")
    private String name;

    @NotBlank(message = "version不能为空")
    private String version;

    private String description;

    private String type;

    private List<String> dependencies = new ArrayList<>(0);

    @NotNull(message = "artifact不能为null")
    private K8sArtifact artifact;
}
