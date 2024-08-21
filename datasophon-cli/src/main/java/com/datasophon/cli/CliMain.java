package com.datasophon.cli;

import com.datasophon.cli.init.CliInit;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Command(name = "CliMain", version = "CliMain 1.0", subcommands = {CliInit.class}, mixinStandardHelpOptions = true)
public class CliMain implements Runnable {
    
    @Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliMain()).execute(args);
        System.exit(exitCode);
    }
}