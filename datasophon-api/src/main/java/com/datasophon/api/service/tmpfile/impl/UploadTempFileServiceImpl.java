package com.datasophon.api.service.tmpfile.impl;

import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.dto.upload.BigFileDTO;
import com.datasophon.api.dto.upload.CheckChunkDTO;
import com.datasophon.api.dto.upload.ChunkDTO;
import com.datasophon.api.dto.upload.MergeChunkDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.service.tmpfile.comp.DownloaderFactory;
import com.datasophon.api.service.tmpfile.comp.RemoteFileDownloader;
import com.datasophon.api.utils.TransactionalUtils;
import com.datasophon.api.vo.extrepo.DownloadProgressVO;
import com.datasophon.api.vo.tmpfile.MergeProgressVO;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.dao.entity.UploadTempFile;
import com.datasophon.dao.entity.UploadTempFileChunk;
import com.datasophon.dao.mapper.UploadTempFileChunkMapper;
import com.datasophon.dao.mapper.UploadTempFileMapper;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
@Service("uploadTempFileService")
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class UploadTempFileServiceImpl extends ServiceImpl<UploadTempFileMapper, UploadTempFile>
        implements
            UploadTempFileService {
    
    @Autowired
    private UploadTempFileChunkMapper uploadTempFileChunkMapper;
    
    @Autowired
    private TransactionalUtils transactionalUtils;
    
    private final Map<Integer, MergeProgressVO> map = new ConcurrentHashMap<>();
    
    /**
     * 下载进度缓存
     */
    private final Map<String, DownloadProgressVO> downloadProgressMap = new ConcurrentHashMap<>();
    
    private static final String CHUNK_SUFFIX = ".chunk.tmp";
    
    private static final long MAX_CHUNK_SIZE = 100L * 1024 * 1024;
    
    @Override
    public UploadTempFile upload(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFileName)) {
            throw new BusinessHintException("文件名不能为空");
        }
        try {
            UploadTempFile db = new UploadTempFile();
            db.setFileName(file.getOriginalFilename());
            db.setContentType(file.getContentType());
            db.setByteCnt(file.getSize());
            db.setByteDesc(FileUtil.readableFileSize(file.getSize()));
            db.setSuffix(FileUtil.getSuffix(file.getOriginalFilename()));
            db.setCreateTime(new Date());
            db.setStatus(0);
            db.setUploadType(0);
            save(db);
            
            File attachDir = createAttachSaveDir(db);
            File destFile = new File(attachDir, file.getOriginalFilename());
            file.transferTo(destFile.toPath());
            
            String md5 = FileUtils.md5(destFile);
            db.setMd5(md5);
            db.setPath(getAttachPath(db));
            db.setStatus(1);
            updateById(db);
            
            return db;
        } catch (IOException e) {
            log.error("文件上传处理异常: {}", e.getMessage(), e);
            throw new ServiceException(500, "写入文件失败");
        }
    }
    
    private File getSaveDir() {
        return PathUtils.getTmpDir("ddp_upload");
    }
    
    private File createAttachSaveDir(UploadTempFile db) {
        File attachDir = new File(getSaveDir(), db.getId().toString());
        if (!attachDir.exists() && !attachDir.mkdirs()) {
            throw new ServiceException(500, "创建文件存储目录失败: " + attachDir.getAbsolutePath());
        }
        return attachDir;
    }
    
    private String getAttachPath(UploadTempFile db) {
        return db.getId() + "/" + db.getFileName();
    }
    
    @Override
    public UploadTempFile createShardUploadTask(BigFileDTO info) {
        UploadTempFile db = BeanUtil.toBean(info, UploadTempFile.class);
        db.setByteDesc(FileUtil.readableFileSize(db.getByteCnt()));
        db.setSuffix(FileUtil.getSuffix(db.getFileName()));
        db.setCreateTime(new Date());
        db.setStatus(0);
        
        File file = null;
        if (StrUtil.isNotBlank(info.getMd5())) {
            List<UploadTempFile> files = lambdaQuery()
                    .eq(UploadTempFile::getMd5, info.getMd5())
                    // 优先查找已经上传完毕的文件
                    .isNotNull(UploadTempFile::getPath)
                    .orderByDesc(UploadTempFile::getCreateTime)
                    .list();
            for (UploadTempFile tmp : files) {
                file = doGetTempFile(tmp).orElse(null);
                if (file != null) {
                    break;
                }
            }
        }
        
        if (file == null) {
            db.setUploadType(1);
            db.setChunk((int) (info.getByteCnt() % MAX_CHUNK_SIZE == 0 ? info.getByteCnt() / MAX_CHUNK_SIZE : (info.getByteCnt() / MAX_CHUNK_SIZE) + 1));
            db.setChunkSize(MAX_CHUNK_SIZE);
        } else {
            db.setUploadType(2);
        }
        save(db);
        
        File attachDir = createAttachSaveDir(db);
        if (file != null) {
            // 秒传，则拷贝文件
            File destFile = new File(attachDir, db.getFileName());
            FileUtil.copyFile(file, destFile);
            db.setMd5(info.getMd5());
            db.setPath(getAttachPath(db));
            db.setStatus(1);
            updateById(db);
        }
        
        return db;
    }
    
    @Override
    public UploadTempFileChunk isChunkUploaded(CheckChunkDTO dto) {
        UploadTempFile db = getById(dto.getAttachId());
        if (db == null) {
            throw new BusinessHintException("任务不存在");
        }
        if (dto.getChunkNo() >= db.getChunk()) {
            throw new BusinessHintException("chunkNo大于任务的分片数");
        }
        UploadTempFileChunk chunk = uploadTempFileChunkMapper.selectOne(
                Wrappers.lambdaQuery(UploadTempFileChunk.class)
                        .eq(UploadTempFileChunk::getAttachId, dto.getAttachId())
                        .eq(UploadTempFileChunk::getChunkNo, dto.getChunkNo()));
        
        Path chunkPath = PathUtils.join(getSaveDir().toPath(), dto.getAttachId().toString(), createChunkName(dto.getChunkNo()));
        if (chunk != null && chunk.getMd5().equalsIgnoreCase(dto.getMd5())) {
            if (chunkPath.toFile().exists()) {
                return chunk;
            }
        }
        
        List<UploadTempFileChunk> chunks = uploadTempFileChunkMapper.selectList(
                Wrappers.lambdaQuery(UploadTempFileChunk.class)
                        .eq(UploadTempFileChunk::getMd5, dto.getMd5()));
        
        boolean uploaded = false;
        for (UploadTempFileChunk existChunk : chunks) {
            Path path = PathUtils.join(getSaveDir().toPath(), existChunk.getAttachId().toString(), createChunkName(existChunk.getChunkNo()));
            if (path.toFile().exists()) {
                FileUtil.copy(path.toFile(), chunkPath.toFile(), true);
                uploaded = true;
                break;
            }
        }
        
        if (uploaded) {
            if (chunk == null) {
                chunk = new UploadTempFileChunk();
                chunk.setAttachId(db.getId());
                chunk.setChunkNo(dto.getChunkNo());
                chunk.setMd5(dto.getMd5());
                uploadTempFileChunkMapper.insert(chunk);
            } else {
                chunk.setMd5(db.getMd5());
                uploadTempFileChunkMapper.updateById(chunk);
            }
        }
        
        return chunk;
    }
    
    @Override
    public UploadTempFileChunk uploadChunk(ChunkDTO info) {
        UploadTempFile db = getById(info.getAttachId());
        if (db == null) {
            throw new BusinessHintException("任务不存在");
        }
        if (info.getChunkNo() >= db.getChunk()) {
            throw new BusinessHintException("chunkNo大于任务的分片数");
        }
        UploadTempFileChunk chunk = uploadTempFileChunkMapper.selectOne(
                Wrappers.lambdaQuery(UploadTempFileChunk.class)
                        .eq(UploadTempFileChunk::getAttachId, db.getId())
                        .eq(UploadTempFileChunk::getChunkNo, info.getChunkNo()));
        // 已经上传，直接忽略
        if (chunk != null && chunk.getMd5().equalsIgnoreCase(info.getMd5())) {
            return chunk;
        }
        
        Path chunkPath = PathUtils.join(getSaveDir().toPath(), db.getId().toString(), createChunkName(info.getChunkNo()));
        log.info("chunk of attach {}  write chunk file to {}", db.getId(), chunkPath);
        try {
            info.getChunk().transferTo(chunkPath);
        } catch (IOException e) {
            throw new BusinessException("IO异常," + e.getMessage(), e);
        }
        
        chunk = BeanUtil.toBean(info, UploadTempFileChunk.class);
        chunk.setMd5(FileUtils.md5(chunkPath.toFile()));
        if (StringUtils.isNotBlank(info.getMd5()) && !chunk.getMd5().equalsIgnoreCase(info.getMd5())) {
            throw new BusinessException("文件MD5不一致");
        }
        
        uploadTempFileChunkMapper.insertOrUpdate(chunk);
        return chunk;
    }
    
    private String createChunkName(Integer chunkNo) {
        return chunkNo + CHUNK_SUFFIX;
    }
    
    @Override
    public MergeProgressVO mergeChunk(MergeChunkDTO vo) {
        UploadTempFile db = getById(vo.getAttachId());
        if (db == null) {
            throw new BusinessHintException("任务不存在");
        }
        long count = uploadTempFileChunkMapper.selectCount(
                Wrappers.lambdaQuery(UploadTempFileChunk.class).eq(UploadTempFileChunk::getAttachId, db.getId()));
        if (db.getChunk() != count) {
            throw new BusinessHintException("已经上传的分片数量尚未达到要求");
        }
        // 已经上传完成，直接返回
        if (db.getStatus() == 1) {
            return queryMergeProgress(db.getId());
        }
        // 重复调用，直接返回
        MergeProgressVO progress = map.get(vo.getAttachId());
        if (progress != null) {
            return progress;
        }
        
        progress = new MergeProgressVO(db.getByteCnt());
        map.put(vo.getAttachId(), progress);
        
        MergeProgressVO finalProgress = progress;
        if (vo.isAsync()) {
            CompletableFuture.runAsync(() -> doMerge(db, vo, finalProgress));
        } else {
            doMerge(db, vo, finalProgress);
        }
        return progress;
    }
    
    @Override
    public MergeProgressVO queryMergeProgress(Integer attachId) {
        UploadTempFile db = getById(attachId);
        if (db == null) {
            throw new BusinessHintException("任务不存在");
        }
        MergeProgressVO vo = map.get(attachId);
        if (vo != null) {
            return vo;
        }
        // 走到这一步，说明进度信息的缓存已经失效了
        vo = new MergeProgressVO();
        vo.setState(db.getStatus() == 1 ? 1 : -1);
        if (vo.getState() == -1) {
            // 正常业务流程，不应该走到这一步
            vo.setError("上传失败");
        }
        return vo;
    }
    
    private void doMerge(UploadTempFile db, MergeChunkDTO vo, MergeProgressVO progress) {
        progress.setState(2);
        
        String error = null;
        File attachDir = new File(getSaveDir(), db.getId().toString());
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> doMergeChunk(db, attachDir)),
                    CompletableFuture.runAsync(() -> doCalMd5(db, attachDir))).join();
            
            if (!vo.getMd5().equalsIgnoreCase(db.getMd5())) {
                error = "文件内容校验失败，可能是上传损坏，请重新上传";
                if (!vo.isAsync()) {
                    throw new BusinessException(error);
                }
            }
            db.setStatus(1);
            updateById(db);
        } catch (CompletionException e) {
            error = e.getMessage();
            if (e.getCause() instanceof BusinessException) {
                throw (BusinessException) e.getCause();
            }
            throw e;
        } finally {
            progress.setError(error);
            progress.setState(StringUtils.isBlank(error) ? 1 : -1);
            progress.setExpire(LocalDateTime.now().plusMinutes(1));
        }
        
        // 异步删除分片文件
        CompletableFuture.runAsync(() -> {
            uploadTempFileChunkMapper.delete(Wrappers.lambdaQuery(UploadTempFileChunk.class).eq(UploadTempFileChunk::getAttachId, db.getId()));
            File[] files = attachDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(CHUNK_SUFFIX)) {
                        FileUtil.del(file);
                    }
                }
            }
        });
    }
    
    private void doMergeChunk(UploadTempFile db, File attachDir) {
        MergeProgressVO progress = map.get(db.getId());
        
        File mergedFile = new File(attachDir, db.getFileName());
        if (mergedFile.exists()) {
            FileUtil.del(mergedFile);
            try {
                mergedFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
            for (int i = 0; i < db.getChunk(); i++) {
                File chunkFile = new File(attachDir, createChunkName(i));
                try {
                    FileUtil.writeToStream(chunkFile, fos);
                } catch (IORuntimeException ex) {
                    throw new BusinessException(String.format("合并第%s个分片文件失败", i), ex);
                }
                progress.plusMerge(chunkFile.length());
            }
        } catch (IOException e) {
            throw new BusinessException("合并分片文件失败", e);
        }
        db.setPath(db.getId() + "/" + db.getFileName());
    }
    
    private void doCalMd5(UploadTempFile db, File attachDir) {
        MergeProgressVO progress = map.get(db.getId());
        db.setMd5(FileUtils.md5(new ChunkIterator(db, i -> new File(attachDir, createChunkName(i))), progress::plusMd5));
    }
    
    @Override
    public Optional<File> getTempFile(Integer attachId) {
        UploadTempFile db = getById(attachId);
        return doGetTempFile(db);
    }
    
    private Optional<File> doGetTempFile(UploadTempFile db) {
        if (db == null || db.getPath() == null) {
            return Optional.empty();
        }
        File file = Paths.get(getSaveDir().getAbsolutePath(), db.getPath()).toFile();
        return file.exists() ? Optional.of(file) : Optional.empty();
    }
    
    @Override
    public void clearProgressCache() {
        Set<Integer> keys = map.keySet();
        for (Integer key : keys) {
            MergeProgressVO pg = map.get(key);
            if (pg != null && pg.isTimeout()) {
                map.remove(key);
            }
        }
        // 清理下载进度缓存
        Set<String> downloadKeys = downloadProgressMap.keySet();
        for (String key : downloadKeys) {
            DownloadProgressVO pg = downloadProgressMap.get(key);
            if (pg != null && pg.isTimeout()) {
                downloadProgressMap.remove(key);
                log.info("清理过期下载进度缓存：{}", key);
            }
        }
    }
    
    @Override
    public DownloadProgressVO createDownloadTask(DownloadTaskDTO dto) {
        String taskId = RandomUtil.randomString(12);
        DownloadProgressVO progress = new DownloadProgressVO(taskId);
        progress.setState(2); // 下载中
        downloadProgressMap.put(taskId, progress);
        // 异步执行下载
        CompletableFuture.runAsync(() -> doDownload(dto, progress));
        return progress;
    }
    
    @Override
    public DownloadProgressVO queryProgress(String taskId) {
        DownloadProgressVO progress = downloadProgressMap.get(taskId);
        if (progress == null) {
            progress = new DownloadProgressVO(taskId);
            progress.setState(-3);
            progress.setError("缓存已经过期");
        }
        return progress;
    }
    
    @Override
    public void cancelDownload(String taskId) {
        DownloadProgressVO progress = downloadProgressMap.get(taskId);
        if (progress != null) {
            progress.setState(3); // 取消中
            progress.setCancel(true);
            log.info("任务 {} 取消请求已发送", taskId);
        } else {
            log.warn("任务 {} 未找到取消标志，可能已完成或异常", taskId);
        }
    }
    
    /**
     * 执行下载
     */
    private void doDownload(DownloadTaskDTO dto, DownloadProgressVO progress) {
        File saveDir = null;
        
        try {
            saveDir = new File(PathUtils.getTmpDir("ddp_download"), progress.getTaskId());
            if (!saveDir.exists() && !saveDir.mkdirs()) {
                throw new BusinessHintException("创建文件存储目录失败");
            }
            
            String url = dto.getUrl();
            RemoteFileDownloader downloader = DownloaderFactory.getDownloader(url);
            String fileName = downloader.determineFileName(dto);
            progress.setFileName(fileName);
            
            File destFile = new File(saveDir, fileName);
            log.info("使用下载器：{} 下载文件：{}", downloader.getClass().getSimpleName(), url);
            
            downloader.download(url, destFile, progress);
            
            // 下载完成，检查是否被取消
            if (progress.isCancel()) {
                progress.setState(-2);
                return;
            }
            
            // 获取文件大小
            long fileSize = destFile.length();
            
            // 下载成功后写入数据库
            UploadTempFile tempFile = new UploadTempFile();
            tempFile.setFileName(fileName);
            tempFile.setCreateTime(new Date());
            tempFile.setStatus(1);
            tempFile.setUploadType(3); // 3 表示外部 URL 导入
            tempFile.setByteCnt(fileSize);
            tempFile.setByteDesc(FileUtil.readableFileSize(fileSize));
            tempFile.setSuffix(FileUtil.getSuffix(destFile.getName()));
            tempFile.setMd5(FileUtils.md5(destFile));
            save(tempFile);
            
            String path = String.format("%s/%s", tempFile.getId(), tempFile.getFileName());
            File dest = PathUtils.getTmpDir("ddp_upload").toPath().resolve(path).toFile();
            FileUtil.move(destFile, dest, true);
            tempFile.setPath(path);
            updateById(tempFile);
            
            progress.setAttachId(tempFile.getId());
            progress.setTotal(fileSize);
            progress.setDownloaded(fileSize);
            progress.setState(1); // 完成
            progress.setExpire(LocalDateTime.now().plusMinutes(30));
            
            log.info("下载任务完成：url:{}, 文件大小：{} bytes", dto.getUrl(), fileSize);
        } catch (IOException e) {
            log.error("下载失败：{}", e.getMessage(), e);
            progress.setState(-1);
            progress.setError("下载失败：" + e.getMessage());
        } finally {
            FileUtil.del(saveDir);
            if (progress.isCancel()) {
                log.info("下载{}被取消", dto.getUrl());
            }
        }
    }
    
    @Override
    public void removeTempFile() {
        List<UploadTempFile> files = lambdaQuery().le(UploadTempFile::getCreateTime, LocalDateTime.now().minusDays(1)).list();
        for (UploadTempFile file : files) {
            try {
                transactionalUtils.doInNewTx(() -> {
                    FileUtil.del(new File(getSaveDir(), file.getId().toString()));
                    uploadTempFileChunkMapper.delete(Wrappers.lambdaQuery(UploadTempFileChunk.class).eq(UploadTempFileChunk::getAttachId, file.getId()));
                    removeById(file.getId());
                });
            } catch (Exception e) {
                log.error("clear temp file {} failure", file.getId(), e);
            }
        }
        
    }
    
}
