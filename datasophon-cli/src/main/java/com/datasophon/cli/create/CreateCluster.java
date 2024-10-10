package com.datasophon.cli.create;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.handler.InitNodeHandlerChain;
import com.datasophon.cli.init.*;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.model.Host;
import com.datasophon.common.utils.ShellUtils;

import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.yaml.snakeyaml.Yaml;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;

@Slf4j
@CommandLine.Command(name = "cluster", description = "create cluster")
public class CreateCluster implements Runnable {
    
    @CommandLine.Option(names = {"-p", "datasophonPath"}, description = "datasophon绝对路径", required = true)
    String datasophonPath;
    
    @CommandLine.Option(names = {"-a", "action"}, description = "执行动作:initALL/initSingleNode", required = true)
    String action;
    
    private String initPath;
    
    private String initBinPath;
    
    private String initConfigPath;
    
    private String initConfigTemplatePath;
    
    private String initSbinPath;
    
    private String packagesPath;
    
    private String initConfigYamlPath;
    
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
        initPath = String.format("%s/datasophon-init", datasophonPath);
        initBinPath = String.format("%s/bin", initPath);
        initConfigPath = String.format("%s/config", initPath);
        initConfigTemplatePath = String.format("%s/config/templates", initPath);
        initSbinPath = String.format("%s/sbin", initPath);
        packagesPath = String.format("%s/packages", initPath);
        initConfigYamlPath = String.format("%s/cluster-sample.yml", initConfigPath);
        log.info("\nDATASOPHON_PATH:{},\nINIT_PATH:{},\nINIT_CONFIG_YAML_PATH:{}", datasophonPath, initPath, initConfigYamlPath);
        
