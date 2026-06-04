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

package com.datasophon.api.log;

import java.util.Arrays;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AbstractDiscriminator;

/**
 * Task Log Discriminator
 */
public class TaskLogDiscriminator extends AbstractDiscriminator<ILoggingEvent> {
    
    /**
     * key
     */
    private String key;
    
    /**
     * log base
     */
    private String logBase;
    
    /**
     * logger name should be like:
     * Task Logger name should be like: Task-{xx}-{xxx}-{xxx}
     *
     */
    @Override
    public String getDiscriminatingValue(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        String prefix = ExecLogConstant.TASK_LOG_LOGGER_NAME + "-";
        if (loggerName.startsWith(prefix)) {
            loggerName = loggerName.substring(prefix.length());
            List<String> paths = Arrays.asList(loggerName.split("-"));
            if (paths.size() >= 2) {
                return paths.get(0) + "/" + ExecLogConstant.LOGGER_FILE_PREFIX + paths.get(1);
            }
        }
        return "k8s/unknown_task";
    }
    
    @Override
    public void start() {
        started = true;
    }
    
    @Override
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getLogBase() {
        return logBase;
    }
    
    public void setLogBase(String logBase) {
        this.logBase = logBase;
    }
}
