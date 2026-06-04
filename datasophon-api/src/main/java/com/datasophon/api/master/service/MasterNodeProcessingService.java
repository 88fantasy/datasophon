/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.master.service;

import com.datasophon.common.command.OlapSqlExecCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.OlapUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * OLAP 节点注册 Spring Service，业务逻辑来自 {@link MasterNodeProcessingActor}。
 */
@Service
public class MasterNodeProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(MasterNodeProcessingService.class);
    
    @Async("masterExecutor")
    public void processOlapNode(OlapSqlExecCommand command) {
        ExecResult execResult = new ExecResult();
        String tip = command.getOpsType().getDesc();
        Map<String, String> globalVariables = command.getVariables();
        String rootPassword = globalVariables.getOrDefault("${DORIS.root_password}", "");
        
        switch (command.getOpsType()) {
            case ADD_BE:
                execResult = OlapUtils.addBackend(command.getFeMaster(), command.getHostName(), rootPassword);
                break;
            case ADD_FE_FOLLOWER:
                execResult = OlapUtils.addFollower(command.getFeMaster(), command.getHostName(), rootPassword);
                break;
            case ADD_FE_OBSERVER:
                execResult = OlapUtils.addObserver(command.getFeMaster(), command.getHostName(), rootPassword);
                break;
            default:
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
                    default:
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
                Thread.currentThread().interrupt();
                logger.info("The SR operate be sleep operation failed");
            }
        }
    }
}
