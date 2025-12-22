// utils/ChunkedUploader.js
import axios from "axios";
import SparkMD5 from "spark-md5";
import { API } from "../api";
import { axiosJsonPost, axiosPostUpload } from "../api/request";

class ChunkedUploader {
  constructor({
    chunkSize = 200 * 1024 * 1024, // 5MB per chunk
    maxConcurrency = 1, // 默认逐片上传（设为1即串行），可调高如3
    maxRetries = 3,
    onProgress = (percent) => {},
    onError = (err) => console.error("Upload error:", err),
    onSuccess = () => {},
  } = {}) {
    this.chunkSize = chunkSize;
    this.maxConcurrency = maxConcurrency;
    this.maxRetries = maxRetries;
    this.onProgress = onProgress;
    this.onError = onError;
    this.onSuccess = onSuccess;
  }

  // === 1. 计算文件唯一 ID（基于内容哈希）===
  async calculateFileHash(file) {
    const chunkSize = this.chunkSize;
    return new Promise((resolve, reject) => {
      const chunks = Math.ceil(file.size / chunkSize);
      const spark = new SparkMD5.ArrayBuffer();
      const fileReader = new FileReader();
      let currentChunk = 0;

      fileReader.onload = (e) => {
        spark.append(e.target.result);
        currentChunk++;
        if (currentChunk < chunks) {
          loadNext();
        } else {
          resolve(spark.end());
        }
      };

      fileReader.onerror = () =>
        reject(new Error("File read error during hashing"));

      const loadNext = () => {
        const start = currentChunk * chunkSize;
        const end = Math.min(start + chunkSize, file.size);
        fileReader.readAsArrayBuffer(file.slice(start, end));
      };

      loadNext();
    });
  }

  // 计算单个 Blob 分片的 MD5（返回 Base64）
  calculateMD5(blob) {
    return new Promise((resolve, reject) => {
      const fileReader = new FileReader();
      fileReader.onload = (e) => {
        const spark = new SparkMD5();
        spark.appendBinary(e.target.result);
        const hexMD5 = spark.end(); // 十六进制字符串
        // const base64MD5 = hexToBase64(hexMD5);
        resolve(hexMD5);
      };
      fileReader.onerror = () => reject(fileReader.error);
      fileReader.readAsBinaryString(blob);
    });
  }

  // === 2. 从 localStorage 读取已上传分片 ===
  getUploadedFromCache(fileId) {
    try {
      const cache = localStorage.getItem(`upload_cache:${fileId}`);
      if (!cache) return new Set();
      const data = JSON.parse(cache);
      // 可加过期判断（例如7天）
      if (Date.now() - data.timestamp > 7 * 24 * 60 * 60 * 1000) {
        localStorage.removeItem(`upload_cache:${fileId}`);
        return new Set();
      }
      return new Set(data.uploaded || []);
    } catch (e) {
      console.warn("Read upload cache failed:", e);
      return new Set();
    }
  }

  // === 3. 保存已上传分片到 localStorage ===
  saveUploadedToCache(fileId, uploadedSet, totalChunks, filename) {
    try {
      localStorage.setItem(
        `upload_cache:${fileId}`,
        JSON.stringify({
          uploaded: Array.from(uploadedSet),
          total: totalChunks,
          filename,
          timestamp: Date.now(),
        })
      );
    } catch (e) {
      console.warn("Save upload cache failed (maybe full):", e);
    }
  }

  // === 4. 清除缓存 ===
  clearCache(fileId) {
    localStorage.removeItem(`upload_cache:${fileId}`);
  }

  // === 5. 上传单个分片（带重试）===
  async uploadChunkWithRetry(chunk, index, fileId, filename, retries = 0) {
    const md5 = await this.calculateMD5(chunk);

    console.log("md5", md5);
    const formData = new FormData();
    formData.append("chunk", chunk);
    formData.append("chunkNo", index);
    formData.append("attachId", fileId);
    formData.append("md5", md5);

    try {
      await axiosPostUpload(API.uploadChunk, formData);
      // await axios.post(API.uploadChunk, formData, {
      //   headers: { "Content-Type": "multipart/form-data" },
      //   // timeout: 60000,
      // });
      return true;
    } catch (error) {
      if (retries < this.maxRetries) {
        console.log(
          `Chunk ${index} failed, retrying... (${retries + 1}/${
            this.maxRetries
          })`
        );
        return this.uploadChunkWithRetry(
          chunk,
          index,
          fileId,
          filename,
          retries + 1
        );
      } else {
        throw new Error(
          `Chunk ${index} upload failed after ${this.maxRetries} retries`
        );
      }
    }
  }

  // === 6. 并发/串行上传分片（支持断点）===
  async uploadChunks(chunks, fileId, filename, initialUploadedSet) {
    const total = chunks.length;
    const uploadedSet = new Set(initialUploadedSet); // 本地副本

    // 构建待上传任务
    const tasks = chunks
      .map((chunk, index) => ({ chunk, index }))
      .filter(({ index }) => !uploadedSet.has(index));

    if (tasks.length === 0) {
      this.onProgress(100);
      return;
    }

    // 控制并发（maxConcurrency=1 即逐片上传）
    for (let i = 0; i < tasks.length; i += this.maxConcurrency) {
      const batch = tasks.slice(i, i + this.maxConcurrency);
      const promises = batch.map(async ({ chunk, index }) => {
        await this.uploadChunkWithRetry(chunk, index, fileId, filename);
        uploadedSet.add(index);
        // 实时更新缓存
        this.saveUploadedToCache(fileId, uploadedSet, total, filename);
        // 更新进度
        const percent = Math.floor((uploadedSet.size / total) * 100);
        this.onProgress(percent);
      });

      await Promise.all(promises);
    }
  }

  // === 7. 合并文件 ===
  async mergeFile(fileId, filename, totalChunks) {
    await axios.post("/api/upload/merge", {
      fileId,
      filename,
      totalChunks,
    });
  }

  // === 8. 主入口 ===
  async upload(file) {
    try {
      // Step 1: 生成唯一 fileId（基于内容）
      const fileId = await this.calculateFileHash(file);
      console.log("Uploading file with ID:", fileId);

      // Step 2: 获取已上传分片（优先用缓存，也可结合服务端校验）
      const cachedUploaded = this.getUploadedFromCache(fileId);
      // 【可选】你也可以在此处请求 /api/upload/check 来校验服务端状态
      // 为简化，此处仅用前端缓存（生产建议双重校验）

      // Step 3: 切片
      const chunks = [];
      let start = 0;
      while (start < file.size) {
        chunks.push(file.slice(start, start + this.chunkSize));
        start += this.chunkSize;
      }

      // Step 4: 上传分片
      await this.uploadChunks(chunks, fileId, file.name, cachedUploaded);

      // Step 5: 通知服务端合并
      await this.mergeFile(fileId, file.name, chunks.length);

      // Step 6: 清理缓存
      this.clearCache(fileId);

      this.onSuccess();
    } catch (err) {
      this.onError(err);
    }
  }
}

export default ChunkedUploader;
