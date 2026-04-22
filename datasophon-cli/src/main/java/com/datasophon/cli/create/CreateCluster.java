package com.datasophon.cli.create;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.handler.InitNodeHandlerChain;
import com.datasophon.cli.init.*;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.ClusterConfig;
import com.datasophon.common.model.GlobalConfig;
import com.datasophon.common.model.Host;
import com.datasophon.common.model.uni.NexusRegistry;
import com.datasophon.common.model.uni.Rustfs;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.ShellUtils;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@CommandLine.Command(name = "cluster", description = "create cluster")
public class CreateCluster implements Runnable {
    
    @CommandLine.Option(names = {"-p", "--datasophonPath"}, description = "datasophon绝对路径", required = true)
    String datasophonPath;

    @CommandLine.Option(names = {"-in", "--installPath"}, description = "安装路径", required = true)
    String installPath;

    @CommandLine.Option(names = {"-pwd", "--password"}, description = "密钥", required = true)
    String password;
    
    @CommandLine.Option(names = {"-a", "--action"}, description = "执行动作", required = true)
    String action;

    @CommandLine.Option(names = {"-if", "--initPathOverwriteForce"}, description = "datasophon-init目录存在是否覆盖")
    boolean initPathOverwriteForce = false;

    @CommandLine.Option(names = {"-disu", "--disableUploadRegistry"}, description = "制品是否上传")
    boolean disableUploadRegistry = false;

    @CommandLine.Option(names = {"-f", "--mysqlInstallForce"}, description = "mysql存在是否覆盖安装")
    boolean mysqlInstallForce = false;

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;

    @CommandLine.Option(names = {"-pn", "--productPackagesPath"}, description = "安装包名", required = true)
    String productPackagesPath;
    
    private String initPath;
    
    private String initConfigPath;
    
    private String packagesPath;
    
    private String initConfigYamlPath;

    private SSHAuthType sshAuthType;

    private Map<String, Host> globalNodes;

    private Host localHost;

    String localIP;

