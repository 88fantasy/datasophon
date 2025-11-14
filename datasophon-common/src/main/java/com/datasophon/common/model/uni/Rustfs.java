package com.datasophon.common.model.uni;

import com.datasophon.common.model.Host;
import lombok.Data;

@Data
public class Rustfs {

    private boolean enable;

    private Package packages;

    private Config config;

    private Host host;

    @Data
    public static class Package {

        private String x86_64;

        private String aarch64;
    }

    @Data
    public static class Config {

        private String webPort;

        private String apiPort;

        private String user;

        private String password;
    }

    
}
