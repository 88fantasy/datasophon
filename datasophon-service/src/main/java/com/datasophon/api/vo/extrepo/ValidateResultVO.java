package com.datasophon.api.vo.extrepo;

import cn.hutool.core.collection.CollectionUtil;
import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/12
 */
@Data
public class ValidateResultVO {


    private List<String> errors;

    public ValidateResultVO() {
    }

    public ValidateResultVO(List<String> errors) {
        this.errors = errors;
    }

    public boolean isSuccess() {
        return CollectionUtil.isEmpty(errors);
    }
}
