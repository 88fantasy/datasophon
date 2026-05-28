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

import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.enums.QuotaState;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/alert/quota")
public class ClusterAlertQuotaController extends ApiController {
    
    @Autowired
    private ClusterAlertQuotaService clusterAlertQuotaService;
    
    /**
     * list alert quota
     */
    @RequestMapping("/list")
    public Result info(Integer clusterId, Integer alertGroupId, String quotaName, Integer page, Integer pageSize) {
        return clusterAlertQuotaService.getAlertQuotaList(clusterId, alertGroupId, quotaName, page, pageSize);
    }
    
    /**
     * enable alert quota
     */
    @RequestMapping("/start")
    public Result start(Integer clusterId, String alertQuotaIds) {
        clusterAlertQuotaService.start(clusterId, alertQuotaIds);
        return Result.success();
    }
    
    /**
     * disable alert quota
     */
    @RequestMapping("/stop")
    public Result stop(Integer clusterId, String alertQuotaIds) {
        clusterAlertQuotaService.stop(clusterId, alertQuotaIds);
        return Result.success();
    }
    
    /**
     * save alert quota
     */
    @RequestMapping("/save")
    public Result save(@RequestBody ClusterAlertQuota clusterAlertQuota) {
        
        clusterAlertQuotaService.saveAlertQuota(clusterAlertQuota);
        return Result.success();
    }
    
    /**
     * update alert quota
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterAlertQuota clusterAlertQuota) {
        clusterAlertQuota.setQuotaState(QuotaState.WAIT_TO_UPDATE);
        clusterAlertQuotaService.updateById(clusterAlertQuota);
        
        return Result.success();
    }
    
    /**
     * delete alert quota
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        clusterAlertQuotaService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
