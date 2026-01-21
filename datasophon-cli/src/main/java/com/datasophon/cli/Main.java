package com.datasophon.cli;

import cn.hutool.core.util.StrUtil;
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
        String ddhHome = System.getenv("DDH_HOME");
        if (StrUtil.isBlank(ddhHome)) {
            System.err.println("DDH_HOME is empty, please set DDH_HOME using ‘export DDH_HOME=xxx’ command");
            System.exit(1);
        }
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}