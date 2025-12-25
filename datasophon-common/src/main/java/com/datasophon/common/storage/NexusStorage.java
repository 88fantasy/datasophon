package com.datasophon.common.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 */
public class NexusStorage implements PackageStorage {


    @Override
    public boolean isEnabled() {
        return getNexusUri().isEnabled();
    }

    @Override
    public void moveToStorage(File src, boolean includeDir) throws IOException {
        ensureDirValid(src);
        ensureNexusEnable();
        Files.walkFileTree(src.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String relative = PathUtils.relative(path.getParent().toFile(), src.getAbsolutePath());
                if (includeDir) {
                    relative = src.getName() + "/" + relative;
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
        NexusFileUtils.uploadFileToRawRepo(relativePath, file);
    }

    @Override
    public String readPackageMd5(String packageName) {
        ensureNexusEnable();
        NexusUri registry = getNexusUri();
        String path = packageName.endsWith(".md5") ? packageName : packageName + ".md5";
        path = "packages/" + path;
        try (InputStream in = NexusFileUtils.downStream(NexusFileUtils.getNexusRawObjectUrl(path), registry.getUser(), registry.getPassword())) {
            return IoUtil.read(in, StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(String.format("package %s does not exists at %s", packageName, NexusFileUtils.getNexusRawObjectUrl(path)), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void downloadPackageToLocal(String packageName) {
        ensureNexusEnable();
        File file = new File(Constants.MASTER_MANAGE_PACKAGE_PATH, packageName);
        boolean needDownload;
        if (!file.exists()) {
            needDownload = true;
        } else {
            String remoteMd5 = readPackageMd5(packageName);
            String md5 = DigestUtil.md5Hex(file);
            needDownload = !md5.equalsIgnoreCase(remoteMd5);
        }
        if (needDownload) {
            NexusUri registry = getNexusUri();
            String path = "packages/" + packageName;
            try (InputStream in = NexusFileUtils.downStream(NexusFileUtils.getNexusRawObjectUrl(path), registry.getUser(), registry.getPassword())) {
                FileUtil.copyFile(in, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException(String.format("package %s does not exists at %s", packageName, NexusFileUtils.getNexusRawObjectUrl(path)), e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private void ensureDirValid(File dir) {
        if (!dir.exists()) {
            throw new IllegalStateException(dir.getAbsolutePath() + " not exists");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir.getAbsolutePath() + " is not dir");
        }
    }

    private void ensureNexusEnable() {
        NexusUri registry = getNexusUri();
        if (registry.isEnabled()) {
            throw new IllegalStateException("datasophon require an nexus available, but nexus is disabled");
        }
    }

    private static NexusUri getNexusUri() {
        NexusUri uri = new NexusUri();
        uri.setEnabled(Constants.NEXUS_ENABLE);
        uri.setUri(String.format("http://%s:%s", Constants.NEXUS_IP, Constants.NEXUS_PORT));
        uri.setUser(Constants.NEXUS_USERNAME);
        uri.setPassword(Constants.NEXUS_PASSWORD);
        return uri;
    }
}
