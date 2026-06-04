package com.datasophon.api.controller;

import com.datasophon.api.dto.IdDTO;
import com.datasophon.api.dto.IntegerIdDTO;
import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.dto.upload.BigFileDTO;
import com.datasophon.api.dto.upload.CheckChunkDTO;
import com.datasophon.api.dto.upload.ChunkDTO;
import com.datasophon.api.dto.upload.MergeChunkDTO;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.vo.extrepo.DownloadProgressVO;
import com.datasophon.api.vo.tmpfile.MergeProgressVO;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.UploadTempFile;
import com.datasophon.dao.entity.UploadTempFileChunk;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author zhanghuangbin
 * @date 2025/11/5
 */
@RestController
@RequestMapping("tempfile")
@Tag(name = "临时文件上传")
public class TempFileController extends ApiController {
    
    @Autowired
    private UploadTempFileService uploadTempFileService;
    
    @PostMapping("/upload")
    @Operation(summary = "上传附件", description = "整个文件一起上传")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = UploadTempFile.class))})
    public Result uploadFile(@Validated @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        return Result.success(uploadTempFileService.upload(file));
    }
    
    @PostMapping("/createShardUploadTask")
    @Operation(summary = "新建分片上传任务")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = UploadTempFile.class))})
    public Result createShardUploadTask(@RequestBody @Validated BigFileDTO info) {
        return Result.success(uploadTempFileService.createShardUploadTask(info));
    }
    
    @PostMapping("/uploadChunk")
    @Operation(summary = "上传分片")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = UploadTempFileChunk.class))})
    @Validated
    public Result uploadChunk(
                              @NotNull(message = "分片不能为空") @RequestPart("chunk") MultipartFile chunk,
                              @NotNull(message = "chunkNo 不能为空") @Schema(description = "分片索引，0-base") Integer chunkNo,
                              @NotNull(message = "attachId 不能为空") Integer attachId,
                              String md5) {
        ChunkDTO info = new ChunkDTO();
        info.setChunk(chunk);
        info.setChunkNo(chunkNo);
        info.setAttachId(attachId);
        info.setMd5(md5);
        return Result.success(uploadTempFileService.uploadChunk(info));
    }
    
    @PostMapping("/isChunkUploaded")
    @Operation(summary = "分片是否已经上传")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = UploadTempFileChunk.class))})
    public Result mergeChunk(@RequestBody @Validated CheckChunkDTO vo) {
        return Result.success(uploadTempFileService.isChunkUploaded(vo));
    }
    
    @PostMapping("/mergeChunk")
    @Operation(summary = "合并分片")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = MergeProgressVO.class))})
    public Result mergeChunk(@RequestBody @Validated MergeChunkDTO vo) {
        return Result.success(uploadTempFileService.mergeChunk(vo));
    }
    
    @PostMapping("/queryMergeProgress")
    @Operation(summary = "查询合并进度")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = MergeProgressVO.class))})
    public Result queryMergeProgress(@RequestBody @Validated IntegerIdDTO id) {
        return Result.success(uploadTempFileService.queryMergeProgress(id.getId()));
    }
    
    @PostMapping("/downloadFromUrl")
    @Operation(summary = "从外部 URL 下载文件", description = "支持 scp://和 http/https://协议")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = DownloadProgressVO.class))})
    public Result downloadFromUrl(@RequestBody @Validated DownloadTaskDTO dto) {
        return Result.success(uploadTempFileService.createDownloadTask(dto));
    }
    
    @PostMapping("/queryDownloadProgress")
    @Operation(summary = "查询下载进度")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = DownloadProgressVO.class))})
    public Result queryDownloadProgress(@RequestBody @Validated IdDTO id) {
        return Result.success(uploadTempFileService.queryProgress(id.getId()));
    }
    
    @PostMapping("/cancelDownload")
    @Operation(summary = "取消下载")
    public Result cancelDownload(@RequestBody @Validated IdDTO id) {
        uploadTempFileService.cancelDownload(id.getId());
        return Result.success();
    }
}
