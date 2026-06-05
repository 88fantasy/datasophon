package com.datasophon.api.service.tmpfile;

import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.dto.upload.BigFileDTO;
import com.datasophon.api.dto.upload.CheckChunkDTO;
import com.datasophon.api.dto.upload.ChunkDTO;
import com.datasophon.api.dto.upload.MergeChunkDTO;
import com.datasophon.api.vo.extrepo.DownloadProgressVO;
import com.datasophon.api.vo.tmpfile.MergeProgressVO;
import com.datasophon.dao.entity.UploadTempFile;
import com.datasophon.dao.entity.UploadTempFileChunk;

import java.io.File;

import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author zhanghuangbin
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
     */
    UploadTempFile createShardUploadTask(BigFileDTO info);
    
    /**
     * 上传分片
     */
    UploadTempFileChunk uploadChunk(ChunkDTO info);
    
    /**
     * 判断chunk是否已经上传
     */
    UploadTempFileChunk isChunkUploaded(CheckChunkDTO dto);
    /**
     * 合并分片
     * @return 进度信息
     */
    MergeProgressVO mergeChunk(MergeChunkDTO vo);
    
    /**
     * 查询合并进度
     */
    MergeProgressVO queryMergeProgress(Integer attachId);
    
    /**
     * 获取附件ID代表的临时文件
     */
    File getTempFile(Integer attachId);
    
    /**
     * 清理缓存
     */
    void clearProgressCache();
    
    /**
     * 清理临时文件
     */
    void removeTempFile();
    
    /**
     * 创建下载任务
     * @param dto 下载请求
     * @return 进度信息
     */
    DownloadProgressVO createDownloadTask(DownloadTaskDTO dto);
    
    /**
     * 查询下载进度
     * @param taskId 任务 ID
     * @return 进度信息
     */
    DownloadProgressVO queryProgress(String taskId);
    
    /**
     * 取消下载
     * @param taskId 任务 ID
     */
    void cancelDownload(String taskId);
}
