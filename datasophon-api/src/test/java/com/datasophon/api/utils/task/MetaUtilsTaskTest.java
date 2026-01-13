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
        File file = new File("D:\\Desktop\\VOS集成测试\\全量测试2\\config_20260112091536.tar.gz");
        String password = "Mu1E5fL/6ycwflLg4DCzIw==";

        String unzipDir = null;
        unzipDir = TarUtils.decompressToTemp(file.getAbsolutePath());
        MetaUtils.decodeMatchedFiles(unzipDir, password);

        System.out.println("解压路径为" + unzipDir);
    }
}
