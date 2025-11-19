package com.datasophon.cli.create;

import cn.hutool.core.collection.CollUtil;
import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.handler.InitNodeHandlerChain;
import com.datasophon.cli.init.*;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.Host;
import com.datasophon.common.model.uni.NexusRegistry;
import com.datasophon.common.model.uni.Rustfs;
import com.datasophon.common.utils.ShellUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import picocli.CommandLine;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@CommandLine.Command(name = "cluster", description = "create cluster")
public class CreateCluster implements Runnable {
    
    @CommandLine.Option(names = {"-p", "datasophonPath"}, description = "datasophon绝对路径", required = true)
    String datasophonPath;
    
    @CommandLine.Option(names = {"-a", "action"}, description = "执行动作", required = true)
    String action;

    @CommandLine.Option(names = {"-if", "initPathOverwriteForce"}, description = "datasophon-init目录存在是否覆盖")
    boolean initPathOverwriteForce = false;

    @CommandLine.Option(names = {"-f", "--mysqlInstallForce"}, description = "mysql存在是否覆盖安装")
    boolean mysqlInstallForce = false;

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;
    
    private String initPath;
    
    private String initConfigPath;
    
    private String packagesPath;
    
    private String initConfigYamlPath;

    private SSHAuthType sshAuthType;
    