        File configFile = new File(initConfigYamlPath);
        if (!configFile.exists() || configFile.isDirectory()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + initConfigYamlPath);
        }
        Yaml yaml = new Yaml();
        String content = FileUtil.readString(configFile, Charset.defaultCharset());
        ClusterConfig clusterConfig = yaml.loadAs(content, ClusterConfig.class);
        GlobalConfig global = clusterConfig.getGlobal();
        if (ObjectUtil.isNull(global.getOs())) {
            // todo 获取当前操作系统
            global.setOs(OsType.CentOS7);
        }
        if (ObjectUtil.isNull(global.getArch())) {
            String cpuArchitecture = ShellUtils.getCpuArchitecture();
            global.setArch(ArchType.of(cpuArchitecture));
        }
        
        if (action.equals("initALL")) {
            initALL(clusterConfig);
        } else if (action.equals("initSingleNode")) {
            initSingleNode(clusterConfig);
        } else {
            throw new CommandLine.ExecutionException(new CommandLine(this), "action not found : " + action);
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
        log.info("安全配置");
        initOsSafeConf(config, nodes);
        
        log.info("分发资源包");
        initBinPackage(config, nodes);
        
        log.info("创建hadoop用户和组");
        initOsUser(config, nodes);
        
        log.info("关闭防火墙");
        initFirewall(config, nodes);
        
        log.info("关闭selinux");
        initSelinux(config, nodes);
        
        log.info("关闭Swap");
        initSwap(config, nodes);
        
        log.info("httpd安装");
        initHttpd(config);
        
        log.info("yum安装包解压");
        initYumPackage(config);
        
        log.info("离线yum仓库配置");
        initYumConf(config, nodes);
        
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
        
        log.info("初始化依赖库");
        initLibrary(config, nodes);
        
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
        log.info("安全配置");
        initOsSafeConf(config, nodes);
        
        log.info("分发资源包");
        initBinPackage(config, nodes);
        
        log.info("创建hadoop用户和组");
        initOsUser(config, nodes);
        
        log.info("关闭防火墙");
        initFirewall(config, nodes);
        
        log.info("关闭selinux");
        initSelinux(config, nodes);
        
        log.info("关闭Swap");
        initSwap(config, nodes);
        
        log.info("离线yum仓库配置");
        initYumConf(config, nodes);
        
        log.info("优化系统配置");
        initSystemConf(config, nodes);
        
        log.info("配置all hostname");
        initHostname(config, nodes);
        
        log.info("配置all hosts");
        initAllHost(config, initNodes);
        initAllHost(config, nodes);
        
        log.info("配置ntpSlave");
        initNtpSlave(config, nodes);
        
        log.info("初始化依赖库");
        initLibrary(config, nodes);
        
        log.info("关闭透明大页");
        initHugePage(config, nodes);
    }
    
    public void testFun() {
        
    }
    
    /**
     * 多节点执行
     */
    private void allNodesExec(List<Host> allNodes, InitNodeHandler initNodeHandler) {
        allNodes.forEach(host -> {
            InitNodeHandlerChain nodeHandlerChain = new InitNodeHandlerChain(host, initNodeHandler);
            nodeHandlerChain.handle();
        });
    }
    
    /**
     * 单节点执行
     */
    private void singleNodesExec(Host node, InitNodeHandler initNodeHandler) {
        InitNodeHandlerChain nodeHandlerChain = new InitNodeHandlerChain(node, initNodeHandler);
        nodeHandlerChain.handle();
    }
    
    /**
     * 1个server、N个slave节点执行
     */
    private void slavesNodesExec(Host serverNode, List<Host> allNodes, InitNodeHandler slaveHandler) {
        List<Host> slaveNodes = allNodes.stream().filter(host -> !host.getIp().equals(serverNode.getIp()))
                .collect(Collectors.toList());
        
        slaveNodes.forEach(node -> {
            InitNodeHandlerChain workerNodeHandlerChain = new InitNodeHandlerChain(node, slaveHandler);
            workerNodeHandlerChain.handle();
        });
    }
    
    private void initOsSafeConf(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitOsSafeConf());
    }
    
    private void initBinPackage(ClusterConfig config, List<Host> nodes) {
        InitBinPackage initBinPackage = new InitBinPackage();
        initBinPackage.setInitPath(initPath)
                .setDatasophonPath(datasophonPath);
        nodes.forEach(node -> {
            singleNodesExec(node, initBinPackage);
        });
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
    
    private void initHttpd(ClusterConfig config) {
        InitHttpd initHttpd = new InitHttpd();
        GlobalConfig.HttpdServer httpdServer = config.getGlobal().getHttpdServer();
        
        initHttpd.setConfigFilePath(initConfigYamlPath);
        initHttpd.setPackagePath(packagesPath)
                .setRootPathName(httpdServer.getRootPathName())
                .setHttpdListenPort(httpdServer.getListenPort())
                .setTemplateDir(initConfigTemplatePath)
                .setHttpdConf("httpd.conf.ftl")
                .setForce(true);
        singleNodesExec(httpdServer.getHost(), initHttpd);
    }
    
    private void initYumPackage(ClusterConfig config) {
        GlobalConfig.HttpdServer httpdServer = config.getGlobal().getHttpdServer();
        InitYumPackage initYumPackage = new InitYumPackage();
        initYumPackage.setConfigFilePath(initConfigYamlPath);
        initYumPackage.setPackagePath(packagesPath)
                .setRootPathName(httpdServer.getRootPathName())
                .setReposTarFilePath(httpdServer.getReposTarName());
        singleNodesExec(httpdServer.getHost(), initYumPackage);
    }
    
    private void initYumConf(ClusterConfig config, List<Host> nodes) {
        GlobalConfig.HttpdServer httpdServer = config.getGlobal().getHttpdServer();
        InitYumConf initYumConf = new InitYumConf();
        initYumConf.setConfigFilePath(initConfigYamlPath);
        initYumConf.setHttpdServerIp(httpdServer.getHost().getIp())
                .setHttpdListenPort(httpdServer.getListenPort());
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
        slavesNodesExec(ntpServer.getHost(), nodes, initNtpSlave);
    }
    
    private void initLibrary(ClusterConfig config, List<Host> nodes) {
        allNodesExec(nodes, new InitLibrary());
    }
    
    private void initMysql(ClusterConfig config) {
        InitMysql initMysql = new InitMysql();
        GlobalConfig.MysqlConfig mysqlConfig = config.getGlobal().getMysql();
        initMysql.setConfigFilePath(initConfigYamlPath);
        initMysql.setEnable(mysqlConfig.getEnable())
                .setPassword(mysqlConfig.getPassword())
                .setPackagePath(packagesPath)
                .setMysqlTarName(mysqlConfig.getTarName());
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
}
