package com.datasophon.api.utils.task;

import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

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

    @Test
    public void upload2() throws IOException {
        PropertiesPathUtils.resetPropertyFile();

        File src = new File("D:\\Desktop\\VOS集成测试\\meta");
        Files.walkFileTree(src.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String relative = PathUtils.relative(path.getParent().toFile(), src.getAbsolutePath());
                relative = src.getName() + "/" + relative;
                relative = PathUtils.unixStyle(relative);

                NexusFileUtils.uploadFileToRawRepo(relative, path.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }
        });
    }
}
