/*
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
 */

package com.datasophon.common.utils;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ShellUtils {


    private static final Logger logger = LoggerFactory.getLogger(ShellUtils.class);


    /**
     * 执行 shell 命令（通过 sh -c 包装）
     *
     * @param workPath        工作目录（可为 null 或空，表示不切换目录）
     * @param commandParts    命令及其参数列表，例如 ["mkdir", "-p", "tmp"]
     * @param timeoutInSecond 超时时间（秒）
     * @return 执行结果封装对象
     */
    public static ExecResult execWithBash(String workPath, List<String> commandParts, long timeoutInSecond) {
        if (CollectionUtils.isEmpty(commandParts)) {
            throw new IllegalArgumentException("Command must not be null or empty");
        }
        String cmd = String.join(" ", commandParts);
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return exec(workPath, Arrays.asList("cmd", "/c", cmd), timeoutInSecond);
        } else {
            return exec(workPath, Arrays.asList("bash", "-c", cmd), timeoutInSecond);
        }
    }


    /**
     * 执行命令，不使用shell执行
     *
     * @param workPath
     * @param commandParts
     * @param timeout
     * @return
     */
    public static ExecResult exec(String workPath, List<String> commandParts, long timeout) {
        if (CollectionUtils.isEmpty(commandParts)) {
            throw new IllegalArgumentException("Command must not be null or empty");
        }

        List<String> args = formatArgs(commandParts);

        ExecResult result = new ExecResult();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Process process = null;
        Thread outputReader = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();

            if (StrUtil.isNotBlank(workPath)) {
                processBuilder.directory(new File(workPath));
            }

            processBuilder.command(args);
            processBuilder.redirectErrorStream(true);

            logger.info("exec cmd: {}, workspace: {}", StrUtil.join(" ", args), workPath);
            process = processBuilder.start();

            Process finalProcess = process;
            outputReader = new Thread(() -> {
                try {
                    IoUtil.copy(finalProcess.getInputStream(), out);
                } catch (IORuntimeException ignore) {

                }
            });
            outputReader.setDaemon(true);
            outputReader.start();

            boolean finished;
            if (timeout <= 0) {
                process.waitFor();
                finished = true;
            } else {
                finished = process.waitFor(timeout, TimeUnit.SECONDS);
            }
            boolean execResult = finished && process.exitValue() == 0;
            String message = new String(out.toByteArray(), Charset.defaultCharset());
            if (!execResult) {
                message = String.format("%s\nCommand timed out after %s seconds", message, timeout);
            }
            result.setExecResult(execResult);
            result.setExecOut(message);
            logger.info("exec cmd {} {}", String.join(" ", args), result.isSuccess() ? "success" : "fail");
            return result;
        } catch (Exception e) {
            logger.error("exec cmd fail, cmd: {}, message: {}", String.join(" ", args), e.getMessage(), e);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            result.setExecErrOut(sw.toString());
            return result;
        } finally {
            destroy(process, false);
            if (outputReader != null && outputReader.isAlive()) {
                outputReader.interrupt();
            }
        }
    }


    /**
     * 将命令列表拼接成字符串，再按命令行参数规则分割。
     * 支持双引号包裹的带空格参数（例如：echo "hello world" 解析为 [echo, hello world]）。
     *
     * @param commands 原始命令列表（例如 ["echo", "\"hello world\""]）
     * @return 解析后的参数列表
     */
    private static List<String> formatArgs(List<String> commands) {
        // 1. 用单个空格拼接所有元素
        String joined = String.join(" ", commands);
        List<String> result = new ArrayList<>();
        int length = joined.length();
        int i = 0;

        while (i < length) {
            // 跳过前导空格
            while (i < length && joined.charAt(i) == ' ') {
                i++;
            }
            if (i >= length) {
                break;
            }

            char current = joined.charAt(i);
            if (current == '"') {
                // 处理双引号包裹的参数：找到下一个双引号，中间内容作为参数（不含引号）
                i++; // 跳过左引号
                int start = i;
                while (i < length && joined.charAt(i) != '"') {
                    i++;
                }
                String arg = joined.substring(start, i);
                result.add(arg);
                i++; // 跳过右引号
            } else {
                // 处理普通参数：直到遇到空格为止
                int start = i;
                while (i < length && joined.charAt(i) != ' ') {
                    i++;
                }
                String arg = joined.substring(start, i);
                result.add(arg);
            }
        }
        return result;
    }

    /**
     * @param pathOrCommand 脚本路径或者命令
     * @return
     */
    public static ExecResult execShell(String pathOrCommand) {
        logger.info("command:{}", pathOrCommand);
        ExecResult result = new ExecResult();
        StringBuilder stringBuffer = new StringBuilder();
        try {
            // 执行脚本
            Process ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", pathOrCommand});
            // 只能接收脚本echo打印的数据，并且是echo打印的最后一次数据
            BufferedInputStream in = new BufferedInputStream(ps.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                stringBuffer.append(line);
                stringBuffer.append(System.lineSeparator());
            }
            // 去除尾部的换行符
            if (stringBuffer.length() > 0 && stringBuffer.charAt(stringBuffer.length() - 1) == '\n') {
                stringBuffer.setLength(stringBuffer.length() - 1);
            }
            in.close();
            br.close();
            String execOut = stringBuffer.toString();
            int exitValue = ps.waitFor();
            if (0 == exitValue) {
                logger.info("exec command: {}, cmd output is : {} {},exitValue:{}", pathOrCommand, System.lineSeparator(), execOut, exitValue);
                result.setExecResult(true);
                result.setExecOut(execOut);
            } else {
                result.setExecOut("call shell failed. error code is :" + exitValue);
                logger.error("exec command {}, cmd out is : {} {},exitValue:{}", pathOrCommand, System.lineSeparator(), execOut, exitValue);
            }

        } catch (Exception e) {
            result.setExecOut(e.getMessage());
            logger.error(e.getMessage(), e);
        }
        return result;
    }


    // 获取cpu架构 arm或x86
    public static String getCpuArchitecture() {
        try {
            Process ps = Runtime.getRuntime().exec("arch");
            StringBuilder stringBuffer = new StringBuilder();
            int exitValue = ps.waitFor();
            if (0 == exitValue) {
                // 只能接收脚本echo打印的数据，并且是echo打印的最后一次数据
                BufferedInputStream in = new BufferedInputStream(ps.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = br.readLine()) != null) {
                    logger.info("脚本返回的数据如下： {}", line);
                    stringBuffer.append(line);
                }
                in.close();
                br.close();
                return stringBuffer.toString();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * @param workPath
     * @param command
     * @param timeout
     * @return
     * @see #exec(String, List, long)
     * @deprecated
     */
    @Deprecated
    public static ExecResult execWithStatus(String workPath, List<String> command, long timeout) {
        Process process = null;
        ExecResult result = new ExecResult();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(workPath));
            processBuilder.command(command);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            getOutput(process);
            boolean execResult = process.waitFor(timeout, TimeUnit.SECONDS);
            if (execResult && process.exitValue() == 0) {
                logger.info("exec cmd success, cmd: {}", String.join(" ", command));
                result.setExecResult(true);
                result.setExecOut("script execute success");
            } else {
                result.setExecOut("script execute failed");
            }
            return result;
        } catch (Exception e) {
            result.setExecErrOut(e.getMessage());
            logger.error("exec cmd fail, cmd: {}, message: {}", String.join(" ", command), e.getMessage(), e);
        }
        return result;
    }

    public static ExecResult execWithStatus(String workPath, List<String> command, long timeout, Logger logger) {
        logger.info("exec cmd, workdir: {}, commands {}", workPath, StrUtil.join(" ", command));
        Process process = null;
        ExecResult result = new ExecResult();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(workPath));
            processBuilder.command(command);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            getOutput(workPath, command, process, logger);
            boolean execResult = process.waitFor(timeout, TimeUnit.SECONDS);
            if (execResult && process.exitValue() == 0) {
                logger.info("script execute success");
                result.setExecResult(true);
                result.setExecOut("script execute success");
            } else {
                result.setExecOut("script execute failed");
            }
            return result;
        } catch (Exception e) {
            result.setExecErrOut(e.getMessage());
            logger.error(e.getMessage(), e);
        }
        return result;
    }

    public static void getOutput(String workPath, List<String> command, Process process, Logger logger) {
        ExecutorService getOutputLogService = Executors.newSingleThreadExecutor();

        getOutputLogService.submit(() -> {
            BufferedReader inReader = null;
            try {
                inReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder stringBuffer = new StringBuilder();
                while ((line = inReader.readLine()) != null) {
                    stringBuffer.append(line);
                    stringBuffer.append(System.lineSeparator());
                }
                String out = stringBuffer.toString();
                if (StringUtils.isNotBlank(out)) {
                    out = String.format("执行命令行: %s\n\t workdir: %s\n\t标准输出流输出: %s", StrUtil.join(" ", command), workPath, out);
                    logger.info(out);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                IoUtil.close(inReader);
            }
            BufferedReader errorReader = null;
            try {
                errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                StringBuilder stringBuffer = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    stringBuffer.append(line);
                    stringBuffer.append(System.lineSeparator());
                }
                String out = stringBuffer.toString();
                if (StringUtils.isNotBlank(out)) {
                    out = String.format("执行命令行: %s\n\t workdir: %s\n\t错误输出流输出: %s", StrUtil.join(" ", command), workPath, out);
                    logger.error(out);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                IoUtil.close(errorReader);
            }
        });
        getOutputLogService.shutdown();
    }

    public static void getOutput(Process process) {
        ExecutorService getOutputLogService = Executors.newSingleThreadExecutor();
        getOutputLogService.submit(() -> {
            BufferedReader inReader = null;
            try {
                inReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuffer stringBuffer = new StringBuffer();
                while ((line = inReader.readLine()) != null) {
                    stringBuffer.append(line);
                    stringBuffer.append(System.lineSeparator());
                }
                logger.trace(stringBuffer.toString());
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                IoUtil.close(inReader);
            }
        });
        getOutputLogService.shutdown();
    }


    public static void destroy(Process process, boolean force) {
        if (process == null) {
            return;
        }
        IOUtils.closeQuietly(process.getInputStream());
        IOUtils.closeQuietly(process.getErrorStream());
        IOUtils.closeQuietly(process.getOutputStream());

        boolean stop = false;
        if (!force) {
            process.destroy();
            try {
                stop = process.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
            }
        }
        if (!stop) {
            process.destroyForcibly();
        }
    }

    public static void addChmod(String path, String chmod) {
        ArrayList<String> command = new ArrayList<>();
        command.add("chmod");
        command.add("-R");
        command.add(chmod);
        command.add(path);
        execWithStatus(Constants.INSTALL_PATH, command, 60, logger);
    }

    public static void addChown(String path, String user, String group) {
        ArrayList<String> command = new ArrayList<>();
        command.add("chown");
        command.add("-R");
        command.add(user + ":" + group);
        command.add(path);
        execWithStatus(Constants.INSTALL_PATH, command, 60, logger);
    }

    public static ArchType getArch() {
        String result = execShell(Constants.OS_ARCH_CMD).getExecOut();
        return OsUtils.getArch(result);
    }

    public static OsType getOs() {
        String result = execShell(Constants.OS_VERSION_CMD).getExecOut();
        return OsUtils.getOs(result);
    }
}
