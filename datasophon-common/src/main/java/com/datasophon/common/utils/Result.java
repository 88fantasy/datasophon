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

import com.datasophon.common.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public class Result extends HashMap<String, Object> {
    
    private static final long serialVersionUID = 1L;
    
    private Integer code;
    private String msg;
    
    private Object data;
    
    public Result() {
    }
    
    public static Result error() {
        return error(500, "未知异常，请联系管理员");
    }
    
    public static Result error(String msg) {
        return error(500, msg);
    }
    
    public static Result error(int code, String msg) {
        Result result = new Result();
        result.put("code", code);
        result.put("msg", msg);
        return result;
    }
    
    public static Result success(Map<String, Object> map) {
        Result result = new Result();
        result.putAll(map);
        return result;
    }
    
    public static Result success(long total, List<?> list) {
        Result result = success(list);
        result.put(Constants.TOTAL, total);
        return result;
    }
    
    public Integer getCode() {
        return (Integer) this.get(Constants.CODE);
    }
    
    public Object getData() {
        return this.get(Constants.DATA);
    }
    
    public boolean isSuccess() {
        return this.getCode() == 200;
    }
    
    public static Result success(Object data) {
        Result result = new Result();
        result.put(Constants.CODE, 200);
        result.put(Constants.MSG, "success");
        result.put("data", data);
        return result;
    }
    
    public static Result success() {
        Result result = new Result();
        result.put(Constants.CODE, 200);
        result.put(Constants.MSG, "success");
        return result;
    }
    
    public static Result successEmptyCount() {
        Result result = success(new ArrayList<>(0));
        result.put(Constants.TOTAL, 0);
        return result;
    }
    
    @Override
    public Result put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
