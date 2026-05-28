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


package com.datasophon.common.model;

import com.datasophon.common.enums.CommandType;

import java.io.Serializable;

import lombok.Data;

/**
 * 支持 worker 上启动的服务管理
 *
 * @author zhenqin
 */
@Data
public class WorkerServiceMessage implements Serializable {
    
    /**
     * 节点名称
     */
    private String hostname;
    
    /**
     * Cluster ID
     */
    private Integer clusterId;
    
    /**
     * 节点 IP
     */
    private String ip;
    
    private CommandType commandType;
    
    public WorkerServiceMessage() {
    }
    
    public WorkerServiceMessage(String hostname, Integer clusterId, CommandType commandType) {
        this.hostname = hostname;
        this.clusterId = clusterId;
        this.commandType = commandType;
    }
}
