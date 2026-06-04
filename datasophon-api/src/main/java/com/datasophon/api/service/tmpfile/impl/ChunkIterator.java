package com.datasophon.api.service.tmpfile.impl;

import com.datasophon.dao.entity.UploadTempFile;

import java.io.File;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 */
class ChunkIterator implements Iterator<File> {
    
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
