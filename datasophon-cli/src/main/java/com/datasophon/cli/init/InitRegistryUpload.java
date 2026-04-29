package com.datasophon.cli.init;

import cn.hutool.core.io.FileUtil;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.NexusFileUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

import java.io.File;
import java.util.Map;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "registryUpload", description = "init registryUpload")
public class InitRegistryUpload extends InitBase {

    @CommandLine.Option(names = {"-t", "--type"}, description = "制品类型", required = false)
    String type = "nexus";

    @CommandLine.Option(names = {"-pn", "--productPackagesPath"}, description = "安装包名", required = true)
    String productPackagesPath;

    @CommandLine.Option(names = {"-wh", "--webHost"}, description = "webHost", required = true)
    String webHost;

    @CommandLine.Option(names = {"-wp", "--webPort"}, description = "webPort", required = true)
    String webPort;

    @CommandLine.Option(names = {"-u", "--username"}, description = "username", required = true)
    String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "password", required = true)
    String password;

    @CommandLine.Option(names = {"-e", "--isSuccessDelete"}, description = "是否上传成功后删除文件")
    boolean isSuccessDelete = false;

    @CommandLine.Option(names = {"-disu", "--disableUploadRegistry"}, description = "disableUploadRegistry")
    boolean disableUploadRegistry = false;

    @CommandLine.Option(names = {"-dp", "--dockerHttpPort"}, description = "http端口", required = true)
    Integer dockerHttpPort;

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
        String baseUrl = String.format("http://%s:%s", webHost, webPort);

        if (!FileUtil.exist(productPackagesPath)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + productPackagesPath);
        }

        if(!disableUploadRegistry) {
            log.info("制品库开始上传,url:{}", baseUrl);
            long ts = System.currentTimeMillis();
            Pair<Map<String, String>, Map<String, String>> result = NexusFileUtils.repositoryUploadBatch(productPackagesPath, baseUrl, username, password, isSuccessDelete);
            updateDocker(result.getLeft(), result.getRight(), executor, productPackagesPath, baseUrl, username, password, isSuccessDelete);
            log.info("制品库上传完成,耗时:{}s.成功数量:{}, 失败数量:{}.", (System.currentTimeMillis() - ts) / 1000.0, result.getLeft().size(), result.getRight().size());
        }
        return true;
    }

    private void updateDocker(Map<String, String> uploadSuccess, Map<String, String> uploadFails, Executor executor, String packageFullDir, String baseUrl, String username, String password, boolean isSuccessDelete) {
        String dockerDir = packageFullDir + File.separator + "docker";
        if(executor.exists(dockerDir).getExecResult()) {
            File[] dockerFiles = FileUtil.ls(dockerDir);
            for (File dockerFile : dockerFiles) {
                String imageId = executor.execShell(String.format("docker load -i %s | cut -d' ' -f3", dockerFile.getAbsolutePath())).getExecOut().trim();
                String imageName = StringUtils.substringAfterLast(imageId, "/");
                String rImageName = String.format("%s:%s/docker/%s", webHost, dockerHttpPort, imageName);
                executor.execShell(String.format("docker tag %s %s", imageId, rImageName));
                ExecResult result = executor.execShell(String.format("docker push %s", rImageName));
                if(result.getExecResult()) {
                    log.info("docker push {} 成功",dockerFile.getAbsolutePath());
                    uploadSuccess.put(dockerFile.getAbsolutePath(), result.getExecOut());
                } else {
                    log.info("docker push {} 失败",dockerFile.getAbsolutePath());
                    uploadFails.put(dockerFile.getAbsolutePath(), result.getExecOut());
                }

            }
        }
    }
}
