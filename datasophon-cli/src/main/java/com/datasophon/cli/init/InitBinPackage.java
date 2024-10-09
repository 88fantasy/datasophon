package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.File;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "bin_packages", description = "init bin packages")
public class InitBinPackage extends InitBase {

    @CommandLine.Option(names = {"-i", "--initPath"}, description = "initPath", required = true)
    private String initPath;

    @CommandLine.Option(names = {"-d", "--datasophonPath"}, description = "datasophonPath", required = true)
    private String datasophonPath;

    @Override
    public String name() {
        return "分发datasophon-init资源包";
    }
    
    public boolean doRun(Executor executor) {
        File initPathF = new File(initPath);
        if (!initPathF.exists() || !initPathF.isDirectory()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + initPath);
        }
        File datasophonPathF = new File(datasophonPath);
        if (!datasophonPathF.exists()) {
            executor.createDir(datasophonPath);
        }
        ExecResult execResult = executor.sendDir(initPath, datasophonPath);
        if (execResult.getExecResult()) {
            log.info("{} to {} distribution sucess.", initPath, datasophonPath);
            return true;
        } else {
            log.info("{} to {} distribution fail.", initPath, datasophonPath);
            return false;
        }
    }
}
