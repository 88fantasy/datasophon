package com.datasophon.api.service.tmpfile.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.dto.upload.BigFileDTO;
import com.datasophon.api.dto.upload.CheckChunkDTO;
import com.datasophon.api.dto.upload.ChunkDTO;
import com.datasophon.api.dto.upload.MergeChunkDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.utils.TransactionalUtils;
import com.datasophon.api.vo.tmpfile.MergeProgressVO;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.dao.entity.UploadTempFile;
import com.datasophon.dao.entity.UploadTempFileChunk;
import com.datasophon.dao.mapper.UploadTempFileChunkMapper;
import com.datasophon.dao.mapper.UploadTempFileMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 * @date 2025/11/5
 */
@Service("uploadTempFileService")
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class UploadTempFileServiceImpl extends ServiceImpl<UploadTempFileMapper, UploadTempFile>
        implements UploadTempFileService {

    @Autowired
    private UploadTempFileChunkMapper uploadTempFileChunkMapper;

    @Autowired
    private TransactionalUtils transactionalUtils;

    private final Map<Integer, MergeProgressVO> map = new ConcurrentHashMap<>();

    private static final String CHUNK_SUFFIX = ".chunk.tmp";

    private static final long MAX_CHUNK_SIZE = 100L * 1024 * 1024;

    @Override
    public UploadTempFile upload(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFileName)) {
            throw new BusinessException("文件名不能为空");
        }
        try {
            UploadTempFile db = new UploadTempFile();
            db.setId(RandomUtils.nextInt(0, Integer.MAX_VALUE));
            db.setFileName(file.getOriginalFilename());
            db.setContentType(file.getContentType());
            db.setByteCnt(file.getSize());
            db.setByteDesc(FileUtil.readableFileSize(file.getSize()));
            db.setSuffix(FileUtil.getSuffix(file.getOriginalFilename()));
            db.setCreateTime(new Date());
            db.setStatus(0);
            db.setUploadType(0);
            save(db);

            File ddhTmpDir = getSaveDir();
            String filePath = saveFileToDisk(file, ddhTmpDir, db.getId());

            String md5 = FileUtils.md5(new File(ddhTmpDir, filePath));
            db.setMd5(md5);

            db.setPath(filePath);
            db.setStatus(1);
            updateById(db);

            return db;
        } catch (IOException e) {
            log.error("文件上传处理异常: {}", e.getMessage(), e);
            throw new ServiceException(500, "写入文件失败");
        }
    }


    private File getSaveDir() {
        return new File(SystemUtils.getJavaIoTmpDir(), "ddp_upload");
    }

    private String saveFileToDisk(MultipartFile file, File ddhTmpDir, Integer attachId) throws IOException {
        File attachDir = new File(ddhTmpDir, attachId.toString());
        if (!attachDir.exists() && !attachDir.mkdirs()) {
            throw new ServiceException(500, "创建文件存储目录失败: " + attachDir.getAbsolutePath());
        }
        File destFile = new File(attachDir, file.getOriginalFilename());
        file.transferTo(destFile.toPath());
        return attachId + "/" + file.getOriginalFilename();
    }

    @Override
    public UploadTempFile createShardUploadTask(BigFileDTO info) {
        File file = null;
        if (StrUtil.isNotBlank(info.getMd5())) {
            UploadTempFile existFile = lambdaQuery()
                    .eq(UploadTempFile::getMd5, info.getMd5())
                    .isNotNull(UploadTempFile::getPath)
                    .orderByDesc(UploadTempFile::getCreateTime)
                    .last("limit 1")
                    .one();
            if (existFile != null) {
                file = getTempFile(existFile.getId());
            }
        }

        UploadTempFile db = BeanUtil.toBean(info, UploadTempFile.class);
        db.setId(RandomUtils.nextInt(0, Integer.MAX_VALUE));
        db.setByteDesc(FileUtil.readableFileSize(db.getByteCnt()));
        db.setSuffix(FileUtil.getSuffix(db.getFileName()));
        db.setCreateTime(new Date());
        db.setStatus(0);
        if (file == null) {
            db.setUploadType(1);
            db.setChunk((int) (info.getByteCnt() % MAX_CHUNK_SIZE == 0 ? info.getByteCnt() / MAX_CHUNK_SIZE : (info.getByteCnt() / MAX_CHUNK_SIZE) + 1));
            db.setChunkSize(MAX_CHUNK_SIZE);
        } else {
            db.setUploadType(2);
        }
        save(db);


        File attachDir = new File(getSaveDir(), db.getId().toString());
        if (!attachDir.exists() && !attachDir.mkdirs()) {
            throw new ServiceException(500, "创建文件存储目录失败: " + attachDir.getAbsolutePath());
        }
        if (file != null) {
            File destFile = new File(attachDir, db.getFileName());
            FileUtil.copyFile(file, destFile);
            db.setMd5(info.getMd5());
            db.setPath(db.getId() + "/" + db.getFileName());
            db.setStatus(1);
            updateById(db);
        }

        return db;
    }

    @Override
    public UploadTempFileChunk uploadChunk(ChunkDTO info) {
        UploadTempFile db = getById(info.getAttachId());
        if (db == null) {
            throw new BusinessException("任务不存在");
        }
        if (info.getChunkNo() >= db.getChunk()) {
            throw new BusinessException("chunkNo大于任务的分片数");
        }
        UploadTempFileChunk chunk = uploadTempFileChunkMapper.selectOne(
                Wrappers.lambdaQuery(UploadTempFileChunk.class)
                        .eq(UploadTempFileChunk::getAttachId, db.getId())
                        .eq(UploadTempFileChunk::getChunkNo, info.getChunkNo())
        );
//        已经上传，直接忽略
        if (chunk != null && chunk.getMd5().equalsIgnoreCase(info.getMd5())) {
            return chunk;
        }


        Path chunkPath = PathUtils.join(getSaveDir().toPath(), db.getId().toString(), createChunkName(db.getFileName(), info.getChunkNo()));
        log.info("chunk of attach {}  write chunk file to {}", db.getId(), chunkPath);
        try {
            info.getChunk().transferTo(chunkPath);
        } catch (IOException e) {
            throw new BusinessException("IO异常," + e.getMessage(), e);
        }

        chunk = BeanUtil.toBean(info, UploadTempFileChunk.class);
        chunk.setMd5(FileUtils.md5(chunkPath.toFile()));
        if (StringUtils.isNotBlank(info.getMd5()) && chunk.getMd5().equalsIgnoreCase(info.getMd5())) {
            throw new BusinessException("文件MD5不一致");
        }

        uploadTempFileChunkMapper.insertOrUpdate(chunk);
        return chunk;
    }

    @Override
    public boolean isChunkUploaded(CheckChunkDTO dto) {
        UploadTempFile db = getById(dto.getAttachId());
        if (db == null) {
            throw new BusinessException("任务不存在");
        }
        if (dto.getChunkNo() >= db.getChunk()) {
            throw new BusinessException("chunkNo大于任务的分片数");
        }
        UploadTempFileChunk chunk = uploadTempFileChunkMapper.selectOne(
                Wrappers.lambdaQuery(UploadTempFileChunk.class)
                        .eq(UploadTempFileChunk::getAttachId, dto.getAttachId())
                        .eq(UploadTempFileChunk::getChunkNo, dto.getChunkNo())
        );
        if (chunk == null) {
            return false;
        }
        if (!chunk.getMd5().equalsIgnoreCase(dto.getMd5())) {
            return false;
        }
        Path chunkPath = PathUtils.join(getSaveDir().toPath(), dto.getAttachId().toString(), createChunkName(db.getFileName(), dto.getChunkNo()));
        return chunkPath.toFile().exists();
    }


    private String createChunkName(String fileName, Integer chunkNo) {
        return chunkNo + CHUNK_SUFFIX;
    }

    @Override
    public MergeProgressVO mergeChunk(MergeChunkDTO vo) {
        UploadTempFile db = getById(vo.getAttachId());
        if (db == null) {
            throw new BusinessException("任务不存在");
        }
        long count = uploadTempFileChunkMapper.selectCount(
                Wrappers.lambdaQuery(UploadTempFileChunk.class).eq(UploadTempFileChunk::getAttachId, db.getId())
        );
        if (db.getChunk() != count) {
            throw new BusinessException("已经上传的分片数量尚未达到要求");
        }
//        已经上传完成，直接返回
        if (db.getStatus() == 1) {
            return queryMergeProgress(db.getId());
        }
//        重复调用，直接返回
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
            throw new BusinessException("任务不存在");
        }
        MergeProgressVO vo = map.get(attachId);
        if (vo != null) {
            return vo;
        }
