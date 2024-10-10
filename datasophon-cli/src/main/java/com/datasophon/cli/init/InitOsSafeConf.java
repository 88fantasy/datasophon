package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.ExecResult;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

/**
 * 初始化基线安全配置
 */
@Slf4j
@CommandLine.Command(name = "osSafeConf", description = "init osSafeConf")
public class InitOsSafeConf extends InitBase {
    
    private static final String DATE_TIME = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    
    private int res;
    
    private int neEcho;
    
    private int insertRow;
    
    private String keyword;
    
    private String confStr;
    
    private String confFullPath;
    
    private String backupSignal;
    
    private String failedSignal;
    
    private Executor executor;
    
    private String sourceFile;
    
    private String backupFile;
    
    @Override
    public String name() {
        return "初始化基线安全配置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        this.executor = executor;
        log.info("============================");
        log.info(">>>>> 基线安全配置开始 <<<<<");
        log.info("============================");
        setLoginTimeout();
        setHistoryRecordCount();
        setKeyFilePermission();
        disabledSuToRoot();
        setPasswdComplexity();
        setPasswordLifespan();
        setPasswdLockStrategy();
        setPasswdRepeatTimes();
        lockNoUseAccount();
        disableShellOfNoUseAccount();
        setHostsAccessControl();
        disableSourceRoute();
        disableIpv4RouteRedirects();
        setRsyslogRotateValue();
        selinuxSetPermissive();
        setSshdBanner();
        setSshdProtocolVersion();
        setSshdSkipDnsCheck();
        // denyRootToSsh();
        log.info("============================");
        log.info(">>>>> 基线安全配置完成 <<<<<");
        log.info("============================");
        return true;
    }
    
    /**
     * 设置限制自动退出时长。
     */
    private void setLoginTimeout() {
        log.info("===== 设置闲置超时自动退出 =====");
        res = -1;
        neEcho = -1;
        keyword = "export TMOUT=";
        confStr = "export TMOUT=300";
        confFullPath = "/etc/profile";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
    }
    
    /**
     * 设置控制台历史操作记录行数
     */
    private void setHistoryRecordCount() {
        log.info("===== 设置历史操作记录保留数量 =====");
        res = -1;
        neEcho = -1;
        keyword = "HISTSIZE=";
        confStr = "HISTSIZE=3";
        confFullPath = "/etc/profile";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
    }
    
    /**
     * 收敛关键文件权限
     */
    private void setKeyFilePermission() {
        log.info("===== 收敛关键文件权限 =====");
        String[] commands = new String[]{"chmod 644 /etc/passwd", "chmod 400 /etc/shadow", "chmod 644 /etc/group",
                "chmod 644 /etc/services", "chmod 600 /etc/xinetd.conf", "chmod 600 /etc/security"};
        for (String command : commands) {
            log.info(command);
            String filePath = command.split(" ")[2];
            if (new File(filePath).exists()) {
                ExecResult exec = executor.execShell(command + "&>/dev/null");
                if (exec.getExecResult()) {
                    log.info("successful, pass!");
                } else {
                    log.error("failed, skip, please check!");
                }
            } else {
                log.error("file or dir not found, skip!");
            }
        }
        
    }
    
    /**
     * 禁止wheel组外的用户使用su命令切换至root
     */
    private void disabledSuToRoot() {
        log.info("===== 禁止除PAM组用户外的任何人su为root用户 =====");
        keyword = "auth            required        pam_wheel.so";
        confStr = "auth            required        pam_wheel.so use_uid";
        confFullPath = "/etc/pam.d/su";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
    }
    
    /**
     * 设置密码复杂度
     */
    private void setPasswdComplexity() {
        log.info("===== 设置账号密码复杂度要求 =====");
        keyword = "password    requisite     pam_cracklib.so";
        confStr = "password    requisite     pam_cracklib.so retry=3 minlen=8 dcredit=-1 ucredit=-1 lcredit=-1 ocredit=-1";
        confFullPath = "/etc/pam.d/system-auth";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
    }
    
    /**
     * 设置账号密码有效期限
     */
    private void setPasswordLifespan() {
        log.info("===== 设置账号密码有效期限 =====");
        keyword = "PASS_MAX_DAYS";
        confStr = "PASS_MAX_DAYS  90";
        confFullPath = "/etc/login.defs";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        
        keyword = "PASS_MIN_DAYS";
        confStr = "PASS_MIN_DAYS  10";
        confFullPath = "/etc/login.defs";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        
        keyword = "PASS_WARN_AGE";
        confStr = "PASS_WARN_AGE  7";
        confFullPath = "/etc/login.defs";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
    }
    
