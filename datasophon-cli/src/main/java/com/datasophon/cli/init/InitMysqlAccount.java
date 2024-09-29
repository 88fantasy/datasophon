package com.datasophon.cli.init;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import org.yaml.snakeyaml.Yaml;

import cn.hutool.core.io.FileUtil;

@CommandLine.Command(name = "mysqlaccount", description = "init mysql account")
@Slf4j
public class InitMysqlAccount implements Runnable {
    
    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件", required = true)
    String configFilePath;
    
    @CommandLine.Option(names = {"-a", "--account"}, description = "app名称,all表示全部", required = true)
    String account;
    
    @CommandLine.Option(names = {"-dp", "--datasphonPasswd"}, description = "密码", defaultValue = "datasphon")
    String datasphonPasswd;
    
    @CommandLine.Option(names = {"-hp", "--hivePasswd"}, description = "密码", defaultValue = "hive")
    String hivePasswd;
    
    @CommandLine.Option(names = {"-dsp", "--dolphinschedulerPasswd"}, description = "密码", defaultValue = "dolphinscheduler")
    String dolphinschedulerPasswd;
    
    @CommandLine.Option(names = {"-usp", "--ustreamPasswd"}, description = "密码", defaultValue = "ustream")
    String ustreamPasswd;
    
    @CommandLine.Option(names = {"-amp", "--amoroPasswd"}, description = "密码", defaultValue = "amoro")
    String amoroPasswd;
    
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
        
        String rootPasswd = global.getMysql().getPassword();
        ExecResult statusResult = ShellUtils.execWithStatus("/", Arrays.asList("systemctl status mysqld | grep running | wc -l".split("\\s+")), 60);
        if (statusResult.getExecOut().equals("1")) {
            switch (account) {
                case "datasphon":
                    initCommonAccount(rootPasswd, "datasphon", datasphonPasswd, "datasphon");
                    break;
                case "dolphinscheduler":
                    initCommonAccount(rootPasswd, "dolphinscheduler", dolphinschedulerPasswd, "dolphinscheduler");
                    break;
                case "ustream":
                    initCommonAccount(rootPasswd, "ustream", ustreamPasswd, "ustream");
                    break;
                case "amoro":
                    initCommonAccount(rootPasswd, "amoro", amoroPasswd, "amoro");
                    break;
                case "hive":
                    initCommonAccount(rootPasswd, "hive", hivePasswd, "hive");
                    break;
                case "all":
                    initCommonAccount(rootPasswd, "datasphon", datasphonPasswd, "datasphon");
                    initCommonAccount(rootPasswd, "dolphinscheduler", dolphinschedulerPasswd, "dolphinscheduler");
                    initCommonAccount(rootPasswd, "ustream", ustreamPasswd, "ustream");
                    initCommonAccount(rootPasswd, "amoro", amoroPasswd, "amoro");
                    initCommonAccount(rootPasswd, "hive", hivePasswd, "hive");
                    break;
                default:
                    throw new CommandLine.ExecutionException(new CommandLine(this), "account not exist : " + account);
            }
        } else {
            ShellUtils.execWithStatus("/", Arrays.asList("systemctl start mysqld".split("\\s+")), 60);
        }
        
    }
    
    public void initCommonAccount(String rootPasswd, String account, String passwd, String dbName) {
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARACTER SET utf8;\"", rootPasswd, dbName).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"CREATE USER '%s'@'%%' IDENTIFIED BY '%s';\"", rootPasswd, account, passwd).split("\\s+")), 60);
        ShellUtils.execWithStatus("/",
                Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"ALTER USER '%s'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", rootPasswd, account, passwd).split("\\s+")), 60);
        ShellUtils.execWithStatus("/",
                Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"ALTER USER '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", rootPasswd, account, passwd).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';\"", rootPasswd, dbName, account).split("\\s+")), 60);
        ShellUtils.execWithStatus("/", Arrays.asList(String.format("mysql -uroot -p''%s'' -e \"FLUSH PRIVILEGES;\"", rootPasswd).split("\\s+")), 60);
    }
}
