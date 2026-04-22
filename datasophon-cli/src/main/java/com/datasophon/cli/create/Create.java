package com.datasophon.cli.create;

import picocli.CommandLine;

@CommandLine.Command(name = "create", subcommands = {
        CreateCluster.class
})
public class Create implements Runnable {
    
    @CommandLine.Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
}
