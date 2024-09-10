package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;

import picocli.CommandLine;
import cn.hutool.system.SystemUtil;

@CommandLine.Command(name = "os", description = "init os")
public class InitOsUser extends InitBase {
    
    @Override
    public boolean doRun(Executor executor) {
        System.out.println(SystemUtil.getOsInfo());
        return true;
    }
    
    @Override
    public String name() {
        return "初始化用户";
    }
}
