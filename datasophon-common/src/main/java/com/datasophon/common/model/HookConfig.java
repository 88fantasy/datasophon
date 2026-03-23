package com.datasophon.common.model;

import com.datasophon.common.enums.HookType;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
@Data
public class HookConfig implements Serializable {

    private static final long serialVersionUID = 2013439883474996138L;
    /**
     * hook类型
     */
    private HookType type;

    /**
     * 执行条件
     */
    private String condition;

    /**
     * 动作
     */
    private String action;

    /**
     * 动作的参数
     */
    private Map<String, Object> params;
}
