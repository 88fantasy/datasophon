package com.datasophon.api.controller.cluster;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author zhanghuangbin
 */

@RestController
@RequestMapping("cluster/k8sNamespace")
@Tag(name = "k8s 集群命名空间管理")
public class K8sClusterNamespaceController extends ApiController {

    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;

    @GetMapping("listByClusterId/{clusterId}")
    @Operation(summary = "根据集群 id 获取并更新命名空间列表")
    @ApiResponse(
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sClusterNamespace.class))}
    )
    public Result listByClusterId(@PathVariable Integer clusterId) {
        List<K8sClusterNamespace> namespaces = k8sClusterNamespaceService.listAndUpdateNamespaceByClusterId(clusterId);
        return Result.success(namespaces);
    }
}
