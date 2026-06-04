package com.datasophon.worker.test.deploy;

import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.worker.test.PropertiesPathUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * @author zhanghuangbin
 */
public class UploadWorkerTest {
    
    @Test
    public void test() throws IOException {
        PropertiesPathUtils.resetPropertyFile();
        
        File base = new File("./target");
        System.out.println(base.getAbsolutePath());
        
        String name = "datasophon-worker.tar.gz";
        List<String> files = Arrays.asList(name, name + ".md5");
        for (String file : files) {
            NexusFileUtils.uploadFileToRawRepo("/packages/", new File(base, file));
        }
        
    }
}
