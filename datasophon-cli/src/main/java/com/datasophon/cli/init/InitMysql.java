package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.NexusFileUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.InputStream;
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

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-in", "installPath"}, description = "安装路径", required = true)
    String installPath;

    @CommandLine.Option(names = {"-t", "--tarName"}, description = "tar离线压缩包名", required = true)
    String tarName;

    @CommandLine.Option(names = {"-mp", "--mysqlPort"}, description = "端口", required = true)
    Integer port;

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;

    @CommandLine.Option(names = {"-ip", "--registryIp"}, description = "制品ip", required = true)
    String registryIp;

    @CommandLine.Option(names = {"-rport", "--registryPort"}, description = "制品端口", required = true)
    String registryPort;

    @CommandLine.Option(names = {"-u", "--registryUsername"}, description = "制品用户", required = true)
    String registryUsername;

    @CommandLine.Option(names = {"-rp", "--registryPassword"}, description = "制品密码", required = true)
    String registryPassword;
    
    @Override
    public String name() {
        return "安装mysql";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        OsType osType = executor.getOs();
        String tarPath = String.format("%s/%s", packagePath, tarName);
        String httpRootPath = String.format("%s/tmp/mysql", installPath);
        CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword, tarName, tarPath);
        Boolean isInstalled;
        if(OsType.isUnbuntu(osType)) {
            isInstalled = executor.execShell("dpkg --list|grep mysql").getExecResult();
        } else {
            isInstalled = executor.execShell("rpm -qa | grep mysql").getExecResult();
        }
        if(isInstalled && !force) {
            log.info("exist mysql. force:{}", false);
            checkStart(osType, executor);
            return true;
        }

        if(!executor.exists(tarPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + tarPath);
        }
        if(executor.exists(httpRootPath).getExecResult()) {
            executor.execShell(String.format("rm -rf %s/tmp/mysql", installPath));
        }
        executor.execShell(String.format("mkdir -p %s", httpRootPath));
        executor.execShell(String.format("tar -xvf %s -C %s", tarPath, httpRootPath));
        if(!executor.exists(httpRootPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + httpRootPath);
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
            executor.execShell(String.format("apt localinstall %s/*.rpm -y", httpRootPath));
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
            executor.execShell(String.format("yum -y localinstall %s/*.rpm", httpRootPath));
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
        checkStart(osType, executor);
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
        myconf.add("collation_server=utf8mb4_bin");
        myconf.add("default-storage-engine=INNODB");
        myconf.add("explicit_defaults_for_timestamp=true");
        myconf.add("max_connections=3600");
        myconf.add("max_connections=3600");
        myconf.add(String.format("port=%s", port));
        //myconf.add("datadir=/data/mysql");
        //myconf.add("socket=/data/mysql/mysql.sock");
        // sql_mode bigdata不支持ONLY_FULL_GROUP_BY
        myconf.add("sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");
        return myconf;
    }

    private void rootUserConf(Executor executor){
        executor.execShell(String.format("mysql -uroot -P'%s' -p'%s' -e \"update mysql.user set host='%%' where user ='root';\"", port, password));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"FLUSH PRIVILEGES;\"", port, password));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", port, password, password));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", port, password, password));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"FLUSH PRIVILEGES;\"", port, password));
    }

    private void checkStart(OsType osType, Executor executor) {
        String mysqlService = "mysqld";
        if(OsType.isUnbuntu(osType)){
            mysqlService = "mysql";
        }
        ExecResult result = executor.execShell(String.format("systemctl status %s", mysqlService));
        if(!result.getExecResult()){
            throw new RuntimeException("mysql启动状态失败,请检查");
        }
    }

}
