import { request } from '@umijs/max';
import SparkMD5 from 'spark-md5';

export const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB

/** 计算整个文件的 MD5（用作服务端去重 key）。 */
async function computeFileMd5(
  file: File,
  chunkSize = DEFAULT_CHUNK_SIZE,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const spark = new SparkMD5.ArrayBuffer();
    const reader = new FileReader();
    let currentChunk = 0;
    const totalChunks = Math.ceil(file.size / chunkSize);

    const loadNext = () => {
      const start = currentChunk * chunkSize;
      const end = Math.min(start + chunkSize, file.size);
      reader.readAsArrayBuffer(file.slice(start, end));
    };

    reader.onload = (e) => {
      spark.append(e.target?.result as ArrayBuffer);
      currentChunk++;
      if (currentChunk < totalChunks) loadNext();
      else resolve(spark.end());
    };

    reader.onerror = () => reject(reader.error);
    loadNext();
  });
}

/** 计算单个分片的 MD5（用于服务端完整性校验）。 */
async function computeChunkMd5(chunk: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const spark = new SparkMD5.ArrayBuffer();
    const reader = new FileReader();
    reader.onload = (e) => {
      spark.append(e.target?.result as ArrayBuffer);
      resolve(spark.end());
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(chunk);
  });
}

export interface ChunkedUploadOptions {
  clusterId: number;
  file: File;
  /** 上传分片进度回调（0-90），合并阶段进度由调用方通过轮询获取。 */
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
}

export interface ChunkedUploadResult {
  attachId: number;
  /** true 表示秒传（文件已存在于服务端），此时合并阶段可跳过。 */
  instant: boolean;
}

/**
 * 分片上传主入口：计算 MD5 → 创建任务 → 逐片上传（断点续传）→ 触发异步合并。
 * 返回 attachId，合并进度需由调用方轮询 `/query-merge-progress`。
 */
export async function chunkedUpload({
  clusterId,
  file,
  onProgress,
  signal,
}: ChunkedUploadOptions): Promise<ChunkedUploadResult> {
  const fileMd5 = await computeFileMd5(file);

  type TaskData = DATASOPHON.UploadedFile & {
    chunkSize?: number;
    uploadType?: number;
  };
  const taskRes = await request<{ data: TaskData }>(
    `/cluster/${clusterId}/deploy/create-shard-task`,
    {
      method: 'POST',
      data: {
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        byteCnt: file.size,
        md5: fileMd5,
      },
    },
  );

  const attachId = taskRes.data.id;
  const chunkSize = taskRes.data.chunkSize ?? DEFAULT_CHUNK_SIZE;

  // uploadType=2 表示秒传，文件已存在
  if (taskRes.data.uploadType === 2) {
    onProgress?.(90);
    return { attachId, instant: true };
  }

  const totalChunks = Math.ceil(file.size / chunkSize);
  let uploadedSize = 0;

  for (let i = 0; i < totalChunks; i++) {
    if (signal?.aborted) throw new DOMException('Upload aborted', 'AbortError');

    const start = i * chunkSize;
    const end = Math.min(start + chunkSize, file.size);
    const chunk = file.slice(start, end);
    const chunkMd5 = await computeChunkMd5(chunk);

    // 断点续传：先检查分片是否已上传
    const checkRes = await request<{ data: boolean }>(
      `/cluster/${clusterId}/deploy/is-chunk-uploaded`,
      { method: 'POST', data: { attachId, chunkNo: i, md5: chunkMd5 } },
    );

    if (!checkRes.data) {
      const formData = new FormData();
      formData.append('chunk', chunk);
      formData.append('chunkNo', String(i));
      formData.append('attachId', String(attachId));
      formData.append('md5', chunkMd5);
      await request(`/cluster/${clusterId}/deploy/upload-chunk`, {
        method: 'POST',
        data: formData,
        requestType: 'form',
      });
    }

    uploadedSize += chunk.size;
    // 上传阶段占 0-90%，合并阶段占 90-100%
    onProgress?.(Math.min(Math.floor((uploadedSize / file.size) * 90), 90));
  }

  // 触发异步合并（async=true 避免大文件合并超时）
  await request(`/cluster/${clusterId}/deploy/merge-chunk`, {
    method: 'POST',
    data: { attachId, md5: fileMd5, async: true },
  });

  return { attachId, instant: false };
}
