package com.datasophon.cli.create;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.base.OS;
import com.datasophon.cli.handler.InitNodeHandlerChain;
import com.datasophon.cli.init.InitFirewall;
import com.datasophon.cli.init.InitSelinux;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.utils.ShellUtils;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

@CommandLine.Command(name = "cluster", description = "create cluster")
public class CreateCluster implements Runnable {

    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件")
    String configFilePath;

    @Override
    public void run() {
        File configFile = new File(configFilePath);
        if (!configFile.exists() || configFile.isDirectory()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + configFilePath);
        }
        Yaml yaml = new Yaml();
        String content = FileUtil.readString(configFile, Charset.defaultCharset());
        ClusterConfig clusterConfig = yaml.loadAs(content, ClusterConfig.class);
        GlobalConfig global = clusterConfig.getGlobal();
        if (ObjectUtil.isNull(global.getOs())) {
            // todo 获取当前操作系统
            global.setOs(OS.CentOS7);
        }
        if (ObjectUtil.isNull(global.getArch())) {
            String cpuArchitecture = ShellUtils.getCpuArchitecture();
            global.setArch(ArchType.of(cpuArchitecture));
        }

        if (CollUtil.isNotEmpty(clusterConfig.getNodes())) {
            clusterConfig.getNodes().forEach(node -> {
                InitNodeHandlerChain nodeHandlerChain = new InitNodeHandlerChain(node,
                        Arrays.asList(new InitFirewall(), new InitSelinux())
                );

                nodeHandlerChain.handle();
            });
        }
    }
}
