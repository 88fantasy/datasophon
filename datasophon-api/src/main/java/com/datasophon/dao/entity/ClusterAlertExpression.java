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
@TableName("t_ddh_cluster_alert_expression")
public class ClusterAlertExpression implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 自增 ID
     */
    @TableId
    private Long id;
    /**
     * 指标名称
     */
    private String name;
    /**
     * 监控指标表达式
     */
    private String expr;
    /**
     * 服务类别
     */
    private String serviceCategory;
    /**
     * 阈值类型  BOOL  INT  FLOAT  
     */
    private String valueType;
    /**
     * 是否预定义
     */
    private String isPredefined;
    /**
     * 表达式状态
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
    
}
