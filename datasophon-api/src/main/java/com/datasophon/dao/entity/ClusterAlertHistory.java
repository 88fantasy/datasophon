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

import com.datasophon.dao.enums.AlertLevel;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@Builder
@TableName("t_ddh_cluster_alert_history")
@NoArgsConstructor
@AllArgsConstructor
public class ClusterAlertHistory implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    private Integer id;
    /**
     * 告警组
     */
    private String alertGroupName;
    /**
     * 告警指标
     */
    private String alertTargetName;
    /**
     * 告警详情
     */
    private String alertInfo;
    /**
     * 告警建议
     */
    private String alertAdvice;
    /**
     * 主机
     */
    private String hostname;
    /**
     * 告警级别 1：警告2：异常
     */
    private AlertLevel alertLevel;
    /**
     * 是否处理 1:未处理2：已处理
     */
    private Integer isEnabled;
    /**
     * 集群服务角色实例id
     */
    private Integer serviceRoleInstanceId;
    /**
     * 集群服务实例id
     */
    private Integer serviceInstanceId;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 集群id
     */
    private Integer clusterId;
    
}
