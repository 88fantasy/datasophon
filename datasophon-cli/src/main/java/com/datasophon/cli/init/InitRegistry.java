package com.datasophon.cli.init;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.Executor;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.registry.Registry;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.model.Host;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;

import java.io.File;
import java.util.ServiceLoader;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.ServiceLoaderUtil;

@Slf4j
@CommandLine.Command(name = "registry", description = "init artifact store")
public class InitRegistry extends InitBase {
    
    @CommandLine.Option(names = {"--with-os"}, description = "操作系统")
    OsType os = OsType.AUTO;
    
    @CommandLine.Option(names = {"--with-arch"}, description = "Cpu架构")
    ArchType archType = ArchType.X86_64;
    
    @CommandLine.Option(arity = "1", names = {"-f", "--file"}, description = "制品库安装文件")
    String registryFilePath;
    
    @Override
    public boolean doRun(Executor executor) {
        
        File registryFile = new File(registryFilePath);
        if (!registryFile.exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found, please check " + registryFilePath);
        }
        ClusterConfig clusterConfig = getConfig();
        GlobalConfig global = clusterConfig.getGlobal();
        GlobalConfig.RegistryConfig registryConfig = global.getRegistry();
        if (registryConfig.getEnable()) {
            Host host = registryConfig.getHost();
            
            ServiceLoader<Registry> registries = ServiceLoaderUtil.load(Registry.class);
            for (Registry registry : registries) {
                if (registry.type().equals(registryConfig.getType())) {
                    registry.setConfig(registryConfig.getConfig());
                    ExecResult status = registry.status(executor, host);
                    if (status.getExecResult()) {
                        log.info("registry is already exist");
                        break;
                    }
                    
                    ExecResult install = registry.install(registryFile, executor, host);
                    if (install.getExecResult()) {
                        log.info("registry install succeed");
                        ExecResult start = registry.start(executor, host);
                        if (start.getExecResult()) {
                            log.info("registry is started");
                        }
                    }
                    
                    break;
                }
            }
            
        }
        return true;
    }
    
    @Override
    public String name() {
        return "安装制品库";
    }
}
