package com.datasophon.common.utils;


import cn.hutool.core.io.IoUtil;
import com.datasophon.common.enums.ArchType;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JschUtils {

    public static Session getJSchSession(String ip, int port, String userName, String password) throws JSchException {
        JSch jSch = new JSch();
        Session session;
        try {
            //创建连接
            log.info("正在连接服务器{}@{}", userName, ip);
            session = jSch.getSession(userName, ip, port);
            session.setPassword(password);
            //是否使用密钥登录，一般默认为no
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            //启用连接
            session.connect(3000);
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

    public static List<String> execForLines(Session session, String command) throws JSchException, IOException {
        InputStream in = null;
        ChannelExec channel = null;
        try {
            //创建执行通道
            channel = (ChannelExec) session.openChannel("exec");
            //设置命令
            channel.setCommand(command);
            //连接通道
            channel.connect();
            //读取通道的输出
            return IoUtil.readLines(in, Charset.defaultCharset(), new ArrayList<>());
        } catch (JSchException e) {
            throw e;
        } finally {
            IoUtil.close(in);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    public static String execForStr(Session session, String command) throws JSchException, IOException {
        InputStream in = null;
        Channel channel = null;
        try {
            //创建执行通道
            channel = session.openChannel("exec");
            //设置命令
            ((ChannelExec) channel).setCommand(command);
            //连接通道
            channel.connect();
            //读取通道的输出
            in = channel.getInputStream();
            return IoUtil.read(in, Charset.defaultCharset());
        } finally {
            IoUtil.close(in);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
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
            //创建执行通道
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

                //读取通道的输出
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
            //创建执行通道
            channel = (ChannelShell) session.openChannel("shell");
            channel.connect(connectTimeout);
            is = channel.getInputStream();
            os = channel.getOutputStream();

            for (String cmd : commands) {
                os.write(cmd.getBytes()); // 输入命令
                os.write('\n'); // 输入换行执行
                os.flush();

                // FIXME 由于读取执行结果是阻塞的，必须等待指令执行一段时间，具体多少不好斟酌
                TimeUnit.SECONDS.sleep(cmdWaitSeconds);

                //读取通道的输出
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


    public static ArchType getArch(Session session) {
        try {
            return ArchType.of(execForStr(session, "arch"));
        } catch (JSchException | IOException e) {
            return ArchType.OTHER;
        }

    }

}
