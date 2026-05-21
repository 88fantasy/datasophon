package com.datasophon.common.k8s.client;

import com.alibaba.fastjson2.JSONObject;
import com.datasophon.common.Constants;
import com.datasophon.common.k8s.config.DockerRegistryOptions;
import com.datasophon.common.k8s.spec.docker.DockerImageParser;
import com.datasophon.common.k8s.spec.docker.DockerTagUtils;
import com.datasophon.common.k8s.vo.docker.ImageManifest;
import com.datasophon.common.k8s.vo.docker.LoadImageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public class DockerClientWrapperTest {


    private DockerClientWrapperImpl client;

    @BeforeEach
    public void init() {
        DockerRegistryOptions options  = new DockerRegistryOptions();
        options.setInsecure(true);
        options.setHost(Constants.NEXUS_IP);
        options.setPort(Constants.NEXUS_PORT);
        options.setUsername(Constants.NEXUS_USERNAME);
        options.setPassword(Constants.NEXUS_PASSWORD);
        options.setRepo("image");
        client =  new DockerClientWrapperImpl(options);
    }

    @Test
    public void test() throws IOException {
        List<LoadImageResult> manifests = client.load(new File("D:\\Desktop\\VOS集成测试\\k8s测试数据\\images\\portal-3.3.1_image.tar"));
        System.out.println(JSONObject.toJSONString(manifests));
    }

    @Test
    public void test3() {
        String tag = DockerTagUtils.normalRepository("192.168.2.200:8091/image", "docker.io/library/portal:3.3.0");
        System.out.println(tag);
    }

    @Test
    public void test1() throws IOException {
        DockerImageParser parser = new DockerImageParser(new File("D:\\Desktop\\VOS集成测试\\k8s测试数据\\images\\portal-3.3.1_image.tar"));
        List<ImageManifest> originalImages = parser.parseImage();
        System.out.println(JSONObject.toJSONString(originalImages));
    }


    @Test
    public void test2() throws IOException {
        client.push("192.168.2.200:8091/image/bigdata/portal-amd64:3.3.0");
    }

}
