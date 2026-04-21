package com.datasophon.common.model.uni.request;

import lombok.Data;

@Data
public class HelmRepository {
        private String name;
        private Boolean online = true;
        private Storage storage = new Storage();
        private Component component = new Component();
}
