package com.datasophon.api.controller.v2;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.instance.K8sTakeoverDTO;
import com.datasophon.api.service.k8s.DorisDatasourceDiscoveryService;
import com.datasophon.api.service.k8s.K8sTakeoverInstanceService;
import com.datasophon.api.service.k8s.K8sTakeoverRegisterService;
import com.datasophon.api.service.k8s.K8sTakeoverScanService;
import com.datasophon.api.vo.k8s.DorisDatasourceCandidate;
import com.datasophon.api.vo.k8s.K8sTakeoverRegisterResult;
import com.datasophon.api.vo.k8s.K8sTakeoverScanResult;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 接管现有 K8s 集群的服务端接口。
 *
 * <p>全流程只读目标集群：扫描 Helm release、发现 Doris 数据源、登记服务实例，
 * **不向目标集群写入任何内容**。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/k8s/takeover")
@Tag(name = "v2 K8s 集群接管")
public class ClusterK8sTakeoverV2Controller extends ApiController {

    private final K8sTakeoverScanService scanService;
    private final DorisDatasourceDiscoveryService datasourceService;
    private final K8sTakeoverRegisterService registerService;
    private final K8sTakeoverInstanceService instanceService;

    public ClusterK8sTakeoverV2Controller(K8sTakeoverScanService scanService,
                                          DorisDatasourceDiscoveryService datasourceService,
                                          K8sTakeoverRegisterService registerService,
                                          K8sTakeoverInstanceService instanceService) {
        this.scanService = scanService;
        this.datasourceService = datasourceService;
        this.registerService = registerService;
        this.instanceService = instanceService;
    }

    @GetMapping("/scan")
    @Operation(summary = "扫描目标集群已存在的服务，按 chart 名匹配框架服务定义")
    public ApiResponse<K8sTakeoverScanResult> scan(@PathVariable Integer clusterId) {
        return ApiResponse.ok(scanService.scan(clusterId));
    }

    @GetMapping("/doris/candidates")
    @Operation(summary = "发现 Doris 数据源候选地址")
    public ApiResponse<List<DorisDatasourceCandidate>> dorisCandidates(@PathVariable Integer clusterId) {
        return ApiResponse.ok(datasourceService.discover(clusterId));
    }

    @PostMapping("/doris/test")
    @Operation(summary = "测试 Doris 连通性，不落库")
    public ApiResponse<String> testDoris(@PathVariable Integer clusterId,
                                         @Valid @RequestBody K8sTakeoverDTO.DatasourceSave req) {
        String failure = datasourceService.testConnection(
                req.getHost(), req.getPort(), req.getUsername(), req.getPassword());
        return failure == null ? ApiResponse.ok("连接成功") : ApiResponse.fail(400, failure);
    }

    @PostMapping("/doris")
    @Operation(summary = "保存 Doris 数据源，连通性测试不通过则拒绝保存")
    public ApiResponse<Void> saveDoris(@PathVariable Integer clusterId,
                                       @Valid @RequestBody K8sTakeoverDTO.DatasourceSave req) {
        datasourceService.saveDatasource(clusterId, req.getHost(), req.getPort(),
                req.getDatabase(), req.getUsername(), req.getPassword());
        return ApiResponse.ok(null);
    }

    @PostMapping("/register")
    @Operation(summary = "提交接管登记，并探测各服务的 OTel job")
    public ApiResponse<List<K8sTakeoverRegisterResult>> register(@PathVariable Integer clusterId,
                                                                 @Valid @RequestBody K8sTakeoverDTO.Register req) {
        List<K8sTakeoverRegisterService.Binding> bindings = req.getBindings().stream()
                .map(b -> new K8sTakeoverRegisterService.Binding(
                        b.getReleaseName(), b.getNamespace(), b.getFrameServiceId(), b.getSourceKind()))
                .toList();
        return ApiResponse.ok(registerService.register(clusterId, bindings));
    }

    @DeleteMapping("/instance/{instanceId}")
    @Operation(summary = "取消接管：只移除平台登记记录，不影响目标集群")
    public ApiResponse<Void> cancelTakeover(@PathVariable Integer clusterId,
                                            @PathVariable Integer instanceId) {
        instanceService.cancelTakeover(clusterId, instanceId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/instance/{instanceId}/values")
    @Operation(summary = "只读反查接管实例的 helm values")
    public ApiResponse<String> readValues(@PathVariable Integer clusterId,
                                          @PathVariable Integer instanceId) {
        return ApiResponse.ok(instanceService.readValues(clusterId, instanceId));
    }
}
