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

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("t_ddh_frame_service")
@Data
@Accessors(chain = true)
public class FrameServiceEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    private Integer id;
    /**
     * 框架id
     */
    private Integer frameId;
    /**
     * 服务名称
     */
    private String serviceName;
    
    private String label;
    /**
     * 服务版本
     */
    private String serviceVersion;
    /**
     * 服务描述
     */
    private String serviceDesc;
    
    // package_name DB 列保留但停用：包名统一通过 arch 字段按主机架构解析，此处不再映射。
    @Deprecated
    @TableField(exist = false)
    private String packageName;
    
    private String arch;
    
    private String dependencies;
    
    private String serviceJson;
    
    private String serviceJsonMd5;
    
    private String serviceConfig;
    
    private String frameCode;
    
    private String configFileJson;
    
    private String configFileJsonMd5;
    
    private String decompressPackageName;
    
    @Deprecated
    private Integer sortNum;
    
    private String type;
    
    @TableField(exist = false)
    private Boolean installed;
    
    @Schema(description = "是否被选中")
    @TableField(exist = false)
    private Boolean selected;
    
}
