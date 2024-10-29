package com.datasophon.cli.init;

import picocli.CommandLine;

@CommandLine.Command(name = "init", subcommands = {
        InitFirewall.class,
        InitSelinux.class,
        InitSwap.class,
        InitOsUser.class,
        InitSystemConf.class,
        InitYumConf.class,
        InitYumServer.class,
        InitNtpServer.class,
        InitMysql.class,
        InitMysqlAppDb.class,
        InitRegistry.class,
        InitHostname.class,
        InitHugePage.class,
        InitLibrary.class,
        InitOsSafeConf.class,
        InitNmap.class,
        InitBinPackage.class,
        InitAllHost.class,
        InitJdk.class,
        InitTar.class
})
public class Init implements Runnable {
    
    @CommandLine.Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
}
