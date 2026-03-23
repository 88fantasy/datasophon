package com.datasophon.cli.base;

import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class BatchExecutor {

    private final Executor executor;

    public BatchExecutor(Executor executor) {
        this.executor = executor;
    }

    public void execBatch(List<String> cmdList) {
        Objects.requireNonNull(cmdList, "cmdList can't be null");
        for (String cmd : cmdList) {
            log.info("start to exec command: {}", cmd);
            ExecResult result = executor.execShell(cmd);
            if (result.isSuccess()) {
                log.info("exec cmd success, cmd: {}", cmd);
            } else {
                log.info("exec cmd fail, cmd: {}, error: {}", cmd, result.getExecResult());
                System.exit(1);
            }
        }
    }


    public void installSoftware(List<String> rpm, List<String> deb) {
        List<String> cmdList = new ArrayList<>();

        OsType type = executor.getOs();
        if (OsType.isCentos(type)) {
            rpm.forEach(rpmId -> cmdList.add("yum install -y " + rpmId));
        } else if (OsType.isUnbuntu(type)){
            deb.forEach(debId -> cmdList.add("apt install -y " + debId));
        } else {
            log.error("unsupported os {}", type);
            System.exit(1);
        }
        if (cmdList.isEmpty()) {
            log.warn("BatchExecutor install no software, do you pass any software name?");
            return;
        }

        if (OsType.isUnbuntu(type)) {
            cmdList.add(0, "apt update");
        }
        execBatch(cmdList);
    }
}
