// utils/uploadUtils.ts
import SparkMD5 from 'spark-md5';
import { axiosJsonPost, axiosPostUpload } from '../api/request';
import { API } from '../api';
import axios from 'axios';
import { message } from 'antd';
import { Export } from '@antv/x6';
import { noop } from 'lodash-es';


export const CHUNK_SIZE = 5 * 1024 * 1024; // 2MB per chunk


// 计算整个文件的 MD5（用于文件唯一 ID）
export const computeFileMD5 = (file: File, chunkSize = 2 * 1024 * 1024): Promise<string> => {
    return new Promise((resolve, reject) => {
        const spark = new SparkMD5.ArrayBuffer();
        const fileReader = new FileReader();
        let currentChunk = 0;
        const totalChunks = Math.ceil(file.size / chunkSize);

        const loadNext = () => {
            const start = currentChunk * chunkSize;
            const end = Math.min(start + chunkSize, file.size);
            const chunk = file.slice(start, end);

            fileReader.onload = (e) => {
                spark.append(e.target?.result as ArrayBuffer); // 累积到 MD5

                currentChunk++;
                if (currentChunk < totalChunks) {
                    // 可选：加个微延迟避免阻塞 UI（尤其在低端设备）
                    setTimeout(loadNext, 1); // 或直接 loadNext()
                } else {
                    const md5 = spark.end();
                    resolve(md5);
                }
            };

            fileReader.onerror = () => {
                reject(new Error('File read error'));
            };

            fileReader.readAsArrayBuffer(chunk);
        };

        loadNext();
    });
};

// 计算 Blob 分片的 Base64 MD5（用于 Content-MD5）
export const computeChunkMD5Base64 = (chunk: Blob): Promise<string> => {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => {
            const spark = new SparkMD5.ArrayBuffer();
            spark.append(e.target?.result as ArrayBuffer);
            const hex = spark.end();
            // console.log('md5', hex)
            // 转为 base64
            // const bytes = hex.match(/\w{2}/g)?.map(b => parseInt(b, 16)) || [];
            // const binary = String.fromCharCode(...bytes);
            resolve(hex);
        };
        reader.onerror = () => reject(reader.error);
        reader.readAsArrayBuffer(chunk);
    });
};

// 从 localStorage 获取已上传分片记录
export const getUploadedChunks = (fileMd5: string): Set<number> => {
    return new Set()
    const key = `upload_chunks_${fileMd5}`;
    const stored = localStorage.getItem(key);
    return stored ? new Set(JSON.parse(stored)) : new Set();
};

// 保存已上传分片到 localStorage
export const saveUploadedChunk = (fileMd5: string, chunkIndex: number) => {
    const key = `upload_chunks_${fileMd5}`;
    const set = getUploadedChunks(fileMd5);
    set.add(chunkIndex);
    localStorage.setItem(key, JSON.stringify(Array.from(set)));
};

// 清除上传记录（可选）
export const clearUploadRecord = (fileMd5: string) => {
    localStorage.removeItem(`upload_chunks_${fileMd5}`);
};





// 模拟上传单个分片（替换为你的 API）
export const uploadChunk = async (
    {
        chunk,
        chunkIndex,
        totalChunks,
        fileMd5,
        filename,
        chunkMd5,
        attachId,
        fileSize,
        onUploadProgress,
        signal
    },

) => {


    let res = await axios.post(API.isChunkUploaded, {
        attachId,
        chunkNo: String(chunkIndex),
        md5: chunkMd5

    })


    if (res.code !== 200 || !res.data) {
        const formData = new FormData();
        formData.append('chunk', chunk);
        formData.append('chunkNo', String(chunkIndex));
        formData.append('attachId', attachId);
        formData.append('md5', chunkMd5);
        // formData.append('filename', filename);

        console.log('formData', formData.get('chunkNo'))



        // const res = await axiosPostUpload(API.uploadChunk, formData)
        res = await axios.post(API.uploadChunk, formData, {
            signal,
            onUploadProgress
            // headers: {
            //     'Content-Type': undefined // 强制 Axios 交由浏览器处理
            // }
            // headers: {
            //     'Content-Type': 'multipart/form-data',
            // },
        })

        console.log('res', res)

        if (res.data.code !== 200) {
            throw new Error(`Chunk ${chunkIndex} upload failed`);
        }
    }




    return res
};

