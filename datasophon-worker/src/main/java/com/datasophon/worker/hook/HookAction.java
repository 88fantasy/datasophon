package com.datasophon.worker.hook;

import com.datasophon.common.utils.ExecResult;

/**
 * @author zhanghuangbin
 */
public interface HookAction {

    ExecResult invoke(HookContext context) throws Exception;

}
