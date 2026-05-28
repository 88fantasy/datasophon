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


package com.datasophon.common.enums;

import com.datasophon.common.Constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CommandType {
    
    // 命令类型1：安装服务 2：启动服务 3：停止服务 4：重启服务 5：更新配置后启动 6：更新配置后重启
    INSTALL_SERVICE(1, "INSTALL", "安装"),
    START_SERVICE(2, "START", "启动"),
    STOP_SERVICE(3, "STOP", "停止"),
    RESTART_SERVICE(4, "RESTART", "重启"),
    START_WITH_CONFIG(5, "START_WITH_CONFIG", "更新配置后启动"),
    RESTART_WITH_CONFIG(6, "RESTART_WITH_CONFIG", "更新配置后重启"),
    UPGRADE_SERVICE(7, "UPGRADE_SERVICE", "升级"),
    CHECK_STATUS(8, "CHECK_STATUS", "检查状态")
    ;
    
    private int value;
    
    private String desc;
    
    private String cnDesc;
    
    CommandType(int value, String desc, String cnDesc) {
        this.value = value;
        this.desc = desc;
        this.cnDesc = cnDesc;
    }
    
    public int getValue() {
        return value;
    }
    
    public void setValue(int value) {
        this.value = value;
    }
    
    @JsonValue
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
    
    public String getCnDesc() {
        return cnDesc;
    }
    
    public void setZnDesc(String cnDesc) {
        this.cnDesc = cnDesc;
    }
    
    public String getCommandName(String language) {
        if (Constants.CN.equals(language)) {
            return this.cnDesc;
        } else {
            return this.desc;
        }
    }

    public static CommandType ofCode(Integer type) {
        for (CommandType cmd : values()) {
            if (cmd.value == type) {
                return cmd;
            }
        }
        return null;
    }
    
    @JsonCreator
    public static CommandType fromDesc(String desc) {
        if (desc == null) {
            return null;
        }
        for (CommandType ct : values()) {
            if (ct.desc.equals(desc)) {
                return ct;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.desc;
    }

}