    /**
     * 设置账号密码错误锁定策略
     */
    private void setPasswdLockStrategy() {
        log.info("===== 设置账号密码错误锁定策略 =====");
        keyword = "auth        required      pam_tally2.so";
        confStr = "auth        required      pam_tally2.so deny=5 onerr=fail no_magic_root unlock_time=180";
        confFullPath = "/etc/pam.d/system-auth";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        
        keyword = "account        required      pam_tally2.so";
        confStr = "account        required      pam_tally2.so";
        confFullPath = "/etc/pam.d/system-auth";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
    }
    
    /**
     * 设置相同密码最大重复使用次数
     */
    private void setPasswdRepeatTimes() {
        log.info("===== 设置口令复用次数限制 =====");
        keyword = "password    sufficient    pam_unix.so sha512 shadow nullok try_first_pass use_authtok";
        confStr = "password    sufficient    pam_unix.so sha512 shadow nullok try_first_pass use_authtok remember=5";
        confFullPath = "/etc/pam.d/system-auth";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
    }
    
    /**
     * 禁用系统预置的不常用用户
     */
    private void lockNoUseAccount() {
        log.info("===== 通过修改/etc/shadow锁定限制账号 =====");
        confFullPath = "/etc/shadow";
        String[] users = new String[]{"lp", "sync", "halt", "news", "uucp", "operator", "games", "gopher", "smmsp", "nfsnobody", "nobody", "adm", "shutdown"};
        userControl();
        
        for (String user : users) {
            log.info("**set user {} password col to *:", user);
            ExecResult exec = executor.execShell(String.format("sed -i 's/^%s:!!/%s:*/g' %s", user, user, confFullPath));
            if (exec.getExecResult()) {
                log.info("successful, pass!");
            } else {
                log.error("failed, skip, please check!");
            }
        }
    }
    
    /**
     * 禁止系统预置的不常用用户登录
     */
    private void disableShellOfNoUseAccount() {
        log.info("===== 禁用系统预置的闲置账号调用shell环境 =====");
        String[] users = new String[]{"lp", "sync", "halt", "news", "uucp", "operator", "games", "gopher", "smmsp", "nfsnobody", "nobody", "adm", "shutdown"};
        for (String user : users) {
            log.info("**set user {} shell disabled:", user);
            ExecResult exec = executor.execShell(String.format("grep -E %s /etc/passwd >& /dev/null", user));
            if (exec.getExecResult()) {
                exec = executor.execShell(String.format("usermod -s /bin/false %s &>/dev/null", user));
                if (exec.getExecResult()) {
                    log.info("successful, pass!");
                } else {
                    log.error("failed, skip, please check!");
                }
            } else {
                log.info("user not found, skip!");
            }
        }
    }
    
    /**
     * 设置hosts.allow及hosts.deny策略
     */
    private void setHostsAccessControl() {
        log.info("===== 设置hosts.allow及hosts.deny策略 =====");
        keyword = "telnet:all:allow";
        confStr = "telnet:all:allow";
        confFullPath = "/etc/hosts.allow";
        backupSignal = "0";
        failedSignal = "skip";
        editConf();
        
        keyword = "telnet:all";
        confStr = "telnet:all";
        confFullPath = "/etc/hosts.deny";
        backupSignal = "0";
        failedSignal = "skip";
        editConf();
    }
    
    /**
     * 禁止源路由功能
     */
    private void disableSourceRoute() {
        log.info("===== 禁止IP源路由 =====");
        keyword = "net.ipv4.conf.all.accept_source_route";
        confStr = "net.ipv4.conf.all.accept_source_route=0";
        reloadSysctl();
    }
    
    /**
     * 禁止ip转发功能
     */
    private void disableIpv4RouteRedirects() {
        log.info("===== 禁止IP路由转发 =====");
        keyword = "net.ipv4.conf.all.accept_redirects";
        confStr = "net.ipv4.conf.all.accept_redirects=0";
        reloadSysctl();
    }
    
    /**
     * 重新加载sysctl.conf
     */
    private void reloadSysctl() {
        confFullPath = "/etc/sysctl.conf";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        
        log.info("**reload sysctl.conf now:");
        ExecResult exec = executor.execShell("sysctl -p > /dev/null 2>&1");
        if (exec.getExecResult()) {
            log.info("successful, pass!");
        } else {
            log.error("reload failed, abort, please check!");
            System.exit(1);
        }
    }
    
