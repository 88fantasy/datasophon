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

import com.datasophon.api.service.ClusterZkService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterZk;
import com.datasophon.dao.mapper.ClusterZkMapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterZkService")
public class ClusterZkServiceImpl extends ServiceImpl<ClusterZkMapper, ClusterZk> implements ClusterZkService {
    
    @Autowired
    private ClusterZkMapper clusterZkMapper;
    
    @Override
    public Integer getMaxMyId(Integer clusterId) {
        return clusterZkMapper.getMaxMyId(clusterId);
    }
    
    @Override
    public List<ClusterZk> getAllZkServer(Integer clusterId) {
        return this.list(new QueryWrapper<ClusterZk>().eq(Constants.CLUSTER_ID, clusterId));
    }
}
