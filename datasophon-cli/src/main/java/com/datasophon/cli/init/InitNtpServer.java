package com.datasophon.cli.init;

import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import picocli.CommandLine;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.io.FileUtil;

@Slf4j
@CommandLine.Command(name = "ntpserver", description = "init ntpserver")
public class InitNtpServer implements Runnable {
    
    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件", required = true)
    String configFilePath;
    
    public void run() {
        String cmd = "rpm -qa | grep chrony-";
        System.out.println(cmd);
        ExecResult reResult = ShellUtils.execWithStatus("/", Arrays.asList(cmd.split("\\s+")), 60);
        if (reResult.getExecResult()) {
            log.info("ntp-chrony exists");
        } else {
            cmd = "yum -y install chrony";
            System.out.println(cmd);
            ShellUtils.execWithStatus("/", Arrays.asList(cmd.split("\\s+")), 60);
        }
        
        cmd = "mv /etc/chrony.conf /etc/chrony.conf.$(date +%Y%m%d.%H%M%S)";
        System.out.println(cmd);
        ShellUtils.execWithStatus("/", Arrays.asList(cmd.split("\\s+")), 60);
        
        String chronyConfPath = "/etc/chrony.conf";
        List<String> conf = new ArrayList<>();
        conf.add("server 127.0.0.1 iburst");
        conf.add("driftfile /var/lib/chrony/drift");
        conf.add("makestep 1.0 3");
        conf.add("rtcsync");
        conf.add("allow all");
        conf.add("local stratum 10");
        conf.add("keyfile /etc/chrony.keys");
        conf.add("leapsectz right/UTC");
        conf.add("logdir /var/log/chrony");
        FileUtil.writeLines(conf, chronyConfPath, Charset.defaultCharset());
        log.info("/etc/chrony.conf overwrite sucess.");
        
        cmd = "systemctl enable chronyd";
        System.out.println(cmd);
        ShellUtils.execWithStatus("/", Arrays.asList(cmd.split("\\s+")), 60);
        
        cmd = "systemctl restart chronyd";
        System.out.println(cmd);
        ShellUtils.execWithStatus("/", Arrays.asList(cmd.split("\\s+")), 60);
        
        log.info("init ntpserver sucess.");
    }
}
