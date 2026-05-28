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
import com.datasophon.dao.enums.RoleType;
import com.datasophon.dao.enums.ServiceRoleState;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("t_ddh_cluster_service_role_instance")
@Data
public class ClusterServiceRoleInstanceEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    private Integer id;
    /**
     * 服务角色名称
     */
    private String serviceRoleName;
    /**
     * 主机
     */
    private String hostname;
    /**
     * 服务角色状态 1:正在运行 2:停止 3:存在告警 4:退役中 5:已退役
     */
    private ServiceRoleState serviceRoleState;
    
    @TableField(exist = false)
    private Integer serviceRoleStateCode;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 服务id
     */
    private Integer serviceId;
    /**
     * 角色类型 1:master2:worker3:client
     */
    private RoleType roleType;
    /**
     * 集群id
     */
    private Integer clusterId;
    /**
     * 服务名称
     */
    private String serviceName;
    
    private Integer roleGroupId;
    
    private NeedRestart needRestart;
    
    @TableField(exist = false)
    private String roleGroupName;
    
}
