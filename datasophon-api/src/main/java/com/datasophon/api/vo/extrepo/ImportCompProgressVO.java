package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class ImportCompProgressVO {
    
    @Schema(description = "进度ID,用于后续查询进度")
    private Integer progressId;
    
    @Schema(description = "状态 0初始化 1表示成功 -1表示失败 -2表示进度对象不存在 2表示解析元数据 3解压安装包 4保存数据 5上传安装包到nexus 6上传镜像到nexus 7上传helm包到nexus")
    private int state;
    
    @Schema(description = "失败原因")
    private String error;
    
    @Schema(description = "总进度")
    private long total;
    
    @Schema(description = "已经执行的进度")
    private long step;
    
    @Schema(description = "缓存过期时间")
    private LocalDateTime expire;
    
    @Schema(description = "进度创建时间")
    private LocalDateTime createTime;
    
    public ImportCompProgressVO(Integer progressId) {
        this.progressId = progressId;
        total = 0;
        step = 0;
        createTime = LocalDateTime.now();
    }
    
    @Schema(description = "进度")
    public double getProgress() {
        if (state <= 0) {
            return 0d;
        }
        if (state == 1) {
            return 1d;
        }
        return total == 0 ? 0d : step * 1.0d / total;
    }
    
    @JsonIgnore
    public boolean isTimeout() {
        // 已经到了过期时间或者距离创建时间已经过期了1天(不可一天都还没有处理完）
        return (expire != null && LocalDateTime.now().isAfter(expire)) || LocalDateTime.now().isAfter(createTime.plusDays(1));
    }
}
