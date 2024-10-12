package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
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
    
    @Override
    public String name() {
        return "创建数据库与账号密码";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        ExecResult statusResult = executor.execShell("systemctl status mysqld | grep running | wc -l");
        if (statusResult.getExecOut().equals("1")) {
            initCommonAccount(executor, rootPassword, account, password, dbName);
        } else {
            executor.execShell("systemctl restart mysqld");
        }
        return true;
    }
    
    public void initCommonAccount(Executor executor, String rootPasswd, String account, String passwd, String dbName) {
        executor.execShell(String.format("mysql -uroot -p''%s'' -e \"CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARACTER SET utf8;\"", rootPasswd, dbName));
        executor.execShell(String.format("mysql -uroot -p''%s'' -e \"CREATE USER '%s'@'%%' IDENTIFIED BY '%s';\"", rootPasswd, account, passwd));
        executor.execShell(String.format("mysql -uroot -p''%s'' -e \"ALTER USER '%s'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", rootPasswd, account, passwd));
        executor.execShell(String.format("mysql -uroot -p''%s'' -e \"ALTER USER '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", rootPasswd, account, passwd));
        executor.execShell(String.format("mysql -uroot -p''%s'' -e \"GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';\"", rootPasswd, dbName, account));
        executor.execShell(String.format("mysql -uroot -p''%s'' -e \"FLUSH PRIVILEGES;\"", rootPasswd));
    }
}