//            走到这一步，说明进度信息的缓存已经失效了
        vo = new MergeProgressVO();
        vo.setState(db.getStatus() == 1 ? 1 : -1);
        if (vo.getState() == -1) {
//            正常业务流程，不应该走到这一步
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
                    CompletableFuture.runAsync(() -> doCalMd5(db, attachDir))
            ).join();

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

//        异步删除分片文件
        CompletableFuture.runAsync(() -> {
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
                File chunkFile = new File(attachDir, createChunkName(db.getFileName(), i));
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
        db.setMd5(FileUtils.md5(new ChunkIterator(db, i -> new File(attachDir, createChunkName(db.getFileName(), i))), progress::plusMd5));
    }

    @Override
    public File getTempFile(Integer attachId) {
        UploadTempFile db = getById(attachId);
        if (db == null) {
            return null;
        }
        File file = Paths.get(getSaveDir().getAbsolutePath(), db.getId().toString(), db.getFileName()).toFile();
        if (file.exists()) {
            return file;
        }
        return null;
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

    private static class ChunkIterator implements Iterator<File> {

        private int idx = 0;

        private final int count;

        private final Function<Integer, File> chunkLocator;

        public ChunkIterator(UploadTempFile db, Function<Integer, File> chunkLocator) {
            this.count = db.getChunk();
            this.chunkLocator = chunkLocator;
        }

        @Override
        public boolean hasNext() {
            return idx < count;
        }

        @Override
        public File next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            File file = chunkLocator.apply(idx);
            idx++;
            return file;
        }
    }
}
