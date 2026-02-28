// md5.worker.js
import { createMD5 } from 'hash-wasm';

self.onmessage = async (e) => {
    const { file } = e.data;
    const chunkSize = e.chunkSize || 64 * 1024 * 1024; // 64MB
    const hasher = await createMD5();

    try {
        const totalChunks = Math.ceil(file.size / chunkSize);

        for (let i = 0; i < totalChunks; i++) {
            const start = i * chunkSize;
            const end = Math.min(start + chunkSize, file.size);
            const chunk = file.slice(start, end);

            // 同步读取当前块
            const reader = new FileReaderSync();
            const buffer = reader.readAsArrayBuffer(chunk);

            // 喂给 Wasm 实例
            hasher.update(new Uint8Array(buffer));

            // 发送进度给主线程
            self.postMessage({
                type: 'PROGRESS',
                progress: Math.round(((i + 1) / totalChunks) * 100),
            });
        }

        const hash = hasher.digest();
        self.postMessage({ type: 'SUCCESS', hash });
    } catch (error) {
        self.postMessage({ type: 'ERROR', error: error.message });
    }
};