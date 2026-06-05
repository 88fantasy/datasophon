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

import com.datasophon.api.enums.Status;
import com.datasophon.api.service.AlertGroupService;
import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.ClusterAlertQuota;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("alert/group")
public class AlertGroupController extends ApiController {
    
    @Autowired
    private AlertGroupService alertGroupService;
    
    @Autowired
    private ClusterAlertQuotaService alertQuotaService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId, String alertGroupName, Integer page, Integer pageSize) {
        return alertGroupService.getAlertGroupList(clusterId, alertGroupName, page, pageSize);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        AlertGroupEntity alertGroup = alertGroupService.getById(id);
        
        return Result.success().put("alertGroup", alertGroup);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody AlertGroupEntity alertGroup) {
        alertGroup.setCreateTime(new Date());
        return alertGroupService.saveAlertGroup(alertGroup);
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody AlertGroupEntity alertGroup) {
        alertGroupService.updateById(alertGroup);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        
        // 校验是否绑定告警指标
        List<ClusterAlertQuota> list =
                alertQuotaService.lambdaQuery().in(ClusterAlertQuota::getAlertGroupId, Arrays.asList(ids)).list();
        if (list.size() > 0) {
            return Result.error(Status.ALERT_GROUP_TIPS_ONE.getMsg());
        }
        alertGroupService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
