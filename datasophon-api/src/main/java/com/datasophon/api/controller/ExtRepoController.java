package com.datasophon.api.controller;

import com.datasophon.api.dto.IntegerIdDTO;
import com.datasophon.api.dto.extrepo.DagIdDto;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.service.extrepo.ExtRepoInstallService;
import com.datasophon.api.service.extrepo.ExtRepoMetaService;
import com.datasophon.api.vo.extrepo.DeploymentDAG;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
import com.datasophon.api.vo.extrepo.InstallProgressDAG2;
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
@Tag(name = "外部软件源")
public class ExtRepoController extends ApiController {


    @Autowired
    private ExtRepoMetaService extRepoMetaService;


    @Autowired
    private ExtRepoInstallService extRepoInstallService;

    @PostMapping("/validMetaFile")
    @Operation(summary = "校验meta文件是否正确")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ValidateResultVO.class))})
    public Result validMetaFile(@RequestBody @Validated InstallComponentDTO info) {
        return Result.success(extRepoMetaService.validMetaFile(info));
    }

    @PostMapping("/validatePkgFile")
    @Operation(summary = "校验安装包文件是否正确")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ValidateResultVO.class))})
    public Result validatePkgFile(@RequestBody @Validated InstallComponentDTO info) {
        return Result.success(extRepoMetaService.validatePkgFile(info));
    }


    @PostMapping("/importCmp")
    @Operation(summary = "导入安装组件")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ImportCompProgressVO.class))})
    public Result importCmp(@RequestBody @Validated InstallComponentDTO info) {
        return Result.success(extRepoMetaService.importCmp(info));
    }


    @PostMapping("/queryProgress")
    @Operation(summary = "查询导入进度")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ImportCompProgressVO.class))})
    public Result queryProgress(@RequestBody @Validated IntegerIdDTO id) {
        return Result.success(extRepoMetaService.queryProgress(id.getId()));
    }

    @PostMapping("/buildDeploymentDAG")
    @Operation(summary = "查询部署依赖图")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = DeploymentDAG.class))})
    public Result buildDeploymentDAG(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(extRepoMetaService.buildDeploymentDAG(dto));
    }


    @PostMapping("/validDeploymentFile")
    @Operation(summary = "校验部署文件")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ValidateResultVO.class))})
    public Result validDeploymentFile(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(extRepoInstallService.validDeploymentFile(dto));
    }


    @PostMapping("/deploy")
    @Operation(summary = "部署应用")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = InstallResult.class))})
    public Result deploy(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(extRepoInstallService.deploy(dto));
    }



    @PostMapping("/getDeployProgressDAG2")
    @Operation(summary = "获取部署进度DAG接口2")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = InstallProgressDAG2.class))})
    public Result getDeployProgressDAG2(@RequestBody @Validated DagIdDto dto) {
        return Result.success(extRepoInstallService.getDeployProgressDAG2(dto.getDagId()));
    }


    @PostMapping("/redeploy")
    @Operation(summary = "重新运行dag")
    @ApiResponse(content = {@Content(mediaType = "application/json")})
    public Result redeploy(@RequestBody @Validated DagIdDto dto) {
        extRepoInstallService.redeploy(dto.getDagId());
        return Result.success();
    }


}
