package com.datasophon.cli.init;

import cn.hutool.core.util.ObjectUtil;
import com.datasophon.cli.base.*;
import com.datasophon.cli.handler.InitNodeHandler;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ShellUtils;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;

import lombok.Data;
import lombok.experimental.Accessors;

import org.yaml.snakeyaml.Yaml;

import com.jcraft.jsch.Session;

import cn.hutool.core.io.FileUtil;

@Data
@Accessors(chain = true)
public abstract class InitBase implements Runnable, InitNodeHandler {
    
    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件")
    String configFilePath;
    
    public ClusterConfig getConfig() {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "config file not found, please check " + configFilePath);
        }
        Yaml yaml = new Yaml();
        String content = FileUtil.readString(configFile, Charset.defaultCharset());
        ClusterConfig clusterConfig = yaml.loadAs(content, ClusterConfig.class);
        GlobalConfig global = clusterConfig.getGlobal();
        if (ObjectUtil.isNull(global.getOs())) {
            global.setOs(OsType.CentOS7);
        }
        if (ObjectUtil.isNull(global.getArch())) {
            String cpuArchitecture = ShellUtils.getCpuArchitecture();
            global.setArch(ArchType.of(cpuArchitecture));
        }
        return clusterConfig;
    }
    
    @Override
    public void run() {
        doRun(new LocalExecutor());
    }
    
    public abstract boolean doRun(Executor executor);
    
    @Override
    public boolean handle(Session session) {
        Executor executor = new JschExecutor(session);
        return doRun(executor);
    }
}
