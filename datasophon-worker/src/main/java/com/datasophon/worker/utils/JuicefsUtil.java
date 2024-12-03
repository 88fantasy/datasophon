package com.datasophon.worker.utils;

import cn.hutool.core.date.DateUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;

import java.io.IOException;

public class JuicefsUtil {

    public static void installFrontend(Logger logger, String metaUrl, String srcPath, String dstPath) throws IOException {
        Configuration conf = new Configuration();
        conf.set("fs.jfs.impl", "io.juicefs.JuiceFileSystem");
        conf.set("juicefs.meta", metaUrl);  // JuiceFS 元数据引擎地址
        Path p = new Path("jfs://appweb/");  // 请替换 {JFS_NAME} 为正确的值
        FileSystem jfs = p.getFileSystem(conf);
        long start = DateUtil.current();

        logger.info("开始上传前端目录 {} -> {}", srcPath, dstPath);

        // 删除目录
        jfs.delete(new Path(dstPath), true);
        jfs.copyFromLocalFile(new Path(srcPath), new Path(dstPath));
        logger.info("完成上传前端目录 {} -> {}, 耗时={}ms", srcPath, dstPath, DateUtil.current() - start);
    }


}
