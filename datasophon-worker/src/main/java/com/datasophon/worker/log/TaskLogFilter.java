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

package com.datasophon.worker.log;

import com.datasophon.worker.utils.TaskConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * task log filter
 */
public class TaskLogFilter extends Filter<ILoggingEvent> {
    
    private static Logger logger = LoggerFactory.getLogger(TaskLogFilter.class);
    
    /**
     * level
     */
    private Level level;
    
    public void setLevel(String level) {
        this.level = Level.toLevel(level);
    }
    
    /**
     * Accept or reject based on thread name
     *
     * @param event event
     * @return FilterReply
     */
    @Override
    public FilterReply decide(ILoggingEvent event) {
        FilterReply filterReply = FilterReply.DENY;
        if ((event.getLoggerName().startsWith(TaskConstants.TASK_LOG_LOGGER_NAME)) || event.getLevel().isGreaterOrEqual(level)) {
            filterReply = FilterReply.ACCEPT;
        }
        logger.debug("task log filter, thread name:{}, loggerName:{}, filterReply:{}, level:{}", event.getThreadName(), event.getLoggerName(), filterReply.name(), level);
        return filterReply;
    }
}
