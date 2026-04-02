package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "k8sTools", description = "init k8sTools ")
public class InitK8sTools extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-kc", "--kubernetesCluster"}, description = "是否安装kubernetes集群")
    boolean kubernetesCluster = true;

    @CommandLine.Option(names = {"-ns", "--namespaces"}, description = "命名空间", required = false)
    List<String> namespaces;

    @CommandLine.Option(names = {"-kt", "--kt"}, description = "kubernetes包", required = true)
    String Packages;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;
    
    @Override
    public String name() {
        return "安装k8sTools";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        //工具复制
        return true;
    }

}
