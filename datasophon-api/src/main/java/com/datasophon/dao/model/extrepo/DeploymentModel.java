package com.datasophon.dao.model.extrepo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploymentModel {
    
    private List<DeploySrvModel> app = new ArrayList<>();
}
