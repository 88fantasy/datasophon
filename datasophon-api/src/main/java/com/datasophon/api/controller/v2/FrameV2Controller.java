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
import com.datasophon.api.dto.ddl.UpdateDdlDTO;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.vo.frameinfo.FrameInfoVO;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 集群框架管理接口。
 *
 * <p>完整路径示例（context-path=/ddh，/api 前缀由 configurePathMatch 注入）：
 * <ul>
 *   <li>GET    {@code /ddh/api/v2/frame/services}       — 框架 + 嵌套服务列表</li>
 *   <li>DELETE {@code /ddh/api/v2/frame/service/{id}}   — 删除物理机服务</li>
 *   <li>DELETE {@code /ddh/api/v2/frame/k8s-service/{id}} — 删除 K8s 服务</li>
 *   <li>GET    {@code /ddh/api/v2/frame/service/{id}/ddl} — 读取服务 DDL</li>
 *   <li>PUT    {@code /ddh/api/v2/frame/service/{id}/ddl} — 更新服务 DDL</li>
 * </ul>
 */
@RestController
@RequestMapping("/v2/frame")
public class FrameV2Controller extends ApiController {
    
    @Autowired
    private FrameInfoService frameInfoService;
    
    @Autowired
    private FrameServiceService frameServiceService;
    
    @Autowired
    private FrameK8sServiceService frameK8sServiceService;
    
    @Autowired
    private DdlMetaService ddlMetaService;
    
    /** 返回所有框架及其下物理机 / K8s 服务列表（嵌套结构）。 */
    @GetMapping("/services")
    public ApiResponse<List<FrameInfoVO>> listFrameServices() {
        return ApiResponse.ok(frameInfoService.getAllClusterFrame());
    }
    
    /** 删除一条物理机框架服务。 */
    @UserPermission
    @DeleteMapping("/service/{id}")
    public ApiResponse<Void> deleteFrameService(@PathVariable Integer id) {
        frameServiceService.removeById(id);
        return ApiResponse.ok();
    }
    
    /** 删除一条 K8s 框架服务。 */
    @UserPermission
    @DeleteMapping("/k8s-service/{id}")
    public ApiResponse<Void> deleteFrameK8sService(@PathVariable Integer id) {
        frameK8sServiceService.removeById(id);
        return ApiResponse.ok();
    }
    
    /** 读取指定物理机服务的 service_ddl.json 内容。 */
    @GetMapping("/service/{id}/ddl")
    public ApiResponse<String> getFrameServiceDdl(@PathVariable Integer id) {
        return ApiResponse.ok(ddlMetaService.getServiceVosDdl(id));
    }
    
    /** 更新指定物理机服务的 service_ddl.json 内容。 */
    @UserPermission
    @PutMapping("/service/{id}/ddl")
    public ApiResponse<Void> updateFrameServiceDdl(
                                                   @PathVariable Integer id, @RequestBody UpdateDdlDTO dto) {
        ddlMetaService.updateServiceVosDdl(id, dto.getContent());
        return ApiResponse.ok();
    }
}
