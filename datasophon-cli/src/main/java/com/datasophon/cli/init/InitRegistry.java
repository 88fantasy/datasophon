package com.datasophon.cli.init;

import cn.hutool.core.io.resource.ResourceUtil;
import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.base.OS;
import com.datasophon.common.enums.ArchType;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

import java.io.File;

@CommandLine.Command(name = "registry", description = "init artifact store")
public class InitRegistry implements Runnable {

    private static final String DEFAULT_FILE = "cluster-sample.yml";

    @CommandLine.Option(names = {"--with-os"}, description = "操作系统")
    OS os = OS.CentOS7;

    @CommandLine.Option(names = {"--with-arch"}, description = "Cpu架构")
    ArchType archType = ArchType.X86;

    @CommandLine.Option(names = {"-f", "--file"}, description = "制品库文件")
    String artifactPackage = "packages.tar.gz";

    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件")
    String configFile;

    @Override
    public void run() {
        File artifactPackageFile = new File(artifactPackage);
        if (!artifactPackageFile.exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found, please check " + artifactPackage);
        }
        Yaml yaml = new Yaml();
        String content = ResourceUtil.getResourceObj(DEFAULT_FILE).readUtf8Str();
        ClusterConfig clusterConfig = yaml.loadAs(content, ClusterConfig.class);
        GlobalConfig global = clusterConfig.getGlobal();
        global.setOs(os);
        GlobalConfig.MysqlConfig mysql = global.getMysql();
    }
}
