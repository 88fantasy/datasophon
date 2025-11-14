package com.datasophon.cli.init;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.MetaUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "configDecode", description = "init configDecode")
public class InitDecode extends InitBase {

    @CommandLine.Option(names = {"-e", "--enable"}, description = "是否执行")
    boolean enable = false;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-cn", "--configTarName"}, description = "元数据包", required = true)
    String configTarName;

    @CommandLine.Option(names = {"-p", "--password"}, description = "密码", required = true)
    String password;
    
    @Override
    public String name() {
        return "元数据包解密";
    }

    @Override
    public boolean doRun(Executor executor) {
        if (!enable) {
            log.info("enable is: {}, skip", enable);
            return true;
        }

        String configTarFullName = String.format("%s/%s", packagePath, configTarName);
        String configFullDir = String.format("%s/config", packagePath);

        if (!FileUtil.exist(packagePath)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packagePath);
        }
        if (!FileUtil.exist(configTarFullName)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + configTarFullName);
        }
        if (!Base64.isBase64(password)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "password校验失败 : " + password);
        }

        if (FileUtil.exist(configFullDir)) {
            executor.execShell(String.format("rm -rf %s", configFullDir));
        }
        executor.execShell(String.format("tar xzf %s -C %s", configTarFullName, packagePath));
        if (!FileUtil.exist(configFullDir)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + configFullDir);
        }
        try {
            log.info("{}解密", configFullDir);
            MetaUtils.decodeMatchedFiles(configFullDir, password);
        } catch (Exception e) {
            throw new RuntimeException(configFullDir + "解密失败", e);
        }
        log.info("{}解密成功", configFullDir);
        return true;
    }
}
