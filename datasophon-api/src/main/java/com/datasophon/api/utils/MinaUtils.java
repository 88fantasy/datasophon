/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.datasophon.api.utils;

import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.function.ThrowableConsumer;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.JschUtils;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public class MinaUtils {
    
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MinaUtils.class);
    
    /**
     * 打开远程会话
     */
    public static Session openConnection(String sshHost, Integer sshPort, String sshUser, String password) throws JSchException {
        if(Objects.isNull(sshPort)){
            sshPort = Constants.PORT_DEFAULT;
        }
        if(StringUtils.isBlank(sshUser)){
            sshUser = Constants.ROOT;
        }
        return JschUtils.getJSchSession(SSHAuthType.AUTO, sshHost, sshPort, sshUser, password);
    }
    
    /**
     * 关闭远程会话
     */
    public static void closeConnection(Session session) {
        JschUtils.closeJSchSession(session);
    }
    
    /**
     * 获取密钥对
     */
    static KeyPair getKeyPairFromString(String pk) {
        final KeyPairGenerator rsa;
        try {
            rsa = KeyPairGenerator.getInstance("RSA");
            final KeyPair keyPair = rsa.generateKeyPair();
            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.write(pk.getBytes());
            final ObjectOutputStream o = new ObjectOutputStream(stream);
            o.writeObject(keyPair);
            return keyPair;
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 同步执行,需要获取执行完的结果
     *
     * @param session 连接
     * @param command 命令
     * @return 结果
     */
    public static String execCmdWithResult(Session session, String command) {
        return JschUtils.execForStr(session, command).getExecOut();
    }

    public static ExecResult execCmd(Session session, String command) {
        return JschUtils.execForStr(session, command);
    }


    /**
     * 上传文件,相同路径ui覆盖
     *
     * @param session    连接
     * @param remotePath 远程目录地址
     * @param inputFile  文件 File
     */
    public static boolean uploadFile(Session session, String remotePath, String inputFile) {
        return JschUtils.sendDir(session, inputFile, remotePath, 5, true).getExecResult();
    }
    
    /**
     * 创建目录
     *
     * @param path
     * @return
     */
    public static boolean createDir(Session session, String path) {
        return JschUtils.createDir(session, path, 5).getExecResult();
    }
    
    public static ArchType getArch(Session session) {
        String arch = MinaUtils.execCmdWithResult(session, Constants.OS_ARCH_CMD);
        return ArchType.of(arch);
    }

    public static String getFileString(Session session, String path){
        return JschUtils.getFileString(session, path, 5);
    }



    public static void doWithSession(SessionCredential credential, ThrowableConsumer<Session> action) throws Exception{
        Session session = null;
        try {
            session = openConnection(credential.getHost(), credential.getPort(), credential.getUsername(), credential.getPassword());
            action.accept(session);
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Data
    public static class SessionCredential {

        private String host;

        private Integer port = 22;

        private String username;

        private String password;

        public SessionCredential(String host, Integer port,  String username, String password) {
            this.host = host;
            this.password = password;
            this.port = port == null ? 22 : port;
            this.username = username;
        }
    }
}
