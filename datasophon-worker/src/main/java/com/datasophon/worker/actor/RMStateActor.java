package com.datasophon.worker.actor;

import com.datasophon.common.command.ExecuteCmdCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

public class RMStateActor extends HookTypedActor<ExecuteCmdCommand> {


    @Override
    protected void doOnReceive(ExecuteCmdCommand command) throws Throwable {
        ExecResult execResult = ShellUtils.execShell(command.getCommandLine());
        getSender().tell(execResult, getSelf());
    }
}
