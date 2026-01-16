package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "mysql_app_db", description = "init mysql app db")
public class InitMysqlAppDb extends InitBase {
    
    @CommandLine.Option(names = {"-rp", "--rootPassword"}, description = "root密码", required = true)
    String rootPassword;
    
    @CommandLine.Option(names = {"-a", "--account"}, description = "app名称,", required = true)
    String account;
    
    @CommandLine.Option(names = {"-p", "--p"}, description = "密码", required = true)
    String password;
    
    @CommandLine.Option(names = {"-d", "--dbName"}, description = "数据库", required = true)
    String dbName;

    @CommandLine.Option(names = {"-mp", "--mysqlPort"}, description = "端口", required = true)
    Integer port;
    
    @Override
    public String name() {
        return "创建数据库与账号密码";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        OsType osType = executor.getOs();
        String mysqlService = "mysqld";
        if(OsType.isUnbuntu(osType)){
            mysqlService = "mysql";
        }
        ExecResult statusResult = executor.execShell(String.format("systemctl status %s | grep running | wc -l", mysqlService));
        if (statusResult.getExecOut().equals("1")) {
            initCommonAccount(executor, rootPassword, account, password, dbName);
        } else {
            executor.execShell(String.format("systemctl restart %s", mysqlService));
        }
        return true;
    }
    
    public void initCommonAccount(Executor executor, String rootPasswd, String account, String passwd, String dbName) {
        executor.execShell(String.format("mysql -uroot  -P'%s'  -p'%s' -e \"CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARACTER SET utf8mb4  COLLATE utf8mb4_bin;\"", rootPasswd, dbName, port));
        executor.execShell(String.format("mysql -uroot  -P'%s'  -p'%s' -e \"CREATE USER '%s'@'%%' IDENTIFIED BY '%s';\"", rootPasswd, account, passwd, port));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"ALTER USER '%s'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", rootPasswd, account, passwd, port));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"ALTER USER '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", rootPasswd, account, passwd, port));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';\"", rootPasswd, dbName, account, port));
        executor.execShell(String.format("mysql -uroot -P'%s'  -p'%s' -e \"FLUSH PRIVILEGES;\"", rootPasswd));
    }
}
