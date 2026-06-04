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

import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterRoleUserEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.enums.UserType;
import com.datasophon.dao.mapper.ClusterRoleUserMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterRoleUserService")
public class ClusterRoleUserServiceImpl extends ServiceImpl<ClusterRoleUserMapper, ClusterRoleUserEntity>
        implements
            ClusterRoleUserService {
    
    @Autowired
    private ClusterRoleUserMapper clusterRoleUserMapper;
    
    @Override
    public boolean isClusterManager(Integer userId, String clusterId) {
        List<ClusterRoleUserEntity> list = this.list(new QueryWrapper<ClusterRoleUserEntity>()
                .eq(Constants.DETAILS_USER_ID, userId)
                .eq(Constants.CLUSTER_ID, clusterId));
        if (Objects.nonNull(list) && list.size() == 1) {
            return true;
        }
        return false;
    }
    
    @Override
    public Result saveClusterManager(Integer clusterId, String userIds) {
        // 首先删除原有管理员
        this.remove(new QueryWrapper<ClusterRoleUserEntity>().eq(Constants.CLUSTER_ID, clusterId));
        if (StringUtils.isEmpty(userIds)) {
            // userIds 为空,表示取消授权
            return Result.success();
        }
        ArrayList<ClusterRoleUserEntity> list = new ArrayList<>();
        for (String userId : userIds.split(",")) {
            Integer id = Integer.parseInt(userId);
            ClusterRoleUserEntity clusterRoleUserEntity = new ClusterRoleUserEntity();
            clusterRoleUserEntity.setClusterId(clusterId);
            clusterRoleUserEntity.setUserId(id);
            clusterRoleUserEntity.setUserType(UserType.CLUSTER_MANAGER);
            list.add(clusterRoleUserEntity);
        }
        this.saveBatch(list);
        return Result.success();
    }
    
    @Override
    public List<UserInfoEntity> getAllClusterManagerByClusterId(Integer clusterId) {
        return clusterRoleUserMapper.getAllClusterManagerByClusterId(clusterId);
    }
}
