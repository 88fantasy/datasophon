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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * YARN 队列响应体（v2），对应前端 {@code DATASOPHON.YarnQueue}。
 */
@Data
public class YarnQueueResponse {

    private Integer id;

    private String queueName;

    private Integer minCore;

    private Integer minMem;

    private Integer maxCore;

    private Integer maxMem;

    private Integer appNum;

    private String schedulePolicy;

    private Integer weight;

    /** 1 = 是，2 = 否 */
    private Integer allowPreemption;

    private String amShare;

    private Integer clusterId;

    private Date createTime;

    public static YarnQueueResponse from(ClusterYarnQueue entity) {
        YarnQueueResponse r = new YarnQueueResponse();
        r.setId(entity.getId());
        r.setQueueName(entity.getQueueName());
        r.setMinCore(entity.getMinCore());
        r.setMinMem(entity.getMinMem());
        r.setMaxCore(entity.getMaxCore());
        r.setMaxMem(entity.getMaxMem());
        r.setAppNum(entity.getAppNum());
        r.setSchedulePolicy(entity.getSchedulePolicy());
        r.setWeight(entity.getWeight());
        r.setAllowPreemption(entity.getAllowPreemption());
        r.setAmShare(entity.getAmShare());
        r.setClusterId(entity.getClusterId());
        r.setCreateTime(entity.getCreateTime());
        return r;
    }

    public static List<YarnQueueResponse> fromList(List<ClusterYarnQueue> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(YarnQueueResponse::from).toList();
    }
}
