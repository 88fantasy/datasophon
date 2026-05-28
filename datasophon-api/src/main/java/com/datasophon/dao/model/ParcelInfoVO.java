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


package com.datasophon.dao.model;

import java.util.List;

import lombok.Data;

/**
 *
 * 第三方框架组件
 *
 *
 * @author zhenqin
 */
@Data
public class ParcelInfoVO {
    
    /**
     * Parcel Remote URL
     */
    String url;
    
    /**
     * Parcel 名称
     */
    String parcelName;
    
    /**
     * hash 256 验证
     */
    String hash;
    
    /**
     * md5 验证
     */
    String md5;
    
    /**
     * 依赖的 DDP 版本
     */
    String depends;
    
    /**
     * 支持的 DDP Frame 框架版本
     */
    String meta;
    
    /**
     * 内部包含的组件
     */
    List<ComponentVO> components;
    
    /**
     * 最后修改时间
     */
    long lastUpdated;
    
}
