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

package com.datasophon.api.controller.cmd;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * K8s 服务命令执行记录
 *
 * @author zhanghuangbin
 */
@RestController
@RequestMapping("cluster/k8sService/command")
@Tag(name = "K8s 服务命令管理")
public class ClusterK8sServiceCommandController extends ApiController {
    
    @Autowired
    private ClusterK8sServiceCommandService clusterK8sServiceCommandService;
    
    /**
     * 分页查询 K8s 服务命令列表
     */
    @PostMapping("findCommandByPage")
    @Operation(summary = "分页查询 K8s 服务命令列表")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ClusterK8sServiceCommandEntity.class))})
    public Result findCommandByPage(Integer clusterId, String serviceName, Integer page, Integer pageSize) {
        IPage<ClusterK8sServiceCommandEntity> result = clusterK8sServiceCommandService.findCommandByPage(clusterId, serviceName, page, pageSize);
        return Result.success(result.getTotal(), result.getRecords());
    }
}