    @Override
    public void run() {
        if (!datasophonPath.startsWith("/")) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "datasophonPath必须是绝对路径,即以/开头");
        }
        if (datasophonPath.endsWith("/")) {
            datasophonPath = datasophonPath.substring(0, datasophonPath.length() - 1);
        }
        File path = new File(datasophonPath);
        if (!path.exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "path not found : " + datasophonPath);
        }
        File installPath = new File(Constants.INSTALL_PATH);
        if (!installPath.exists()) {
            ShellUtils.execShell(String.format("mkdir -p %s", Constants.INSTALL_PATH));
        }
        initPath = String.format("%s/datasophon-init", datasophonPath);
        initConfigPath = String.format("%s/config", initPath);
        packagesPath = String.format("%s/packages", initPath);
        initConfigYamlPath = String.format("%s/cluster-sample.yml", initConfigPath);
        log.info("\nDATASOPHON_PATH:{},\nINIT_PATH:{},\nINIT_CONFIG_YAML_PATH:{}", datasophonPath, initPath, initConfigYamlPath);

        ClusterConfig clusterConfig = CliUtil.getConfig(initConfigYamlPath);
        sshAuthType = clusterConfig.getGlobal().getSshAuthType();

        if (action.equals("initALL")) {
            initALL(clusterConfig);
        } else if(action.equals("initSingleNode")) {
            initSingleNode(clusterConfig);
        }
        else {
            throw new CommandLine.ExecutionException(new CommandLine(this),
                    "action[initALL/initSingleNode] not found: " + action);
        }
    }
    
    /**
     * 初始化所有节点
     */
    public void initALL(ClusterConfig config) {
        List<Host> nodes = config.getNodes();
        if (CollUtil.isEmpty(nodes)) {
            return;
        }

        log.info("分发资源包");
        initBinPackage(config, nodes);

        log.info("shell bash设置");
        initBash(config, nodes);

        log.info("安装tar");
        initTar(config, nodes);

        if(enableRegistry) {
            log.info("安装rustfs");
            initRustfs(config);

            log.info("安装registry");
            initRegistry(config);

            log.info("安装registryUpload");
            initRegistryUpload(config, nodes);
        }

        log.info("安装jdk");
        initJdk(config, nodes);
        
        log.info("创建hadoop用户和组");
        initOsUser(config, nodes);
        
        log.info("关闭防火墙");
        initFirewall(config, nodes);
        
        log.info("关闭selinux");
        initSelinux(config, nodes);
        
        log.info("关闭Swap");
        initSwap(config, nodes);
        
        log.info("yum/apt离线源服务配置");
        initOfflineServer(config);

        log.info("yum/apt离线源节点配置");
        initOfflineNodes(config, nodes);

        log.info("初始化依赖库");
        initLibrary(config, nodes);

        log.info("安全配置");
        initOsSafeConf(config, nodes);
        
        log.info("优化系统配置");
        initSystemConf(config, nodes);
        
        log.info("配置all hostname");
        initHostname(config, nodes);
        
        log.info("配置all hosts");
        initAllHost(config, nodes);
        
        log.info("nmap安装");
        initNmap(config);
        
        log.info("配置ntpServer");
        initNtpServer(config);
        
        log.info("配置ntpSlave");
        initNtpSlave(config, nodes);
        
        log.info("安装mysql");
        initMysql(config);
        
        log.info("初始化mysql数据库和账号密码");
        initMysqlAppDb(config);
        
        log.info("关闭透明大页");
        initHugePage(config, nodes);
    }
    
    /**
     * 初始化单节点
     */
    public void initSingleNode(ClusterConfig config) {
        List<Host> initNodes = config.getNodes();
        List<Host> nodes = config.getAddNodes();
        if (CollUtil.isEmpty(nodes)) {
            return;
        }

        log.info("分发资源包");
        initBinPackage(config, nodes);

        log.info("shell bash设置");
        initBash(config, nodes);

        log.info("安装tar");
        initTar(config, nodes);

        log.info("安装jdk");
        initJdk(config, nodes);
        
        log.info("创建hadoop用户和组");
        initOsUser(config, nodes);
        
        log.info("关闭防火墙");
        initFirewall(config, nodes);
        
        log.info("关闭selinux");
        initSelinux(config, nodes);
        
        log.info("关闭Swap");
        initSwap(config, nodes);
        
        log.info("离线yum/apt仓库配置");
        initOfflineNodes(config, nodes);

        log.info("初始化依赖库");
        initLibrary(config, nodes);

        log.info("安全配置");
        initOsSafeConf(config, nodes);
        
        log.info("优化系统配置");
        initSystemConf(config, nodes);
        
        log.info("配置all hostname");
        initHostname(config, nodes);
        
        log.info("配置all hosts");
        initAllHost(config, initNodes);
        initAllHost(config, nodes);
        
        log.info("配置ntpSlave");
        initNtpSlave(config, nodes);
        
        log.info("关闭透明大页");
        initHugePage(config, nodes);
    }
    
    /**
     * 多节点执行
     */
    private void allNodesExec(List<Host> allNodes, InitNodeHandler initNodeHandler) {
        allNodes.forEach(host -> {
            InitNodeHandlerChain nodeHandlerChain = new InitNodeHandlerChain(host, sshAuthType, initNodeHandler);
            nodeHandlerChain.handle();
        });
    }
    
    /**
     * 单节点执行
     */
    private void singleNodesExec(Host node, InitNodeHandler initNodeHandler) {
        InitNodeHandlerChain nodeHandlerChain = new InitNodeHandlerChain(node, sshAuthType, initNodeHandler);
        nodeHandlerChain.handle();
    }
    
    /**
     * 1个server、N个slave节点执行
     */
    private void slavesNodesExec(Host serverNode, List<Host> allNodes, InitNodeHandler slaveHandler) {
        List<Host> slaveNodes = allNodes.stream().filter(host -> !host.getIp().equals(serverNode.getIp()))
                .collect(Collectors.toList());
        
        slaveNodes.forEach(node -> {
            InitNodeHandlerChain workerNodeHandlerChain = new InitNodeHandlerChain(node, sshAuthType, slaveHandler);
            workerNodeHandlerChain.handle();
        });
    }
    
    private void initOsSafeConf(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitOsSafeConf());
    }
    
    private void initBinPackage(ClusterConfig config, List<Host> nodes) {
        InitBinPackage initBinPackage = new InitBinPackage();
        initBinPackage.setInitPath(initPath);
        initBinPackage.setInitPathOverwriteForce(initPathOverwriteForce);
        initBinPackage.setEnableRegistry(config.getGlobal().getRegistry().isEnable());
        initBinPackage.setRegistryPath(config.getGlobal().getRegistry().getConfig().getRegistryPath());
        initBinPackage.setInstallDataDir(config.getGlobal().getInstallDataDir());
        List<Host> workerNodes = nodes.stream().filter( x -> !x.getIsLocalhost()).collect(Collectors.toList());
        allNodesExec(workerNodes, initBinPackage);

    }

    private void initBash(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitBash());
    }

    private void initTar(ClusterConfig config, List<Host> nodes) {
        InitTar initTar = new InitTar();
        initTar.setPackagePath(packagesPath);
        List<Host> workerNodes = nodes.stream().filter( x -> !x.getIsLocalhost()).collect(Collectors.toList());
        allNodesExec(workerNodes, initTar);
    }

    private void initJdk(ClusterConfig config, List<Host> nodes) {
        InitJdk initJdk = new InitJdk();
        initJdk.setPackagePath(packagesPath);
        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initJdk.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getHost().getIp())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        List<Host> workerNodes = nodes.stream().filter( x -> !x.getIsLocalhost()).collect(Collectors.toList());
        allNodesExec(workerNodes, initJdk);
    }
    
    private void initOsUser(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitOsUser());
    }
    
    private void initFirewall(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitFirewall());
    }
    
    private void initSelinux(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitSelinux());
    }
    
    private void initSwap(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitSwap());
    }

    private void initRegistry(ClusterConfig config) {
        NexusRegistry registryConfig = config.getGlobal().getRegistry();
        InitRegistry initRegistry = new InitRegistry();
        initRegistry.setEnableRegistry(registryConfig.isEnable())
                .setType(registryConfig.getType())
                .setPackagePath(packagesPath)
                .setRepositories(registryConfig.getConfig().getRepositories())
                .setInstallPath(config.getGlobal().getInstallDataDir())
                .setX86Tar(registryConfig.getPackages().getX86_64())
                .setAarch64Tar(registryConfig.getPackages().getAarch64())
                .setWebHost(registryConfig.getHost().getIp())
                .setWebPort(registryConfig.getConfig().getWebPort())
                .setUsername(registryConfig.getConfig().getUser())
                .setPassword(registryConfig.getConfig().getPassword());
        singleNodesExec(registryConfig.getHost(), initRegistry);
    }

    private void initRegistryUpload(ClusterConfig config, List<Host> nodes) {
        Host localNode = getLocalNode(nodes);
        NexusRegistry registryConfig = config.getGlobal().getRegistry();
        InitRegistryUpload initRegistryUpload = new InitRegistryUpload();
        initRegistryUpload.setEnableRegistry(registryConfig.isEnable())
                .setType(registryConfig.getType())
                .setRegistryPath(registryConfig.getConfig().getRegistryPath())
                .setWebHost(registryConfig.getHost().getIp())
                .setWebPort(registryConfig.getConfig().getWebPort())
                .setUsername(registryConfig.getConfig().getUser())
                .setPassword(registryConfig.getConfig().getPassword());
        singleNodesExec(localNode, initRegistryUpload);
    }

    private void initRustfs(ClusterConfig config) {
        Rustfs rustfs = config.getGlobal().getRustfs();
        InitRustfs initRustfs = new InitRustfs();
        initRustfs.setEnable(rustfs.isEnable())
            .setPackagePath(packagesPath)
            .setInstallPath(config.getGlobal().getInstallDataDir())
            .setX86Tar(rustfs.getPackages().getX86_64())
            .setAarch64Tar(rustfs.getPackages().getAarch64())
            .setWebHost(rustfs.getHost().getIp())
            .setWebPort(rustfs.getConfig().getWebPort())
            .setApiPort(rustfs.getConfig().getApiPort())
            .setUsername(rustfs.getConfig().getUser())
            .setPassword(rustfs.getConfig().getPassword());
        singleNodesExec(rustfs.getHost(), initRustfs);
    }
    
    private void initOfflineServer(ClusterConfig config) {
        GlobalConfig.YumServer yumServer = config.getGlobal().getYumServer();
        InitOfflineServer initYumServer = new InitOfflineServer();
        initYumServer.setConfigFilePath(initConfigYamlPath);
        initYumServer.setPackagePath(packagesPath)
                .setServerIp(yumServer.getHost().getIp())
                .setServerPort(yumServer.getListenPort())
                .setEnableRegistry(config.getGlobal().getRegistry().isEnable());
        singleNodesExec(yumServer.getHost(), initYumServer);
    }
    
    private void initOfflineNodes(ClusterConfig config, List<Host> nodes) {
        GlobalConfig.YumServer yumServer = config.getGlobal().getYumServer();
        NexusRegistry registryConfig = config.getGlobal().getRegistry();
        InitOfflineSlave initYumConf = new InitOfflineSlave();
        initYumConf.setConfigFilePath(initConfigYamlPath);
        initYumConf.setServerIp(yumServer.getHost().getIp())
                .setServerPort(yumServer.getListenPort());
        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initYumConf.setEnableRegistry(registry.isEnable())
                    .setServerIp(registry.getHost().getIp())
                    .setServerPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword())
                    .setRegistryPath(registry.getConfig().getRegistryPath());
        }
        allNodesExec(nodes, initYumConf);
    }
    
    private void initSystemConf(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitSystemConf());
    }
    
    private void initHostname(ClusterConfig config, List<Host> nodes) {
        nodes.forEach(node -> {
            InitHostname initHostname = new InitHostname();
            initHostname.setHostname(node.getHostname());
            singleNodesExec(node, initHostname);
        });
    }
    
    private void initAllHost(ClusterConfig config, List<Host> nodes) {
        nodes.forEach(node -> {
            InitAllHost initAllHost = new InitAllHost();
            initAllHost.setConfigFilePath(initConfigYamlPath);
            singleNodesExec(node, initAllHost);
        });
    }
    
    private void initNmap(ClusterConfig config) {
        singleNodesExec(config.getGlobal().getNmapServer(), new InitNmap());
    }
    
    private void initNtpServer(ClusterConfig config) {
        InitNtpServer initNtpServer = new InitNtpServer();
        GlobalConfig.NtpServer ntpServer = config.getGlobal().getNtpServer();
        initNtpServer.setConfigFilePath(initConfigYamlPath);
        singleNodesExec(ntpServer.getHost(), initNtpServer);
    }
    
    private void initNtpSlave(ClusterConfig config, List<Host> nodes) {
        GlobalConfig.NtpServer ntpServer = config.getGlobal().getNtpServer();
        InitNtpSlave initNtpSlave = new InitNtpSlave();
        initNtpSlave.setConfigFilePath(initConfigYamlPath);
        initNtpSlave.setNtpServerIp(ntpServer.getHost().getIp());
        slavesNodesExec(ntpServer.getHost(), nodes, initNtpSlave);
    }
    
    private void initLibrary(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitLibrary());
    }
    
    private void initMysql(ClusterConfig config) {
        InitMysql initMysql = new InitMysql();
        GlobalConfig.MysqlConfig mysqlConfig = config.getGlobal().getMysql();
        initMysql.setConfigFilePath(initConfigYamlPath);
        initMysql.setPassword(mysqlConfig.getPassword())
                .setForce(mysqlInstallForce)
                .setPackagePath(packagesPath)
                .setTarName(mysqlConfig.getTarName());
        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initMysql.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getHost().getIp())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(mysqlConfig.getHost(), initMysql);
    }
    
    private void initMysqlAppDb(ClusterConfig config) {
        GlobalConfig.MysqlConfig mysqlConfig = config.getGlobal().getMysql();
        mysqlConfig.getAppDbs().forEach(x -> {
            InitMysqlAppDb initMysqlAppDb = new InitMysqlAppDb();
            initMysqlAppDb.setConfigFilePath(initConfigYamlPath);
            initMysqlAppDb.setRootPassword(mysqlConfig.getPassword())
                    .setAccount(x.getAccount())
                    .setPassword(x.getPassword())
                    .setDbName(x.getDbName());
            singleNodesExec(mysqlConfig.getHost(), initMysqlAppDb);
        });
    }
    
    private void initHugePage(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitHugePage());
    }

    /**
     * 获取localhost, 默认第一个节点
     * @return
     */
    private Host getLocalNode(List<Host> nodes) {
        Host localNode = nodes.get(0);
        List<Host> localNodes = nodes.stream().filter(Host::getIsLocalhost).collect(Collectors.toList());
        if(CollectionUtils.isNotEmpty(localNodes)) {
            localNode = localNodes.get(0);
        }
        return localNode;
    }
}