    /**
     * 设置系统审计日志留存周期
     */
    private void setRsyslogRotateValue() {
        log.info("===== 设置审计日志留存50周 =====");
        keyword = "rotate ";
        confStr = "rotate 50";
        confFullPath = "/etc/logrotate.conf";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        
        log.info("**restart rsyslog now:");
        ExecResult exec = executor.execShell("systemctl restart rsyslog > /dev/null 2>&1");
        if (exec.getExecResult()) {
            log.info("successful, pass!");
        } else {
            log.error("restart failed, abort, please check!");
            System.exit(1);
        }
    }
    
    /**
     * 设置selinux模式为permissive
     */
    private void selinuxSetPermissive() {
        log.info("===== 设置selinux模式为permissive =====");
        ExecResult exec = executor.execShell("sestatus &>/dev/null");
        if (exec.getExecResult()) {
            exec = executor.execShell("sestatus | grep 'SELinux status:' | awk -F ':' '{print $2}' | awk '{print $1}'");
            String currentStatus = exec.getExecOut();
            if ("enabled".equals(currentStatus)) {
                log.info("**status of current is enabled, check mode now...");
                exec = executor.execShell("sestatus | grep 'Mode from config file:' | awk -F ':' '{print $2}' | awk '{print $1}'");
                String configureMode = exec.getExecOut();
                switch (configureMode) {
                    case "permissive":
                        log.info("**mode from config file is permissive:");
                        log.info("pass!");
                        break;
                    case "enforcing":
                        log.info("**mode from config file is enforcing, change to permissive now:");
                        exec = executor.execShell("sed -i 's/SELINUX=enforcing/SELINUX=permissive/g' /etc/selinux/config");
                        if (exec.getExecResult()) {
                            log.info("success!");
                        } else {
                            log.error("failed, please check!");
                            System.exit(1);
                        }
                        break;
                    default:
                        log.error("**error: mode from config file is {}, may be error, please check manually!", configureMode);
                        System.exit(1);
                }
                
                exec = executor.execShell("sestatus | grep 'Current mode:' | awk -F ':' '{print $2}' | awk '{print $1}'");
                String currentMode = exec.getExecOut();
                switch (currentMode) {
                    case "permissive":
                        log.info("**mode from config file is permissive:");
                        log.info("pass!");
                        break;
                    case "enforcing":
                        log.info("**mode from config file is enforcing, change to permissive now:");
                        executor.execShell("setenforce 0");
                        exec = executor.execShell("sestatus | grep 'Current mode:' | awk -F ':' '{print $2}' | awk '{print $1}'");
                        currentMode = exec.getExecOut();
                        if ("permissive".equals(currentMode)) {
                            log.info("success!");
                        } else {
                            log.error("failed, please check!");
                            System.exit(1);
                        }
                        break;
                    default:
                        log.error("**error: mode from config file is {}, may be error, please check manually!", configureMode);
                        System.exit(1);
                }
            } else if ("disabled".equals(currentStatus)) {
                log.info("**status of current is disabled, change config file now...");
                log.info("**mode from config file is disabled, change to permissive now: ");
                // 处理实际状态是disabled,但是配置文件是enforcing的情况
                executor.execShell("sed -i 's/SELINUX=enforcing/SELINUX=permissive/g' /etc/selinux/config");
                exec = executor.execShell("sed -i 's/SELINUX=disabled/SELINUX=permissive/g' /etc/selinux/config");
                if (exec.getExecResult()) {
                    log.info("success!");
                    log.info("**please reboot the system to change status!");
                } else {
                    log.error("failed, please check!");
                    System.exit(1);
                }
                
            } else {
                log.error("*status of current is {}, maybe error, please check manually!", currentStatus);
            }
        } else {
            log.info("**selinux maybe not install, please check!");
            System.exit(1);
        }
    }
    
    /**
     * 设定ssh服务的banner信息
     */
    private void setSshdBanner() {
        log.info("===== set sshd banner and motd msg =====");
        userControl();
        confFullPath = "/etc/motd";
        String content = "Warning!!! If you are not the operations staff, logout the system right now!";
        writeContent2File(content);
        
        confFullPath = "/etc/ssh/mybanner";
        content = "Authorized users only!!! All activity may be monitored and reported!";
        writeContent2File(content);
        
        keyword = "Banner ";
        confStr = "Banner /etc/ssh/mybanner";
        confFullPath = "/etc/ssh/sshd_config";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        
        restartSshd();
    }
    
