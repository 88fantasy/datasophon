package com.datasophon.api.controller.extrepo;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.ServiceRoleQueryDTO;
import com.datasophon.api.service.extrepo.PhysicalProductInstallService;
import com.datasophon.common.utils.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
@Tag(name = "外部软件源(VOS制品接口)")
public class ExtRepoPhysicalInstallController extends ApiController {
    
    @Autowired
    private PhysicalProductInstallService physicalProductActionService;
    
    @PostMapping("/listNewestByDeployment")
    @Operation(summary = "获取最新的服务列表(并更加部署清单对服务列表进行勾选)")
    @ApiResponse(content = {@Content(mediaType = "application/json")})
    public Result listNewestByDeployment(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(physicalProductActionService.listNewestByDeployment(dto));
    }
    
    @PostMapping("/getServiceRoleListByDeployment")
    @Operation(summary = "获取服务角色列表(并根据部署清单对host进行填充)")
    @ApiResponse(content = {@Content(mediaType = "application/json")})
    public Result getServiceRoleListByDeployment(@RequestBody @Validated ServiceRoleQueryDTO dto) {
        return Result.success(physicalProductActionService.getServiceRoleListByDeployment(dto));
    }
    
    @PostMapping("/getNonMasterRoleListByDeployment")
    @Operation(summary = "获取非master服务角色列表(并根据部署清单对host进行填充)")
    @ApiResponse(content = {@Content(mediaType = "application/json")})
    public Result getNonMasterRoleListByDeployment(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(physicalProductActionService.getNonMasterRoleListByDeployment(dto));
    }
    
}
