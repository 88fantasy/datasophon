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

package com.datasophon.common.utils;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Setter
public class ExecResult implements Serializable {
    
    private static final long serialVersionUID = -6542233431946706943L;
    
    private boolean execResult = false;
    
    /**
     * 命令行的输出
     */
    @Getter
    private String execOut;
    
    /**
     * 调用程序的异常信息
     */
    @Getter
    private String execErrOut;
    
    public boolean getExecResult() {
        return execResult;
    }
    
    public boolean isSuccess() {
        return execResult;
    }
    
    public static ExecResult success() {
        return success(null);
    }
    
    public String getErrorTraceMessage() {
        if (execErrOut != null) {
            return "堆栈信息：" + execErrOut;
        }
        return "错误信息:" + execOut;
    }
    
    public static ExecResult success(String out) {
        ExecResult exec = new ExecResult();
        exec.setExecResult(true);
        exec.setExecOut(out);
        return exec;
    }
    
    public static ExecResult error(String out) {
        ExecResult exec = new ExecResult();
        exec.setExecResult(false);
        exec.setExecOut(out);
        return exec;
    }
}
