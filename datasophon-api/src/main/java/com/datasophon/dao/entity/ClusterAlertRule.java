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

package com.datasophon.dao.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@TableName("t_ddh_cluster_alert_rule")
public class ClusterAlertRule implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 自增 ID
     */
    @TableId
    private Long id;
    /**
     * 表达式 ID
     */
    private Long expressionId;
    /**
     * 是否预定义
     */
    private String isPredefined;
    /**
     * 比较方式 如 大于 小于 等于 等
     */
    private String compareMethod;
    /**
     * 阈值
     */
    private String thresholdValue;
    /**
     * 持续时长
     */
    private Long persistentTime;
    /**
     * 告警策略：单次，连续
     */
    private String strategy;
    /**
     * 连续告警时 间隔时长
     */
    private Long repeatInterval;
    /**
     * 告警级别
     */
    private String alertLevel;
    /**
     * 告警描述
     */
    private String alertDesc;
    /**
     * 接收组 ID
     */
    private Long receiverGroupId;
    /**
     * 状态
     */
    private String state;
    /**
     * 是否删除
     */
    private String isDelete;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 修改时间
     */
    private Date updateTime;
    /**
     * 集群id
     */
    private Integer clusterId;
    
}
