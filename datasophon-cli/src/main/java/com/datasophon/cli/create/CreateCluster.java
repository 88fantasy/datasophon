package com.datasophon.cli.create;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.handler.InitNodeHandlerChain;
import com.datasophon.cli.init.*;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.model.Host;
import com.datasophon.common.utils.ShellUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@CommandLine.Command(name = "cluster", description = "create cluster")
public class CreateCluster implements Runnable {
    
    @CommandLine.Option(names = {"-p", "datasophonPath"}, description = "datasophon绝对路径", required = true)
    String datasophonPath;
    
    @CommandLine.Option(names = {"-a", "action"}, description = "执行动作", required = true)
    String action;
    
    private String initPath;
    
    private String initBinPath;
    
    private String initConfigPath;
    
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
        }
    }
    
    /**
     * 初始化所有节点
     */
    public void initALL(ClusterConfig config) {
        List<Host> allNodes = config.getNodes();
        if (CollUtil.isEmpty(allNodes)) {
            return;
        }
        log.info("安全配置");
        allNodesExec(allNodes, new InitOsSafeConf());
        
        log.info("创建hadoop用户和组");
        allNodesExec(allNodes, new InitOsUser());
        
        log.info("关闭防火墙");
        allNodesExec(allNodes, new InitFirewall());
        
        log.info("关闭selinux");
        allNodesExec(allNodes, new InitSelinux());
        
        log.info("关闭Swap");
        allNodesExec(allNodes, new InitSwap());
        
        log.info("httpd安装");
        InitHttpd initHttpd = new InitHttpd();
        GlobalConfig.HttpdServer httpdServer = config.getGlobal().getHttpdServer();
        initHttpd.setConfigFilePath(initConfigYamlPath);
        initHttpd.setHttpdPkgPath(httpdServer.getPkgPath())
                .setHttpdRootPath(httpdServer.getRootPath())
                .setHttpdListenPort(httpdServer.getListenPort())
                .setTemplateDir(httpdServer.getTemplateDir())
                .setHttpdConf("httpd.conf.ftl")
                .setForce(true);
        singleNodesExec(httpdServer.getHost(), initHttpd);
        
        log.info("yum安装包解压");
        InitYumPackage initYumPackage = new InitYumPackage();
        initYumPackage.setConfigFilePath(initConfigYamlPath);
        initYumPackage.setHttpdRootPath(httpdServer.getRootPath())
                .setReposTarFilePath(httpdServer.getReposTarFilePath());
        singleNodesExec(httpdServer.getHost(), initHttpd);
        
        log.info("离线yum仓库配置");
        InitYumConf initYumConf = new InitYumConf();
        initYumConf.setConfigFilePath(initConfigYamlPath);
        initYumConf.setHttpdServerIp(httpdServer.getHost().getIp())
                .setHttpdListenPort(httpdServer.getListenPort());
        allNodesExec(allNodes, initYumConf);
        
        log.info("优化系统配置");
        allNodesExec(allNodes, new InitSystemConf());
        
        log.info("配置all hostname");
        config.getNodes().forEach(node -> {
            InitHostname initHostname = new InitHostname();
            initHostname.setHostname(node.getHostname());
            singleNodesExec(node, initHostname);
        });
        
        log.info("nmap安装");
        singleNodesExec(config.getGlobal().getNmapServer(), new InitNmap());
        
        log.info("配置ntpServer");
        InitNtpServer initNtpServer = new InitNtpServer();
        GlobalConfig.NtpServer ntpServer = config.getGlobal().getNtpServer();
        initNtpServer.setConfigFilePath(initConfigYamlPath);
        singleNodesExec(ntpServer.getHost(), initNtpServer);
        
        log.info("配置ntpSlave");
        InitNtpSlave initNtpSlave = new InitNtpSlave();
        initNtpSlave.setConfigFilePath(initConfigYamlPath);
        slavesNodesExec(ntpServer.getHost(), allNodes, initNtpSlave);
        
        log.info("初始化依赖库");
        allNodesExec(allNodes, new InitLibrary());
        
        log.info("安装mysql");
        InitMysql initMysql = new InitMysql();
        GlobalConfig.MysqlConfig mysqlConfig = config.getGlobal().getMysql();
        initMysql.setConfigFilePath(initConfigYamlPath);
        initMysql.setEnable(mysqlConfig.getEnable())
                .setPassword(mysqlConfig.getPassword())
                .setPackagePath(packagesPath)
                .setMysqlTarName(mysqlConfig.getTarName());
        singleNodesExec(mysqlConfig.getHost(), initMysql);
        
        log.info("初始化mysql数据库和账号密码");
        mysqlConfig.getAppDbs().forEach(x -> {
            InitMysqlAppDb initMysqlAppDb = new InitMysqlAppDb();
            initMysqlAppDb.setConfigFilePath(initConfigYamlPath);
            initMysqlAppDb.setRootPassword(mysqlConfig.getPassword())
                    .setAccount(x.getAccount())
                    .setPassword(x.getPassword())
                    .setDbName(x.getDbName());
            singleNodesExec(mysqlConfig.getHost(), initMysqlAppDb);
        });
        
        log.info("关闭透明大页");
        allNodesExec(allNodes, new InitHugePage());
    }
    
    /**
     * 初始化单节点
     */
    public void initSingleNode() {
        
    }
    
    public void secretFreeAllLogin() {
        
    }
    
    public void checkcloseAllSwap() {
        
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
    
}
