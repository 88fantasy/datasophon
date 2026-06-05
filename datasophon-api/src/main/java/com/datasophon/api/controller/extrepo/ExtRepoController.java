package com.datasophon.api.controller.extrepo;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.IntegerIdDTO;
import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.service.extrepo.ExtRepoMetaService;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
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
 */
@RestController
@RequestMapping("extrepo")
@Tag(name = "外部软件源(导入制品信息)")
public class ExtRepoController extends ApiController {
    
    @Autowired
    private ExtRepoMetaService extRepoMetaService;
    
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
    
}
