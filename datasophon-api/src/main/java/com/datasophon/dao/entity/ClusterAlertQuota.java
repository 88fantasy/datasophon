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
import com.datasophon.dao.enums.QuotaState;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@TableName("t_ddh_cluster_alert_quota")
public class ClusterAlertQuota implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    private Integer id;
    /**
     * 告警指标名称
     */
    private String alertQuotaName;
    /**
     * 服务分类
     */
    private String serviceCategory;
    /**
     * 告警指标表达式
     */
    private String alertExpr;
    /**
     * 告警级别 1:警告2：异常
     */
    private AlertLevel alertLevel;
    /**
     * 告警组
     */
    private Integer alertGroupId;
    /**
     * 通知组
     */
    private Integer noticeGroupId;
    /**
     * 告警建议
     */
    private String alertAdvice;
    /**
     * 比较方式 !=;>;<
     */
    private String compareMethod;
    /**
     * 告警阀值
     */
    private Long alertThreshold;
    /**
     * 告警策略 1:单次2：连续
     */
    private Integer alertTactic;
    /**
     * 间隔时长 单位分钟
     */
    private Integer intervalDuration;
    /**
     * 触发时长 单位秒
     */
    private Integer triggerDuration;
    
    private String serviceRoleName;
    
    private QuotaState quotaState;
    
    private Date createTime;
    
    @TableField(exist = false)
    private Integer quotaStateCode;
    
    @TableField(exist = false)
    private String alertGroupName;
    
}
