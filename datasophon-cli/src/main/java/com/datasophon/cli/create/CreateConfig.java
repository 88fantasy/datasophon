package com.datasophon.cli.create;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;

import picocli.CommandLine;

import java.io.File;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;

@CommandLine.Command(name = "config", description = "create config file")
public class CreateConfig implements Runnable {
    
    private static final String DEFAULT_FILE = "cluster-sample.yml";
    
    @CommandLine.Option(names = {"--with-os"}, description = "操作系统")
    OsType os = OsType.CentOS7;
    
    @CommandLine.Option(names = {"--with-arch"}, description = "Cpu架构")
    ArchType archType = ArchType.X86;
    
    @CommandLine.Option(names = {"--with-mysql"}, description = "安装Mysql", negatable = true)
    boolean installMysql;
    
    @CommandLine.Option(names = {"--with-mysql-password"}, description = "Mysql-Root密码")
    String mysqlPassword;
    
    @CommandLine.Option(names = {"-o", "--override"}, description = "覆盖", negatable = true)
    boolean override;
    
    @CommandLine.Parameters(arity = "0..1", paramLabel = "<path>", description = "路径")
    private String path = "cluster-sample.yml";
    
    @Override
    public void run() {
        File target = new File(path);
        if (target.exists() && !override) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file is existed, if you want to override, please use --override");
        }
        Yaml yaml = new Yaml();
        String content = ResourceUtil.getResourceObj(DEFAULT_FILE).readUtf8Str();
        ClusterConfig clusterConfig = yaml.loadAs(content, ClusterConfig.class);
        GlobalConfig global = clusterConfig.getGlobal();
        global.setOs(os);
        global.setArch(archType);
        GlobalConfig.MysqlConfig mysql = global.getMysql();
        mysql.setEnable(installMysql);
        if (StrUtil.isNotEmpty(mysqlPassword)) {
            mysql.setPassword(mysqlPassword);
        }
        String dump = yaml.dumpAs(clusterConfig, Tag.MAP, DumperOptions.FlowStyle.BLOCK);
        FileUtil.writeUtf8String(dump, target);
    }
}
