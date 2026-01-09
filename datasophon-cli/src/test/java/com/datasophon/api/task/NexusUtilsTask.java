package com.datasophon.api.task;

import cn.hutool.core.util.StrUtil;
import com.datasophon.common.utils.NexusFileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author zhanghuangbin
 */
public class NexusUtilsTask {


    @Test
    public void  upload() throws IOException {
        System.setProperty("debug", "true");
        File workspace = new File(".");
        File dir = new File("D:\\Desktop\\VOS集成测试\\temp");

        String path = System.getProperty("commonPropertiesLocation");

        for (File file : dir.listFiles()) {
            NexusFileUtils.uploadFileToRawRepo("/packages/", file);
        }
    }
}
