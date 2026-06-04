package com.datasophon.common.utils;

import com.datasophon.common.enums.SSHAuthType;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
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
            // 这里阻塞等待执行完成
            while (!channel.isClosed()) {
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
    public static ExecResult shellForExp(Session session, String command, Map<String, String> expects) {
        InputStream is = null;
        BufferedReader bufReader = null;
        OutputStream os = null;
        ChannelShell channel = null;
        ExecResult result = new ExecResult();
        StringBuilder execOut = new StringBuilder();
        try {
            // 创建执行通道
            channel = (ChannelShell) session.openChannel("shell");
            is = channel.getInputStream();
            os = channel.getOutputStream();
            
            os.write(command.getBytes()); // 输入命令
            os.write('\n'); // 输入换行执行
            os.flush();
            // FIXME 由于读取执行结果是阻塞的，必须等待指令执行一段时间，具体多少不好斟酌
            TimeUnit.SECONDS.sleep(500);
            
            // 读取通道的输出
            bufReader = new BufferedReader(new InputStreamReader(is));
            String line = "";
            while (Objects.nonNull((line = bufReader.readLine()))) {
                execOut.append(line);
                if (Objects.nonNull(expects)) {
                    for (Map.Entry<String, String> entry : expects.entrySet()) {
                        if (line.contains(entry.getKey())) {
                            os.write(entry.getValue().getBytes());
                            os.write('\n'); // 输入换行执行
                            os.flush();
                            break;
                        }
                    }
                }
            }
            os.write("exit".getBytes()); // 退出命令
            os.write('\n'); // 输入换行执行
            os.flush();
            result.setExecResult(true);
            result.setExecOut(execOut.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IoUtil.close(os);
            IoUtil.close(bufReader);
            IoUtil.close(is);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return result;
    }
    
    public static Map<String, String> shellForStr(Session session, List<String> commands) throws Exception {
        Map<String, String> result = new ConcurrentHashMap<>();
        InputStream is = null;
        OutputStream os = null;
        ChannelShell channel = null;
        try {
            // 创建执行通道
            channel = (ChannelShell) session.openChannel("shell");
            is = channel.getInputStream();
            os = channel.getOutputStream();
            
            for (String cmd : commands) {
                os.write(cmd.getBytes()); // 输入命令
                os.write('\n'); // 输入换行执行
                os.flush();
                // FIXME 由于读取执行结果是阻塞的，必须等待指令执行一段时间，具体多少不好斟酌
                TimeUnit.SECONDS.sleep(500);
                // 读取通道的输出
                String rs = IoUtil.read(is, Charset.defaultCharset());
                result.put(cmd, rs);
            }
            return result;
        } finally {
            IoUtil.close(os);
            IoUtil.close(is);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
    
    public static List<String> shellForLines(Session session, String command, int connectTimeout, int cmdWaitSeconds) throws Exception {
        Map<String, List<String>> map = shellForLines(session, Collections.singletonList(command), connectTimeout, cmdWaitSeconds);
        return map.get(command);
    }
    
    public static Map<String, List<String>> shellForLines(Session session, List<String> commands, int connectTimeout, int cmdWaitSeconds) throws Exception {
        Map<String, List<String>> result = new ConcurrentHashMap<>();
        InputStream is = null;
        OutputStream os = null;
        ChannelShell channel = null;
        try {
            // 创建执行通道
            channel = (ChannelShell) session.openChannel("shell");
            channel.connect(connectTimeout * 1000);
            is = channel.getInputStream();
            os = channel.getOutputStream();
            
            for (String cmd : commands) {
                os.write(cmd.getBytes()); // 输入命令
                os.write('\n'); // 输入换行执行
                os.flush();
                
                // FIXME 由于读取执行结果是阻塞的，必须等待指令执行一段时间，具体多少不好斟酌
                TimeUnit.SECONDS.sleep(cmdWaitSeconds);
                
                // 读取通道的输出
                ArrayList<String> readLines = IoUtil.readLines(is, Charset.defaultCharset(), new ArrayList<>());
                result.put(cmd, readLines);
            }
            return result;
        } finally {
            IoUtil.close(os);
            IoUtil.close(is);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
    
    public static List<String> getFileLines(Session session, String path, int connectTimeout) throws Exception {
        String fileString = getFileString(session, path, connectTimeout);
        return Arrays.asList(fileString.split("\n"));
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
    
    public static ExecResult exists(Session session, String path, int connectTimeout) {
        ExecResult result = new ExecResult();
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout * 1000);
            channel.ls(path);
            result.setExecResult(true);
            log.info("path exists :{}", path);
        } catch (Exception e) {
            log.warn(path, e.getMessage());
            result.setExecErrOut(e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
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
