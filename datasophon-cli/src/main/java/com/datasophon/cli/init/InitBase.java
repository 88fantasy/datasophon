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

    @CommandLine.Option(names = {"-pwd", "password"}, description = "密钥", required = true)
    String configPassword;

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;

    @CommandLine.Option(names = {"-ip", "--registryIp"}, description = "制品ip", required = false)
    String registryIp;

    @CommandLine.Option(names = {"-rport", "--registryPort"}, description = "制品端口", required = false)
    String registryPort;

    @CommandLine.Option(names = {"-u", "--registryUsername"}, description = "制品用户", required = false)
    String registryUsername;

    @CommandLine.Option(names = {"-rp", "--registryPassword"}, description = "制品密码", required = false)
    String registryPassword;
    
    public ClusterConfig getConfig() {
        return CliUtil.getConfig(configFilePath, configPassword);
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
