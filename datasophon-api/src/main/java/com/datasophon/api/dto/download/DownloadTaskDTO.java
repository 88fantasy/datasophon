package com.datasophon.api.dto.download;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 外部文件下载请求 DTO
 * @author zhanghuangbin
 * @date 2026/04/20
 */
@Data
public class DownloadTaskDTO {

    @NotBlank(message = "文件 URL 不能为空")
    @Schema(description = "文件 URL，支持scp://和 http/https://, eg: scp://username:password@host:port/path/to/file")
    private String url;

    @Schema(description = "自定义文件名(可选，不传则从 URL 解析)")
    private String fileName;

}