    /**
     * 写入内容到文件
     * @param content
     */
    private void writeContent2File(String content) {
        String skip = null;
        String fileContent;
        log.info("**create {}: ", confFullPath);
        if (new File(confFullPath).isFile()) {
            ExecResult exec = executor.execShell(String.format("cat %s", confFullPath));
            fileContent = exec.getExecOut();
            if (StringUtils.isNotBlank(fileContent)) {
                backupConf();
            } else {
                exec = executor.execShell(String.format("touch %s", confFullPath));
                if (!exec.getExecResult()) {
                    log.error("!!!create {} failed, skip, please check  or create manually!", confFullPath);
                    skip = "0";
                }
            }
            
            if (StringUtils.isEmpty(skip)) {
                exec = executor.execShell(String.format("echo '%s' > %s", content, confFullPath));
                if (exec.getExecResult()) {
                    res = 0;
                } else {
                    res = 1;
                }
            }
        }
    }
    
    /**
     * 重启sshd服务
     */
    private void restartSshd() {
        log.info("**restart sshd now:");
        ExecResult exec = executor.execShell("systemctl restart sshd > /dev/null 2>&1");
        if (exec.getExecResult()) {
            log.info("successful, pass!");
        } else {
            log.error("restart failed, abort, please check!");
            System.exit(1);
        }
    }
    
    /**
     * 设置ssh登录协议版本
     */
    private void setSshdProtocolVersion() {
        log.info("===== 设置ssh登录协议版本 =====");
        keyword = "Protocol ";
        confStr = "Protocol 2";
        confFullPath = "/etc/ssh/sshd_config";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        restartSshd();
    }
    
    /**
     * 停用ssh连接的dns检查
     */
    private void setSshdSkipDnsCheck() {
        log.info("===== 设置ssh连接不进行dns检查  =====");
        keyword = "UseDNS ";
        confStr = "UseDNS no";
        confFullPath = "/etc/ssh/sshd_config";
        backupSignal = "0";
        failedSignal = "abort";
        editConf();
        restartSshd();
    }
    
    /**
     * 禁止root用户远程登录
     */
    private void denyRootToSsh() {
        log.info("===== 禁止root用户使用ssh远程登录 =====");
        log.info("*create admin user now: ");
        
        String user = "uniadmin";
        String group = "uniadmin";
        
        log.info("**create group uniadmin to system:");
        ExecResult exec = executor.execShell(String.format("grep -E %s /etc/group >& /dev/null", group));
        if (exec.getExecResult()) {
            log.info("group uniadmin already exist, skip!");
        } else {
            exec = executor.execShell(String.format("groupadd %s", group));
            if (exec.getExecResult()) {
                log.info("success!");
            } else {
                log.error("failed, abort, please check!");
                System.exit(1);
            }
        }
        
        log.info("**add user uniadmin to sudoer list:");
        if (new File("/etc/sudoers").isFile()) {
            exec = executor.execShell(String.format("cat /etc/sudoers | grep '^%s ALL=(ALL) NOPASSWD:ALL' > /dev/null 2>&1", user));
            if (exec.getExecResult()) {
                log.info("user uniadmin already exist, skip!");
            } else {
                exec = executor.execShell(String.format("echo '%s ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers", user));
                if (exec.getExecResult()) {
                    log.info("success!");
                } else {
                    log.error("failed, abort, please check!");
                    System.exit(1);
                }
            }
        } else {
            log.error("!!!file /etc/sudoers not exist, abort, please check!");
            System.exit(1);
        }
    }
    
    /**
     * 编辑配置文件
     */
    private void editConf() {
        res = -1;
        log.info("**set {} to {}:", confStr, confFullPath);
        if (StringUtils.isEmpty(keyword) || StringUtils.isEmpty(confStr) || StringUtils.isEmpty(confFullPath)) {
            log.error("!!!keyword conf_str conf_full_path backup_signal can not be null, abort, please check!");
            System.exit(1);
        } else if (!new File(confFullPath).isFile()) {
            log.error("!!!conf file {} not found, abort, please check!", confFullPath);
            System.exit(1);
        } else if (StringUtils.isNotBlank(backupSignal)) {
            backupConf();
        }
        
        delExistConf();
        
        if (res != 1) {
            if (insertRow == 0) {
                ExecResult exec = executor.execShell(String.format("grep -E -n \"^#%s\" %s | awk -F \":\" '{print $1}' | xargs echo | awk '{print $NF}'", keyword, confFullPath));
                String row = exec.getExecOut();
                if (exec.getExecResult() && StringUtils.isEmpty(row)) {
                    exec = executor.execShell(String.format("sed -i \"$ a %s \" %s &>/dev/null", confStr, confFullPath));
                    if (!exec.getExecResult()) {
                        res = 1;
                    } else {
                        res = 0;
                    }
                } else {
                    exec = executor.execShell(String.format("sed -i \"%s a %s \" %s &>/dev/null", row, confStr, confFullPath));
                    if (!exec.getExecResult()) {
                        res = 1;
                    } else {
                        res = 0;
                    }
                }
            } else {
                ExecResult exec = executor.execShell(String.format("sed -i \"%s a %s \" %s &>/dev/null", insertRow, confStr, confFullPath));
                if (!exec.getExecResult()) {
                    res = 1;
                } else {
                    res = 0;
                }
            }
        }
        
        resJudge();
    }
    
