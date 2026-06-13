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
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.dto.upload.BigFileDTO;
import com.datasophon.api.dto.upload.CheckChunkDTO;
import com.datasophon.api.dto.upload.MergeChunkDTO;
import com.datasophon.api.dto.v2.UploadedFileResponse;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.extrepo.ExtRepoMetaService;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.api.vo.tmpfile.MergeProgressVO;
import com.datasophon.dao.entity.UploadTempFile;
import com.datasophon.dao.entity.UploadTempFileChunk;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * v2 集群部署接口 — 上传文件 + 部署清单校验 + 执行部署 + 部署包分片上传。
 * 方法体全量委托现有 service，零业务改动。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/deploy")
public class ClusterDeployV2Controller extends ApiController {
    
    private final UploadTempFileService uploadTempFileService;
    private final ExtRepoInstallDelegateService extRepoInstallDelegateService;
    private final ExtRepoMetaService extRepoMetaService;
    
    public ClusterDeployV2Controller(
                                     UploadTempFileService uploadTempFileService,
                                     ExtRepoInstallDelegateService extRepoInstallDelegateService,
                                     ExtRepoMetaService extRepoMetaService) {
        this.uploadTempFileService = uploadTempFileService;
        this.extRepoInstallDelegateService = extRepoInstallDelegateService;
        this.extRepoMetaService = extRepoMetaService;
    }
    
    // ─── 切片 7a：部署清单 ──────────────────────────────────────────────────
    
    /** 上传单个文件（清单 yaml），返回 UploadedFileResponse（含 id 字段）。 */
    @PostMapping("/upload")
    public ApiResponse<UploadedFileResponse> upload(
                                                    @PathVariable Integer clusterId,
                                                    @RequestPart("file") MultipartFile file) {
        UploadTempFile entity = uploadTempFileService.upload(file);
        return ApiResponse.ok(UploadedFileResponse.from(entity));
    }
    
    /** 校验部署清单，返回制品/版本预览。 */
    @PostMapping("/validate-deployment-file")
    public ApiResponse<ValidateResultVO> validateDeploymentFile(
                                                                @PathVariable Integer clusterId,
                                                                @RequestBody @Valid DeployRequest req) {
        ValidateResultVO result = extRepoInstallDelegateService.validDeploymentFile(buildDeployDto(clusterId, req));
        return ApiResponse.ok(result);
    }
    
    /** 执行部署，返回 InstallResult（含 dagId），前端跳 DAG 图。 */
    @PostMapping("/deploy")
    public ApiResponse<InstallResult> deploy(
                                             @PathVariable Integer clusterId,
                                             @RequestBody @Valid DeployRequest req) {
        InstallResult result = extRepoInstallDelegateService.deploy(buildDeployDto(clusterId, req));
        return ApiResponse.ok(result);
    }
    
    // ─── 切片 7b：部署包（元数据/包校验 + 导入） ───────────────────────────
    
    /** 校验配置元数据文件（meta yaml）。 */
    @PostMapping("/valid-meta-file")
    public ApiResponse<ValidateResultVO> validMetaFile(
                                                       @PathVariable Integer clusterId,
                                                       @RequestBody @Valid InstallComponentRequest req) {
        ValidateResultVO result = extRepoMetaService.validMetaFile(buildInstallDto(req));
        return ApiResponse.ok(result);
    }
    
    /** 校验部署包文件（安装包完整性）。 */
    @PostMapping("/validate-pkg-file")
    public ApiResponse<ValidateResultVO> validatePkgFile(
                                                         @PathVariable Integer clusterId,
                                                         @RequestBody @Valid InstallComponentRequest req) {
        ValidateResultVO result = extRepoMetaService.validatePkgFile(buildInstallDto(req));
        return ApiResponse.ok(result);
    }
    
    /** 触发导入安装组件（异步），返回 ImportCompProgressVO（含 progressId）。 */
    @PostMapping("/import-cmp")
    public ApiResponse<ImportCompProgressVO> importCmp(
                                                       @PathVariable Integer clusterId,
                                                       @RequestBody @Valid InstallComponentRequest req) {
        ImportCompProgressVO result = extRepoMetaService.importCmp(buildInstallDto(req));
        return ApiResponse.ok(result);
    }
    
    /** 查询导入进度。 */
    @PostMapping("/query-progress")
    public ApiResponse<ImportCompProgressVO> queryProgress(
                                                           @PathVariable Integer clusterId,
                                                           @RequestBody @Valid ProgressIdRequest req) {
        ImportCompProgressVO result = extRepoMetaService.queryProgress(req.getProgressId());
        return ApiResponse.ok(result);
    }
    
