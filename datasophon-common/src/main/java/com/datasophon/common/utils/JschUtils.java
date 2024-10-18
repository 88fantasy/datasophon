package com.datasophon.common.utils;

import cn.hutool.core.io.IoUtil;
import com.datasophon.common.enums.SSHAuthType;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JschUtils {
    
    public static Session getJSchSession(SSHAuthType sshAuthType, String ip, int port, String userName, String password) throws JSchException {
        JSch jSch = new JSch();
        Session session;
        try {
            // 创建连接
            log.info("正在连接服务器{}@{}", userName, ip);
            session = jSch.getSession(userName, ip, port);
            if (sshAuthType == SSHAuthType.PASSWORD) {
                session.setPassword(password);
            } else {
                jSch.addIdentity(String.format("/%s/.ssh/id_rsa", userName));
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
    
    public static ExecResult execForStr(Session session, String command){
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
            if (execOut.length() > 0 && execOut.charAt(execOut.length() - 1) == '\n') {
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
    public static String shellForStr(Session session, String command, int connectTimeout, int cmdWaitSeconds) throws Exception {
        Map<String, String> map = shellForStr(session, Collections.singletonList(command), connectTimeout, cmdWaitSeconds);
        return map.get(command);
    }
    
    public static Map<String, String> shellForStr(Session session, List<String> commands, int connectTimeout, int cmdWaitSeconds) throws Exception {
        Map<String, String> result = new ConcurrentHashMap<>();
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
    
    public static String getFileString(Session session, String path, int connectTimeout) throws Exception {
        ChannelSftp channel = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // 创建执行通道
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout * 1000);
            channel.get(path, baos);
            return baos.toString();
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
    
    public static ExecResult sendInputStream(Session session, InputStream is, String path, int connectTimeout, boolean override) {
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
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.exit();
            }
        }
        return result;
    }
    
    public static ExecResult sendDir(Session session, String localDirPath, String remoteDirPath, int connectTimeout,boolean isVisual) {
        ExecResult result = new ExecResult();
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout * 1000);
            result = sendDirChannel(channel, localDirPath, remoteDirPath, isVisual);
            result.setExecResult(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.setExecErrOut(e.getMessage());
        }  finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return result;
    }
    private static ExecResult sendDirChannel(ChannelSftp channel, String localDirPath, String remoteDirPath, boolean isVisual) {
        ExecResult result = new ExecResult();
        try {
            File dir = new File(localDirPath);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
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
            }
            result.setExecResult(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.setExecErrOut(e.getMessage());
        }
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
        }  finally {
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
                if (!isDirExist(channel,currentDir)) {
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
