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

package com.datasophon.api.controller;

import com.datasophon.api.service.AlertGroupService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.common.utils.IdUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("frame/service/role")
public class FrameServiceRoleController extends ApiController {
    
    @Autowired
    private FrameServiceRoleService frameServiceRoleService;
    
    @Autowired
    private AlertGroupService alertGroupService;
    
    /**
     * 查询服务对应的角色列表
     */
    @RequestMapping("/getServiceRoleList")
    public Result getServiceRoleOfMaster(Integer clusterId, String serviceIds, Integer serviceRoleType) {
        return Result.success(frameServiceRoleService.getServiceRoleList(clusterId, IdUtils.toIdList(serviceIds), serviceRoleType));
    }
    
    @RequestMapping("/getNonMasterRoleList")
    public Result getNonMasterRoleList(Integer clusterId, String serviceIds) {
        return frameServiceRoleService.getNonMasterRoleList(clusterId, serviceIds);
    }
    
    @RequestMapping("/getServiceRoleByServiceName")
    public Result getServiceRoleByServiceName(Integer clusterId, Integer alertGroupId) {
        AlertGroupEntity alertGroupEntity = alertGroupService.getById(alertGroupId);
        return frameServiceRoleService.getServiceRoleByServiceName(clusterId, alertGroupEntity.getAlertGroupCategory());
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        FrameServiceRoleEntity frameServiceRole = frameServiceRoleService.getById(id);
        
        return Result.success().put("frameServiceRole", frameServiceRole);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody FrameServiceRoleEntity frameServiceRole) {
        frameServiceRoleService.save(frameServiceRole);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody FrameServiceRoleEntity frameServiceRole) {
        frameServiceRoleService.updateById(frameServiceRole);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        frameServiceRoleService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
