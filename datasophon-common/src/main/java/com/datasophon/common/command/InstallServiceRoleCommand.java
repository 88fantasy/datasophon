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


package com.datasophon.common.command;

import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.model.RunAs;
import com.datasophon.common.model.ServiceConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class InstallServiceRoleCommand extends BaseCommand implements Serializable {


    private static final long serialVersionUID = -521810628281824531L;

    private Map<Generators, List<ServiceConfig>> cofigFileMap;
    
    private Long deliveryId;
    
    private Integer normalSize;
    
    private String packageMd5;
    

    /**
     * 创建解压目录
     */
    private Boolean createDecompressDir;


    /**
     * 规范化后的文件夹名称
     */
    private String normalPkgDir;


    private RunAs runAs;
    
    private ServiceRoleType serviceRoleType;
    
    private List<Map<String, Object>> resourceStrategies;

    private Map<String,String> variables;

    private List<HookConfig> hooks;
    
}
