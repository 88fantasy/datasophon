package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;
import lombok.extern.slf4j.Slf4j;

/**
 * 初始化插件
 */
@Slf4j
@CommandLine.Command(name = "library", description = "init library")
public class InitLibrary extends InitBase {
    
    private Executor executor;
    @Override
    public String name() {
        return "初始化依赖库";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        this.executor = executor;
        
        installLibxsltDevel();
        installPsmisc();
        installPerlJson();
        installNmap();
        initJavaPolicy();
        initTmpPid();
        installXdgUtils();
        installGcc();
        installOpenssl();
        installLibtool();
        enableNtp();
        initChmodDev();
        initCleanBuff();
        sourceProfile();
        return true;
    }
    
    private void installLibxsltDevel() {
        log.info("install libxslt-devel");
        ExecResult libExec = executor.execShell("rpm -qa | grep libxslt-devel");
        if (!libExec.getExecResult()) {
            log.info("libxslt-devel not installed");
            executor.execShell("yum install libxslt-devel -y");
            libExec = executor.execShell("rpm -qa | grep libxslt-devel");
            if (!"0".equals(libExec.getExecOut())) {
                log.info("libxslt-devel install failed.");
                System.exit(1);
            }
        }
        log.info("install libxslt-devel finished");
    }
    
    private void installPsmisc() {
        log.info("install psmisc");
        ExecResult psmiscExec = executor.execShell("rpm -qa | grep psmisc");
        if (!psmiscExec.getExecResult()) {
            log.info("psmisc not installed");
            executor.execShell("yum install psmisc -y");
            psmiscExec = executor.execShell("rpm -qa | grep psmisc");
            if (!"0".equals(psmiscExec.getExecOut())) {
                log.info("psmisc install failed.");
                System.exit(1);
            }
        }
        log.info("install psmisc finished");
    }
    
    private void installPerlJson() {
        log.info("install perl-JSON");
        ExecResult perlExec = executor.execShell("rpm -qa | grep perl-JSON");
        if (!perlExec.getExecResult()) {
            log.info("perl-JSON not installed");
            executor.execShell("yum install perl-JSON -y");
            perlExec = executor.execShell("rpm -qa | grep perl-JSON");
            if (!"0".equals(perlExec.getExecOut())) {
                log.info("perl-JSON install failed.");
                System.exit(1);
            }
        }
        log.info("install perl-JSON finished.");
    }
    
    private void installNmap() {
        log.info("install nmap.");
        ExecResult nmapExec = executor.execShell("rpm -qa | grep nmap");
        if (!nmapExec.getExecResult()) {
            log.info("nmap not installed");
            executor.execShell("yum install nmap -y");
            nmapExec = executor.execShell("rpm -qa | grep nmap");
            if (!"0".equals(nmapExec.getExecOut())) {
                log.info("nmap install failed.");
                System.exit(1);
            }
        }
        log.info("install nmap finished.");
    }
    
    private void initJavaPolicy() {
        log.info("init java policy.");
        executor.execShell("source /etc/profile");
        executor.execShell("sed -i '/modify java policy start/,/modify java policy end/d' ${JAVA_HOME}/jre/lib/security/java.policy");
        executor.execShell("sed -i '/grant {/a\\//modify java policy end' ${JAVA_HOME}/jre/lib/security/java.policy");
        executor.execShell("sed -i '/modify java policy end/i\\//modify java policy start' ${JAVA_HOME}/jre/lib/security/java.policy");
        executor.execShell("sed -i '/modify java policy end/i\\permission javax.management.MBeanTrustPermission \"register\";' ${JAVA_HOME}/jre/lib/security/java.policy");
        log.info("init java policy finished.");
    }
    
