package com.datasophon.cli.init;

import com.datasophon.common.model.ClusterConfig;
import com.datasophon.cli.base.Executor;
import com.datasophon.cli.base.JschExecutor;
import com.datasophon.cli.base.LocalExecutor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import com.jcraft.jsch.Session;
import lombok.Data;
import lombok.experimental.Accessors;
import picocli.CommandLine;

@Data
@Accessors(chain = true)
public abstract class InitBase implements Runnable, InitNodeHandler {
    
    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件")
    String configFilePath;

    @CommandLine.Option(names = {"-pwd", "--password"}, description = "密钥", required = true)
    String password;
    
    public ClusterConfig getConfig() {
        return CliUtil.getConfig(configFilePath, password);
    }

    @Override
    public void run() {
        doRun(new LocalExecutor());
    }
    
    public abstract boolean doRun(Executor executor);
    
    @Override
    public boolean handle(Session session) {
        Executor executor = new JschExecutor(session);
        return doRun(executor);
    }
}
