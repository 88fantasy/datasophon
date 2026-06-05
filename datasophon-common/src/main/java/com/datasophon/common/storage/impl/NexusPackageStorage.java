package com.datasophon.common.storage.impl;

import com.datasophon.common.Constants;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.vo.DownloadResult;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;

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

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;

/**
 * @author zhanghuangbin
 */
@Slf4j
@SuppressWarnings("deprecated")
public class NexusPackageStorage extends NexusStorageSupport implements PackageStorage {
    
    private static final Map<String, ReentrantLock> LOCK_MAP = new ConcurrentHashMap<>();
    
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
        String fileName = getPkgMd5FileName(packageName);
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
    
    private String getPkgMd5FileName(String packageName) {
        return packageName.endsWith(".md5") ? packageName : packageName + ".md5";
    }
    
    @Override
    public DownloadResult downloadPackageToLocal(String packageName) {
        return doDownload(packageName, () -> readPackageMd5(packageName));
    }
    
    @Override
    public DownloadResult downloadResourceToLocal(String resourceName) {
        return doDownload(resourceName, () -> NexusFileUtils.getAssertMd5FromRawRepo(resourceName));
    }
    
    @Override
    public void deletePackage(String packageName) {
        File pkgFile = Paths.get(Constants.MASTER_MANAGE_PACKAGE_PATH, packageName).toFile();
        if (pkgFile.exists()) {
            pkgFile.delete();
        }
        File md5File = Paths.get(Constants.MASTER_MANAGE_PACKAGE_PATH, getPkgMd5FileName(packageName)).toFile();
        if (md5File.exists()) {
            md5File.delete();
        }
        NexusFileUtils.removeFileFromRawRepo("/packages/" + packageName);
        NexusFileUtils.removeFileFromRawRepo("/packages/" + getPkgMd5FileName(packageName));
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
    
}
