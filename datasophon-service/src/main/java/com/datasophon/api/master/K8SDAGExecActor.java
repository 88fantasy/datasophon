package com.datasophon.api.master;

import com.datasophon.common.command.dag.DAGExecCommand;

/**
 * @author zhanghuangbin
 */
public class K8SDAGExecActor extends TypedActor<DAGExecCommand>{
    @Override
    protected void doOnReceive(DAGExecCommand message) throws Throwable {

    }
}
