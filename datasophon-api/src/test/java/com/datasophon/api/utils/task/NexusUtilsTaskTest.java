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
        System.setProperty("devMode", "local");
        File workspace = new File("./");
        String path = PathUtils.join(workspace.getAbsolutePath(), "../conf/common.properties").toFile().getAbsolutePath();
        System.setProperty("commonPropertiesLocation", path);

        File dir = new File("D:\\Desktop\\VOS集成测试\\temp");


        for (File file : dir.listFiles()) {
            NexusFileUtils.uploadFileToRawRepo("/packages/", file);
        }
    }
}
