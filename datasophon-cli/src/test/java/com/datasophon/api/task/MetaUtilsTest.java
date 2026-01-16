package com.datasophon.api.task;

import com.datasophon.common.utils.MetaUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class MetaUtilsTest {

    @Test
    public void  decodeMatchedFiles() throws IOException {
        //encodeFile(new File("/Users/liushumin/Downloads/config/common.properties"), "41nMhvOMYCZGT3vVdIZB1w==");
        MetaUtils.decodeMatchedFiles("/Users/liushumin/Downloads/config", "VgZIGbxo2Mdpcc7H7OMAhw==");
        //encodeMatchedFiles("/Users/liushumin/Downloads/config", "41nMhvOMYCZGT3vVdIZB1w==");
        //decodeMatchedFiles("/Users/liushumin/_tmp/config", "5bWx3KT7vM7pJUjBf9GtSA==");
        //String s = FileUtil.readString(new File("/Users/liushumin/Downloads/config/common.properties"), StandardCharsets.UTF_8);
        //System.out.println(Base64.isBase64("xx"));
    }
}
