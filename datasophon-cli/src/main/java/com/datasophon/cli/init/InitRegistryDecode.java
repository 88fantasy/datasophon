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
import java.nio.charset.StandardCharsets;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "registryDecode", description = "init registryDecode")
public class InitRegistryDecode extends InitBase {

    @CommandLine.Option(names = {"-e", "--enable"}, description = "是否执行")
    boolean enable = false;

    @CommandLine.Option(names = {"-d", "--datasophonHomePath"}, description = "datasophonHomePath", required = true)
    private String datasophonHomePath;

    @CommandLine.Option(names = {"-cn", "--config"}, description = "元数据包", required = true)
    String configPath;

    @CommandLine.Option(names = {"-pn", "--packages"}, description = "安装包名", required = true)
    String packagesPath;
    
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
        String rawPackagesPath = String.format("%s/packages/raw/packages", packagesPath);
        String rawOsPath = String.format("%s/packages/raw/os", packagesPath);

        String commonPropertiesPath = String.format("%s/common.properties", configPath);
        String clusterSamplePath = String.format("%s/cluster-sample.yml", configPath);
        String datasophonInitPath = String.format("%s/datasophon-init", packagesPath);

        checkPath(datasophonHomePath);
        checkPath(configPath);
        checkPath(packagesPath);
        checkPath(rawPackagesPath);
        checkPath(rawOsPath);
        checkPath(commonPropertiesPath);
        checkPath(clusterSamplePath);
        checkPath(datasophonInitPath);


        String commonPropertiesStr = FileUtil.readString(new File(commonPropertiesPath), StandardCharsets.UTF_8);
        if(Base64.isBase64(commonPropertiesStr)) {
            MetaUtils.decodeFile(FileUtil.file(commonPropertiesPath), password);
        }
        // cluster-sample.yml不用解密
        
        /*String clusterSampleStr = FileUtil.readString(new File(clusterSamplePath), StandardCharsets.UTF_8);
        if(Base64.isBase64(clusterSampleStr)) {
            MetaUtils.decodeFile(FileUtil.file(clusterSamplePath), password);
        }*/
        executor.execShell(String.format("cp -f %s  %s/conf", commonPropertiesPath, datasophonHomePath));
        executor.execShell(String.format("cp -f %s  %s/config", clusterSamplePath, datasophonInitPath));
        executor.execShell(String.format("cp -rf %s %s", rawPackagesPath, datasophonInitPath));
        executor.execShell(String.format("cp -rf %s %s/packages", rawOsPath, datasophonInitPath));

        log.info("制品包解密初始化完成");
        return true;
    }

    public void checkPath(String path) {
        if (!FileUtil.exist(path))
                throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + path);
    }
}