    // ─── 切片 7b：部署包分片上传 ────────────────────────────────────────────
    
    /** 创建分片上传任务（支持 MD5 秒传），返回 UploadedFileResponse（含 id/chunkSize/uploadType）。 */
    @PostMapping("/create-shard-task")
    public ApiResponse<UploadedFileResponse> createShardTask(
                                                             @PathVariable Integer clusterId,
                                                             @RequestBody @Valid BigFileDTO info) {
        UploadTempFile entity = uploadTempFileService.createShardUploadTask(info);
        return ApiResponse.ok(UploadedFileResponse.from(entity));
    }
    
    /** 上传单个分片（multipart）。 */
    @PostMapping("/upload-chunk")
    public ApiResponse<Object> uploadChunk(
                                           @PathVariable Integer clusterId,
                                           @NotNull(message = "分片不能为空") @RequestPart("chunk") MultipartFile chunk,
                                           @NotNull(message = "chunkNo 不能为空") Integer chunkNo,
                                           @NotNull(message = "attachId 不能为空") Integer attachId,
                                           String md5) {
        com.datasophon.api.dto.upload.ChunkDTO info = new com.datasophon.api.dto.upload.ChunkDTO();
        info.setChunk(chunk);
        info.setChunkNo(chunkNo);
        info.setAttachId(attachId);
        info.setMd5(md5);
        UploadTempFileChunk result = uploadTempFileService.uploadChunk(info);
        return ApiResponse.ok(result);
    }
    
    /** 检查分片是否已上传（断点续传去重）。 */
    @PostMapping("/is-chunk-uploaded")
    public ApiResponse<Boolean> isChunkUploaded(
                                                @PathVariable Integer clusterId,
                                                @RequestBody CheckChunkDTO dto) {
        UploadTempFileChunk result = uploadTempFileService.isChunkUploaded(dto);
        return ApiResponse.ok(result != null);
    }
    
    /** 触发合并分片（推荐 async=true，避免请求超时）。 */
    @PostMapping("/merge-chunk")
    public ApiResponse<Object> mergeChunk(
                                          @PathVariable Integer clusterId,
                                          @RequestBody @Valid MergeChunkDTO vo) {
        MergeProgressVO result = uploadTempFileService.mergeChunk(vo);
        return ApiResponse.ok(result);
    }
    
    /** 查询分片合并进度。 */
    @PostMapping("/query-merge-progress")
    public ApiResponse<MergeProgressVO> queryMergeProgress(
                                                           @PathVariable Integer clusterId,
                                                           @RequestBody @Valid ProgressIdRequest req) {
        MergeProgressVO result = uploadTempFileService.queryMergeProgress(req.getProgressId());
        return ApiResponse.ok(result);
    }
    
    // ─── 内部 DTO / 工具 ────────────────────────────────────────────────────
    
    private static DeploymentDTO buildDeployDto(Integer clusterId, DeployRequest req) {
        DeploymentDTO dto = new DeploymentDTO();
        dto.setClusterId(clusterId);
        dto.setDeployFileId(req.getDeployFileId());
        dto.setContentDecodePasswd(req.getContentDecodePasswd() != null ? req.getContentDecodePasswd() : "");
        return dto;
    }
    
    private static InstallComponentDTO buildInstallDto(InstallComponentRequest req) {
        InstallComponentDTO dto = new InstallComponentDTO();
        dto.setMeteFileId(req.getMeteFileId());
        dto.setPkgFileId(req.getPkgFileId());
        dto.setContentDecodePasswd(req.getContentDecodePasswd() != null ? req.getContentDecodePasswd() : "");
        return dto;
    }
    
    /** 部署清单操作请求体（clusterId 由路径变量提供）。 */
    @Data
    public static class DeployRequest {
        @NotNull(message = "部署文件ID不能为空")
        private Integer deployFileId;
        /** 配置文件密码，可为空。 */
        private String contentDecodePasswd;
    }
    
    /** 安装组件操作请求体。 */
    @Data
    public static class InstallComponentRequest {
        @NotNull(message = "meta文件ID不能为空")
        private Integer meteFileId;
        /** 安装包文件ID，可为空（不上传到 nexus 时）。 */
        private Integer pkgFileId;
        private String contentDecodePasswd;
    }
    
    /** 进度查询请求体。 */
    @Data
    public static class ProgressIdRequest {
        @NotNull(message = "progressId不能为空")
        private Integer progressId;
    }
}
