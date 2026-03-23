package com.datasophon.cli.init;

import picocli.CommandLine;

@CommandLine.Command(name = "init", subcommands = {
        InitFirewall.class,
        InitSelinux.class,
        InitSwap.class,
        InitOsUser.class,
        InitSystemConf.class,
        InitOfflineSlave.class,
        InitOfflineServer.class,
        InitNtpServer.class,
        InitMysql.class,
        InitMysqlAppDb.class,
        InitHostname.class,
        InitHugePage.class,
        InitLibrary.class,
        InitOsSafeConf.class,
        InitNmap.class,
        InitBinPackage.class,
        InitAllHost.class,
        InitJdk8.class,
        InitJdk17.class,
        InitTar.class,
        InitBash.class,
        InitRegistry.class,
        InitRegistryUpload.class,
        InitRustfs.class,
        InitRegistryDecode.class
})
public class Init implements Runnable {
    
    @CommandLine.Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
}
