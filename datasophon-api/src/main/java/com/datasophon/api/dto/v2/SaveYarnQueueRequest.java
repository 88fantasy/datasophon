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

package com.datasophon.api.dto.v2;

import com.datasophon.dao.entity.ClusterYarnQueue;

import jakarta.validation.constraints.NotBlank;

import java.util.Date;

import lombok.Data;

/**
 * 新建 YARN 队列请求体（v2）。
 */
@Data
public class SaveYarnQueueRequest {
    
    @NotBlank
    private String queueName;
    
    private Integer minCore;
    
    private Integer minMem;
    
    private Integer maxCore;
    
    private Integer maxMem;
    
    private Integer appNum;
    
    @NotBlank
    private String schedulePolicy;
    
    private Integer weight;
    
    /** 1 = 是，2 = 否 */
    private Integer allowPreemption;
    
    private String amShare;
    
    public ClusterYarnQueue toEntity(Integer clusterId) {
        ClusterYarnQueue e = new ClusterYarnQueue();
        e.setQueueName(queueName);
        e.setMinCore(minCore);
        e.setMinMem(minMem);
        e.setMaxCore(maxCore);
        e.setMaxMem(maxMem);
        e.setAppNum(appNum);
        e.setSchedulePolicy(schedulePolicy);
        e.setWeight(weight);
        e.setAllowPreemption(allowPreemption);
        e.setAmShare(amShare);
        e.setClusterId(clusterId);
        e.setCreateTime(new Date());
        return e;
    }
}
