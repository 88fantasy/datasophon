package com.datasophon.common.k8s.client;

import com.alibaba.fastjson.JSONObject;
import com.datasophon.common.Constants;
import com.datasophon.common.PropertiesPathUtils;
import com.datasophon.common.k8s.spec.DockerImageParser;
import com.datasophon.common.k8s.vo.ImageManifest;
import com.datasophon.common.model.uni.NexusUri;
import com.github.dockerjava.api.model.AuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public class DockerClientWrapperTest {


    private DockerClientWrapper client;
    @BeforeEach
    public void init() {
        PropertiesPathUtils.resetPropertyFile();
        NexusUri uri = new NexusUri();
        uri.setEnabled(Constants.NEXUS_ENABLE);
        uri.setUri(String.format("http://%s:%s", Constants.NEXUS_IP, Constants.NEXUS_PORT));
        uri.setUser(Constants.NEXUS_USERNAME);
        uri.setPassword(Constants.NEXUS_PASSWORD);
        AuthConfig config = new AuthConfig()
                .withUsername(uri.getUser())
                .withPassword(uri.getPassword())
                .withRegistryAddress(uri.getUri() + "/image/");
        client =  new DockerClientWrapperImpl(config);
    }

    @Test
    public void test() {
        List<ImageManifest> manifests = client.load(new File("D:\\Desktop\\VOS集成测试\\k8s测试数据\\images\\portal-3.3.1_image.tar"));
        System.out.println(JSONObject.toJSONString(manifests));
    }

    @Test
    public void test3() {
        String tag = client.normalTag("docker.io/library/portal:3.3.0");
        System.out.println(tag);
    }

    @Test
    public void test1() throws IOException {
        DockerImageParser parser = new DockerImageParser();
        List<ImageManifest> originalImages = parser.parseImage(new File("D:\\Desktop\\VOS集成测试\\k8s测试数据\\images\\portal-3.3.1_image.tar"));
        System.out.println(JSONObject.toJSONString(originalImages));
    }


    @Test
    public void test2() throws IOException {
        client.push("192.168.2.200:8091/image/bigdata/portal-amd64:3.3.0");
    }

}
