package com.datasophon.cli.init;

import picocli.CommandLine;

@CommandLine.Command(name = "init", subcommands = {
        InitRegistry.class,
        InitSsh.class
})
public class Init implements Runnable {

    @CommandLine.Parameters(paramLabel = "<command>", description = "指令")
    private final String[] commands = {};

    @Override
    public void run() {

    }

}
