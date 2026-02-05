package com.datasophon.common.storage;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zhanghuangbin
 */
@Data
@NoArgsConstructor
public class DownloadResult {


    private boolean change;

    private String target;

    private String md5;


}
