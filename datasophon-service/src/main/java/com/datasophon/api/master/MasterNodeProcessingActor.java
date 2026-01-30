package com.datasophon.api.master;

import cn.hutool.json.JSONUtil;
import com.datasophon.common.command.OlapSqlExecCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.OlapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MasterNodeProcessingActor extends TypedActor<OlapSqlExecCommand> {
    
    private static final Logger logger = LoggerFactory.getLogger(MasterNodeProcessingActor.class);

    @Override
    protected void doOnReceive(OlapSqlExecCommand command) throws Throwable {
        logger.info("MasterNodeProcessingActor receive message: {}", JSONUtil.toJsonStr(command));
        ExecResult execResult = new ExecResult();
        String tip = command.getOpsType().getDesc();
        switch (command.getOpsType()) {
            case ADD_BE:
                execResult = OlapUtils.addBackend(command.getFeMaster(), command.getHostName(), command.getWorkerPath());
                break;
            case ADD_FE_FOLLOWER:
                execResult = OlapUtils.addFollower(command.getFeMaster(), command.getHostName(), command.getWorkerPath());
                break;
            case ADD_FE_OBSERVER:
                execResult = OlapUtils.addObserver(command.getFeMaster(), command.getHostName(), command.getWorkerPath());
                break;
        }
        if (execResult.getExecResult()) {
            logger.info("{} {} added success", command.getHostName(), tip);
        } else {
            logger.info("{} {} added failed", command.getHostName(), tip);
        }
        int tryTimes = 0;
        while (!execResult.getExecResult() && tryTimes < 3) {
            try {
                TimeUnit.SECONDS.sleep(10L);
                switch (command.getOpsType()) {
                    case ADD_BE:
                        execResult = OlapUtils.addBackendBySqlClient(command.getFeMaster(), command.getHostName());
                        break;
                    case ADD_FE_FOLLOWER:
                        execResult = OlapUtils.addFollowerBySqlClient(command.getFeMaster(), command.getHostName());
                        break;
                    case ADD_FE_OBSERVER:
                        execResult = OlapUtils.addObserverBySqlClient(command.getFeMaster(), command.getHostName());
                        break;
                }
                if (execResult.getExecResult()) {
                    logger.info("{} {} added success", command.getHostName(), tip);
                    break;
                } else {
                    logger.info("{} {} added failed", command.getHostName(), tip);
                }
                tryTimes++;
            } catch (InterruptedException e) {
                logger.info("The SR operate be sleep operation failed");
            }
        }
    }
}
