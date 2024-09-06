package com.datasophon.cli.init;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.MapUtil;
import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.common.model.Host;
import com.datasophon.cli.base.OS;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "ssh", description = "init ssh free")
public class InitSsh implements Runnable {

    @CommandLine.Option(names = {"-d", "--dir"}, description = "制品库目录")
    String artifactPath = "/data/artifact";

    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件")
    String configFilePath;

    private ClusterConfig config;

    private Map<String, Map<OS, String>> packages = MapUtil.builder("pssh",
            MapUtil.builder(OS.CentOS7, "pssh-2.3.1-5.el7.noarch.rpm")
                    .put(OS.CentOS8, "pssh-2.3.1-29.el8.noarch.rpm")
                    .put(OS.OpenEuler, "pssh-2.3.4-1.el9.noarch.rpm").build()
    ).put("tcl",
            MapUtil.builder(OS.CentOS7, "tcl-8.5.13-8.el7.x86_64.rpm")
//                            .put(OS.CentOS8, "pssh-2.3.1-29.el8.noarch.rpm")
//                            .put(OS.OpenEuler, "pssh-2.3.4-1.el9.noarch.rpm")
                    .build()
    ).put("expect",
            MapUtil.builder(OS.CentOS7, "expect-5.45-14.el7_1.x86_64.rpm")
//                            .put(OS.CentOS8, "pssh-2.3.1-29.el8.noarch.rpm")
//                            .put(OS.OpenEuler, "pssh-2.3.4-1.el9.noarch.rpm")
                    .build()
    ).build();

    @Override
    public void run() {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "config file not found, please check " + configFilePath);
        }
        Yaml yaml = new Yaml();
        String content = FileUtil.readString(configFile, Charset.defaultCharset());
        config = yaml.loadAs(content, ClusterConfig.class);
        Collection<Host> nodes = config.getNodes();
        if (CollUtil.isNotEmpty(nodes)) {
            for (Host host : nodes) {
                if (!checkSshPass(host)) {
                    doPass(host);
                }
            }
        }
    }

    private boolean checkSshPass(Host host) {
        System.out.printf("checking if %s[%s] ssh free is ok%n", host.getIp(), host.getHostname());
        ExecResult execResult = ShellUtils.execWithStatus(artifactPath, Collections.singletonList(String.format("ssh -p%s -o StrictHostKeyChecking=no %s@%s 'ls'", host.getPort(), host.getUser(), host.getIp())), 60);
        if (execResult.getExecResult()) {
            System.out.println("ssh free is ok");
        } else {
            System.out.println("ssh free is fail");
            System.out.println(execResult.getExecOut());
        }
        return execResult.getExecResult();
    }

    private void doPass(Host host) {
        OS os = config.getGlobal().getOs();

        // 默认都带 openssh 不检查
        List<String> components = Arrays.asList("pssh", "tcl", "expect");

        for (String component : components) {
            if (CliUtil.checkAndInstall(component, os, artifactPath, packages)) {
                break;
            }
        }

    }
}
