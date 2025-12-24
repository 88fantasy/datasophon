package com.datasophon.api.service.storage.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.load.Application;
import com.datasophon.api.service.extrepo.utils.PathUtils;
import com.datasophon.api.service.storage.StorageService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.uni.NexusRegistry;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.utils.NexusFileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 */
@Service
public class StorageServiceManagerImpl implements StorageService {

  @Override
  public void moveToStorage(File dir, boolean includeDir) throws IOException {
    if (!dir.exists()) {
      throw new FileNotFoundException(dir.getAbsolutePath() + " not exists");
    }
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException(dir.getAbsolutePath() + " is not dir");
    }
      NexusUri registry = Application.getNexusUri();
    if (registry.isEnabled()) {
      Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
          String relative = PathUtils.relative(path.getParent().toFile(), dir.getAbsolutePath());
          if (includeDir) {
            relative = dir.getName() + "/" + relative;
          }
          relative = PathUtils.unixStyle(relative);
          NexusFileUtils.uploadFileToRawRepo(relative, path.toFile());
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          return FileVisitResult.TERMINATE;
        }
      });
    } else {
      if (includeDir) {
        FileUtil.move(dir, new File(Constants.INIT_HOME), true);
      } else {
        File[] files = dir.listFiles();
        if (files != null) {
          for (File subFile : files) {
            FileUtil.move(subFile, new File(Constants.INIT_HOME), true);
          }
        }
      }
    }
  }


  @Override
  public void moveToStorage(File file, Function<File, String> relativePathHandler) throws IOException {
    if (!file.exists()) {
      throw new FileNotFoundException(file.getAbsolutePath() + " not exists");
    }
    if (!file.isFile()) {
      throw new IllegalArgumentException(file.getAbsolutePath() + " is not file");
    }

    String relativePath = relativePathHandler.apply(file);
    relativePath = PathUtils.unixStyle(relativePath);
    NexusUri registry = Application.getNexusUri();
    if (registry.isEnabled()) {
      NexusFileUtils.uploadFileToRawRepo(relativePath, file);
    } else {
      File dir = StrUtil.isBlank(relativePath) || "/".equals(relativePath) ? new File(Constants.INIT_HOME) :
          new File(Constants.INIT_HOME, relativePath);
      FileUtil.move(file, dir, true);
    }
  }
}
