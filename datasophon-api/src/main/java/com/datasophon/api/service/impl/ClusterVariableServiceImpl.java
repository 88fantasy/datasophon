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


package com.datasophon.api.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.mapper.ClusterVariableMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("clusterVariableService")
public class ClusterVariableServiceImpl extends ServiceImpl<ClusterVariableMapper, ClusterVariable>
        implements
        ClusterVariableService {

    @Override
    public ClusterVariable getVariableByVariableName(Integer clusterId, String serviceName, String variableName) {
        return lambdaQuery()
                .eq(ClusterVariable::getServiceName, serviceName)
                .eq(ClusterVariable::getVariableName, variableName)
                .eq(ClusterVariable::getClusterId, clusterId)
                .one();
    }

    @Override
    public List<ClusterVariable> getVariables(Integer clusterId, String serviceName) {
        return lambdaQuery()
                .eq(ClusterVariable::getServiceName, serviceName)
                .eq(ClusterVariable::getClusterId, clusterId)
                .list();
    }
}
