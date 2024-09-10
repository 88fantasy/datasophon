package com.datasophon.cli;

import com.datasophon.cli.create.Create;
import com.datasophon.cli.init.Init;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Command(name = "Main", version = "Main 1.0", subcommands = {Create.class, Init.class}, mixinStandardHelpOptions = true)
public class Main implements Runnable {
    
    @Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};
    
    @Override
    public void run() {
        
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}