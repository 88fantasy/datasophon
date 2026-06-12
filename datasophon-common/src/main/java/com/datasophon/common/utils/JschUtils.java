package com.datasophon.common.utils;

import com.datasophon.common.enums.SSHAuthType;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;

import lombok.extern.slf4j.Slf4j;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import cn.hutool.core.io.IoUtil;

@Slf4j
public class JschUtils {
    
    public static Session getJSchSession(SSHAuthType sshAuthType, String ip, int port, String userName, String password) throws JSchException {
        JSch jSch = new JSch();
        Session session;
        try {
            if (StringUtils.isEmpty(userName)) {
                userName = "root";
            }
            // 创建连接
            log.info("正在连接服务器{}@{}", userName, ip);
            session = jSch.getSession(userName, ip, port);
            String publicKey = String.format("/%s/.ssh/id_rsa", userName);
            if (!"root".equals(userName)) {
                publicKey = String.format("/home/%s/.ssh/id_rsa", userName);
            }
            if (sshAuthType == SSHAuthType.PASSWORD) {
                session.setPassword(password);
            } else if (sshAuthType == SSHAuthType.PUBLICKEY) {
                jSch.addIdentity(publicKey);
            } else if (sshAuthType == SSHAuthType.AUTO) {
                if (new File(publicKey).exists()) {
                    jSch.addIdentity(publicKey);
                } else {
                    session.setPassword(password);
                }
            } else {
                jSch.addIdentity(publicKey);
            }
            // 是否使用密钥登录，一般默认为no
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey,gssapi-with-mic,keyboard-interactive,password");
            // 启用连接
            session.connect(10000);
        } catch (JSchException e) {
            log.error(String.format("服务器%s@%s连接失败:%s", userName, ip, e.getMessage()), e);
            throw e;
        }
        return session;
    }
    
    public static void closeJSchSession(Session session) {
        if (session != null) {
            try {
                session.disconnect();
                log.info("服务器{}@{}连接关闭成功", session.getUserName(), session.getHost());
            } catch (Exception e) {
                log.error(String.format("服务器%s@%s连接关闭失败", session.getUserName(), session.getHost()), e.getMessage());
            }
        }
    }
    
    /** 远端命令执行超时上限（10 分钟）：覆盖安装/解压等长任务，同时防止失控命令永久占用线程。 */
    private static final long EXEC_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    
    public static ExecResult execForStr(Session session, String command) {
        InputStream in = null;
        Channel channel = null;
        ExecResult result = new ExecResult();
        try {
            // 创建执行通道
            channel = session.openChannel("exec");
            // 设置命令
            ((ChannelExec) channel).setCommand(command);
            // 连接通道
            channel.connect();
            // 读取通道的输出
            in = channel.getInputStream();
            String execOut = IoUtil.read(in, Charset.defaultCharset());
            // 去除尾部的换行符
            if (!execOut.isEmpty() && execOut.charAt(execOut.length() - 1) == '\n') {
                execOut = execOut.substring(0, execOut.length() - 1);
            }
            // 这里阻塞等待执行完成（带超时上限：远端命令不结束时避免线程永久阻塞）
            long deadline = System.currentTimeMillis() + EXEC_TIMEOUT_MILLIS;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new RuntimeException(
                            String.format("command [%s] timed out after %d ms", command, EXEC_TIMEOUT_MILLIS));
                }
                Thread.sleep(500);
            }
            int exitValue = channel.getExitStatus();
            if (0 == exitValue) {
                log.info("{} command exec out is :{}, exitValue:{}", command, execOut, exitValue);
                result.setExecResult(true);
                result.setExecOut(execOut);
            } else {
                result.setExecOut(execOut);
                log.warn("{} command exec out is :{}, exitValue:{}", command, execOut, exitValue);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IoUtil.close(in);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return result;
    }
    public static String getFileString(Session session, String path, int connectTimeout) {
        ChannelSftp channel = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // 创建执行通道
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout * 1000);
            channel.get(path, baos);
            return baos.toString().trim();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
    
    public static ExecResult sendInputStream(Session session, InputStream is, String path, int connectTimeout, boolean override) {
        log.info("{}文件写入...", path);
        ChannelSftp channel = null;
        ExecResult result = new ExecResult();
        try {
            // 创建执行通道
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout * 1000);
            if (!override) {
                try {
                    channel.lstat(path);
                    // 文件存在
                    result.setExecResult(true);
                    return result;
                } catch (SftpException exception) {
                    
                }
            }
            channel.put(is, path);
            result.setExecResult(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.setExecErrOut(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.exit();
            }
        }
        log.info("{}文件写入完成", path);
        return result;
    }
    