// 合并文件
export const mergeFile = async (md5: string, attachId: string) => {
    const res = await axiosJsonPost(API.mergeChunk, {
        md5,
        attachId
    })


    if (res.code === 200) {
        clearUploadRecord(md5)
    }

    return res
};


export const invokeQueryMergeProgress = async (id: string) => {
    const res = await axiosJsonPost(API.queryMergeProgress, {
        id
    })

    return res

};


export const invokeCreateUploadTask = async (obj = {}, chunk: number = 0) => {
    const {
        file,
        md5
    } = obj
    const res = await axiosPostUpload(API.createShardUploadTask, {
        fileName: file.name,
        contentType: file.type,
        byteCnt: file.size,
        chunk,
        md5
    })

    return res

};



export const invokeMakePartUploadRequest = async (options) => {

    const {
        file,
        onProgress,
        onSuccess,
        onError,
        signal

    } = options

    const _file = file;


    let chunkSize = CHUNK_SIZE

    try {
        // 1. 计算整个文件 MD5（作为唯一标识）
        const fileMd5 = await computeFileMD5(_file);
        console.log('File MD5:', fileMd5);

        // 2. 获取已上传分片
        const uploadedChunks = getUploadedChunks(fileMd5);



        const invokeCreateUploadTaskRes = await invokeCreateUploadTask(
            {
                file,
                md5: fileMd5
            }
        )

        if (invokeCreateUploadTaskRes.data?.uploadType !== 2) {

            if (invokeCreateUploadTaskRes.code === 200) {
                chunkSize = invokeCreateUploadTaskRes.data.chunkSize || CHUNK_SIZE
            }

            const fileSize = _file.size

            const totalChunks = Math.ceil(fileSize / chunkSize);


            // console.log('totalChunks', totalChunks)

            let uploadedSize = 0
            const onUploadProgress = (chunk, progressEvent) => {
                // console.log('progressEvent', progressEvent, fileSize)
                const currentChunkUploaded = progressEvent.loaded;
                const totalUploaded = uploadedSize + currentChunkUploaded;
                const percent = (totalUploaded / file.size) * 100;
                onProgress?.({ percent }, _file);
            }

            // 3. 逐个上传未完成的分片
            for (let i = 0; i < totalChunks; i++) {
                if (uploadedChunks.has(i)) {
                    console.log(`Chunk ${i} already uploaded, skip.`);
                    continue;
                }

                const start = i * chunkSize;
                const end = Math.min(start + chunkSize, _file.size);
                const chunk = _file.slice(start, end);

                // 计算分片 MD5
                const chunkMd5 = await computeChunkMD5Base64(chunk);




                // 上传分片
                await uploadChunk({
                    chunk,
                    chunkIndex: i,
                    totalChunks,
                    fileMd5,
                    filename: _file.name,
                    chunkMd5,
                    attachId: invokeCreateUploadTaskRes.data.id,
                    onUploadProgress: onUploadProgress.bind(noop, {
                        i,
                        totalChunks,
                        chunk
                    }),
                    fileSize,
                    signal
                },);

                // 保存上传记录（断点续传关键）
                saveUploadedChunk(fileMd5, i);

                uploadedSize += chunk.size;

                // 更新进度
                // let percent = Math.round(((i + 1) / totalChunks) * 100);
                // if (percent === 100) {
                //     percent = 99
                // }
                // onProgress?.({ percent }, _file);
            }

            const mergeFileRes = await mergeFile(fileMd5, invokeCreateUploadTaskRes.data.id);
            if (mergeFileRes.code !== 200) {
                throw new Error(`${JSON.stringify(mergeFileRes)}`);

            }
        }



        onSuccess?.(
            {
                name: _file.name,
                uid: _file.uid,
                attachId: invokeCreateUploadTaskRes.data.id,
                ...invokeCreateUploadTaskRes,
                status: 'done',
            },
            _file
        );

        // }

        message.success(`${_file.name} 上传完成！`);



    } catch (err) {
        console.error('Upload error:', err);
        message.error(`上传失败: ${err.message}`);
        onError?.(err);
    }
}
