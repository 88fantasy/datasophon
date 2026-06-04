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

import com.datasophon.api.enums.Status;
import com.datasophon.api.service.ClusterRackService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterRack;
import com.datasophon.dao.mapper.ClusterHostMapper;
import com.datasophon.dao.mapper.ClusterRackMapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterRackService")
public class ClusterRackServiceImpl extends ServiceImpl<ClusterRackMapper, ClusterRack> implements ClusterRackService {
    
    @Autowired
    private ClusterHostMapper clusterHostMapper;
    
    @Override
    public List<ClusterRack> queryClusterRack(Integer clusterId) {
        return this.list(new QueryWrapper<ClusterRack>().eq(Constants.CLUSTER_ID, clusterId));
    }
    
    @Override
    public void saveRack(Integer clusterId, String rack) {
        ClusterRack clusterRack = new ClusterRack();
        clusterRack.setRack(rack);
        clusterRack.setClusterId(clusterId);
        this.save(clusterRack);
    }
    
    @Override
    public Result deleteRack(Integer rackId) {
        ClusterRack clusterRack = this.getById(rackId);
        if (rackInUse(clusterRack)) {
            return Result.error(Status.RACK_IS_USING.getMsg());
        }
        this.removeById(rackId);
        return Result.success();
    }
    
    @Override
    public void createDefaultRack(Integer clusterId) {
        ClusterRack clusterRack = new ClusterRack();
        clusterRack.setRack("/default-rack");
        clusterRack.setClusterId(clusterId);
        this.save(clusterRack);
    }
    
    private boolean rackInUse(ClusterRack clusterRack) {
        List<ClusterHostDO> list =
                clusterHostMapper.getClusterHostByRack(clusterRack.getClusterId(), clusterRack.getRack());
        return !list.isEmpty();
    }
    
}