    @Override
    public void run() {
        if (!datasophonPath.startsWith("/") || !installPath.startsWith("/")) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "datasophonPath、installPath必须是绝对路径,即以/开头");
        }
        if (datasophonPath.endsWith("/")) {
            datasophonPath = datasophonPath.substring(0, datasophonPath.length() - 1);
        }
        File path = new File(datasophonPath);
        if (!path.exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "path not found : " + datasophonPath);
        }
        if (FileUtil.exist(installPath)) {
            ShellUtils.execShell(String.format("mkdir -p %s", installPath));
        }
        initPath = String.format("%s/datasophon-init", datasophonPath);
        initConfigPath = String.format("%s/config", initPath);
        packagesPath = String.format("%s/packages", initPath);
        initConfigYamlPath = String.format("%s/cluster-sample.yml", initConfigPath);
        log.info("\nDATASOPHON_PATH:{},\nINIT_PATH:{},\nINIT_CONFIG_YAML_PATH:{}", datasophonPath, initPath, initConfigYamlPath);

        ClusterConfig clusterConfig = CliUtil.getConfig(initConfigYamlPath, password);
        sshAuthType = clusterConfig.getGlobal().getSshAuthType();
        globalNodes = clusterConfig.getNodes().stream().collect(Collectors.toMap(Host::getHostname, host -> host));

        //本地节点设置
        localHost = clusterConfig.getNodes().get(0);
        localIP = HostUtils.getLocalIp();
        clusterConfig.getNodes().forEach( x -> {
            if (x.getIp().equals(localIP)) {
                localHost = x;
                log.info("local host is:{}", localHost);
            }
        });

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

        log.info("安装jdk8");
        initJdk8(config, nodes);

        log.info("安装jdk17");
        initJdk17(config, nodes);
        
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

        log.info("安装k8s集群");
        if(config.getGlobal().getKubernetes().getEnable()) {
            initK8SBaseServicess(config);
            initK8SKuboard(config);
            initK8sRegistryConf(config);
            initDocker(config);
            initKubectl(config);
            initHelm(config);
            initHelmify(config);
        }

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

        log.info("安装jdk8");
        initJdk8(config, nodes);

        log.info("安装jdk17");
        initJdk17(config, nodes);
        
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
        initBinPackage.setInstallPath(installPath);
        initBinPackage.setDatasophonInitPath(initPath);
        initBinPackage.setInitPathOverwriteForce(initPathOverwriteForce);
        initBinPackage.setEnableRegistry(config.getGlobal().getRegistry().isEnable());
        List<Host> workerNodes = nodes.stream().filter( x -> !x.getIp().equals(localIP)).collect(Collectors.toList());
        allNodesExec(workerNodes, initBinPackage);

    }

    private void initBash(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitBash());
    }

    private void initTar(ClusterConfig config, List<Host> nodes) {
        InitTar initTar = new InitTar();
        initTar.setPackagePath(packagesPath);
        List<Host> workerNodes = nodes.stream().filter( x -> !x.getIp().equals(localIP)).collect(Collectors.toList());
        allNodesExec(workerNodes, initTar);
    }

    private void initJdk8(ClusterConfig config, List<Host> nodes) {
        InitJdk8 initJdk = new InitJdk8();
        initJdk.setPackagePath(packagesPath);
        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initJdk.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        List<Host> workerNodes = nodes.stream().filter( x -> !x.getIp().equals(localIP)).collect(Collectors.toList());
        allNodesExec(workerNodes, initJdk);
    }

    private void initJdk17(ClusterConfig config, List<Host> nodes) {
        InitJdk17 initJdk = new InitJdk17();
        initJdk.setPackagePath(packagesPath);
        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initJdk.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        List<Host> workerNodes = nodes.stream().filter( x -> !x.getIp().equals(localIP)).collect(Collectors.toList());
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
        NexusRegistry registry = config.getGlobal().getRegistry();
        InitRegistry initRegistry = new InitRegistry();
        initRegistry.setPackagePath(packagesPath)
                .setInstallPath(installPath)
                .setRepositories(registry.getConfig().getRepositories())
                .setX86Tar(registry.getPackages().getX86_64())
                .setAarch64Tar(registry.getPackages().getAarch64())
                .setWebHost(registry.getNode())
                .setWebPort(registry.getConfig().getWebPort())
                .setUsername(registry.getConfig().getUser())
                .setPassword(registry.getConfig().getPassword());
        if(registry.isEnable()) {
            initRegistry.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(globalNodes.get(registry.getNode()), initRegistry);
    }

    private void initRegistryUpload(ClusterConfig config, List<Host> nodes) {
        NexusRegistry registry = config.getGlobal().getRegistry();
        InitRegistryUpload initRegistryUpload = new InitRegistryUpload();
        initRegistryUpload.setType(registry.getType())
                .setWebHost(registry.getNode())
                .setWebPort(registry.getConfig().getWebPort())
                .setUsername(registry.getConfig().getUser())
                .setPassword(registry.getConfig().getPassword())
                .setDisableUploadRegistry(disableUploadRegistry)
                        .setProductPackagesPath(productPackagesPath);
        if(registry.isEnable()) {
            initRegistryUpload.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(localHost, initRegistryUpload);
    }

    private void initRustfs(ClusterConfig config) {
        Rustfs rustfs = config.getGlobal().getRustfs();
        InitRustfs initRustfs = new InitRustfs();
        initRustfs.setEnable(rustfs.isEnable())
            .setPackagePath(packagesPath)
            .setInstallPath(installPath)
            .setX86Tar(rustfs.getPackages().getX86_64())
            .setAarch64Tar(rustfs.getPackages().getAarch64())
            .setWebHost(rustfs.getNodes().get(0))
            .setWebPort(rustfs.getConfig().getWebPort())
            .setApiPort(rustfs.getConfig().getApiPort())
            .setUsername(rustfs.getConfig().getUser())
            .setPassword(rustfs.getConfig().getPassword());
        singleNodesExec(globalNodes.get(rustfs.getNodes().get(0)), initRustfs);
    }
    
    private void initOfflineServer(ClusterConfig config) {
        GlobalConfig.YumServer yumServer = config.getGlobal().getYumServer();
        InitOfflineServer initYumServer = new InitOfflineServer();
        initYumServer.setConfigFilePath(initConfigYamlPath)
                        .setConfigPassword(password);
        initYumServer.setPackagePath(packagesPath)
                .setServerIp(yumServer.getNode())
                .setServerPort(yumServer.getListenPort())
                .setEnableRegistry(config.getGlobal().getRegistry().isEnable());
        singleNodesExec(globalNodes.get(yumServer.getNode()), initYumServer);
    }
    
    private void initOfflineNodes(ClusterConfig config, List<Host> nodes) {
        GlobalConfig.YumServer yumServer = config.getGlobal().getYumServer();
        InitOfflineSlave initYumConf = new InitOfflineSlave();
        initYumConf.setConfigFilePath(initConfigYamlPath);
        initYumConf.setServerIp(yumServer.getNode());
        initYumConf.setConfigPassword(password);
        initYumConf.setServerIp(yumServer.getNode())
                .setServerPort(yumServer.getListenPort());
        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initYumConf.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
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
            initAllHost.setConfigPassword(password);
            singleNodesExec(node, initAllHost);
        });
    }
    
    private void initNmap(ClusterConfig config) {
        singleNodesExec(globalNodes.get(config.getGlobal().getNmapServer().getNode()), new InitNmap());
    }
    
    private void initNtpServer(ClusterConfig config) {
        InitNtpServer initNtpServer = new InitNtpServer();
        GlobalConfig.NtpServer ntpServer = config.getGlobal().getNtpServer();
        initNtpServer.setConfigFilePath(initConfigYamlPath);
        initNtpServer.setConfigPassword(password);
        singleNodesExec(globalNodes.get(ntpServer.getNode()), initNtpServer);}
    
    private void initNtpSlave(ClusterConfig config, List<Host> nodes) {
        GlobalConfig.NtpServer ntpServer = config.getGlobal().getNtpServer();
        InitNtpSlave initNtpSlave = new InitNtpSlave();
        initNtpSlave.setConfigFilePath(initConfigYamlPath);
        initNtpSlave.setConfigPassword(password);
        initNtpSlave.setNtpServerIp(globalNodes.get(ntpServer.getNode()).getIp());
        slavesNodesExec(globalNodes.get(ntpServer.getNode()), nodes, initNtpSlave);
    }
    
    private void initLibrary(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitLibrary());
    }
    
    private void initMysql(ClusterConfig config) {
        InitMysql initMysql = new InitMysql();
        GlobalConfig.MysqlConfig mysqlConfig = config.getGlobal().getMysql();
        initMysql.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);
        initMysql.setPassword(mysqlConfig.getPassword())
                .setForce(mysqlInstallForce)
                .setPackagePath(packagesPath)
                .setInstallPath(installPath)
                .setPort(mysqlConfig.getPort())
                .setX86Tar(config.getGlobal().getPackages().getMysql().getX86_64())
                .setAarch64Tar(config.getGlobal().getPackages().getMysql().getAarch64());
        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initMysql.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(globalNodes.get(mysqlConfig.getNode()), initMysql);
    }
    
    private void initMysqlAppDb(ClusterConfig config) {
        GlobalConfig.MysqlConfig mysqlConfig = config.getGlobal().getMysql();
        mysqlConfig.getAppDbs().forEach(x -> {
            InitMysqlAppDb initMysqlAppDb = new InitMysqlAppDb();
            initMysqlAppDb.setConfigFilePath(initConfigYamlPath)
                            .setConfigPassword(password);
            initMysqlAppDb.setRootPassword(mysqlConfig.getPassword())
                    .setAccount(x.getAccount())
                    .setPassword(x.getPassword())
                    .setDbName(x.getDbName())
                    .setPort(mysqlConfig.getPort());
            singleNodesExec(globalNodes.get(mysqlConfig.getNode()), initMysqlAppDb);
        });
    }

    private void initK8SBaseServicess(ClusterConfig config) {
        boolean KubernetesEnable = config.getGlobal().getKubernetes().getEnable();
        GlobalConfig.BaseServices baseServices = config.getGlobal().getKubernetes().getBaseServices();
        InitK8sBaseServices initK8sBaseServices = new InitK8sBaseServices();
        initK8sBaseServices.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);

        initK8sBaseServices.setEnableKubernetesCluster(KubernetesEnable)
                .setNamespaces(baseServices.getNamespaces())
                .setMasters(baseServices.getMasters())
                .setNodes(baseServices.getNodes())
                .setSealos(baseServices.getSealos().getEnable())
                .setSealosX86Tar(config.getGlobal().getPackages().getSealos().getX86_64())
                .setSealosArmTar(config.getGlobal().getPackages().getSealos().getAarch64())
                .setKubernetes(baseServices.getKubernetesI().getEnable())
                .setKubernetesX86Tar(config.getGlobal().getPackages().getKubernetesI().getX86_64())
                .setKubernetesArmTar(config.getGlobal().getPackages().getKubernetesI().getAarch64())
                .setHelm(baseServices.getHelmI().getEnable())
                .setHelmTX86ar(config.getGlobal().getPackages().getHelmI().getX86_64())
                .setHelmArmTar(config.getGlobal().getPackages().getHelmI().getAarch64())
                .setCalico(baseServices.getCalicoI().getEnable())
                .setCalicoX86Tar(config.getGlobal().getPackages().getCalicoI().getX86_64())
                .setCalicoArmTar(config.getGlobal().getPackages().getCalicoI().getAarch64())
                .setIngress(baseServices.getIngressI().getEnable())
                .setIngressX86Tar(config.getGlobal().getPackages().getIngressI().getX86_64())
                .setIngressArmTar(config.getGlobal().getPackages().getIngressI().getAarch64())
                .setPackagePath(packagesPath);

        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            initK8sBaseServices.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(globalNodes.get(baseServices.getMasters().get(0)), initK8sBaseServices);
    }

    private void initK8SKuboard(ClusterConfig config) {
        GlobalConfig.Kuboard services = config.getGlobal().getKubernetes().getKuboardI();
        InitK8sKuboard instance = new InitK8sKuboard();
        instance.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);

        instance.setEnableKubernetesCluster(config.getGlobal().getKubernetes().getEnable())
                .setKuboardX86Tar(config.getGlobal().getPackages().getKuboardI().getX86_64())
                .setKuboardArmTar(config.getGlobal().getPackages().getKuboardI().getAarch64())
                .setPackagePath(packagesPath);

        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            instance.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(globalNodes.get(services.getNodes().get(0)), instance);
    }

    private void initK8sRegistryConf(ClusterConfig config) {
        GlobalConfig.BaseServices services = config.getGlobal().getKubernetes().getBaseServices();
        InitK8sRegistryConf instance = new InitK8sRegistryConf();
        instance.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);

        instance.setEnableKubernetesCluster(config.getGlobal().getKubernetes().getEnable())
                .setDockerHttpPort(config.getGlobal().getRegistry().getConfig().getDockerHttpPort());

        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            instance.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        List<Host> k8sNodes = new ArrayList<>();
        k8sNodes.addAll(services.getMasters().stream().map(x -> globalNodes.get(x)).collect(Collectors.toList()));
        k8sNodes.addAll(services.getNodes().stream().map(x -> globalNodes.get(x)).collect(Collectors.toList()));
        allNodesExec(k8sNodes, instance);
    }

    private void initDocker(ClusterConfig config) {
        GlobalConfig.BaseServices services = config.getGlobal().getKubernetes().getBaseServices();
        InitDocker instance = new InitDocker();
        instance.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);

        instance.setEnableKubernetesCluster(config.getGlobal().getKubernetes().getEnable())
                .setPackagePath(packagesPath)
                .setInstallPath(installPath)
                .setX86Tar(config.getGlobal().getPackages().getDocker().getX86_64())
                .setAarch64Tar(config.getGlobal().getPackages().getDocker().getAarch64())
                .setDockerHttpPort(config.getGlobal().getRegistry().getConfig().getDockerHttpPort());

        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            instance.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(localHost, instance);
    }

    private void initKubectl(ClusterConfig config) {
        GlobalConfig.BaseServices services = config.getGlobal().getKubernetes().getBaseServices();
        InitKubectl instance = new InitKubectl();
        instance.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);

        instance.setEnableKubernetesCluster(config.getGlobal().getKubernetes().getEnable())
                .setPackagePath(packagesPath)
                .setInstallPath(installPath)
                .setX86Tar(config.getGlobal().getPackages().getDocker().getX86_64())
                .setAarch64Tar(config.getGlobal().getPackages().getDocker().getAarch64());

        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            instance.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(localHost, instance);
    }

    private void initHelm(ClusterConfig config) {
        GlobalConfig.BaseServices services = config.getGlobal().getKubernetes().getBaseServices();
        InitHelm instance = new InitHelm();
        instance.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);

        instance.setEnableKubernetesCluster(config.getGlobal().getKubernetes().getEnable())
                .setPackagePath(packagesPath)
                .setInstallPath(installPath)
                .setX86Tar(config.getGlobal().getPackages().getDocker().getX86_64())
                .setAarch64Tar(config.getGlobal().getPackages().getDocker().getAarch64());

        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            instance.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(localHost, instance);
    }

    private void initHelmify(ClusterConfig config) {
        GlobalConfig.BaseServices services = config.getGlobal().getKubernetes().getBaseServices();
        InitHelmify instance = new InitHelmify();
        instance.setConfigFilePath(initConfigYamlPath)
                .setConfigPassword(password);

        instance.setEnableKubernetesCluster(config.getGlobal().getKubernetes().getEnable())
                .setPackagePath(packagesPath)
                .setInstallPath(installPath)
                .setX86Tar(config.getGlobal().getPackages().getDocker().getX86_64())
                .setAarch64Tar(config.getGlobal().getPackages().getDocker().getAarch64());

        NexusRegistry registry = config.getGlobal().getRegistry();
        if(registry.isEnable()) {
            instance.setEnableRegistry(registry.isEnable())
                    .setRegistryIp(registry.getNode())
                    .setRegistryPort(registry.getConfig().getWebPort())
                    .setRegistryUsername(registry.getConfig().getUser())
                    .setRegistryPassword(registry.getConfig().getPassword());
        }
        singleNodesExec(localHost, instance);
    }

    private void initHugePage(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitHugePage());
    }
}
