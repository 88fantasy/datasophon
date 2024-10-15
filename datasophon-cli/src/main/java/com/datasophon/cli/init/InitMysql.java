package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "mysql", description = "init mysql")
public class InitMysql extends InitBase implements InitNodeHandler {
    
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
        OsType osType = executor.getOs();
        String mysqlTarPath = String.format("%s/%s", packagePath, mysqlTarName);
        if(!executor.exists(mysqlTarPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packagePath);
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
        if (osType == OsType.CentOS7) {
            mysqlLib(executor, "libaio", "rpm -qa | grep libaio", "yum -y install libaio");
        }
        
        // 安装mysql
        String folder = "mysql";
        executor.execShell(String.format("mkdir -p %s/%s", Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("tar -xvf %s/%s -C %s/%s", packagePath, mysqlTarName, Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-common-8.*", Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-client-plugins-8.*", Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-libs-8.*", Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-devel-8.*", Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-client-8.*", Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-icu-data-files-8.*", Constants.INSTALL_PATH, folder));
        executor.execShell(String.format("rpm -ivh %s/%s/mysql-community-server-8.*", Constants.INSTALL_PATH, folder));
        executor.execShell("mysqld --initialize --user=mysql");
        executor.execShell("systemctl start mysqld");
        executor.execShell("systemctl enable mysqld");
        executor.execShell("sleep 2");
        
        log.info("set password to {}", password);
        ExecResult statusResult = executor.execShell("systemctl status mysqld");
        if (statusResult.getExecResult()) {
            log.info("mysql在运行");
            String tmpPasswd = executor.execShell("grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}'").getExecOut();
            log.info("临时密码:{}", tmpPasswd);
            executor.execShell(String.format("/usr/bin/mysqladmin -uroot -p'%s' password '%s'", tmpPasswd, password));
            executor.execShell(String.format("mysql -uroot -p'%s' -e \"update mysql.user set host='%%' where user ='root';\"", password));
            executor.execShell(String.format("mysql -uroot -p'%s' -e \"FLUSH PRIVILEGES;\"", password));
            executor.execShell(String.format("mysql -uroot -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", password, password));
            executor.execShell(String.format("mysql -uroot -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", password, password));
            executor.execShell(String.format("mysql -uroot -p'%s' -e \"FLUSH PRIVILEGES;\"", password));
            
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
            return true;
        } else {
            log.info("mysql install fail.");
            return false;
        }
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
