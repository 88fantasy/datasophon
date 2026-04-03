package com.datasophon.cli.init;

import cn.hutool.core.io.FileUtil;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.NexusFileUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

import java.util.Map;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "registryUpload", description = "init registryUpload")
public class InitRegistryUpload extends InitBase {

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;

    @CommandLine.Option(names = {"-t", "--type"}, description = "制品类型", required = false)
    String type = "nexus";

    @CommandLine.Option(names = {"-pp", "--registryPath"}, description = "制品安装包路径", required = true)
    String registryPath;

    @CommandLine.Option(names = {"-wh", "--webHost"}, description = "webHost", required = true)
    String webHost;

    @CommandLine.Option(names = {"-wp", "--webPort"}, description = "webPort", required = true)
    String webPort;

    @CommandLine.Option(names = {"-u", "--username"}, description = "username", required = true)
    String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "password", required = true)
    String password;

    @CommandLine.Option(names = {"-disu", "--disableUploadRegistry"}, description = "disableUploadRegistry", required = true)
    boolean disableUploadRegistry = false;

    @Override
    public String name() {
        return "制品库上传";
    }

    @Override
    public boolean doRun(Executor executor) {
        if (!enableRegistry) {
            log.info("registry enable is: {}, skip", enableRegistry);
            return true;
        }
        String packagesFullDir = String.format("%s/packages", registryPath);
        String baseUrl = String.format("http://%s:%s", webHost, webPort);

        if (!FileUtil.exist(packagesFullDir)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + registryPath);
        }

        if(!disableUploadRegistry) {
            log.info("制品库开始上传,url:{}", baseUrl);
            long ts = System.currentTimeMillis();
            Pair<Map<String, String>, Map<String, String>> result = NexusFileUtils.repositoryUploadBatch(packagesFullDir, baseUrl, username, password, false);
            log.info("制品库上传完成,耗时:{}s.成功数量:{}, 失败数量:{}.", (System.currentTimeMillis() - ts) / 1000.0, result.getLeft().size(), result.getRight().size());
        }
        return true;
    }


}
