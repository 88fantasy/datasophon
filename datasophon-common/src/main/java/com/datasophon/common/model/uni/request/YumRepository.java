package com.datasophon.common.model.uni.request;

import lombok.Data;

@Data
public class YumRepository {
        private String name;
        private Boolean online = true;
        private Storage storage = new Storage();
        private Component component = new Component();
        private Yum yum = new Yum();

        @Data
         public static class Yum {
                private Integer repodataDepth = 2;
                private String deployPolicy = "STRICT";
        }
}
