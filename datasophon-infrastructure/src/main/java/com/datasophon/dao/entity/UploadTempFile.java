package com.datasophon.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

import static com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID;

/**
 * 临时文件文件
 * @author zhanghuangbin
 * @date 2025/11/5
 */
@Data
@TableName("t_ddh_upload_temp_file")
public class UploadTempFile implements Serializable {


    @TableId
    @Schema(description = "主键")
    private Integer id;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "content-type")
    private String contentType;

    @Schema(description = "附件大小")
    private Long byteCnt;

    @Schema(description = "附件大小描述")
    private String byteDesc;

    @Schema(description = "后缀")
    private String suffix;

    @Schema(description = "相对于临时目录的位置")
    private String path;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "上传状态 0表示附件未写入 1表示已经写入")
    private Integer status;

    @Schema(description = "文件md5")
    private String md5;

    @Schema(description = "上传方式 0整体上传 1分片上传")
    private Integer uploadType;

    @Schema(description = "分片大小")
    private Integer chunk;


    @Schema(description = "分片大小")
    @TableField(exist = false)
    private Long chunkSize;

}
