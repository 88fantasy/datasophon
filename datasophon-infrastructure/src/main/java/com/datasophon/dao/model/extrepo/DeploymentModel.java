package com.datasophon.dao.model.extrepo;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploymentModel {

    private List<DeploySrvModel> app;
}
