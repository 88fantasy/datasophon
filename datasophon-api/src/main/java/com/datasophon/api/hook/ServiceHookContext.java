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

package com.datasophon.api.hook;

import com.datasophon.common.enums.CommandType;

import java.util.HashMap;
import java.util.Map;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.Data;

@Data
public class ServiceHookContext {

    private String serviceName;

    private Integer clusterId;

    private CommandType commandType;

    private String commandId;

    private Map<String, Object> params;

    public <T> T getParamsAs(Class<T> clazz) {
        if (params == null) {
            return ReflectUtil.newInstance(clazz);
        }
        return BeanUtil.toBean(params, clazz);
    }

    public Map<String, Object> toConditionMap() {
        Map<String, Object> conditionMap = new HashMap<>();
        conditionMap.put("serviceName", serviceName);
        conditionMap.put("commandType", commandType);
        conditionMap.put("clusterId", clusterId);
        if (params != null) {
            conditionMap.putAll(params);
        }
        return conditionMap;
    }
}
