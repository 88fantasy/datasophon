package com.datasophon.api.utils.task;

import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.common.utils.TarUtils;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * @author zhanghuangbin
 */
public class MetaUtilsTaskTest {
    
    @Test
    public void testDecode() throws IOException {
        File file = new File("/Users/liushumin/Downloads/config_20260114162130.tar.gz");
        String password = "WYe8VviWpGpjQJobsamkFQ==";
        
        String unzipDir = "/Users/liushumin/Downloads/config";
        TarUtils.decompress(file.getAbsolutePath(), unzipDir);
        MetaUtils.decodeMatchedFiles(unzipDir, password);
        
        System.out.println("解压路径为" + unzipDir);
    }
}
