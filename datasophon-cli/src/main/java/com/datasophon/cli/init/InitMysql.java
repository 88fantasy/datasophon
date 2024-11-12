package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.Constants;
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

    @CommandLine.Option(names = {"-f", "--force"}, description = "mysql存在是否覆盖安装")
    boolean force = false;
    
    @Override
    public String name() {
        return "安装mysql";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        OsType osType = executor.getOs();
        String mysqlService = "mysqld";
        if(OsType.isUnbuntu(osType)){
            mysqlService = "mysql";
        }
        ExecResult result = executor.execShell(String.format("systemctl status %s", mysqlService));
        if(result.getExecResult()){
            log.info("mysql has exist");
            if(!force){
                return true;
            }
        }
        if(OsType.isUnbuntu(osType)) {
            ExecResult mysqlResult = executor.execShell("dpkg --list|grep mysql");
            if (mysqlResult.getExecResult()) {
                log.info("exist mysql");
                log.info("开始卸载已存在的 mysql...............");
                executor.execShell("systemctl stop mysql");
                executor.execShell("apt remove mysql-common -y");
                executor.execShell("apt autoremove --purge mysql-server-8.0 -y");
                executor.execShell("dpkg -P systemd-timesyncd");
                executor.execShell("rm -rf /var/lib/mysql");
                executor.execShell("rm -rf /etc/mysql");
                executor.execShell("rm -rf /var/log/mysql");
            }
            executor.execShell("apt install mysql-server-8.0 -y");
            ExecResult statusResult = executor.execShell("systemctl status mysql");
            if (statusResult.getExecResult()) {
                log.info("mysql在运行");
                rootUserConf(executor);
                executor.execShell("mv  /etc/mysql/mysql.conf.d/mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf.bak");
                executor.writeLines(getMysqldConf(), "/etc/mysql/mysql.conf.d/mysqld.cnf");
                log.info("mysqld.cnf overwrite sucess.");

                executor.execShell("systemctl restart mysql");
                executor.execShell("systemctl enable mysql");
                log.info("mysql install sucess.");
                return true;
            } else {
                log.info("mysql install fail.");
                System.exit(1);
            }
        } else {
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
            if (osType == OsType.CENTOS_7) {
                mysqlLib(executor, "libaio", "rpm -qa | grep libaio", "yum -y install libaio");
            }

            // 安装mysql
            executor.execShell("yum -y install mysql-community-server");
            // 初始化配置
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
                rootUserConf(executor);
                executor.execShell("mv /etc/my.cnf /etc/my.cnf.bak");
                executor.writeLines(getMysqldConf(), "/etc/my.cnf");
                log.info("/etc/my.cnf overwrite sucess.");

                executor.execShell("systemctl restart mysqld");
                executor.execShell("systemctl enable mysqld");
                log.info("mysql install sucess.");
                return true;
            } else {
                log.info("mysql install fail.");
                System.exit(1);
            }
        }
        return true;
    }
    
    private void mysqlLib(Executor executor, String name, String checkCmd, String installCmd) {
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

    private List<String> getMysqldConf(){
        List<String> myconf = new ArrayList<>();
        myconf.add("[mysqld]");
        myconf.add("character_set_server=utf8mb4");
        myconf.add("collation_server=utf8mb4_general_ci");
        myconf.add("default-storage-engine=INNODB");
        myconf.add("explicit_defaults_for_timestamp=true");
        myconf.add("max_connections=3600");
        myconf.add("max_connections=3600");
        return myconf;
    }

    private void rootUserConf(Executor executor){
        executor.execShell(String.format("mysql -uroot -p'%s' -e \"update mysql.user set host='%%' where user ='root';\"", password));
        executor.execShell(String.format("mysql -uroot -p'%s' -e \"FLUSH PRIVILEGES;\"", password));
        executor.execShell(String.format("mysql -uroot -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", password, password));
        executor.execShell(String.format("mysql -uroot -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", password, password));
        executor.execShell(String.format("mysql -uroot -p'%s' -e \"FLUSH PRIVILEGES;\"", password));
    }


}
