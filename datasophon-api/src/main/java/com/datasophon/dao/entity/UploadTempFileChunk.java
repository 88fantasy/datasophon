package com.datasophon.dao.entity;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 临时文件文件
 * @author zhanghuangbin
 * @date 2025/11/5
 */
@Data
@TableName("t_ddh_upload_temp_file_chunk")
public class UploadTempFileChunk implements Serializable {
    
    @Schema(description = "主键")
    @TableId
    private Integer id;
    
    @Schema(description = "附件ID")
    private Integer attachId;
    
    @Schema(description = "分片序号")
    private Integer chunkNo;
    
    @Schema(description = "分片MD5")
    private String md5;
}
