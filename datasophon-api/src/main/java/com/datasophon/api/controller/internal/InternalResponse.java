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

package com.datasophon.api.controller.internal;

import lombok.Data;

/**
 * 内部系统对接端点的统一响应信封。
 */
@Data
public class InternalResponse<T> {

    private boolean success;

    private int code;

    private String message;

    private T data;

    public static <T> InternalResponse<T> ok(T data) {
        InternalResponse<T> response = new InternalResponse<>();
        response.setSuccess(true);
        response.setCode(200);
        response.setData(data);
        return response;
    }

    public static <T> InternalResponse<T> ok() {
        return ok(null);
    }

    public static <T> InternalResponse<T> fail(int code, String message) {
        InternalResponse<T> response = new InternalResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
