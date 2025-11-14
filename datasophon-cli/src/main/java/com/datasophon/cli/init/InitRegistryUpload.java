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

    @CommandLine.Option(names = {"-d", "--datasophonHomePath"}, description = "datasophonHomePath", required = true)
    private String datasophonHomePath;
    @CommandLine.Option(names = {"-i", "--initPath"}, description = "initPath", required = true)
    private String initPath;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-pn", "--packagesTarName"}, description = "安装包名", required = true)
    String packagesTarName;

    @CommandLine.Option(names = {"-cn", "--configTarName"}, description = "元数据包", required = true)
    String configTarName;

    @CommandLine.Option(names = {"-wh", "--webHost"}, description = "webHost", required = true)
    String webHost;

    @CommandLine.Option(names = {"-wp", "--webPort"}, description = "webPort", required = true)
    String webPort;

    @CommandLine.Option(names = {"-u", "--username"}, description = "username", required = true)
    String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "password", required = true)
    String password;

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
        String packageTarFullName = String.format("%s/%s", packagePath, packagesTarName);
        String configTarFullName = String.format("%s/%s", packagePath, configTarName);
        String packageFullDir = String.format("%s/packages", packagePath);
        String configFullDir = String.format("%s/config", packagePath);
        String baseUrl = String.format("http://%s:%s", webHost, webPort);

        if (!FileUtil.exist(packagePath)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packagePath);
        }
        if (!FileUtil.exist(datasophonHomePath)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + datasophonHomePath);
        }

        // config
        if (!FileUtil.exist(configFullDir)) {
            if (!FileUtil.exist(configTarFullName)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + configTarFullName);
            }
            executor.execShell(String.format("tar xzf %s -C %s", configTarFullName, packagePath));
        }
        if (!executor.exists(configFullDir).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + configFullDir);
        } else {
            executor.execShell(String.format("cp -f %s/application.conf  %s/conf", configFullDir, datasophonHomePath));
            executor.execShell(String.format("cp -f %s/common.properties %s/conf", configFullDir, datasophonHomePath));
            executor.execShell(String.format("cp -f %s/datasophon.conf %s/conf", configFullDir, datasophonHomePath));
            executor.execShell(String.format("cp -rf %s/meta %s/conf", configFullDir, datasophonHomePath));
            executor.execShell(String.format("cp -f %s/cluster-sample.yml %s/config", configFullDir, initPath));
        }

        // packages
        if (!FileUtil.exist(packageFullDir)) {
            if (!FileUtil.exist(packageTarFullName)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + packageTarFullName);
            }
            executor.execShell(String.format("tar xzf %s -C %s", packageTarFullName, packagePath));
        }
        if (!FileUtil.exist(packageFullDir)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packageFullDir);
        }

        Pair<Map<String, String>, Map<String, String>> result = NexusFileUtils.repositoryUploadBatch(packageFullDir, baseUrl, username, password);
        log.info("制品库上传完成. 成功数量:{}, 失败数量:{}", result.getLeft().size(), result.getRight().size());
        return true;
    }


}
