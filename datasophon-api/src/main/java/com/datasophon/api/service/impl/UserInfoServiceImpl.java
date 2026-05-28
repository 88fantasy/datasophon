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
import com.datasophon.api.service.UserInfoService;
import com.datasophon.api.utils.CheckUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.EncryptionUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.mapper.UserInfoMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("userInfoService")
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfoEntity> implements UserInfoService {
    
    @Autowired
    private UserInfoMapper userMapper;
    
    @Override
    public UserInfoEntity queryUser(String username, String password) {
        String md5 = EncryptionUtils.getMd5(password);
        return userMapper.selectOne(new QueryWrapper<UserInfoEntity>()
                .eq(Constants.USERNAME, username)
                .eq(Constants.PASSWORD, md5));
    }
    
    @Override
    public Result createUser(UserInfoEntity userInfo) {
        // check all user params
        String msg = this.checkUserParams(userInfo.getUsername(), userInfo.getPassword(), userInfo.getEmail(),
                userInfo.getPhone());
        if (!StringUtils.isEmpty(msg)) {
            return Result.error(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), msg);
        }
        // UserInfoEntity authUser = SecurityUtils.getAuthUser();
        // if (!SecurityUtils.isAdmin(authUser)) {
        // return Result.error(Status.USER_NO_OPERATION_PERM.getCode(), Status.USER_NO_OPERATION_PERM.getMsg());
        // }
        // 用户名判重
        List<UserInfoEntity> list =
                this.list(new QueryWrapper<UserInfoEntity>().eq(Constants.USERNAME, userInfo.getUsername()));
        if (Objects.nonNull(list) && list.size() >= 1) {
            return Result.error(Status.USER_NAME_EXIST.getCode(), Status.USER_NAME_EXIST.getMsg());
        }
        userInfo.setCreateTime(new Date());
        userInfo.setPassword(EncryptionUtils.getMd5(userInfo.getPassword()));
        this.save(userInfo);
        return Result.success();
    }
    
    /**
     * @param userName
     * @param password
     * @param email
     * @param phone
     * @return if check failed return the field, otherwise return null
     */
    private String checkUserParams(String userName, String password, String email, String phone) {
        
        String msg = null;
        if (!CheckUtils.checkUserName(userName)) {
            
            msg = userName;
        } else if (!CheckUtils.checkPassword(password)) {
            
            msg = password;
        } else if (!CheckUtils.checkEmail(email)) {
            
            msg = email;
        } else if (!CheckUtils.checkPhone(phone)) {
            
            msg = phone;
        }
        
        return msg;
    }
    
    @Override
    public Result getUserListByPage(String username, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        List<UserInfoEntity> list = this.list(
                new QueryWrapper<UserInfoEntity>().like(StringUtils.isNotBlank(username), Constants.USERNAME, username)
                        .last("limit " + offset + "," + pageSize));
        long total = this.count(new QueryWrapper<UserInfoEntity>().like(StringUtils.isNotBlank(username),
                Constants.USERNAME, username));
        return Result.success().put(Constants.DATA, list).put(Constants.TOTAL, total);
    }
}
