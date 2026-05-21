package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 下载进度 VO
 * @author zhanghuangbin
 * @date 2026/04/20
 */
@Data
@NoArgsConstructor
public class DownloadProgressVO {

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "状态 0 初始状态 1 完成 2 下载中 3 取消中 -1 异常 -2 已取消 -3缓存已经过期")
    private int state;

    @Schema(description = "错误原因")
    private String error;

    @Schema(description = "总字节数")
    private long total;

    @Schema(description = "已下载字节数")
    private long downloaded;

    @Schema(description = "缓存过期时间")
    private LocalDateTime expire;


    @Schema(description = "进度创建时间")
    private  LocalDateTime createTime = LocalDateTime.now();

    @Schema(description = "是否取消")
    private boolean cancel;

    @Schema(description = "下载附件的ID")
    private Integer attachId;

    @Schema(description = "content type")
    private String contentType;

    @Schema(description = "文件名称")
    private String fileName;

    public DownloadProgressVO(String taskId) {
        this.taskId = taskId;
        this.state = 0;
        this.downloaded = 0;
        this.total = 0;
    }

    public void plusDownloaded(long plus) {
        downloaded += plus;
    }

    @Schema(description = "进度 0-1")
    public Double getProgress() {
        if (state <= 0) {
            return 0d;
        }
        if (state == 1) {
            return 1d;
        }
        if (total <= 0) {
            return 0d;
        }
        return downloaded * 1.0 / total;
    }

    public boolean isTimeout() {
        // 已经到了过期时间或者距离创建时间已经过期了 1 天
        return (expire != null && expire.isBefore(LocalDateTime.now())) || LocalDateTime.now().isAfter(createTime.plusDays(1));
    }
}
