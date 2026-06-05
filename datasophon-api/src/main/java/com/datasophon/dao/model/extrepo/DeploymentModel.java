package com.datasophon.dao.model.extrepo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DeploymentModel {
    
    private List<DeploySrvModel> app = new ArrayList<>();
}