    /**
     * 备份配置文件
     */
    private void backupConf() {
        if (StringUtils.isEmpty(confFullPath)) {
            log.error("**backup source file: failed, because conf file path not assign, abort!");
            System.exit(-1);
        } else {
            String confFileName = confFullPath.substring(confFullPath.lastIndexOf("/") + 1);
            String confFilePath = confFullPath.substring(0, confFullPath.length() - confFileName.length());
            String confBackupPath = confFilePath + "." + confFileName + ".bak";
            
            sourceFile = confFilePath + confFileName;
            backupFile = confBackupPath + "/" + confFileName + ".bak-" + DATE_TIME;
            
            if (!new File(confBackupPath).isDirectory()) {
                boolean result = new File(confBackupPath).mkdirs();
                if (!result) {
                    log.error("create dir {} failed, abort, please check!", confBackupPath);
                    System.exit(1);
                }
            }
            
            ExecResult exec = executor.execShell(String.format("cp %s %s &>/dev/null", sourceFile, backupFile));
            if (!exec.getExecResult()) {
                log.error("backup {}: failed, please check!", backupFile);
                System.exit(1);
            }
            log.info("backup {}: success!", backupFile);
        }
    }
    
    /**
     * 删除已存在的配置
     */
    private void delExistConf() {
        insertRow = 0;
        if (StringUtils.isNotBlank(keyword)) {
            log.info("**delete exist conf: ");
            while (true) {
                ExecResult exec = executor.execShell(String.format("grep -E -n \"^%s\" %s | awk -F \":\" '{print $1}' | xargs echo | awk '{print $1}'", keyword, confFullPath));
                String row = exec.getExecOut();
                if (exec.getExecResult() && StringUtils.isNotBlank(row)) {
                    executor.execShell(String.format("sed -i \"%sd\" %s &>/dev/null", row, confFullPath));
                    insertRow = Integer.parseInt(row) - 1;
                } else {
                    break;
                }
            }
            
            ExecResult exec = executor.execShell(String.format("grep -E -i \"^%s\" %s | wc -l", keyword, confFullPath));
            String count = exec.getExecOut();
            if (exec.getExecResult() && Integer.parseInt(count) > 0) {
                res = 1;
                neEcho = 0;
                resJudge();
            }
        } else {
            res = 1;
            neEcho = 0;
            resJudge();
        }
    }
    
    /**
     * 判断结果
     */
    private void resJudge() {
        if (res == 0) {
            if (neEcho > -1) {
                log.info("successful, pass!");
                neEcho = -1;
            }
        } else {
            if ("skip".equals(failedSignal)) {
                log.error("failed, skip, please check!");
                restoreConf();
            } else {
                log.error("failed, abort, please check!");
                restoreConf();
                System.exit(1);
            }
        }
    }
    
    private void restoreConf() {
        log.info("**restore backup conf: ");
        boolean restore = false;
        if (StringUtils.isNotBlank(sourceFile) && StringUtils.isNotBlank(backupFile)) {
            if (new File(sourceFile).isFile()) {
                ExecResult exec = executor.execShell(String.format("mv %s %s &>/dev/null", sourceFile, backupFile));
                if (exec.getExecResult()) {
                    restore = true;
                }
            }
        }
        
        if (restore) {
            log.info("successful, pass!");
        } else {
            log.error("failed, skip, please check!");
        }
    }
    
    /**
     * 用户控制
     */
    private void userControl() {
        ExecResult exec = executor.execShell("whoami");
        String user = exec.getExecOut();
        if (!"root".equals(user)) {
            log.error("!!!permission denied, please change to user: root! or use sudo to execute the command!");
            System.exit(1);
        }
    }
    
}
