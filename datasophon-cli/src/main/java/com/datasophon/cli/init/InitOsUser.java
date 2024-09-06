package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import picocli.CommandLine;

@CommandLine.Command(name = "firewall", description = "init firewall")
public class InitOsUser extends InitBase {

    @Override
    public boolean doRun(Executor executor) {
        return true;
    }

    @Override
    public String name() {
        return "初始化用户";
    }
}
