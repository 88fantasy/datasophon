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

package com.datasophon.common.model;

import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.enums.ServiceRoleType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.annotation.JSONField;

import lombok.Data;

@Data
public class ServiceRoleInfo implements Serializable, ServiceRoleResource {

    private String name;

    private ServiceRoleType roleType;

    private String cardinality;

    private Integer sortNum;

    private ServiceRoleRunner startRunner;

    private ServiceRoleRunner stopRunner;

    private ServiceRoleRunner statusRunner;

    private ExternalLink externalLink;

    private String hostname;

    private String hostCommandId;

    private Integer clusterId;

    private String parentName;

    private String frameCode;

    private Map<String, ArchInfo> archInfoMap;

    /**
     * 创建解压目录
     */
    private Boolean createDecompressDir;

    private Map<Generators, List<ServiceConfig>> configFileMap;

    private String logFile;

    /**
     * 该角色的监控端口对应的 ddl 业务参数名；OtelScrapeConfigBuilder 据此读取角色组 configJson
     * 里的实时值，读不到时退回该参数的 ddl defaultValue。留空表示该角色不暴露监控端口，不生成抓取任务。
     */
    private String jmxPortParam;

    private boolean isSlave = false;

    private CommandType commandType;

    private String masterHost;

    private Boolean enableRangerPlugin;

    private Integer serviceInstanceId;

    private RunAs runAs;

    private List<HookConfig> hooks;

    @Override
    public String getServiceName() {
        return parentName;
    }

    @Override
    public String getServiceRoleName() {
        return name;
    }

    /**
     * ServiceRoleInfo 是多架构模板，单一包名由 handler 经 archInfoMap 按主机架构解析。
     * 接口方法仅为满足 ServiceRoleResource 契约，master 侧不应读取此返回值。
     */
    @Override
    @JSONField(serialize = false)
    public String getPackageName() {
        throw new UnsupportedOperationException(
                "ServiceRoleInfo 不支持直接读取 packageName，请通过 archInfoMap 按主机架构解析");
    }

    @Override
    @JSONField(serialize = false)
    public String getDecompressPackageName() {
        throw new UnsupportedOperationException(
                "ServiceRoleInfo 不支持直接读取 decompressPackageName，请通过 archInfoMap 按主机架构解析");
    }

    public List<HookConfig> getMatchedHooks(HookType... types) {
        List<HookType> typeList = Arrays.asList(types);
        List<HookConfig> tmp = hooks == null ? new ArrayList<>(0) : hooks;
        return tmp.stream().filter(hook -> typeList.contains(hook.getType())).toList();
    }
}
