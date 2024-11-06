package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "bash", description = "shell bash")
public class InitBash extends InitBase implements InitNodeHandler {
    
    @Override
    public String name() {
        return "bash解析器设置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        ExecResult exec = executor.execShell("ls -l /bin/sh");
        if (exec.getExecResult()) {
            String shStr = exec.getExecOut();
            String typeStr = shStr.split("->")[1].trim();
            if (!typeStr.equals("bash")) {
                ExecResult setexec = executor.execShell("sudo ln -svf bash /bin/sh");
                if (!setexec.getExecResult()) {
                    log.info("init shell bash fail.");
                    System.exit(1);
                }
            }
        }
        ExecResult userExe = executor.execShell("whoami");
        if(userExe.getExecResult()){
            if(userExe.getExecOut().equals("root")){
                executor.execShell("sed -i 's|root:x:0:0:root:/root:/bin/sh|root:x:0:0:root:/root:/bin/bash|g' /etc/passwd");
            }
        }
        ExecResult bashCheck = executor.execShell("echo $SHELL");
        if(!bashCheck.getExecOut().equals("/bin/bash")){
            log.info("当前用户[{}]shell解析器不是bash[/etc/passwd]", userExe.getExecOut());
            System.exit(1);
        }
        log.info("init shell bash finished.");
        return true;
    }
}
