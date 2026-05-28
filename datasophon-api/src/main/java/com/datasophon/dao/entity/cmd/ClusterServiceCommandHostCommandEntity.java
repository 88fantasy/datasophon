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


package com.datasophon.dao.entity.cmd;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datasophon.common.jackson.annotation.WithEnumDescription;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.RoleType;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName("t_ddh_cluster_service_command_host_command")
@Data
public class ClusterServiceCommandHostCommandEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    private String hostCommandId;
    /**
     * 指令名称
     */
    private String commandName;
    /**
     * 指令状态 1、正在运行2：成功3：失败
     */
    @WithEnumDescription(fieldNameTpl = "#field + 'Code'", field = "value")
    private CommandState commandState;

    /**
     * 指令进度
     */
    private Integer commandProgress;
    /**
     * 主机id
     */
    private String commandHostId;
    
    private String commandId;
    
    private String hostname;
    /**
     * 服务角色名称
     */
    private String serviceRoleName;
    
    private RoleType serviceRoleType;
    
    private String resultMsg;
    
    private Date createTime;
    
    private Integer commandType;

    private Integer sort;
    
}
