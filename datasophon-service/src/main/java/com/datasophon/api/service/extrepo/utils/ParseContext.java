package com.datasophon.api.service.extrepo.utils;

import java.nio.file.Path;

/**
 * @author zhanghuangbin
 * @date 2025/11/10
 */
public class ParseContext {

    private Path root;

    private Path current;

    public ParseContext(Path root) {
        this.root = root;
        current = root;
    }

    public Path cd(String dir) {
        current = current.resolve(dir);
        return current;
    }


    public String relativeRoot(String fileName) {
        return current.resolve(fileName).resolve(root).toString();
    }
}
