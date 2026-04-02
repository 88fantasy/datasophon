package com.datasophon.common.model.uni;

import com.datasophon.common.enums.DiskType;
import lombok.Data;

import java.util.List;

@Data
public class Rustfs {

    private boolean enable;

    private Package packages;

    private Config config;

    private List<String> nodes;

    @Data
    public static class Config {

        private String webPort;

        private String apiPort;

        private String user;

        private String password;

        private DiskType installType;

        private String volumes;
    }

    
}
