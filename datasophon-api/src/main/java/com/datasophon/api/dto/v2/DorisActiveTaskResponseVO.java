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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.dto.v2;

import java.util.List;

import lombok.Data;

/** Response contract for a Doris active-task snapshot. */
@Data
public class DorisActiveTaskResponseVO {

    private List<DorisActiveTaskVO> tasks = List.of();
    private boolean degraded;
    private String degradedReason;
    private List<String> partialFailures = List.of();
    private boolean truncated;
    private boolean sourceTruncated;
    private long total;
    private int returned;
    private String connectedHostPort;
    /** 服务端版本串（{@code @@version_comment} 原文），供页面自证连的是哪个版本。 */
    private String serverVersion = "";
    /** 当前 Doris 大版本确定拿不到的字段，前端据此显示「该版本不支持」而不是空值。 */
    private List<String> unsupportedFields = List.of();

    public static DorisActiveTaskResponseVO empty() {
        DorisActiveTaskResponseVO response = new DorisActiveTaskResponseVO();
        response.setConnectedHostPort("");
        return response;
    }
}
