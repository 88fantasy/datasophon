package com.datasophon.api.service.tmpfile;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.api.dto.upload.BigFileDTO;
import com.datasophon.api.dto.upload.CheckChunkDTO;
import com.datasophon.api.dto.upload.ChunkDTO;
import com.datasophon.api.dto.upload.MergeChunkDTO;
import com.datasophon.dao.entity.UploadTempFile;
import com.datasophon.dao.entity.UploadTempFileChunk;
import com.datasophon.api.vo.tmpfile.MergeProgressVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * @author zhanghuangbin
 * @date 2025/11/5
 */
public interface UploadTempFileService extends IService<UploadTempFile> {

    /**
     * 上传临时文件
     * @param file 上传的文件
     * @return 文件信息
     */
    UploadTempFile upload(MultipartFile file);

    /**
     * 创建分片任务
     * @param info
     * @return
     */
    UploadTempFile createShardUploadTask(BigFileDTO info);


    /**
     * 上传分片
     * @param info
     * @return
     */
    UploadTempFileChunk uploadChunk(ChunkDTO info);


    boolean isChunkUploaded(CheckChunkDTO dto);
    /**
     * 合并分片
     * @param vo
     * @return 进度信息
     */
    MergeProgressVO mergeChunk(MergeChunkDTO vo);

    /**
     * 查询合并进度
     * @param attachId
     * @return
     */
    MergeProgressVO queryMergeProgress(Integer attachId);

    File getTempFile(Integer attachId);

    /**
     * 清理缓存
     */
    void clearProgressCache();

    /**
     * 清理临时文件
     */
    void removeTempFile();
}
