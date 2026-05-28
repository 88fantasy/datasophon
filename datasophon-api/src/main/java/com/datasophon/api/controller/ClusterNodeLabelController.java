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

import com.datasophon.api.service.ClusterNodeLabelService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterNodeLabelEntity;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/node/label")
public class ClusterNodeLabelController extends ApiController {
    
    @Autowired
    private ClusterNodeLabelService nodeLabelService;
    
    /**
     * save node label
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId) {
        List<ClusterNodeLabelEntity> list = nodeLabelService.queryClusterNodeLabel(clusterId);
        return Result.success(list);
    }
    
    /**
     * save node label
     */
    @RequestMapping("/save")
    public Result save(Integer clusterId, String nodeLabel) {
        return nodeLabelService.saveNodeLabel(clusterId, nodeLabel);
    }
    
    /**
     * delete node label
     */
    @RequestMapping("/delete")
    public Result delete(Integer nodeLabelId) {
        return nodeLabelService.deleteNodeLabel(nodeLabelId);
    }
    
    /**
     * assign node label
     */
    @RequestMapping("/assign")
    public Result assign(Integer nodeLabelId, String hostIds) {
        return nodeLabelService.assignNodeLabel(nodeLabelId, hostIds);
    }
}
