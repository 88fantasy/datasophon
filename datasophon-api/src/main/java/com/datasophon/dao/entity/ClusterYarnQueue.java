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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@TableName("t_ddh_cluster_yarn_queue")
public class ClusterYarnQueue implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 
     */
    @TableId
    private Integer id;
    /**
     * 
     */
    private String queueName;
    /**
     * 
     */
    private Integer minCore;
    /**
     * 
     */
    private Integer minMem;
    /**
     * 
     */
    private Integer maxCore;
    /**
     * 
     */
    private Integer maxMem;
    /**
     * 
     */
    private Integer appNum;
    /**
     * 
     */
    private Integer weight;
    /**
     * 
     */
    private String schedulePolicy;
    /**
     * 1: true 2:false
     */
    private Integer allowPreemption;
    
    private Integer clusterId;
    
    private Date createTime;
    
    private String amShare;
    
    @TableField(exist = false)
    private String minResources;
    
    @TableField(exist = false)
    private String maxResources;
    
}