    public static ExecResult sendDir(Session session, String localDirPath, String remoteDirPath, int connectTimeout, boolean isVisual) {
        ExecResult result = new ExecResult();
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout * 1000);
            result = ensureRemotePathExists(session.getHost(), channel, remoteDirPath);
            if (result.isSuccess()) {
                result = sendDirChannel(channel, localDirPath, remoteDirPath, isVisual);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.setExecErrOut(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return result;
    }
    
    public static ExecResult ensureRemotePathExists(Session session, String remotePath) {
        ExecResult result = new ExecResult();
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(5 * 1000);
            return ensureRemotePathExists(session.getHost(), channel, remotePath);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.setExecErrOut(e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return result;
    }
    
    private static ExecResult ensureRemotePathExists(String host, ChannelSftp channel, String remotePath) {
        if (remotePath == null || !remotePath.startsWith("/")) {
            return ExecResult.error("Invalid remote path: must be non-null and absolute (start with '/').");
        }
        
        String normalizedPath = remotePath.equals("/") ? "/" : remotePath.endsWith("/") ? remotePath.substring(0, remotePath.length() - 1) : remotePath;
        try {
            channel.stat(normalizedPath);
            log.info("dir:{} is exist, host:{}", remotePath, host);
            return ExecResult.success();
        } catch (SftpException e) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return ExecResult.error("Failed to check existence of remote path '" + normalizedPath + "': " + e.getMessage());
            }
        }
        
        log.info("create dir: {} at {}", remotePath, host);
        
        String[] parts = normalizedPath.substring(1).split("/");
        StringBuilder currentPath = new StringBuilder("/");
        
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            currentPath.append(part);
            String pathToCreate = currentPath.toString();
            try {
                channel.stat(pathToCreate);
            } catch (SftpException ex) {
                if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    try {
                        channel.mkdir(pathToCreate);
                    } catch (SftpException mkdirEx) {
                        return ExecResult.error("Failed to create directory '" + pathToCreate + "': " + mkdirEx.getMessage());
                    }
                } else {
                    return ExecResult.error("Failed to check existence of directory '" + pathToCreate + "': " + ex.getMessage());
                }
            }
            currentPath.append("/");
        }
        
        return ExecResult.success();
    }
    
    private static ExecResult sendDirChannel(ChannelSftp channel, String localDirPath, String remoteDirPath, boolean isVisual) {
        log.info("sendDirChannel localDirPath:{}, remoteDirPath:{} start", localDirPath, remoteDirPath);
        
        ExecResult result = new ExecResult();
        try {
            File src = new File(localDirPath);
            if (src.exists() && src.isDirectory()) {
                File[] files = src.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            String newRemotePath = remoteDirPath + "/" + file.getName();
                            try {
                                channel.ls(newRemotePath);
                            } catch (SftpException ex) {
                                if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                                    channel.mkdir(newRemotePath);
                                    channel.ls(newRemotePath);
                                }
                            }
                            sendDirChannel(channel, file.getAbsolutePath(), newRemotePath, isVisual);
                        } else {
                            if (isVisual) {
                                log.info("transmit file:{}", file.getAbsolutePath());
                            }
                            channel.put(file.getAbsolutePath(), remoteDirPath + "/" + file.getName());
                        }
                    }
                }
            } else {
                if (isVisual) {
                    log.info("transmit file:{}", src.getAbsolutePath());
                }
                channel.put(src.getAbsolutePath(), remoteDirPath + "/" + src.getName());
            }
            result.setExecResult(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.setExecErrOut(e.getMessage());
        }
        log.info("sendDirChannel localDirPath:{}, remoteDirPath:{} end", localDirPath, remoteDirPath);
        return result;
    }
    
    public static ExecResult createDir(Session session, String dirPath, int connectTimeout) {
        ChannelSftp channel = null;
        ExecResult result = new ExecResult();
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout * 1000);
            String[] dirs = dirPath.split("/");
            String currentDir = "";
            for (String dir : dirs) {
                if (dir.isEmpty()) {
                    continue;
                }
                currentDir += "/" + dir;
                if (!isDirExist(channel, currentDir)) {
                    channel.mkdir(currentDir);
                }
            }
            result.setExecResult(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.setExecErrOut(e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return result;
    }
    
    private static boolean isDirExist(ChannelSftp channelSftp, String dir) {
        try {
            SftpATTRS attrs = channelSftp.lstat(dir);
            return attrs.isDir();
        } catch (Exception e) {
            return false;
        }
    }
    
    // public static void main(String[] args) throws Exception {
    // Session jSchSession = JschUtils.getJSchSession("192.168.2.122", 22, "root", "M2MwNTA3MjkwNjdhOG!b_");
    // String fileString = JschUtils.getFileString(jSchSession, "/etc/systemd/system.conf", 30);
    // System.out.println(fileString);
    // }
}
