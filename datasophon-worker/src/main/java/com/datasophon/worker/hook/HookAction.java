package com.datasophon.worker.hook;

import com.datasophon.common.utils.ExecResult;

/**
 * @author zhanghuangbin
 */
public interface HookAction {

    String getType();


    ExecResult invoke(HookContext context) throws Exception;

}
