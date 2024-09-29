package com.datasophon.cli.init;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.yaml.snakeyaml.Yaml;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;

@Slf4j
@CommandLine.Command(name = "mysql", description = "init mysql")
public class InitMysql implements Runnable {
    
    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件", required = true)
    String configFilePath;
    
    @CommandLine.Option(names = {"-p", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;
    
    @CommandLine.Option(names = {"-f", "--file"}, description = "安装包文件", required = true)
    String mysqlTarName;
    
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
            global.setOs(OsType.CentOS7);
        }
        if (ObjectUtil.isNull(global.getArch())) {
            String cpuArchitecture = ShellUtils.getCpuArchitecture();
            global.setArch(ArchType.of(cpuArchitecture));
        }
        if (StringUtils.isBlank(global.getMysql().getPassword())) {
            throw new RuntimeException("mysql password is needed");
        }
        
        // 卸载mariadb
        ExecResult mariadbResult = ShellUtils.execWithStatus("/", Arrays.asList("rpm -qa | grep mariadb".split("\\s+")), 60);
        if (mariadbResult.getExecResult()) {
            log.info("exist mariadb");
            ShellUtils.execWithStatus("/", Arrays.asList("rpm -qa | grep mariadb | xargs rpm -e --nodeps".split("\\s+")), 60);
        }
        
        // 卸载mysql
        ExecResult mysqlResult = ShellUtils.execWithStatus("/", Arrays.asList("rpm -qa | grep mysql".split("\\s+")), 60);
        if (mysqlResult.getExecResult()) {
            log.info("exist mysql");
            log.info("开始卸载已存在的 mysql...............");
            ShellUtils.execWithStatus("/", Arrays.asList("systemctl stop mysqld".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("rpm -qa | grep mysql | xargs rpm -e".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("rm -rf /var/lib/mysql".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("rm -rf /usr/sbin/mysqld".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("rm -rf /usr/local/mysql".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("rm -rf /etc/my.cnf".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("rm -rf /var/log/mysqld.log".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("rm -rf /var/log/mysql.log".split("\\s+")), 60);
        }
        
        // 安装mysql依赖
        mysqlLib("zlib-devel", "rpm -qa | grep zlib-devel", "yum -y install zlib-devel");
        mysqlLib("bzip2-devel", "rpm -qa | grep bzip2-devel", "yum -y install bzip2-devel");
        mysqlLib("openssl-devel", "rpm -qa | grep openssl-devel", "yum -y install openssl-devel");
        mysqlLib("ncurses-devel", "rpm -qa | grep ncurses-devel", "yum -y install ncurses-devel");
        mysqlLib("mysql-devel", "rpm -qa | grep mysql-devel", "yum -y install mysql-devel");
        if (global.getOs() == OsType.CentOS7) {
            mysqlLib("libaio", "rpm -qa | grep libaio", "yum -y install libaio");
        }
        
        // 安装mysql
        String folder = "mysql";
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("tar -zxvf %s/%s -C %s", packagePath, mysqlTarName, folder).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("rpm -ivh %s/%s/mysql-community-common-8.0.28-1.el8.x86_64.rpm", packagePath, folder).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("rpm -ivh %s/%s/mysql-community-client-plugins-8.0.28-1.el8.x86_64.rpm", packagePath, folder).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("rpm -ivh %s/%s/mysql-community-libs-8.0.28-1.el8.x86_64.rpm", packagePath, folder).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("rpm -ivh %s/%s/mysql-community-devel-8.0.28-1.el8.x86_64.rpm", packagePath, folder).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("rpm -ivh %s/%s/mysql-community-client-8.0.28-1.el8.x86_64.rpm", packagePath, folder).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("rpm -ivh %s/%s/mysql-community-icu-data-files-8.0.28-1.el8.x86_64.rpm", packagePath, folder).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("rpm -ivh %s/%s/mysql-community-server-8.0.28-1.el8.x86_64.rpm", packagePath, folder).split("\\s+")), 60);
        
        ShellUtils.execWithStatus("/", Arrays.asList("mysqld --initialize --user=mysql".split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList("systemctl start mysqld".split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList("systemctl enable mysqld".split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList("sleep 2".split("\\s+")), 60);
        
        String passwd = global.getMysql().getPassword();
        log.info("set password to {}", passwd);
        ExecResult statusResult = ShellUtils.execWithStatus("/", Arrays.asList("systemctl status mysqld | grep running | wc -l".split("\\s+")), 60);
        if (statusResult.getExecOut().equals("1")) {
            log.info("mysql在运行");
            String tmpPasswd = ShellUtils.execWithStatus("/", Arrays.asList("grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}'".split("\\s+")), 60).getExecOut();
            log.info("临时密码:{}", tmpPasswd);
            ShellUtils.execWithStatus("/", Arrays.asList(String.format("/usr/bin/mysqladmin -uroot -p''%s'' password ''%s''", tmpPasswd, passwd).split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"update mysql.user set host='%%' where user ='root';\"", passwd).split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"FLUSH PRIVILEGES;\"", passwd).split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", passwd, passwd).split("\\s+")),
                    60);
            ShellUtils.execWithStatus("/",
                    Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", passwd, passwd).split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"FLUSH PRIVILEGES;\"", passwd).split("\\s+")), 60);
            
            List<String> myconf = new ArrayList<>();
            myconf.add(" [mysqld] ");
            myconf.add("character_set_server=utf8mb4");
            myconf.add("collation_server=utf8mb4_general_ci");
            myconf.add("default-storage-engine=INNODB");
            myconf.add("explicit_defaults_for_timestamp=true");
            myconf.add("max_connections=3600");
            FileUtil.writeLines(myconf, "/etc/my.cnf", Charset.defaultCharset());
            log.info("/etc/my.cnf overwrite sucess.");
            
            ShellUtils.execWithStatus("/", Arrays.asList("systemctl restart mysqld".split("\\s+")), 60);
            ShellUtils.execWithStatus("/", Arrays.asList("systemctl enable mysqld".split("\\s+")), 60);
            log.info("mysql install sucess.");
        }
        
    }
    
    public void mysqlLib(String name, String checkCmd, String installCmd) {
        ExecResult zlibResult = ShellUtils.execWithStatus("/", Arrays.asList(checkCmd.split("\\s+")), 60);
        if (zlibResult.getExecResult()) {
            log.info("{} exists", name);
        } else {
            ShellUtils.execWithStatus("/", Arrays.asList(installCmd.split("\\s+")), 60);
            zlibResult = ShellUtils.execWithStatus("/", Arrays.asList(checkCmd.split("\\s+")), 60);
            if (zlibResult.getExecResult()) {
                log.info("{} install successfully", name);
            }
        }
    }
}
