package com.datasophon.common.model.uni.request;

import lombok.Data;

@Data
public class RawRepository {
    private String name;
    private Boolean online = true;
    private Storage storage = new Storage();
    private Component component = new Component();
    private Raw raw = new Raw();
    
    @Data
    public static class Raw {
        private ContentDisposition contentDisposition = ContentDisposition.ATTACHMENT;
    }
}
