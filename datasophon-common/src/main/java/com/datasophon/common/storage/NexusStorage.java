package com.datasophon.common.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class NexusStorage implements PackageStorage {

    private static final Map<String, ReentrantLock> LOCK_MAP = new ConcurrentHashMap<>();

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

                log.info("upload {} to raw repo, path: {}", path, relative);
                NexusFileUtils.uploadFileToRawRepo(relative, path.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
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

        log.info("upload {} to raw repo, path: {}", file, relativePath);
        NexusFileUtils.uploadFileToRawRepo(relativePath, file);
    }

    @Override
    public String readPackageMd5(String packageName) {
        ensureNexusEnable();
        String fileName = packageName.endsWith(".md5") ? packageName : packageName + ".md5";
        String path = "packages/" + fileName;

        log.info("read the md5 of package:{}", packageName);
        try {
            String md5 = NexusFileUtils.downloadAsString(NexusFileUtils.getNexusRawObjectUrl(path));
            log.info("read the md5 of package:{}, content: {}", packageName, md5);
            return md5.replaceAll("\\s", "");
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(String.format("package %s does not exists at %s", fileName, NexusFileUtils.getNexusRawObjectUrl(path)), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DownloadResult downloadPackageToLocal(String packageName) {
        return doDownload(packageName, () -> readPackageMd5(packageName));
    }

    @Override
    public DownloadResult downloadResourceToLocal(String resourceName) {
        return doDownload(resourceName, () -> NexusFileUtils.getAssertMd5FromRawRepo(resourceName));
    }


    private DownloadResult doDownload(String resourceName, Supplier<String> remoteResourceMd5) {
        ensureNexusEnable();
        Lock lock = LOCK_MAP.computeIfAbsent(resourceName, k -> new ReentrantLock());
        try {
            lock.lock();
            DownloadResult result = new DownloadResult();
            result.setMd5(remoteResourceMd5.get());
            File file = Paths.get(Constants.MASTER_MANAGE_PACKAGE_PATH, resourceName).toFile();
            boolean needDownload;
            if (!file.exists()) {
                needDownload = true;
            } else {
                String md5 = DigestUtil.md5Hex(file);
                needDownload = !md5.equalsIgnoreCase(result.getMd5());
            }
            if (needDownload) {
                String path = "packages/" + resourceName;
                log.info("download package: {}, path is {}", resourceName, path);
                if (file.exists()) {
                    file.delete();
                }
                file = FileUtil.newFile(file.getAbsolutePath());
                try (FileOutputStream out = new FileOutputStream(file)) {
                    NexusFileUtils.downStream(NexusFileUtils.getNexusRawObjectUrl(path), out);
                } catch (FileNotFoundException e) {
                    throw new IllegalStateException(String.format("package %s does not exists at %s", resourceName, NexusFileUtils.getNexusRawObjectUrl(path)), e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                log.info("package {} exists, we do need to download", resourceName);
            }

            result.setChange(needDownload);
            result.setTarget(file.getAbsolutePath());
            return result;
        } finally {
            LOCK_MAP.remove(resourceName);
            lock.unlock();
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
        if (!registry.isEnabled()) {
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
