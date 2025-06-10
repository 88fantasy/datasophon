package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.enums.OsType;
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
        OsType osType = executor.getOs();
        if(OsType.isCentos(osType)) {
            installLibxsltDevel();
            installPsmisc();
            installPerlJson();
            initJavaPolicy();
            initTmpPid();
            installXdgUtils();
            installGcc();
            installOpenssl();
            installLibtool();
            initChmodDev();
            initCleanBuff();
            sourceProfile();
            telnet();
        } else if(OsType.isUnbuntu(osType)){
            installPsmisc();
            initJavaPolicy();
            initTmpPid();
            initChmodDev();
            initCleanBuff();
            sourceProfile();
            libpamCracklib();
            policycoreutils();
            telnet();
        }
        return true;
    }
    
    private void installLibxsltDevel() {
        log.info("install libxslt-devel");
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep libxslt-devel";
        String installCmd = "yum install libxslt-devel -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep libxslt-devel";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install libxslt-devel -y";
        }
        ExecResult libExec = executor.execShell(checkCmd);
        if (!libExec.getExecResult()) {
            log.info("libxslt-devel not installed");
            executor.execShell(installCmd);
            libExec = executor.execShell(checkCmd);
            if (!libExec.getExecResult()) {
                log.info("libxslt-devel install failed.");
                System.exit(1);
            }
        }
        log.info("install libxslt-devel finished");
    }
    
    private void installPsmisc() {
        log.info("install psmisc");
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep psmisc";
        String installCmd = "yum install psmisc -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep psmisc";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install psmisc -y";
        }
        ExecResult psmiscExec = executor.execShell(checkCmd);
        if (!psmiscExec.getExecResult()) {
            log.info("psmisc not installed");
            executor.execShell(installCmd);
            psmiscExec = executor.execShell(checkCmd);
            if (!psmiscExec.getExecResult()) {
                log.info("psmisc install failed.");
                System.exit(1);
            }
        }
        log.info("install psmisc finished");
    }
    
    private void installPerlJson() {
        log.info("install perl-JSON");
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep perl-JSON";
        String installCmd = "yum install perl-JSON -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep perl-JSON";
            installCmd = "apt install perl-JSON -y";
        }
        ExecResult perlExec = executor.execShell(checkCmd);
        if (!perlExec.getExecResult()) {
            log.info("perl-JSON not installed");
            executor.execShell(installCmd);
            perlExec = executor.execShell(checkCmd);
            if (!perlExec.getExecResult()) {
                log.info("perl-JSON install failed.");
                System.exit(1);
            }
        }
        log.info("install perl-JSON finished.");
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
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep xdg-utils";
        String installCmd = "yum install xdg-utils -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep xdg-utils";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install xdg-utils -y";
        }
        ExecResult xdgExec = executor.execShell(checkCmd);
        if (xdgExec.getExecResult()) {
            log.info("xdg-utils already installed");
        } else {
            executor.execShell(installCmd);
            xdgExec = executor.execShell(checkCmd);
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
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep gcc-c++";
        String installCmd = "yum install gcc-c++ -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep gcc-c++";
            installCmd = "apt install gcc-c++ -y";
        }
        ExecResult gccExec = executor.execShell(checkCmd);
        if (gccExec.getExecResult()) {
            log.info("gcc-c++ already installed");
        } else {
            executor.execShell(installCmd);
            gccExec = executor.execShell(checkCmd);
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
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep openssl-devel";
        String installCmd = "yum install openssl-devel -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep openssl-devel";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install openssl-devel -y";
        }
        ExecResult opensslExec = executor.execShell(checkCmd);
        if (opensslExec.getExecResult()) {
            log.info("openssl-devel already installed");
        } else {
            executor.execShell(installCmd);
            opensslExec = executor.execShell(checkCmd);
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
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep libtool";
        String installCmd = "yum install libtool -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep libtool";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install libtool -y";
        }
        ExecResult libtoolExec = executor.execShell(checkCmd);
        if (libtoolExec.getExecResult()) {
            log.info("libtool already installed");
        } else {
            executor.execShell(installCmd);
            libtoolExec = executor.execShell(checkCmd);
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

    private void telnet() {
        log.info("install telnet.");
        OsType osType = executor.getOs();
        String installCmd = "yum install telnet -y";
        if(OsType.isUnbuntu(osType)) {
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install telnet -y";
        }
        ExecResult result = executor.execShell(installCmd);
        if (result.getExecResult()) {
            log.info("telnet installe success");
        } else {
            log.info("telnet install failed.");
            System.exit(1);
        }
    }

    private void libpamCracklib() {
        log.info("install libpam-cracklib.");
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep libpam-cracklib";
        String installCmd = "yum install libpam-cracklib -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep libpam-cracklib";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install libpam-cracklib -y";
        }
        ExecResult result = executor.execShell(checkCmd);
        if (result.getExecResult()) {
            log.info("libpam-cracklib already installed");
        } else {
            executor.execShell(installCmd);
            result = executor.execShell(checkCmd);
            if (result.getExecResult()) {
                log.info("libpam-cracklib install successfully.");
            } else {
                log.error("libpam-cracklib install failed.");
                System.exit(1);
            }
        }
        log.info("install libpam-cracklib finished.");
    }

    private void policycoreutils() {
        log.info("install policycoreutils.");
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep policycoreutils";
        String installCmd = "yum install policycoreutils -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep policycoreutils";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install policycoreutils -y";
        }
        ExecResult result = executor.execShell(checkCmd);
        if (result.getExecResult()) {
            log.info("policycoreutils already installed");
        } else {
            executor.execShell(installCmd);
            result = executor.execShell(checkCmd);
            if (result.getExecResult()) {
                log.info("policycoreutils install successfully.");
            } else {
                log.error("policycoreutils install failed.");
                System.exit(1);
            }
        }
        log.info("install policycoreutils finished.");
    }
    
}
