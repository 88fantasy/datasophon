package com.datasophon.cli.init;

import picocli.CommandLine;

@CommandLine.Command(name = "init", subcommands = {
        InitFirewall.class,
        InitSelinux.class,
        InitSwap.class,
        InitOsUser.class,
        InitSystemConf.class,
        InitHttpd.class,
        InitYumPackage.class,
        InitYumConf.class,
        InitNtpServer.class,
        InitMysql.class,
        InitMysqlAccount.class,
        InitSystemConf.class,
        InitRegistry.class
})
public class Init implements Runnable {
    
    @CommandLine.Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
}
