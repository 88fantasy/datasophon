package com.datasophon.api.utils.task;

import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author zhanghuangbin
 */
public class NexusUtilsTaskTest {


    @Test
    public void  upload() throws IOException {
        PropertiesPathUtils.resetPropertyFile();

        File dir = new File("D:\\Desktop\\VOS集成测试\\软件包");
        for (File file : dir.listFiles()) {
            NexusFileUtils.uploadFileToRawRepo("/packages/", file);
        }
    }
}
