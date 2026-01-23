package com.datasophon.worker.actor;

import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.worker.handler.ServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhanghuangbin
 */
public class ServiceStatusActor extends HookTypedActor<ServiceRoleOperateCommand> {


    private static final Logger log = LoggerFactory.getLogger(ServiceStatusActor.class);

    @Override
    protected void doOnReceive(ServiceRoleOperateCommand command) throws Throwable {
        ExecResult result = new ExecResult();
        try {
            ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
            int times = PropertyUtils.getInt("times");
            int count = 0;
            while (count < times) {
                count++;
                result = serviceHandler.status(command.getStatusRunner(), command.getDecompressPackageName());
                if (result.getExecResult()) {
                    break;
                } else {
                    try {Thread.sleep(5 * 1000);} catch (InterruptedException ignored) {}
                }
            }
            if (count == times) {
                result.setExecResult(false);
                result.setExecOut(String.format("检查%s状态失败，已经达到重试次数%s", command.getServiceRoleName(), times));
            }
        } catch (Throwable throwable) {
            result = ExecResult.error(String.format("检查%s状态失败，%s", command.getServiceRoleName(), throwable.getMessage()));
            log.error("检查{}状态失败", command.getServiceRoleName(), throwable);
        }finally {
            getSender().tell(result, getSelf());
        }
    }
}
