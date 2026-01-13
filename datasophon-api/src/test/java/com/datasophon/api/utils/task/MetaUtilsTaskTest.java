package com.datasophon.api.utils.task;

import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.common.utils.TarUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author zhanghuangbin
 */
public class MetaUtilsTaskTest {


    @Test
    public void  testDecode() throws IOException {
        File file = new File("D:\\Desktop\\VOS集成测试\\门户1\\config_20260113105505.tar.gz");
        String password = "Ax75UKPE8rL7evFCIQzekA==";

        String unzipDir = null;
        unzipDir = TarUtils.decompressToTemp(file.getAbsolutePath());
        MetaUtils.decodeMatchedFiles(unzipDir, password);

        System.out.println("解压路径为" + unzipDir);
    }
}
