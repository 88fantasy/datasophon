package com.datasophon.api.controller.extrepo;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.extrepo.DagIdDto;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.vo.extrepo.InstallProgressDAG;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.utils.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanghuangbin
 * @date 2025/11/5
 */
@RestController
@RequestMapping("extrepo")
@Tag(name = "外部软件源(安装)")
public class ExtRepoInstallController extends ApiController {
    
    @Autowired
    private ExtRepoInstallDelegateService extRepoInstallDelegateService;
    
    @PostMapping("/validDeploymentFile")
    @Operation(summary = "校验部署文件")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ValidateResultVO.class))})
    public Result validDeploymentFile(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(extRepoInstallDelegateService.validDeploymentFile(dto));
    }
    
    @PostMapping("/deploy")
    @Operation(summary = "部署应用")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = InstallResult.class))})
    public Result deploy(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(extRepoInstallDelegateService.deploy(dto));
    }
    
    @PostMapping("/getDeployProgressDAG2")
    @Operation(summary = "获取部署进度DAG接口2")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = InstallProgressDAG.class))})
    public Result getDeployProgressDAG2(@RequestBody @Validated DagIdDto dto) {
        return Result.success(extRepoInstallDelegateService.getDeployProgressDAG2(dto.getDagId()));
    }
    
    @PostMapping("/redeploy")
    @Operation(summary = "重新运行dag")
    @ApiResponse(content = {@Content(mediaType = "application/json")})
    public Result redeploy(@RequestBody @Validated RunDagDto dto) {
        extRepoInstallDelegateService.redeploy(dto);
        return Result.success();
    }
    
}
