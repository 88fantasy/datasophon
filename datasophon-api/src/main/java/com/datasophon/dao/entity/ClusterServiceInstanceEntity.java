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

import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceState;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("t_ddh_cluster_service_instance")
@Data
public class ClusterServiceInstanceEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    private Integer id;
    /**
     * 集群id
     */
    private Integer clusterId;
    /**
     * 服务名称
     */
    private String serviceName;
    
    private String label;
    /**
     * 服务状态 1、待安装 2：正在运行  3：存在告警 4:存在异常
     */
    private ServiceState serviceState;

    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 创建时间
     */
    private Date createTime;
    
    private NeedRestart needRestart;
    
    private Integer frameServiceId;
    

    
    private Integer sortNum;



    @TableField(exist = false)
    private Integer serviceStateCode;


    @TableField(exist = false)
    private String dashboardUrl;

    @TableField(exist = false)
    private Integer alertNum;

    @TableField(exist = false)
    @Schema(description = "分类， ENVIRONMENT=基础环境, MIDDLEWARE=中间件, APPLICATION=应用")
    private String catalog;
}
