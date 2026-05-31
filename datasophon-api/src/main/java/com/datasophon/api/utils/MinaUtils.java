/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
