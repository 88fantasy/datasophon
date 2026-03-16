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
import com.datasophon.api.exceptions.BusinessHintException;
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
import org.apache.commons.lang3.StringUtils;
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
            List<UploadTempFile> files =  lambdaQuery()
                    .eq(UploadTempFile::getMd5, info.getMd5())
//                    优先查找已经上传完毕的文件
                    .isNotNull(UploadTempFile::getPath)
                    .orderByDesc(UploadTempFile::getCreateTime)
                    .list();
            for (UploadTempFile tmp : files) {
                file = doGetTempFile(tmp);
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
//            秒传，则拷贝文件
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
                        .eq(UploadTempFileChunk::getChunkNo, dto.getChunkNo())
        );


        Path chunkPath = PathUtils.join(getSaveDir().toPath(), dto.getAttachId().toString(), createChunkName(dto.getChunkNo()));
        if (chunk != null && chunk.getMd5().equalsIgnoreCase(dto.getMd5())) {
            if (chunkPath.toFile().exists()) {
                return chunk;
            }
        }


        List<UploadTempFileChunk> chunks = uploadTempFileChunkMapper.selectList(
                Wrappers.lambdaQuery(UploadTempFileChunk.class)
                        .eq(UploadTempFileChunk::getMd5, dto.getMd5())
        );

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
                        .eq(UploadTempFileChunk::getChunkNo, info.getChunkNo())
        );
//        已经上传，直接忽略
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
                Wrappers.lambdaQuery(UploadTempFileChunk.class).eq(UploadTempFileChunk::getAttachId, db.getId())
        );
        if (db.getChunk() != count) {
            throw new BusinessHintException("已经上传的分片数量尚未达到要求");
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
            throw new BusinessHintException("任务不存在");
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
    public File getTempFile(Integer attachId) {
        UploadTempFile db = getById(attachId);
        return doGetTempFile(db);
    }

    private File doGetTempFile(UploadTempFile db) {
        if (db == null) {
            return null;
        }
        if (db.getPath() == null) {
            return null;
        }
        File file = Paths.get(getSaveDir().getAbsolutePath(), db.getPath()).toFile();
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
