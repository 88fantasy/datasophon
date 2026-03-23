package com.datasophon.cli.init;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.MetaUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.File;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "registryDecode", description = "init registryDecode")
public class InitRegistryDecode extends InitBase {

    @CommandLine.Option(names = {"-e", "--enable"}, description = "是否执行")
    boolean enable = false;

    @CommandLine.Option(names = {"-d", "--datasophonHomePath"}, description = "datasophonHomePath", required = true)
    private String datasophonHomePath;

    @CommandLine.Option(names = {"-i", "--initPath"}, description = "initPath", required = true)
    private String initPath;

    @CommandLine.Option(names = {"-pp", "--registryPath"}, description = "制品安路径", required = true)
    String registryPath;

    @CommandLine.Option(names = {"-cn", "--configTarName"}, description = "元数据包名", required = true)
    String configTarName;

    @CommandLine.Option(names = {"-pn", "--packagesTarName"}, description = "安装包名", required = true)
    String packagesTarName;
    
    @Override
    public String name() {
        return "制品包解压解密";
    }

    @Override
    public boolean doRun(Executor executor) {
        if (!enable) {
            log.info("enable is: {}, skip", enable);
            return true;
        }

        String configTarFullName = String.format("%s/%s", registryPath, configTarName);
        String packagesTarFullName = String.format("%s/%s", registryPath, packagesTarName);
        String configFullDir = String.format("%s/config", registryPath);
        String commonPropertiesPath = String.format("%s/common.properties", configFullDir);
        String packagesFullDir = String.format("%s/packages", registryPath);

        if (!FileUtil.exist(registryPath)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + registryPath);
        }

        // config
        if(!FileUtil.exist(configFullDir)) {
            if (!FileUtil.exist(configTarFullName)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + configTarFullName);
            }
            if (!Base64.isBase64(password)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "password校验失败 : " + password);
            }
            executor.execShell(String.format("tar xzf %s -C %s", configTarFullName, registryPath));
            if (!FileUtil.exist(configFullDir)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + configFullDir);
            }
            try {
                log.info("{}解密, password:{}", commonPropertiesPath, password);
                MetaUtils.decodeFile(FileUtil.file(commonPropertiesPath), password);
            } catch (Exception e) {
                executor.execShell(String.format("rm -rf %s/config", registryPath));
                throw new RuntimeException(String.format("解密失败, configFullDir:%s, password:%s", configFullDir, password), e);
            }
            executor.execShell(String.format("cp %s/common.properties   %s/conf", configFullDir, datasophonHomePath));
            executor.execShell(String.format("cp -r %s/datasophon-init/cluster-sample.yml  %s/datasophon-init/config", configFullDir, datasophonHomePath));
            log.info("{}处理完成", configFullDir);
        } else {
            log.info("{}已存在,跳过解密", configFullDir);
        }

        // packages
        if (!FileUtil.exist(packagesFullDir)) {
            if (!FileUtil.exist(packagesTarFullName)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + packagesTarFullName);
            }
            executor.execShell(String.format("tar xzvf %s -C %s", packagesTarFullName, registryPath));
            if (!FileUtil.exist(packagesFullDir)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packagesFullDir);
            }
        } else {
            log.info("{}已存在", packagesFullDir);
        }

        log.info("{},{}制品包解压解密完成", configFullDir, packagesFullDir);
        return true;
    }
}
