package com.datasophon.cli.init;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.Executor;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "mysql", description = "init mysql")
public class InitMysql extends InitBase implements InitNodeHandler {
    
    @CommandLine.Option(names = {"-e", "--enable"}, description = "是否安装")
    boolean enable;
    
    @CommandLine.Option(names = {"-p", "--password"}, description = "密码", required = true)
    String password;
    
    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;
    
    @CommandLine.Option(names = {"-f", "--file"}, description = "安装包文件", required = true)
    String mysqlTarName;
    
    @Override
    public String name() {
        return "安装mysql";
    }
    
    @Override
    public boolean doRun(Executor executor) {
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
        
        if (!enable) {
            return false;
        }
        
        // 卸载mariadb
        ExecResult mariadbResult = executor.execShell("rpm -qa | grep mariadb");
        if (mariadbResult.getExecResult()) {
            log.info("exist mariadb");
            executor.execShell("rpm -qa | grep mariadb | xargs rpm -e --nodeps");
        }
        
        // 卸载mysql
        ExecResult mysqlResult = executor.execShell("rpm -qa | grep mysql");
        if (mysqlResult.getExecResult()) {
            log.info("exist mysql");
            log.info("开始卸载已存在的 mysql...............");
            executor.execShell("systemctl stop mysqld");
            executor.execShell("rpm -qa | grep mysql | xargs rpm -e");
            executor.execShell("rm -rf /var/lib/mysql");
            executor.execShell("rm -rf /usr/sbin/mysqld");
            executor.execShell("rm -rf /usr/local/mysql");
            executor.execShell("rm -rf /etc/my.cnf");
            executor.execShell("rm -rf /var/log/mysqld.log");
            executor.execShell("rm -rf /var/log/mysql.log");
        }
        
        // 安装mysql依赖
        mysqlLib(executor, "zlib-devel", "rpm -qa | grep zlib-devel", "yum -y install zlib-devel");
        mysqlLib(executor, "bzip2-devel", "rpm -qa | grep bzip2-devel", "yum -y install bzip2-devel");
        mysqlLib(executor, "openssl-devel", "rpm -qa | grep openssl-devel", "yum -y install openssl-devel");
        mysqlLib(executor, "ncurses-devel", "rpm -qa | grep ncurses-devel", "yum -y install ncurses-devel");
        mysqlLib(executor, "mysql-devel", "rpm -qa | grep mysql-devel", "yum -y install mysql-devel");
        if (global.getOs() == OsType.CentOS7) {
            mysqlLib(executor, "libaio", "rpm -qa | grep libaio", "yum -y install libaio");
        }
        
        // 安装mysql
        String folder = "mysql";
        executor.execShell(String.format("tar -zxvf %s/%s -C %s", packagePath, mysqlTarName, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-common-8.0.28-1.el8.x86_64.rpm", packagePath, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-client-plugins-8.0.28-1.el8.x86_64.rpm", packagePath, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-libs-8.0.28-1.el8.x86_64.rpm", packagePath, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-devel-8.0.28-1.el8.x86_64.rpm", packagePath, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-client-8.0.28-1.el8.x86_64.rpm", packagePath, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-icu-data-files-8.0.28-1.el8.x86_64.rpm", packagePath, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-server-8.0.28-1.el8.x86_64.rpm", packagePath, folder));
        executor.execShell("mysqld --initialize --user=mysql");
        executor.execShell("systemctl start mysqld");
        executor.execShell("systemctl enable mysqld");
        executor.execShell("sleep 2");
        
        log.info("set password to {}", password);
        ExecResult statusResult = executor.execShell("systemctl status mysqld | grep running | wc -l");
        if (statusResult.getExecOut().equals("1")) {
            log.info("mysql在运行");
            String tmpPasswd = executor.execShell("grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}'").getExecOut();
            log.info("临时密码:{}", tmpPasswd);
            executor.execShell(String.format("/usr/bin/mysqladmin -uroot -p''%s'' password ''%s''", tmpPasswd, password));
            executor.execShell(String.format("mysql -uroot -p''%s'' -e \"update mysql.user set host='%%' where user ='root';\"", password));
            executor.execShell(String.format("mysql -uroot -p''%s'' -e \"FLUSH PRIVILEGES;\"", password));
            executor.execShell(String.format("mysql -uroot -p''%s'' -e \"ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", password, password));
            executor.execShell(String.format("mysql -uroot -p''%s'' -e \"ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", password, password));
            executor.execShell(String.format("mysql -uroot -p''%s'' -e \"FLUSH PRIVILEGES;\"", password));
            
            List<String> myconf = new ArrayList<>();
            myconf.add(" [mysqld] ");
            myconf.add("character_set_server=utf8mb4");
            myconf.add("collation_server=utf8mb4_general_ci");
            myconf.add("default-storage-engine=INNODB");
            myconf.add("explicit_defaults_for_timestamp=true");
            myconf.add("max_connections=3600");
            executor.writeLines(myconf, "/etc/my.cnf");
            log.info("/etc/my.cnf overwrite sucess.");
            
            executor.execShell("systemctl restart mysqld");
            executor.execShell("systemctl enable mysqld");
            log.info("mysql install sucess.");
        }
        return true;
    }
    
    public void mysqlLib(Executor executor, String name, String checkCmd, String installCmd) {
        ExecResult zlibResult = executor.execShell(checkCmd);
        if (zlibResult.getExecResult()) {
            log.info("{} exists", name);
        } else {
            executor.execShell(installCmd);
            zlibResult = executor.execShell(checkCmd);
            if (zlibResult.getExecResult()) {
                log.info("{} install successfully", name);
            }
        }
    }
}
