package com.datasophon.cli.base;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;

import java.util.List;
import java.util.Map;

public interface Executor {
    /**
     * 执行shell命令
     * @param cmd shell命令
     * @return ExecResult
     */
    ExecResult execShell(String cmd);

    /**
     * 执行交互shell命令
     * @param cmd shell命令
     * @param expects 匹配输入命令
     * @return ExecResult
     */
    ExecResult execShellExp(String cmd, Map<String, String> expects);
    
    /**
     * 判断文件是否存在
     * @param path 文件路径
     * @return ExecResult
     */
    ExecResult exists(String path);
    
    /**
     * 发送文件
     * @param src 源文件路径
     * @param dest 目标文件路径
     * @return ExecResult
     */
    ExecResult sendFile(String src, String dest, boolean override);
    
    /**
     * 发送目录
     * @param srcDir 源目录路径
     * @param destDir 目标目录路径
     * @return 是否打印过程
     */
    ExecResult sendDir(String srcDir, String destDir, boolean isVisual);
    
    /**
     * 创建目录
     * @param destDir 目标目录路径
     * @return
     */
    ExecResult createDir(String destDir);
    
    /**
     * 读取文件内容
     * @param path 文件路径
     * @return 文件内容列表
     */
    ExecResult getFileString(String path);
    
    /**
     * 写入文件内容
     * @param lines 文件内容列表
     * @param path 文件路径
     */
    ExecResult writeLines(List<String> lines, String path);
    
    /**
     * 获取当前系统架构
     * @return ArchType
     */
    ArchType getArch();
    
    /**
     * 获取当前操作系统类型
     * @return OsType
     */
    OsType getOs();
}
