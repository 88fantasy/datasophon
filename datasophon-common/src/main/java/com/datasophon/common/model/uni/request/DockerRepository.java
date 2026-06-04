package com.datasophon.common.model.uni.request;

import lombok.Data;

@Data
public class DockerRepository {
    private String name;
    private Boolean online = true;
    private Storage storage = new Storage();
    private Component component = new Component();
    private Docker docker = new Docker();
    
    @Data
    public static class Docker {
        private boolean v1Enabled = true;
        private boolean forceBasicAuth = false;
        private Integer httpPort;
        private Integer httpsPort;
        private String subdomain;
        private boolean pathEnabled = false;
    }
}
