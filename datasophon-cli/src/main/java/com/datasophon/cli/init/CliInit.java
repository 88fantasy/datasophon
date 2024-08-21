package com.datasophon.cli.init;

import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "create config file", subcommands = {
        CliInitConfig.class
})
public class CliInit implements Runnable {
    
    @CommandLine.Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
}
