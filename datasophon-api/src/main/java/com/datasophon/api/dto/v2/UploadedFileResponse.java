/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.dto.v2;

import com.datasophon.dao.entity.UploadTempFile;

import java.util.Date;

import lombok.Data;

/**
 * 上传文件响应，映射 UploadTempFile 实体到前端期望的字段名。
 *
 * <p>字段映射：
 * <ul>
 *   <li>{@code path} → {@code filePath}</li>
 *   <li>{@code byteCnt} → {@code fileSize}</li>
 *   <li>{@code chunkSize} / {@code uploadType} 透传（ChunkedUploader 需要）</li>
 * </ul>
 */
@Data
public class UploadedFileResponse {
    
    private Integer id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private Date createTime;
    /** 分片大小（ChunkedUploader 使用）。 */
    private Long chunkSize;
    /** 上传方式：0=整体 1=分片 2=秒传（ChunkedUploader 使用）。 */
    private Integer uploadType;
    
    public static UploadedFileResponse from(UploadTempFile entity) {
        UploadedFileResponse resp = new UploadedFileResponse();
        resp.id = entity.getId();
        resp.fileName = entity.getFileName();
        resp.filePath = entity.getPath();
        resp.fileSize = entity.getByteCnt();
        resp.createTime = entity.getCreateTime();
        resp.chunkSize = entity.getChunkSize();
        resp.uploadType = entity.getUploadType();
        return resp;
    }
}
