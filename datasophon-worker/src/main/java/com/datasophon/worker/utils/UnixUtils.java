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


package com.datasophon.worker.utils;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnixUtils {
    
    private static final Long TIME_OUT = 60L;
    
    private static final Logger logger = LoggerFactory.getLogger(UnixUtils.class);
    
    public static ExecResult createUnixUser(String username, String mainGroup, String otherGroups) {
        ArrayList<String> commands = new ArrayList<>();
        if (isUserExists(username)) {
            commands.add("usermod");
        } else {
            commands.add("useradd");
        }
        commands.add(username);
        commands.add("--shell");
        commands.add("/bin/bash");
        if (StringUtils.isNotBlank(mainGroup)) {
            commands.add("-g");
            commands.add(mainGroup);
        }
        if (StringUtils.isNotBlank(otherGroups)) {
            commands.add("-G");
            commands.add(otherGroups);
        }
        return ShellUtils.exec(Constants.INSTALL_PATH, commands, TIME_OUT);
    }
    
    public static ExecResult delUnixUser(String username) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("userdel");
        commands.add("-r");
        commands.add(username);
        return ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, TIME_OUT);
    }
    
    public static boolean isUserExists(String username) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("id");
        commands.add(username);
        ExecResult execResult = ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, TIME_OUT);
        return execResult.getExecResult();
    }
    
    public static ExecResult createUnixGroup(String groupName) {
        if (isGroupExists(groupName)) {
            ExecResult execResult = new ExecResult();
            execResult.setExecResult(true);
            return execResult;
        }
        ArrayList<String> commands = new ArrayList<>();
        commands.add("groupadd");
        commands.add(groupName);
        return ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, TIME_OUT);
    }
    
    public static ExecResult delUnixGroup(String groupName) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("groupdel");
        commands.add(groupName);
        return ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, TIME_OUT);
    }
    
    public static boolean isGroupExists(String groupName) {
        ExecResult execResult = ShellUtils.execShell("egrep \"" + groupName + "\" /etc/group >& /dev/null");
        return execResult.getExecResult();
    }
    
}
