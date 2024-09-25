package com.datasophon.cli.create;

import picocli.CommandLine;

@CommandLine.Command(name = "create", subcommands = {
        CreateConfig.class,
        CreateCluster.class,
        CreateHttpd.class
})
public class Create implements Runnable {
    
    @CommandLine.Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
}
