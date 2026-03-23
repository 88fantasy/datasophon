package com.datasophon.api.vo.tmpfile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author zhanghuangbin
 * @date 2025/11/6
 */
@Data
public class MergeProgressVO {

    @Schema(description = "状态 0初始状态 1完成 2处理中 -1异常")
    private int state;

    @Schema(description = "错误原因")
    private String error;

    @Schema(description = "总进度")
    private long total;

    @Schema(description = "合并字节数")
    private long merge;

    @Schema(description = "计算MD5进度")
    private long md5;


    @Schema(description = "缓存过期时间")
    private LocalDateTime expire;

    @Schema(description = "进度创建时间")
    private LocalDateTime createTime;


    public MergeProgressVO() {
        this(-1);
    }

    public MergeProgressVO(long total) {
        state = 0;
        merge = 0;
        md5 = 0;
        this.total = total;
        createTime = LocalDateTime.now();
    }

    public void plusMerge(long plus) {
        merge += plus;
    }

    public void plusMd5(long plus) {
        md5 += plus;
    }

    @Schema(description = "进度 0-1")
    public Double getProgress() {
        if (state <= 0) {
            return 0d;
        }
        if (state == 1) {
            return 1d;
        }
        return (md5 + merge) * 1.0 / (total * 2.0);
    }

    public boolean isTimeout() {
//        已经到了过期时间或者距离创建时间已经过期了1天(不可一天都还没有处理完）
        return expire != null && LocalDateTime.now().isAfter(expire) || LocalDateTime.now().plusDays(1).isAfter(createTime);
    }
}
