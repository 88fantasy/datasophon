package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class ImportCompProgressVO {

    private Integer progressId;

    @Schema(description = "状态 0初始化 1表示成功 -1表示失败 2表示解析元数据 3解压安装包")
    private int state;

    private String error;

    private long total;

    private long step;

    public ImportCompProgressVO(Integer progressId) {
        this.progressId = progressId;
        total = 0;
        step = 0;
    }

    public double getProgress() {
        if (state <= 0) {
            return 0d;
        }
        if (state == 1) {
            return 1d;
        }
        return total == 0 ? 0d : step * 1.0d / total;
    }
}
