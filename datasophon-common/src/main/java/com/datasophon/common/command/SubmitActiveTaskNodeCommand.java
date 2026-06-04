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

package com.datasophon.common.command;

import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceExecuteState;
import com.datasophon.common.model.DAGGraph;
import com.datasophon.common.model.ServiceNode;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Data;

@Data
public class SubmitActiveTaskNodeCommand implements Serializable {
    
    private static final long serialVersionUID = 3733897759707096649L;
    
    /**
     * @deprecated 不再使用，由serviceNode传递
     * @see ServiceNode#setCommandType(CommandType)
     */
    @Deprecated
    private CommandType commandType;
    private Integer clusterId;
    private String clusterCode;
    
    /**
     * nodeKey(string): 服务名称
     * nodeInfo(ServiceNode): 该服务需要执行的命令
     * edge: 无意义
     */
    private DAGGraph<String, ServiceNode, String> dag;
    private Map<String, String> errorTaskList = new ConcurrentHashMap<>();
    private Map<String, ServiceExecuteState> activeTaskList = new ConcurrentHashMap<>();
    private Map<String, String> readyToSubmitTaskList = new ConcurrentHashMap<>();
    private Map<String, String> completeTaskList = new ConcurrentHashMap<>();
    
}
