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

package com.datasophon.api.dto;

import lombok.Data;

/**
 * ant-design-pro 标准响应信封（v2 API 专用）。
 *
 * <p>对齐前端 requestErrorConfig.ts 的约定：
 * <pre>
 * {
 *   success: boolean,
 *   data: T,
 *   errorCode: number,      // 业务错误码，success=false 时有效
 *   errorMessage: string,   // 用户可读错误描述
 *   showType: number        // 0=SILENT 1=WARN 2=ERROR 3=NOTIFICATION 9=REDIRECT
 * }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
@Data
public class ApiResponse<T> {
    
    private boolean success;
    private T data;
    private Integer errorCode;
    private String errorMessage;
    /** 前端弹出方式：0=静默 1=warning 2=error(默认) 3=notification 9=跳登录 */
    private Integer showType;
    
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = true;
        resp.data = data;
        return resp;
    }
    
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }
    
    public static <T> ApiResponse<T> fail(int errorCode, String errorMessage) {
        return fail(errorCode, errorMessage, 2);
    }
    
    public static <T> ApiResponse<T> fail(int errorCode, String errorMessage, int showType) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = false;
        resp.errorCode = errorCode;
        resp.errorMessage = errorMessage;
        resp.showType = showType;
        return resp;
    }
}
