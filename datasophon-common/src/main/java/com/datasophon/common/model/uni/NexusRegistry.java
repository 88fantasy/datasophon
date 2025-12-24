package com.datasophon.common.model.uni;

import com.datasophon.common.model.Host;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
@Getter
public class NexusRegistry {

    private boolean enable;

    private String type;

    private Package packages;

    private Config config;

    private Host host;

    @Data
    @Getter
    public static class Package {

        private String x86_64;

        private String aarch64;
    }

    @Data
    @Getter
    public static class Config {

        private String webPort;

        private String user;

        private String password;

        private List<String> repositories;

        private String registryPath;

        private String configTarName;

        private String packagesTarName;
    }


    public NexusUri getNexusUri() {
        NexusUri uri = new NexusUri();
        uri.setUri(String.format("http://%s:%s", getHost().getIp(), getConfig().getWebPort()));
        uri.setUser(getConfig().getUser());
        uri.setPassword(getConfig().getPassword());
        return uri;
    }
    
}