    private void initTmpPid() {
        log.info("init tmp pid.");
        ExecResult exec = executor.execShell("egrep '/tmp/hsperfdata' /usr/lib/tmpfiles.d/tmp.conf >&/dev/null");
        if (!exec.getExecResult()) {
            executor.execShell("echo \"x /tmp/*.pid\" >> /usr/lib/tmpfiles.d/tmp.conf");
            executor.execShell("echo \"x /tmp/hsperfdata*/*\" >> /usr/lib/tmpfiles.d/tmp.conf");
            executor.execShell("echo \"X /tmp/hsperfdata*\" >> /usr/lib/tmpfiles.d/tmp.conf");
        }
        log.info("init tmp pid finished.");
    }
    
    private void installXdgUtils() {
        log.info("install xdg-utils.");
        ExecResult xdgExec = executor.execShell("rpm -qa | grep xdg-utils");
        if (xdgExec.getExecResult()) {
            log.info("xdg-utils already installed");
        } else {
            executor.execShell("yum install xdg-utils -y");
            xdgExec = executor.execShell("rpm -qa | grep xdg-utils");
            if (xdgExec.getExecResult()) {
                log.info("xdg-utils install successfully.");
            } else {
                log.error("xdg-utils install failed.");
                System.exit(1);
            }
        }
        log.info("install xdg-utils finished.");
    }
    
    private void installGcc() {
        log.info("install gcc-c++.");
        ExecResult gccExec = executor.execShell("rpm -qa | grep gcc-c++");
        if (gccExec.getExecResult()) {
            log.info("gcc-c++ already installed");
        } else {
            executor.execShell("yum install gcc-c++ -y");
            gccExec = executor.execShell("rpm -qa | grep gcc-c++");
            if (gccExec.getExecResult()) {
                log.info("gcc-c++ install successfully.");
            } else {
                log.error("gcc-c++ install failed.");
                System.exit(1);
            }
        }
        log.info("install gcc-c++ finished.");
    }
    
    private void installOpenssl() {
        log.info("install openssl-devel.");
        ExecResult opensslExec = executor.execShell("rpm -qa | grep openssl-devel");
        if (opensslExec.getExecResult()) {
            log.info("openssl-devel already installed");
        } else {
            executor.execShell("yum install openssl-devel -y");
            opensslExec = executor.execShell("rpm -qa | grep openssl-devel");
            if (opensslExec.getExecResult()) {
                log.info("openssl-devel install successfully.");
            } else {
                log.error("openssl-devel install failed.");
                System.exit(1);
            }
        }
        log.info("install openssl-devel finished.");
    }
    
    private void installLibtool() {
        log.info("install libtool.");
        ExecResult libtoolExec = executor.execShell("rpm -qa | grep libtool");
        if (libtoolExec.getExecResult()) {
            log.info("libtool already installed");
        } else {
            executor.execShell("yum install libtool -y");
            libtoolExec = executor.execShell("rpm -qa | grep libtool");
            if (libtoolExec.getExecResult()) {
                log.info("libtool install successfully.");
            } else {
                log.error("libtool install failed.");
                System.exit(1);
            }
        }
        log.info("install libtool finished.");
    }
    
    private void enableNtp() {
        log.info("enable ntp.");
        executor.execShell("systemctl enable ntpd.service");
        executor.execShell("systemctl restart ntpd.service");
        log.info("enable ntp finished.");
    }
    
    private void initChmodDev() {
        log.info("init chmod dev null.");
        executor.execShell("rm -rf /dev/null && mknod -m 0666 /dev/null c 1 3");
        log.info("init chmod dev null finished.");
    }
    
    private void initCleanBuff() {
        log.info("init clean buff.");
        executor.execShell("sync");
        executor.execShell("sync");
        executor.execShell("sync");
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
        executor.execShell("echo 1 >/proc/sys/vm/drop_caches");
        executor.execShell("echo 2 >/proc/sys/vm/drop_caches");
        executor.execShell("echo 3 >/proc/sys/vm/drop_caches");
        log.info("init clean buff finished.");
    }
    
    private void sourceProfile() {
        executor.execShell("source /etc/profile");
        executor.execShell("source /root/.bash_profile");
        executor.execShell("echo $(java -version)");
    }
    
}
