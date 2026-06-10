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

package com.datasophon.api.controller.v2;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * v2 DAG 命令相关接口（命令历史列表 + DAG 图数据 + 重新运行）。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}")
public class ClusterDagV2Controller extends ApiController {
    
    private final DAGService dagService;
    
    private final ExtRepoInstallDelegateService extRepoInstallDelegateService;
    
    public ClusterDagV2Controller(DAGService dagService,
                                  ExtRepoInstallDelegateService extRepoInstallDelegateService) {
        this.dagService = dagService;
        this.extRepoInstallDelegateService = extRepoInstallDelegateService;
    }
    
    // ── 命令历史列表（切片 6a）─────────────────────────────────────
    
    /**
     * 分页查询集群命令历史列表。
     */
    @GetMapping("/command/list")
    public ApiResponse<IPage<DagDefinitionEntity>> listCommands(
                                                                @PathVariable Integer clusterId,
                                                                @RequestParam(defaultValue = "1") Integer page,
                                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(dagService.findDagByPage(clusterId, page, pageSize));
    }
    
    // ── DAG 图可视化（切片 6b）────────────────────────────────────
    
    /**
     * 获取指定 DAG 的节点/边/状态数据（用于前端 x6 图渲染）。
     */
    @GetMapping("/dag/{dagId}/graph")
    public ApiResponse<Object> getDagGraph(
                                           @PathVariable Integer clusterId,
                                           @PathVariable String dagId) {
        return ApiResponse.ok(extRepoInstallDelegateService.getDeployProgressDAG2(dagId));
    }
    
    /**
     * 重新运行指定 DAG（restart=true 跳过已成功节点）。
     */
    @PostMapping("/dag/{dagId}/redeploy")
    public ApiResponse<Void> redeployDag(
                                         @PathVariable Integer clusterId,
                                         @PathVariable String dagId) {
        RunDagDto dto = new RunDagDto();
        dto.setDagId(dagId);
        dto.setRestart(true);
        extRepoInstallDelegateService.redeploy(dto);
        return ApiResponse.ok(null);
    }
}
